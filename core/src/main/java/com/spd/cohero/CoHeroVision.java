package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Light;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.duelist.Challenge;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.effects.particles.FlameParticle;
import com.shatteredpixel.shatteredpixeldungeon.items.Torch;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfMagicMapping;
import com.shatteredpixel.shatteredpixeldungeon.journal.Catalog;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.watabou.noosa.audio.Sample;
import com.watabou.noosa.particles.Emitter;
import com.watabou.utils.Random;

import java.util.ArrayList;

/**
 * Owns CoHero-local perception and visibility refresh behavior.
 */
final class CoHeroVision {

    private final CoHeroAlly owner;

    CoHeroVision(CoHeroAlly owner) {
        this.owner = owner;
    }

    void syncViewDistance() {
        if (Dungeon.level == null) {
            return;
        }

        int baseViewDistance = Dungeon.level.viewDistance;
        owner.viewDistance = owner.buff(Light.class) == null
                ? baseViewDistance
                : Math.max(baseViewDistance, Light.DISTANCE);
    }

    void refreshOwnFieldOfView() {
        Dungeon.level.updateFieldOfView(owner, owner.fieldOfView);
        revealVisibleCells();
    }

    void revealVisibleCells() {
        long started = System.nanoTime();
        boolean newlyVisited = false;
        for (int i = 0; i < owner.fieldOfView.length; i++) {
            if (owner.fieldOfView[i]
                    && Dungeon.level.discoverable[i]
                    && !Dungeon.level.visited[i]) {
                Dungeon.level.visited[i] = true;
                newlyVisited = true;
            }
        }

        // CoHero vision is display-only. Do not alter Hero gameplay visibility.
        // An unchanged visited map needs neither a new fog texture nor a sprite visibility pass.
        if (newlyVisited) {
            GameScene.updateFog(owner.pos, owner.viewDistance + 1);
            GameScene.afterObserve();
        }
        owner.timings().record(owner, CoHeroTimings.Action.VISION, started);
    }

    boolean tryAutoTorch() {
        if (Dungeon.level == null
                || Dungeon.level.viewDistance >= Light.DISTANCE
                || owner.buff(Light.class) != null) {
            return false;
        }

        Torch torch = owner.inventory().takeOneAutoTorch();
        if (torch == null) {
            return false;
        }

        Buff.affect(owner, Light.class, Light.DURATION);
        refreshOwnFieldOfView();
        Catalog.countUse(Torch.class);
        Sample.INSTANCE.play(Assets.Sounds.BURNING);

        CharSprite sprite = owner.attachedSprite();
        if (sprite != null) {
            sprite.operate(owner.pos);
            Emitter emitter = sprite.centerEmitter();
            if (emitter != null) {
                emitter.start(FlameParticle.FACTORY, 0.2f, 3);
            }
        }

        owner.spendActionTime(Torch.TIME_TO_LIGHT);
        return true;
    }

    void passiveSearch() {
        int radius = CoHero.companionClass() == HeroClass.ROGUE ? 2 : 1;
        int width = Dungeon.level.width();
        int height = Dungeon.level.height();
        int centerX = owner.pos % width;
        int centerY = owner.pos / width;
        boolean found = false;

        for (int y = Math.max(0, centerY - radius); y <= Math.min(height - 1, centerY + radius); y++) {
            for (int x = Math.max(0, centerX - radius); x <= Math.min(width - 1, centerX + radius); x++) {
                int cell = x + y * width;
                if (cell == owner.pos
                        || !owner.fieldOfView[cell]
                        || !Dungeon.level.secret[cell]) {
                    continue;
                }

                Trap trap = Dungeon.level.traps.get(cell);
                if (trap != null && !trap.canBeSearched) {
                    continue;
                }

                float chance;
                if (Dungeon.level.map[cell] == Terrain.SECRET_TRAP) {
                    chance = 0.4f - (Dungeon.depth / 250f);
                } else if (Dungeon.level.map[cell] == Terrain.SECRET_DOOR) {
                    chance = 0.2f - (Dungeon.depth / 100f);
                } else {
                    continue;
                }

                if (SPDSettings.intro() || Random.Float() >= chance) {
                    continue;
                }

                int oldValue = Dungeon.level.map[cell];
                GameScene.discoverTile(cell, oldValue);
                Dungeon.level.discover(cell);
                ScrollOfMagicMapping.discover(cell);
                found = true;
            }
        }

        if (found) {
            Sample.INSTANCE.play(Assets.Sounds.SECRET);
        }
    }

    ArrayList<Mob> visibleAwakeEnemies() {
        ArrayList<Mob> result = new ArrayList<>();
        for (Mob mob : Dungeon.level.mobs) {
            if (mob != owner
                    && mob.alignment == Char.Alignment.ENEMY
                    && mob.isAlive()
                    && mob.invisible <= 0
                    && owner.fieldOfView[mob.pos]
                    && mob.state != mob.SLEEPING
                    && mob.buff(Challenge.SpectatorFreeze.class) == null) {
                result.add(mob);
            }
        }
        return result;
    }
}
