package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.ConfusionGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.CorrosiveGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Electricity;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Fire;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.ParalyticGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.blobs.ToxicGas;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Barrier;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Bless;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Blindness;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Cripple;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Invisibility;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Vulnerable;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Weakness;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.items.potions.exotic.PotionOfCleansing;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTeleportation;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfBlastWave;
import com.shatteredpixel.shatteredpixeldungeon.scenes.CellSelector;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.QuickSlotButton;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.utils.Bundle;
import com.watabou.utils.PathFinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Shared party resource: only attacks from BOTH heroes can earn combo energy.
 * A paired action scores once, regardless of damage rolls, projectiles or attack procs.
 */
public final class CoHeroCombo {

    public static final int MAX_ENERGY = 180;
    public static final int CAST_COST = 60;
    private static final float PAIR_WINDOW = 3f;
    private static final int PARTY_RANGE = 6;
    private static final int ENGAGEMENT_RANGE = 4;
    private static final String ENERGY_KEY = "cohero_combo_energy";

    private static int energy;
    private static Level observedLevel;
    private static float lastTime = -1f;
    private static int lastRole;
    private static int lastTargetId = -1;
    private static int lastTargetCell = -1;
    // Bitmask per enemy: 1 = Hero dealt damage, 2 = CoHero dealt damage.
    private static final HashMap<Integer, Integer> participants = new HashMap<>();
    // The aim is transient UI state, not combat state or a saved preference.
    private static CellSelector.Listener targetSelector;
    private static Mob aimedTarget;

    private CoHeroCombo() {
    }

    public static void reset() {
        energy = 0;
        clearTransientState();
        clearTargetSelection();
    }

    public static int energy() {
        return energy;
    }

    /**
     * Test-only refill at the beginning of the next Hero turn.
     * The meter deliberately stays at 60..179 until another legitimate cast
     * drops it below 60. This does not alter an already earned charge mid-turn.
     */
    public static void onHeroTurn() {
        if (CoHeroSettings.autoFillLinkEnabled() && energy < CAST_COST) {
            energy = MAX_ENERGY;
        }
    }

    public static void store(Bundle bundle) {
        bundle.put(ENERGY_KEY, energy);
    }

    public static void restore(Bundle bundle) {
        // Older CoHero saves predate the Link meter. Start them at zero.
        // A present but invalid value is still a corrupted save, not a missing field.
        energy = bundle.contains(ENERGY_KEY) ? bundle.getInt(ENERGY_KEY) : 0;
        if (energy < 0 || energy > MAX_ENERGY) {
            throw new IllegalStateException("Invalid saved combo energy: " + energy);
        }
        clearTransientState();
        clearTargetSelection();
    }

    public static void onLevelChanged() {
        clearTransientState();
        clearTargetSelection();
    }

    private static void clearTransientState() {
        observedLevel = null;
        lastTime = -1f;
        lastRole = 0;
        lastTargetId = -1;
        lastTargetCell = -1;
        participants.clear();
    }

    private static void gain(int points) {
        energy = Math.min(MAX_ENERGY, energy + points);
    }

    /**
     * Hooked at the single Char.attack damage application point, not attackProc.
     * This excludes misses, zero-damage hits, DOT, and duplicate on-hit effects.
     */
    public static void onAttack(Char attacker, Char victim, int hpBefore) {
        recordAttack(attacker, victim, hpBefore, true);
    }

    /**
     * The normal and companion Wand cast routes call this once around onZap().
     * Delayed gas/fire ticks and ward attacks occur outside this window.
     * Only one pairing can be scored by a multi-target wand cast.
     */
    public static void onWandZap(Wand wand, Char caster,
                                 Ballistica bolt) {
        if (wand == null || caster == null || bolt == null) {
            throw new IllegalArgumentException("Wand combo requires wand, caster and bolt");
        }
        if (Dungeon.level == null) {
            throw new IllegalStateException("Wand combo cast requires a level");
        }
        int role = roleOf(caster);
        if (role == 0) {
            wand.onZap(bolt);
            return;
        }

        HashMap<Mob, Integer> before = new HashMap<>();
        for (Mob mob : Dungeon.level.mobs) {
            if (mob.alignment == Char.Alignment.ENEMY && mob.isAlive()) {
                before.put(mob, mob.HP);
            }
        }
        wand.onZap(bolt);

        Mob primary = null;
        int best = Integer.MAX_VALUE;
        for (Mob mob : before.keySet()) {
            if (before.get(mob) > mob.HP) {
                int rank = mob.id() == lastTargetId ? -1
                        : Dungeon.level.distance(mob.pos, bolt.collisionPos);
                if (rank < best || (rank == best && primary != null
                        && mob.id() < primary.id())) {
                    best = rank;
                    primary = mob;
                }
            }
        }
        if (primary == null) {
            return;
        }
        recordAttack(caster, primary, before.get(primary), true);
        for (Mob mob : before.keySet()) {
            if (mob != primary && before.get(mob) > mob.HP) {
                recordAttack(caster, mob, before.get(mob), false);
            }
        }
    }

