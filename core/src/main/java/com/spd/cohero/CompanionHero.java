package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.LevelTransition;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.utils.Bundle;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;

import java.util.ArrayList;

/**
 * A real SPD Hero controlled by CoHero AI rather than player input.
 *
 * Keeping the companion inside the Hero type is intentional: inventory, equipment, potion,
 * scroll, wand, EXP and other item semantics should come from SPD itself instead of a parallel
 * compatibility layer. Only movement/decision-making is custom.
 */
public class CompanionHero extends Hero {

    private static final String EXPLORATION_TARGET = "cohero_exploration_target";

    private int explorationTarget = -1;

    public CompanionHero() {
        super();
        alignment = Alignment.ALLY;
        damageInterrupt = false;
    }

    /**
     * Strength has one authority for the run. The companion's raw Hero.STR field is deliberately
     * ignored; permanent strength gains are routed to Dungeon.hero by the CoHero strength seam.
     */
    @Override
    public int STR() {
        if (Dungeon.hero == null) {
            throw new IllegalStateException("CoHero STR requested without Dungeon.hero");
        }
        return Dungeon.hero.STR();
    }

    /**
     * Hunger is intentionally not part of CoHero yet.
     */
    @Override
    public boolean isStarving() {
        return false;
    }

    @Override
    public void storeInBundle(Bundle bundle) {
        super.storeInBundle(bundle);
        bundle.put(EXPLORATION_TARGET, explorationTarget);
    }

    @Override
    public void restoreFromBundle(Bundle bundle) {
        super.restoreFromBundle(bundle);
        explorationTarget = bundle.contains(EXPLORATION_TARGET)
                ? bundle.getInt(EXPLORATION_TARGET)
                : -1;
    }

    void enterLevel(int cell) {
        if (!isAlive()) {
            throw new IllegalStateException("Cannot move a dead CoHero companion to a new level");
        }

        pos = cell;
        explorationTarget = -1;
        curAction = null;
        lastAction = null;
        ready = false;
        resting = false;
        path = null;
        timeToNow();

        if (fieldOfView == null || fieldOfView.length != Dungeon.level.length()) {
            fieldOfView = new boolean[Dungeon.level.length()];
        }

        Buff.affect(this, CompanionRegeneration.class);
    }

    @Override
    public boolean act() {
        if (fieldOfView == null || fieldOfView.length != Dungeon.level.length()) {
            fieldOfView = new boolean[Dungeon.level.length()];
        }
        Dungeon.level.updateFieldOfView(this, fieldOfView);
        revealVisibleCells();

        if (CoHero.tryAutoExit(this)) {
            return true;
        }

        if (paralysed > 0) {
            spend(TICK);
            return true;
        }

        ArrayList<Mob> visibleThreats = visibleAwakeEnemies();
        if (!visibleThreats.isEmpty()) {
            int escapeStep = chooseEscapeStep(visibleThreats);
            if (escapeStep != -1 && moveToward(escapeStep)) {
                return true;
            }

            // Combat AI is deliberately separate. Until it is implemented, a visible awake enemy
            // never causes the companion to intentionally move closer.
            spend(TICK);
            return true;
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

        if (explorationTarget != -1 && moveToward(explorationTarget)) {
            return true;
        }

        explorationTarget = exitTransition != null
                ? chooseExitWaitingCell(exitTransition)
                : chooseExplorationTarget();
        spend(TICK);
        return true;
    }

    private boolean moveToward(int targetCell) {
        if (rooted || targetCell == pos || !Dungeon.level.insideMap(targetCell)) {
            return false;
        }

        int step = Dungeon.findStep(this, targetCell, Dungeon.level.passable, fieldOfView, true);
        if (step == -1) {
            return false;
        }

        int oldPos = pos;
        move(step);
        if (pos == oldPos) {
            return false;
        }

        spend(1f / speed());

        // Char.move handles Vertigo animation itself. Normal movement still needs its sprite tween.
        if (sprite != null && !sprite.isMoving) {
            sprite.move(oldPos, pos);
        }

        Dungeon.level.updateFieldOfView(this, fieldOfView);
        revealVisibleCells();
        CoHero.tryAutoExit(this);
        return true;
    }

    @Override
    public void die(Object cause) {
        // Do not use Hero.die(): a companion Ankh must not turn companion death into a separate
        // resurrection flow. The CoHero contract is still "either hero dies, the run ends".
        curAction = null;
        HP = 0;
        destroy();
        if (sprite != null) {
            sprite.die();
        }

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
            if (mob.alignment == Alignment.ENEMY
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
            if (mob.alignment == Alignment.ENEMY
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
