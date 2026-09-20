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
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;
import com.watabou.noosa.Game;
import com.watabou.utils.Bundle;
import com.watabou.utils.Callback;
import com.watabou.utils.GameSettings;
import com.watabou.utils.PathFinder;

import java.util.ArrayList;
import java.util.Collection;

public final class CoHero {

    public static final int TRANSITION_BLOCKED = 0;
    public static final int TRANSITION_STARTED = 1;
    public static final int TRANSITION_PROMPTED = 2;

    private static final String COMPANION_CLASS_KEY_PREFIX = "cohero_companion_class_slot_";
    private static final String SAVE_COHERO_VERSION = "cohero_version";
    private static final String SAVE_COMPANION_CLASS = "cohero_companion_class";
    private static final String SAVE_COMPANION_STATE = "cohero_companion_state";
    private static final String SAVE_COMPANION_ARMOR_TIER = "cohero_companion_armor_tier";
    private static final String SAVE_EXCLUDED_DEPTH = "cohero_excluded_depth";
    private static final String SAVE_EXCLUDED_BRANCH = "cohero_excluded_branch";

    private static HeroClass playerSelection;
    private static HeroClass companionSelection;
    private static Bundle companionState;
    private static int companionPreviewArmorTier;
    private static boolean selectingCompanion;
    private static boolean openingCompanionSelection;
    private static boolean companionDeathEndedRun;
    private static boolean restoringSavedGame;
    private static int excludedDepth = -1;
    private static int excludedBranch = -1;

    private CoHero() {
    }

    public static String version() {
        return CoHeroVersion.version();
    }