    private static int roleOf(Char attacker) {
        if (Dungeon.hero == null) {
            return 0;
        }
        if (attacker == Dungeon.hero) {
            return 1;
        }
        CoHeroAlly companion = CoHero.findCompanion();
        return companion != null && companion.isAlive() && attacker == companion ? 2 : 0;
    }

    private static void recordAttack(
            Char attacker, Char victim, int hpBefore, boolean allowPairing) {
        if (Dungeon.level == null || Dungeon.hero == null
                || !(victim instanceof Mob) || victim.alignment != Char.Alignment.ENEMY
                || hpBefore <= victim.HP) {
            return;
        }
        int role = roleOf(attacker);
        if (role == 0) {
            return;
        }
        CoHeroAlly companion = CoHero.findCompanion();
        if (observedLevel != Dungeon.level || Actor.now() < lastTime) {
            clearTransientState();
            observedLevel = Dungeon.level;
        }

        Mob mob = (Mob) victim;
        int id = mob.id();
        int mask = participants.containsKey(id) ? participants.get(id) : 0;
        mask |= role;
        if (mob.isAlive()) {
            participants.put(id, mask);
        } else {
            participants.remove(id);
        }

        float now = Actor.now();
        if (allowPairing && lastRole != 0 && lastRole != role
                && now - lastTime <= PAIR_WINDOW
                && Dungeon.level.distance(Dungeon.hero.pos, companion.pos) <= PARTY_RANGE) {
            if (lastTargetId == id) {
                gain(8);
            } else if (Dungeon.level.distance(lastTargetCell, victim.pos) <= ENGAGEMENT_RANGE) {
                gain(5);
            }
        }

        if (!mob.isAlive() && mask == 3) {
            gain(4);
        }

        // Secondary victims of an AoE cast can earn shared-kill credit,
        // but must not replace the pending attack or trigger another pairing.
        if (allowPairing) {
            lastRole = role;
            lastTime = now;
            lastTargetId = id;
            lastTargetCell = victim.pos;
        }
    }

    private static int classIndex(HeroClass heroClass) {
        if (heroClass == null) {
            throw new IllegalArgumentException("Combo requires an explicit hero class");
        }
        switch (heroClass) {
            case WARRIOR: return 0;
            case MAGE: return 1;
            case ROGUE: return 2;
            case HUNTRESS: return 3;
            case DUELIST: return 4;
            case CLERIC: return 5;
            default: throw new IllegalStateException("Unsupported combo class: " + heroClass);
        }
    }

    public static String skillName() {
        if (Dungeon.hero == null) {
            throw new IllegalStateException("Combo skill requested without Hero");
        }
        return CoHeroMessages.get("combo.skill." + classIndex(Dungeon.hero.heroClass)
                + "." + classIndex(CoHero.companionClass()));
    }

    /** Text reflects the effects actually implemented for this ordered pair. */
    public static String skillDescription() {
        if (Dungeon.hero == null) {
            throw new IllegalStateException("Combo description requested without Hero");
        }
        int heroClass = classIndex(Dungeon.hero.heroClass);
        int partnerClass = classIndex(CoHero.companionClass());
        String effects;
        if (heroClass == 5) {
            effects = CoHeroMessages.get("combo.detail.cleric." + partnerClass);
        } else {
            effects = CoHeroMessages.get("combo.detail.main." + heroClass) + " "
                    + CoHeroMessages.get("combo.detail.partner." + partnerClass);
        }
        return effects + " " + CoHeroMessages.get(
                heroClass == 5 ? "combo.detail.requirement.support"
                        : "combo.detail.requirement.attack", CAST_COST);
    }

