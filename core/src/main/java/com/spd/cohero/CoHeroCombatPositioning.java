package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;

import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;

import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;

import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Scorpio;

import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Swarm;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;

import com.watabou.utils.PathFinder;

import java.util.ArrayList;

import java.util.Arrays;
import java.util.BitSet;

/**

 * Tactical cell selection and combat movement.

 *

 * Target priority belongs to CoHeroCombatTargeting; enemy-specific mechanics belong to

 * CoHeroEnemyTactics. This class only owns where CoHero should stand or move.

 */

final class CoHeroCombatPositioning {

    private static final int ENCIRCLEMENT_SEARCH_RADIUS = 5;

    private static final int CHOKE_REAR_SCAN_RADIUS = 6;

    private static final int GREAT_CRAB_TACTICAL_SEARCH_RADIUS = 5;

    private static final int RANGED_COVER_SEARCH_RADIUS = 6;

    private final CoHeroAlly owner;
    private int[] chokeSide0Distance = new int[0];
    private int[] chokeSide1Distance = new int[0];
    private int[] chokeQueue = new int[0];
    private Object chokeTopologyLevel;
    private boolean[] chokePassableSnapshot;
    private ChokeTopology[] chokeTopologyByCell = new ChokeTopology[0];
    private boolean[] chokeTopologyComputed = new boolean[0];

    private static final class ChokeTopology {
        final int exit0;
        final int exit1;
        final BitSet side0;
        final BitSet side1;

        ChokeTopology(int exit0, int exit1, BitSet side0, BitSet side1) {
            this.exit0 = exit0;
            this.exit1 = exit1;
            this.side0 = side0;
            this.side1 = side1;
        }
    }

    CoHeroCombatPositioning(CoHeroAlly owner) {

        this.owner = owner;

    }

    Boolean tryAvoidCharmingThreats(
            ArrayList<Mob> charmingThreats, ArrayList<Mob> allThreats) {
        if (charmingThreats == null || charmingThreats.isEmpty()) {
            return null;
        }
        if (allThreats == null || allThreats.isEmpty()) {
            throw new IllegalArgumentException(
                    "Charm avoidance requires the current visible threat set");
        }

        int escapeStep = owner.rooted
                ? -1
                : chooseCharmedEscapeStep(charmingThreats, allThreats);
        if (escapeStep == -1) {
            return null;
        }

        int oldPos = owner.pos;
        owner.clearCombatTarget();
        owner.allowAnyGuardMovement();
        owner.setMovementDecision("charm_escape", escapeStep);
        owner.move(escapeStep, true);
        owner.spendActionTime(1 / owner.speed());
        Dungeon.level.updateFieldOfView(owner, owner.fieldOfView);
        owner.revealVisibleCells();
        return owner.animateMoveFrom(oldPos);
    }

    int chooseCharmedEscapeStep(
            ArrayList<Mob> charmingThreats, ArrayList<Mob> allThreats) {
        int bestCell = -1;
        int bestCharmerAttackers =
                owner.countCurrentAttackersAtCell(owner.pos, charmingThreats);
        int bestAllAttackers =
                owner.countCurrentAttackersAtCell(owner.pos, allThreats);
        float bestAllIncoming =
                owner.estimatedIncomingDptAtCell(owner.pos, allThreats);
        int bestVisibleCharmers =
                charmersSeeingCell(owner.pos, charmingThreats);
        int bestDistance =
                owner.nearestThreatDistance(owner.pos, charmingThreats);

        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = owner.pos + offset;
            if (!Dungeon.level.insideMap(cell)
                    || Dungeon.level.distance(owner.pos, cell) != 1
                    || !Dungeon.level.passable[cell]
                    || Actor.findChar(cell) != null
                    || !owner.isMovementSafe(cell)) {
                continue;
            }

            int charmerAttackers =
                    owner.countCurrentAttackersAtCell(cell, charmingThreats);
            int allAttackers =
                    owner.countCurrentAttackersAtCell(cell, allThreats);
            float allIncoming =
                    owner.estimatedIncomingDptAtCell(cell, allThreats);
            int visibleCharmers =
                    charmersSeeingCell(cell, charmingThreats);
            int distance =
                    owner.nearestThreatDistance(cell, charmingThreats);

            boolean better = charmerAttackers < bestCharmerAttackers
                    || (charmerAttackers == bestCharmerAttackers
                        && allAttackers < bestAllAttackers)
                    || (charmerAttackers == bestCharmerAttackers
                        && allAttackers == bestAllAttackers
                        && allIncoming < bestAllIncoming - 0.01f)
                    || (charmerAttackers == bestCharmerAttackers
                        && allAttackers == bestAllAttackers
                        && Math.abs(allIncoming - bestAllIncoming) <= 0.01f
                        && visibleCharmers < bestVisibleCharmers)
                    || (charmerAttackers == bestCharmerAttackers
                        && allAttackers == bestAllAttackers
                        && Math.abs(allIncoming - bestAllIncoming) <= 0.01f
                        && visibleCharmers == bestVisibleCharmers
                        && distance > bestDistance);

            if (better) {
                bestCell = cell;
                bestCharmerAttackers = charmerAttackers;
                bestAllAttackers = allAttackers;
                bestAllIncoming = allIncoming;
                bestVisibleCharmers = visibleCharmers;
                bestDistance = distance;
            }
        }

