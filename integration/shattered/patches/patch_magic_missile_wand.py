#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_magic_missile_wand.py <WandOfMagicMissile.java>")

path = Path(sys.argv[1])
magic = path.read_text(encoding="utf-8")

def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one {label} anchor")
    return text.replace(old, new, 1)

magic_old = """			//apply the magic charge buff if we have another wand in inventory of a lower level, or already have the buff
			for (Wand.Charger wandCharger : curUser.buffs(Wand.Charger.class)){
				if (wandCharger.wand().buffedLvl() < buffedLvl() || curUser.buff(MagicCharge.class) != null){
					Buff.prolong(curUser, MagicCharge.class, MagicCharge.DURATION).setup(this);
					break;
				}
			}
"""
magic_new = """			//apply the magic charge buff if we have another wand in inventory of a lower level, or already have the buff
			Char user = zapUser();
			for (Wand.Charger wandCharger : user.buffs(Wand.Charger.class)){
				if (wandCharger.wand().buffedLvl() < buffedLvl() || user.buff(MagicCharge.class) != null){
					Buff.prolong(user, MagicCharge.class, MagicCharge.DURATION).setup(this);
					break;
				}
			}
"""
magic = replace_once(magic, magic_old, magic_new, "MagicMissile user")


path.write_text(magic, encoding="utf-8")
print(f"patched {path}")
