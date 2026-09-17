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
    ),
]

for old, new, label in replacements:
    if new in text:
        raise SystemExit(f"CoHero {label} hook is already present")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one {label} anchor, found {count}")
    text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
