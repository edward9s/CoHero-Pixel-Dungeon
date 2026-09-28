#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_danger_indicator.py <DangerIndicator.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

helper_anchor = """	private int lastNumber = -1;

	public static int HEIGHT = 16;
"""

helper_replacement = """	private int lastNumber = -1;

	private boolean currentlyHeroVisible(Mob mob) {
		return mob != null
				&& mob.isAlive()
				&& mob.isActive()
				&& com.spd.cohero.CoHero.heroCanSee(mob.pos);
	}

	private int currentHeroVisibleEnemyCount() {
		int result = 0;
		int cachedCount = Dungeon.hero.visibleEnemies();
		for (int i = 0; i < cachedCount; i++) {
			if (currentlyHeroVisible(Dungeon.hero.visibleEnemy(i))) {
				result++;
			}
		}
		return result;
	}

	private java.util.ArrayList<Mob> currentHeroVisibleEnemySnapshot() {
		java.util.ArrayList<Mob> result = new java.util.ArrayList<>();
		for (Mob mob : Dungeon.hero.getVisibleEnemies()) {
			if (currentlyHeroVisible(mob)) {
				result.add(mob);
			}
		}
		return result;
	}

	public static int HEIGHT = 16;
"""

update_old = """		if (Dungeon.hero.isAlive()) {
			int v =  Dungeon.hero.visibleEnemies();
"""
update_new = """		if (Dungeon.hero.isAlive()) {
			int v = currentHeroVisibleEnemyCount();
"""

click_old = """		if (Dungeon.hero.visibleEnemies() > 0) {

			Mob target = Dungeon.hero.visibleEnemy(++enemyIndex);

			QuickSlotButton.target(target);
"""
click_new = """		java.util.ArrayList<Mob> visibleEnemies = currentHeroVisibleEnemySnapshot();
		if (!visibleEnemies.isEmpty()) {

			Mob target = visibleEnemies.get(++enemyIndex % visibleEnemies.size());

			QuickSlotButton.target(target);
"""

marker = "currentHeroVisibleEnemyCount()"
if marker in text:
    raise SystemExit("CoHero DangerIndicator visibility filter is already present")

for name, old in (
    ("helper", helper_anchor),
    ("update", update_old),
    ("click", click_old),
):
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one DangerIndicator {name} anchor, found {text.count(old)}")

text = text.replace(helper_anchor, helper_replacement, 1)
text = text.replace(update_old, update_new, 1)
text = text.replace(click_old, click_new, 1)
path.write_text(text, encoding="utf-8")
print(f"patched {path}")
