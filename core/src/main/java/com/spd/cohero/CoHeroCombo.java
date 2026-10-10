package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Barrier;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Bless;
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
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.scenes.CellSelector;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.QuickSlotButton;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.utils.Bundle;

import java.util.ArrayList;
import java.util.HashMap;

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
                    + CoHeroMessages.get(heroClass == 3 && partnerClass == 5
                            ? "combo.detail.partner.3.5"
                            : "combo.detail.partner." + partnerClass);
            if ((heroClass == 0 && partnerClass == 5)
                    || (heroClass == 3 && partnerClass == 0)
                    || (heroClass == 4 && partnerClass == 5)) {
                effects += " " + CoHeroMessages.get(
                        "combo.detail.extra." + heroClass + "." + partnerClass);
            }
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
                && mob.sprite != null && mob.sprite.parent != null
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
                && canTargetFromEitherHero(mob.pos, hero, companion);
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

        // Every validation above precedes both resource and actor-time consumption.
        energy -= CAST_COST;

        // Presentation reads only the already-validated cast context. It neither delays
        // this action nor owns any damage, Buff, targeting, or actor callback.
        CoHeroComboFX.play(
                hero, companion, target == null ? hero.pos : target.pos, mainClass, partnerClass);

        if (mainClass == 5) {
            performClericUltimate(hero, companion, partnerClass);
        } else {
            performAttackUltimate(hero, companion, target, mainClass, partnerClass);
        }
        GLog.p(CoHeroMessages.get("combo.used", skillName()));
        companion.spendComboTurn();
        hero.spendAndNext(Actor.TICK);
    }

    private static boolean canTargetFromEitherHero(int cell, Hero hero, CoHeroAlly companion) {
        return (Dungeon.level.heroFOV[cell]
                    && new Ballistica(hero.pos, cell, Ballistica.PROJECTILE).collisionPos == cell)
                || (companion.fieldOfView != null && companion.fieldOfView[cell]
                    && new Ballistica(companion.pos, cell, Ballistica.PROJECTILE).collisionPos == cell);
    }

    private static void performAttackUltimate(
            Hero hero, CoHeroAlly companion, Mob target, int mainClass, int partnerClass) {
        int power = 9 + hero.lvl * 2;
        switch (mainClass) {
            case 0: // Warrior: heavy shockwave
                damageArea(hero, target.pos, 1, power);
                break;
            case 1: // Mage: wider magical eruption
                damageArea(hero, target.pos, 2, power);
                break;
            case 2: // Rogue: focused execution
                damageEnemy(hero, target, power * 2 + (target.HP < target.HT / 2 ? power : 0));
                break;
            case 3: // Huntress: line attack, respecting the target's projectile lane
                // Ignore intervening mobs for a piercing attack, but never go through walls
                // or beyond the chosen target (Ballistica.path contains cells past collision).
                final int terrainLine = Ballistica.STOP_TARGET | Ballistica.STOP_SOLID;
                Ballistica line = new Ballistica(hero.pos, target.pos, terrainLine);
                if (line.collisionPos != target.pos) {
                    line = new Ballistica(companion.pos, target.pos, terrainLine);
                }
                for (Mob mob : new ArrayList<>(Dungeon.level.mobs)) {
                    if (line.subPath(0, line.dist).contains(mob.pos)) {
                        damageEnemy(hero, mob, power + hero.lvl);
                    }
                }
                break;
            case 4: // Duelist: concentrated double strike
                damageEnemy(hero, target, power * 3);
                break;
            default:
                throw new IllegalStateException("Unexpected offensive combo class " + mainClass);
        }

        // Partner role adds a second component. Ordered pairs yield 36 distinct combinations.
        switch (partnerClass) {
            case 0:
                shield(hero, 5 + hero.lvl);
                shield(companion, 5 + hero.lvl);
                if (mainClass == 3 && target.isAlive()) {
                    Buff.prolong(target, Vulnerable.class, 3f);
                }
                break;
            case 1:
                damageArea(hero, target.pos, 1, 4 + hero.lvl);
                break;
            case 2:
                if (target.isAlive()) {
                    Buff.prolong(target, Vulnerable.class, 3f);
                }
                break;
            case 3:
                if (target.isAlive()) {
                    Buff.prolong(target, Cripple.class, 3f);
                }
                break;
            case 4:
                damageEnemy(hero, target, 5 + hero.lvl * 2);
                break;
            case 5:
                if (mainClass == 3) {
                    shield(hero, 5 + hero.lvl);
                    shield(companion, 5 + hero.lvl);
                } else {
                    heal(hero, 4 + hero.lvl);
                    heal(companion, 4 + hero.lvl);
                }
                if (mainClass == 0 && target.isAlive()) {
                    Buff.prolong(target, Weakness.class, 3f);
                }
                if (mainClass == 4) {
                    PotionOfCleansing.cleanse(hero);
                    PotionOfCleansing.cleanse(companion);
                }
                break;
            default:
                throw new IllegalStateException("Unexpected partner combo class " + partnerClass);
        }
    }

    private static void performClericUltimate(
            Hero hero, CoHeroAlly companion, int partnerClass) {
        switch (partnerClass) {
            case 0: // Guardian oath
                shield(hero, 14 + hero.lvl * 2);
                shield(companion, 14 + hero.lvl * 2);
                break;
            case 1: // Holy magic and recovery
                damageArea(hero, hero.pos, 2, 7 + hero.lvl);
                PotionOfCleansing.cleanse(hero);
                PotionOfCleansing.cleanse(companion);
                heal(hero, 5 + hero.lvl);
                heal(companion, 5 + hero.lvl);
                break;
            case 2: // Protective invisibility
                Buff.prolong(hero, Invisibility.class, 4f);
                Buff.prolong(companion, Invisibility.class, 4f);
                break;
            case 3: // Sanctuary and ranged suppression
                heal(hero, 6 + hero.lvl);
                heal(companion, 6 + hero.lvl);
                for (Mob mob : new ArrayList<>(Dungeon.level.mobs)) {
                    if (mob.alignment == Char.Alignment.ENEMY && mob.isAlive()
                            && Dungeon.level.distance(companion.pos, mob.pos) <= 4
                            && canTargetFromEitherHero(mob.pos, hero, companion)) {
                        damageEnemy(hero, mob, 3 + hero.lvl);
                    }
                }
                break;
            case 4: // Vow of counterattack
                shield(hero, 8 + hero.lvl);
                shield(companion, 8 + hero.lvl);
                Buff.prolong(hero, Bless.class, 6f);
                Buff.prolong(companion, Bless.class, 6f);
                break;
            case 5: // Double sanctuary
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

    private static void damageArea(Hero caster, int center, int radius, int amount) {
        for (Mob mob : new ArrayList<>(Dungeon.level.mobs)) {
            if (Dungeon.level.distance(center, mob.pos) <= radius
                    && !Dungeon.level.solid[mob.pos]
                    && new Ballistica(center, mob.pos,
                        Ballistica.STOP_TARGET | Ballistica.STOP_SOLID).collisionPos == mob.pos) {
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
