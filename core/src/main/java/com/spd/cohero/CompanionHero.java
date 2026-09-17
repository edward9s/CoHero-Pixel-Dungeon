package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.DirectableAlly;
import com.shatteredpixel.shatteredpixeldungeon.sprites.GhostSprite;

/**
 * Autonomous second hero actor.
 *
 * The initial implementation deliberately reuses SPD's existing ally actor
 * model. Equipment-driven combat and exploration behavior will be added here
 * instead of being spread through upstream classes.
 */
public class CompanionHero extends DirectableAlly {

    {
        spriteClass = GhostSprite.class;
        attacksAutomatically = false;
    }
}
