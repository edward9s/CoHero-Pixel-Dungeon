package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.watabou.utils.PathFinder;

/**
 * Stable entry point for CoHero-owned code.
 *
 * CoHero source lives outside the upstream Shattered Pixel Dungeon package so
 * upstream code can remain untouched except for explicit integration seams.
 */
public final class CoHero {

    public static final String VERSION = "0.0.1-dev";

    private CoHero() {
    }

    /**
     * Called once GameScene has created its mob layer and linked existing mobs.
     * Ensures exactly one companion exists on the current level.
     */
    public static void onGameSceneReady() {
        if (Dungeon.hero == null || Dungeon.level == null) {
            return;
        }

        for (Mob mob : Dungeon.level.mobs) {
            if (mob instanceof CompanionHero) {
                return;
            }
        }

        int spawn = findSpawnCell();
        if (spawn == -1) {
            throw new IllegalStateException("CoHero could not find a spawn cell next to the hero");
        }

        CompanionHero companion = new CompanionHero();
        companion.pos = spawn;
        GameScene.add(companion);
        Dungeon.level.occupyCell(companion);
    }

    private static int findSpawnCell() {
        int heroPos = Dungeon.hero.pos;
        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = heroPos + offset;
            if (cell >= 0
                    && cell < Dungeon.level.length()
                    && Dungeon.level.passable[cell]
                    && Actor.findChar(cell) == null) {
                return cell;
            }
        }
        return -1;
    }
}
