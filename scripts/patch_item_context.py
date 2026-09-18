#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_item_context.py <Item.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

replacements = [
    (
        """	public ArrayList<String> actions( Hero hero ) {
		ArrayList<String> actions = new ArrayList<>();
		actions.add( AC_DROP );
		actions.add( AC_THROW );
		return actions;
	}
""",
        """	public ArrayList<String> actions( Hero hero ) {
		ArrayList<String> actions = new ArrayList<>();
		actions.add( AC_DROP );
		actions.add( AC_THROW );
		com.spd.cohero.CoHero.appendTransferAction(this, hero, actions);
		return actions;
	}
""",
        "Item transfer action",
        1,
    ),
    (
        """	public String actionName(String action, Hero hero){
		return Messages.get(this, "ac_" + action);
	}
""",
        """	public String actionName(String action, Hero hero){
		if (com.spd.cohero.CoHero.isTransferAction(action)) {
			return com.spd.cohero.CoHero.transferActionName(hero);
		}
		return Messages.get(this, "ac_" + action);
	}
""",
        "Item transfer action name",
        1,
    ),
    (
        """		curUser = hero;
		curItem = this;
		
""",
        """		curUser = hero;
		curItem = this;
		com.spd.cohero.CoHero.noteItemUser(hero);
		if (com.spd.cohero.CoHero.handleTransferAction(this, hero, action)) {
			return;
		}
		
""",
        "Item user context",
        1,
    ),
    (
        """		ArrayList<Item> items = container.items;
""",
        """		ArrayList<Item> items = container.items;
		Hero collectingHero = container.owner instanceof Hero ? (Hero)container.owner : Dungeon.hero;
""",
        "Item collection owner",
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

collection_replacements = [
    (
        "if (Dungeon.hero != null && Dungeon.hero.isAlive())",
        "if (collectingHero != null && collectingHero.isAlive())",
        2,
        "collection alive owner",
    ),
    (
        "Talent.onItemCollected(Dungeon.hero, item)",
        "Talent.onItemCollected(collectingHero, item)",
        1,
        "merged item collection talent",
    ),
    (
        "Talent.onItemCollected( Dungeon.hero, this )",
        "Talent.onItemCollected( collectingHero, this )",
        1,
        "item collection talent",
    ),
    (
        "if (!d.collect()){",
        "if (!d.collect(collectingHero.belongings.backpack)){",
        1,
        "lost dart collection bag owner",
    ),
    (
        "Dungeon.level.drop(d, Dungeon.hero.pos).sprite.drop()",
        "Dungeon.level.drop(d, collectingHero.pos).sprite.drop()",
        1,
        "lost dart collection owner",
    ),
]

for old, new, expected, label in collection_replacements:
    if new in text:
        raise SystemExit(f"CoHero {label} hook is already present")
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"expected {expected} {label} anchor(s), found {count}")
    text = text.replace(old, new)

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
