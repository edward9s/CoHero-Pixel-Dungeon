package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.watabou.noosa.Group;
import com.watabou.utils.Callback;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Render-only mirror for actors visible through CoHero FOV but outside Hero FOV.
 *
 * Real actors and their real CharSprites keep stock SPD timing/visibility. Actor-thread code may
 * enqueue presentation hints here, but this class is consumed only from GameScene.update() on the
 * render thread and never mutates gameplay state.
 */
public final class CoHeroRemoteView {

    private enum EventType {
        ATTACK,
        ZAP
    }

    private static final class Event {
        final Mob actor;
        final EventType type;
        final int cell;

        Event(Mob actor, EventType type, int cell) {
            this.actor = actor;
            this.type = type;
            this.cell = cell;
        }
    }

    private static final class Entry {
        final Mob actor;
        final CharSprite sprite;
        final CoHeroHealthIndicator health;
        final ArrayDeque<Event> events = new ArrayDeque<>();
        int visualCell;
        boolean actionBusy;
        long seenGeneration;

        Entry(Mob actor, CharSprite sprite, CoHeroHealthIndicator health) {
            this.actor = actor;
            this.sprite = sprite;
            this.health = health;
            this.visualCell = actor.pos;
        }
    }

    private static final IdentityHashMap<Mob, Entry> entries = new IdentityHashMap<>();
    private static final ConcurrentLinkedQueue<Event> queuedEvents = new ConcurrentLinkedQueue<>();
    private static Mob[] mobSnapshot = new Mob[0];
    private static int mobSnapshotCount;
    private static long updateGeneration;

    private CoHeroRemoteView() {
    }

    public static synchronized void reset() {
        entries.clear();
        queuedEvents.clear();
        mobSnapshot = new Mob[0];
        mobSnapshotCount = 0;
        updateGeneration = 0;
    }

    public static void attack(Mob actor, int cell) {
        enqueue(actor, EventType.ATTACK, cell);
    }

    public static void zap(Mob actor, int cell) {
        enqueue(actor, EventType.ZAP, cell);
    }

    private static void enqueue(Mob actor, EventType type, int cell) {
        if (actor == null) {
            return;
        }
        queuedEvents.add(new Event(actor, type, cell));
    }

    /**
     * Render-thread update. Proxies exist only for CoHero-only visibility.
     */
    public static synchronized void update(Group mobLayer) {
        if (mobLayer == null || Dungeon.level == null || Dungeon.hero == null) {
            return;
        }

        CoHeroAlly companion = CoHero.findCompanion();
        if (companion == null
                || !companion.isAlive()
                || companion.fieldOfView == null
                || companion.fieldOfView.length != Dungeon.level.length()) {
            clearEntries();
            queuedEvents.clear();
            return;
        }

        long generation = ++updateGeneration;
        Mob[] snapshot = Dungeon.level.mobs.toArray(mobSnapshot);
        if (snapshot != mobSnapshot) {
            mobSnapshot = snapshot;
        }

        int snapshotCount = 0;
        while (snapshotCount < snapshot.length && snapshot[snapshotCount] != null) {
            snapshotCount++;
        }
        for (int i = snapshotCount; i < mobSnapshotCount && i < snapshot.length; i++) {
            snapshot[i] = null;
        }
        mobSnapshotCount = snapshotCount;

        for (int i = 0; i < snapshotCount; i++) {
            Mob mob = snapshot[i];
            if (!remoteVisible(mob, companion)) {
                continue;
            }

            Entry entry = entries.get(mob);
            if (entry == null) {
                entry = createEntry(mob, mobLayer);
                entries.put(mob, entry);
            }
            entry.seenGeneration = generation;
            entry.sprite.visible = true;
        }

        for (Event event; (event = queuedEvents.poll()) != null; ) {
            Entry entry = entries.get(event.actor);
            if (entry == null || entry.seenGeneration != generation) {
                continue;
            }
            if (entry.events.size() >= 8) {
                entry.events.removeFirst();
            }
            entry.events.addLast(event);
        }

        boolean changed = false;
        Iterator<Map.Entry<Mob, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Mob, Entry> mapped = iterator.next();
            Mob mob = mapped.getKey();
            Entry entry = mapped.getValue();

            if (entry.seenGeneration != generation) {
                destroyEntry(entry);
                iterator.remove();
                changed = true;
                continue;
            }

            if (entry.actionBusy || entry.sprite.isMoving) {
                continue;
            }

            Event event = entry.events.pollFirst();
            if (event != null) {
                play(entry, event);
                changed = true;
                continue;
            }

            if (entry.visualCell != mob.pos) {
                int from = entry.visualCell;
                int to = mob.pos;
                entry.visualCell = to;
                entry.sprite.move(from, to);
                changed = true;
            } else {
                entry.sprite.place(mob.pos);
            }
        }

        if (changed) {
            GameScene.sortMobSprites();
        }
    }

    private static Entry createEntry(Mob mob, Group mobLayer) {
        CharSprite proxy = mob.sprite();
        proxy.linkVisuals(mob);
        proxy.ch = mob;
        proxy.visibleOutOfFFOV = false;
        proxy.visible = true;
        proxy.place(mob.pos);
        proxy.idle();
        mobLayer.add(proxy);

        CoHeroHealthIndicator health = mob instanceof CoHeroAlly
                ? new CoHeroHealthIndicator(mob, proxy)
                : null;
        return new Entry(mob, proxy, health);
    }

    private static void clearEntries() {
        for (Entry entry : entries.values()) {
            destroyEntry(entry);
        }
        entries.clear();
    }

    private static void destroyEntry(Entry entry) {
        if (entry.health != null) {
            entry.health.killAndErase();
        }
        entry.sprite.killAndErase();
    }

    private static boolean remoteVisible(Mob mob, CoHeroAlly companion) {
        return mob != null
                && mob.isAlive()
                && mob.pos >= 0
                && mob.pos < Dungeon.level.length()
                && !Dungeon.level.heroFOV[mob.pos]
                && companion.fieldOfView[mob.pos];
    }

    private static void play(final Entry entry, Event event) {
        entry.actionBusy = true;
        Callback done = new Callback() {
            @Override
            public void call() {
                synchronized (CoHeroRemoteView.class) {
                    entry.actionBusy = false;
                    if (entry.actor.pos >= 0) {
                        entry.sprite.place(entry.actor.pos);
                        entry.visualCell = entry.actor.pos;
                    }
                }
            }
        };

        if (event.type == EventType.ZAP) {
            entry.sprite.zap(event.cell, done);
        } else {
            entry.sprite.attack(event.cell, done);
        }
    }
}
