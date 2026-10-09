package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.duelist.Challenge;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.DirectableAlly;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClassArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfAccuracy;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfEvasion;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfHaste;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfMight;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfForce;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfFuror;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfTenacity;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.SpiritBow;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.missiles.MissileWeapon;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.utils.Bundle;
import com.watabou.utils.Random;

import java.util.ArrayList;

public class CoHeroAlly extends DirectableAlly {

    private static final String EXPLORATION_TARGET = "cohero_exploration_target";
    private static final String EXPLORATION_TARGET_ROAMING = "cohero_exploration_target_roaming";
    private static final String INVENTORY = "cohero_inventory";
    private static final String LOW_HEALTH_RALLY = "cohero_low_health_rally";
    private static final String DEBUG_LOG = "cohero_debug_log";

    private final CoHeroNavigation navigation = new CoHeroNavigation(this);
    private final CoHeroGuardController guard = new CoHeroGuardController(this);
    private final CoHeroSupportController support = new CoHeroSupportController(this);
    private final CoHeroCombatObjectiveController combatObjective =
            new CoHeroCombatObjectiveController(this);
    private final CoHeroCombatController combat = new CoHeroCombatController(this);
    private final CoHeroVision vision = new CoHeroVision(this);
    private final CoHeroLoot loot = new CoHeroLoot(this);
    private final CoHeroCombatRiskEstimator riskEstimator = new CoHeroCombatRiskEstimator(this);
    private final CoHeroSurvivalController survival = new CoHeroSurvivalController(this);
    private final CoHeroControlItems controlItems = new CoHeroControlItems(this);
    private final CoHeroRevivalController revival = new CoHeroRevivalController(this);
    private final CoHeroUnderFireController underFire = new CoHeroUnderFireController(this);
    private int syncedLevel = 1;
    private final CompanionInventory inventory = new CompanionInventory(this);
    MissileWeapon activeMissileWeapon;
    private String lastBossDecisionLog;
    private String movementDecision = "unspecified";
    private int movementDecisionTarget = -1;
    private boolean debugLogEnabled;
    private boolean inCombat;
    private final CoHeroTurnContext turnContext = new CoHeroTurnContext(this);

    {
        spriteClass = CoHeroAllySprite.class;
        HT = HP = 20;
        attacksAutomatically = false;
    }

    public CompanionInventory inventory() {
        return inventory;
    }

    CoHeroLoot loot() {
        return loot;
    }

    CoHeroTimings timings() {
        return CoHero.timings();
    }

    CoHeroTurnContext currentTurnContext() {
        return turnContext.isActive() ? turnContext : null;
    }

    boolean lowHealthRally() {
        return support.isLowHealthRally();
    }

    boolean isLowHealth() {
        return support.isBelowLowHealthThreshold();
    }

    boolean inCombat() {
        return inCombat;
    }

