package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.AllyBuff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Barkskin;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Barrier;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Blindness;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Dread;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Haste;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Healing;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Invisibility;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Invulnerability;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.LostInventory;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MagicImmune;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MagicalSleep;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Sleep;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Stamina;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Terror;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.GreatCrab;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Swarm;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.DirectableAlly;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Sheep;
import com.shatteredpixel.shatteredpixeldungeon.items.Ankh;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClassArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.bombs.Bomb;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHaste;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfInvisibility;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfCleansing;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfEarthenArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfShielding;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfStamina;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfAccuracy;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfEvasion;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfHaste;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfMight;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfSharpshooting;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfTenacity;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.Scroll;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTeleportation;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTerror;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.exotic.ScrollOfDread;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.Runestone;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfAggression;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfBlast;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfBlink;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfDeepSleep;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfFear;
import com.shatteredpixel.shatteredpixeldungeon.items.stones.StoneOfFlock;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfLivingEarth;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfWarding;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfWarding.Ward;
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
import com.shatteredpixel.shatteredpixeldungeon.plants.Earthroot;
import com.shatteredpixel.shatteredpixeldungeon.plants.Fadeleaf;
import com.shatteredpixel.shatteredpixeldungeon.plants.Mageroyal;
import com.shatteredpixel.shatteredpixeldungeon.plants.Plant;
import com.shatteredpixel.shatteredpixeldungeon.plants.Sungrass;
import com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter;
import com.shatteredpixel.shatteredpixeldungeon.effects.SpellSprite;
import com.shatteredpixel.shatteredpixeldungeon.effects.FloatingText;
import com.shatteredpixel.shatteredpixeldungeon.effects.Speck;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.sprites.MissileSprite;
import com.watabou.noosa.audio.Sample;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.utils.BArray;
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
    private static final int PRE_EXIT_EXPLORATION_PERCENT = 30;
    private static final int MELEE_TACTICAL_SEARCH_RADIUS = 5;
    private static final int RANGED_COVER_SEARCH_RADIUS = 6;
    private static final int RANGED_LURE_MAX_WAIT_TURNS = 6;

    private int explorationTarget = -1;
    private int syncedLevel = 1;
    private final CompanionInventory inventory = new CompanionInventory(this);
    private final HashMap<Long, Integer> thrownOutstanding = new HashMap<>();
    private MissileWeapon activeMissileWeapon;
    private boolean lowHealthRally;
    private boolean combatRetreating;
    private int meleeTacticalTargetId = -1;
    private int meleeTacticalCell = -1;
    private int rangedLureTargetId = -1;
    private int rangedLureCoverCell = -1;
    private int rangedLureWaitTurns;

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

    int enemySpawnMultiplierTenths() {
        CompanionEnemySurge surge = buff(CompanionEnemySurge.class);
        return surge == null
                ? CompanionEnemySurge.MIN_MULTIPLIER_TENTHS
                : surge.multiplierTenths();
    }

    void setEnemySpawnMultiplierTenths(int value) {
        CompanionEnemySurge surge = Buff.affect(this, CompanionEnemySurge.class);
        if (surge == null) {
            throw new IllegalStateException("CoHero enemy surge buff could not be attached");
        }
        surge.setMultiplierTenths(value);
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
        clearRangedLurePlan();
        combatRetreating = false;
        timeToNow();

        // Recreate item-owned buffs against this live Char after save restoration / floor transfer.
        inventory.rebuildPassiveEffects();
        syncedLevel = level();
        updateHT(false);
        Buff.affect(this, CompanionRegeneration.class);
        Buff.affect(this, CompanionEnemySurge.class);
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
            attackWeapon.coHeroUseForIdentification();
        }
        return damage;
    }

    @Override
    public void damage(int damage, Object source) {
        int adjusted = (int) Math.ceil(
                Math.max(0, damage) * RingOfTenacity.damageMultiplier(this));
        super.damage(adjusted, source);
    }

    @Override
    public int defenseProc(Char enemy, int damage) {
        Armor equippedArmor = armor();
        if (equippedArmor != null) {
            damage = equippedArmor.proc(enemy, this, damage);
            equippedArmor.coHeroUseForIdentification();
        }

        WandOfLivingEarth.RockArmor rockArmor =
                buff(WandOfLivingEarth.RockArmor.class);
        if (rockArmor != null) {
            damage = rockArmor.absorb(damage);
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
        if (visibleThreats.isEmpty()) {
            Boolean rangedLure = continueRangedLureWithoutVisibleThreat();
            if (rangedLure != null) {
                return rangedLure;
            }
        }

        if (!visibleThreats.isEmpty()) {
            Mob combatTarget = nearestThreat(visibleThreats);

            Boolean survivalAction = tryCombatSurvival(combatTarget, visibleThreats);
            if (survivalAction != null) {
                return survivalAction;
            }

            Boolean combatPlant = tryKnownCombatPlant(combatTarget, visibleThreats);
            if (combatPlant != null) {
                return combatPlant;
            }

            if (tryUseCleansingPotion(assessCombatRisk(combatTarget, visibleThreats))) {
                return true;
            }

            if (tryAutoSurvivalPotion()) {
                return true;
            }

            if (tryUseCombatRunestone(combatTarget, visibleThreats)) {
                return true;
            }

            if (tryUseCombatEarthenArmor(combatTarget, visibleThreats)) {
                return true;
            }

            if (tryUseCombatStamina(combatTarget, visibleThreats)) {
                return true;
            }

            Boolean rangedEngagement = tryRangedEngagement(combatTarget, visibleThreats);
            if (rangedEngagement != null) {
                return rangedEngagement;
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
                    return moveSprite(oldPos, pos);
                }
            }

            spend(TICK);
            return true;
        }

        combatRetreating = false;
        clearRangedLurePlan();

        Boolean recoveryPlant = tryKnownRecoveryPlant();
        if (recoveryPlant != null) {
            return recoveryPlant;
        }

        if (tryUseCleansingPotion(null)) {
            return true;
        }

        if (tryAutoSurvivalPotion()) {
            return true;
        }

        if (lowHealthRally) {
            return actLowHealthRally();
        }

        Boolean supportAction = trySupportAction();
        if (supportAction != null) {
            return supportAction;
        }

        if (recoverPreferredLootAtCurrentCell()) {
            spend(TICK);
            return true;
        }

        int recoveryCell = nearestPreferredLootCell();
        if (recoveryCell != -1 && recoveryCell != pos) {
            int recoveryStep = lootRecoveryStep(recoveryCell);
            if (recoveryStep != -1) {
                int oldPos = pos;
                move(recoveryStep, true);
                spend(1 / speed());
                return moveSprite(oldPos, pos);
            }
        }

        boolean[] explorationArea = preExitExplorationArea();
        boolean unexploredFrontier = hasUnexploredFrontier(explorationArea);
        if (explorationTarget == -1
                || explorationTarget == pos
                || !Dungeon.level.passable[explorationTarget]
                || (Actor.findChar(explorationTarget) != null && Actor.findChar(explorationTarget) != this)
                || !isMovementSafe(explorationTarget)
                || !explorationAreaAllows(explorationArea, explorationTarget)
                || (!unexploredFrontier && !isWithinExploredRoamingArea(explorationTarget))) {
            explorationTarget = chooseExplorationTarget(explorationArea);
        }

        int oldPos = pos;
        if (explorationTarget != -1 && getCloser(explorationTarget)) {
            spend(1 / speed());

            Dungeon.level.updateFieldOfView(this, fieldOfView);
            revealVisibleCells();
            return moveSprite(oldPos, pos);
        }

        explorationTarget = chooseExplorationTarget(explorationArea);
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
        clearRangedLurePlan();
        move(best, true);
        spend(1 / speed());
        Dungeon.level.updateFieldOfView(this, fieldOfView);
        revealVisibleCells();
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

    private boolean hasSeriousCleansableNegative() {
        int negatives = 0;
        for (Buff active : buffs()) {
            if (active.type != Buff.buffType.NEGATIVE
                    || active instanceof AllyBuff
                    || active instanceof LostInventory) {
                continue;
            }
            negatives++;
            if (active instanceof Buff.DOTbuff) {
                return true;
            }
        }

        return rooted
                || negatives >= 2
                || (negatives > 0 && HT > 0 && HP * 100 < HT * 50);
    }

    private boolean tryUseCleansingPotion(CombatRisk risk) {
        if (!hasSeriousCleansableNegative()) {
            return false;
        }

        // Do not spend an emergency turn cleansing when the current volley is already lethal.
        // Controlled/random teleport or immediate shielding remain safer in that situation.
        if (risk != null && risk.immediateIncoming * 1.35f >= HP + shielding()) {
            return false;
        }

        Potion potion = inventory.takeOneAutoCleansingPotion();
        if (!(potion instanceof PotionOfCleansing)) {
            return false;
        }

        PotionOfCleansing.cleanse(this);
        Catalog.countUse(PotionOfCleansing.class);
        Sample.INSTANCE.play(Assets.Sounds.DRINK);
        spend(TICK);
        return true;
    }

    private Boolean tryKnownRecoveryPlant() {
        // Sungrass only heals while its target remains on the activation cell.
        if (buff(Sungrass.Health.class) != null && HP < HT) {
            spend(TICK);
            return true;
        }

        if (hasSeriousCleansableNegative()) {
            int mageroyal = nearestKnownPlantCell(Mageroyal.class, 4);
            if (mageroyal != -1) {
                return moveTowardKnownPlant(mageroyal);
            }
        }

        if (HT > 0
                && HP * 100 < HT * 60
                && buff(Healing.class) == null) {
            int sungrass = nearestKnownPlantCell(Sungrass.class, 6);
            if (sungrass != -1) {
                return moveTowardKnownPlant(sungrass);
            }
        }

        return null;
    }

    private Boolean tryKnownCombatPlant(Mob targetMob, ArrayList<Mob> threats) {
        if (rooted || targetMob == null || threats == null || threats.isEmpty()) {
            return null;
        }

        if (hasSeriousCleansableNegative()) {
            int mageroyal = adjacentKnownPlantCell(Mageroyal.class);
            if (mageroyal != -1) {
                return moveOntoAdjacentPlant(mageroyal);
            }
        }

        boolean hardFight = threats.size() >= 2
                || Char.hasProp(targetMob, Char.Property.BOSS)
                || Char.hasProp(targetMob, Char.Property.MINIBOSS);
        if (hardFight
                && buff(Earthroot.Armor.class) == null
                && Barkskin.currentLevel(this) <= 0) {
            int earthroot = adjacentKnownPlantCell(Earthroot.class);
            if (earthroot != -1) {
                return moveOntoAdjacentPlant(earthroot);
            }
        }

        return null;
    }

    private Boolean tryKnownRetreatPlant(CombatRisk risk, ArrayList<Mob> threats) {
        if (rooted || risk == null || threats == null || threats.isEmpty()) {
            return null;
        }

        boolean severe = risk.attackersNow >= 2 || risk.ttd <= 3f;
        if (severe) {
            int fadeleaf = adjacentKnownPlantCell(Fadeleaf.class);
            if (fadeleaf != -1) {
                return moveOntoAdjacentPlant(fadeleaf);
            }
        }

        if (hasSeriousCleansableNegative()
                && risk.immediateIncoming * 1.35f < HP + shielding()) {
            int mageroyal = adjacentKnownPlantCell(Mageroyal.class);
            if (mageroyal != -1) {
                return moveOntoAdjacentPlant(mageroyal);
            }
        }

        return null;
    }

    private int adjacentKnownPlantCell(Class<? extends Plant> plantType) {
        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = pos + offset;
            if (!Dungeon.level.insideMap(cell)
                    || Dungeon.level.distance(pos, cell) != 1
                    || !Dungeon.level.visited[cell]
                    || !Dungeon.level.passable[cell]
                    || Actor.findChar(cell) != null
                    || !isMovementSafe(cell)) {
                continue;
            }

            Plant plant = Dungeon.level.plants.get(cell);
            if (plantType.isInstance(plant)) {
                return cell;
            }
        }
        return -1;
    }

    private int nearestKnownPlantCell(Class<? extends Plant> plantType, int maxDistance) {
        PathFinder.buildDistanceMap(pos, Dungeon.level.passable, maxDistance);

        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (Plant plant : Dungeon.level.plants.valueList()) {
            if (!plantType.isInstance(plant)) {
                continue;
            }
            int cell = plant.pos;
            if (!Dungeon.level.visited[cell]
                    || !Dungeon.level.passable[cell]
                    || Actor.findChar(cell) != null
                    || !isMovementSafe(cell)
                    || PathFinder.distance[cell] == Integer.MAX_VALUE) {
                continue;
            }

            if (PathFinder.distance[cell] < bestDistance) {
                best = cell;
                bestDistance = PathFinder.distance[cell];
            }
        }
        return best;
    }

    private Boolean moveTowardKnownPlant(int plantCell) {
        if (plantCell == -1 || rooted) {
            return null;
        }

        int oldPos = pos;
        if (!getCloser(plantCell)) {
            return null;
        }

        spend(1 / speed());
        Dungeon.level.updateFieldOfView(this, fieldOfView);
        revealVisibleCells();
        return moveSprite(oldPos, pos);
    }

    private Boolean moveOntoAdjacentPlant(int plantCell) {
        if (plantCell == -1
                || rooted
                || Dungeon.level.distance(pos, plantCell) != 1) {
            return null;
        }

        int oldPos = pos;
        move(plantCell, true);
        spend(1 / speed());
        Dungeon.level.updateFieldOfView(this, fieldOfView);
        revealVisibleCells();

        // Fadeleaf teleports during Level.occupyCell(). Its teleport VFX already placed the sprite,
        // so do not draw a second long-distance movement animation from the pre-plant cell.
        if (pos != plantCell) {
            path = null;
            clearMeleeTacticalPlan();
            clearRangedLurePlan();
            return true;
        }
        return moveSprite(oldPos, pos);
    }

    private boolean tryUseCombatEarthenArmor(Mob targetMob, ArrayList<Mob> threats) {
        if (targetMob == null
                || threats == null
                || threats.isEmpty()
                || combatRetreating
                || Barkskin.currentLevel(this) > 0
                || buff(Earthroot.Armor.class) != null) {
            return false;
        }

        boolean hardFight = threats.size() >= 2
                || Char.hasProp(targetMob, Char.Property.BOSS)
                || Char.hasProp(targetMob, Char.Property.MINIBOSS);
        if (!hardFight) {
            return false;
        }

        Potion potion = inventory.takeOneAutoEarthenArmorPotion();
        if (!(potion instanceof PotionOfEarthenArmor)) {
            return false;
        }

        Barkskin.conditionallyAppend(this, 2 + level() / 3, 50);
        Catalog.countUse(PotionOfEarthenArmor.class);
        Sample.INSTANCE.play(Assets.Sounds.DRINK);
        spend(TICK);
        return true;
    }

    private boolean tryAutoSurvivalPotion() {
        if (HT <= 0 || HP * 100 >= HT * LOW_HEALTH_RALLY_ENTER_PERCENT) {
            return false;
        }
        return consumeSurvivalPotion(false);
    }

    private boolean tryUseCombatRunestone(Mob targetMob, ArrayList<Mob> threats) {
        if (targetMob == null
                || threats == null
                || threats.isEmpty()
                || buff(MagicImmune.class) != null
                || combatRetreating) {
            return false;
        }

        // Safe clustered damage: only when at least two awake enemies are caught and no
        // ally/neutral/sleeping enemy or heap would be hit.
        int blastCell = chooseSafeBlastCell(threats);
        if (blastCell != -1 && useBlastStone(blastCell)) {
            return true;
        }

        // With 3+ visible threats, redirect the pack onto one non-boss enemy.
        if (threats.size() >= 3) {
            Mob aggressionTarget = chooseAggressionTarget(threats);
            if (aggressionTarget != null && useAggressionStone(aggressionTarget)) {
                return true;
            }
        }

        // With exactly two threats, remove one from the fight rather than spending a stronger
        // area-control resource. Prefer the non-current target when possible.
        if (threats.size() == 2
                && (hasRangedPressure(threats)
                    || Char.hasProp(targetMob, Char.Property.BOSS)
                    || Char.hasProp(targetMob, Char.Property.MINIBOSS))) {
            Mob sleepTarget = chooseDeepSleepTarget(targetMob, threats);
            if (sleepTarget != null && useDeepSleepStone(sleepTarget)) {
                return true;
            }
        }

        // A lone ranged attacker can be boxed in with sheep while CoHero closes the distance.
        if (threats.size() == 1
                && isCurrentRangedPressure(targetMob)
                && Dungeon.level.distance(pos, targetMob.pos) >= 3
                && chooseRangedCoverCell(targetMob, threats) == -1
                && canUseFlockAt(targetMob.pos)
                && useFlockStone(targetMob.pos)) {
            return true;
        }

        return false;
    }

    private boolean tryEmergencyBlinkRunestone(ArrayList<Mob> threats) {
        if (threats == null
                || threats.isEmpty()
                || buff(MagicImmune.class) != null) {
            return false;
        }

        int blinkCell = chooseBlinkEscapeCell(threats);
        return blinkCell != -1 && useBlinkStone(blinkCell);
    }

    private boolean tryEmergencyRunestone(CombatRisk risk, ArrayList<Mob> threats) {
        if (risk == null
                || threats == null
                || threats.isEmpty()
                || buff(MagicImmune.class) != null) {
            return false;
        }

        boolean immediateLethal = risk.immediateIncoming * 1.35f >= HP + shielding();
        Mob fearTarget = chooseFearTarget(threats);
        if (fearTarget != null
                && (immediateLethal || risk.ttd <= 2.5f || risk.attackersNow >= 2)
                && useFearStone(fearTarget)) {
            return true;
        }

        // Deep sleep is a fallback single-target control when fear is unavailable or ineffective.
        Mob sleepTarget = chooseEmergencySleepTarget(threats);
        if (sleepTarget != null
                && (immediateLethal || risk.attackersNow >= 2)
                && useDeepSleepStone(sleepTarget)) {
            return true;
        }

        // Flock is only used defensively here when it can be centered far enough away not to box
        // the Hero or CoHero in with the summoned sheep.
        if (threats.size() >= 2) {
            int flockCell = chooseEmergencyFlockCell(threats);
            if (flockCell != -1 && useFlockStone(flockCell)) {
                return true;
            }
        }

        return false;
    }

    private int chooseSafeBlastCell(ArrayList<Mob> threats) {
        if (!inventory.hasCombatRunestone(StoneOfBlast.class)) {
            return -1;
        }

        int bestCell = -1;
        int bestEnemies = 1;
        for (Mob threat : threats) {
            if (threat == null || !threat.isAlive() || !fieldOfView[threat.pos]) {
                continue;
            }

            boolean[] explodable = new boolean[Dungeon.level.length()];
            BArray.not(Dungeon.level.solid, explodable);
            BArray.or(Dungeon.level.flamable, explodable, explodable);
            PathFinder.buildDistanceMap(threat.pos, explodable, 1);

            int enemies = 0;
            boolean unsafe = false;
            for (int cell = 0; cell < PathFinder.distance.length; cell++) {
                if (PathFinder.distance[cell] == Integer.MAX_VALUE) {
                    continue;
                }

                if (Dungeon.level.heaps.get(cell) != null) {
                    unsafe = true;
                    break;
                }

                Char ch = Actor.findChar(cell);
                if (ch == null) {
                    continue;
                }
                if (ch.alignment != Alignment.ENEMY) {
                    unsafe = true;
                    break;
                }
                if (ch instanceof Mob && ((Mob) ch).state == ((Mob) ch).SLEEPING) {
                    unsafe = true;
                    break;
                }
                enemies++;
            }

            if (!unsafe && enemies >= 2 && enemies > bestEnemies) {
                bestEnemies = enemies;
                bestCell = threat.pos;
            }
        }
        return bestCell;
    }

    private Mob chooseAggressionTarget(ArrayList<Mob> threats) {
        if (!inventory.hasCombatRunestone(StoneOfAggression.class)) {
            return null;
        }

        Mob best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Mob mob : threats) {
            if (mob == null
                    || !mob.isAlive()
                    || Char.hasProp(mob, Char.Property.BOSS)
                    || Char.hasProp(mob, Char.Property.MINIBOSS)
                    || mob.buff(StoneOfAggression.Aggression.class) != null) {
                continue;
            }

            int nearbyEnemies = 0;
            for (Mob other : threats) {
                if (other != mob
                        && other != null
                        && other.isAlive()
                        && Dungeon.level.distance(other.pos, mob.pos) <= 5) {
                    nearbyEnemies++;
                }
            }

            int score = nearbyEnemies * 100 + mob.HP;
            if (best == null || score > bestScore) {
                best = mob;
                bestScore = score;
            }
        }
        return best;
    }

    private Mob chooseDeepSleepTarget(Mob combatTarget, ArrayList<Mob> threats) {
        if (!inventory.hasCombatRunestone(StoneOfDeepSleep.class)) {
            return null;
        }

        Mob fallback = null;
        for (Mob mob : threats) {
            if (!canDeepSleep(mob)) {
                continue;
            }
            if (mob != combatTarget) {
                return mob;
            }
            fallback = mob;
        }
        return fallback;
    }

    private Mob chooseEmergencySleepTarget(ArrayList<Mob> threats) {
        if (!inventory.hasCombatRunestone(StoneOfDeepSleep.class)) {
            return null;
        }

        Mob best = null;
        float bestThreat = Float.NEGATIVE_INFINITY;
        for (Mob mob : threats) {
            if (!canDeepSleep(mob)) {
                continue;
            }
            float score = estimatedThreatDamage(mob, pos)
                    * estimatedHitChance(mob, pos)
                    * Math.max(0.1f, threatOpportunity(mob, pos));
            if (best == null || score > bestThreat) {
                best = mob;
                bestThreat = score;
            }
        }
        return best;
    }

    private boolean canDeepSleep(Mob mob) {
        return mob != null
                && mob.isAlive()
                && mob.state != mob.SLEEPING
                && !mob.isImmune(Sleep.class)
                && mob.buff(MagicalSleep.class) == null;
    }

    private Mob chooseFearTarget(ArrayList<Mob> threats) {
        if (!inventory.hasCombatRunestone(StoneOfFear.class)) {
            return null;
        }

        Mob best = null;
        float bestThreat = Float.NEGATIVE_INFINITY;
        for (Mob mob : threats) {
            if (mob == null
                    || !mob.isAlive()
                    || mob.isImmune(Terror.class)
                    || mob.buff(Terror.class) != null) {
                continue;
            }

            float score = estimatedThreatDamage(mob, pos)
                    * estimatedHitChance(mob, pos)
                    * Math.max(0.1f, threatOpportunity(mob, pos));
            if (best == null || score > bestThreat) {
                best = mob;
                bestThreat = score;
            }
        }
        return best;
    }

    private int chooseBlinkEscapeCell(ArrayList<Mob> threats) {
        if (!inventory.hasCombatRunestone(StoneOfBlink.class)) {
            return -1;
        }

        int currentDistance = nearestThreatDistance(pos, threats);
        int best = -1;
        int bestDistance = currentDistance;

        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (!fieldOfView[cell]
                    || !isKnown(cell)
                    || !Dungeon.level.passable[cell]
                    || Dungeon.level.pit[cell]
                    || Dungeon.level.secret[cell]
                    || Actor.findChar(cell) != null
                    || !isMovementSafe(cell)
                    || Dungeon.level.distance(pos, cell) < 3) {
                continue;
            }

            Ballistica path = new Ballistica(pos, cell, Ballistica.PROJECTILE);
            if (path.collisionPos != cell) {
                continue;
            }

            int distance = nearestThreatDistance(cell, threats);
            if (distance < currentDistance + 2) {
                continue;
            }

            if (best == -1 || distance > bestDistance
                    || (distance == bestDistance
                        && Dungeon.level.distance(pos, cell) < Dungeon.level.distance(pos, best))) {
                best = cell;
                bestDistance = distance;
            }
        }
        return best;
    }

    private boolean canUseFlockAt(int center) {
        if (!inventory.hasCombatRunestone(StoneOfFlock.class)
                || !Dungeon.level.insideMap(center)
                || !fieldOfView[center]
                || Dungeon.level.distance(pos, center) <= 2
                || (Dungeon.hero != null && Dungeon.level.distance(Dungeon.hero.pos, center) <= 2)) {
            return false;
        }

        boolean[] open = BArray.not(Dungeon.level.solid, null);
        PathFinder.buildDistanceMap(center, open, 2);
        int spawnable = 0;
        for (int cell = 0; cell < PathFinder.distance.length; cell++) {
            if (PathFinder.distance[cell] != Integer.MAX_VALUE
                    && Dungeon.level.insideMap(cell)
                    && Actor.findChar(cell) == null
                    && !Dungeon.level.pit[cell]) {
                spawnable++;
            }
        }
        return spawnable >= 3;
    }

    private int chooseEmergencyFlockCell(ArrayList<Mob> threats) {
        if (!inventory.hasCombatRunestone(StoneOfFlock.class)) {
            return -1;
        }

        int best = -1;
        int bestNearbyThreats = 0;
        for (Mob mob : threats) {
            if (mob == null || !mob.isAlive() || !canUseFlockAt(mob.pos)) {
                continue;
            }

            int nearby = 0;
            for (Mob other : threats) {
                if (other != null
                        && other.isAlive()
                        && Dungeon.level.distance(other.pos, mob.pos) <= 2) {
                    nearby++;
                }
            }
            if (best == -1 || nearby > bestNearbyThreats) {
                best = mob.pos;
                bestNearbyThreats = nearby;
            }
        }
        return best;
    }

    private boolean useAggressionStone(Mob targetMob) {
        Runestone stone = inventory.takeOneCombatRunestone(StoneOfAggression.class);
        if (!(stone instanceof StoneOfAggression)) {
            return false;
        }

        Buff.prolong(targetMob,
                StoneOfAggression.Aggression.class,
                StoneOfAggression.Aggression.DURATION);
        CellEmitter.center(targetMob.pos).start(Speck.factory(Speck.SCREAM), 0.3f, 3);
        return finishRunestoneUse(stone, Assets.Sounds.READ);
    }

    private boolean useBlastStone(int cell) {
        Runestone stone = inventory.takeOneCombatRunestone(StoneOfBlast.class);
        if (!(stone instanceof StoneOfBlast)) {
            return false;
        }

        new Bomb.ConjuredBomb().explode(cell);
        return finishRunestoneUse(stone, null);
    }

    private boolean useFearStone(Mob targetMob) {
        Runestone stone = inventory.takeOneCombatRunestone(StoneOfFear.class);
        if (!(stone instanceof StoneOfFear)) {
            return false;
        }

        Terror terror = Buff.affect(targetMob, Terror.class, Terror.DURATION);
        terror.object = id();
        return finishRunestoneUse(stone, Assets.Sounds.READ);
    }

    private boolean useDeepSleepStone(Mob targetMob) {
        Runestone stone = inventory.takeOneCombatRunestone(StoneOfDeepSleep.class);
        if (!(stone instanceof StoneOfDeepSleep)) {
            return false;
        }

        Buff.affect(targetMob, MagicalSleep.class);
        if (targetMob.sprite != null) {
            targetMob.sprite.centerEmitter().start(Speck.factory(Speck.NOTE), 0.3f, 5);
        }
        return finishRunestoneUse(stone, Assets.Sounds.LULLABY);
    }

    private boolean useBlinkStone(int cell) {
        Runestone stone = inventory.takeOneCombatRunestone(StoneOfBlink.class);
        if (!(stone instanceof StoneOfBlink)) {
            return false;
        }

        if (!ScrollOfTeleportation.teleportToLocation(this, cell)) {
            inventory.addToBackpack(stone);
            return false;
        }

        Dungeon.level.updateFieldOfView(this, fieldOfView);
        revealVisibleCells();
        clearMeleeTacticalPlan();
        clearRangedLurePlan();
        path = null;
        return finishRunestoneUse(stone, null);
    }

    private boolean useFlockStone(int center) {
        Runestone stone = inventory.takeOneCombatRunestone(StoneOfFlock.class);
        if (!(stone instanceof StoneOfFlock)) {
            return false;
        }

        boolean[] open = BArray.not(Dungeon.level.solid, null);
        PathFinder.buildDistanceMap(center, open, 2);
        int spawned = 0;
        for (int cell = 0; cell < PathFinder.distance.length; cell++) {
            if (PathFinder.distance[cell] == Integer.MAX_VALUE
                    || !Dungeon.level.insideMap(cell)
                    || Actor.findChar(cell) != null
                    || Dungeon.level.pit[cell]) {
                continue;
            }

            Sheep sheep = new Sheep();
            sheep.initialize(8);
            sheep.pos = cell;
            GameScene.add(sheep);
            Dungeon.level.occupyCell(sheep);
            CellEmitter.get(cell).burst(Speck.factory(Speck.WOOL), 4);
            spawned++;
        }

        if (spawned == 0) {
            inventory.addToBackpack(stone);
            return false;
        }

        CellEmitter.get(center).burst(Speck.factory(Speck.WOOL), 4);
        Sample.INSTANCE.play(Assets.Sounds.PUFF);
        Sample.INSTANCE.play(Assets.Sounds.SHEEP);
        return finishRunestoneUse(stone, null);
    }

    private boolean finishRunestoneUse(Runestone stone, String sound) {
        if (stone == null) {
            return false;
        }
        Catalog.countUse(stone.getClass());
        Invisibility.dispel(this);
        if (sound != null) {
            Sample.INSTANCE.play(sound);
        }
        spend(TICK);
        return true;
    }

    private boolean tryUseTeleportationScroll() {
        Scroll scroll = inventory.takeOneAutoTeleportationScroll();
        if (!(scroll instanceof ScrollOfTeleportation)) {
            return false;
        }

        if (!ScrollOfTeleportation.teleportChar(this)) {
            inventory.addToBackpack(scroll);
            return false;
        }

        Catalog.countUse(ScrollOfTeleportation.class);
        Invisibility.dispel(this);
        Sample.INSTANCE.play(Assets.Sounds.READ);
        path = null;
        clearMeleeTacticalPlan();
        clearRangedLurePlan();
        Dungeon.level.updateFieldOfView(this, fieldOfView);
        revealVisibleCells();
        spend(TICK);
        return true;
    }

    private int usableDreadTargetCount(ArrayList<Mob> threats) {
        if (buff(MagicImmune.class) != null || buff(Blindness.class) != null) {
            return 0;
        }

        int count = 0;
        for (Mob mob : threats) {
            if (mob == null
                    || !mob.isAlive()
                    || mob.alignment != Alignment.ENEMY
                    || mob.invisible > 0
                    || fieldOfView == null
                    || !fieldOfView[mob.pos]
                    || mob.state == mob.SLEEPING
                    || (mob.isImmune(Dread.class) && mob.isImmune(Terror.class))) {
                continue;
            }
            count++;
        }
        return count;
    }

    private boolean tryUseDreadScroll(ArrayList<Mob> threats) {
        if (usableDreadTargetCount(threats) == 0) {
            return false;
        }

        Scroll scroll = inventory.takeOneAutoDreadScroll();
        if (!(scroll instanceof ScrollOfDread)) {
            return false;
        }

        int affected = 0;
        for (Mob mob : threats) {
            if (mob == null
                    || !mob.isAlive()
                    || mob.alignment != Alignment.ENEMY
                    || mob.invisible > 0
                    || fieldOfView == null
                    || !fieldOfView[mob.pos]
                    || mob.state == mob.SLEEPING) {
                continue;
            }

            if (!mob.isImmune(Dread.class)) {
                Dread dread = Buff.affect(mob, Dread.class);
                if (dread != null) {
                    dread.object = id();
                    affected++;
                }
            } else if (!mob.isImmune(Terror.class)) {
                Terror terror = Buff.affect(mob, Terror.class, Terror.DURATION);
                if (terror != null) {
                    terror.object = id();
                    affected++;
                }
            }
        }

        if (affected == 0) {
            inventory.addToBackpack(scroll);
            return false;
        }

        Catalog.countUse(ScrollOfDread.class);
        Invisibility.dispel(this);
        Sample.INSTANCE.play(Assets.Sounds.READ);
        spend(TICK);
        return true;
    }

    private boolean tryEmergencyEscapeConsumable(
            CombatRisk risk, ArrayList<Mob> threats) {
        boolean immediateLethal = risk.immediateIncoming * 1.35f >= HP + shielding();

        int terrorTargets = usableTerrorTargetCount(threats);
        if (terrorTargets >= 2 || (terrorTargets >= 1 && immediateLethal)) {
            if (tryUseTerrorScroll(threats)) {
                return true;
            }
        }

        int dreadTargets = usableDreadTargetCount(threats);
        boolean criticallyShortTtd = risk.ttd <= 2f;
        if (dreadTargets >= 2
                && (risk.attackersNow >= 2 || immediateLethal || criticallyShortTtd)
                && tryUseDreadScroll(threats)) {
            return true;
        }

        boolean lowHealthDanger = HT > 0 && HP * 100 < HT * LOW_HEALTH_RALLY_ENTER_PERCENT;
        if (risk.attackersNow >= 3 || immediateLethal || lowHealthDanger || criticallyShortTtd) {
            if (tryUseInvisibilityPotion()) {
                return true;
            }
        }

        return false;
    }

    private int usableTerrorTargetCount(ArrayList<Mob> threats) {
        if (buff(MagicImmune.class) != null || buff(Blindness.class) != null) {
            return 0;
        }

        int count = 0;
        for (Mob mob : threats) {
            if (mob != null
                    && mob.isAlive()
                    && mob.alignment == Alignment.ENEMY
                    && mob.invisible <= 0
                    && fieldOfView != null
                    && fieldOfView[mob.pos]
                    && mob.state != mob.SLEEPING
                    && !mob.isImmune(Terror.class)) {
                count++;
            }
        }
        return count;
    }

    private boolean tryUseTerrorScroll(ArrayList<Mob> threats) {
        if (usableTerrorTargetCount(threats) == 0) {
            return false;
        }

        Scroll scroll = inventory.takeOneAutoTerrorScroll();
        if (!(scroll instanceof ScrollOfTerror)) {
            return false;
        }

        int affected = 0;
        for (Mob mob : threats) {
            if (mob == null
                    || !mob.isAlive()
                    || mob.alignment != Alignment.ENEMY
                    || mob.invisible > 0
                    || fieldOfView == null
                    || !fieldOfView[mob.pos]
                    || mob.state == mob.SLEEPING
                    || mob.isImmune(Terror.class)) {
                continue;
            }

            Terror terror = Buff.affect(mob, Terror.class, Terror.DURATION);
            if (terror != null) {
                terror.object = id();
                affected++;
            }
        }

        if (affected == 0) {
            // The pre-check should prevent this, but do not consume a known scroll for no effect.
            inventory.addToBackpack(scroll);
            return false;
        }

        Invisibility.dispel(this);
        Catalog.countUse(ScrollOfTerror.class);
        Sample.INSTANCE.play(Assets.Sounds.READ);
        spend(TICK);
        return true;
    }

    private boolean tryUseInvisibilityPotion() {
        if (buff(Invisibility.class) != null) {
            return false;
        }

        Potion potion = inventory.takeOneAutoInvisibilityPotion();
        if (!(potion instanceof PotionOfInvisibility)) {
            return false;
        }

        Buff.prolong(this, Invisibility.class, Invisibility.DURATION);
        Catalog.countUse(PotionOfInvisibility.class);
        Sample.INSTANCE.play(Assets.Sounds.DRINK);
        Sample.INSTANCE.play(Assets.Sounds.MELD);
        spend(TICK);
        return true;
    }

    private boolean shouldUseHasteForRetreat(
            CombatRisk risk, ArrayList<Mob> threats, int escapeStep) {
        if (risk == null
                || threats == null
                || escapeStep == -1
                || buff(Haste.class) != null
                || buff(Stamina.class) != null
                || buff(Invisibility.class) != null) {
            return false;
        }

        // Do not spend a turn drinking when the current incoming volley is already near-lethal.
        // In that case the immediate movement/control path remains safer.
        if (risk.immediateIncoming * 1.35f >= HP + shielding()) {
            return false;
        }

        int attackersAfterStep = countCurrentAttackersAtCell(escapeStep, threats);
        float incomingAfterStep = estimatedIncomingDptAtCell(escapeStep, threats);

        boolean fastPursuer = false;
        for (Mob threat : threats) {
            if (threat == null || !threat.isAlive()) {
                continue;
            }
            if (threatOpportunity(threat, escapeStep) >= 0.55f
                    && threat.speed() >= speed() * 0.95f) {
                fastPursuer = true;
                break;
            }
        }

        return attackersAfterStep > 0
                || (incomingAfterStep > 0.01f && fastPursuer)
                || risk.ttd <= 3.5f;
    }

    private boolean tryUseHastePotion() {
        if (buff(Haste.class) != null || buff(Stamina.class) != null) {
            return false;
        }

        Potion potion = inventory.takeOneAutoHastePotion();
        if (!(potion instanceof PotionOfHaste)) {
            return false;
        }

        Buff.prolong(this, Haste.class, Haste.DURATION);
        Catalog.countUse(PotionOfHaste.class);
        SpellSprite.show(this, SpellSprite.HASTE, 1f, 1f, 0f);
        Sample.INSTANCE.play(Assets.Sounds.DRINK);
        spend(TICK);
        return true;
    }

    private boolean tryUseCombatStamina(Mob targetMob, ArrayList<Mob> threats) {
        if (targetMob == null
                || threats == null
                || threats.isEmpty()
                || combatRetreating
                || buff(Stamina.class) != null
                || buff(Haste.class) != null
                || buff(Invisibility.class) != null) {
            return false;
        }

        boolean rangedPressure = hasRangedPressure(threats);
        boolean multipleThreats = threats.size() >= 2;
        boolean bossFight = Char.hasProp(targetMob, Char.Property.BOSS)
                || Char.hasProp(targetMob, Char.Property.MINIBOSS);

        float outgoing = estimateOutgoingDpt(targetMob);
        boolean shortTrivialFight = threats.size() == 1
                && !rangedPressure
                && !bossFight
                && outgoing > 0.01f
                && targetMob.HP <= outgoing;

        if (shortTrivialFight || (!multipleThreats && !rangedPressure && !bossFight)) {
            return false;
        }

        Potion potion = inventory.takeOneAutoStaminaPotion();
        if (!(potion instanceof PotionOfStamina)) {
            return false;
        }

        Buff.prolong(this, Stamina.class, Stamina.DURATION);
        Catalog.countUse(PotionOfStamina.class);
        SpellSprite.show(this, SpellSprite.HASTE, 0.5f, 1f, 0.5f);
        Sample.INSTANCE.play(Assets.Sounds.DRINK);
        spend(TICK);
        return true;
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

        // Adjacent is deliberately too close for the low-health Hero rally. Move one step away when a
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
        clearRangedLurePlan();
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
        clearRangedLurePlan();

        // Once invisibility has been spent as an escape resource, preserve it: move away instead
        // of immediately breaking it with another attack or offensive utility.
        if (buff(Invisibility.class) != null) {
            int invisibleEscapeStep = rooted ? -1 : chooseEscapeStep(threats);
            if (invisibleEscapeStep != -1) {
                int oldPos = pos;
                move(invisibleEscapeStep, true);
                spend(1 / speed());
                Dungeon.level.updateFieldOfView(this, fieldOfView);
                revealVisibleCells();
                return moveSprite(oldPos, pos);
            }
            spend(TICK);
            return true;
        }

        Boolean escapeUtility = tryEscapeUtility(threats);
        if (escapeUtility != null) {
            return escapeUtility;
        }

        if (tryUseCleansingPotion(risk)) {
            return true;
        }

        Boolean retreatPlant = tryKnownRetreatPlant(risk, threats);
        if (retreatPlant != null) {
            return retreatPlant;
        }

        int escapeStep = rooted ? -1 : chooseEscapeStep(threats);
        if (escapeStep != -1) {
            if (shouldUseHasteForRetreat(risk, threats, escapeStep)
                    && tryUseHastePotion()) {
                return true;
            }

            int oldPos = pos;
            move(escapeStep, true);
            spend(1 / speed());
            Dungeon.level.updateFieldOfView(this, fieldOfView);
            revealVisibleCells();
            return moveSprite(oldPos, pos);
        }

        // No safe movement remains. Controlled Blink is preferred to random teleportation.
        if (tryEmergencyBlinkRunestone(threats)) {
            return true;
        }

        if (tryUseTeleportationScroll()) {
            return true;
        }

        // Other control runestones remain ahead of consumable fear/invisibility resources.
        if (tryEmergencyRunestone(risk, threats)) {
            return true;
        }

        // Potions/scrolls remain the next emergency layer.
        if (tryEmergencyEscapeConsumable(risk, threats)) {
            return true;
        }

        // If control resources are unavailable, fall back to immediate shielding/healing.
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
     * Ranged enemies are often weakest once CoHero reaches melee. If melee can be established in
     * one safe step, close immediately. Otherwise, prefer a nearby known LOS break and wait there
     * briefly for the ranged enemy to advance instead of walking straight through its fire.
     */
    private Boolean tryRangedEngagement(Mob targetMob, ArrayList<Mob> threats) {
        if (weapon() == null || targetMob == null || threats == null || threats.isEmpty()) {
            clearRangedLurePlan();
            return null;
        }

        if (rangedLureTargetId != -1 && rangedLureTargetId != targetMob.id()) {
            clearRangedLurePlan();
        }

        // If melee is already legal, let the ordinary combat path attack immediately.
        if (canAttack(targetMob)) {
            clearRangedLurePlan();
            return null;
        }

        boolean rangedPressure = isCurrentRangedPressure(targetMob);
        boolean continuingLure = rangedLureTargetId == targetMob.id();
        if (!rangedPressure && !continuingLure) {
            return null;
        }

        int chargeStep = chooseOneStepMeleeApproach(targetMob, threats);
        if (chargeStep != -1) {
            clearRangedLurePlan();
            return moveForRangedEngagement(chargeStep);
        }

        if (continuingLure) {
            // If the current cell has become proper cover after the enemy moved, hold here rather
            // than walking back to an obsolete planned cover cell.
            if (!rangedPressure && isRangedCoverCell(pos, targetMob)) {
                rangedLureCoverCell = pos;
            }

            if (rangedLureCoverCell != -1
                    && isRangedCoverCell(rangedLureCoverCell, targetMob)) {
                if (pos != rangedLureCoverCell) {
                    int step = rangedLureStep(rangedLureCoverCell);
                    if (step != -1) {
                        rangedLureWaitTurns = 0;
                        return moveForRangedEngagement(step);
                    }
                    clearRangedLurePlan();
                    return null;
                }

                // We have broken the enemy's line of sight. Do not immediately leave cover just
                // because CoHero still has some ranged option; force the ranged enemy to advance.
                if (!rangedPressure) {
                    if (++rangedLureWaitTurns <= RANGED_LURE_MAX_WAIT_TURNS) {
                        spend(TICK);
                        return true;
                    }
                    clearRangedLurePlan();
                    return null;
                }
            }
        }

        if (!rangedPressure) {
            return null;
        }

        int coverCell = chooseRangedCoverCell(targetMob, threats);
        if (coverCell == -1) {
            clearRangedLurePlan();
            return null;
        }

        rangedLureTargetId = targetMob.id();
        rangedLureCoverCell = coverCell;
        rangedLureWaitTurns = 0;

        int step = rangedLureStep(coverCell);
        if (step == -1) {
            clearRangedLurePlan();
            return null;
        }
        return moveForRangedEngagement(step);
    }

    private boolean isCurrentRangedPressure(Mob targetMob) {
        return targetMob != null
                && targetMob.isAlive()
                && Dungeon.level.distance(targetMob.pos, pos) > 1
                && targetMob.coHeroCanAttackFrom(targetMob.pos, this);
    }

    /**
     * A "close" ranged enemy is one CoHero can put into legal melee range with one safe movement
     * step. This respects long-reach melee weapons through canAttackFrom().
     */
    private int chooseOneStepMeleeApproach(Mob targetMob, ArrayList<Mob> threats) {
        if (rooted || targetMob == null) {
            return -1;
        }

        int best = -1;
        int bestAttackers = Integer.MAX_VALUE;
        float bestIncoming = Float.POSITIVE_INFINITY;
        int bestDistance = Integer.MAX_VALUE;

        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = pos + offset;
            if (!Dungeon.level.insideMap(cell)
                    || Dungeon.level.distance(pos, cell) != 1
                    || !Dungeon.level.passable[cell]
                    || !isMovementSafe(cell)
                    || (!fieldOfView[cell] && !isKnown(cell))
                    || Actor.findChar(cell) != null
                    || !canAttackFrom(cell, targetMob)) {
                continue;
            }

            int attackers = countCurrentAttackersAtCell(cell, threats);
            float incoming = estimatedIncomingDptAtCell(cell, threats);
            int distance = Dungeon.level.distance(cell, targetMob.pos);

            if (best == -1
                    || attackers < bestAttackers
                    || (attackers == bestAttackers && incoming < bestIncoming - 0.01f)
                    || (attackers == bestAttackers
                        && Math.abs(incoming - bestIncoming) <= 0.01f
                        && distance < bestDistance)) {
                best = cell;
                bestAttackers = attackers;
                bestIncoming = incoming;
                bestDistance = distance;
            }
        }

        return best;
    }

    private int chooseRangedCoverCell(Mob targetMob, ArrayList<Mob> threats) {
        if (targetMob == null
                || targetMob.fieldOfView == null
                || targetMob.fieldOfView.length != Dungeon.level.length()) {
            return -1;
        }

        boolean[] passable = rangedLurePassable();
        PathFinder.buildDistanceMap(pos, passable);

        ArrayList<Integer> candidates = new ArrayList<>();
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (cell == pos
                    || PathFinder.distance[cell] == Integer.MAX_VALUE
                    || PathFinder.distance[cell] > RANGED_COVER_SEARCH_RADIUS
                    || !isRangedCoverCell(cell, targetMob)) {
                continue;
            }
            candidates.add(cell);
        }

        int best = -1;
        int bestScore = Integer.MAX_VALUE;
        for (int cell : candidates) {
            PathFinder.Path route =
                    Dungeon.findPath(this, cell, passable, fieldOfView, true);
            if (route == null
                    || route.isEmpty()
                    || route.size() > RANGED_COVER_SEARCH_RADIUS) {
                continue;
            }

            int exposedSteps = 0;
            for (int routeCell : route) {
                if (targetMob.fieldOfView[routeCell]) {
                    exposedSteps++;
                }
            }

            int attackers = countCurrentAttackersAtCell(cell, threats);
            int targetDistance = Dungeon.level.distance(cell, targetMob.pos);

            // Reaching cover quickly matters most. Remaining exposed to the shooter while moving
            // and choosing cover that is still attackable by other threats are both expensive.
            int score = route.size() * 24
                    + exposedSteps * 80
                    + attackers * 120
                    + targetDistance * 4;

            if (best == -1 || score < bestScore || (score == bestScore && cell < best)) {
                best = cell;
                bestScore = score;
            }
        }
        return best;
    }

    private boolean isRangedCoverCell(int cell, Mob targetMob) {
        if (targetMob == null
                || targetMob.fieldOfView == null
                || targetMob.fieldOfView.length != Dungeon.level.length()
                || !Dungeon.level.insideMap(cell)
                || !Dungeon.level.passable[cell]
                || !isKnown(cell)
                || !isMovementSafe(cell)
                || targetMob.fieldOfView[cell]) {
            return false;
        }

        if (!fieldOfView[cell]) {
            return true;
        }

        Char occupant = Actor.findChar(cell);
        return occupant == null || occupant == this;
    }

    private boolean[] rangedLurePassable() {
        boolean[] result = Dungeon.level.passable.clone();
        for (int cell = 0; cell < result.length; cell++) {
            if (cell == pos) {
                result[cell] = true;
                continue;
            }

            if (!result[cell] || !isKnown(cell) || !isMovementSafe(cell)) {
                result[cell] = false;
                continue;
            }

            // Only use currently visible occupancy information. Do not inspect actors hidden
            // behind cover merely to improve pathfinding.
            if (fieldOfView[cell]) {
                Char occupant = Actor.findChar(cell);
                if (occupant != null && occupant != this) {
                    result[cell] = false;
                }
            }
        }
        return result;
    }

    private int rangedLureStep(int destination) {
        if (rooted || destination == pos || !Dungeon.level.insideMap(destination)) {
            return -1;
        }

        boolean[] passable = rangedLurePassable();
        int step = Dungeon.findStep(this, destination, passable, fieldOfView, true);
        return step != -1 && isMovementSafe(step) ? step : -1;
    }

    private Boolean moveForRangedEngagement(int step) {
        if (step == -1 || step == pos) {
            return null;
        }

        int oldPos = pos;
        path = null;
        move(step, true);
        spend(1 / speed());
        Dungeon.level.updateFieldOfView(this, fieldOfView);
        revealVisibleCells();
        return moveSprite(oldPos, pos);
    }

    /**
     * Once cover hides the target, do not read the target actor's hidden position. CoHero merely
     * waits at the already-known cover cell for a few turns; normal perception resumes the tactic
     * when the enemy becomes visible again.
     */
    private Boolean continueRangedLureWithoutVisibleThreat() {
        if (rangedLureTargetId == -1) {
            return null;
        }

        if (lowHealthRally || combatRetreating) {
            clearRangedLurePlan();
            return null;
        }

        if (rangedLureCoverCell != -1 && pos != rangedLureCoverCell) {
            int step = rangedLureStep(rangedLureCoverCell);
            if (step != -1) {
                return moveForRangedEngagement(step);
            }
            clearRangedLurePlan();
            return null;
        }

        if (++rangedLureWaitTurns <= RANGED_LURE_MAX_WAIT_TURNS) {
            spend(TICK);
            return true;
        }

        clearRangedLurePlan();
        return null;
    }

    private void clearRangedLurePlan() {
        rangedLureTargetId = -1;
        rangedLureCoverCell = -1;
        rangedLureWaitTurns = 0;
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

        Boolean wardRecall = tryWardRecall(targetMob);
        if (wardRecall != null) {
            return wardRecall;
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
                    && !missile.cursed
                    && new Ballistica(pos, targetMob.pos, Ballistica.PROJECTILE).collisionPos == targetMob.pos) {
                missiles.add(missile);
            }
        }

        SpiritBow spiritBow = inventory.spiritBow();
        MissileWeapon spiritArrow = null;
        if (spiritBow != null
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

    private Boolean tryWardRecall(Mob targetMob) {
        if (targetMob == null || rooted) {
            return null;
        }

        ArrayList<Mob> threats = visibleAwakeEnemies();
        if (anyThreatCanAttackNow(threats)) {
            return null;
        }

        CoHeroWardingPlanner.RecallPlan best = null;
        for (Wand candidate : inventory.wands()) {
            if (!(candidate instanceof WandOfWarding)) {
                continue;
            }

            CoHeroWardingPlanner.RecallPlan plan =
                    CoHeroWardingPlanner.chooseRecall(
                            (WandOfWarding) candidate, this, targetMob);
            if (plan != null && (best == null || plan.gain > best.gain)) {
                best = plan;
            }
        }

        if (best == null || best.ward == null || !best.ward.isAlive()) {
            return null;
        }

        Ward ward = best.ward;
        if (Dungeon.level.adjacent(pos, ward.pos)) {
            if (ward.coHeroDismiss(this)) {
                path = null;
                spend(TICK);
                return true;
            }
            return null;
        }

        int approach = chooseWardRecallApproachCell(ward);
        if (approach == -1) {
            return null;
        }

        PathFinder.Path recallPath =
                Dungeon.findPath(this, approach, Dungeon.level.passable, fieldOfView, true);
        if (recallPath == null || recallPath.isEmpty()) {
            return null;
        }

        int step = recallPath.getFirst();
        if (!isMovementSafe(step)) {
            return null;
        }

        Char blocker = Actor.findChar(step);
        if (blocker != null && blocker != this) {
            return null;
        }

        int oldPos = pos;
        move(step, true);
        spend(1 / speed());
        Dungeon.level.updateFieldOfView(this, fieldOfView);
        revealVisibleCells();
        return moveSprite(oldPos, pos);
    }

    private int chooseWardRecallApproachCell(Ward ward) {
        int bestCell = -1;
        int bestDistance = Integer.MAX_VALUE;

        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = ward.pos + offset;
            if (!Dungeon.level.insideMap(cell)
                    || Dungeon.level.distance(ward.pos, cell) != 1
                    || !Dungeon.level.passable[cell]
                    || !isMovementSafe(cell)) {
                continue;
            }

            Char occupant = Actor.findChar(cell);
            if (occupant != null && occupant != this) {
                continue;
            }

            if (cell == pos) {
                return cell;
            }

            PathFinder.Path recallPath =
                    Dungeon.findPath(this, cell, Dungeon.level.passable, fieldOfView, true);
            if (recallPath == null) {
                continue;
            }

            int distance = recallPath.size();
            if (bestCell == -1
                    || distance < bestDistance
                    || (distance == bestDistance && cell < bestCell)) {
                bestCell = cell;
                bestDistance = distance;
            }
        }

        return bestCell;
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
            if (supportedMissileWeapon(missile) && !missile.cursed) {
                return true;
            }
        }
        SpiritBow spiritBow = inventory.spiritBow();
        if (spiritBow != null && !spiritBow.cursed) {
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
        // Blast Wave is a survival tool when a safe blast reduces next-turn attackers,
        // even when another wand would deal more raw damage.
        for (Wand wand : inventory.wands()) {
            int blastAim = CoHeroWandAdapter.blastWaveEscapeAim(wand, this, visibleThreats);
            if (blastAim != -1) {
                return performWandCast(blastAim, wand);
            }
        }

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

    private boolean recoverPreferredLootAtCurrentCell() {
        Heap heap = Dungeon.level.heaps.get(pos);
        if (heap == null || heap.type != Heap.Type.HEAP || heap.hidden) {
            return false;
        }

        Item selected = null;
        boolean selectedOwnedMissile = false;

        // Recover CoHero's own thrown weapon before taking unrelated loot from the same heap.
        for (Item item : new ArrayList<>(heap.items)) {
            if (!(item instanceof MissileWeapon)) {
                continue;
            }
            MissileWeapon missile = (MissileWeapon) item;
            Integer outstanding = thrownOutstanding.get(missile.setID);
            if (outstanding != null
                    && outstanding > 0
                    && supportedMissileWeapon(missile)
                    && inventory.canAddToBackpack(missile)) {
                selected = missile;
                selectedOwnedMissile = true;
                break;
            }
        }

        // Gold never consumes backpack capacity, so prefer it over unrelated ranged loot.
        if (selected == null) {
            for (Item item : new ArrayList<>(heap.items)) {
                if (item instanceof Gold) {
                    selected = item;
                    break;
                }
            }
        }

        if (selected == null) {
            for (Item item : new ArrayList<>(heap.items)) {
                if (item instanceof MissileWeapon) {
                    MissileWeapon missile = (MissileWeapon) item;
                    if (supportedMissileWeapon(missile) && inventory.canAddToBackpack(missile)) {
                        selected = missile;
                        break;
                    }
                } else if (item instanceof Wand) {
                    Wand wand = (Wand) item;
                    if (CoHeroWandAdapter.supported(wand) && inventory.canAddToBackpack(wand)) {
                        selected = wand;
                        break;
                    }
                }
            }
        }

        if (selected == null) {
            return false;
        }

        heap.remove(selected);

        if (selected instanceof Gold) {
            collectGold((Gold) selected);
            return true;
        }

        if (!inventory.addToBackpack(selected)) {
            Dungeon.level.drop(selected, pos).sprite.drop();
            return false;
        }

        if (selectedOwnedMissile) {
            MissileWeapon missile = (MissileWeapon) selected;
            markRecovered(missile.setID, missile.quantity());
        }
        return true;
    }

    private void collectGold(Gold gold) {
        int amount = gold.quantity();
        Catalog.setSeen(Gold.class);
        Statistics.itemTypesDiscovered.add(Gold.class);
        Dungeon.gold += amount;
        Statistics.goldCollected += amount;
        Badges.validateGoldCollected();

        GameScene.pickUp(gold, pos);
        if (sprite != null) {
            sprite.showStatusWithIcon(
                    CharSprite.NEUTRAL,
                    Integer.toString(amount),
                    FloatingText.GOLD);
        }
        Sample.INSTANCE.play(
                Assets.Sounds.GOLD,
                1,
                1,
                Random.Float(0.9f, 1.1f));
    }

    private int nearestPreferredLootCell() {
        int bestOwnedCell = -1;
        int bestOwnedDistance = Integer.MAX_VALUE;
        int bestLootCell = -1;
        int bestLootDistance = Integer.MAX_VALUE;

        boolean[] safePassable = lootRecoveryPassable();

        for (int cell : Dungeon.level.heaps.keyArray()) {
            Heap heap = Dungeon.level.heaps.get(cell);
            if (heap == null || heap.type != Heap.Type.HEAP || heap.hidden) {
                continue;
            }

            Char occupant = Actor.findChar(cell);
            if (occupant != null && occupant != this) {
                continue;
            }

            boolean ownedCandidate = false;
            boolean lootCandidate = false;

            for (Item item : heap.items) {
                if (item instanceof MissileWeapon) {
                    MissileWeapon missile = (MissileWeapon) item;
                    Integer outstanding = thrownOutstanding.get(missile.setID);
                    if (outstanding != null
                            && outstanding > 0
                            && supportedMissileWeapon(missile)
                            && inventory.canAddToBackpack(missile)) {
                        ownedCandidate = true;
                        break;
                    }

                    if (isKnown(cell)
                            && supportedMissileWeapon(missile)
                            && inventory.canAddToBackpack(missile)) {
                        lootCandidate = true;
                    }
                } else if (item instanceof Wand) {
                    Wand wand = (Wand) item;
                    if (isKnown(cell)
                            && CoHeroWandAdapter.supported(wand)
                            && inventory.canAddToBackpack(wand)) {
                        lootCandidate = true;
                    }
                } else if (item instanceof Gold && isKnown(cell)) {
                    lootCandidate = true;
                }
            }

            if (!ownedCandidate && !lootCandidate) {
                continue;
            }

            int distance = lootRecoveryPathDistance(cell, safePassable);
            if (distance == Integer.MAX_VALUE) {
                continue;
            }

            if (ownedCandidate) {
                if (distance < bestOwnedDistance
                        || (distance == bestOwnedDistance
                        && (bestOwnedCell == -1 || cell < bestOwnedCell))) {
                    bestOwnedDistance = distance;
                    bestOwnedCell = cell;
                }
            } else if (distance < bestLootDistance
                    || (distance == bestLootDistance
                    && (bestLootCell == -1 || cell < bestLootCell))) {
                bestLootDistance = distance;
                bestLootCell = cell;
            }
        }

        return bestOwnedCell != -1 ? bestOwnedCell : bestLootCell;
    }

    private boolean[] lootRecoveryPassable() {
        boolean[] result = Dungeon.level.passable.clone();
        for (int cell = 0; cell < result.length; cell++) {
            if (cell != pos && result[cell] && !isMovementSafe(cell)) {
                result[cell] = false;
            }
        }
        result[pos] = true;
        return result;
    }

    private int lootRecoveryPathDistance(int cell, boolean[] safePassable) {
        if (cell == pos) {
            return 0;
        }

        PathFinder.Path recoveryPath =
                Dungeon.findPath(this, cell, safePassable, fieldOfView, true);
        return recoveryPath == null ? Integer.MAX_VALUE : recoveryPath.size();
    }

    private int lootRecoveryStep(int cell) {
        if (rooted || cell == pos || !Dungeon.level.insideMap(cell)) {
            return -1;
        }

        boolean[] safePassable = lootRecoveryPassable();
        int step = Dungeon.findStep(this, cell, safePassable, fieldOfView, true);
        return step != -1 && isMovementSafe(step) ? step : -1;
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

    private boolean isExitKnown() {
        int exit = Dungeon.level.exit();
        return isKnown(exit);
    }

    private boolean[] preExitExplorationArea() {
        if (isExitKnown() || Dungeon.hero == null || !Dungeon.hero.isAlive()) {
            return null;
        }

        PathFinder.buildDistanceMap(Dungeon.hero.pos, Dungeon.level.passable);

        ArrayList<Integer> reachable = new ArrayList<>();
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (Dungeon.level.passable[cell]
                    && Dungeon.level.discoverable[cell]
                    && PathFinder.distance[cell] < Integer.MAX_VALUE) {
                reachable.add(cell);
            }
        }

        if (reachable.isEmpty()) {
            return null;
        }

        reachable.sort((a, b) -> Integer.compare(PathFinder.distance[a], PathFinder.distance[b]));
        int areaSize = Math.max(
                1,
                (reachable.size() * PRE_EXIT_EXPLORATION_PERCENT + 99) / 100);
        int maxDistance = PathFinder.distance[reachable.get(areaSize - 1)];

        boolean[] allowed = new boolean[Dungeon.level.length()];
        for (int cell : reachable) {
            if (PathFinder.distance[cell] > maxDistance) {
                break;
            }
            allowed[cell] = true;
        }
        return allowed;
    }

    private boolean explorationAreaAllows(boolean[] explorationArea, int cell) {
        return explorationArea == null
                || (cell >= 0 && cell < explorationArea.length && explorationArea[cell]);
    }

    private boolean hasUnexploredFrontier(boolean[] explorationArea) {
        PathFinder.buildDistanceMap(pos, Dungeon.level.passable);
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (cell != pos
                    && explorationAreaAllows(explorationArea, cell)
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

    private int chooseExplorationTarget(boolean[] explorationArea) {
        PathFinder.buildDistanceMap(pos, Dungeon.level.passable);

        ArrayList<Integer> unknown = new ArrayList<>();
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (cell != pos
                    && explorationAreaAllows(explorationArea, cell)
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
        return chooseExploredRoamingTarget(explorationArea);
    }

    private int chooseExploredRoamingTarget(boolean[] explorationArea) {
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
            if (!explorationAreaAllows(explorationArea, cell)
                    || cell == pos
                    || cell == Dungeon.hero.pos
                    || !isMovementSafe(cell)) {
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
