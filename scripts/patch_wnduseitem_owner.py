#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_wnduseitem_owner.py <WndUseItem.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

old = """		float y = height;
		
		if (Dungeon.hero.isAlive() && Dungeon.hero.belongings.contains(item)) {
			y += GAP;
			ArrayList<RedButton> buttons = new ArrayList<>();
			for (final String action : item.actions(Dungeon.hero)) {

				RedButton btn = new RedButton(item.actionName(action, Dungeon.hero), 8) {
					@Override
					protected void onClick() {
						hide();
						if (owner != null && owner.parent != null) owner.hide();
						if (Dungeon.hero.isAlive() && Dungeon.hero.belongings.contains(item)) {
							item.execute(Dungeon.hero, action);
						}
"""

new = """		float y = height;
		final com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero itemUser =
				com.spd.cohero.CoHero.itemOwner(item);
		
		if (itemUser != null && itemUser.isAlive() && itemUser.belongings.contains(item)) {
			y += GAP;
			ArrayList<RedButton> buttons = new ArrayList<>();
			for (final String action : com.spd.cohero.CoHero.itemActions(item, itemUser)) {

				RedButton btn = new RedButton(item.actionName(action, itemUser), 8) {
					@Override
					protected void onClick() {
						hide();
						if (owner != null && owner.parent != null) owner.hide();
						if (itemUser.isAlive() && itemUser.belongings.contains(item)) {
							item.execute(itemUser, action);
						}
"""

if new in text:
    raise SystemExit("CoHero WndUseItem owner hook is already present")
count = text.count(old)
if count != 1:
    raise SystemExit(f"expected exactly one WndUseItem owner anchor, found {count}")

path.write_text(text.replace(old, new, 1), encoding="utf-8")
print(f"patched {path}")
