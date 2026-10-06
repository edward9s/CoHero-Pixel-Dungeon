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

    Mob selectCombatTarget(ArrayList<Mob> candidates, ArrayList<Mob> activeEnemies) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        Mob strategicSource = selectStrategicSource(candidates, activeEnemies);
        if (strategicSource != null) {
            return strategicSource;
        }

        ArrayList<Ghoul> ghouls = new ArrayList<>();
        Ghoul linkedHost = null;
        int linkedHostLinks = 0;
        for (Mob threat : candidates) {
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

        return nearestThreat(candidates);
    }

    private Mob selectStrategicSource(
            ArrayList<Mob> candidates, ArrayList<Mob> activeEnemies) {
        if (activeEnemies == null || activeEnemies.isEmpty()) {
            return null;
        }

        Mob best = null;
        int bestRemoved = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (Mob source : candidates) {
            int removed = 0;
            for (Mob dependent : activeEnemies) {
                if (dependent != source
                        && dependent != null
                        && dependent.isAlive()
                        && source.coHeroDeathRemoves(dependent)) {
                    removed++;
                }
            }
            if (removed == 0) {
                continue;
            }

            int distance = Dungeon.level.distance(owner.pos, source.pos);
            if (best == null
                    || removed > bestRemoved
                    || (removed == bestRemoved && distance < bestDistance)
                    || (removed == bestRemoved
                        && distance == bestDistance
                        && source.id() < best.id())) {
                best = source;
                bestRemoved = removed;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Survival uses the fastest currently killable root target, not the tactical focus target.
     * A dependent whose death-removing source is itself currently attackable is excluded: killing
     * that dependent does not resolve the source and can understate the real fight duration.
     */
    Mob selectSurvivalTarget(ArrayList<Mob> threats) {
        if (threats == null || threats.isEmpty()) {
            return null;
        }

        Mob best = null;
        float bestTtk = Float.POSITIVE_INFINITY;
        int bestDistance = Integer.MAX_VALUE;

        for (Mob threat : threats) {
            if (hasAttackableDeathRemovingSource(threat, threats)) {
                continue;
            }

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

        if (best == null) {
            throw new IllegalStateException(
                    "All CoHero survival targets were excluded without an attackable root");
        }
        return best;
    }

    private boolean hasAttackableDeathRemovingSource(
            Mob dependent, ArrayList<Mob> attackableThreats) {
        for (Mob source : attackableThreats) {
            if (source != dependent
                    && source.isAlive()
                    && source.coHeroDeathRemoves(dependent)) {
                return true;
            }
        }
        return false;
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