    public static boolean canCast() {
        Hero hero = Dungeon.hero;
        CoHeroAlly companion = CoHero.findCompanion();
        return energy >= CAST_COST && Dungeon.level != null
                && hero != null && hero.isAlive() && hero.ready && hero.paralysed <= 0
                && companion != null && companion.isAlive() && companion.paralysed <= 0
                && companion.canPerformCombo()
                && Dungeon.level.distance(hero.pos, companion.pos) <= PARTY_RANGE;
    }

    /**
     * Follow the stock/ModAssassinate quickslot convention: start manual
     * targeting with an automatically suggested legal enemy. Press the Tag
     * again to confirm that target, or tap any other valid enemy on the map.
     * Cleric-led support ultimates never require a target.
     */
    public static void requestCast() {
        if (!canCast()) {
            GLog.w(CoHeroMessages.get("combo.unavailable"));
            return;
        }
        if (classIndex(Dungeon.hero.heroClass) == 5) {
            cast(null);
            return;
        }
        if (targetSelector != null) {
            Mob target = aimTarget();
            if (!validAutoTarget(target)) {
                aimedTarget = preferredAutoTarget();
                target = aimedTarget;
            }
            if (target != null) {
                GameScene.handleCell(target.pos);
            } else {
                GameScene.cancelCellSelector();
            }
            return;
        }
        CellSelector.Listener listener = new CellSelector.Listener() {
            @Override
            public void onSelect(Integer cell) {
                clearTargetSelection();
                if (cell != null) {
                    cast(cell);
                }
            }

            @Override
            public String prompt() {
                return CoHeroMessages.get("combo.target");
            }
        };
        targetSelector = listener;
        GameScene.selectCell(listener);
        aimedTarget = preferredAutoTarget();
    }

    private static void clearTargetSelection() {
        targetSelector = null;
        aimedTarget = null;
    }

    /** Returns only visible, currently legal enemies for the aiming reticle. */
    public static Mob aimTarget() {
        if (targetSelector == null || aimedTarget == null) {
            return null;
        }
        // The HUD calls this every frame. Avoid repeating costly Ballistica
        // scans while merely displaying the chosen enemy's crosshair.
        if (Dungeon.level == null || !aimedTarget.isAlive()
                || aimedTarget.pos < 0 || aimedTarget.pos >= Dungeon.level.length()
                || !Dungeon.level.heroFOV[aimedTarget.pos]
                || Actor.findChar(aimedTarget.pos) != aimedTarget) {
            aimedTarget = null;
        }
        return aimedTarget;
    }

    private static boolean validAutoTarget(Mob mob) {
        Hero hero = Dungeon.hero;
        CoHeroAlly companion = CoHero.findCompanion();
        // Never auto-aim into CoHero-only vision: the reticle would reveal an
        // enemy the player cannot see.
        return hero != null && companion != null && Dungeon.level != null
                && mob != null && mob.pos >= 0 && mob.pos < Dungeon.level.length()
                && Dungeon.level.heroFOV[mob.pos]
                && mob.sprite != null && mob.sprite.visible && mob.sprite.parent != null
                && validAttackTarget(mob, hero, companion);
    }

    private static Mob preferredAutoTarget() {
        if (QuickSlotButton.lastTarget instanceof Mob) {
            Mob last = (Mob) QuickSlotButton.lastTarget;
            if (validAutoTarget(last)) {
                return last;
            }
        }

        Hero hero = Dungeon.hero;
        Mob closest = null;
        int distance = Integer.MAX_VALUE;
        for (Mob mob : Dungeon.level.mobs) {
            if (!validAutoTarget(mob)) {
                continue;
            }
            int candidateDistance = Dungeon.level.distance(hero.pos, mob.pos);
            if (candidateDistance < distance
                    || (candidateDistance == distance && closest != null
                        && mob.id() < closest.id())) {
                distance = candidateDistance;
                closest = mob;
            }
        }
        return closest;
    }

