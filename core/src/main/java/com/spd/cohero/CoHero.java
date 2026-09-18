package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.HeroSelectScene;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.noosa.Game;
import com.watabou.utils.Bundle;
import com.watabou.utils.GameSettings;
import com.watabou.utils.PathFinder;

import java.util.ArrayList;
import java.util.Collection;

public final class CoHero {

    public static final String VERSION = "0.0.1-dev";

    private static final String COMPANION_CLASS_KEY_PREFIX = "cohero_companion_class_slot_";
    private static final String SAVE_COMPANION_CLASS = "cohero_companion_class";
    private static final String SAVE_COMPANION_STATE = "cohero_companion_state";

    private static HeroClass playerSelection;
    private static HeroClass companionSelection;
    private static Bundle companionState;
    private static boolean selectingCompanion;
    private static boolean openingCompanionSelection;
    private static boolean companionDeathEndedRun;

    private CoHero() {
    }

    public static void onHeroSelectSceneCreated() {
        if (openingCompanionSelection) {
            openingCompanionSelection = false;
            return;
        }

        playerSelection = null;
        companionSelection = null;
        companionState = null;
        selectingCompanion = false;
        companionDeathEndedRun = false;
    }

    public static boolean onHeroSelectionConfirmed(HeroClass selectedClass) {
        if (selectedClass == null) {
            throw new IllegalArgumentException("selectedClass must not be null");
        }

        if (!selectingCompanion) {
            playerSelection = selectedClass;
            companionState = null;
            companionDeathEndedRun = false;
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
        return selectingCompanion
                ? CoHeroMessages.get("hero_select.companion")
                : CoHeroMessages.get("hero_select.player");
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

    public static void storeGame(Bundle bundle) {
        if (bundle == null) {
            throw new IllegalArgumentException("bundle must not be null");
        }

        HeroClass heroClass = companionClass();
        if (heroClass == null) {
            throw new IllegalStateException("CoHero run has no selected companion class");
        }
        bundle.put(SAVE_COMPANION_CLASS, heroClass.name());

        CoHeroAlly companion = findCompanion();
        if (companion != null && companion.isAlive()) {
            Bundle state = new Bundle();
            companion.storeInBundle(state);
            companionState = state;
        }

        if (companionState != null) {
            bundle.put(SAVE_COMPANION_STATE, companionState);
        }
    }

    public static void restoreGame(Bundle bundle) {
        if (bundle == null) {
            throw new IllegalArgumentException("bundle must not be null");
        }
        if (!bundle.contains(SAVE_COMPANION_CLASS)) {
            throw new IllegalStateException("Save is missing CoHero companion class");
        }

        String storedClass = bundle.getString(SAVE_COMPANION_CLASS);
        try {
            companionSelection = HeroClass.valueOf(storedClass);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid CoHero companion class in save: " + storedClass, ex);
        }

        companionState = bundle.contains(SAVE_COMPANION_STATE)
                ? bundle.getBundle(SAVE_COMPANION_STATE)
                : null;
        playerSelection = null;
        selectingCompanion = false;
        openingCompanionSelection = false;
        companionDeathEndedRun = false;
    }

    public static void storeLevelMobs(Bundle bundle, String key, Collection<Mob> mobs) {
        ArrayList<Mob> storedMobs = new ArrayList<>();
        for (Mob mob : mobs) {
            if (!(mob instanceof CoHeroAlly)) {
                storedMobs.add(mob);
            }
        }
        bundle.put(key, storedMobs);
    }

    public static void onGameSceneReady() {
        if (Dungeon.hero == null || Dungeon.level == null) {
            return;
        }

        HeroClass heroClass = companionClass();
        if (heroClass == null) {
            throw new IllegalStateException("CoHero run has no selected companion class");
        }

        CoHeroAlly existing = findCompanion();
        if (existing != null) {
            CompanionLongPress.ensureInstalled();
            return;
        }

        int spawn = findSpawnCell();
        if (spawn == -1) {
            throw new IllegalStateException("CoHero could not find a spawn cell next to the hero");
        }

        CoHeroAlly companion = new CoHeroAlly();
        if (companionState != null) {
            companion.restoreFromBundle(companionState);
        } else {
            CompanionStartingEquipment.initialize(companion, heroClass);
        }
        companion.enterLevel(spawn);
        GameScene.add(companion);
        Dungeon.level.occupyCell(companion);
        CompanionLongPress.ensureInstalled();
    }

    public static boolean canUseTransition(LevelTransition transition) {
        if (transition == null || transition.type != LevelTransition.Type.REGULAR_EXIT) {
            return true;
        }

        CoHeroAlly companion = findCompanion();
        boolean ready = companion != null
                && companion.isAlive()
                && isAdjacentToTransition(companion.pos, transition);

        if (!ready) {
            GLog.i(CoHeroMessages.get("exit_required"));
        }
        return ready;
    }

    static boolean tryAutoExit(CoHeroAlly companion) {
        if (companion == null
                || !companion.isAlive()
                || Dungeon.hero == null
                || !Dungeon.hero.isAlive()
                || Dungeon.level == null
                || Dungeon.level.locked) {
            return false;
        }

        LevelTransition transition = Dungeon.level.getTransition(Dungeon.hero.pos);
        if (transition == null
                || transition.type != LevelTransition.Type.REGULAR_EXIT
                || !transition.inside(Dungeon.hero.pos)
                || !isAdjacentToTransition(companion.pos, transition)) {
            return false;
        }

        return Dungeon.level.activateTransition(Dungeon.hero, transition);
    }

    static boolean isAdjacentToTransition(int cell, LevelTransition transition) {
        if (transition == null || transition.inside(cell)) {
            return false;
        }

        for (int offset : PathFinder.NEIGHBOURS8) {
            int adjacent = cell + offset;
            if (adjacent >= 0
                    && adjacent < Dungeon.level.length()
                    && transition.inside(adjacent)) {
                return true;
            }
        }
        return false;
    }

    static void markCompanionDeathGameOver() {
        companionDeathEndedRun = true;
    }

    public static boolean companionDeathEndedRun() {
        return companionDeathEndedRun;
    }

    private static String companionClassKey() {
        return COMPANION_CLASS_KEY_PREFIX + GamesInProgress.curSlot;
    }

    static CoHeroAlly findCompanion() {
        if (Dungeon.level == null) {
            return null;
        }
        for (Mob mob : Dungeon.level.mobs) {
            if (mob instanceof CoHeroAlly) {
                return (CoHeroAlly) mob;
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
