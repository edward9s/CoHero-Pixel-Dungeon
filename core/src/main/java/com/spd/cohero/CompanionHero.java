package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.DirectableAlly;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.GhostSprite;
import com.watabou.utils.Random;

import java.util.ArrayList;

/**
 * Autonomous second hero prototype.
 *
 * This first playable version deliberately focuses on movement and failure:
 * the companion explores on its own, heads to a discovered exit, reveals the
 * cells it sees, and ends the run if it dies. Inventory-driven combat is added
 * later rather than being mixed into the movement prototype.
 */
public class CompanionHero extends DirectableAlly {

    {
        spriteClass = GhostSprite.class;

        HT = HP = 20;
        defenseSkill = 5;

        // Until inventory-driven combat exists, this prototype never seeks combat.
        attacksAutomatically = false;

        WANDERING = new Wandering() {
            @Override
            public boolean act(boolean enemyInFOV, boolean justAlerted) {
                enemySeen = false;

                int exit = Dungeon.level.exit();
                boolean exitKnown = isKnown(exit);
                if (exitKnown && target != exit) {
                    target = exit;
                }

                if (target == -1 || target == pos || !Dungeon.level.passable[target]) {
                    target = chooseExplorationTarget();
                }

                int oldPos = pos;
                if (target != -1 && getCloser(target)) {
                    spend(1 / speed());
                    return moveSprite(oldPos, pos);
                }

                target = chooseExplorationTarget();
                spend(TICK);
                return true;
            }

            @Override
            protected int randomDestination() {
                return chooseExplorationTarget();
            }
        };
        state = WANDERING;
    }

    @Override
    protected boolean act() {
        boolean result = super.act();

        // Char.act() calculates FOV before movement. Recalculate here so newly
        // reached cells are immediately revealed by the companion.
        Dungeon.level.updateFieldOfView(this, fieldOfView);
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

        return result;
    }

    @Override
    public void aggro(Char ch) {
        // The prototype has no weapon inventory yet, therefore being attacked
        // must not silently turn it into a normal combat-capable ally.
        enemy = null;
        state = WANDERING;
    }

    @Override
    public void die(Object cause) {
        super.die(cause);
        if (Dungeon.hero != null && Dungeon.hero.isAlive()) {
            // Companion death is an unconditional run loss; bypass Ankh-style
            // resurrection because the dead character is the companion.
            Hero.reallyDie(CompanionHero.class);
        }
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
                    && !Dungeon.level.mapped[cell]) {
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