    /** Same validation for auto-aim and user-chosen targets. */
    private static boolean validAttackTarget(Mob mob, Hero hero, CoHeroAlly companion) {
        return mob != null && mob.isAlive()
                && mob.alignment == Char.Alignment.ENEMY
                && mob.pos >= 0 && mob.pos < Dungeon.level.length()
                && Actor.findChar(mob.pos) == mob
                && !mob.isInvulnerable(hero.getClass())
                && (Dungeon.level.distance(hero.pos, mob.pos) <= 8
                    || Dungeon.level.distance(companion.pos, mob.pos) <= 8)
                && (classIndex(hero.heroClass) == 3
                    ? (canSeePiercing(hero, mob.pos) || canSeePiercing(companion, mob.pos))
                    : canTargetFromEitherHero(mob.pos, hero, companion));
    }

    private static void cast(Integer cell) {
        if (!canCast()) {
            GLog.w(CoHeroMessages.get("combo.unavailable"));
            return;
        }
        Hero hero = Dungeon.hero;
        CoHeroAlly companion = CoHero.findCompanion();
        int mainClass = classIndex(hero.heroClass);
        int partnerClass = classIndex(CoHero.companionClass());
        Mob target = null;
        if (mainClass != 5) {
            if (cell == null || cell < 0 || cell >= Dungeon.level.length()) {
                GLog.w(CoHeroMessages.get("combo.invalid_target"));
                return;
            }
            Char selected = Actor.findChar(cell);
            if (!(selected instanceof Mob)
                    || !validAttackTarget((Mob) selected, hero, companion)) {
                GLog.w(CoHeroMessages.get("combo.invalid_target"));
                return;
            }
            target = (Mob) selected;
            QuickSlotButton.target(target);
        }

        // Reserve this confirmed cast, but do not apply combat effects yet:
        // dying enemies must remain on-screen until the joint impact lands.
        energy -= CAST_COST;
        hero.busy();
        final Mob victim = target;
        CoHeroComboFX.play(
                hero, companion, victim == null ? hero.pos : victim.pos,
                mainClass, partnerClass, new Runnable() {
                    @Override
                    public void run() {
                        // The Hero turn has not advanced during the telegraph.
                        // Resolve once at the animation's actual hit moment.
                        if (mainClass == 5) {
                            performClericUltimate(hero, companion, partnerClass);
                        } else {
                            performAttackUltimate(hero, companion, victim, mainClass, partnerClass);
                        }
                        GLog.p(CoHeroMessages.get("combo.used", skillName()));
                        companion.spendComboTurn();
                        hero.spendAndNext(Actor.TICK);
                    }
                });
    }

    private static boolean canTargetFromEitherHero(int cell, Hero hero, CoHeroAlly companion) {
        return (Dungeon.level.heroFOV[cell]
                    && new Ballistica(hero.pos, cell, Ballistica.PROJECTILE).collisionPos == cell)
                || (companion.fieldOfView != null && companion.fieldOfView[cell]
                    && new Ballistica(companion.pos, cell, Ballistica.PROJECTILE).collisionPos == cell);
    }

