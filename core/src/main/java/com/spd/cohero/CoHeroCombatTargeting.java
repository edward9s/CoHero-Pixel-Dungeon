package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.CrystalGuardian;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Ghoul;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;

import java.util.ArrayList;

/**
 * Pure combat target selection and threat filtering.
 *
 * This class decides who matters and who should be attacked. It does not move, attack,
 * consume items, or decide whether CoHero should retreat.
 */
final class CoHeroCombatTargeting {

    private final CoHeroAlly owner;

    CoHeroCombatTargeting(CoHeroAlly owner) {
        this.owner = owner;
    }

    Mob nearestThreat(ArrayList<Mob> threats) {
        Mob result = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Mob threat : threats) {
            int distance = Dungeon.level.distance(owner.pos, threat.pos);
            if (result == null || distance < bestDistance) {
                result = threat;
                bestDistance = distance;
            }
        }
        return result;
    }

    Mob selectCombatTarget(ArrayList<Mob> threats) {
        if (threats == null || threats.isEmpty()) {
            return null;
        }

        ArrayList<Ghoul> ghouls = new ArrayList<>();
        Ghoul linkedHost = null;
        int linkedHostLinks = 0;
        for (Mob threat : threats) {
            if (!(threat instanceof Ghoul)) {
                continue;
            }

            Ghoul ghoul = (Ghoul) threat;
            ghouls.add(ghoul);

            int links = ghoul.buffs(Ghoul.GhoulLifeLink.class).size();
            if (links > 0
                    && (linkedHost == null
                        || betterLinkedGhoulHost(
                                ghoul, links, linkedHost, linkedHostLinks))) {
                linkedHost = ghoul;
                linkedHostLinks = links;
            }
        }

        if (linkedHost != null) {
            return linkedHost;
        }

        Ghoul lowHealthGhoul = null;
        for (Ghoul ghoul : ghouls) {
            int reviveHp = Math.round(ghoul.HT / 10f);
            if (ghoul.HP > reviveHp) {
                continue;
            }
            if (lowHealthGhoul == null || betterGhoulFocusTarget(ghoul, lowHealthGhoul)) {
                lowHealthGhoul = ghoul;
            }
        }
        if (lowHealthGhoul != null) {
            return lowHealthGhoul;
        }

        if (ghouls.size() >= 2) {
            Ghoul best = null;
            for (Ghoul ghoul : ghouls) {
                if (best == null || betterGhoulFocusTarget(ghoul, best)) {
                    best = ghoul;
                }
            }
            return best;
        }

        return nearestThreat(threats);
    }

    /**
     * Survival uses the fastest currently killable target, not the tactical focus target.
     * This keeps Ghoul host priority (and future target priorities) from inflating the whole
     * fight's TTK merely because that tactically important target is durable.
     */
    Mob selectSurvivalTarget(ArrayList<Mob> threats) {
        if (threats == null || threats.isEmpty()) {
            return null;
        }

        Mob best = null;
        float bestTtk = Float.POSITIVE_INFINITY;
        int bestDistance = Integer.MAX_VALUE;

        for (Mob threat : threats) {
            float ttk = owner.estimateTargetTtk(threat);
            int distance = Dungeon.level.distance(owner.pos, threat.pos);
            if (best == null
                    || ttk < bestTtk - 0.001f
                    || (Math.abs(ttk - bestTtk) <= 0.001f && distance < bestDistance)
                    || (Math.abs(ttk - bestTtk) <= 0.001f
                        && distance == bestDistance
                        && threat.id() < best.id())) {
                best = threat;
                bestTtk = ttk;
                bestDistance = distance;
            }
        }

        return best;
    }

    ArrayList<Mob> collectActiveThreats(ArrayList<Mob> threats) {
        ArrayList<Mob> result = new ArrayList<>();
        if (threats == null) {
            return result;
        }

        for (Mob threat : threats) {
            if (threat != null
                    && threat.isAlive()
                    && !isTemporarilyInactiveThreat(threat)) {
                result.add(threat);
            }
        }
        return result;
    }

    boolean hasRecoveringCrystalGuardian(ArrayList<Mob> threats) {
        if (threats == null) {
            return false;
        }
        for (Mob threat : threats) {
            if (isTemporarilyInactiveThreat(threat)) {
                return true;
            }
        }
        return false;
    }

    ArrayList<Mob> collectAttackableThreats(ArrayList<Mob> threats) {
        ArrayList<Mob> result = new ArrayList<>();
        if (threats == null) {
            return result;
        }

        for (Mob threat : threats) {
            if (threat != null
                    && threat.isAlive()
                    && !isTemporarilyInactiveThreat(threat)
                    && !owner.isCombatInvulnerable(threat)
                    && !owner.isCharmedBy(threat)) {
                result.add(threat);
            }
        }
        return result;
    }

    ArrayList<Mob> collectCharmingThreats(ArrayList<Mob> threats) {
        ArrayList<Mob> result = new ArrayList<>();
        if (threats == null) {
            return result;
        }

        for (Mob threat : threats) {
            if (threat != null
                    && threat.isAlive()
                    && !isTemporarilyInactiveThreat(threat)
                    && owner.isCharmedBy(threat)) {
                result.add(threat);
            }
        }
        return result;
    }

    private boolean isTemporarilyInactiveThreat(Mob threat) {
        return threat instanceof CrystalGuardian
                && ((CrystalGuardian) threat).recovering();
    }

    private boolean betterLinkedGhoulHost(
            Ghoul candidate, int candidateLinks, Ghoul current, int currentLinks) {
        if (candidateLinks != currentLinks) {
            return candidateLinks > currentLinks;
        }
        return betterGhoulFocusTarget(candidate, current);
    }

    private boolean betterGhoulFocusTarget(Ghoul candidate, Ghoul current) {
        int candidateHp = ghoulEffectiveHp(candidate);
        int currentHp = ghoulEffectiveHp(current);
        if (candidateHp != currentHp) {
            return candidateHp < currentHp;
        }

        int candidateDistance = Dungeon.level.distance(owner.pos, candidate.pos);
        int currentDistance = Dungeon.level.distance(owner.pos, current.pos);
        if (candidateDistance != currentDistance) {
            return candidateDistance < currentDistance;
        }

        return candidate.id() < current.id();
    }

    private int ghoulEffectiveHp(Ghoul ghoul) {
        return Math.max(0, ghoul.HP) + Math.max(0, ghoul.shielding());
    }
}
