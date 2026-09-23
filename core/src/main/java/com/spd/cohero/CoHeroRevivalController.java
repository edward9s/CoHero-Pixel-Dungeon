package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.Statistics;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Invulnerability;
import com.shatteredpixel.shatteredpixeldungeon.effects.SpellSprite;
import com.shatteredpixel.shatteredpixeldungeon.items.Ankh;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfTeleportation;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.levels.features.Chasm;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.watabou.noosa.audio.Sample;
import com.watabou.utils.Random;

import java.util.ArrayList;

final class CoHeroRevivalController {

    private final CoHeroAlly owner;

    CoHeroRevivalController(CoHeroAlly owner) {
        this.owner = owner;
    }

    boolean tryRevive(Object cause) {
        Ankh ankh = owner.inventory().takeAnkhForRevive();
        if (ankh == null) {
            return false;
        }

        boolean fellIntoChasm = cause == Chasm.class;
        int destination = -1;

        // Ordinary Ankhs already relocate CoHero. A blessed Ankh normally revives in place, but
        // reviving in place on a pit would immediately leave CoHero in an invalid lethal cell.
        if (!ankh.isBlessed() || fellIntoChasm) {
            destination = chooseReviveCell(true);
            if (destination == -1) {
                destination = chooseReviveCell(false);
            }
            if (fellIntoChasm && destination == -1) {
                return false;
            }
        }

        owner.HP = owner.HT;
        owner.clearLowHealthRally();

        Statistics.ankhsUsed++;
        Catalog.countUse(Ankh.class);
        SpellSprite.show(owner, SpellSprite.ANKH);
        GameScene.flash(0x80FFFF40);

        if (ankh.isBlessed()) {
            Buff.prolong(owner, Invulnerability.class, 15f);
        }

        if (destination != -1) {
            owner.resetNavigationAfterAnkhTeleport();
            ScrollOfTeleportation.appear(owner, destination);
            Dungeon.level.occupyCell(owner);
            Dungeon.level.updateFieldOfView(owner, owner.fieldOfView);
            owner.revealVisibleCells();
        } else {
            // Blessed Ankh deaths that did not involve a chasm keep the stock revive-in-place
            // behavior. An ordinary Ankh only reaches this fallback on a pathological full level.
            Sample.INSTANCE.play(Assets.Sounds.TELEPORT);
        }

        return true;
    }

    String deathMessage(Object cause) {
        if (cause instanceof Char && cause != owner) {
            return CoHeroMessages.get("companion.killed_by", ((Char) cause).name());
        }
        return CoHeroMessages.get("companion.died");
    }

    private int chooseReviveCell(boolean strictSafety) {
        ArrayList<Integer> destinations = new ArrayList<>();
        for (int cell = 0; cell < Dungeon.level.length(); cell++) {
            if (!Dungeon.level.passable[cell]
                    || Dungeon.level.pit[cell]
                    || Dungeon.level.secret[cell]
                    || Actor.findChar(cell) != null) {
                continue;
            }
            if (strictSafety && !owner.isMovementSafe(cell)) {
                continue;
            }
            destinations.add(cell);
        }
        return destinations.isEmpty() ? -1 : Random.element(destinations);
    }
}