    /**
     * Six tactical primaries composed with six active partner roles.
     * Each role changes positioning, threat control or attack geometry:
     * not six damage coefficients with six additive bonuses.
     */
    private static void performAttackUltimate(
            Hero hero, CoHeroAlly companion, Mob target, int mainClass, int partnerClass) {
        int power = 9 + hero.lvl * 2;
        HashSet<Integer> pierced = new HashSet<>();
        switch (mainClass) {
            case 0: // Warrior: break the formation and clear space around the party.
                damageArea(hero, target.pos, 1, power);
                for (Mob mob : new ArrayList<>(Dungeon.level.mobs)) {
                    if (inClearArea(target.pos, 1, mob)) {
                        pushAway(mob, hero, companion);
                    }
                }
                break;
            case 1: // Mage: suppress even enemies outside the damaging center.
                damageArea(hero, target.pos, 1, power);
                for (Mob mob : new ArrayList<>(Dungeon.level.mobs)) {
                    if (inClearArea(target.pos, 2, mob)) {
                        Buff.prolong(mob, Weakness.class, 3f);
                        Buff.prolong(mob, Cripple.class, 2f);
                    }
                }
                break;
            case 2: // Rogue: execute a weak target then disengage safely.
                damageEnemy(hero, target, power * 2
                        + (target.HP < target.HT / 2 ? power / 2 : 0));
                retreatFrom(hero, companion, target.pos);
                Buff.prolong(hero, Invisibility.class, 2f);
                break;
            case 3: // Huntress: terrain-bounded piercing ray and vision denial.
                pierceLane(hero, piercingLane(hero, companion, target.pos),
                        target.pos, power + hero.lvl, pierced);
                if (target.isAlive()) {
                    Buff.prolong(target, Blindness.class, 2f);
                }
                break;
            case 4: // Duelist: focused duel plus close-range sword sweep.
                damageEnemy(hero, target, power * 2);
                int swept = 0;
                for (Mob mob : new ArrayList<>(Dungeon.level.mobs)) {
                    if (mob != target && mob.isAlive()
                            && (Dungeon.level.distance(hero.pos, mob.pos) <= 1
                                || Dungeon.level.distance(companion.pos, mob.pos) <= 1)
                            && canTargetFromEitherHero(mob.pos, hero, companion)) {
                        damageEnemy(hero, mob, power / 2);
                        if (++swept == 2) {
                            break;
                        }
                    }
                }
                Buff.prolong(hero, Bless.class, 3f);
                Buff.prolong(companion, Bless.class, 3f);
                break;
            default:
                throw new IllegalStateException("Unexpected offensive combo class " + mainClass);
        }

        // Every companion has one recognizable cooperative action.
        switch (partnerClass) {
            case 0: // Warrior: guard the team and knock the target off balance.
                shield(hero, 5 + hero.lvl);
                shield(companion, 5 + hero.lvl);
                if (mainClass != 0 && target.isAlive()) {
                    pushAway(target, hero, companion);
                }
                break;
            case 1: // Mage: chain to two nearby, unobstructed enemies.
                int chained = 0;
                for (Mob mob : new ArrayList<>(Dungeon.level.mobs)) {
                    if (mob != target && inClearArea(target.pos, 2, mob)) {
                        damageEnemy(hero, mob, Math.max(1, power / 2));
                        if (mob.isAlive()) {
                            Buff.prolong(mob, Weakness.class, 2f);
                        }
                        if (++chained == 2) {
                            break;
                        }
                    }
                }
                break;
            case 2: // Rogue: expose the prey and conceal the partner's approach.
                if (target.isAlive()) {
                    Buff.prolong(target, Vulnerable.class, 3f);
                }
                Buff.prolong(companion, Invisibility.class, 2f);
                break;
            case 3: // Huntress: a second, independently calculated firing lane.
                pierceLane(hero, piercingLane(companion, hero, target.pos),
                        target.pos, Math.max(1, power / 2), pierced);
                if (target.isAlive()) {
                    Buff.prolong(target, Blindness.class, 2f);
                }
                break;
            case 4: // Duelist: close-range follow-up; otherwise pin the prey.
                if (target.isAlive()) {
                    if (Dungeon.level.distance(companion.pos, target.pos) <= 2) {
                        damageEnemy(hero, target, 5 + hero.lvl);
                    }
                    if (target.isAlive()) {
                        Buff.prolong(target, Cripple.class, 3f);
                    }
                }
                Buff.prolong(companion, Bless.class, 2f);
                break;
            case 5: // Cleric: cleanse, recover, and reinforce in emergencies.
                PotionOfCleansing.cleanse(hero);
                PotionOfCleansing.cleanse(companion);
                heal(hero, 4 + hero.lvl);
                heal(companion, 4 + hero.lvl);
                if (hero.HP * 2 <= hero.HT || companion.HP * 2 <= companion.HT) {
                    shield(hero, 5 + hero.lvl);
                    shield(companion, 5 + hero.lvl);
                }
                break;
            default:
                throw new IllegalStateException("Unexpected partner combo class " + partnerClass);
        }
    }

