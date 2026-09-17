package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.DirectableAlly;
import com.shatteredpixel.shatteredpixeldungeon.messages.Languages;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.utils.Random;

import java.util.ArrayList;

public class CompanionHero extends DirectableAlly {

    private int explorationTarget = -1;

    {
        spriteClass = CompanionHeroSprite.class;
        HT = HP = 20;
        defenseSkill = 5;
        attacksAutomatically = false;
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

        int exit = Dungeon.level.exit();
        if (isKnown(exit)) {
            explorationTarget = exit;
        } else if (explorationTarget == -1
                || explorationTarget == pos
                || !Dungeon.level.passable[explorationTarget]) {
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
