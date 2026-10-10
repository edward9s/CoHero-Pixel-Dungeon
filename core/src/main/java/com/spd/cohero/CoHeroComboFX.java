package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.effects.CellEmitter;
import com.shatteredpixel.shatteredpixeldungeon.effects.Flare;
import com.shatteredpixel.shatteredpixeldungeon.effects.MagicMissile;
import com.shatteredpixel.shatteredpixeldungeon.effects.Speck;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.BlastParticle;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.SmokeParticle;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap;
import com.watabou.noosa.Game;
import com.watabou.noosa.Visual;
import com.watabou.noosa.audio.Sample;
import com.watabou.utils.PathFinder;

/**
 * Presentation only. Six Hero motifs and six CoHero accents compose all 36 ultimates.
 *
 * All effects are fire-and-forget: no callback advances an Actor or owns gameplay state.
 * Never create world particles at cells outside the player's gameplay FOV. In particular,
 * companion-only remote vision must not make hidden enemies visually apparent.
 */
public final class CoHeroComboFX {

    private static final float CHARGE_DURATION = 0.22f;
    private static final float IMPACT_DURATION = 0.36f;
    private static final float MAIN_START = 0.12f;
    private static final float PARTNER_START = 0.30f;
    private static final float TOTAL_DURATION = 0.58f;

    private CoHeroComboFX() {
    }

    /**
     * Called once by a confirmed cast before applying its immediate gameplay effects.
     * @param center offensive target cell, or Hero cell for a support ultimate
     */
    public static void play(Hero hero, CoHeroAlly companion, int center,
                            int heroClass, int companionClass) {
        if (hero == null || companion == null
                || heroClass < 0 || heroClass >= 6
                || companionClass < 0 || companionClass >= 6
                || Dungeon.level == null
                || center < 0 || center >= Dungeon.level.length()) {
            throw new IllegalArgumentException("Invalid combo FX context");
        }

        // The two brief caster flashes are independent of gameplay/action scheduling.
        casterFlare(hero, heroColor(heroClass), CHARGE_DURATION);
        casterFlare(companion, partnerColor(companionClass), CHARGE_DURATION);

        // A short-lived Noosa visual drives the timing, not an Actor or a game turn.
        // Releasing / switching scenes destroys it without touching combat resolution.
        if (hero.sprite != null && hero.sprite.parent != null) {
            hero.sprite.parent.add(new Cue(hero, companion, center, heroClass, companionClass));
        }
    }

    private static final class Cue extends Visual {

        private final Hero hero;
        private final CoHeroAlly companion;
        private final Level level;
        private final int center;
        private final int heroClass;
        private final int companionClass;
        private float elapsed;
        private int stage;

        Cue(Hero hero, CoHeroAlly companion, int center, int heroClass, int companionClass) {
            super(0, 0, 0, 0);
            this.hero = hero;
            this.companion = companion;
            this.level = Dungeon.level;
            this.center = center;
            this.heroClass = heroClass;
            this.companionClass = companionClass;
        }

        @Override
        public void update() {
            super.update();
            if (Dungeon.level != level || Dungeon.hero != hero) {
                killAndErase();
                return;
            }
            elapsed += Game.elapsed;
            if (stage == 0 && elapsed >= MAIN_START) {
                stage = 1;
                primary(hero, companion, center, heroClass);
                Sample.INSTANCE.play(primarySound(heroClass), 0.85f);
            }
            if (stage == 1 && elapsed >= PARTNER_START) {
                stage = 2;
                accent(hero, companion, center, heroClass, companionClass);
            }
            if (elapsed >= TOTAL_DURATION) {
                killAndErase();
            }
        }
    }