    public static void onHeroSelectSceneCreated() {
        if (openingCompanionSelection) {
            openingCompanionSelection = false;
            return;
        }

        playerSelection = null;
        companionSelection = null;
        companionState = null;
        companionPreviewArmorTier = 0;
        selectingCompanion = false;
        companionDeathEndedRun = false;
        restoringSavedGame = false;
        excludedDepth = -1;
        excludedBranch = -1;
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

    public static void onHeroGainIdentificationExp(float levelPercent) {
        if (levelPercent <= 0f) {
            return;
        }
        CoHeroAlly companion = findCompanion();
        if (companion != null && companion.isAlive()) {
            companion.inventory().gainIdentificationExp(levelPercent);
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
        bundle.put(SAVE_COHERO_VERSION, CoHeroVersion.version());
        bundle.put(SAVE_COMPANION_CLASS, heroClass.name());

        captureCompanionState();

        if (companionState != null) {
            bundle.put(SAVE_COMPANION_STATE, companionState);
        }
        bundle.put(SAVE_COMPANION_ARMOR_TIER, companionPreviewArmorTier);
        if (excludedDepth >= 0 && excludedBranch >= 0) {
            bundle.put(SAVE_EXCLUDED_DEPTH, excludedDepth);
            bundle.put(SAVE_EXCLUDED_BRANCH, excludedBranch);
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
        companionPreviewArmorTier = bundle.contains(SAVE_COMPANION_ARMOR_TIER)
                ? bundle.getInt(SAVE_COMPANION_ARMOR_TIER)
                : 0;
        excludedDepth = bundle.contains(SAVE_EXCLUDED_DEPTH)
                ? bundle.getInt(SAVE_EXCLUDED_DEPTH)
                : -1;
        excludedBranch = bundle.contains(SAVE_EXCLUDED_BRANCH)
                ? bundle.getInt(SAVE_EXCLUDED_BRANCH)
                : -1;
        playerSelection = null;
        selectingCompanion = false;
        openingCompanionSelection = false;
        companionDeathEndedRun = false;
        restoringSavedGame = true;
    }

    public static void populateGameInfo(GamesInProgress.Info info) {
        if (info == null) {
            return;
        }
        info.companionClass = companionClass();
        CoHeroAlly companion = findCompanion();
        if (companion != null && companion.isAlive()) {
            companionPreviewArmorTier = companion.armorTier();
        }
        info.companionArmorTier = companionPreviewArmorTier;
    }

    public static void previewGameInfo(GamesInProgress.Info info, Bundle bundle) {
        if (info == null || bundle == null || !bundle.contains(SAVE_COMPANION_CLASS)) {
            return;
        }

        try {
            info.companionClass = HeroClass.valueOf(bundle.getString(SAVE_COMPANION_CLASS));
        } catch (IllegalArgumentException ex) {
            info.companionClass = null;
        }
        info.companionArmorTier = bundle.contains(SAVE_COMPANION_ARMOR_TIER)
                ? bundle.getInt(SAVE_COMPANION_ARMOR_TIER)
                : 0;
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

        if (isCompanionExcludedFromCurrentFloor()) {
            // A save made while the companion is intentionally outside this special floor must
            // continue to keep it outside after reload.
            restoringSavedGame = false;
            return;
        }

        boolean rejoiningAfterExcludedFloor = excludedDepth >= 0 && excludedBranch >= 0;
        if (rejoiningAfterExcludedFloor) {
            clearExcludedFloor();
        }

        CoHeroAlly existing = findCompanion();
        if (existing != null) {
            CompanionLongPress.ensureInstalled();
            restoringSavedGame = false;
            return;
        }

        CoHeroAlly companion = new CoHeroAlly();
        int spawn;

        if (companionState != null) {
            companion.restoreFromBundle(companionState);

            if (restoringSavedGame && !rejoiningAfterExcludedFloor) {
                spawn = companion.pos;
                if (spawn < 0
                        || spawn >= Dungeon.level.length()
                        || !Dungeon.level.passable[spawn]
                        || (Actor.findChar(spawn) != null && Actor.findChar(spawn) != companion)) {
                    throw new IllegalStateException("Saved CoHero position is not valid on the restored level: " + spawn);
                }
            } else {
                spawn = findSpawnCell();
            }
        } else {
            CompanionStartingEquipment.initialize(companion, heroClass);
            spawn = findSpawnCell();
        }

        restoringSavedGame = false;

        if (spawn == -1) {
            throw new IllegalStateException("CoHero could not find a spawn cell next to the hero");
        }

        companion.enterLevel(spawn);
        GameScene.add(companion);
        Dungeon.level.occupyCell(companion);
        CompanionLongPress.ensureInstalled();
    }

    public static int requestTransition(Hero hero, LevelTransition transition) {
        if (hero == null || transition == null || Dungeon.level == null) {
            return TRANSITION_BLOCKED;
        }

        // While CoHero is intentionally outside this special floor, leaving the floor must not
        // require an actor that is deliberately absent.
        if (isCompanionExcludedFromCurrentFloor()) {
            return Dungeon.level.activateTransition(hero, transition)
                    ? TRANSITION_STARTED
                    : TRANSITION_BLOCKED;
        }

        CoHeroAlly companion = findCompanion();
        if (companion == null || !companion.isAlive()) {
            GLog.i(CoHeroMessages.get("companion_unavailable"));
            return TRANSITION_BLOCKED;
        }

        if (offersCompanionChoice(transition)) {
            showCompanionTransitionChoice(hero, transition);
            return TRANSITION_PROMPTED;
        }

        captureCompanionState();
        return Dungeon.level.activateTransition(hero, transition)
                ? TRANSITION_STARTED
                : TRANSITION_BLOCKED;
    }

    private static boolean offersCompanionChoice(LevelTransition transition) {
        boolean bossEntry = transition.type == LevelTransition.Type.REGULAR_EXIT
                && transition.destBranch == 0
                && Dungeon.bossLevel(transition.destDepth);
        boolean branchEntry = transition.type == LevelTransition.Type.BRANCH_EXIT
                && transition.destBranch != Dungeon.branch;
        return bossEntry || branchEntry;
    }

    private static void showCompanionTransitionChoice(
            Hero hero, LevelTransition transition) {
        boolean branchEntry = transition.type == LevelTransition.Type.BRANCH_EXIT;
        final String finalMessage = CoHeroMessages.get(
                branchEntry ? "transition.branch_message" : "transition.boss_message");
        Game.runOnRenderThread(new Callback() {
            @Override
            public void call() {
                GameScene.show(new WndOptions(
                        CoHeroMessages.get("transition.title"),
                        finalMessage,
                        CoHeroMessages.get("transition.together"),
                        CoHeroMessages.get("transition.leave")) {

                    @Override
                    protected void onSelect(int index) {
                        if (index == 0) {
                            captureCompanionState();
                            clearExcludedFloor();
                        } else if (index == 1) {
                            captureCompanionState();
                            excludedDepth = transition.destDepth;
                            excludedBranch = transition.destBranch;
                        } else {
                            return;
                        }
                        Dungeon.level.activateTransition(hero, transition);
                    }
                });
            }
        });
    }

    private static void captureCompanionState() {
        CoHeroAlly companion = findCompanion();
        if (companion == null || !companion.isAlive()) {
            return;
        }
        Bundle state = new Bundle();
        companion.storeInBundle(state);
        companionState = state;
        companionPreviewArmorTier = companion.armorTier();
    }

    private static boolean isCompanionExcludedFromCurrentFloor() {
        return Dungeon.level != null
                && excludedDepth == Dungeon.depth
                && excludedBranch == Dungeon.branch;
    }

    private static void clearExcludedFloor() {
        excludedDepth = -1;
        excludedBranch = -1;
    }

    static void markCompanionDeathGameOver() {
        companionDeathEndedRun = true;
    }

    public static boolean companionDeathEndedRun() {
        return companionDeathEndedRun;
    }

    /**
     * Merges the live CoHero FOV into the shared party FOV.
     *
     * Level.updateFieldOfView calls this while recomputing Dungeon.level.heroFOV for the Hero.
     * The companion FOV is refreshed first so moving doors, blindness, Light, special-floor
     * view distance and other upstream vision rules are reflected immediately.
     */
    public static void mergeCompanionFieldOfView(boolean[] sharedFieldOfView) {
        if (Dungeon.level == null || sharedFieldOfView == null) {
            return;
        }
        if (sharedFieldOfView.length != Dungeon.level.length()) {
            throw new IllegalArgumentException("sharedFieldOfView length does not match current level");
        }

        CoHeroAlly companion = findCompanion();
        if (companion == null || !companion.isAlive()) {
            return;
        }

        if (companion.fieldOfView == null
                || companion.fieldOfView.length != Dungeon.level.length()) {
            companion.fieldOfView = new boolean[Dungeon.level.length()];
        }

        Dungeon.level.updateFieldOfView(companion, companion.fieldOfView);

        for (int i = 0; i < sharedFieldOfView.length; i++) {
            sharedFieldOfView[i] |= companion.fieldOfView[i];
        }
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
