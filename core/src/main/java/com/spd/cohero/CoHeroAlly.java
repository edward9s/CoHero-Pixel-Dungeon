package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Barrier;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Healing;
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
import com.shatteredpixel.shatteredpixeldungeon.items.potions.Potion;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.PotionOfHealing;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfShielding;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfAccuracy;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfEvasion;
import com.shatteredpixel.shatteredpixeldungeon.items.rings.RingOfHaste;
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
import com.shatteredpixel.shatteredpixeldungeon.effects.FloatingText;
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

    private static final int LOW_HEALTH_RALLY_ENTER_PERCENT = 35;
    private static final int LOW_HEALTH_RALLY_EXIT_PERCENT = 60;
    private static final int HERO_RALLY_MIN_DISTANCE = 2;
    private static final int HERO_RALLY_MAX_DISTANCE = 3;

    private int explorationTarget = -1;
    private int syncedLevel = 1;
    private final CompanionInventory inventory = new CompanionInventory(this);
    private final HashMap<Long, Integer> thrownOutstanding = new HashMap<>();
    private MissileWeapon activeMissileWeapon;
    private boolean lowHealthRally;

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
        if (tryAutoSurvivalPotion()) {
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
                || !isSleepSafe(explorationTarget)
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

        // Healing is the first choice. Do not consume another healing potion while the current
        // Healing buff is still active.
        if (buff(Healing.class) == null) {
            Potion healing = inventory.takeOneAutoHealingPotion();
            if (healing != null) {
                // Apply only the Char-safe healing semantics. Do not route through Potion.drink/apply,
                // which is hard-wired to Hero belongings, Hero talents, and Hero action callbacks.
                PotionOfHealing.cure(this);
                PotionOfHealing.heal(this);
                Sample.INSTANCE.play(Assets.Sounds.DRINK);
                spend(TICK);
                return true;
            }
        }

        // If healing is already running or unavailable, shielding is the fallback. Existing
        // Barrier means there is still useful shield remaining, so do not overwrite/waste it.
        Barrier barrier = buff(Barrier.class);
        if (barrier == null || barrier.shielding() <= 0) {
            Potion shielding = inventory.takeOneAutoShieldingPotion();
            if (shielding != null) {
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
        }

        return false;
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
                    || !isSleepSafe(cell)) {
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

    private boolean hasUnexploredFrontier() {
        PathFinder.buildDistanceMap(pos, Dungeon.level.passable);
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (cell != pos
                    && Dungeon.level.passable[cell]
                    && Dungeon.level.discoverable[cell]
                    && !Dungeon.level.visited[cell]
                    && !Dungeon.level.mapped[cell]
                    && PathFinder.distance[cell] < Integer.MAX_VALUE
                    && isSleepSafe(cell)) {
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
                    && isSleepSafe(cell)) {
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
            if (cell == pos || cell == Dungeon.hero.pos || !isSleepSafe(cell)) {
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
