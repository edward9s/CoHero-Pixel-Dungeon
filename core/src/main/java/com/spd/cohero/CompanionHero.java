package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.DirectableAlly;
import com.shatteredpixel.shatteredpixeldungeon.messages.Languages;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.utils.Bundle;
import com.watabou.utils.PathFinder;
import com.watabou.utils.Random;

import java.util.ArrayList;

public class CompanionHero extends DirectableAlly {

    private static final String EXPLORATION_TARGET = "cohero_exploration_target";

    private int explorationTarget = -1;

    {
        spriteClass = CompanionHeroSprite.class;
        HT = HP = 20;
        defenseSkill = 5;
        attacksAutomatically = false;
    }

    @Override
    public String name() {
        HeroClass heroClass = CoHero.companionClass();
        return heroClass == null ? "companion hero" : heroClass.title();
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
    }

    @Override
    protected boolean act() {
        if (fieldOfView == null || fieldOfView.length != Dungeon.level.length()) {
            fieldOfView = new boolean[Dungeon.level.length()];
        }
        Dungeon.level.updateFieldOfView(this, fieldOfView);
        revealVisibleCells();

        if (paralysed > 0) {
            spend(TICK);
            return true;
        }

        ArrayList<Mob> visibleThreats = visibleAwakeEnemies();
        if (!visibleThreats.isEmpty()) {
            int escapeStep = chooseEscapeStep(visibleThreats);
            if (escapeStep != -1) {
                int oldPos = pos;
                if (getCloser(escapeStep)) {
                    spend(1 / speed());
                    Dungeon.level.updateFieldOfView(this, fieldOfView);
                    revealVisibleCells();
                    return moveSprite(oldPos, pos);
                }
            }

            // With no combat capability yet, never deliberately walk closer to a visible awake enemy.
            spend(TICK);
            return true;
        }

        int exit = Dungeon.level.exit();
        if (isKnown(exit)) {
            explorationTarget = exit;
        } else if (explorationTarget == -1
                || explorationTarget == pos
                || !Dungeon.level.passable[explorationTarget]
                || !isSleepSafe(explorationTarget)) {
            explorationTarget = chooseExplorationTarget();
        }

        int oldPos = pos;
        if (explorationTarget != -1 && getCloser(explorationTarget)) {
            spend(1 / speed());

            Dungeon.level.updateFieldOfView(this, fieldOfView);
            revealVisibleCells();
            return moveSprite(oldPos, pos);
        }

        explorationTarget = chooseExplorationTarget();
        spend(TICK);
        return true;
    }

    @Override
    public void die(Object cause) {
        super.die(cause);
        if (Dungeon.hero != null && Dungeon.hero.isAlive()) {
            GLog.n(companionDeathMessage(cause));
            Hero.reallyDie(cause);
        }
    }

    private String companionDeathMessage(Object cause) {
        String killer = null;
        if (cause instanceof Char && cause != this) {
            killer = ((Char) cause).name();
        }

        if (Messages.lang() == Languages.CHI_TRAD) {
            return killer == null
                    ? "你的夥伴英雄死亡了。"
                    : killer + "殺死了你的夥伴英雄。";
        }
        if (Messages.lang() == Languages.CHI_SMPL) {
            return killer == null
                    ? "你的伙伴英雄死亡了。"
                    : killer + "杀死了你的伙伴英雄。";
        }
        return killer == null
                ? "Your companion hero has died."
                : "Your companion hero was killed by " + killer + ".";
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

    private int chooseExplorationTarget() {
        int exit = Dungeon.level.exit();
        if (isKnown(exit)) {
            return exit;
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