    private static void performClericUltimate(
            Hero hero, CoHeroAlly companion, int partnerClass) {
        switch (partnerClass) {
            case 0: // Warrior: protective oath and space for regrouping.
                shield(hero, 14 + hero.lvl * 2);
                shield(companion, 14 + hero.lvl * 2);
                Buff.prolong(hero, Bless.class, 3f);
                Buff.prolong(companion, Bless.class, 3f);
                break;
            case 1: // Mage: cleansing wave and enemy suppression.
                damageArea(hero, hero.pos, 2, 7 + hero.lvl);
                for (Mob mob : new ArrayList<>(Dungeon.level.mobs)) {
                    if (inClearArea(hero.pos, 2, mob)) {
                        Buff.prolong(mob, Cripple.class, 3f);
                    }
                }
                PotionOfCleansing.cleanse(hero);
                PotionOfCleansing.cleanse(companion);
                heal(hero, 5 + hero.lvl);
                heal(companion, 5 + hero.lvl);
                break;
            case 2: // Rogue: protect the retreat without moving through hazards.
                Buff.prolong(hero, Invisibility.class, 4f);
                Buff.prolong(companion, Invisibility.class, 4f);
                heal(hero, 3 + hero.lvl);
                heal(companion, 3 + hero.lvl);
                break;
            case 3: // Huntress: sanctuary plus suppression of visible ranged threats.
                heal(hero, 6 + hero.lvl);
                heal(companion, 6 + hero.lvl);
                for (Mob mob : new ArrayList<>(Dungeon.level.mobs)) {
                    if (mob.alignment == Char.Alignment.ENEMY && mob.isAlive()
                            && Dungeon.level.distance(companion.pos, mob.pos) <= 4
                            && canTargetFromEitherHero(mob.pos, hero, companion)) {
                        damageEnemy(hero, mob, 3 + hero.lvl);
                        if (mob.isAlive()) {
                            Buff.prolong(mob, Blindness.class, 3f);
                        }
                    }
                }
                break;
            case 4: // Duelist: guarded counterattack stance.
                shield(hero, 8 + hero.lvl);
                shield(companion, 8 + hero.lvl);
                Buff.prolong(hero, Bless.class, 6f);
                Buff.prolong(companion, Bless.class, 6f);
                break;
            case 5: // Cleric: the strongest emergency rescue.
                PotionOfCleansing.cleanse(hero);
                PotionOfCleansing.cleanse(companion);
                heal(hero, 12 + hero.lvl * 2);
                heal(companion, 12 + hero.lvl * 2);
                shield(hero, 7 + hero.lvl);
                shield(companion, 7 + hero.lvl);
                break;
            default:
                throw new IllegalStateException("Unexpected cleric partner " + partnerClass);
        }
    }

    private static Ballistica piercingLane(Char preferred, Char alternate, int cell) {
        // STOP_TARGET | STOP_SOLID stops exactly on the selected target,
        // pierces intervening mobs, and never crosses walls.
        if (canSeePiercing(preferred, cell)) {
            return new Ballistica(preferred.pos, cell,
                    Ballistica.STOP_TARGET | Ballistica.STOP_SOLID);
        }
        if (canSeePiercing(alternate, cell)) {
            return new Ballistica(alternate.pos, cell,
                    Ballistica.STOP_TARGET | Ballistica.STOP_SOLID);
        }
        return null;
    }

    private static boolean canSeePiercing(Char attacker, int cell) {
        boolean[] sight = attacker == Dungeon.hero
                ? Dungeon.level.heroFOV : attacker.fieldOfView;
        return sight != null && sight[cell]
                && new Ballistica(attacker.pos, cell,
                        Ballistica.STOP_TARGET | Ballistica.STOP_SOLID).collisionPos == cell;
    }

    private static void pierceLane(Hero caster, Ballistica line, int targetCell,
                                   int amount, HashSet<Integer> alreadyHit) {
        if (line == null) {
            return;
        }
        // Do not hit beyond the selected target or through solid terrain.
        for (Mob mob : new ArrayList<>(Dungeon.level.mobs)) {
            if (line.subPath(0, line.dist).contains(mob.pos)
                    && Dungeon.level.distance(line.sourcePos, mob.pos)
                       <= Dungeon.level.distance(line.sourcePos, targetCell)
                    && alreadyHit.add(mob.id())) {
                damageEnemy(caster, mob, amount);
            }
        }
    }