        return bestCell;
    }

    private int charmersSeeingCell(int cell, ArrayList<Mob> charmingThreats) {
        int result = 0;
        for (Mob charmer : charmingThreats) {
            boolean[] fov = charmer.fieldOfView;
            if (fov == null || fov.length != Dungeon.level.length()) {
                fov = new boolean[Dungeon.level.length()];
                Dungeon.level.updateFieldOfView(charmer, fov);
            }
            if (fov[cell]) {
                result++;
            }
        }
        return result;
    }

    Boolean tryAvoidInvulnerableThreats(ArrayList<Mob> threats) {
        if (threats == null || threats.isEmpty()) {
            return null;
        }

        ArrayList<Mob> invulnerableThreats = new ArrayList<>();
        for (Mob threat : threats) {
            if (threat != null
                    && threat.isAlive()
                    && owner.isCombatInvulnerable(threat)) {
                invulnerableThreats.add(threat);
            }
        }
        if (invulnerableThreats.isEmpty()
                || owner.countCurrentAttackersAtCell(owner.pos, invulnerableThreats) == 0) {
            return null;
        }
        owner.clearCombatTarget();

        owner.logBossDecision("invulnerable_range_retreat",
                "invulnerable enemy can attack current cell -> leave attack range");

        int escapeStep = owner.rooted
                ? -1
                : chooseInvulnerableEscapeStep(invulnerableThreats, threats);
        if (escapeStep != -1) {
            int oldPos = owner.pos;
            owner.allowAnyGuardMovement();
            owner.setMovementDecision("invulnerable_escape", escapeStep);
            owner.move(escapeStep, true);
            owner.spendActionTime(1 / owner.speed());
            Dungeon.level.updateFieldOfView(owner, owner.fieldOfView);
            owner.revealVisibleCells();
            return owner.animateMoveFrom(oldPos);
        }

        // No ordinary step improves the invulnerable threat exposure. Escape resources are allowed
        // here even when other damageable enemies are present: staying in an attack range that
        // CoHero cannot answer is the worse failure mode.
        if (owner.controlItems().tryEmergencyBlinkRunestone(invulnerableThreats)) {
            return true;
        }
        if (owner.controlItems().tryUseTeleportationScroll()) {
            return true;
        }
        if (owner.survival().tryUseInvisibilityPotion()) {
            return true;
        }
        float incoming = owner.estimatedIncomingDptAtCell(owner.pos, invulnerableThreats);
        if (owner.survival().tryEmergencySurvivalPotion(incoming, incoming)) {
            return true;
        }

        return null;
    }

    int chooseInvulnerableEscapeStep(
            ArrayList<Mob> invulnerableThreats, ArrayList<Mob> allThreats) {
        int currentInvulnerableAttackers =
                owner.countCurrentAttackersAtCell(owner.pos, invulnerableThreats);
        float currentInvulnerableIncoming =
                owner.estimatedIncomingDptAtCell(owner.pos, invulnerableThreats);
        int currentAllAttackers = owner.countCurrentAttackersAtCell(owner.pos, allThreats);
        float currentAllIncoming = owner.estimatedIncomingDptAtCell(owner.pos, allThreats);
        int currentDistance = owner.nearestThreatDistance(owner.pos, invulnerableThreats);

        int bestCell = -1;
        int bestInvulnerableAttackers = currentInvulnerableAttackers;
        float bestInvulnerableIncoming = currentInvulnerableIncoming;
        int bestAllAttackers = currentAllAttackers;
        float bestAllIncoming = currentAllIncoming;
        int bestDistance = currentDistance;

        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = owner.pos + offset;
            if (cell < 0
                    || cell >= Dungeon.level.length()
                    || Dungeon.level.distance(owner.pos, cell) != 1
                    || !Dungeon.level.passable[cell]
                    || Actor.findChar(cell) != null
                    || !owner.isMovementSafe(cell)) {
                continue;
            }

            int invulnerableAttackers =
                    owner.countCurrentAttackersAtCell(cell, invulnerableThreats);
            float invulnerableIncoming =
                    owner.estimatedIncomingDptAtCell(cell, invulnerableThreats);
            int allAttackers = owner.countCurrentAttackersAtCell(cell, allThreats);
            float allIncoming = owner.estimatedIncomingDptAtCell(cell, allThreats);
            int distance = owner.nearestThreatDistance(cell, invulnerableThreats);

            boolean better =
                    invulnerableAttackers < bestInvulnerableAttackers
                    || (invulnerableAttackers == bestInvulnerableAttackers
                        && invulnerableIncoming < bestInvulnerableIncoming - 0.01f)
                    || (invulnerableAttackers == bestInvulnerableAttackers
                        && Math.abs(invulnerableIncoming - bestInvulnerableIncoming) <= 0.01f
                        && allAttackers < bestAllAttackers)
                    || (invulnerableAttackers == bestInvulnerableAttackers
                        && Math.abs(invulnerableIncoming - bestInvulnerableIncoming) <= 0.01f
                        && allAttackers == bestAllAttackers
                        && allIncoming < bestAllIncoming - 0.01f)
                    || (invulnerableAttackers == bestInvulnerableAttackers
                        && Math.abs(invulnerableIncoming - bestInvulnerableIncoming) <= 0.01f
                        && allAttackers == bestAllAttackers
                        && Math.abs(allIncoming - bestAllIncoming) <= 0.01f
                        && distance > bestDistance);

            if (better) {
                bestCell = cell;
                bestInvulnerableAttackers = invulnerableAttackers;
                bestInvulnerableIncoming = invulnerableIncoming;
                bestAllAttackers = allAttackers;
                bestAllIncoming = allIncoming;
                bestDistance = distance;
            }
        }

        return bestCell;
    }

    int chooseRangedCoverCell(Mob targetMob, ArrayList<Mob> threats) {
        if (targetMob == null
                || targetMob.fieldOfView == null
                || targetMob.fieldOfView.length != Dungeon.level.length()) {
            return -1;
        }

        boolean[] passable = rangedLurePassable();
        PathFinder.buildDistanceMap(owner.pos, passable);

        ArrayList<Integer> candidates = new ArrayList<>();
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (cell == owner.pos
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
                    Dungeon.findPath(owner, cell, passable, owner.fieldOfView, true);
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

            int attackers = owner.countCurrentAttackersAtCell(cell, threats);
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

    boolean isRangedCoverCell(int cell, Mob targetMob) {
        if (targetMob == null
                || targetMob.fieldOfView == null
                || targetMob.fieldOfView.length != Dungeon.level.length()
                || !Dungeon.level.insideMap(cell)
                || !Dungeon.level.passable[cell]
                || !owner.isKnown(cell)
                || !owner.isMovementSafe(cell)
                || targetMob.fieldOfView[cell]) {
            return false;
        }

        if (!owner.fieldOfView[cell]) {
            return true;
        }

        Char occupant = Actor.findChar(cell);
        return occupant == null || occupant == owner;
    }

    boolean[] rangedLurePassable() {
        boolean[] result = Dungeon.level.passable.clone();
        for (int cell = 0; cell < result.length; cell++) {
            if (cell == owner.pos) {
                result[cell] = true;
                continue;
            }

            if (!result[cell] || !owner.isKnown(cell) || !owner.isMovementSafe(cell)) {
                result[cell] = false;
                continue;
            }

            // Only use currently visible occupancy information. Do not inspect actors hidden
            // behind cover merely to improve pathfinding.
            if (owner.fieldOfView[cell]) {
                Char occupant = Actor.findChar(cell);
                if (occupant != null && occupant != owner) {
                    result[cell] = false;
                }
            }
        }
        return result;
    }

    int rangedLureStep(int destination) {
        if (owner.rooted || destination == owner.pos || !Dungeon.level.insideMap(destination)) {
            return -1;
        }

        boolean[] passable = rangedLurePassable();
        int step = Dungeon.findStep(owner, destination, passable, owner.fieldOfView, true);
        return step != -1 && owner.isMovementSafe(step) ? step : -1;
    }

    Boolean tryEncirclementPositioning(
            Mob targetMob, ArrayList<Mob> threats) {
        if (targetMob == null
                || threats == null
                || threats.isEmpty()) {
            return null;
        }

        // Swarm is the only single-enemy special case here. This estimate must be derived from
        // the tactical target itself, not from the separate survival-race target.
        float expectedNextDamage = owner.canAttack(targetMob)
                ? owner.estimateMeleeDpt(targetMob) * Math.max(0.25f, owner.attackDelay())
                : owner.estimateBestRangedDpt(targetMob);
        boolean swarmSplitPressure =
                targetMob instanceof Swarm
                        && !isSwarmEngagingHero(targetMob)
                        && targetMob.HP >= expectedNextDamage + 2f;
        ArrayList<Mob> meleeThreats = collectEncirclementMeleeThreats(threats);
        boolean crowdedMelee = meleeThreats.size() >= 2;
        if (!swarmSplitPressure && !crowdedMelee) {
            return null;
        }

        // Ranged pressure does not disable anti-encirclement positioning. Melee threats define
        // whether a choke actually limits frontage; every threat still contributes to incoming
        // DPT when choosing between otherwise valid positions.
        long searchStarted = owner.timings().startNanos();
        int tacticalCell;
        try {
            tacticalCell = chooseEncirclementCell(targetMob, meleeThreats, threats);
        } finally {
            owner.timings().record(
                    owner, CoHeroTimings.Action.ENCIRCLEMENT_SEARCH, searchStarted);
        }
        if (tacticalCell != -1 && tacticalCell != owner.pos) {
            int oldPos = owner.pos;
            owner.releaseGuardAreaForCombat();
            owner.setMovementDecision("encirclement_positioning", tacticalCell);
            if (owner.getCloser(tacticalCell)) {
                owner.spendActionTime(1 / owner.speed());
                Dungeon.level.updateFieldOfView(owner, owner.fieldOfView);
                owner.revealVisibleCells();
                return owner.animateMoveFrom(oldPos);
            }
        } else if (tacticalCell == owner.pos) {
            return null;
        }

        // With several melee threats and no usable choke nearby, prefer a step that already
        // improves current exposure against the whole threat set, including ranged enemies.
        // chooseEscapeStep refuses neutral/worse moves, so this does not make CoHero run forever
        // from a lone swarm in an open room.
        if (crowdedMelee && !owner.rooted) {
            long escapeStarted = owner.timings().startNanos();
            try {
                int escape = chooseEscapeStep(threats);
                if (escape != -1) {
                    int oldPos = owner.pos;
                    owner.releaseGuardAreaForCombat();
                    owner.setMovementDecision("encirclement_escape", escape);
                    owner.move(escape, true);
                    if (owner.pos != oldPos) {
                        owner.spendActionTime(1 / owner.speed());
                        Dungeon.level.updateFieldOfView(owner, owner.fieldOfView);
                        owner.revealVisibleCells();
                        return owner.animateMoveFrom(oldPos);
                    }
                }
            } finally {
                owner.timings().record(
                        owner, CoHeroTimings.Action.ENCIRCLEMENT_ESCAPE, escapeStarted);
            }
        }

        return null;
    }

    ArrayList<Mob> collectEncirclementMeleeThreats(ArrayList<Mob> threats) {
        ArrayList<Mob> result = new ArrayList<>();
        for (Mob threat : threats) {
            if (threat == null
                    || !threat.isAlive()
                    || owner.isCombatInvulnerable(threat)
                    || owner.hasNonAdjacentAttackCapability(threat)
                    || !owner.hasAdjacentAttackCapability(threat)
                    || isSwarmEngagingHero(threat)) {
                continue;
            }
            result.add(threat);
        }
        return result;
    }

    private boolean isSwarmEngagingHero(Mob threat) {
        return threat instanceof Swarm
                && Dungeon.hero != null
                && Dungeon.hero.isAlive()
                && Dungeon.level.adjacent(threat.pos, Dungeon.hero.pos);
    }

    int chooseEncirclementCell(
            Mob targetMob, ArrayList<Mob> meleeThreats, ArrayList<Mob> allThreats) {
        if (meleeThreats.isEmpty()) {
            return -1;
        }

        boolean profile = owner.timings().isEnabled();
        long filterNanos = 0L;
        long dynamicNanos = 0L;

        long phaseStarted = profile ? System.nanoTime() : 0L;
        boolean[] movementSafe = owner.movementSafeMask();
        ensureChokeTopologyCache();
        if (profile) {
            filterNanos += System.nanoTime() - phaseStarted;
        }

        phaseStarted = profile ? System.nanoTime() : 0L;
        PathFinder.buildDistanceMap(
                owner.pos, Dungeon.level.passable, ENCIRCLEMENT_SEARCH_RADIUS);
        if (profile) {
            owner.timings().recordElapsed(
                    owner,
                    CoHeroTimings.Action.ENCIRCLEMENT_DISTANCE_MAP,
                    System.nanoTime() - phaseStarted);
        }

        int best = -1;
        float bestIncoming = Float.POSITIVE_INFINITY;
        int bestPathDistance = Integer.MAX_VALUE;
        int bestTargetDistance = Integer.MAX_VALUE;

        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            phaseStarted = profile ? System.nanoTime() : 0L;
            int pathDistance = PathFinder.distance[cell];
            boolean candidate = pathDistance != Integer.MAX_VALUE
                    && pathDistance <= ENCIRCLEMENT_SEARCH_RADIUS
                    && owner.fieldOfView[cell]
                    && owner.isKnown(cell)
                    && Dungeon.level.passable[cell]
                    && movementSafe[cell];
            if (candidate) {
                Char occupant = Actor.findChar(cell);
                candidate = occupant == null || occupant == owner;
            }
            if (profile) {
                filterNanos += System.nanoTime() - phaseStarted;
            }
            if (!candidate) {
                continue;
            }

            ChokeTopology topology = chokeTopology(cell);
            if (topology == null) {
                continue;
            }

            phaseStarted = profile ? System.nanoTime() : 0L;
            if (!isDefensibleChokeCached(cell, topology, meleeThreats)) {
                if (profile) {
                    dynamicNanos += System.nanoTime() - phaseStarted;
                }
                continue;
            }

            float incoming = owner.estimatedIncomingDptAtCell(cell, allThreats);
            int targetDistance = Dungeon.level.distance(cell, targetMob.pos);

            boolean better = best == -1
                    || incoming < bestIncoming - 0.01f
                    || (Math.abs(incoming - bestIncoming) <= 0.01f
                        && pathDistance < bestPathDistance)
                    || (Math.abs(incoming - bestIncoming) <= 0.01f
                        && pathDistance == bestPathDistance
                        && targetDistance < bestTargetDistance)
                    || (Math.abs(incoming - bestIncoming) <= 0.01f
                        && pathDistance == bestPathDistance
                        && targetDistance == bestTargetDistance
                        && cell < best);
            if (better) {
                best = cell;
                bestIncoming = incoming;
                bestPathDistance = pathDistance;
                bestTargetDistance = targetDistance;
            }
            if (profile) {
                dynamicNanos += System.nanoTime() - phaseStarted;
            }
        }

        if (profile) {
            owner.timings().recordElapsed(
                    owner, CoHeroTimings.Action.ENCIRCLEMENT_FILTER, filterNanos);
            owner.timings().recordElapsed(
                    owner, CoHeroTimings.Action.ENCIRCLEMENT_DYNAMIC, dynamicNanos);
        }
        return best;
    }

    boolean isDefensibleChoke(int cell, ArrayList<Mob> threats) {
        ensureChokeTopologyCache();
        ChokeTopology topology = chokeTopology(cell);
        return topology != null && isDefensibleChokeCached(cell, topology, threats);
    }

    private boolean isDefensibleChokeCached(
            int cell, ChokeTopology topology, ArrayList<Mob> threats) {
        if (threats == null || threats.isEmpty()) {
            return false;
        }

        // If multiple enemies can already attack this cell, its geometry is not protecting us.
        if (owner.countCurrentAttackersAtCell(cell, threats) > 1) {
            return false;
        }

        boolean pressure0 = false;
        boolean pressure1 = false;
        for (Mob threat : threats) {
            if (!Dungeon.level.insideMap(threat.pos)) {
                continue;
            }
            boolean side0 = topology.side0.get(threat.pos);
            boolean side1 = topology.side1.get(threat.pos);

            // Both exits are locally reachable without crossing the candidate cell: enemies can
            // flank this position in the near term, so it is not a real defensive choke.
            if (side0 && side1) {
                return false;
            }
            pressure0 |= side0;
            pressure1 |= side1;
        }

        if (pressure0 == pressure1) {
            return false;
        }

        int rear = pressure0 ? topology.exit1 : topology.exit0;
        return owner.isKnown(rear)
                && owner.isMovementSafe(rear)
                && Actor.findChar(rear) == null;
    }

    private ChokeTopology chokeTopology(int cell) {
        if (!Dungeon.level.insideMap(cell)) {
            return null;
        }
        if (chokeTopologyComputed[cell]) {
            return chokeTopologyByCell[cell];
        }

        boolean profile = owner.timings().isEnabled();
        long started = profile ? System.nanoTime() : 0L;
        chokeTopologyComputed[cell] = true;
        try {
        int[] exits = new int[2];
        int exitCount = 0;
        for (int offset : PathFinder.NEIGHBOURS8) {
            int adjacent = cell + offset;
            if (adjacent < 0
                    || adjacent >= Dungeon.level.length()
                    || Dungeon.level.distance(cell, adjacent) != 1
                    || !Dungeon.level.passable[adjacent]) {
                continue;
            }
            if (exitCount == exits.length) {
                return null;
            }
            exits[exitCount++] = adjacent;
        }
        if (exitCount != 2) {
            return null;
        }

        ensureChokeScratch();
        int[] side0Distance =
                localPathDistances(
                        exits[0], cell, CHOKE_REAR_SCAN_RADIUS, chokeSide0Distance);
        int[] side1Distance =
                localPathDistances(
                        exits[1], cell, CHOKE_REAR_SCAN_RADIUS, chokeSide1Distance);

        BitSet side0 = new BitSet(Dungeon.level.length());
        BitSet side1 = new BitSet(Dungeon.level.length());
        for (int candidate = 0; candidate < Dungeon.level.length(); candidate++) {
            if (side0Distance[candidate] >= 0) {
                side0.set(candidate);
            }
            if (side1Distance[candidate] >= 0) {
                side1.set(candidate);
            }
        }

        ChokeTopology topology = new ChokeTopology(exits[0], exits[1], side0, side1);
        chokeTopologyByCell[cell] = topology;
        return topology;
        } finally {
            if (profile) {
                owner.timings().recordElapsed(
                        owner,
                        CoHeroTimings.Action.ENCIRCLEMENT_TOPOLOGY_BUILD,
                        System.nanoTime() - started);
            }
        }
    }

    private void ensureChokeTopologyCache() {
        int length = Dungeon.level.length();
        if (chokeTopologyLevel == Dungeon.level
                && chokePassableSnapshot != null
                && chokePassableSnapshot.length == length
                && Arrays.equals(chokePassableSnapshot, Dungeon.level.passable)) {
            return;
        }

        chokeTopologyLevel = Dungeon.level;
        chokePassableSnapshot = Dungeon.level.passable.clone();
        chokeTopologyByCell = new ChokeTopology[length];
        chokeTopologyComputed = new boolean[length];
    }

    private void ensureChokeScratch() {
        int length = Dungeon.level.length();
        if (chokeSide0Distance.length == length) {
            return;
        }
        chokeSide0Distance = new int[length];
        chokeSide1Distance = new int[length];
        chokeQueue = new int[length];
    }

    private int[] localPathDistances(
            int start, int blockedCell, int maxDistance, int[] distance) {
        Arrays.fill(distance, -1);
        if (!Dungeon.level.insideMap(start) || start == blockedCell) {
            return distance;
        }

        int head = 0;
        int tail = 0;
        distance[start] = 0;
        chokeQueue[tail++] = start;

        while (head < tail) {
            int current = chokeQueue[head++];
            int nextDistance = distance[current] + 1;
            if (nextDistance > maxDistance) {
                continue;
            }

            for (int offset : PathFinder.NEIGHBOURS8) {
                int next = current + offset;
                if (!Dungeon.level.insideMap(next)
                        || next == blockedCell
                        || Dungeon.level.distance(current, next) != 1
                        || distance[next] != -1
                        || !Dungeon.level.passable[next]) {
                    continue;
                }
                distance[next] = nextDistance;
                chokeQueue[tail++] = next;
            }
        }

        return distance;
    }

    int chooseEscapeStep(ArrayList<Mob> threats) {
        float moveTime = Math.max(0.25f, 1f / owner.speed());
        CoHeroThreatTiming current =
                owner.assessThreatTimingAtCell(owner.pos, threats, moveTime);

        int bestCell = -1;
        CoHeroThreatTiming best = current;

        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = owner.pos + offset;
            if (cell < 0
                    || cell >= Dungeon.level.length()
                    || Dungeon.level.distance(owner.pos, cell) != 1
                    || !Dungeon.level.passable[cell]
                    || Actor.findChar(cell) != null
                    || !owner.isMovementSafe(cell)) {
                continue;
            }

            CoHeroThreatTiming candidate =
                    owner.assessThreatTimingAtCell(cell, threats, moveTime);

            boolean gainsBreathingRoom =
                    best.nearestAttackTime <= moveTime + 0.001f
                    && candidate.nearestAttackTime > moveTime + 0.001f;
            boolean extendsExistingWindow =
                    best.nearestAttackTime > moveTime + 0.001f
                    && candidate.nearestAttackTime > best.nearestAttackTime + 0.01f;

            boolean better =
                    candidate.attackersWithinHorizon < best.attackersWithinHorizon
                    || (candidate.attackersWithinHorizon == best.attackersWithinHorizon
                        && candidate.incomingDptWithinHorizon
                                < best.incomingDptWithinHorizon - 0.01f)
                    || (candidate.attackersWithinHorizon == best.attackersWithinHorizon
                        && Math.abs(
                                candidate.incomingDptWithinHorizon
                                        - best.incomingDptWithinHorizon) <= 0.01f
                        && (gainsBreathingRoom || extendsExistingWindow));

            if (better) {
                bestCell = cell;
                best = candidate;
            }
        }

        return bestCell;
    }

    int chooseImmediateScorpioCaptureStep(
            Scorpio scorpio, ArrayList<Mob> threats) {
        int best = -1;
        int bestAttackers = Integer.MAX_VALUE;
        float bestIncoming = Float.POSITIVE_INFINITY;

        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = owner.pos + offset;
            if (!Dungeon.level.insideMap(cell)
                    || Dungeon.level.distance(owner.pos, cell) != 1
                    || !Dungeon.level.adjacent(cell, scorpio.pos)
                    || !Dungeon.level.passable[cell]
                    || Actor.findChar(cell) != null
                    || !owner.isMovementSafe(cell)) {
                continue;
            }

            int attackers = owner.countCurrentAttackersAtCell(cell, threats);
            float incoming = owner.estimatedIncomingDptAtCell(cell, threats);
            if (best == -1
                    || attackers < bestAttackers
                    || (attackers == bestAttackers && incoming < bestIncoming - 0.01f)
                    || (attackers == bestAttackers
                        && Math.abs(incoming - bestIncoming) <= 0.01f
                        && cell < best)) {
                best = cell;
                bestAttackers = attackers;
                bestIncoming = incoming;
            }
        }

        return best;
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

    int chooseGreatCrabTacticalCell(Mob targetMob) {
        PathFinder.buildDistanceMap(
                owner.pos, Dungeon.level.passable, GREAT_CRAB_TACTICAL_SEARCH_RADIUS);

        int best = -1;
        int bestScore = Integer.MAX_VALUE;

        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            int pathDistance = PathFinder.distance[cell];
            if (pathDistance == Integer.MAX_VALUE
                    || pathDistance > GREAT_CRAB_TACTICAL_SEARCH_RADIUS
                    || !owner.fieldOfView[cell]
                    || !owner.isKnown(cell)
                    || !Dungeon.level.passable[cell]
                    || !owner.isMovementSafe(cell)) {
                continue;
            }

            Char occupant = Actor.findChar(cell);
            if (occupant != null && occupant != owner) {
                continue;
            }

            int targetDistance = Dungeon.level.distance(cell, targetMob.pos);
            if (targetMob.fieldOfView == null
                    || targetMob.fieldOfView.length != Dungeon.level.length()
                    || targetMob.fieldOfView[cell]
                    || targetDistance < 2
                    || targetDistance > GREAT_CRAB_TACTICAL_SEARCH_RADIUS) {
                continue;
            }

            int frontage = meleeFrontage(cell);
            if (frontage < 2) {
                continue;
            }

            int score = pathDistance * 12
                    + frontage * 40
                    + Math.abs(targetDistance - 3) * 10;
            if (best == -1 || score < bestScore || (score == bestScore && cell < best)) {
                best = cell;
                bestScore = score;
            }
        }

        return best;
    }

    int chooseRangedTargetClosingStep(Mob targetMob, ArrayList<Mob> threats) {
        if (owner.rooted || targetMob == null) {
            return -1;
        }

        boolean[] passable = rangedLurePassable();
        int bestStep = -1;
        int bestScore = Integer.MAX_VALUE;

        for (int offset : PathFinder.NEIGHBOURS8) {
            int destination = targetMob.pos + offset;
            if (!Dungeon.level.insideMap(destination)
                    || Dungeon.level.distance(destination, targetMob.pos) != 1
                    || !passable[destination]
                    || !owner.isMovementSafe(destination)) {
                continue;
            }

            Char occupant = Actor.findChar(destination);
            if (occupant != null && occupant != owner) {
                continue;
            }

            PathFinder.Path route =
                    Dungeon.findPath(owner, destination, passable, owner.fieldOfView, true);
            if (route == null || route.isEmpty()) {
                continue;
            }

            int exposedSteps = 0;
            if (targetMob.fieldOfView != null
                    && targetMob.fieldOfView.length == Dungeon.level.length()) {
                for (int routeCell : route) {
                    if (targetMob.fieldOfView[routeCell]) {
                        exposedSteps++;
                    }
                }
            }

            int attackers = owner.countCurrentAttackersAtCell(destination, threats);
            int score = route.size() * 24
                    + exposedSteps * 18
                    + attackers * 90;

            int firstStep = route.getFirst();
            if (bestStep == -1
                    || score < bestScore
                    || (score == bestScore && firstStep < bestStep)) {
                bestStep = firstStep;
                bestScore = score;
            }
        }

        return bestStep;
    }

    int chooseFriendlyBlockedProjectileStep(
            Mob targetMob, ArrayList<Mob> threats) {
        if (owner.rooted
                || targetMob == null
                || threats == null
                || threats.isEmpty()) {
            return -1;
        }

        int currentAttackers = owner.countCurrentAttackersAtCell(owner.pos, threats);
        float currentIncoming = owner.estimatedIncomingDptAtCell(owner.pos, threats);
        int best = -1;
        int bestAttackers = currentAttackers;
        float bestIncoming = currentIncoming;
        int bestDistance = Dungeon.level.distance(owner.pos, targetMob.pos);

        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = owner.pos + offset;
            if (!Dungeon.level.insideMap(cell)
                    || Dungeon.level.distance(owner.pos, cell) != 1
                    || Dungeon.level.distance(cell, targetMob.pos) <= 1
                    || !Dungeon.level.passable[cell]
                    || !owner.isMovementSafe(cell)
                    || (!owner.fieldOfView[cell] && !owner.isKnown(cell))
                    || Actor.findChar(cell) != null
                    || !hasProjectileLineFrom(cell, targetMob)) {
                continue;
            }

            int attackers = owner.countCurrentAttackersAtCell(cell, threats);
            float incoming = owner.estimatedIncomingDptAtCell(cell, threats);
            int distance = Dungeon.level.distance(cell, targetMob.pos);

            boolean noWorse = attackers < currentAttackers
                    || (attackers == currentAttackers
                        && incoming <= currentIncoming + 0.01f);
            if (!noWorse) {
                continue;
            }

            boolean better = best == -1
                    || attackers < bestAttackers
                    || (attackers == bestAttackers && incoming < bestIncoming - 0.01f)
                    || (attackers == bestAttackers
                        && Math.abs(incoming - bestIncoming) <= 0.01f
                        && distance > bestDistance);
            if (better) {
                best = cell;
                bestAttackers = attackers;
                bestIncoming = incoming;
                bestDistance = distance;
            }
        }

        return best;
    }

    private boolean hasProjectileLineFrom(int sourceCell, Mob targetMob) {
        int livePos = owner.pos;
        try {
            // Ballistica uses Actor.findChar(), so probe from the hypothetical source cell with
            // CoHero temporarily moved there. Otherwise CoHero's live cell can falsely block the
            // candidate line when the sidestep ray crosses its current position.
            owner.pos = sourceCell;
            return new Ballistica(
                    sourceCell, targetMob.pos, Ballistica.PROJECTILE).collisionPos
                    == targetMob.pos;
        } finally {
            owner.pos = livePos;
        }
    }

    int chooseOneStepMeleeApproach(Mob targetMob, ArrayList<Mob> threats) {
        if (owner.rooted || targetMob == null) {
            return -1;
        }

        int best = -1;
        int bestAttackers = Integer.MAX_VALUE;
        float bestIncoming = Float.POSITIVE_INFINITY;
        int bestDistance = Integer.MAX_VALUE;

        for (int offset : PathFinder.NEIGHBOURS8) {
            int cell = owner.pos + offset;
            if (!Dungeon.level.insideMap(cell)
                    || Dungeon.level.distance(owner.pos, cell) != 1
                    || !Dungeon.level.passable[cell]
                    || !owner.isMovementSafe(cell)
                    || (!owner.fieldOfView[cell] && !owner.isKnown(cell))
                    || Actor.findChar(cell) != null
                    || !Dungeon.level.adjacent(cell, targetMob.pos)) {
                continue;
            }

            int attackers = owner.countCurrentAttackersAtCell(cell, threats);
            float incoming = owner.estimatedIncomingDptAtCell(cell, threats);
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
}