    private static void primary(Hero hero, CoHeroAlly companion, int cell, int kind) {
        switch (kind) {
            case 0: // Warrior: crushing shockwave and flying fragments.
                impact(cell, 0xFFAA66, 6, 18);
                particles(cell, Speck.ROCK, 6);
                ring(cell, Speck.DUST, 1);
                break;
            case 1: // Mage: a magical projectile detonates with stock bomb particles.
                missile(hero, companion, cell, MagicMissile.MAGIC_MISSILE);
                arcaneExplosion(cell, true);
                break;
            case 2: // Rogue: shadow dash and a sharp, brief hit.
                missile(hero, companion, cell, MagicMissile.SHADOW);
                impact(cell, 0x9976CB, 4, 16);
                particles(cell, Speck.SMOKE, 5);
                break;
            case 3: // Huntress: precise luminous shot and scattered star trails.
                missile(hero, companion, cell, MagicMissile.LIGHT_MISSILE);
                particles(cell, Speck.STAR, 9);
                impact(cell, 0x88DDAA, 5, 16);
                break;
            case 4: // Duelist: two crossing flashes with sparks.
                impact(cell, 0xFFF3CF, 4, 20);
                impact(cell, 0xA9E7FF, 3, 14);
                particles(cell, Speck.STAR, 6);
                break;
            case 5: // Cleric: twin sanctuary halos and rising restorative lights.
                impact(hero.pos, 0xFFE7A0, 8, 22);
                impact(companion.pos, 0xFFE7A0, 8, 22);
                particles(hero.pos, Speck.HEALING, 5);
                particles(companion.pos, Speck.HEALING, 5);
                break;
            default:
                throw new IllegalArgumentException("Unknown Hero combo motif: " + kind);
        }
    }

    private static void accent(Hero hero, CoHeroAlly companion, int cell,
                               int heroClass, int kind) {
        switch (kind) {
            case 0: // Warrior: armor-strengthening metallic shield glow.
                casterFlare(hero, 0xC3D1DA, IMPACT_DURATION);
                casterFlare(companion, 0xC3D1DA, IMPACT_DURATION);
                break;
            case 1: // Mage: smaller explosion; Cleric-led support stays luminous.
                if (heroClass == 5) {
                    impact(cell, 0xA58BFF, 5, 15);
                    particles(cell, Speck.BLUE_LIGHT, 4);
                } else {
                    arcaneExplosion(cell, false);
                    // A Mage-led combo has already played the full blast.
                    if (heroClass != 1) {
                        Sample.INSTANCE.play(Assets.Sounds.BLAST, 0.45f);
                    }
                }
                break;
            case 2: // Rogue: shadow slashes over the target, or nearby ally for support.
                impact(cell, 0x9B80B6, 3, 14);
                particles(cell, Speck.SMOKE, 5);
                break;
            case 3: // Huntress: a second projectile trace and a marker.
                if (heroClass != 5) {
                    missile(companion, hero, cell, MagicMissile.LIGHT_MISSILE);
                }
                particles(cell, Speck.STAR, 4);
                break;
            case 4: // Duelist: crossing sword flashes.
                impact(cell, 0xE2ECFF, 4, 17);
                particles(cell, Speck.STAR, 5);
                break;
            case 5: // Cleric: both allies glow with healing or protective light.
                casterFlare(hero, 0xFFE3A2, IMPACT_DURATION);
                casterFlare(companion, 0xFFE3A2, IMPACT_DURATION);
                particles(hero.pos, Speck.HEALING, 4);
                particles(companion.pos, Speck.HEALING, 4);
                break;
            default:
                throw new IllegalArgumentException("Unknown CoHero combo accent: " + kind);
        }
    }

    /**
     * Visual-only bomb-style detonation using the stock SPD blast and smoke
     * particles. A large Mage primary burst has peripheral smoke; the companion
     * accent is intentionally smaller. Never triggers a gameplay bomb, damage, or terrain changes.
     */
    private static void arcaneExplosion(int cell, boolean major) {
        if (!visible(cell)) {
            return;
        }

        impact(cell, major ? 0x7AB6FF : 0xA58BFF, major ? 10 : 5, major ? 23 : 15);
        CellEmitter.center(cell).burst(BlastParticle.FACTORY, major ? 20 : 7);
        CellEmitter.center(cell).burst(Speck.factory(Speck.BLUE_LIGHT), major ? 5 : 3);
        CellEmitter.center(cell).burst(SmokeParticle.FACTORY, major ? 3 : 1);

        if (major) {
            // A few outward smoke puffs resemble a bomb without filling the room.
            // Never draw into unseen or wall-blocked cells.
            for (int offset : PathFinder.NEIGHBOURS8) {
                int neighbor = cell + offset;
                if (visible(neighbor) && !Dungeon.level.solid[neighbor]
                        && Dungeon.level.distance(cell, neighbor) == 1
                        && new Ballistica(cell, neighbor,
                                Ballistica.STOP_TARGET | Ballistica.STOP_SOLID)
                                .collisionPos == neighbor) {
                    CellEmitter.get(neighbor).burst(SmokeParticle.FACTORY, 1);
                }
            }
        }
    }

