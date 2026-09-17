package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.HeroSelectScene;
import com.watabou.noosa.Game;
import com.watabou.utils.GameSettings;
import com.watabou.utils.PathFinder;

public final class CoHero {

    public static final String VERSION = "0.0.1-dev";

    private static final String COMPANION_CLASS_KEY_PREFIX = "cohero_companion_class_slot_";

    private static HeroClass playerSelection;
    private static HeroClass companionSelection;
    private static boolean selectingCompanion;
    private static boolean openingCompanionSelection;

    private CoHero() {
    }

    public static void onHeroSelectSceneCreated() {
        if (openingCompanionSelection) {
            openingCompanionSelection = false;
            return;
        }

        playerSelection = null;
        companionSelection = null;
        selectingCompanion = false;
    }

    public static boolean onHeroSelectionConfirmed(HeroClass selectedClass) {
        if (selectedClass == null) {
            throw new IllegalArgumentException("selectedClass must not be null");
        }

        if (!selectingCompanion) {
            playerSelection = selectedClass;
            selectingCompanion = true;
            openingCompanionSelection = true;
            GamesInProgress.selectedClass = null;
            Game.switchScene(HeroSelectScene.class);
            return true;
        }

        if (playerSelection == null) {
            throw new IllegalStateException("CoHero companion selection has no player selection");
        }

        companionSelection = selectedClass;
        selectingCompanion = false;
        GamesInProgress.selectedClass = playerSelection;
        GameSettings.put(companionClassKey(), companionSelection.name());
        return false;
    }

    public static String heroSelectionTitle(String stockTitle) {
        return stockTitle + (selectingCompanion ? " 2/2" : " 1/2");
    }

    public static HeroClass companionClass() {
        if (companionSelection != null) {
            return companionSelection;
        }

        String stored = GameSettings.getString(companionClassKey(), "");
        if (stored.isEmpty()) {
            return null;
        }

        try {
            companionSelection = HeroClass.valueOf(stored);
            return companionSelection;
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid stored CoHero companion class: " + stored, ex);
        }
    }

    public static void onGameSceneReady() {
        if (Dungeon.hero == null || Dungeon.level == null) {
            return;
        }

        if (companionClass() == null) {
            throw new IllegalStateException("CoHero run has no selected companion class");
        }

        if (findCompanion() != null) {
            return;
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

    public static boolean canUseTransition(LevelTransition transition) {
        if (transition == null || transition.type != LevelTransition.Type.REGULAR_EXIT) {
            return true;
        }

        CompanionHero companion = findCompanion();
        return companion != null && companion.isAlive() && transition.inside(companion.pos);
    }

    private static String companionClassKey() {
        return COMPANION_CLASS_KEY_PREFIX + GamesInProgress.curSlot;
    }

    private static CompanionHero findCompanion() {
        if (Dungeon.level == null) {
            return null;
        }
        for (Mob mob : Dungeon.level.mobs) {
            if (mob instanceof CompanionHero) {
                return (CompanionHero) mob;
            }
        }
        return null;
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
