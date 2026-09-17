#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_wndbag_owner.py <WndBag.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

replacements = [
    (
        "for (Bag b : Dungeon.hero.belongings.getBags()) {",
        "for (Bag b : com.spd.cohero.CoHero.noteBagOwner(bag).belongings.getBags()) {",
        "WndBag tab owner",
        1,
    ),
    (
        """	public static WndBag lastBag(ItemSelector selector ) {
		
		if (lastBag != null && Dungeon.hero.belongings.backpack.contains( lastBag )) {
			
			return new WndBag( lastBag, selector );
			
		} else {
			
			return new WndBag( Dungeon.hero.belongings.backpack, selector );
			
		}
	}
""",
        """	public static WndBag lastBag(ItemSelector selector ) {
		Hero owner = com.spd.cohero.CoHero.inventoryContextHero();
		if (owner == null) {
			throw new IllegalStateException("WndBag requested without an inventory Hero");
		}

		if (lastBag != null && owner.belongings.backpack.contains( lastBag )
				&& lastBag.owner == owner) {
			return new WndBag( lastBag, selector );
		} else {
			return new WndBag( owner.belongings.backpack, selector );
		}
	}
""",
        "WndBag lastBag owner",
        1,
    ),
    (
        """	public static WndBag getBag( ItemSelector selector ) {
		if (selector.preferredBag() == Belongings.Backpack.class){
			return new WndBag( Dungeon.hero.belongings.backpack, selector );

		} else if (selector.preferredBag() != null){
			Bag bag = Dungeon.hero.belongings.getItem( selector.preferredBag() );
			if (bag != null)    return new WndBag( bag, selector );
			//if a specific preferred bag isn't present, then the relevant items will be in backpack
			else                return new WndBag( Dungeon.hero.belongings.backpack, selector );
		}

		return lastBag( selector );
	}
""",
        """	public static WndBag getBag( ItemSelector selector ) {
		Hero owner = com.spd.cohero.CoHero.inventoryContextHero();
		if (owner == null) {
			throw new IllegalStateException("WndBag selector requested without an inventory Hero");
		}

		if (selector.preferredBag() == Belongings.Backpack.class){
			return new WndBag( owner.belongings.backpack, selector );

		} else if (selector.preferredBag() != null){
			Bag bag = owner.belongings.getItem( selector.preferredBag() );
			if (bag != null)    return new WndBag( bag, selector );
			//if a specific preferred bag isn't present, then the relevant items will be in backpack
			else                return new WndBag( owner.belongings.backpack, selector );
		}

		return lastBag( selector );
	}
""",
        "WndBag selector owner",
        1,
    ),
    (
        "Belongings stuff = Dungeon.hero.belongings;",
        "Belongings stuff = com.spd.cohero.CoHero.noteBagOwner(container).belongings;",
        "WndBag equipped owner",
        1,
    ),
    (
        "if (container != Dungeon.hero.belongings.backpack){",
        "if (container != com.spd.cohero.CoHero.bagOwner(container).belongings.backpack){",
        "WndBag root owner",
        1,
    ),
    (
        "!item.isEquipped(Dungeon.hero)",
        "!item.isEquipped(com.spd.cohero.CoHero.bagOwner(lastBag))",
        "WndBag equipped item owner",
        2,
    ),
]

for old, new, label, expected in replacements:
    if new in text:
        raise SystemExit(f"CoHero {label} hook is already present")
    count = text.count(old)
    if count != expected:
        raise SystemExit(f"expected {expected} {label} anchor(s), found {count}")
    text = text.replace(old, new)

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
