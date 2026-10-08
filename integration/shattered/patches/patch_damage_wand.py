#!/usr/bin/env python3
from pathlib import Path
import sys

from java_patch import replace_code_once as replace_once

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_damage_wand.py <DamageWand.java>")

path = Path(sys.argv[1])
damage = path.read_text(encoding="utf-8")


damage_old = """	public int damageRoll(int lvl){
		int dmg = Hero.heroDamageIntRange(min(lvl), max(lvl));
		WandEmpower emp = Dungeon.hero.buff(WandEmpower.class);
		if (emp != null){
			dmg += emp.dmgBoost;
			emp.left--;
			if (emp.left <= 0) {
				emp.detach();
			}
			Sample.INSTANCE.play(Assets.Sounds.HIT_STRONG, 0.75f, 1.2f);
		}
		return dmg;
	}
"""
damage_new = """	public int damageRoll(int lvl){
		boolean heroCast = zapUser() == Dungeon.hero;
		int dmg = heroCast
				? Hero.heroDamageIntRange(min(lvl), max(lvl))
				: Random.NormalIntRange(min(lvl), max(lvl));
		if (heroCast) {
			WandEmpower emp = Dungeon.hero.buff(WandEmpower.class);
			if (emp != null){
				dmg += emp.dmgBoost;
				emp.left--;
				if (emp.left <= 0) {
					emp.detach();
				}
				Sample.INSTANCE.play(Assets.Sounds.HIT_STRONG, 0.75f, 1.2f);
			}
		}
		return dmg;
	}
"""
damage = replace_once(damage, damage_old, damage_new, "DamageWand damageRoll")
damage = replace_once(
    damage,
    "import com.watabou.noosa.audio.Sample;\n",
    "import com.watabou.noosa.audio.Sample;\nimport com.watabou.utils.Random;\n",
    "DamageWand Sample import",
)


path.write_text(damage, encoding="utf-8")
print(f"patched {path}")
