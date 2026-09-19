package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Charm;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Chill;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Corruption;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Doom;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Frost;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Roots;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.DamageWand;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.Wand;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfCorruption;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfDisintegration;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfFrost;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfLightning;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfMagicMissile;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfPrismaticLight;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfRegrowth;
import com.shatteredpixel.shatteredpixeldungeon.items.wands.WandOfTransfusion;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.ConeAOE;

import java.util.ArrayList;
import java.util.List;

/**
 * Fail-closed capability adapter for stock SPD wands that have explicit CoHero semantics.
 *
 * The wand classes still own their actual effects. This class only answers AI questions:
 * whether an action is legal/safe and how it should be categorized for decision-making.
 */
final class CoHeroWandAdapter {

    private CoHeroWandAdapter() {}

    static boolean supported(Wand wand) {
        if (wand == null) {
            return false;
        }
        Class<?> type = wand.getClass();
        return type == WandOfMagicMissile.class
                || type == WandOfFrost.class
                || type == WandOfDisintegration.class
                || type == WandOfLightning.class
                || type == WandOfPrismaticLight.class
                || type == WandOfRegrowth.class
                || type == WandOfTransfusion.class
                || type == WandOfCorruption.class;
    }

    static boolean offensiveCapability(Wand wand) {
        return supported(wand) && wand.getClass() != WandOfRegrowth.class;
    }

    static boolean hasOffensivePotential(Wand wand, CoHeroAlly owner, Mob target) {
        if (!offensiveCapability(wand)
                || !wand.coHeroCanZap(owner)
                || target == null
                || target.isImmune(wand.getClass())
                || target.isInvulnerable(wand.getClass())) {
            return false;
        }

        if (wand instanceof WandOfFrost && target.buff(Frost.class) != null) {
            return false;
        }
        if (wand instanceof WandOfCorruption
                && (target.buff(Corruption.class) != null || target.buff(Doom.class) != null)) {
            return false;
        }
        if (wand instanceof WandOfTransfusion
                && !target.properties().contains(Char.Property.UNDEAD)
                && target.buff(Charm.class) != null) {
            return false;
        }
        return true;
    }

    static boolean canAffectEnemy(Wand wand, CoHeroAlly owner, Mob target) {
        if (!hasOffensivePotential(wand, owner, target)) {
            return false;
        }

        if (wand instanceof WandOfDisintegration) {
            return safeDisintegrationBeam((WandOfDisintegration) wand, owner, target);
        }

        return wand.coHeroBallistica(owner, target.pos).collisionPos == target.pos;
    }

    static boolean directDamage(Wand wand, Mob target) {
        if (wand instanceof WandOfMagicMissile
                || wand instanceof WandOfFrost
                || wand instanceof WandOfDisintegration
                || wand instanceof WandOfLightning
                || wand instanceof WandOfPrismaticLight) {
            return true;
        }
        return wand instanceof WandOfTransfusion
                && target.properties().contains(Char.Property.UNDEAD);
    }

    static boolean guaranteedControl(Wand wand, CoHeroAlly owner, Mob target) {
        return wand instanceof WandOfCorruption
                && canAffectEnemy(wand, owner, target)
                && ((WandOfCorruption) wand).coHeroPowerBeatsResistance(target);
    }

    static boolean fallbackControl(Wand wand, CoHeroAlly owner, Mob target) {
        if (!canAffectEnemy(wand, owner, target)) {
            return false;
        }

        if (wand instanceof WandOfCorruption) {
            return target.buff(Corruption.class) == null && target.buff(Doom.class) == null;
        }

        if (wand instanceof WandOfTransfusion) {
            return !target.properties().contains(Char.Property.UNDEAD)
                    && target.buff(Charm.class) == null;
        }

        return false;
    }

    static float expectedDamage(Wand wand, CoHeroAlly owner, Mob target) {
        if (!directDamage(wand, target)) {
            return Float.NEGATIVE_INFINITY;
        }

        if (wand instanceof WandOfDisintegration) {
            return expectedDisintegrationDamage((WandOfDisintegration) wand, owner, target);
        }

        DamageWand damageWand = (DamageWand) wand;
        int level = wand.buffedLvl();
        float average = (damageWand.min(level) + damageWand.max(level)) / 2f;

        if (wand instanceof WandOfFrost) {
            Chill chill = target.buff(Chill.class);
            if (chill != null) {
                float chillTurns = Math.min(10f, chill.cooldown());
                average *= Math.pow(0.9333f, chillTurns);
            }
        } else if (wand instanceof WandOfPrismaticLight
                && (target.properties().contains(Char.Property.DEMONIC)
                || target.properties().contains(Char.Property.UNDEAD))) {
            average *= 1.333f;
        }

        // Lightning uses a conservative primary-target score. Its actual chain can increase total
        // value, but estimating the full recursive arc would duplicate too much upstream logic.
        return average * target.resist(wand.getClass());
    }