    private static void casterFlare(Char actor, int color, float duration) {
        if (visible(actor.pos) && actor.sprite != null
                && actor.sprite.visible && actor.sprite.parent != null) {
            new Flare(5, 12).color(color, true).show(actor.sprite, duration);
        }
    }

    private static void impact(int cell, int color, int rays, int radius) {
        if (visible(cell) && Dungeon.hero.sprite != null
                && Dungeon.hero.sprite.parent != null) {
            new Flare(rays, radius).color(color, true).show(
                    Dungeon.hero.sprite.parent, DungeonTilemap.tileCenterToWorld(cell),
                    IMPACT_DURATION);
        }
    }

    private static void particles(int cell, int type, int count) {
        if (visible(cell)) {
            CellEmitter.center(cell).burst(Speck.factory(type), count);
        }
    }

    private static void ring(int cell, int type, int radius) {
        // At most eight peripheral bursts; not a full-area particle flood.
        int[] offsets = PathFinder.NEIGHBOURS8;
        int stride = radius == 1 ? 1 : radius;
        for (int offset : offsets) {
            int neighbor = cell + offset * stride;
            if (visible(neighbor) && !Dungeon.level.solid[neighbor]
                    && Dungeon.level.distance(cell, neighbor) <= radius) {
                CellEmitter.center(neighbor).burst(Speck.factory(type), 2);
            }
        }
    }

    private static void missile(Char preferred, Char alternate, int cell, int type) {
        Char source = clearVisibleShot(preferred, cell) ? preferred
                : clearVisibleShot(alternate, cell) ? alternate : null;
        if (source != null) {
            // Use the explicit cell endpoint: a lethal combo can remove the enemy
            // before this render cue runs, leaving the target sprite unavailable.
            ((MagicMissile) source.sprite.parent.recycle(MagicMissile.class))
                    .reset(type, source.sprite, cell, null);
        }
    }

    private static boolean clearVisibleShot(Char actor, int cell) {
        return visible(cell) && visible(actor.pos)
                && actor.sprite != null && actor.sprite.visible
                && actor.sprite.parent != null
                && new Ballistica(actor.pos, cell, Ballistica.PROJECTILE).collisionPos == cell;
    }

    private static boolean visible(int cell) {
        return cell >= 0 && cell < Dungeon.level.length() && Dungeon.level.heroFOV[cell];
    }

    private static int heroColor(int kind) {
        switch (kind) {
            case 0: return 0xFFAA66;
            case 1: return 0x66AAFF;
            case 2: return 0x9D80CB;
            case 3: return 0x88DDAA;
            case 4: return 0xECF2FF;
            case 5: return 0xFFE09A;
            default: throw new IllegalArgumentException("Unknown Hero combo color");
        }
    }

    private static int partnerColor(int kind) {
        switch (kind) {
            case 0: return 0xC3D1DA;
            case 1: return 0xA58BFF;
            case 2: return 0x8A72AC;
            case 3: return 0x9FECB4;
            case 4: return 0xE2ECFF;
            case 5: return 0xFFE3A2;
            default: throw new IllegalArgumentException("Unknown CoHero combo color");
        }
    }

    private static String primarySound(int kind) {
        switch (kind) {
            case 0: return Assets.Sounds.HIT_CRUSH;
            case 1: return Assets.Sounds.BLAST;
            case 2: return Assets.Sounds.HIT_STAB;
            case 3: return Assets.Sounds.HIT_ARROW;
            case 4: return Assets.Sounds.HIT_SLASH;
            case 5: return Assets.Sounds.EVOKE;
            default: throw new IllegalArgumentException("Unknown combo sound");
        }
    }
}
