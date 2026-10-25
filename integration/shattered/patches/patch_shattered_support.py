#!/usr/bin/env python3
"""Hide upstream Shattered support UI with one compile-time flag."""
from pathlib import Path
import sys
from java_patch import java_source

SUPPORT_FLAG = "com.spd.cohero.CoHeroSettings.SHOW_SHATTERED_SUPPORT"


def replace_once(source, old, new, label):
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    return source.replace(old, new, 1)


def main():
    if len(sys.argv) != 4:
        raise SystemExit("usage: patch_shattered_support.py <TitleScene.java> <WornKey.java> <WndVictoryCongrats.java>")

    title_path = Path(sys.argv[1])
    key_path = Path(sys.argv[2])
    victory_path = Path(sys.argv[3])
    title = java_source(title_path.read_text(encoding="utf-8"))
    key = java_source(key_path.read_text(encoding="utf-8"))
    victory = java_source(victory_path.read_text(encoding="utf-8"))

    title = replace_once(
        title,
        '\t\tbtnSupport = new SupportButton(GREY_TR, Messages.get(this, "support"));\n'
        '\t\tadd(btnSupport);',
        '\t\tif (' + SUPPORT_FLAG + ') {\n'
        '\t\t\tbtnSupport = new SupportButton(GREY_TR, Messages.get(this, "support"));\n'
        '\t\t\tadd(btnSupport);\n'
        '\t\t}',
        "support button creation",
    )
    title = replace_once(
        title,
        'int GAP = (int)(h - topRegion - (landscape() ? 3 : 4)*BTN_HEIGHT)/3;',
        'int GAP = (int)(h - topRegion - (landscape() ? 3 : (btnSupport == null ? 3 : 4))*BTN_HEIGHT)/3;',
        "portrait menu row count",
    )
    title = replace_once(
        title,
        '\t\t\tbtnPlay.setRect(btnAreaLeft, insets.top + topRegion+GAP, (buttonAreaWidth/2)-1, BTN_HEIGHT);\n'
        '\t\t\talign(btnPlay);\n'
        '\t\t\tbtnSupport.setRect(btnPlay.right()+2, btnPlay.top(), btnPlay.width(), BTN_HEIGHT);',
        '\t\t\tbtnPlay.setRect(btnAreaLeft, insets.top + topRegion+GAP,\n'
        '\t\t\t\t\tbtnSupport == null ? buttonAreaWidth : (buttonAreaWidth/2)-1, BTN_HEIGHT);\n'
        '\t\t\talign(btnPlay);\n'
        '\t\t\tif (btnSupport != null) {\n'
        '\t\t\t\tbtnSupport.setRect(btnPlay.right()+2, btnPlay.top(), btnPlay.width(), BTN_HEIGHT);\n'
        '\t\t\t}',
        "landscape support layout",
    )
    title = replace_once(
        title,
        '\t\t\tbtnSupport.setRect(btnPlay.left(), btnPlay.bottom()+ GAP, btnPlay.width(), BTN_HEIGHT);\n'
        '\t\t\tbtnRankings.setRect(btnPlay.left(), btnSupport.bottom()+ GAP, (btnPlay.width()/2)-1, BTN_HEIGHT);',
        '\t\t\tif (btnSupport != null) {\n'
        '\t\t\t\tbtnSupport.setRect(btnPlay.left(), btnPlay.bottom()+ GAP, btnPlay.width(), BTN_HEIGHT);\n'
        '\t\t\t}\n'
        '\t\t\tbtnRankings.setRect(btnPlay.left(),\n'
        '\t\t\t\t\t(btnSupport == null ? btnPlay.bottom() : btnSupport.bottom()) + GAP,\n'
        '\t\t\t\t\t(btnPlay.width()/2)-1, BTN_HEIGHT);',
        "portrait support layout",
    )
    title = replace_once(
        title,
        '\t\tbtnSupport.enable(alpha != 0);',
        '\t\tif (btnSupport != null) btnSupport.enable(alpha != 0);',
        "support fade interaction",
    )
    title = replace_once(
        title,
        '\t\tbtnSupport.alpha(alpha);',
        '\t\tif (btnSupport != null) btnSupport.alpha(alpha);',
        "support fade opacity",
    )

    key = replace_once(
        key,
        'if(!SPDSettings.supportNagged()){',
        'if (' + SUPPORT_FLAG + ' && !SPDSettings.supportNagged()){',
        "worn key support prompt",
    )

    victory = replace_once(
        victory,
        "\t\tRedButton btnSupport = new RedButton(Messages.get(this, \"support\")) {\n"
        "\t\t\t@Override\n"
        "\t\t\tprotected void onClick() {\n"
        "\t\t\t\tShatteredPixelDungeon.switchScene(SupporterScene.class);\n"
        "\t\t\t}\n"
        "\t\t};\n"
        "\t\tbtnSupport.icon(Icons.GOLD.get());\n"
        "\t\tbtnSupport.setRect(0, height, width / 2, 18);\n"
        "\t\tadd(btnSupport);",
        '\t\tif (' + SUPPORT_FLAG + ') {\n'
        + "\t\t\tRedButton btnSupport = new RedButton(Messages.get(this, \"support\")) {\n"
        "\t\t\t\t@Override\n"
        "\t\t\t\tprotected void onClick() {\n"
        "\t\t\t\t\tShatteredPixelDungeon.switchScene(SupporterScene.class);\n"
        "\t\t\t\t}\n"
        "\t\t\t};\n"
        "\t\t\tbtnSupport.icon(Icons.GOLD.get());\n"
        "\t\t\tbtnSupport.setRect(0, height, width / 2, 18);\n"
        "\t\t\tadd(btnSupport);"
        + '\n\t\t}',
        "victory support button creation",
    )
    victory = replace_once(
        victory,
        '\t\tbtnClose.setRect(btnSupport.right() + 1, height, width / 2 - 1, 18);',
        '\t\tbtnClose.setRect(' + SUPPORT_FLAG + ' ? width / 2 + 1 : 0, height,\n'
        '\t\t\t\t' + SUPPORT_FLAG + ' ? width / 2 - 1 : width, 18);',
        "victory close button layout",
    )

    title_path.write_text(title, encoding="utf-8")
    key_path.write_text(key, encoding="utf-8")
    victory_path.write_text(victory, encoding="utf-8")
    print("patched TitleScene, WornKey, and WndVictoryCongrats: Shattered support UI guarded by " + SUPPORT_FLAG)


if __name__ == "__main__":
    main()
