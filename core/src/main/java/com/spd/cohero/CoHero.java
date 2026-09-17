package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.shatteredpixel.shatteredpixeldungeon.messages.Languages;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
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
    }

    public static boolean onHeroSelectionConfirmed(HeroClass selectedClass) {
        if (selectedClass == null) {
            throw new IllegalArgumentException("selectedClass must not be null");
        }

        if (!selectingCompanion) {
            playerSelection = selectedClass;
            companionState = null;
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
        if (Messages.lang() == Languages.CHI_TRAD) {
            return selectingCompanion
                    ? "選擇你的夥伴英雄 — 2/2"
                    : "選擇你的英雄 — 1/2";
        }
        if (Messages.lang() == Languages.CHI_SMPL) {
            return selectingCompanion
                    ? "选择你的伙伴英雄 — 2/2"
                    : "选择你的英雄 — 1/2";
        }
        return selectingCompanion
                ? "Choose your companion hero — 2/2"
                : "Choose your hero — 1/2";
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

        CompanionHero companion = findCompanion();
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
    }

    public static void storeLevelMobs(Bundle bundle, String key, Collection<Mob> mobs) {
        ArrayList<Mob> storedMobs = new ArrayList<>();
        for (Mob mob : mobs) {
            if (!(mob instanceof CompanionHero)) {
                storedMobs.add(mob);
            }
        }
        bundle.put(key, storedMobs);
    }

    public static void onGameSceneReady() {
        if (Dungeon.hero == null || Dungeon.level == null) {
            return;
        }

        if (companionClass() == null) {
            throw new IllegalStateException("CoHero run has no selected companion class");
        }

        CompanionHero existing = findCompanion();
        if (existing != null) {
            CompanionLongPress.ensureInstalled();
            return;
        }

        int spawn = findSpawnCell();
        if (spawn == -1) {
            throw new IllegalStateException("CoHero could not find a spawn cell next to the hero");
        }

        CompanionHero companion = new CompanionHero();
        if (companionState != null) {
            companion.restoreFromBundle(companionState);
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

        CompanionHero companion = findCompanion();
        boolean ready = companion != null
                && companion.isAlive()
                && transition.inside(companion.pos);

        if (!ready) {
            GLog.i(companionMustReachExitMessage());
        }
        return ready;
    }

    private static String companionMustReachExitMessage() {
        if (Messages.lang() == Languages.CHI_TRAD) {
            return "你的夥伴英雄必須先抵達出口。";
        }
        if (Messages.lang() == Languages.CHI_SMPL) {
            return "你的伙伴英雄必须先抵达出口。";
        }
        return "Your companion hero must reach the exit first.";
    }

    private static String companionClassKey() {
        return COMPANION_CLASS_KEY_PREFIX + GamesInProgress.curSlot;
    }

    static CompanionHero findCompanion() {
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
