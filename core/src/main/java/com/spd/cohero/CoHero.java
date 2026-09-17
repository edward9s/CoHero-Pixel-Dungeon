package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.effects.FloatingText;
import com.shatteredpixel.shatteredpixeldungeon.items.EquipableItem;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.Chasm;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.HeroSelectScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.noosa.Game;
import com.watabou.utils.Bundle;
import com.watabou.utils.GameSettings;
import com.watabou.utils.PathFinder;

import java.util.ArrayList;

public final class CoHero {

    public static final String VERSION = "0.0.1-dev";
    public static final String TRANSFER_ACTION = "COHERO_TRANSFER";

    private static final String COMPANION_CLASS_KEY_PREFIX = "cohero_companion_class_slot_";
    private static final String SAVE_COMPANION_CLASS = "cohero_companion_class";
    private static final String SAVE_COMPANION_STATE = "cohero_companion_state";

    private static HeroClass playerSelection;
    private static HeroClass companionSelection;
    private static Bundle companionState;
    private static boolean selectingCompanion;
    private static boolean openingCompanionSelection;
    private static boolean companionDeathEndedRun;

    // Item/selector UIs in SPD assume Dungeon.hero. This context is set centrally by Item.execute
    // and by owner-aware WndBag construction so nested selectors stay on the correct Hero.
    private static Hero inventoryContextHero;

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
        inventoryContextHero = null;
    }

    public static boolean onHeroSelectionConfirmed(HeroClass selectedClass) {
        if (selectedClass == null) {
            throw new IllegalArgumentException("selectedClass must not be null");
        }

        if (!selectingCompanion) {
            playerSelection = selectedClass;
            companionState = null;
            companionDeathEndedRun = false;
            inventoryContextHero = null;
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
        companionDeathEndedRun = false;
        inventoryContextHero = null;
    }

    public static void onGameSceneReady() {
        if (Dungeon.hero == null || Dungeon.level == null) {
            return;
        }

        HeroClass heroClass = companionClass();
        if (heroClass == null) {
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
        } else {
            CompanionStartingEquipment.initialize(companion, heroClass);
        }

        companion.enterLevel(spawn);
        GameScene.addCoHero(companion);
        Dungeon.level.occupyCell(companion);
        CompanionLongPress.ensureInstalled();
    }

    /** Returns the level used by stock Mob EXP eligibility for the character credited with the kill. */
    public static int killLevel(Object cause) {
        if (cause instanceof CompanionHero) {
            return ((CompanionHero) cause).lvl;
        }
        if (Dungeon.hero == null) {
            throw new IllegalStateException("Enemy EXP requested without Dungeon.hero");
        }
        return Dungeon.hero.lvl;
    }

    /** Mirrors stock Mob EXP gating, but uses the credited killer's independent level. */
    public static int killExp(Mob enemy, Object cause) {
        if (enemy == null) {
            throw new IllegalArgumentException("enemy must not be null");
        }
        return killLevel(cause) <= enemy.maxLvl ? enemy.EXP : 0;
    }

    public static void awardKillExp(Mob enemy, Object cause, int exp) {
        if (enemy == null) {
            throw new IllegalArgumentException("enemy must not be null");
        }
        if (exp < 0) {
            throw new IllegalArgumentException("exp must not be negative");
        }

        Hero recipient = cause instanceof CompanionHero ? (CompanionHero) cause : Dungeon.hero;
        if (recipient == null) {
            throw new IllegalStateException("Enemy EXP requested without a Hero recipient");
        }

        if (exp > 0 && recipient.sprite != null) {
            recipient.sprite.showStatusWithIcon(
                    CharSprite.POSITIVE,
                    Integer.toString(exp),
                    FloatingText.EXPERIENCE);
        }
        recipient.earnExp(exp, enemy.getClass());
    }

    /** Adds the autonomous Hero to ordinary enemy target candidates. */
    public static CompanionHero companionForEnemyTargeting() {
        CompanionHero companion = findCompanion();
        return companion != null && companion.isAlive() ? companion : null;
    }

    /** A companion falling into a chasm ends the run; it never performs an independent floor change. */
    public static boolean handleCompanionChasm(Char ch) {
        if (!(ch instanceof CompanionHero)) {
            return false;
        }
        CompanionHero companion = (CompanionHero) ch;
        if (companion.isAlive()) {
            companion.die(Chasm.class);
        }
        return true;
    }

    public static boolean canUseTransition(LevelTransition transition) {
        if (transition == null || transition.type != LevelTransition.Type.REGULAR_EXIT) {
            return true;
        }

        CompanionHero companion = findCompanion();
        boolean ready = companion != null
                && companion.isAlive()
                && isAdjacentToTransition(companion.pos, transition);

        if (!ready) {
            GLog.i(CoHeroMessages.get("exit_required"));
        }
        return ready;
    }

    static boolean tryAutoExit(CompanionHero companion) {
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

    /**
     * Central item-use context. All stock Item.execute(Hero, ...) calls pass through this seam.
     */
    public static void noteItemUser(Hero hero) {
        if (hero == null) {
            throw new IllegalArgumentException("item user must not be null");
        }
        inventoryContextHero = hero;
    }

    /** WndBag calls this when a concrete bag is opened. */
    public static Hero noteBagOwner(Bag bag) {
        Hero owner = bagOwner(bag);
        inventoryContextHero = owner;
        return owner;
    }

    public static Hero inventoryContextHero() {
        if (inventoryContextHero instanceof CompanionHero) {
            CompanionHero live = findCompanion();
            if (live == inventoryContextHero && live.isAlive()) {
                return live;
            }
        } else if (inventoryContextHero == Dungeon.hero && Dungeon.hero != null) {
            return Dungeon.hero;
        }
        inventoryContextHero = Dungeon.hero;
        return Dungeon.hero;
    }

    public static Hero bagOwner(Bag bag) {
        if (bag != null && bag.owner instanceof Hero) {
            return (Hero) bag.owner;
        }
        Hero context = inventoryContextHero();
        if (context == null) {
            throw new IllegalStateException("Bag UI requested without a Hero owner");
        }
        return context;
    }

    public static Hero itemOwner(Item item) {
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }

        CompanionHero companion = findCompanion();
        if (companion != null
                && companion.isAlive()
                && (companion.belongings.contains(item) || item.isEquipped(companion))) {
            return companion;
        }

        if (Dungeon.hero != null
                && (Dungeon.hero.belongings.contains(item) || item.isEquipped(Dungeon.hero))) {
            return Dungeon.hero;
        }

        Hero context = inventoryContextHero();
        return context != null ? context : Dungeon.hero;
    }

    public static void appendTransferAction(Item item, Hero owner, ArrayList<String> actions) {
        if (item == null || owner == null || actions == null) {
            throw new IllegalArgumentException("transfer action arguments must not be null");
        }

        CompanionHero companion = findCompanion();
        if (companion == null || !companion.isAlive()) {
            return;
        }

        if ((owner == Dungeon.hero || owner == companion)
                && (owner.belongings.contains(item) || item.isEquipped(owner))
                && !actions.contains(TRANSFER_ACTION)) {
            actions.add(TRANSFER_ACTION);
        }
    }

    public static boolean isTransferAction(String action) {
        return TRANSFER_ACTION.equals(action);
    }

    public static String transferActionName(Hero owner) {
        if (owner instanceof CompanionHero) {
            return CoHeroMessages.get("inventory.give_to_hero");
        }
        return CoHeroMessages.get("inventory.give_to_companion");
    }

    public static boolean handleTransferAction(Item item, Hero owner, String action) {
        if (!isTransferAction(action)) {
            return false;
        }

        CompanionHero companion = findCompanion();
        if (companion == null || !companion.isAlive() || Dungeon.hero == null) {
            throw new IllegalStateException("CoHero transfer requested without both living heroes");
        }

        Hero target = owner instanceof CompanionHero ? Dungeon.hero : companion;
        transferItem(item, owner, target);
        return true;
    }

    private static void transferItem(Item item, Hero from, Hero to) {
        if (item == null || from == null || to == null) {
            throw new IllegalArgumentException("transfer arguments must not be null");
        }
        if (!(from.belongings.contains(item) || item.isEquipped(from))) {
            throw new IllegalStateException("Transfer source does not own item: " + item.getClass().getName());
        }

        if (item.isEquipped(from)) {
            if (!(item instanceof EquipableItem)
                    || !((EquipableItem) item).doUnequip(from, false, false)) {
                return;
            }
        }

        Item moved = item.detachAll(from.belongings.backpack);
        if (!moved.collect(to.belongings.backpack)) {
            if (!moved.collect(from.belongings.backpack)) {
                throw new IllegalStateException("CoHero transfer rollback failed");
            }
            GLog.w(CoHeroMessages.get("inventory.backpack_full"));
        }
    }

    /**
     * Permanent strength is shared. Buff-derived effective STR still comes from Dungeon.hero.STR().
     */
    public static void increaseSharedStrength(Hero user) {
        if (user == null || Dungeon.hero == null) {
            throw new IllegalStateException("Shared STR increase requested without both Hero references");
        }
        Dungeon.hero.STR++;
    }

    private static String companionClassKey() {
        return COMPANION_CLASS_KEY_PREFIX + GamesInProgress.curSlot;
    }

    static CompanionHero findCompanion() {
        for (Char ch : Actor.chars()) {
            if (ch instanceof CompanionHero) {
                return (CompanionHero) ch;
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