    void clearLowHealthRally() {
        support.clearLowHealthRally();
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

    /**
     * Returns the live sprite linked to this actor in the current scene.
     * Mob.sprite() is a factory that creates an unlinked sprite and must not be used for actions.
     */
    CharSprite attachedSprite() {
        if (sprite != null && sprite.ch != this) {
            throw new IllegalStateException("CoHero live sprite is not linked to its actor");
        }
        return sprite;
    }

    @Override
    public CharSprite sprite() {
        CoHeroAllySprite preview = new CoHeroAllySprite();
        preview.updateArmor(armorTier());
        return preview;
    }

    @Override
    public String description() {
        return CoHeroMessages.get("companion.desc");
    }

    @Override
    public String name() {
        HeroClass heroClass = CoHero.companionClass();
        return heroClass == null ? CoHeroMessages.get("companion.name") : heroClass.title();
    }

    @Override
    public void storeInBundle(Bundle bundle) {
        super.storeInBundle(bundle);
        bundle.put(EXPLORATION_TARGET, navigation.explorationTarget());
        bundle.put(EXPLORATION_TARGET_ROAMING, navigation.explorationTargetRoaming());

        Bundle inventoryBundle = new Bundle();
        inventory.storeInBundle(inventoryBundle);
        bundle.put(INVENTORY, inventoryBundle);

        loot.storeInBundle(bundle);
        bundle.put(LOW_HEALTH_RALLY, support.isLowHealthRally());
        bundle.put(DEBUG_LOG, debugLogEnabled);
    }

    @Override
    public void restoreFromBundle(Bundle bundle) {
        super.restoreFromBundle(bundle);

        syncedLevel = level();

        navigation.restoreExplorationTarget(
                bundle.contains(EXPLORATION_TARGET)
                        ? bundle.getInt(EXPLORATION_TARGET)
                        : -1,
                bundle.getBoolean(EXPLORATION_TARGET_ROAMING));

        if (bundle.contains(INVENTORY)) {
            inventory.restoreFromBundle(bundle.getBundle(INVENTORY));
        }

        support.restoreLowHealthRally(bundle.getBoolean(LOW_HEALTH_RALLY));
        debugLogEnabled = bundle.getBoolean(DEBUG_LOG);

        loot.restoreFromBundle(bundle);
        underFire.reset();

        // Mob/DirectableAlly serializes its own AI state, but CoHero decisions are rebuilt from
        // live state. Never carry inherited HUNTING/enemy/target/path decisions across a load.
        resetInheritedDecisionState();
    }

    int enemySpawnMultiplierQuarters() {
        CompanionEnemySurge surge = buff(CompanionEnemySurge.class);
        return surge == null
                ? CompanionEnemySurge.DEFAULT_MULTIPLIER_QUARTERS
                : surge.multiplierQuarters();
    }

    void setEnemySpawnMultiplierQuarters(int value) {
        CompanionEnemySurge surge = Buff.affect(this, CompanionEnemySurge.class);
        if (surge == null) {
            throw new IllegalStateException("CoHero enemy surge buff could not be attached");
        }
        surge.setMultiplierQuarters(value);
    }

    void enterLevel(int cell) {
        if (!isAlive()) {
            throw new IllegalStateException("Cannot move a dead CoHero companion to a new level");
        }

        pos = cell;
        navigation.clearExplorationTarget();
        loot.resetForLevel();
        underFire.reset();
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
        inCombat = false;
        timeToNow();

        // Recreate item-owned buffs against this live Char after save restoration / floor transfer.
        inventory.rebuildPassiveEffects();
        syncedLevel = level();
        updateHT(false);
        Buff.affect(this, CompanionRegeneration.class);
        Buff.affect(this, CompanionEnemySurge.class);
        syncViewDistance();
    }

    void relocateImmediately(int cell) {
        if (Dungeon.level == null
                || cell < 0
                || cell >= Dungeon.level.length()
                || !Dungeon.level.passable[cell]) {
            throw new IllegalArgumentException("Invalid CoHero forced relocation destination: " + cell);
        }
        Char occupant = Actor.findChar(cell);
        if (occupant != null && occupant != this) {
            throw new IllegalArgumentException("CoHero forced relocation destination is occupied: " + cell);
        }

        pos = cell;
        navigation.clearExplorationTarget();
        underFire.reset();
        path = null;
        target = -1;
        enemy = null;
        enemyID = -1;
        enemySeen = false;
        alerted = false;
        path = null;
        defendingPos = -1;
        movingToDefendPos = false;
        inCombat = false;

        if (sprite != null) {
            sprite.interruptMotion();
            sprite.place(pos);
        }

        if (fieldOfView == null || fieldOfView.length != Dungeon.level.length()) {
            fieldOfView = new boolean[Dungeon.level.length()];
        }
        Dungeon.level.occupyCell(this);
        Dungeon.level.updateFieldOfView(this, fieldOfView);
        revealVisibleCells();
    }

    void syncViewDistance() {
        vision.syncViewDistance();
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

    boolean hasMeleeCombatCapability() {
        return weapon() != null || buff(RingOfForce.Force.class) != null;
    }

    @Override
    protected boolean canAttack(Char enemy) {
        if (weapon() == null) {
            return buff(RingOfForce.Force.class) != null && super.canAttack(enemy);
        }
        return super.canAttack(enemy) || weapon().canReach(this, enemy.pos);
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

    int attackSkillWith(Weapon attackWeapon, Char target) {
        float accuracy = 9 + level();
        accuracy *= RingOfAccuracy.accuracyMultiplier(this);

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
            if (buff(RingOfForce.Force.class) == null) {
                return super.damageRoll();
            }
            return Random.NormalIntRange(
                    RingOfForce.coHeroUnarmedMinDamage(this, STR()),
                    RingOfForce.coHeroUnarmedMaxDamage(this, STR()));
        }

        int damage = attackWeapon.damageRoll(this);
        if (!(attackWeapon instanceof MissileWeapon)) {
            damage += RingOfForce.armedDamageBonus(this);
        }
        int excessStrength = STR() - attackWeapon.STRReq();
        if (excessStrength > 0) {
            damage += Random.NormalIntRange(0, excessStrength);
        }
        return damage;
    }

    float meleeAttackDelay(MeleeWeapon attackWeapon) {
        float delay = super.attackDelay();
        if (attackWeapon != null) {
            delay *= attackWeapon.delayFactor(this);
            delay /= CoHeroClassTraits.meleeAttackSpeedMultiplier(this);
            int encumbrance = Math.max(0, attackWeapon.STRReq() - STR());
            if (encumbrance > 0) {
                delay *= Math.pow(1.2, encumbrance);
            }
        } else if (buff(RingOfForce.Force.class) != null) {
            // Stock Hero semantics apply Furor to Ring of Force unarmed attacks too.
            delay /= RingOfFuror.attackSpeedMultiplier(this);
            delay /= CoHeroClassTraits.meleeAttackSpeedMultiplier(this);
        }
        return delay;
    }

    @Override
    public float attackDelay() {
        if (activeMissileWeapon != null) {
            return super.attackDelay() * activeMissileWeapon.delayFactor(this);
        }
        return meleeAttackDelay(weapon());
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
        int oldPos = pos;

        String blockedMovement = guard.blockedMovementReason(step);
        if (blockedMovement != null) {
            logMovement(blockedMovement, oldPos, step);
            return;
        }

        super.move(step, travelling);
        logMovement(pos == oldPos ? "NO_MOVE" : "MOVE", oldPos, step);
        if (sprite != null) {
            sprite.visible = true;
        }
    }

    @Override
    public int defenseSkill(Char enemy) {
        float evasion = (4 + level())
                * RingOfEvasion.evasionMultiplier(this);
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
    public void fixTime(float decrement) {
        super.fixTime(decrement);
        underFire.fixTime(decrement);
    }

    @Override
    public void damage(int damage, Object source) {
        int adjusted = (int) Math.ceil(
                Math.max(0, damage)
                        * RingOfTenacity.damageMultiplier(this)
                        * CoHeroClassTraits.intrinsicTenacityDamageMultiplier(this));
        super.damage(adjusted, source);
        if (adjusted > 0 && isAlive()) {
            underFire.observeDamage(source);
        }
    }

    @Override
    public float resist(Class effect) {
        return super.resist(effect)
                * CoHeroClassTraits.elementsResistanceMultiplier(this, effect);
    }

    @Override
    public int defenseProc(Char enemy, int damage) {
        Armor equippedArmor = armor();
        if (equippedArmor != null) {
            damage = equippedArmor.proc(enemy, this, damage);
            equippedArmor.coHeroUseForIdentification();
        }

        damage = CoHeroWandAdapter.absorbLivingEarthArmor(this, damage);

        return super.defenseProc(enemy, damage);
    }

    void logBossDecision(String key, String detail) {
        if (!debugLogEnabled || Dungeon.level == null || !Dungeon.level.locked) {
            lastBossDecisionLog = null;
            return;
        }
        if (key.equals(lastBossDecisionLog)) {
            return;
        }
        lastBossDecisionLog = key;
        logDebug("CoHero: " + detail);
    }

    String targetDebug(Mob targetMob) {
        if (targetMob == null) {
            return "no target";
        }
        return targetMob.getClass().getSimpleName()
                + " d=" + Dungeon.level.distance(pos, targetMob.pos)
                + " HP=" + HP + "/" + HT;
    }

    private String threatScanDebug() {
        int enemies = 0;
        int inFov = 0;
        int invisibleEnemies = 0;
        int sleepingEnemies = 0;
        for (Mob mob : Dungeon.level.mobs) {
            if (mob == this || mob.alignment != Alignment.ENEMY || !mob.isAlive()) {
                continue;
            }
            enemies++;
            if (mob.pos >= 0
                    && mob.pos < fieldOfView.length
                    && fieldOfView[mob.pos]) {
                inFov++;
            }
            if (mob.invisible > 0) {
                invisibleEnemies++;
            }
            if (mob.state == mob.SLEEPING) {
                sleepingEnemies++;
            }
        }
        return "no visible threat"
                + " (enemies=" + enemies
                + ", inFOV=" + inFov
                + ", invisible=" + invisibleEnemies
                + ", sleeping=" + sleepingEnemies + ")";
    }

    @Override
    protected boolean act() {
        if (turnContext.isActive()) {
            throw new IllegalStateException("CoHero decision turn re-entered before completion");
        }

        long started = timings().startNanos();
        try {
            return decideAction();
        } finally {
            turnContext.end();
            timings().record(this, CoHeroTimings.Action.ACT, started, movementDecision);
        }
    }

    private boolean decideAction() {
        long prepareStarted = timings().startNanos();
        movementDecision = "unspecified";
        movementDecisionTarget = -1;
        combat.beginTurn();
        riskEstimator.beginTurn();
        resetInheritedDecisionState();
        guard.beginTurn();

        syncSharedLevel();
        syncViewDistance();

        if (fieldOfView == null || fieldOfView.length != Dungeon.level.length()) {
            fieldOfView = new boolean[Dungeon.level.length()];
        }
        Dungeon.level.updateFieldOfView(this, fieldOfView);
        revealVisibleCells();
        guard.updateSession();
        combatObjective.update();
        turnContext.begin();

        ArrayList<Mob> visibleThreats = visibleAwakeEnemies();
        inCombat = !visibleThreats.isEmpty();

        if (paralysed > 0) {
            logBossDecision("paralysed", "paralysed");
            spend(TICK);
            return true;
        }

        if (tryAutoTorch()) {
            logBossDecision("auto_torch", "used torch");
            return true;
        }

        support.updateLowHealthRallyState();

        Boolean hazardAvoidance = tryAvoidHazard();
        if (hazardAvoidance != null) {
            logBossDecision("avoid_hazard", "avoiding hazard");
            return hazardAvoidance;
        }
        if (survival.tryUsePurityPotion()) {
            logBossDecision("purity_hazard", "used purity against environmental blob");
            return true;
        }

        Boolean piranhaAvoidance = tryLeavePiranhaDanger();
        if (piranhaAvoidance != null) {
            logBossDecision("avoid_piranha_pool", "leaving Piranha attack zone");
            return piranhaAvoidance;
        }

        Boolean waterWash = navigation.tryWashInWater();
        if (waterWash != null) {
            logBossDecision("wash_in_water", "seeking water to remove Burning or Ooze");
            return waterWash;
        }

        Mob guardSupportThreat =
                guard.isActive() ? support.heroSupportThreat() : null;
        guard.prepareMovementScope(guardSupportThreat);

        timings().record(this, CoHeroTimings.Action.PREPARE, prepareStarted);
        if (visibleThreats.isEmpty() && debugLogEnabled) {
            String detail = threatScanDebug();
            logBossDecision("no_visible_threat:" + detail, detail);
        }

        ArrayList<Mob> combatThreats = combat.collectActiveThreats(visibleThreats);
        if (combatThreats.isEmpty() && combat.hasRecoveringCrystalGuardian(visibleThreats)) {
            clearCombatTarget();
            clearExplorationTarget();
            prepareGuardHeroSupportMovement();
            setMovementDecision("crystal_guardian_recovering_follow", Dungeon.hero.pos);
            logBossDecision("crystal_guardian_recovering",
                    "recovering Crystal Guardian -> follow Hero");
            return support.followHeroDirective();
        }

        if (!combatThreats.isEmpty()) {
            long combatStarted = timings().startNanos();
            try {
                ArrayList<Mob> attackableThreats;
                ArrayList<Mob> charmingThreats;
                Mob combatTarget;
                Mob survivalTarget;

                long combatSetupStarted = timings().startNanos();
                try {
                long setupFilterStarted = timings().startNanos();
                try {
                    attackableThreats = combat.collectAttackableThreats(combatThreats);
                    charmingThreats = combat.collectCharmingThreats(combatThreats);
                } finally {
                    timings().record(
                            this, CoHeroTimings.Action.COMBAT_SETUP_FILTER, setupFilterStarted);
                }

                // Invulnerability does not end the fight. It only has tactical priority while an
                // invulnerable enemy can currently hit CoHero. Once outside that enemy's attack range,
                // ordinary combat against any damageable enemies resumes immediately.
                long setupInvulnerableStarted = timings().startNanos();
                Boolean invulnerableRetreat;
                try {
                    invulnerableRetreat =
                            combat.tryAvoidInvulnerableThreats(combatThreats);
                } finally {
                    timings().record(
                            this,
                            CoHeroTimings.Action.COMBAT_SETUP_INVULNERABLE,
                            setupInvulnerableStarted);
                }
                if (invulnerableRetreat != null) {
                    return invulnerableRetreat;
                }

                // Charm blocks offense against its source in stock Mob AI. CoHero derives the same
                // restriction from the live buff each turn, then first tries to leave the charmer's
                // immediate pressure while preferring safer cells and, when otherwise equal, broken
                // line of sight and greater distance.
                long setupCharmStarted = timings().startNanos();
                Boolean charmRetreat;
                try {
                    charmRetreat =
                            combat.tryAvoidCharmingThreats(charmingThreats, combatThreats);
                } finally {
                    timings().record(
                            this, CoHeroTimings.Action.COMBAT_SETUP_CHARM, setupCharmStarted);
                }
                if (charmRetreat != null) {
                    return charmRetreat;
                }

                if (attackableThreats.isEmpty()) {
                    if (!charmingThreats.isEmpty()) {
                        // A lone ordinary Charm is intentionally not enough to spend cleansing
                        // resources. The existing serious-negative rule still allows cleansing when
                        // low health, rooted, or carrying multiple negative effects.
                        Boolean mageroyalCure = survival.tryKnownMageroyalCurePlant();
                        if (mageroyalCure != null) {
                            return mageroyalCure;
                        }
                        if (survival.tryUseCleansingPotion(null)) {
                            return true;
                        }
                        if (survival.tryAutoSurvivalPotion()) {
                            return true;
                        }

                        logBossDecision("charmed_hold",
                                "all visible offensive targets are charm sources; no safer step -> hold");
                        spend(TICK);
                        return true;
                    }

                    logBossDecision("invulnerable_out_of_range",
                            "no damageable visible enemy; invulnerable threats cannot attack -> hold");
                    spend(TICK);
                    return true;
                }

                long setupTargetStarted = timings().startNanos();
                try {
                    combatTarget = combat.selectCombatTarget(attackableThreats, combatThreats);
                } finally {
                    timings().record(
                            this, CoHeroTimings.Action.COMBAT_SETUP_TARGET, setupTargetStarted);
                }

                long setupSurvivalTargetStarted = timings().startNanos();
                try {
                    survivalTarget = combat.selectSurvivalTarget(attackableThreats);
                } finally {
                    timings().record(
                            this,
                            CoHeroTimings.Action.COMBAT_SETUP_SURVIVAL_TARGET,
                            setupSurvivalTargetStarted);
                }
                } finally {
                    timings().record(
                            this, CoHeroTimings.Action.COMBAT_SETUP, combatSetupStarted);
                }

                long riskStarted = timings().startNanos();
                CoHeroCombatRisk combatRisk;
                try {
                    combatRisk = assessCombatRisk(survivalTarget, combatThreats);
                } finally {
                    timings().record(this, CoHeroTimings.Action.COMBAT_RISK, riskStarted);
                }

                long combatSurvivalStarted = timings().startNanos();
                try {
                Boolean shortBruteRage = combat.tryShortBruteRageTactics(combatThreats);
                if (shortBruteRage != null) {
                    logBossDecision("short_brute_rage",
                            "visible short Brute rage -> escape/wait");
                    return shortBruteRage;
                }

                Boolean monkFocus = combat.tryMonkFocusTactics(
                        combatTarget, combatThreats, combatRisk);
                if (monkFocus != null) {
                    return monkFocus;
                }

                Boolean survivalAction = combat.tryCombatSurvival(combatRisk, combatThreats);
                if (survivalAction != null) {
                    if (debugLogEnabled) {
                        logBossDecision("combat_survival:" + combatTarget.id(),
                                targetDebug(combatTarget)
                                        + " -> survival/retreat"
                                        + " attackers=" + combatRisk.attackersNow
                                        + " ttd=" + String.format("%.1f", combatRisk.ttd)
                                        + " ttk=" + String.format("%.1f", combatRisk.ttk));
                    }
                    return survivalAction;
                }

                Boolean mageroyalCure = survival.tryKnownMageroyalCurePlant();
                if (mageroyalCure != null) {
                    return mageroyalCure;
                }

                if (survival.tryUseCleansingPotion(combatRisk)) {
                    return true;
                }

                if (survival.tryAutoSurvivalPotion()) {
                    return true;
                }
                } finally {
                    timings().record(
                            this, CoHeroTimings.Action.COMBAT_SURVIVAL, combatSurvivalStarted);
                }

                Boolean ownedMissileRecovery = loot.actOwnedMissileRecovery(combatThreats);
                if (ownedMissileRecovery != null) {
                    return ownedMissileRecovery;
                }

                long combatObjectiveStarted = timings().startNanos();
                try {
                Mob assessedSurvivalTarget = survivalTarget;
                Boolean objectiveAction =
                        combatObjective.actBeforeOffense(attackableThreats, visibleThreats);
                if (objectiveAction != null) {
                    return objectiveAction;
                }

                ArrayList<Mob> objectiveThreats =
                        combatObjective.offensiveThreats(attackableThreats);
                if (objectiveThreats.isEmpty()) {
                    throw new IllegalStateException(
                            "Active CoHero combat objective produced no offensive target");
                }

                if (!objectiveThreats.equals(attackableThreats)) {
                    attackableThreats = objectiveThreats;
                    combatTarget = combat.selectCombatTarget(attackableThreats, combatThreats);
                    survivalTarget = combat.selectSurvivalTarget(attackableThreats);
                    if (survivalTarget != assessedSurvivalTarget) {
                        long objectiveRiskStarted = timings().startNanos();
                        try {
                            combatRisk = assessCombatRisk(survivalTarget, combatThreats);
                        } finally {
                            timings().record(
                                    this, CoHeroTimings.Action.COMBAT_RISK, objectiveRiskStarted);
                        }
                    }
                }
                } finally {
                    timings().record(
                            this, CoHeroTimings.Action.COMBAT_OBJECTIVE, combatObjectiveStarted);
                }

                long combatTacticsStarted = timings().startNanos();
                try {
                Boolean armoredBruteRage = combat.tryArmoredBruteRageTactics(
                        combatTarget, combatThreats, combatRisk);
                if (armoredBruteRage != null) {
                    logBossDecision("armored_brute_rage:" + combatTarget.id(),
                            targetDebug(combatTarget) + " -> ranged shield pressure");
                    return armoredBruteRage;
                }

                long encirclementStarted = timings().startNanos();
                Boolean encirclementPositioning;
                try {
                    encirclementPositioning = combat.tryEncirclementPositioning(
                            combatTarget, combatThreats);
                } finally {
                    timings().record(
                            this, CoHeroTimings.Action.MELEE_POSITIONING, encirclementStarted);
                }
                if (encirclementPositioning != null) {
                    logBossDecision("encirclement_positioning:" + combatTarget.id(),
                            targetDebug(combatTarget) + " -> encirclement positioning");
                    return encirclementPositioning;
                }

                Boolean monkOpening = combat.tryMonkOpeningTactics(
                        combatTarget, combatThreats);
                if (monkOpening != null) {
                    return monkOpening;
                }

                Boolean scorpioTactics = combat.tryScorpioTactics(
                        combatTarget, combatThreats, combatRisk);
                if (scorpioTactics != null) {
                    logBossDecision("scorpio_tactics:" + combatTarget.id(),
                            targetDebug(combatTarget) + " -> scorpio tactics");
                    return scorpioTactics;
                }
                } finally {
                    timings().record(
                            this, CoHeroTimings.Action.COMBAT_TACTICS, combatTacticsStarted);
                }

                long combatRangedStarted = timings().startNanos();
                try {
                // Ranged enemies are normally closed to adjacency. Exception: sufficiently stronger
                // ranged offense may keep spacing, or create it against an immobilized/slower target.
                Boolean rangedEngagement = combat.tryRangedEngagement(combatTarget, combatThreats);
                if (rangedEngagement != null) {
                    logBossDecision("ranged_positioning:" + combatTarget.id(),
                            targetDebug(combatTarget) + " -> ranged positioning");
                    return rangedEngagement;
                }

                // Direct ranged offense is a normal combat action, not a last-resort fallback.
                // If the preferred threat cannot be shot, this may select another visible threat that
                // has a legal missile / Spirit Bow / wand line.
                Boolean directRanged = combat.tryDirectRangedAttack(combatTarget, attackableThreats);
                if (directRanged != null) {
                    return directRanged;
                }

                Boolean clearProjectileLine =
                        combat.tryFriendlyBlockedProjectileReposition(
                                combatTarget, combatThreats);
                if (clearProjectileLine != null) {
                    return clearProjectileLine;
                }

                Boolean piranhaSafeRanged =
                        combat.tryPiranhaSafeRangedPositioning(combatTarget);
                if (piranhaSafeRanged != null) {
                    return piranhaSafeRanged;
                }
                } finally {
                    timings().record(
                            this, CoHeroTimings.Action.COMBAT_RANGED, combatRangedStarted);
                }

                long combatActionStarted = timings().startNanos();
                try {
                // Non-emergency consumables and setup should not repeatedly steal turns from an
                // immediately available ranged attack.
                Boolean armorPlant = survival.tryKnownCombatArmorPlant(combatTarget, combatThreats);
                if (armorPlant != null) {
                    return armorPlant;
                }

                if (controlItems.tryUseCombatRunestone(
                        combatTarget, attackableThreats, combatRisk)) {
                    return true;
                }

                if (controlItems.tryUseCombatFrostPotion(
                        combatTarget, attackableThreats, combatRisk)) {
                    return true;
                }

                if (survival.tryUseCombatEarthenArmor(
                        combatTarget, combatThreats, combatRisk)) {
                    return true;
                }

                if (survival.tryUseCombatStamina(
                        combatTarget, combatThreats, combatRisk)) {
                    return true;
                }

                long meleeStarted = timings().startNanos();
                Boolean meleePositioning;
                try {
                    meleePositioning = combat.tryMeleePositioning(combatTarget, combatThreats);
                } finally {
                    timings().record(this, CoHeroTimings.Action.MELEE_POSITIONING, meleeStarted);
                }
                if (meleePositioning != null) {
                    logBossDecision("melee_positioning:" + combatTarget.id(),
                            targetDebug(combatTarget) + " -> melee positioning");
                    return meleePositioning;
                }

                Boolean combatResult = combat.tryCombat(combatTarget);
                if (combatResult != null) {
                    return combatResult;
                }
                } finally {
                    timings().record(
                            this, CoHeroTimings.Action.COMBAT_ACTION, combatActionStarted);
                }

                long combatEscapeStarted = timings().startNanos();
                try {
                Boolean escapeUtility = combat.tryEscapeUtility(combatRisk, combatThreats);
                if (escapeUtility != null) {
                    return escapeUtility;
                }

                int escapeStep = combat.chooseEscapeStep(combatThreats);
                if (escapeStep != -1) {
                    int oldPos = pos;
                    guard.allowAnyMovement();
                    setMovementDecision("combat_escape", escapeStep);
                    if (getCloser(escapeStep)) {
                        spend(1 / speed());
                        Dungeon.level.updateFieldOfView(this, fieldOfView);
                        revealVisibleCells();
                        return moveSprite(oldPos, pos);
                    }
                }
                } finally {
                    timings().record(
                            this, CoHeroTimings.Action.COMBAT_ESCAPE, combatEscapeStarted);
                }

                logBossDecision("combat_idle:" + combatTarget.id(),
                        targetDebug(combatTarget) + " -> no legal combat action");
                spend(TICK);
                return true;
            } finally {
                timings().record(this, CoHeroTimings.Action.COMBAT, combatStarted);
            }
        }

        // A recently hit companion must resolve unseen enemy fire before gathering items.
        // Only visible enemies enter normal combat targeting.
        Boolean unseenFire = underFire.tryRespond();
        if (unseenFire != null) {
            inCombat = true;
            return unseenFire;
        }

        Boolean recoveryResource = survival.tryKnownRecoveryResource();
        if (recoveryResource != null) {
            return recoveryResource;
        }

        if (survival.tryUseCleansingPotion(null)) {
            return true;
        }

        if (survival.tryAutoSurvivalPotion()) {
            return true;
        }

        if (support.isLowHealthRally()) {
            return support.actLowHealthRally();
        }

        Boolean ownedMissileRecovery = loot.actOwnedMissileRecovery(combatThreats);
        if (ownedMissileRecovery != null) {
            return ownedMissileRecovery;
        }

        long supportStarted = timings().startNanos();
        try {
            Boolean supportAction = combat.trySupportAction();
            if (supportAction != null) {
                return supportAction;
            }

            Boolean heroSupport = support.tryFollowHeroForNearbyEnemy();
            if (heroSupport != null) {
                return heroSupport;
            }
        } finally {
            timings().record(this, CoHeroTimings.Action.SUPPORT, supportStarted);
        }

        long recoveryStarted = timings().startNanos();
        try {
            Boolean lootAction = loot.actRecovery();
            if (lootAction != null) {
                return lootAction;
            }
        } finally {
            timings().record(this, CoHeroTimings.Action.RECOVERY, recoveryStarted);
        }

        long guardStarted = timings().startNanos();
        try {
            Boolean guardAction = guard.act();
            if (guardAction != null) {
                return guardAction;
            }
        } finally {
            timings().record(this, CoHeroTimings.Action.GUARD, guardStarted);
        }

        guard.clearDirective();

        long exploreStarted = timings().startNanos();
        try {
            return navigation.actExplore();
        } finally {
            timings().record(this, CoHeroTimings.Action.EXPLORE, exploreStarted);
        }
    }





































    boolean debugLogEnabled() {
        return debugLogEnabled;
    }

    void setDebugLogEnabled(boolean enabled) {
        debugLogEnabled = enabled;
        timings().setEnabled(enabled);
        if (!enabled) {
            lastBossDecisionLog = null;
        }
    }

    void logDebug(String message) {
        if (!debugLogEnabled) {
            return;
        }
        timings().recordDebug(message);
        GLog.i(message);
    }

    void setMovementDecision(String decision, int target) {
        movementDecision = decision;
        movementDecisionTarget = target;
        if (!debugLogEnabled) {
            return;
        }
        logDebug("[CoHeroMove] DECIDE"
                + " decision=" + decision
                + " target=" + target
                + " " + movementContext());
    }

    private void logMovement(String result, int oldPos, int requestedStep) {
        if (!debugLogEnabled) {
            return;
        }
        logDebug("[CoHeroMove] " + result
                + " decision=" + movementDecision
                + " target=" + movementDecisionTarget
                + " from=" + oldPos
                + " requested=" + requestedStep
                + " actual=" + pos
                + " " + movementContext());
    }

    String movementContext() {
        int heroPos = Dungeon.hero == null ? -1 : Dungeon.hero.pos;
        return "pos=" + pos
                + " hero=" + heroPos
                + " " + guard.debugState()
                + " " + combatObjective.debugState();
    }



    private Boolean tryAvoidHazard() {
        return navigation.tryAvoidHazard();
    }

    private Boolean tryLeavePiranhaDanger() {
        return navigation.tryLeavePiranhaDanger();
    }

    @Override
    protected boolean getCloser(int target) {
        return navigation.getCloser(target);
    }











































    private static Object rankingCause(Object cause) {
        if (cause == null) {
            throw new IllegalStateException("CoHero final death has no cause");
        }

        Class<?> causeClass = cause instanceof Class
                ? (Class<?>) cause
                : cause.getClass();

        if (Mob.class.isAssignableFrom(causeClass)) {
            return cause;
        }

        for (Class<?> enclosing = causeClass.getEnclosingClass();
                enclosing != null;
                enclosing = enclosing.getEnclosingClass()) {
            if (Mob.class.isAssignableFrom(enclosing)) {
                return enclosing;
            }
        }

        return cause;
    }

    @Override
    public void die(Object cause) {
        Object rankingCause = rankingCause(cause);

        if (revival.tryRevive()) {
            return;
        }

        super.die(cause);
        if (Dungeon.hero != null && Dungeon.hero.isAlive()) {
            GLog.n(revival.deathMessage(cause));
            CoHero.markCompanionDeathGameOver();
            Dungeon.hero.die(cause);
            Dungeon.fail(rankingCause);
        }
    }

    void resetNavigationAfterAnkhTeleport() {
        navigation.clearExplorationTarget();
        underFire.reset();
        clearNavigationPath();
        target = -1;
        enemy = null;
        enemyID = -1;
        enemySeen = false;
        alerted = false;
        defendingPos = -1;
        movingToDefendPos = false;
        state = WANDERING;
        inCombat = false;
    }

    private void resetInheritedDecisionState() {
        target = -1;
        enemy = null;
        enemyID = -1;
        enemySeen = false;
        alerted = false;
        defendingPos = -1;
        movingToDefendPos = false;
        state = WANDERING;
        inCombat = false;
    }

    boolean isCombatInvulnerable(Mob threat) {
        if (threat == null || !threat.isAlive()) {
            return false;
        }
        // SpectatorFreeze is used both by Duelist Challenge spectators and while a saved game is
        // restored. It is temporary suspension, not an enemy combat phase that CoHero should flee.
        if (threat.buff(Challenge.SpectatorFreeze.class) != null) {
            return false;
        }
        return threat.isInvulnerable(getClass());
    }

    CoHeroCombatRisk assessCombatRisk(
            Mob targetMob, ArrayList<Mob> threats) {
        return riskEstimator.assess(targetMob, threats);
    }

    int countCurrentAttackersAtCell(
            int defenderCell, ArrayList<Mob> threats) {
        return riskEstimator.countCurrentAttackersAtCell(defenderCell, threats);
    }

    CoHeroThreatTiming assessThreatTimingAtCell(
            int defenderCell, ArrayList<Mob> threats, float horizon) {
        return riskEstimator.assessThreatTimingAtCell(defenderCell, threats, horizon);
    }

    CoHeroThreatTiming assessThreatTimingAtCellWithBlockedCells(
            int defenderCell, ArrayList<Mob> threats, float horizon, boolean[] blocked) {
        return riskEstimator.assessThreatTimingAtCellWithBlockedCells(
                defenderCell, threats, horizon, blocked);
    }

    float estimatedTimeToAttackCell(Mob threat, int defenderCell) {
        return riskEstimator.estimatedTimeToAttackCell(threat, defenderCell);
    }

    float estimatedIncomingDptAtCell(
            int defenderCell, ArrayList<Mob> threats) {
        return riskEstimator.estimatedIncomingDptAtCell(defenderCell, threats);
    }

    float estimateMeleeDpt(Mob targetMob) {
        return riskEstimator.estimateMeleeDpt(targetMob);
    }

    float estimateMissileDpt(Mob targetMob, MissileWeapon missile) {
        return riskEstimator.estimateMissileDpt(targetMob, missile);
    }

    float estimateSpiritBowDpt(Mob targetMob, SpiritBow bow) {
        return riskEstimator.estimateSpiritBowDpt(targetMob, bow);
    }

    CoHeroWandAdapter.DamageEvaluation usableDamageWandEvaluation(
            Mob targetMob, Wand wand) {
        return combat.usableDamageEvaluation(targetMob, wand);
    }

    float estimateDamageWandDpt(Mob targetMob, Wand wand) {
        return riskEstimator.estimateDamageWandDpt(targetMob, wand);
    }

    float estimateBestRangedDpt(Mob targetMob) {
        return riskEstimator.estimateBestRangedDpt(targetMob);
    }

    float estimateTargetTtk(Mob targetMob) {
        return riskEstimator.estimateTargetTtk(targetMob);
    }

    float threatOpportunity(Mob threat, int defenderCell) {
        return riskEstimator.threatOpportunity(threat, defenderCell);
    }







    float estimatedThreatDamage(Mob threat, int defenderCell) {
        return riskEstimator.estimatedThreatDamage(threat, defenderCell);
    }

    float averageRangedThreatDamage(Mob threat) {
        return riskEstimator.averageRangedThreatDamage(threat, pos);
    }

    float estimatedHitChance(Mob threat, int defenderCell) {
        return riskEstimator.estimatedHitChance(threat, defenderCell);
    }

    float blessRollMultiplier(Char target) {
        return riskEstimator.blessRollMultiplier(target);
    }



    boolean isCurrentRangedPressure(Mob targetMob) {
        return targetMob != null
                && targetMob.isAlive()
                && Dungeon.level.distance(targetMob.pos, pos) > 1
                && targetMob.coHeroCanAttackFrom(targetMob.pos, this);
    }

    boolean hasRangedPressure(ArrayList<Mob> threats) {
        for (Mob threat : threats) {
            if (Dungeon.level.distance(threat.pos, pos) > 1
                    && threat.coHeroCanAttackFrom(threat.pos, this)) {
                return true;
            }
        }
        return false;
    }

    boolean hasNonAdjacentAttackCapability(Mob threat) {
        if (riskEstimator.hasNonAdjacentAttackCapability(threat, this)) {
            return true;
        }
        return Dungeon.hero != null
                && Dungeon.hero.isAlive()
                && riskEstimator.hasNonAdjacentAttackCapability(threat, Dungeon.hero);
    }

    boolean hasAdjacentAttackCapability(Mob threat) {
        return riskEstimator.hasAdjacentAttackCapability(threat);
    }

    boolean anyThreatCanAttackNow(ArrayList<Mob> threats) {
        for (Mob threat : threats) {
            if (threat.coHeroCanAttackFrom(threat.pos, this)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns null when CoHero has no currently usable attack capability and should flee.
     * Otherwise returns the synchronous/asynchronous result expected by Actor.act().
     */
    void revealVisibleCells() {
        vision.revealVisibleCells();
    }

    private boolean tryAutoTorch() {
        return vision.tryAutoTorch();
    }

    ArrayList<Mob> visibleAwakeEnemies() {
        CoHeroTurnContext context = currentTurnContext();
        if (context == null) {
            throw new IllegalStateException("CoHero visible threats requested outside decision turn");
        }
        return context.visibleAwakeEnemies();
    }

    int nearestThreatDistance(int cell, ArrayList<Mob> threats) {
        int nearest = Integer.MAX_VALUE;
        for (Mob threat : threats) {
            nearest = Math.min(nearest, Dungeon.level.distance(cell, threat.pos));
        }
        return nearest;
    }

    boolean isMovementSafe(int cell) {
        return navigation.isMovementSafe(cell);
    }

    boolean[] movementSafeMask() {
        return navigation.movementSafeMask();
    }

    boolean[] ordinarySafePassable(boolean knownOnly) {
        return navigation.ordinarySafePassable(knownOnly);
    }

    boolean[] nonCombatSafePassable(boolean knownOnly) {
        return navigation.nonCombatSafePassable(knownOnly);
    }

    boolean getCloserNonCombat(int target) {
        return navigation.getCloserNonCombat(target);
    }







    boolean isKnown(int cell) {
        return navigation.isKnown(cell);
    }

    int chooseRangedCoverCell(Mob targetMob, ArrayList<Mob> threats) {
        return combat.chooseRangedCoverCell(targetMob, threats);
    }

    Boolean tryUnseenRangedCover(Mob attacker, ArrayList<Mob> attackers) {
        return combat.tryUnseenRangedCover(attacker, attackers);
    }

    CoHeroControlItems controlItems() {
        return controlItems;
    }

    CoHeroSurvivalController survival() {
        return survival;
    }

    void clearCombatTarget() {
        enemy = null;
        enemyID = -1;
        target = -1;
    }

    void spendActionTime(float time) {
        spend(time);
    }

    boolean animateMoveFrom(int oldPos) {
        return moveSprite(oldPos, pos);
    }

    boolean attackTarget(Char target) {
        if (target != null && isCharmedBy(target)) {
            throw new IllegalStateException(
                    "CoHero attempted to attack its current charm source: "
                            + target.getClass().getSimpleName());
        }

        long started = timings().startNanos();
        boolean hit = attack(target);
        timings().record(this, target.isAlive()
                ? CoHeroTimings.Action.ATTACK
                : CoHeroTimings.Action.ATTACK_KILL, started);
        return hit;
    }

    void finishAsyncAction() {
        next();
    }

    void clearNavigationPath() {
        path = null;
        navigation.clearPolicyPath();
    }

    int defendingPosition() {
        return defendingPos;
    }

    boolean finishMovementAnimation(int oldPos) {
        return moveSprite(oldPos, pos);
    }

    void refreshOwnFieldOfView() {
        vision.refreshOwnFieldOfView();
    }

    void passiveSearch() {
        vision.passiveSearch();
    }

    void clearExplorationTarget() {
        navigation.clearExplorationTarget();
    }

    boolean actCurrentState() {
        return state.act(false, false);
    }

    void prepareGuardHeroSupportMovement() {
        guard.prepareHeroSupportMovement();
    }

    boolean followHeroDirectiveForGuard() {
        return support.followHeroDirective();
    }

    boolean isGuardMovementRestricted() {
        return guard.isMovementRestricted();
    }

    void restrictGuardPassable(boolean[] passable) {
        guard.restrictPassable(passable);
    }

    void allowAnyGuardMovement() {
        guard.allowAnyMovement();
    }

    void releaseGuardAreaForCombat() {
        guard.releaseGuardAreaForCombat();
    }

    boolean getCloserWithoutCoHeroPolicy(int target) {
        return super.getCloser(target);
    }

    boolean isBelowLowHealthThreshold() {
        return support.isBelowLowHealthThreshold();
    }

    boolean trySurvivalInvisibility() {
        return survival.tryUseInvisibilityPotion();
    }

    boolean trySurvivalHaste() {
        return survival.tryUseHastePotion();
    }
}
