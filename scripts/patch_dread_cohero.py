#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_dread_cohero.py <Dread.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

def replace_once(old, new, label):
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one {label} anchor, found {count}")
    text = text.replace(old, new, 1)

if "private Char dreadSource()" in text:
    raise SystemExit("CoHero Dread ownership hook is already present")

replace_once(
"""import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
""",
"""import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
""",
"Dread Actor import")

replace_once(
"""	@Override
	public boolean act() {

		if (!Dungeon.level.heroFOV[target.pos]
				&& Dungeon.level.distance(target.pos, Dungeon.hero.pos) >= 6) {
			if (target instanceof Mob){
				((Mob) target).EXP /= 2;
			}
			target.destroy();
			target.sprite.killAndErase();
			Dungeon.level.mobs.remove(target);
		} else {
			left--;
			if (left <= 0){
				detach();
			}
		}

		spend(TICK);
		return true;
	}
""",
"""	private Char dreadSource() {
		Actor actor = object == 0 ? null : Actor.findById(object);
		if (actor instanceof Char && ((Char) actor).isAlive()) {
			return (Char) actor;
		}
		return Dungeon.hero;
	}

	private boolean sourceCanSeeTarget(Char source) {
		if (source == null || Dungeon.level == null) {
			return false;
		}

		if (source == Dungeon.hero) {
			return Dungeon.level.heroFOV != null
					&& target.pos >= 0
					&& target.pos < Dungeon.level.heroFOV.length
					&& Dungeon.level.heroFOV[target.pos];
		}

		if (source.fieldOfView == null
				|| source.fieldOfView.length != Dungeon.level.length()) {
			source.fieldOfView = new boolean[Dungeon.level.length()];
		}
		Dungeon.level.updateFieldOfView(source, source.fieldOfView);
		return target.pos >= 0
				&& target.pos < source.fieldOfView.length
				&& source.fieldOfView[target.pos];
	}

	@Override
	public boolean act() {

		Char source = dreadSource();
		boolean sourceSeesTarget = sourceCanSeeTarget(source);
		int sourceDistance = source == null
				? Integer.MAX_VALUE
				: Dungeon.level.distance(target.pos, source.pos);

		if (!sourceSeesTarget && sourceDistance >= 6) {
			if (target instanceof Mob){
				((Mob) target).EXP /= 2;
			}
			target.destroy();
			target.sprite.killAndErase();
			Dungeon.level.mobs.remove(target);
		} else {
			left--;
			if (left <= 0){
				detach();
			}
		}

		spend(TICK);
		return true;
	}
""",
"Dread owner-aware act")

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
