#!/usr/bin/env python3
from pathlib import Path
import sys

MARKER = "// COHERO_SAVE_TRANSFER"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: patch_wndsettings.py <WndSettings.java>")

    path = Path(sys.argv[1])
    text = path.read_text(encoding="utf-8")

    if MARKER in text:
        raise SystemExit(f"CoHero save-transfer controls already exist in {path}")

    field_anchor = "\t\tCheckBox chkVibrate;\n"
    field_replacement = (
        field_anchor
        + "\t\tColorBlock sep3;\n"
        + "\t\tRedButton btnExportSave;\n"
        + "\t\tRedButton btnImportSave;\n"
    )
    text = replace_once(text, field_anchor, field_replacement, "UITab field")

    create_anchor = "\t\t\tadd(chkVibrate);\n"
    create_replacement = create_anchor + """

			if (DeviceCompat.isAndroid() || DeviceCompat.isDesktop()) {
				// COHERO_SAVE_TRANSFER
				sep3 = new ColorBlock(1, 1, 0xFF000000);
				add(sep3);

				btnExportSave = new RedButton(Messages.get("cohero.save_transfer.export"), 8) {
					@Override
					protected void onClick() {
						super.onClick();
						com.spd.cohero.CoHeroSaveTransfer.exportSave();
					}
				};
				add(btnExportSave);

				btnImportSave = new RedButton(Messages.get("cohero.save_transfer.import"), 8) {
					@Override
					protected void onClick() {
						super.onClick();
						com.spd.cohero.CoHeroSaveTransfer.importSave();
					}
				};
				add(btnImportSave);
			}
"""
    text = replace_once(text, create_anchor, create_replacement, "UITab create")

    layout_anchor = (
        "\t\t\t\tchkVibrate.setRect(0, chkFont.bottom() + GAP, width, BTN_HEIGHT);\n"
        "\t\t\t\theight = chkVibrate.bottom();\n"
        "\t\t\t}\n"
    )
    layout_replacement = layout_anchor + """

			if (btnExportSave != null) {
				sep3.size(width, 1);
				sep3.y = height + GAP;

				float transferWidth = width / 2f - GAP / 2f;
				btnExportSave.setRect(0, sep3.y + 1 + GAP, transferWidth, BTN_HEIGHT);
				btnImportSave.setRect(
						btnExportSave.right() + GAP,
						btnExportSave.top(),
						transferWidth,
						BTN_HEIGHT);
				height = btnImportSave.bottom();
			}
"""
    text = replace_once(text, layout_anchor, layout_replacement, "UITab layout")

    path.write_text(text, encoding="utf-8")
    print(f"patched {path}: CoHero Android/Desktop save-transfer controls")


if __name__ == "__main__":
    main()
