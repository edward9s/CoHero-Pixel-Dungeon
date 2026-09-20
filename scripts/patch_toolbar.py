#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_toolbar.py <Toolbar.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

field_marker = "\tprivate com.spd.cohero.CoHeroInventoryButton btnCoHeroInventory;"
create_marker = "\t\tadd(btnCoHeroInventory = new com.spd.cohero.CoHeroInventoryButton());"
layout_marker = "\t\tbtnCoHeroInventory.setRect(btnInventory.left() + 4, btnInventory.top() - 15, 16, 16);"

if field_marker in text or create_marker in text or layout_marker in text:
    raise SystemExit("CoHero Toolbar hooks are already present")

field_anchor = "\tprivate Tool btnInventory;\n"
if text.count(field_anchor) != 1:
    raise SystemExit(
        f"expected exactly one Toolbar inventory field anchor, found {text.count(field_anchor)}"
    )
text = text.replace(field_anchor, field_anchor + field_marker + "\n", 1)

create_anchor = "\t\tbtnInventory.icon( 160, 0, 16, 16 );\n"
if text.count(create_anchor) != 1:
    raise SystemExit(
        f"expected exactly one Toolbar inventory icon anchor, found {text.count(create_anchor)}"
    )
text = text.replace(create_anchor, create_anchor + "\n" + create_marker + "\n", 1)

large_return_anchor = (
    "\t\t\t//swap button never appears on larger interface sizes\n"
    "\n"
    "\t\t\treturn;\n"
)
if text.count(large_return_anchor) != 1:
    raise SystemExit(
        f"expected exactly one Toolbar large-interface return anchor, found {text.count(large_return_anchor)}"
    )
text = text.replace(
    large_return_anchor,
    "\t\t\t//swap button never appears on larger interface sizes\n"
    "\n"
    "\t\t\t" + layout_marker.strip() + "\n"
    "\n"
    "\t\t\treturn;\n",
    1,
)

layout_end_anchor = (
    "\t\tif (SPDSettings.flipToolbar()) {\n"
)
start = text.find(layout_end_anchor)
if start < 0:
    raise SystemExit("Toolbar flip-layout anchor not found")

end_anchor = "\n\t}\n\n\tpublic static void updateLayout(){"
end = text.find(end_anchor, start)
if end < 0:
    raise SystemExit("Toolbar layout end anchor not found")

layout_tail = text[start:end]
if layout_marker in layout_tail:
    raise SystemExit("CoHero inventory layout hook already present in Toolbar layout tail")

layout_tail += "\n\t\t" + layout_marker.strip() + "\n"
text = text[:start] + layout_tail + text[end:]

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
