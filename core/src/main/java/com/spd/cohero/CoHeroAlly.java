package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Barrier;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Healing;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Invisibility;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Invulnerability;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MagicImmune;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GreatCrab;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Swarm;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.DirectableAlly;
import com.shatteredpixel.shatteredpixeldungeon.items.Ankh;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClassArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfShielding;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfAccuracy;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfEvasion;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfHaste;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfMight;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfSharpshooting;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTeleportation;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.Bolas;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.FishingSpear;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.Javelin;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.Kunai;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingClub;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingHammer;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingKnife;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingSpear;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingSpike;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.ThrowingStone;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.Tomahawk;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.Trident;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.Chasm;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.effects.SpellSprite;
import com.shatteredpixel.shatteredpixeldungeon.effects.FloatingText;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.MissileSprite;
import com.watabou.noosa.audio.Sample;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.utils.Bundle;
import com.watabou.utils.Callback;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class CoHeroAlly extends DirectableAlly {

    private static final String EXPLORATION_TARGET = "cohero_exploration_target";
    private static final String INVENTORY = "cohero_inventory";
    private static final String THROWN_SET_IDS = "cohero_thrown_set_ids";
    private static final String THROWN_SET_COUNTS = "cohero_thrown_set_counts";
    private static final String LOW_HEALTH_RALLY = "cohero_low_health_rally";
    private static final String COMBAT_RETREATING = "cohero_combat_retreating";

    private static final int LOW_HEALTH_RALLY_ENTER_PERCENT = 35;
    private static final int LOW_HEALTH_RALLY_EXIT_PERCENT = 60;
    private static final int HERO_RALLY_MIN_DISTANCE = 2;
    private static final int HERO_RALLY_MAX_DISTANCE = 3;
    private static final int MELEE_TACTICAL_SEARCH_RADIUS = 5;

    private int explorationTarget = -1;
    private int syncedLevel = 1;
    private final CompanionInventory inventory = new CompanionInventory(this);
    private final HashMap<Long, Integer> thrownOutstanding = new HashMap<>();
    private MissileWeapon activeMissileWeapon;
    private boolean lowHealthRally;
    private boolean combatRetreating;
    private int meleeTacticalTargetId = -1;
    private int meleeTacticalCell = -1;

    {
        spriteClass = CoHeroAllySprite.class;
        HT = HP = 20;
        attacksAutomatically = false;
    }

    public CompanionInventory inventory() {
        return inventory;
    }

    boolean lowHealthRally() {
        return lowHealthRally;
    }

    public MeleeWeapon weapon() {
        return inventory.weapon();
    }

    public Armor armor() {
        return inventory.armor();
    }

    public int level() {
        if (Dungeon.hero == null) {
            throw new IllegalStateException("CoHero level requested without Dungeon.hero");
        }
        return Dungeon.hero.lvl;
    }

    /**
     * Player Hero effective STR is the shared base. CoHero's own Ring of Might and intrinsic
     * Warrior trait then stack on top of it.
     */
    public int STR() {
        if (Dungeon.hero == null) {
            throw new IllegalStateException("CoHero STR requested without Dungeon.hero");
        }
        return Dungeon.hero.STR()
                + RingOfMight.strengthBonus(this)
                + CoHeroClassTraits.strengthBonus(this);
    }

    void updateHT(boolean boostHP) {
        int oldHT = HT;
        HT = Math.round((20 + 5 * (level() - 1))
                * RingOfMight.HTMultiplier(this)
                * CoHeroClassTraits.maxHealthMultiplier(this));
        if (boostHP) {
            HP += Math.max(HT - oldHT, 0);
        }
        HP = Math.min(HP, HT);
    }

    int armorTier() {
        Armor armor = armor();
        if (armor instanceof ClassArmor) {
            return 6;
        }
        return armor == null ? 0 : armor.tier;
    }

    void updateArmorSprite() {
        if (sprite instanceof CoHeroAllySprite) {
            ((CoHeroAllySprite) sprite).updateArmor();
        }
    }

    @Override
    public String name() {
        HeroClass heroClass = CoHero.companionClass();
        return heroClass == null ? CoHeroMessages.get("companion.name") : heroClass.title();
    }

    @Override
    public void storeInBundle(Bundle bundle) {
        super.storeInBundle(bundle);
        bundle.put(EXPLORATION_TARGET, explorationTarget);

        Bundle inventoryBundle = new Bundle();
        inventory.storeInBundle(inventoryBundle);
        bundle.put(INVENTORY, inventoryBundle);

        long[] thrownIDs = new long[thrownOutstanding.size()];
        int[] thrownCounts = new int[thrownOutstanding.size()];
        int thrownIndex = 0;
        for (Map.Entry<Long, Integer> entry : thrownOutstanding.entrySet()) {
            thrownIDs[thrownIndex] = entry.getKey();
            thrownCounts[thrownIndex] = entry.getValue();
            thrownIndex++;
        }
        bundle.put(THROWN_SET_IDS, thrownIDs);
        bundle.put(THROWN_SET_COUNTS, thrownCounts);
        bundle.put(LOW_HEALTH_RALLY, lowHealthRally);
        bundle.put(COMBAT_RETREATING, combatRetreating);
    }

    @Override
    public void restoreFromBundle(Bundle bundle) {
        super.restoreFromBundle(bundle);

        syncedLevel = level();

        explorationTarget = bundle.contains(EXPLORATION_TARGET)
                ? bundle.getInt(EXPLORATION_TARGET)
                : -1;

        if (bundle.contains(INVENTORY)) {
            inventory.restoreFromBundle(bundle.getBundle(INVENTORY));
        }

        lowHealthRally = bundle.getBoolean(LOW_HEALTH_RALLY);
        combatRetreating = bundle.getBoolean(COMBAT_RETREATING);

        thrownOutstanding.clear();
        if (bundle.contains(THROWN_SET_IDS) || bundle.contains(THROWN_SET_COUNTS)) {
            long[] thrownIDs = bundle.getLongArray(THROWN_SET_IDS);
            int[] thrownCounts = bundle.getIntArray(THROWN_SET_COUNTS);
            if (thrownIDs.length != thrownCounts.length) {
                throw new IllegalStateException("Corrupt CoHero thrown-weapon tracking");
            }
            for (int i = 0; i < thrownIDs.length; i++) {
                if (thrownCounts[i] > 0) {
                    thrownOutstanding.put(thrownIDs[i], thrownCounts[i]);
                }
            }
        }
    }

    void enterLevel(int cell) {
        if (!isAlive()) {
            throw new IllegalStateException("Cannot move a dead CoHero companion to a new level");
        }

        pos = cell;
        explorationTarget = -1;
        thrownOutstanding.clear();
        activeMissileWeapon = null;
        target = -1;
        enemy = null;
        enemyID = -1;
        enemySeen = false;
        alerted = false;
        path = null;
        defendingPos = -1;
        movingToDefendPos = false;
        state = WANDERING;
        clearMeleeTacticalPlan();
        combatRetreating = false;
        timeToNow();

        // Recreate item-owned buffs against this live Char after save restoration / floor transfer.
        inventory.rebuildPassiveEffects();
        syncedLevel = level();
        updateHT(false);
        Buff.affect(this, CompanionRegeneration.class);
    }

    private void syncSharedLevel() {
        int currentLevel = level();
        if (currentLevel == syncedLevel) {
            return;
        }

        boolean gainedLevel = currentLevel > syncedLevel;
        syncedLevel = currentLevel;
        updateHT(gainedLevel);
    }

    private int weaponEncumbrance() {
        return weapon() == null ? 0 : Math.max(0, weapon().STRReq() - STR());
    }

    private int armorEncumbrance() {
        return armor() == null ? 0 : Math.max(0, armor().STRReq() - STR());
    }

    private Weapon attackingWeapon() {
        return activeMissileWeapon != null ? activeMissileWeapon : weapon();
    }

    @Override
    protected boolean canAttack(Char enemy) {
        return weapon() != null && (super.canAttack(enemy) || weapon().canReach(this, enemy.pos));
    }

    /**
     * Uses the same final melee-legality rule from a hypothetical source cell. Tactical planners
     * must not assume that melee range is one tile: equipped weapons and upstream Mob rules can
     * change canAttack() reach.
     */
    private boolean canAttackFrom(int sourceCell, Char enemy) {
        if (enemy == null || !Dungeon.level.insideMap(sourceCell)) {
            return false;
        }
        int livePos = pos;
        try {
            pos = sourceCell;
            return canAttack(enemy);
        } finally {
            pos = livePos;
        }
    }

    @Override
    public int attackSkill(Char target) {
        return attackSkillWith(attackingWeapon(), target);
    }

    private int attackSkillWith(Weapon attackWeapon, Char target) {
        float accuracy = 9 + level();
        accuracy *= RingOfAccuracy.accuracyMultiplier(this);
        accuracy *= CoHeroClassTraits.clericAuraMultiplier(this);

        if (attackWeapon != null) {
            accuracy *= attackWeapon.accuracyFactor(this, target);
            if (attackWeapon == weapon()) {
                int encumbrance = weaponEncumbrance();
                if (encumbrance > 0) {
                    accuracy /= Math.pow(1.5, encumbrance);
                }
            }
        }
        return Math.round(accuracy);
    }

    @Override
    public int damageRoll() {
        Weapon attackWeapon = attackingWeapon();
        if (attackWeapon == null) {
            return super.damageRoll();
        }

        int damage = attackWeapon.damageRoll(this);
        int excessStrength = STR() - attackWeapon.STRReq();
        if (excessStrength > 0) {
            damage += Random.NormalIntRange(0, excessStrength);
        }
        return damage;
    }

    @Override
    public float attackDelay() {
        float delay = super.attackDelay();
        Weapon attackWeapon = attackingWeapon();
        if (attackWeapon != null) {
            delay *= attackWeapon.delayFactor(this);
            if (attackWeapon == weapon()) {
                delay /= CoHeroClassTraits.meleeAttackSpeedMultiplier(this);
                int encumbrance = weaponEncumbrance();
                if (encumbrance > 0) {
                    delay *= Math.pow(1.2, encumbrance);
                }
            }
        }
        return delay;
    }

    @Override
    public void hitSound(float pitch) {
        Weapon attackWeapon = attackingWeapon();
        if (attackWeapon != null) {
            attackWeapon.hitSound(pitch);
        } else {
            super.hitSound(pitch);
        }
    }

    @Override
    public void move(int step, boolean travelling) {
        super.move(step, travelling);
        if (sprite != null) {
            sprite.visible = true;
        }
    }

    @Override
    public int defenseSkill(Char enemy) {
        float evasion = (4 + level())
                * RingOfEvasion.evasionMultiplier(this)
                * CoHeroClassTraits.clericAuraMultiplier(this);
        if (armor() != null) {
            float armoredEvasion = armor().evasionFactor(this, evasion);
            int encumbrance = armorEncumbrance();
            if (encumbrance > 0 && armoredEvasion != 0) {
                // Armor.evasionFactor applies this before the flat augment bonus for Hero owners.
                float augmentBonus = armor().augment.evasionFactor(armor().buffedLvl());
                armoredEvasion = (float) (evasion / Math.pow(1.5, encumbrance)) + augmentBonus;
            }
            evasion = armoredEvasion;
        }
        return Math.round(evasion);
    }

    @Override
    public int glyphLevel(Class<? extends Armor.Glyph> cls) {
        if (armor() != null && armor().hasGlyph(cls, this)) {
            return Math.max(super.glyphLevel(cls), armor().buffedLvl());
        }
        return super.glyphLevel(cls);
    }

    @Override
    public float speed() {
        float speed = super.speed();
        speed *= RingOfHaste.speedMultiplier(this);
        speed *= CoHeroClassTraits.movementSpeedMultiplier(this);
        int encumbrance = armorEncumbrance();
        if (encumbrance > 0) {
            speed /= Math.pow(1.2, encumbrance);
        }
        return speed;
    }

    @Override
    public int drRoll() {
        int dr = super.drRoll();
        if (armor() != null) {
            int armorDr = Random.NormalIntRange(armor().DRMin(), armor().DRMax());
            armorDr -= 2 * armorEncumbrance();
            if (armorDr > 0) {
                dr += armorDr;
            }
        }
        if (weapon() != null) {
            int weaponDr = Random.NormalIntRange(0, weapon().defenseFactor(this));
            weaponDr -= 2 * weaponEncumbrance();
            if (weaponDr > 0) {
                dr += weaponDr;
            }
        }
        return dr;
    }

    @Override
    public int attackProc(Char enemy, int damage) {
        damage = super.attackProc(enemy, damage);
        Weapon attackWeapon = attackingWeapon();
        if (attackWeapon != null) {
            damage = attackWeapon.proc(this, enemy, damage);
        }
        return damage;
    }

    @Override
    public int defenseProc(Char enemy, int damage) {
        if (armor() != null) {
            damage = armor().proc(enemy, this, damage);
        }
        return super.defenseProc(enemy, damage);
    }

    @Override
    protected boolean act() {
        syncSharedLevel();

        if (fieldOfView == null || fieldOfView.length != Dungeon.level.length()) {
            fieldOfView = new boolean[Dungeon.level.length()];
        }
        Dungeon.level.updateFieldOfView(this, fieldOfView);
        revealVisibleCells();

        // If the player is already waiting on the exit, reaching an adjacent rally cell should
        // immediately use the player's normal transition flow.
        if (CoHero.tryAutoExit(this)) {
            return true;
        }

        if (paralysed > 0) {
            spend(TICK);
            return true;
        }

        updateLowHealthRallyState();

        Boolean hazardAvoidance = tryAvoidHazard();
        if (hazardAvoidance != null) {
            return hazardAvoidance;
        }

        ArrayList<Mob> visibleThreats = visibleAwakeEnemies();
        if (!visibleThreats.isEmpty()) {
            Mob combatTarget = nearestThreat(visibleThreats);

            Boolean survivalAction = tryCombatSurvival(combatTarget, visibleThreats);
            if (survivalAction != null) {
                return survivalAction;
            }

            if (tryAutoSurvivalPotion()) {
                return true;
            }

            Boolean meleePositioning = tryMeleePositioning(combatTarget, visibleThreats);
            if (meleePositioning != null) {
                return meleePositioning;
            }

            Boolean combatResult = tryCombat(combatTarget);
            if (combatResult != null) {
                return combatResult;
            }

            Boolean escapeUtility = tryEscapeUtility(visibleThreats);
            if (escapeUtility != null) {
                return escapeUtility;
            }

            int escapeStep = chooseEscapeStep(visibleThreats);
            if (escapeStep != -1) {
                int oldPos = pos;
                if (getCloser(escapeStep)) {
                    spend(1 / speed());
                    Dungeon.level.updateFieldOfView(this, fieldOfView);
                    revealVisibleCells();
                    CoHero.tryAutoExit(this);
                    return moveSprite(oldPos, pos);
                }
            }

            spend(TICK);
            return true;
        }

        combatRetreating = false;
        if (tryAutoSurvivalPotion()) {
            return true;
        }

        Boolean exitRally = tryExitRally();
        if (exitRally != null) {
            return exitRally;
        }

        if (lowHealthRally && !heroWaitingAtExit()) {
            return actLowHealthRally();
        }

        Boolean supportAction = trySupportAction();
        if (supportAction != null) {
            return supportAction;
        }

        if (recoverOwnedMissileAtCurrentCell()) {
            spend(TICK);
            return true;
        }

        int recoveryCell = nearestOwnedMissileCell();
        if (recoveryCell != -1 && recoveryCell != pos) {
            int oldPos = pos;
            if (getCloser(recoveryCell)) {
                spend(1 / speed());
                return moveSprite(oldPos, pos);
            }
        }

        boolean unexploredFrontier = hasUnexploredFrontier();
        if (explorationTarget == -1
                || explorationTarget == pos
                || !Dungeon.level.passable[explorationTarget]
                || (Actor.findChar(explorationTarget) != null && Actor.findChar(explorationTarget) != this)
                || !isMovementSafe(explorationTarget)
                || (!unexploredFrontier && !isWithinExploredRoamingArea(explorationTarget))) {
            explorationTarget = chooseExplorationTarget();
        }

        int oldPos = pos;
        if (explorationTarget != -1 && getCloser(explorationTarget)) {
            spend(1 / speed());

            Dungeon.level.updateFieldOfView(this, fieldOfView);
            revealVisibleCells();
            CoHero.tryAutoExit(this);
            return moveSprite(oldPos, pos);
        }

        explorationTarget = chooseExplorationTarget();
        spend(TICK);
        return true;
    }

    private Boolean tryAvoidHazard() {
        if (rooted || !CoHeroHazards.isDangerous(this, pos)) {
            return null;
        }

        int best = -1;
        int bestNearbyDanger = Integer.MAX_VALUE;
        int bestHeroDistance = Integer.MAX_VALUE;

        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = pos + offset;
            if (cell < 0
                    || cell >= Dungeon.level.length()
                    || Dungeon.level.distance(pos, cell) != 1
                    || !Dungeon.level.passable[cell]
                    || Actor.findChar(cell) != null
                    || !isMovementSafe(cell)) {
                continue;
            }

            int nearbyDanger = CoHeroHazards.nearbyDangerCount(this, cell);
            int heroDistance = Dungeon.hero == null
                    ? 0
                    : Dungeon.level.distance(cell, Dungeon.hero.pos);

            if (best == -1
                    || nearbyDanger < bestNearbyDanger
                    || (nearbyDanger == bestNearbyDanger && heroDistance < bestHeroDistance)) {
                best = cell;
                bestNearbyDanger = nearbyDanger;
                bestHeroDistance = heroDistance;
            }
        }

        if (best == -1) {
            return null;
        }

        int oldPos = pos;
        move(best, true);
        spend(1 / speed());
        Dungeon.level.updateFieldOfView(this, fieldOfView);
        revealVisibleCells();
        CoHero.tryAutoExit(this);
        return moveSprite(oldPos, pos);
    }

    @Override
    protected boolean getCloser(int target) {
        if (!CoHeroHazards.hasActiveHazards(this)) {
            return super.getCloser(target);
        }
        if (rooted || target == pos || !Dungeon.level.insideMap(target)) {
            return false;
        }

        boolean[] safePassable = CoHeroHazards.maskDangerous(this, Dungeon.level.passable);
        // A CoHero already standing in danger must still be able to path out of it.
        safePassable[pos] = true;

        int step = Dungeon.findStep(this, target, safePassable, fieldOfView, true);
        if (step == -1 || CoHeroHazards.isDangerous(this, step)) {
            path = null;
            return false;
        }

        path = null;
        move(step);
        return true;
    }

    private Boolean tryExitRally() {
        if (!heroWaitingAtExit()) {
            return null;
        }

        LevelTransition transition = Dungeon.level.getTransition(Dungeon.hero.pos);
        int rallyCell = chooseExitWaitingCell(transition);
        if (rallyCell == -1 || rallyCell == pos) {
            spend(TICK);
            return true;
        }

        int oldPos = pos;
        if (getCloser(rallyCell)) {
            spend(1 / speed());
            Dungeon.level.updateFieldOfView(this, fieldOfView);
            revealVisibleCells();
            CoHero.tryAutoExit(this);
            return moveSprite(oldPos, pos);
        }

        spend(TICK);
        return true;
    }

    private boolean tryAutoSurvivalPotion() {
        if (HT <= 0 || HP * 100 >= HT * LOW_HEALTH_RALLY_ENTER_PERCENT) {
            return false;
        }
        return consumeSurvivalPotion(false);
    }

    private boolean tryEmergencySurvivalPotion() {
        return consumeSurvivalPotion(true);
    }

    private boolean consumeSurvivalPotion(boolean shieldingFirst) {
        if (shieldingFirst && tryConsumeShieldingPotion()) {
            return true;
        }

        // Healing is the normal first choice, but not during a trapped emergency because its
        // recovery is spread over future turns.
        if (buff(Healing.class) == null) {
            Potion healing = inventory.takeOneAutoHealingPotion();
            if (healing != null) {
                PotionOfHealing.cure(this);
                PotionOfHealing.heal(this);
                Sample.INSTANCE.play(Assets.Sounds.DRINK);
                spend(TICK);
                return true;
            }
        }

        return shieldingFirst ? false : tryConsumeShieldingPotion();
    }

    private boolean tryConsumeShieldingPotion() {
        Barrier barrier = buff(Barrier.class);
        if (barrier != null && barrier.shielding() > 0) {
            return false;
        }

        Potion shielding = inventory.takeOneAutoShieldingPotion();
        if (shielding == null) {
            return false;
        }

        int amount = (int) (0.6f * HT + 10);
        Buff.affect(this, Barrier.class).setShield(amount);
        if (sprite != null) {
            sprite.showStatusWithIcon(
                    CharSprite.POSITIVE,
                    Integer.toString(amount),
                    FloatingText.SHIELDING);
        }
        Sample.INSTANCE.play(Assets.Sounds.DRINK);
        spend(TICK);
        return true;
    }

    private void updateLowHealthRallyState() {
        if (HT <= 0) {
            lowHealthRally = false;
            return;
        }

        if (lowHealthRally) {
            if (HP * 100 >= HT * LOW_HEALTH_RALLY_EXIT_PERCENT) {
                lowHealthRally = false;
                explorationTarget = -1;
            }
        } else if (HP * 100 < HT * LOW_HEALTH_RALLY_ENTER_PERCENT) {
            lowHealthRally = true;
            explorationTarget = -1;
        }
    }

    private boolean heroWaitingAtExit() {
        if (Dungeon.hero == null || !Dungeon.hero.isAlive() || Dungeon.level.locked) {
            return false;
        }
        LevelTransition transition = Dungeon.level.getTransition(Dungeon.hero.pos);
        return transition != null
                && transition.type == LevelTransition.Type.REGULAR_EXIT
                && transition.inside(Dungeon.hero.pos);
    }

    private boolean actLowHealthRally() {
        if (Dungeon.hero == null || !Dungeon.hero.isAlive()) {
            spend(TICK);
            return true;
        }

        int distance = Dungeon.level.distance(pos, Dungeon.hero.pos);

        // Far away: approach the Hero normally. Stop once the 2-3 cell comfort band is reached.
        if (distance > HERO_RALLY_MAX_DISTANCE) {
            int oldPos = pos;
            if (getCloser(Dungeon.hero.pos)) {
                spend(1 / speed());
                return moveSprite(oldPos, pos);
            }
            spend(TICK);
            return true;
        }

        // Adjacent is deliberately too close outside exit rally. Move one step away when a
        // passable, unoccupied, sleep-safe cell can restore the preferred one-cell gap.
        if (!rooted && distance < HERO_RALLY_MIN_DISTANCE) {
            int spacingStep = chooseHeroSpacingStep();
            if (spacingStep != -1) {
                int oldPos = pos;
                move(spacingStep, true);
                spend(1 / speed());
                return moveSprite(oldPos, pos);
            }
        }

        // Distances 2-3 are both acceptable. Holding here avoids jitter while the Hero moves.
        spend(TICK);
        return true;
    }

    private int chooseHeroSpacingStep() {
        int fallback = -1;
        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = pos + offset;
            if (cell < 0
                    || cell >= Dungeon.level.length()
                    || !Dungeon.level.passable[cell]
                    || Actor.findChar(cell) != null
                    || !isMovementSafe(cell)) {
                continue;
            }

            int distance = Dungeon.level.distance(cell, Dungeon.hero.pos);
            if (distance == HERO_RALLY_MIN_DISTANCE) {
                return cell;
            }
            if (fallback == -1 && distance <= HERO_RALLY_MAX_DISTANCE) {
                fallback = cell;
            }
        }
        return fallback;
    }

    @Override
    public void die(Object cause) {
        Ankh ankh = inventory.takeAnkhForRevive();
        if (ankh != null && reviveWithAnkh(ankh, cause)) {
            return;
        }

        super.die(cause);
        if (Dungeon.hero != null && Dungeon.hero.isAlive()) {
            GLog.n(companionDeathMessage(cause));
            CoHero.markCompanionDeathGameOver();
            Hero.reallyDie(cause);
        }
    }

    private boolean reviveWithAnkh(Ankh ankh, Object cause) {
        boolean fellIntoChasm = cause == Chasm.class;
        int destination = -1;

        // Ordinary Ankhs already relocate CoHero. A blessed Ankh normally revives in place, but
        // reviving in place on a pit would immediately leave CoHero in an invalid lethal cell.
        if (!ankh.isBlessed() || fellIntoChasm) {
            destination = chooseAnkhReviveCell(true);
            if (destination == -1) {
                destination = chooseAnkhReviveCell(false);
            }
            if (fellIntoChasm && destination == -1) {
                return false;
            }
        }

        HP = HT;
        lowHealthRally = false;

        Statistics.ankhsUsed++;
        Catalog.countUse(Ankh.class);
        SpellSprite.show(this, SpellSprite.ANKH);
        GameScene.flash(0x80FFFF40);

        if (ankh.isBlessed()) {
            Buff.prolong(this, Invulnerability.class, 15f);
        }

        if (destination != -1) {
            resetNavigationAfterAnkhTeleport();
            ScrollOfTeleportation.appear(this, destination);
            Dungeon.level.occupyCell(this);
            Dungeon.level.updateFieldOfView(this, fieldOfView);
            revealVisibleCells();
        } else {
            // Blessed Ankh deaths that did not involve a chasm keep the stock revive-in-place
            // behavior. An ordinary Ankh only reaches this fallback on a pathological full level.
            Sample.INSTANCE.play(Assets.Sounds.TELEPORT);
        }

        return true;
    }

    private int chooseAnkhReviveCell(boolean strictSafety) {
        ArrayList<Integer> destinations = new ArrayList<>();
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (!Dungeon.level.passable[cell]
                    || Dungeon.level.pit[cell]
                    || Dungeon.level.secret[cell]
                    || Actor.findChar(cell) != null) {
                continue;
            }
            if (strictSafety && !isMovementSafe(cell)) {
                continue;
            }
            destinations.add(cell);
        }
        return destinations.isEmpty() ? -1 : Random.element(destinations);
    }

    private void resetNavigationAfterAnkhTeleport() {
        explorationTarget = -1;
        target = -1;
        enemy = null;
        enemyID = -1;
        enemySeen = false;
        alerted = false;
        path = null;
        defendingPos = -1;
        movingToDefendPos = false;
        state = WANDERING;
        clearMeleeTacticalPlan();
        combatRetreating = false;
    }

    private String companionDeathMessage(Object cause) {
        if (cause instanceof Char && cause != this) {
            return CoHeroMessages.get("companion.killed_by", ((Char) cause).name());
        }
        return CoHeroMessages.get("companion.died");
    }

    private Mob nearestThreat(ArrayList<Mob> threats) {
        Mob result = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Mob threat : threats) {
            int distance = Dungeon.level.distance(pos, threat.pos);
            if (result == null || distance < bestDistance) {
                result = threat;
                bestDistance = distance;
            }
        }
        return result;
    }

    /**
     * Survival decisions run before any melee positioning or attack. The model is deliberately
     * conservative: current HP/shield are real effective health, only one usable potion is given
     * partial reserve value, and an Ankh is never treated as expendable combat HP.
     */
    private Boolean tryCombatSurvival(Mob targetMob, ArrayList<Mob> threats) {
        CombatRisk risk = assessCombatRisk(targetMob, threats);
        if (!risk.retreat) {
            combatRetreating = false;
            return null;
        }

        combatRetreating = true;
        clearMeleeTacticalPlan();

        Boolean escapeUtility = tryEscapeUtility(threats);
        if (escapeUtility != null) {
            return escapeUtility;
        }

        int escapeStep = rooted ? -1 : chooseEscapeStep(threats);
        if (escapeStep != -1) {
            int oldPos = pos;
            move(escapeStep, true);
            spend(1 / speed());
            Dungeon.level.updateFieldOfView(this, fieldOfView);
            revealVisibleCells();
            CoHero.tryAutoExit(this);
            return moveSprite(oldPos, pos);
        }

        // If terrain leaves no escape route, spend the turn on a real survival resource rather
        // than waiting. Healing is gradual, so the risk model never pre-counts the whole backpack.
        if (tryEmergencySurvivalPotion()) {
            return true;
        }

        // Trapped with no survival action: fall through to combat rather than waste the turn.
        return null;
    }

    private CombatRisk assessCombatRisk(Mob targetMob, ArrayList<Mob> threats) {
        int attackersNow = countCurrentAttackersAtCell(pos, threats);
        float incomingDpt = estimatedIncomingDptAtCell(pos, threats);
        float immediateIncoming = estimatedImmediateIncomingAtCell(pos, threats);
        float effectiveHp = HP + shielding();
        float reserve = estimatedNearTermSurvivalReserve(attackersNow);
        float outgoingDpt = estimateOutgoingDpt(targetMob);

        float ttd = incomingDpt <= 0.01f
                ? Float.POSITIVE_INFINITY
                : (effectiveHp + reserve) / incomingDpt;
        float ttk = outgoingDpt <= 0.01f
                ? Float.POSITIVE_INFINITY
                : Math.max(0.25f, targetMob.HP / outgoingDpt);

        boolean immediateLethal = immediateIncoming * 1.35f >= effectiveHp;
        boolean overwhelmed = attackersNow >= 3;
        boolean losingRace = incomingDpt > 0.01f && ttd <= ttk + 1.25f;
        boolean outnumberedRace = attackersNow >= 2
                && incomingDpt > 0.01f
                && ttd <= ttk * 1.5f;

        boolean retreat;
        if (combatRetreating) {
            boolean recovered = attackersNow <= 1
                    && HP * 100 >= HT * 45
                    && (incomingDpt <= 0.01f
                        || ttd >= Math.max(4f, ttk * 1.75f));
            retreat = !recovered;
        } else {
            retreat = immediateLethal || overwhelmed || losingRace || outnumberedRace;
        }

        return new CombatRisk(retreat, attackersNow, incomingDpt, immediateIncoming, ttd, ttk);
    }

    private float estimatedNearTermSurvivalReserve(int attackersNow) {
        float reserve = 0f;

        Barrier barrier = buff(Barrier.class);
        if ((barrier == null || barrier.shielding() <= 0)
                && inventory.autoShieldingPotionCount() > 0) {
            // Shielding is immediate, but drinking still costs an action.
            float shieldingPotion = 0.6f * HT + 10f;
            reserve += shieldingPotion * (attackersNow >= 2 ? 0.45f : 0.75f);
        }

        if (buff(Healing.class) != null) {
            // Existing healing is already ticking, but do not pretend the whole buff is instant.
            reserve += HT * 0.15f;
        } else if (inventory.autoHealingPotionCount() > 0) {
            float missingHp = Math.max(0, HT - HP);
            float potionTotal = Math.min(0.8f * HT + 14f, missingHp);
            reserve += potionTotal * (attackersNow >= 2 ? 0.20f : 0.35f);
        }

        return reserve;
    }

    private int countCurrentAttackersAtCell(int defenderCell, ArrayList<Mob> threats) {
        int result = 0;
        for (Mob threat : threats) {
            if (canThreatAttackCell(threat, defenderCell)) {
                result++;
            }
        }
        return result;
    }

    private float estimatedImmediateIncomingAtCell(int defenderCell, ArrayList<Mob> threats) {
        float result = 0f;
        for (Mob threat : threats) {
            if (canThreatAttackCell(threat, defenderCell)) {
                result += estimatedThreatDamage(threat, defenderCell)
                        * estimatedHitChance(threat, defenderCell);
            }
        }
        return result;
    }

    private float estimatedIncomingDptAtCell(int defenderCell, ArrayList<Mob> threats) {
        float result = 0f;
        for (Mob threat : threats) {
            float opportunity = threatOpportunity(threat, defenderCell);
            if (opportunity <= 0f) {
                continue;
            }
            float delay = Math.max(0.25f, threat.attackDelay());
            result += estimatedThreatDamage(threat, defenderCell)
                    * estimatedHitChance(threat, defenderCell)
                    * opportunity
                    / delay;
        }
        result += Math.max(0, incomingDOT()) * 0.20f;
        return result;
    }

    private float threatOpportunity(Mob threat, int defenderCell) {
        if (canThreatAttackCell(threat, defenderCell)) {
            return 1f;
        }
        if (threat.rooted || threat.paralysed > 0) {
            return 0f;
        }

        for (int offset : PathFinder.NEIGHBOURS8) {
            int source = threat.pos + offset;
            if (!Dungeon.level.insideMap(source)
                    || Dungeon.level.distance(threat.pos, source) != 1
                    || !enemyCanEnterForRisk(threat, source)) {
                continue;
            }
            if (canThreatAttackFromTo(threat, source, defenderCell)) {
                return 0.55f;
            }
        }

        return Dungeon.level.distance(threat.pos, defenderCell) <= 3 ? 0.10f : 0f;
    }

    private boolean enemyCanEnterForRisk(Mob threat, int cell) {
        if (!Dungeon.level.passable[cell]) {
            if (!threat.flying || Dungeon.level.avoid[cell]) {
                return false;
            }
        }
        return !Char.hasProp(threat, Char.Property.LARGE) || Dungeon.level.openSpace[cell];
    }

    private boolean canThreatAttackCell(Mob threat, int defenderCell) {
        return canThreatAttackFromTo(threat, threat.pos, defenderCell);
    }

    private boolean canThreatAttackFromTo(Mob threat, int sourceCell, int defenderCell) {
        int livePos = pos;
        try {
            pos = defenderCell;
            return threat.coHeroCanAttackFrom(sourceCell, this);
        } finally {
            pos = livePos;
        }
    }

    private float estimatedThreatDamage(Mob threat, int defenderCell) {
        int livePos = pos;
        Random.pushGenerator(0xC0E0A11L ^ ((long) threat.id() << 21) ^ defenderCell);
        try {
            pos = defenderCell;
            float total = 0f;
            for (int i = 0; i < 7; i++) {
                total += Math.max(0, threat.damageRoll());
            }
            // Do not subtract full armor here: ranged/special mob attacks do not always use normal
            // melee DR. The 0.85 factor gives armor some credit without making the estimate unsafe.
            return Math.max(0.5f, total / 7f * 0.85f);
        } finally {
            pos = livePos;
            Random.popGenerator();
        }
    }

    private float estimatedHitChance(Mob threat, int defenderCell) {
        int livePos = pos;
        try {
            pos = defenderCell;
            return estimatedUniformHitChance(
                    Math.max(0, threat.attackSkill(this)),
                    Math.max(0, defenseSkill(threat)));
        } finally {
            pos = livePos;
        }
    }

    private float estimatedUniformHitChance(float accuracy, float evasion) {
        if (accuracy <= 0f) {
            return 0f;
        }
        if (evasion <= 0f) {
            return 1f;
        }

        float chance;
        if (accuracy <= evasion) {
            chance = accuracy / (2f * evasion);
        } else {
            chance = 1f - evasion / (2f * accuracy);
        }
        // Buffs/champion modifiers are not all encoded in the raw skill values. Keep the survival
        // estimate conservative instead of allowing a deceptively tiny calculated hit chance.
        return Math.max(0.20f, Math.min(0.98f, chance));
    }

    private float estimateOutgoingDpt(Mob targetMob) {
        if (targetMob == null) {
            return 0f;
        }

        if (canAttack(targetMob)) {
            if (targetMob instanceof GreatCrab && !targetMob.coHeroSurprisedBy(this)) {
                return 0f;
            }

            float raw = sampledDamageRoll(this, targetMob.id());
            float dr = sampledDrRoll(targetMob, id());
            float effective = Math.max(0.5f, raw - dr);
            float hitChance = targetMob.coHeroSurprisedBy(this)
                    ? 1f
                    : estimatedPhysicalHitChance(attackSkill(targetMob), targetMob, this);
            return effective * hitChance / Math.max(0.25f, attackDelay());
        }

        float best = 0f;
        float targetDr = sampledDrRoll(targetMob, id());

        for (MissileWeapon missile : inventory.missileWeapons()) {
            if (!supportedMissileWeapon(missile)
                    || !missile.isIdentified()
                    || missile.cursed
                    || new Ballistica(pos, targetMob.pos, Ballistica.PROJECTILE).collisionPos != targetMob.pos) {
                continue;
            }
            float hitChance = estimatedPhysicalHitChance(
                    attackSkillWith(missile, targetMob), targetMob, this);
            best = Math.max(best,
                    Math.max(0.5f, expectedMissileDamage(missile) - targetDr) * hitChance);
        }

        SpiritBow bow = inventory.spiritBow();
        if (bow != null
                && bow.isIdentified()
                && !bow.cursed
                && new Ballistica(pos, targetMob.pos, Ballistica.PROJECTILE).collisionPos == targetMob.pos) {
            MissileWeapon arrow = bow.knockArrow();
            float hitChance = estimatedPhysicalHitChance(
                    attackSkillWith(arrow, targetMob), targetMob, this);
            best = Math.max(best,
                    Math.max(0.5f, expectedSpiritBowDamage(bow) - targetDr) * hitChance);
        }

        for (Wand wand : inventory.wands()) {
            if (!CoHeroWandAdapter.supported(wand)) {
                continue;
            }
            if (CoHeroWandAdapter.guaranteedControl(wand, this, targetMob)) {
                return Math.max(best, targetMob.HP);
            }
            if (CoHeroWandAdapter.canAffectEnemy(wand, this, targetMob)
                    && CoHeroWandAdapter.damagingCapability(wand, targetMob)) {
                best = Math.max(best, CoHeroWandAdapter.expectedDamage(wand, this, targetMob));
            }
        }

        return best;
    }

    private float estimatedPhysicalHitChance(int accuracy, Mob targetMob, Char attacker) {
        if (targetMob instanceof GreatCrab && !targetMob.coHeroSurprisedBy(attacker)) {
            return 0f;
        }
        if (targetMob.coHeroSurprisedBy(attacker)) {
            return 1f;
        }
        return estimatedUniformHitChance(
                Math.max(0, accuracy),
                Math.max(0, targetMob.defenseSkill(attacker)));
    }

    private float sampledDamageRoll(Char attacker, int salt) {
        Random.pushGenerator(0xC0E0D4A6L ^ ((long) attacker.id() << 19) ^ salt);
        try {
            float total = 0f;
            for (int i = 0; i < 7; i++) {
                total += Math.max(0, attacker.damageRoll());
            }
            return total / 7f;
        } finally {
            Random.popGenerator();
        }
    }

    private float sampledDrRoll(Char defender, int salt) {
        Random.pushGenerator(0xC0E0D2L ^ ((long) defender.id() << 17) ^ salt);
        try {
            float total = 0f;
            for (int i = 0; i < 5; i++) {
                total += Math.max(0, defender.drRoll());
            }
            return total / 5f;
        } finally {
            Random.popGenerator();
        }
    }

    private static final class CombatRisk {
        final boolean retreat;
        final int attackersNow;
        final float incomingDpt;
        final float immediateIncoming;
        final float ttd;
        final float ttk;

        CombatRisk(
                boolean retreat,
                int attackersNow,
                float incomingDpt,
                float immediateIncoming,
                float ttd,
                float ttk) {
            this.retreat = retreat;
            this.attackersNow = attackersNow;
            this.incomingDpt = incomingDpt;
            this.immediateIncoming = immediateIncoming;
            this.ttd = ttd;
            this.ttk = ttk;
        }
    }

    /**
     * Repositions melee CoHero before committing to an attack when terrain can materially improve
     * the exchange. Great Crab needs an unseen strike, while Swarms and multiple melee attackers
     * are much safer when pulled into a narrow approach instead of fought in open space.
     */
    private Boolean tryMeleePositioning(Mob targetMob, ArrayList<Mob> threats) {
        if (weapon() == null || targetMob == null || threats == null || threats.isEmpty()) {
            clearMeleeTacticalPlan();
            return null;
        }

        boolean greatCrab = targetMob instanceof GreatCrab;
        boolean swarmPressure = false;
        for (Mob threat : threats) {
            if (threat instanceof Swarm) {
                swarmPressure = true;
                break;
            }
        }

        boolean crowdedMelee = threats.size() >= 2 && !hasRangedPressure(threats);
        if (!greatCrab && !swarmPressure && !crowdedMelee) {
            clearMeleeTacticalPlan();
            return null;
        }

        if (greatCrab
                && targetMob.coHeroSurprisedBy(this)
                && canAttack(targetMob)) {
            clearMeleeTacticalPlan();
            return null;
        }

        if (!greatCrab && meleeFrontage(pos) <= 2) {
            clearMeleeTacticalPlan();
            return null;
        }

        if (meleeTacticalTargetId != targetMob.id()
                || !validMeleeTacticalCell(meleeTacticalCell, targetMob, greatCrab)) {
            meleeTacticalTargetId = targetMob.id();
            meleeTacticalCell = chooseMeleeTacticalCell(targetMob, greatCrab);
        }

        if (meleeTacticalCell == -1) {
            if (greatCrab
                    && canAttack(targetMob)
                    && !targetMob.coHeroSurprisedBy(this)) {
                int escape = chooseEscapeStep(threats);
                if (escape != -1) {
                    int oldPos = pos;
                    move(escape, true);
                    spend(1 / speed());
                    return moveSprite(oldPos, pos);
                }
            }
            return null;
        }

        if (pos != meleeTacticalCell) {
            int oldPos = pos;
            if (getCloser(meleeTacticalCell)) {
                spend(1 / speed());
                Dungeon.level.updateFieldOfView(this, fieldOfView);
                revealVisibleCells();
                return moveSprite(oldPos, pos);
            }
            clearMeleeTacticalPlan();
            return null;
        }

        if (canAttack(targetMob)
                && (!greatCrab || targetMob.coHeroSurprisedBy(this))) {
            clearMeleeTacticalPlan();
            return null;
        }

        if (!anyThreatCanAttackNow(threats)) {
            spend(TICK);
            return true;
        }

        clearMeleeTacticalPlan();
        return null;
    }

    private int chooseMeleeTacticalCell(Mob targetMob, boolean greatCrab) {
        PathFinder.buildDistanceMap(pos, Dungeon.level.passable);

        int best = -1;
        int bestScore = Integer.MAX_VALUE;

        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            int pathDistance = PathFinder.distance[cell];
            if (pathDistance == Integer.MAX_VALUE
                    || pathDistance > MELEE_TACTICAL_SEARCH_RADIUS
                    || !fieldOfView[cell]
                    || !isKnown(cell)
                    || !Dungeon.level.passable[cell]
                    || !isMovementSafe(cell)) {
                continue;
            }

            Char occupant = Actor.findChar(cell);
            if (occupant != null && occupant != this) {
                continue;
            }

            int frontage = meleeFrontage(cell);
            if (frontage < 2) {
                continue;
            }

            int targetDistance = Dungeon.level.distance(cell, targetMob.pos);

            if (greatCrab) {
                if (targetMob.fieldOfView == null
                        || targetMob.fieldOfView.length != Dungeon.level.length()
                        || targetMob.fieldOfView[cell]
                        || targetDistance < 2
                        || targetDistance > MELEE_TACTICAL_SEARCH_RADIUS) {
                    continue;
                }
            } else if (frontage > 3) {
                continue;
            }

            int score = pathDistance * 12 + frontage * 40;
            if (greatCrab) {
                score += Math.abs(targetDistance - 3) * 10;
            } else {
                score += Math.abs(targetDistance - 2) * 4;
                if (frontage == 2) {
                    score -= 80;
                }
            }

            if (best == -1 || score < bestScore || (score == bestScore && cell < best)) {
                best = cell;
                bestScore = score;
            }
        }

        return best;
    }

    private boolean validMeleeTacticalCell(int cell, Mob targetMob, boolean greatCrab) {
        if (cell < 0
                || cell >= Dungeon.level.length()
                || !Dungeon.level.passable[cell]
                || !fieldOfView[cell]
                || !isKnown(cell)
                || !isMovementSafe(cell)) {
            return false;
        }

        Char occupant = Actor.findChar(cell);
        if (occupant != null && occupant != this) {
            return false;
        }

        int frontage = meleeFrontage(cell);
        if (frontage < 2) {
            return false;
        }

        if (greatCrab) {
            int distance = Dungeon.level.distance(cell, targetMob.pos);
            return targetMob.fieldOfView != null
                    && targetMob.fieldOfView.length == Dungeon.level.length()
                    && !targetMob.fieldOfView[cell]
                    && distance >= 2
                    && distance <= MELEE_TACTICAL_SEARCH_RADIUS;
        }

        return frontage <= 3;
    }

    private int meleeFrontage(int cell) {
        int result = 0;
        for (int offset : PathFinder.NEIGHBOURS8) {
            int adjacent = cell + offset;
            if (adjacent >= 0
                    && adjacent < Dungeon.level.length()
                    && Dungeon.level.distance(cell, adjacent) == 1
                    && Dungeon.level.passable[adjacent]) {
                result++;
            }
        }
        return result;
    }

    private boolean hasRangedPressure(ArrayList<Mob> threats) {
        for (Mob threat : threats) {
            if (Dungeon.level.distance(threat.pos, pos) > 1
                    && threat.coHeroCanAttackFrom(threat.pos, this)) {
                return true;
            }
        }
        return false;
    }

    private boolean anyThreatCanAttackNow(ArrayList<Mob> threats) {
        for (Mob threat : threats) {
            if (threat.coHeroCanAttackFrom(threat.pos, this)) {
                return true;
            }
        }
        return false;
    }

    private void clearMeleeTacticalPlan() {
        meleeTacticalTargetId = -1;
        meleeTacticalCell = -1;
    }

    /**
     * Returns null when CoHero has no currently usable attack capability and should flee.
     * Otherwise returns the synchronous/asynchronous result expected by Actor.act().
     */
    private Boolean tryCombat(Mob targetMob) {
        if (targetMob == null) {
            return null;
        }

        enemy = targetMob;
        target = targetMob.pos;

        // Contract: if the equipped melee weapon can legally reach, never substitute a ranged attack.
        if (canAttack(targetMob)) {
            state = HUNTING;
            return doAttack(targetMob);
        }

        RangedChoice ranged = chooseRangedAttack(targetMob);
        if (ranged != null) {
            state = HUNTING;
            if (ranged.missile != null) {
                return performMissileAttack(targetMob, ranged.missile);
            } else if (ranged.spiritBow != null) {
                return performSpiritBowAttack(targetMob, ranged.spiritBow);
            } else {
                return performWandCast(ranged.wandTargetCell, ranged.wand);
            }
        }

        // If we have a usable combat tool but cannot use it from this cell, close distance.
        if (hasUsableCombatCapability(targetMob)) {
            int oldPos = pos;
            if (getCloser(targetMob.pos)) {
                spend(1 / speed());
                return moveSprite(oldPos, pos);
            }
            spend(TICK);
            return true;
        }

        return null;
    }

    private RangedChoice chooseRangedAttack(Mob targetMob) {
        ArrayList<MissileWeapon> missiles = new ArrayList<>();
        for (MissileWeapon missile : inventory.missileWeapons()) {
            if (supportedMissileWeapon(missile)
                    && missile.isIdentified()
                    && !missile.cursed
                    && new Ballistica(pos, targetMob.pos, Ballistica.PROJECTILE).collisionPos == targetMob.pos) {
                missiles.add(missile);
            }
        }

        SpiritBow spiritBow = inventory.spiritBow();
        MissileWeapon spiritArrow = null;
        if (spiritBow != null
                && spiritBow.isIdentified()
                && !spiritBow.cursed
                && new Ballistica(pos, targetMob.pos, Ballistica.PROJECTILE).collisionPos == targetMob.pos) {
            spiritArrow = spiritBow.knockArrow();
        }

        Wand guaranteedControl = null;
        ArrayList<Wand> damageWands = new ArrayList<>();
        for (Wand wand : inventory.wands()) {
            if (!CoHeroWandAdapter.supported(wand)) {
                continue;
            }
            if (CoHeroWandAdapter.guaranteedControl(wand, this, targetMob)) {
                if (guaranteedControl == null || wand.buffedLvl() > guaranteedControl.buffedLvl()) {
                    guaranteedControl = wand;
                }
            } else if (CoHeroWandAdapter.canAffectEnemy(wand, this, targetMob)
                    && CoHeroWandAdapter.damagingCapability(wand, targetMob)) {
                damageWands.add(wand);
            }
        }

        // A guaranteed corruption/doom conversion is treated as higher-value control than damage.
        if (guaranteedControl != null) {
            return RangedChoice.wand(guaranteedControl, targetMob.pos);
        }

        if ((!missiles.isEmpty() || spiritArrow != null) && !damageWands.isEmpty()) {
            int bestPhysicalAccuracy = 0;
            for (MissileWeapon missile : missiles) {
                bestPhysicalAccuracy = Math.max(bestPhysicalAccuracy, attackSkillWith(missile, targetMob));
            }
            if (spiritArrow != null) {
                bestPhysicalAccuracy = Math.max(bestPhysicalAccuracy, attackSkillWith(spiritArrow, targetMob));
            }
            if (targetMob.defenseSkill(this) > bestPhysicalAccuracy) {
                Wand best = bestDamageWand(damageWands, targetMob);
                return RangedChoice.wand(
                        best, CoHeroWandAdapter.aimCell(best, this, targetMob));
            }
        }

        MissileWeapon bestMissile = null;
        float bestMissileDamage = Float.NEGATIVE_INFINITY;
        for (MissileWeapon missile : missiles) {
            float damage = expectedMissileDamage(missile);
            if (bestMissile == null || damage > bestMissileDamage) {
                bestMissile = missile;
                bestMissileDamage = damage;
            }
        }

        float spiritBowDamage = spiritBow == null || spiritArrow == null
                ? Float.NEGATIVE_INFINITY
                : expectedSpiritBowDamage(spiritBow);
        boolean spiritBowBestPhysical = spiritBowDamage > bestMissileDamage;
        float bestPhysicalDamage = spiritBowBestPhysical ? spiritBowDamage : bestMissileDamage;

        Wand bestWand = bestDamageWand(damageWands, targetMob);
        float bestWandDamage = bestWand == null
                ? Float.NEGATIVE_INFINITY
                : CoHeroWandAdapter.expectedDamage(bestWand, this, targetMob);

        // Stable tie-break: preserve wand charges when physical expected damage is equal.
        if (bestPhysicalDamage > Float.NEGATIVE_INFINITY
                && (bestWand == null || bestPhysicalDamage >= bestWandDamage)) {
            return spiritBowBestPhysical
                    ? RangedChoice.spiritBow(spiritBow)
                    : RangedChoice.missile(bestMissile);
        }
        if (bestWand != null) {
            return RangedChoice.wand(
                    bestWand, CoHeroWandAdapter.aimCell(bestWand, this, targetMob));
        }

        // Control-only wands are fallbacks when no direct ranged damage is currently available.
        Wand fallbackControl = null;
        for (Wand wand : inventory.wands()) {
            if (CoHeroWandAdapter.fallbackControl(wand, this, targetMob)
                    && (fallbackControl == null || wand.buffedLvl() > fallbackControl.buffedLvl())) {
                fallbackControl = wand;
            }
        }
        return fallbackControl == null
                ? null
                : RangedChoice.wand(fallbackControl, targetMob.pos);
    }

    private Wand bestDamageWand(ArrayList<Wand> wands, Mob targetMob) {
        Wand best = null;
        float bestDamage = Float.NEGATIVE_INFINITY;
        for (Wand wand : wands) {
            float damage = CoHeroWandAdapter.expectedDamage(wand, this, targetMob);
            if (best == null || damage > bestDamage) {
                best = wand;
                bestDamage = damage;
            }
        }
        return best;
    }

    private boolean hasUsableCombatCapability(Mob targetMob) {
        if (weapon() != null) {
            return true;
        }
        for (MissileWeapon missile : inventory.missileWeapons()) {
            if (supportedMissileWeapon(missile) && missile.isIdentified() && !missile.cursed) {
                return true;
            }
        }
        SpiritBow spiritBow = inventory.spiritBow();
        if (spiritBow != null && spiritBow.isIdentified() && !spiritBow.cursed) {
            return true;
        }
        for (Wand wand : inventory.wands()) {
            if (CoHeroWandAdapter.hasOffensivePotential(wand, this, targetMob)) {
                return true;
            }
        }
        return false;
    }

    private boolean supportedMissileWeapon(MissileWeapon missile) {
        // Only stock projectile types that use the standard rangedHit/rangedMiss path are enabled.
        // Exact classes are intentional: unknown fork projectile semantics fail closed.
        Class<?> type = missile.getClass();
        return type == ThrowingStone.class
                || type == ThrowingKnife.class
                || type == ThrowingSpike.class
                || type == FishingSpear.class
                || type == ThrowingClub.class
                || type == ThrowingSpear.class
                || type == Kunai.class
                || type == Bolas.class
                || type == Javelin.class
                || type == Tomahawk.class
                || type == Trident.class
                || type == ThrowingHammer.class;
    }

    private float expectedMissileDamage(MissileWeapon missile) {
        int level = missile.buffedLvl()
                + RingOfSharpshooting.levelDamageBonus(this)
                + CoHeroClassTraits.missileLevelBonus(this);
        float average = (missile.min(level) + missile.max(level)) / 2f;
        average = missile.augment.damageFactor(average);
        int excessStrength = STR() - missile.STRReq();
        if (excessStrength > 0) {
            average += excessStrength / 2f;
        }
        return average;
    }

    private float expectedSpiritBowDamage(SpiritBow bow) {
        float average = (bow.coHeroMin(this) + bow.coHeroMax(this)) / 2f;
        average = bow.augment.damageFactor(average);
        int excessStrength = STR() - bow.STRReq();
        if (excessStrength > 0) {
            average += excessStrength / 2f;
        }
        return average;
    }

    private Boolean tryEscapeUtility(ArrayList<Mob> visibleThreats) {
        for (Mob threat : visibleThreats) {
            for (Wand wand : inventory.wands()) {
                if (CoHeroWandAdapter.regrowthUsefulForEscape(wand, this, threat, visibleThreats)) {
                    return performWandCast(threat.pos, wand);
                }
            }
        }
        return null;
    }

    private Boolean trySupportAction() {
        if (Dungeon.hero == null
                || Dungeon.hero.pos < 0
                || Dungeon.hero.pos >= fieldOfView.length) {
            return null;
        }

        boolean heroVisible = fieldOfView[Dungeon.hero.pos];
        Wand best = null;
        for (Wand wand : inventory.wands()) {
            if (CoHeroWandAdapter.transfusionShouldSupportHero(
                    wand, this, Dungeon.hero, heroVisible)
                    && (best == null || wand.buffedLvl() > best.buffedLvl())) {
                best = wand;
            }
        }
        return best == null ? null : performWandCast(Dungeon.hero.pos, best);
    }

    private boolean performSpiritBowAttack(Mob targetMob, SpiritBow bow) {
        MissileWeapon arrow = bow.knockArrow();
        float delay = arrow.castDelay(this, targetMob.pos);
        arrow.throwSound();

        if (sprite != null && sprite.parent != null && targetMob.sprite != null
                && (sprite.visible || targetMob.sprite.visible)) {
            ((MissileSprite) sprite.parent.recycle(MissileSprite.class)).reset(
                    sprite,
                    targetMob.sprite,
                    arrow,
                    new Callback() {
                        @Override
                        public void call() {
                            resolveSpiritBowAttack(targetMob, arrow);
                            spend(delay);
                            next();
                        }
                    });
            return false;
        }

        resolveSpiritBowAttack(targetMob, arrow);
        spend(delay);
        return true;
    }

    private void resolveSpiritBowAttack(Mob targetMob, MissileWeapon arrow) {
        activeMissileWeapon = arrow;
        try {
            attack(targetMob);
        } finally {
            activeMissileWeapon = null;
        }
        Invisibility.dispel(this);
    }

    private boolean performMissileAttack(Mob targetMob, MissileWeapon source) {
        MissileWeapon thrown = inventory.takeOneMissile(source);
        if (thrown == null) {
            throw new IllegalStateException("CoHero missile source disappeared before attack");
        }
        markThrown(thrown.setID, 1);

        float delay = thrown.castDelay(this, targetMob.pos);
        if (sprite != null && sprite.parent != null && targetMob.sprite != null
                && (sprite.visible || targetMob.sprite.visible)) {
            ((MissileSprite) sprite.parent.recycle(MissileSprite.class)).reset(
                    sprite,
                    targetMob.sprite,
                    thrown,
                    new Callback() {
                        @Override
                        public void call() {
                            resolveMissileAttack(targetMob, thrown);
                            spend(delay);
                            next();
                        }
                    });
            return false;
        }

        resolveMissileAttack(targetMob, thrown);
        spend(delay);
        return true;
    }

    private void resolveMissileAttack(Mob targetMob, MissileWeapon thrown) {
        boolean hit;
        activeMissileWeapon = thrown;
        try {
            hit = attack(targetMob);
        } finally {
            activeMissileWeapon = null;
        }

        boolean survived = thrown.coHeroResolveThrow(this, targetMob, hit);
        if (!survived) {
            markRecovered(thrown.setID, 1);
        }
        Invisibility.dispel(this);
    }

    private boolean performWandCast(int targetCell, Wand wand) {
        // Some stock wand fx methods are synchronous (beam/chain effects call their callback
        // before fx() returns), while projectile/cone effects complete asynchronously. Calling
        // next() synchronously from inside act() re-enters Actor processing, so distinguish both
        // cases explicitly.
        final boolean[] insideCast = {true};
        final boolean[] completedSynchronously = {false};

        if (targetCell < 0) {
            throw new IllegalStateException("CoHero wand choice has no legal aim cell");
        }

        wand.coHeroCast(this, targetCell, new Callback() {
            @Override
            public void call() {
                if (insideCast[0]) {
                    completedSynchronously[0] = true;
                    return;
                }

                Invisibility.dispel(CoHeroAlly.this);
                spend(TICK);
                next();
            }
        });

        insideCast[0] = false;
        if (completedSynchronously[0]) {
            Invisibility.dispel(this);
            spend(TICK);
            return true;
        }

        return false;
    }

    private void markThrown(long setID, int amount) {
        thrownOutstanding.put(setID, thrownOutstanding.getOrDefault(setID, 0) + amount);
    }

    private void markRecovered(long setID, int amount) {
        Integer count = thrownOutstanding.get(setID);
        if (count == null) {
            return;
        }
        int remaining = count - amount;
        if (remaining > 0) {
            thrownOutstanding.put(setID, remaining);
        } else {
            thrownOutstanding.remove(setID);
        }
    }

    private boolean recoverOwnedMissileAtCurrentCell() {
        Heap heap = Dungeon.level.heaps.get(pos);
        if (heap == null || heap.type != Heap.Type.HEAP || heap.hidden) {
            return false;
        }

        for (Item item : new ArrayList<>(heap.items)) {
            if (item instanceof MissileWeapon) {
                MissileWeapon missile = (MissileWeapon) item;
                Integer outstanding = thrownOutstanding.get(missile.setID);
                if (outstanding != null && outstanding > 0 && inventory.canAddToBackpack(missile)) {
                    heap.remove(missile);
                    if (!inventory.addToBackpack(missile)) {
                        Dungeon.level.drop(missile, pos).sprite.drop();
                        return false;
                    }
                    markRecovered(missile.setID, missile.quantity());
                    return true;
                }
            }
        }
        return false;
    }

    private int nearestOwnedMissileCell() {
        int result = -1;
        int bestDistance = Integer.MAX_VALUE;

        for (int cell : Dungeon.level.heaps.keyArray()) {
            Heap heap = Dungeon.level.heaps.get(cell);
            if (heap == null || heap.type != Heap.Type.HEAP || heap.hidden) {
                continue;
            }

            boolean containsOwnedMissile = false;
            for (Item item : heap.items) {
                if (item instanceof MissileWeapon) {
                    MissileWeapon missile = (MissileWeapon) item;
                    Integer outstanding = thrownOutstanding.get(missile.setID);
                    if (outstanding != null && outstanding > 0 && inventory.canAddToBackpack(missile)) {
                        containsOwnedMissile = true;
                        break;
                    }
                }
            }

            if (containsOwnedMissile) {
                int distance = Dungeon.level.distance(pos, cell);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    result = cell;
                }
            }
        }
        return result;
    }

    private static final class RangedChoice {
        final MissileWeapon missile;
        final Wand wand;
        final SpiritBow spiritBow;
        final int wandTargetCell;

        private RangedChoice(
                MissileWeapon missile, Wand wand, SpiritBow spiritBow, int wandTargetCell) {
            this.missile = missile;
            this.wand = wand;
            this.spiritBow = spiritBow;
            this.wandTargetCell = wandTargetCell;
        }

        static RangedChoice missile(MissileWeapon missile) {
            return new RangedChoice(missile, null, null, -1);
        }

        static RangedChoice wand(Wand wand, int targetCell) {
            if (wand == null || targetCell < 0) {
                return null;
            }
            return new RangedChoice(null, wand, null, targetCell);
        }

        static RangedChoice spiritBow(SpiritBow spiritBow) {
            return new RangedChoice(null, null, spiritBow, -1);
        }
    }

    private void revealVisibleCells() {
        for (int i = 0; i < fieldOfView.length; i++) {
            if (fieldOfView[i]
                    && Dungeon.level.discoverable[i]
                    && !Dungeon.level.visited[i]) {
                Dungeon.level.visited[i] = true;
            }
        }

        // CoHero vision is a display-only second FOV source. Refresh the local fog and mob
        // visibility every time its FOV is recomputed, even if all cells were already visited.
        GameScene.updateFog(pos, viewDistance + 1);
        GameScene.afterObserve();
    }

    private ArrayList<Mob> visibleAwakeEnemies() {
        ArrayList<Mob> result = new ArrayList<>();
        for (Mob mob : Dungeon.level.mobs) {
            if (mob != this
                    && mob.alignment == Alignment.ENEMY
                    && mob.isAlive()
                    && mob.invisible <= 0
                    && fieldOfView[mob.pos]
                    && mob.state != mob.SLEEPING) {
                result.add(mob);
            }
        }
        return result;
    }

    private int chooseEscapeStep(ArrayList<Mob> threats) {
        float currentIncoming = estimatedIncomingDptAtCell(pos, threats);
        int currentAttackers = countCurrentAttackersAtCell(pos, threats);
        int currentDistance = nearestThreatDistance(pos, threats);

        int bestCell = -1;
        float bestIncoming = currentIncoming;
        int bestAttackers = currentAttackers;
        int bestDistance = currentDistance;

        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = pos + offset;
            if (cell < 0
                    || cell >= Dungeon.level.length()
                    || Dungeon.level.distance(pos, cell) != 1
                    || !Dungeon.level.passable[cell]
                    || Actor.findChar(cell) != null
                    || !isMovementSafe(cell)) {
                continue;
            }

            float incoming = estimatedIncomingDptAtCell(cell, threats);
            int attackers = countCurrentAttackersAtCell(cell, threats);
            int distance = nearestThreatDistance(cell, threats);

            boolean better = attackers < bestAttackers
                    || (attackers == bestAttackers && incoming < bestIncoming - 0.01f)
                    || (attackers == bestAttackers
                        && Math.abs(incoming - bestIncoming) <= 0.01f
                        && distance > bestDistance);

            if (better) {
                bestCell = cell;
                bestIncoming = incoming;
                bestAttackers = attackers;
                bestDistance = distance;
            }
        }

        return bestCell;
    }

    private int nearestThreatDistance(int cell, ArrayList<Mob> threats) {
        int nearest = Integer.MAX_VALUE;
        for (Mob threat : threats) {
            nearest = Math.min(nearest, Dungeon.level.distance(cell, threat.pos));
        }
        return nearest;
    }

    private boolean isMovementSafe(int cell) {
        return !CoHeroHazards.isDangerous(this, cell) && isSleepSafe(cell);
    }

    private boolean isSleepSafe(int cell) {
        for (Mob mob : Dungeon.level.mobs) {
            if (mob != this
                    && mob.alignment == Alignment.ENEMY
                    && mob.isAlive()
                    && mob.state == mob.SLEEPING
                    && fieldOfView[mob.pos]
                    && Dungeon.level.distance(cell, mob.pos) <= 1) {
                return false;
            }
        }
        return true;
    }

    private int chooseExitWaitingCell(LevelTransition transition) {
        int bestCell = -1;
        int bestDistance = Integer.MAX_VALUE;

        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (!Dungeon.level.passable[cell]
                    || transition.inside(cell)
                    || !CoHero.isAdjacentToTransition(cell, transition)
                    || !isMovementSafe(cell)) {
                continue;
            }

            Char occupant = Actor.findChar(cell);
            if (occupant != null && occupant != this) {
                continue;
            }

            int distance = Dungeon.level.distance(pos, cell);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestCell = cell;
            }
        }

        return bestCell;
    }

    private boolean hasUnexploredFrontier() {
        PathFinder.buildDistanceMap(pos, Dungeon.level.passable);
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (cell != pos
                    && Dungeon.level.passable[cell]
                    && Dungeon.level.discoverable[cell]
                    && !Dungeon.level.visited[cell]
                    && !Dungeon.level.mapped[cell]
                    && PathFinder.distance[cell] < Integer.MAX_VALUE
                    && isMovementSafe(cell)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWithinExploredRoamingArea(int cell) {
        if (Dungeon.hero == null
                || !Dungeon.hero.isAlive()
                || cell < 0
                || cell >= Dungeon.level.length()
                || !Dungeon.level.passable[cell]
                || (!Dungeon.level.visited[cell] && !Dungeon.level.mapped[cell])) {
            return false;
        }

        PathFinder.buildDistanceMap(Dungeon.hero.pos, Dungeon.level.passable);

        ArrayList<Integer> reachable = new ArrayList<>();
        for (int candidate = 0; candidate < Dungeon.level.length(); candidate++) {
            if (Dungeon.level.passable[candidate]
                    && (Dungeon.level.visited[candidate] || Dungeon.level.mapped[candidate])
                    && PathFinder.distance[candidate] < Integer.MAX_VALUE) {
                reachable.add(candidate);
            }
        }

        if (reachable.isEmpty() || PathFinder.distance[cell] == Integer.MAX_VALUE) {
            return false;
        }

        reachable.sort((a, b) -> Integer.compare(PathFinder.distance[a], PathFinder.distance[b]));
        int roamingAreaSize = Math.max(1, (reachable.size() + 3) / 4);
        int maxDistance = PathFinder.distance[reachable.get(roamingAreaSize - 1)];
        return PathFinder.distance[cell] <= maxDistance;
    }

    private int chooseExplorationTarget() {
        PathFinder.buildDistanceMap(pos, Dungeon.level.passable);

        ArrayList<Integer> unknown = new ArrayList<>();
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (cell != pos
                    && Dungeon.level.passable[cell]
                    && Dungeon.level.discoverable[cell]
                    && !Dungeon.level.visited[cell]
                    && !Dungeon.level.mapped[cell]
                    && PathFinder.distance[cell] < Integer.MAX_VALUE
                    && isMovementSafe(cell)) {
                unknown.add(cell);
            }
        }

        if (!unknown.isEmpty()) {
            return Random.element(unknown);
        }

        // No reachable unexplored frontier remains. Keep roaming near the Hero instead of
        // getting stuck retrying isolated/secret cells or wandering across the whole floor.
        return chooseExploredRoamingTarget();
    }

    private int chooseExploredRoamingTarget() {
        if (Dungeon.hero == null || !Dungeon.hero.isAlive()) {
            return -1;
        }

        PathFinder.buildDistanceMap(Dungeon.hero.pos, Dungeon.level.passable);

        ArrayList<Integer> reachable = new ArrayList<>();
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (Dungeon.level.passable[cell]
                    && (Dungeon.level.visited[cell] || Dungeon.level.mapped[cell])
                    && PathFinder.distance[cell] < Integer.MAX_VALUE) {
                reachable.add(cell);
            }
        }

        if (reachable.isEmpty()) {
            return -1;
        }

        reachable.sort((a, b) -> Integer.compare(PathFinder.distance[a], PathFinder.distance[b]));
        int roamingAreaSize = Math.max(1, (reachable.size() + 3) / 4);

        int maxDistance = PathFinder.distance[reachable.get(roamingAreaSize - 1)];

        ArrayList<Integer> candidates = new ArrayList<>();
        for (int cell : reachable) {
            if (PathFinder.distance[cell] > maxDistance) {
                break;
            }
            if (cell == pos || cell == Dungeon.hero.pos || !isMovementSafe(cell)) {
                continue;
            }

            Char occupant = Actor.findChar(cell);
            if (occupant == null || occupant == this) {
                candidates.add(cell);
            }
        }

        return candidates.isEmpty() ? -1 : Random.element(candidates);
    }

    private boolean isKnown(int cell) {
        return cell >= 0
                && cell < Dungeon.level.length()
                && (Dungeon.level.visited[cell] || Dungeon.level.mapped[cell]);
    }
}