    /** Tactical push: one traversable tile only, never into pits or traps. */
    private static void pushAway(Mob mob, Hero hero, CoHeroAlly companion) {
        if (!mob.isAlive() || mob.rooted
                || Char.hasProp(mob, Char.Property.BOSS)
                || Char.hasProp(mob, Char.Property.IMMOVABLE)) {
            return;
        }
        int origin = Dungeon.level.distance(hero.pos, mob.pos)
                <= Dungeon.level.distance(companion.pos, mob.pos)
                ? hero.pos : companion.pos;
        int width = Dungeon.level.width();
        int dx = Integer.signum(mob.pos % width - origin % width);
        int dy = Integer.signum(mob.pos / width - origin / width);
        if (dx == 0 && dy == 0) {
            return;
        }
        int destination = mob.pos + dx + dy * width;
        if (!safeDisplacementCell(destination)
                || Dungeon.level.distance(mob.pos, destination) != 1
                || (Char.hasProp(mob, Char.Property.LARGE)
                    && !Dungeon.level.openSpace[destination])) {
            return;
        }
        // STOP_TARGET is essential: MAGIC_BOLT keeps travelling past an empty cell.
        Ballistica pushLine = new Ballistica(mob.pos, destination, Ballistica.PROJECTILE);
        if (pushLine.collisionPos == destination) {
            WandOfBlastWave.throwChar(mob, pushLine, 1, false, false, CoHeroCombo.class);
        }
    }

    /** Optional safe one-tile disengage; no teleports into pits/traps or CoHero's square. */
    private static void retreatFrom(Hero hero, CoHeroAlly companion, int threatCell) {
        if (hero.rooted) {
            return;
        }
        int origin = hero.pos;
        int current = Dungeon.level.distance(origin, threatCell);
        int best = -1;
        int bestDistance = current;
        for (int offset : PathFinder.NEIGHBOURS8) {
            int candidate = origin + offset;
            if (!safeDisplacementCell(candidate)
                    || Dungeon.level.distance(origin, candidate) != 1
                    || !Dungeon.level.heroFOV[candidate]
                    || Dungeon.level.distance(candidate, companion.pos) > PARTY_RANGE
                    || adjacentHostile(candidate)) {
                continue;
            }
            int distance = Dungeon.level.distance(candidate, threatCell);
            if (distance > bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        if (best != -1) {
            ScrollOfTeleportation.teleportToLocation(hero, best);
        }
    }

    /** Only relocate onto traversable, unoccupied and immediately harmless tiles. */
    private static boolean safeDisplacementCell(int cell) {
        return Dungeon.level.insideMap(cell)
                && Dungeon.level.passable[cell]
                && !Dungeon.level.avoid[cell] && !Dungeon.level.pit[cell]
                && Dungeon.level.traps.get(cell) == null
                && Actor.findChar(cell) == null
                && Blob.volumeAt(cell, Fire.class) == 0
                && Blob.volumeAt(cell, ToxicGas.class) == 0
                && Blob.volumeAt(cell, ParalyticGas.class) == 0
                && Blob.volumeAt(cell, CorrosiveGas.class) == 0
                && Blob.volumeAt(cell, ConfusionGas.class) == 0
                && Blob.volumeAt(cell, Electricity.class) == 0;
    }

    private static boolean adjacentHostile(int cell) {
        for (Mob mob : Dungeon.level.mobs) {
            if (mob.isAlive() && mob.alignment == Char.Alignment.ENEMY
                    && Dungeon.level.distance(cell, mob.pos) <= 1) {
                return true;
            }
        }
        return false;
    }

    private static boolean inClearArea(int center, int radius, Mob mob) {
        return mob.isAlive() && mob.alignment == Char.Alignment.ENEMY
                && Dungeon.level.distance(center, mob.pos) <= radius
                && !Dungeon.level.solid[mob.pos]
                && new Ballistica(center, mob.pos,
                        Ballistica.STOP_TARGET | Ballistica.STOP_SOLID).collisionPos == mob.pos;
    }

    private static void damageArea(Hero caster, int center, int radius, int amount) {
        for (Mob mob : new ArrayList<>(Dungeon.level.mobs)) {
            if (inClearArea(center, radius, mob)) {
                damageEnemy(caster, mob, amount);
            }
        }
    }

    private static void damageEnemy(Hero caster, Mob mob, int amount) {
        if (mob.alignment == Char.Alignment.ENEMY && mob.isAlive()
                && !mob.isInvulnerable(caster.getClass()) && amount > 0) {
            mob.damage(amount, caster);
        }
    }

    private static void shield(Char hero, int amount) {
        Barrier barrier = Buff.affect(hero, Barrier.class);
        if (barrier != null) {
            barrier.incShield(amount);
        }
    }

    private static void heal(Char hero, int amount) {
        if (hero.isAlive()) {
            hero.HP = Math.min(hero.HT, hero.HP + amount);
        }
    }
}