    static boolean regrowthUsefulForEscape(
            Wand wand, CoHeroAlly owner, Mob aimedThreat, List<Mob> visibleThreats) {
        if (!(wand instanceof WandOfRegrowth)
                || !wand.coHeroCanZap(owner)
                || aimedThreat == null) {
            return false;
        }

        Ballistica core = wand.coHeroBallistica(owner, aimedThreat.pos);
        int charges = wand.coHeroChargesPerCast();
        ConeAOE cone = new ConeAOE(
                core,
                2 + 2 * charges,
                20 + 10 * charges,
                Ballistica.STOP_SOLID | Ballistica.STOP_TARGET);

        if (!cone.cells.contains(aimedThreat.pos)) {
            return false;
        }

        for (int cell : cone.cells) {
            Char ch = Actor.findChar(cell);
            if (ch == null || ch == owner) {
                continue;
            }
            if (ch.alignment != Char.Alignment.ENEMY) {
                return false;
            }
            if (ch instanceof Mob && ((Mob) ch).state == ((Mob) ch).SLEEPING) {
                return false;
            }
        }

        for (Mob threat : visibleThreats) {
            if (cone.cells.contains(threat.pos)
                    && !Char.hasProp(threat, Char.Property.IMMOVABLE)
                    && threat.buff(Roots.class) == null) {
                return true;
            }
        }
        return false;
    }

    static boolean transfusionShouldSupportHero(
            Wand wand, CoHeroAlly owner, Hero hero, boolean heroVisible) {
        if (!(wand instanceof WandOfTransfusion)
                || !heroVisible
                || hero == null
                || !hero.isAlive()
                || !wand.coHeroCanZap(owner)) {
            return false;
        }

        // Conservative health transfer: only rescue a badly injured Hero from a healthy CoHero.
        if (hero.HP * 2 >= hero.HT || owner.HP * 4 < owner.HT * 3) {
            return false;
        }

        int selfDamage = Math.round(owner.HT * 0.05f);
        if (owner.HP - selfDamage <= owner.HT / 2) {
            return false;
        }

        return wand.coHeroBallistica(owner, hero.pos).collisionPos == hero.pos;
    }

    private static boolean safeDisintegrationBeam(
            WandOfDisintegration wand, CoHeroAlly owner, Mob target) {
        Ballistica beam = wand.coHeroBallistica(owner, target.pos);
        int maxDistance = Math.min(wand.buffedLvl() * 2 + 6, beam.dist);
        int targetIndex = beam.path.indexOf(target.pos);
        if (targetIndex < 1 || targetIndex > maxDistance) {
            return false;
        }

        for (int cell : beam.subPath(1, maxDistance)) {
            Char ch = Actor.findChar(cell);
            if (ch == null) {
                continue;
            }
            if (ch.alignment != Char.Alignment.ENEMY) {
                return false;
            }
            if (ch instanceof Mob && ch != target && ((Mob) ch).state == ((Mob) ch).SLEEPING) {
                return false;
            }
        }
        return true;
    }

    private static float expectedDisintegrationDamage(
            WandOfDisintegration wand, CoHeroAlly owner, Mob target) {
        Ballistica beam = wand.coHeroBallistica(owner, target.pos);
        int maxDistance = Math.min(wand.buffedLvl() * 2 + 6, beam.dist);

        int terrainPassed = 2;
        int terrainBonus = 0;
        int targets = 0;
        float resistanceTotal = 0f;

        for (int cell : beam.subPath(1, maxDistance)) {
            Char ch = Actor.findChar(cell);
            if (ch != null) {
                terrainBonus += terrainPassed / 3;
                terrainPassed %= 3;
                if (ch.alignment == Char.Alignment.ENEMY) {
                    targets++;
                    resistanceTotal += ch.resist(wand.getClass());
                }
            }
            if (Dungeon.level.solid[cell]) {
                terrainPassed++;
            }
        }

        if (targets == 0) {
            return Float.NEGATIVE_INFINITY;
        }

        int level = wand.buffedLvl() + (targets - 1) + terrainBonus;
        float average = (wand.min(level) + wand.max(level)) / 2f;
        return average * resistanceTotal;
    }
}
