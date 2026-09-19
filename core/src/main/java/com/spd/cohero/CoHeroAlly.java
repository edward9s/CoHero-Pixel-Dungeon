package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Invisibility;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MagicImmune;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.DirectableAlly;
import com.shatteredpixel.shatteredpixeldungeon.items.Heap;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.Armor;
import com.shatteredpixel.shatteredpixeldungeon.items.armor.ClassArmor;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfAccuracy;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfEvasion;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfMight;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfSharpshooting;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
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
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.MissileSprite;
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

    private int explorationTarget = -1;
    private int syncedLevel = 1;
    private final CompanionInventory inventory = new CompanionInventory(this);
    private final HashMap<Long, Integer> thrownOutstanding = new HashMap<>();
    private MissileWeapon activeMissileWeapon;

    {
        spriteClass = CoHeroAllySprite.class;
        HT = HP = 20;
        attacksAutomatically = false;
    }

    public CompanionInventory inventory() {
        return inventory;
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
     * CoHero strength deliberately has one authority: the player's current effective STR.
     * No second mutable STR value is stored or synchronized.
     */
    public int STR() {
        if (Dungeon.hero == null) {
            throw new IllegalStateException("CoHero STR requested without Dungeon.hero");
        }
        return Dungeon.hero.STR();
    }

    void updateHT(boolean boostHP) {
        int oldHT = HT;
        HT = Math.round((20 + 5 * (level() - 1)) * RingOfMight.HTMultiplier(this));
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

    @Override
    public int attackSkill(Char target) {
        return attackSkillWith(attackingWeapon(), target);
    }

    private int attackSkillWith(Weapon attackWeapon, Char target) {
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
        float evasion = (4 + level()) * RingOfEvasion.evasionMultiplier(this);
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

        ArrayList<Mob> visibleThreats = visibleAwakeEnemies();
        if (!visibleThreats.isEmpty()) {
            Mob combatTarget = nearestThreat(visibleThreats);
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

        int exit = Dungeon.level.exit();
        LevelTransition exitTransition = isKnown(exit) ? Dungeon.level.getTransition(exit) : null;
        if (exitTransition != null && exitTransition.type == LevelTransition.Type.REGULAR_EXIT) {
            if (CoHero.isAdjacentToTransition(pos, exitTransition)) {
                explorationTarget = pos;
                spend(TICK);
                return true;
            }

            explorationTarget = chooseExitWaitingCell(exitTransition);
        } else if (explorationTarget == -1
                || explorationTarget == pos
                || !Dungeon.level.passable[explorationTarget]
                || (Actor.findChar(explorationTarget) != null && Actor.findChar(explorationTarget) != this)
                || !isSleepSafe(explorationTarget)) {
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

        explorationTarget = exitTransition != null
                ? chooseExitWaitingCell(exitTransition)
                : chooseExplorationTarget();
        spend(TICK);
        return true;
    }

    @Override
    public void die(Object cause) {
        super.die(cause);
        if (Dungeon.hero != null && Dungeon.hero.isAlive()) {
            GLog.n(companionDeathMessage(cause));
            CoHero.markCompanionDeathGameOver();
            Hero.reallyDie(cause);
        }
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
        if (weapon() != null && weapon().canReach(this, targetMob.pos)) {
            state = HUNTING;
            return doAttack(targetMob);
        }

        RangedChoice ranged = chooseRangedAttack(targetMob);
        if (ranged != null) {
            state = HUNTING;
            if (ranged.missile != null) {
                return performMissileAttack(targetMob, ranged.missile);
            } else {
                return performWandCast(targetMob, ranged.wand);
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
                    && CoHeroWandAdapter.directDamage(wand, targetMob)) {
                damageWands.add(wand);
            }
        }

        // A guaranteed corruption/doom conversion is treated as higher-value control than damage.
        if (guaranteedControl != null) {
            return RangedChoice.wand(guaranteedControl);
        }

        if (!missiles.isEmpty() && !damageWands.isEmpty()) {
            int bestPhysicalAccuracy = 0;
            for (MissileWeapon missile : missiles) {
                bestPhysicalAccuracy = Math.max(bestPhysicalAccuracy, attackSkillWith(missile, targetMob));
            }
            if (targetMob.defenseSkill(this) > bestPhysicalAccuracy) {
                return RangedChoice.wand(bestDamageWand(damageWands, targetMob));
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

        Wand bestWand = bestDamageWand(damageWands, targetMob);
        float bestWandDamage = bestWand == null
                ? Float.NEGATIVE_INFINITY
                : CoHeroWandAdapter.expectedDamage(bestWand, this, targetMob);

        // Stable tie-break: preserve wand charges when physical expected damage is equal.
        if (bestMissile != null && (bestWand == null || bestMissileDamage >= bestWandDamage)) {
            return RangedChoice.missile(bestMissile);
        }
        if (bestWand != null) {
            return RangedChoice.wand(bestWand);
        }

        // Control-only wands are fallbacks when no direct ranged damage is currently available.
        Wand fallbackControl = null;
        for (Wand wand : inventory.wands()) {
            if (CoHeroWandAdapter.fallbackControl(wand, this, targetMob)
                    && (fallbackControl == null || wand.buffedLvl() > fallbackControl.buffedLvl())) {
                fallbackControl = wand;
            }
        }
        return fallbackControl == null ? null : RangedChoice.wand(fallbackControl);
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
        int level = missile.buffedLvl() + RingOfSharpshooting.levelDamageBonus(this);
        float average = (missile.min(level) + missile.max(level)) / 2f;
        average = missile.augment.damageFactor(average);
        int excessStrength = STR() - missile.STRReq();
        if (excessStrength > 0) {
            average += excessStrength / 2f;
        }
        return average;
    }

    private Boolean tryEscapeUtility(ArrayList<Mob> visibleThreats) {
        for (Mob threat : visibleThreats) {
            for (Wand wand : inventory.wands()) {
                if (CoHeroWandAdapter.regrowthUsefulForEscape(wand, this, threat, visibleThreats)) {
                    return performWandCast(threat, wand);
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
        return best == null ? null : performWandCast(Dungeon.hero, best);
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

    private boolean performWandCast(Char targetChar, Wand wand) {
        // Some stock wand fx methods are synchronous (beam/chain effects call their callback
        // before fx() returns), while projectile/cone effects complete asynchronously. Calling
        // next() synchronously from inside act() re-enters Actor processing, so distinguish both
        // cases explicitly.
        final boolean[] insideCast = {true};
        final boolean[] completedSynchronously = {false};

        wand.coHeroCast(this, targetChar.pos, new Callback() {
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

        private RangedChoice(MissileWeapon missile, Wand wand) {
            this.missile = missile;
            this.wand = wand;
        }

        static RangedChoice missile(MissileWeapon missile) {
            return new RangedChoice(missile, null);
        }

        static RangedChoice wand(Wand wand) {
            return new RangedChoice(null, wand);
        }
    }

    private void revealVisibleCells() {
        boolean changed = false;
        for (int i = 0; i < fieldOfView.length; i++) {
            if (fieldOfView[i]
                    && Dungeon.level.discoverable[i]
                    && !Dungeon.level.visited[i]) {
                Dungeon.level.visited[i] = true;
                changed = true;
            }
        }
        if (changed) {
            GameScene.updateFog(pos, viewDistance + 1);
        }
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
        int currentDistance = nearestThreatDistance(pos, threats);
        int bestCell = -1;
        int bestDistance = currentDistance;

        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = pos + offset;
            if (cell < 0
                    || cell >= Dungeon.level.length()
                    || !Dungeon.level.passable[cell]
                    || Actor.findChar(cell) != null
                    || !isSleepSafe(cell)) {
                continue;
            }

            int distance = nearestThreatDistance(cell, threats);
            if (distance > bestDistance) {
                bestDistance = distance;
                bestCell = cell;
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
                    || !isSleepSafe(cell)) {
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

    private int chooseExplorationTarget() {
        int exit = Dungeon.level.exit();
        if (isKnown(exit)) {
            LevelTransition transition = Dungeon.level.getTransition(exit);
            if (transition != null && transition.type == LevelTransition.Type.REGULAR_EXIT) {
                return chooseExitWaitingCell(transition);
            }
        }

        ArrayList<Integer> unknown = new ArrayList<>();
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (cell != pos
                    && Dungeon.level.passable[cell]
                    && Dungeon.level.discoverable[cell]
                    && !Dungeon.level.visited[cell]
                    && !Dungeon.level.mapped[cell]
                    && isSleepSafe(cell)) {
                unknown.add(cell);
            }
        }

        if (!unknown.isEmpty()) {
            return Random.element(unknown);
        }

        return Dungeon.level.randomDestination(this);
    }

    private boolean isKnown(int cell) {
        return cell >= 0
                && cell < Dungeon.level.length()
                && (Dungeon.level.visited[cell] || Dungeon.level.mapped[cell]);
    }
}
