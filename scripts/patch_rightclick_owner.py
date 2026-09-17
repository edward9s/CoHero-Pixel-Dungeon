#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_rightclick_owner.py <RightClickMenu.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

old_field = """	private Item item;

	//if a window is made from this right click menu, it gets this offset
"""
new_field = """	private Item item;
	private com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero itemUser;

	//if a window is made from this right click menu, it gets this offset
"""

old_ctor = """	public RightClickMenu(Item item){
		ArrayList<String> actions = item.actions(Dungeon.hero);
"""
new_ctor = """	public RightClickMenu(Item item){
		itemUser = com.spd.cohero.CoHero.itemOwner(item);
		ArrayList<String> actions = com.spd.cohero.CoHero.itemActions(item, itemUser);
"""

replacements = [
    (old_field, new_field, "right-click item user field", 1),
    (old_ctor, new_ctor, "right-click item actions", 1),
    (
        "if (item != null && Dungeon.hero.belongings.contains(item)){",
        "if (item != null && itemUser != null && itemUser.belongings.contains(item)){",
        "right-click ownership check",
        1,
    ),
    (
        "item.execute(Dungeon.hero, options[finalI]);",
        "item.execute(itemUser, options[finalI]);",
        "right-click execution owner",
        1,
    ),
    (
        "buttons[i].text(item.actionName(options[i], Dungeon.hero));",
        "buttons[i].text(item.actionName(options[i], itemUser));",
        "right-click action-name owner",
        1,
    ),
]

for old, new, label, expected in replacements:
    if new in text:
        raise SystemExit(f"CoHero {label} hook is already present")
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"expected {expected} {label} anchor(s), found {count}")
    text = text.replace(old, new, expected)

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
