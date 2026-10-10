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
import com.watabou.utils.Callback;
import com.watabou.utils.PathFinder;

/**
 * Presentation only. Six Hero motifs and six CoHero accents compose all 36 ultimates.
 *
 * The Hero's turn remains busy until the real projectiles arrive and the joint
 * hit cue resolves once. Render callbacks never create a game Actor or expose
 * enemies outside the Hero's gameplay field of view.
 */
public final class CoHeroComboFX {

    private static final float CHARGE_DURATION = 0.22f;
    private static final float IMPACT_DURATION = 0.36f;
    private static final float MAIN_START = 0.12f;
    private static final float PARTNER_START = 0.30f;
    private static final float HIT_MIN_START = 0.62f;
    private static final float MAX_PROJECTILE_WAIT = 1.80f;
    private static final float HIT_TAIL = 0.18f;

    private CoHeroComboFX() {
    }

    /**
     * Start the telegraph. Gameplay resolves once after both outgoing missiles
     * reach the target (and no earlier than the joint wind-up). A bounded wait
     * prevents a missing render callback from leaving the Hero busy indefinitely.
     * With no drawable scene, resolve immediately.
     *
     * @param center offensive target cell, or Hero cell for a support ultimate
     */
    public static void play(Hero hero, CoHeroAlly companion, int center,
                            int heroClass, int companionClass, Runnable onImpact) {
        if (hero == null || companion == null || onImpact == null
                || heroClass < 0 || heroClass >= 6
                || companionClass < 0 || companionClass >= 6
                || Dungeon.level == null
                || center < 0 || center >= Dungeon.level.length()) {
            throw new IllegalArgumentException("Invalid combo FX context");
        }

        // The two brief caster flashes are independent of gameplay/action scheduling.
        casterFlare(hero, heroColor(heroClass), CHARGE_DURATION);
        casterFlare(companion, partnerColor(companionClass), CHARGE_DURATION);

        if (hero.sprite != null && hero.sprite.parent != null) {
            hero.sprite.parent.add(new Cue(
                    hero, companion, center, heroClass, companionClass, onImpact));
        } else {
            // No scene to animate: finish the already-confirmed action now.
            onImpact.run();
        }
    }

    private static final class Cue extends Visual {

        private final Hero hero;
        private final CoHeroAlly companion;
        private final Level level;
        private final int center;
        private final int heroClass;
        private final int companionClass;
        private final Runnable onImpact;
        private float elapsed;
        private float impactTime;
        private int pendingMissiles;
        private int stage;

        Cue(Hero hero, CoHeroAlly companion, int center, int heroClass,
            int companionClass, Runnable onImpact) {
            super(0, 0, 0, 0);
            this.hero = hero;
            this.companion = companion;
            this.level = Dungeon.level;
            this.center = center;
            this.heroClass = heroClass;
            this.companionClass = companionClass;
            this.onImpact = onImpact;
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
                primary(hero, companion, center, heroClass, this);
                // Mage's explosion sound belongs at the actual hit, not launch.
                if (heroClass != 1) {
                    Sample.INSTANCE.play(primarySound(heroClass), 0.85f);
                }
            }
            if (stage == 1 && elapsed >= PARTNER_START) {
                stage = 2;
                accent(hero, companion, center, heroClass, companionClass, this);
            }
            if (stage == 2 && elapsed >= HIT_MIN_START
                    && (pendingMissiles == 0 || elapsed >= MAX_PROJECTILE_WAIT)) {
                // Commit this stage before gameplay can kill sprites, change
                // the level, or advance the Actor schedule.
                stage = 3;
                impactTime = elapsed;
                jointImpact(center, heroClass, companionClass);
                onImpact.run();
            }
            if (stage == 3 && elapsed >= impactTime + HIT_TAIL) {
                killAndErase();
            }
        }

        private void missileArrived() {
            // Callbacks may outlive this cue if the scene was replaced.
            if (stage >= 3) {
                return;
            }
            if (pendingMissiles <= 0) {
                throw new IllegalStateException("Unexpected combo missile callback");
            }
            pendingMissiles--;
        }
    }

    private static void primary(Hero hero, CoHeroAlly companion, int cell, int kind,
                                Cue cue) {
        switch (kind) {
            case 0: // Warrior: crushing shockwave and flying fragments.
                impact(cell, 0xFFAA66, 6, 18);
                particles(cell, Speck.ROCK, 6);
                ring(cell, Speck.DUST, 1);
                break;
            case 1: // Mage: projectile first; actual explosion occurs on arrival.
                missile(hero, companion, cell, MagicMissile.MAGIC_MISSILE, cue);
                break;
            case 2: // Rogue: shadow dash and a sharp, brief hit.
                missile(hero, companion, cell, MagicMissile.SHADOW, cue);
                impact(cell, 0x9976CB, 4, 16);
                particles(cell, Speck.SMOKE, 5);
                break;
            case 3: // Huntress: precise luminous shot and scattered star trails.
                missile(hero, companion, cell, MagicMissile.LIGHT_MISSILE, cue);
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
                               int heroClass, int kind, Cue cue) {
        switch (kind) {
            case 0: // Warrior: armor-strengthening metallic shield glow.
                casterFlare(hero, 0xC3D1DA, IMPACT_DURATION);
                casterFlare(companion, 0xC3D1DA, IMPACT_DURATION);
                break;
            case 1: // Mage: smaller explosion; Cleric-led support stays luminous.
                if (heroClass == 5) {
                    impact(cell, 0xA58BFF, 5, 15);
                    particles(cell, Speck.BLUE_LIGHT, 4);
                }
                // Offensive Mage blast and sound wait for the joint hit.
                break;
            case 2: // Rogue: shadow slashes over the target, or nearby ally for support.
                impact(cell, 0x9B80B6, 3, 14);
                particles(cell, Speck.SMOKE, 5);
                break;
            case 3: // Huntress: a second projectile trace and a marker.
                if (heroClass != 5) {
                    missile(companion, hero, cell, MagicMissile.LIGHT_MISSILE, cue);
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

    /** Last visible strike, immediately followed by its actual combat damage. */
    private static void jointImpact(int cell, int heroClass, int companionClass) {
        if (heroClass == 1) {
            arcaneExplosion(cell, true);
            Sample.INSTANCE.play(Assets.Sounds.BLAST, 0.85f);
        } else if (heroClass != 5) {
            impact(cell, 0xE6F0FF, 5, 13);
        }
        if (companionClass == 1 && heroClass != 5) {
            arcaneExplosion(cell, false);
            if (heroClass != 1) {
                Sample.INSTANCE.play(Assets.Sounds.BLAST, 0.45f);
            }
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

    private static void missile(Char preferred, Char alternate, int cell,
                                int type, Cue cue) {
        Char source = clearVisibleShot(preferred, cell) ? preferred
                : clearVisibleShot(alternate, cell) ? alternate : null;
        if (source != null) {
            // Wait for the actual stock SPD missile callback; fixed wall-clock
            // timing can kill an enemy before a long-range projectile arrives.
            cue.pendingMissiles++;
            ((MagicMissile) source.sprite.parent.recycle(MagicMissile.class))
                    .reset(type, source.sprite, cell, new Callback() {
                        @Override
                        public void call() {
                            cue.missileArrived();
                        }
                    });
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
