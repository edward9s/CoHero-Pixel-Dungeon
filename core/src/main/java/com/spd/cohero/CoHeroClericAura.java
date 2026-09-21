package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.Game;
import com.watabou.noosa.Group;
import com.watabou.noosa.NoosaScript;
import com.watabou.noosa.NoosaScriptNoLighting;

import java.util.Arrays;

/**
 * Ground overlay for the Cleric cooperation aura.
 *
 * The fill shows the exact current Cleric cooperation area and the brighter
 * 1px outline shows its boundary. It is attached below fog/mobs, so unexplored
 * terrain is not revealed by the overlay.
 */
public final class CoHeroClericAura extends Group {

    private static final int AURA_COLOR = 0xFFFFE3A0;
    private static final float FILL_ALPHA = 0.09f;
    private static final float EDGE_ALPHA = 0.34f;
    private static final float REFRESH_INTERVAL = 0.20f;

    private float refreshDelay;
    private boolean[] renderedArea;

    public static void install(Group levelVisuals) {
        if (levelVisuals == null) {
            throw new IllegalArgumentException("CoHero Cleric aura requires level visuals");
        }
        levelVisuals.add(new CoHeroClericAura());
    }

    @Override
    public void update() {
        super.update();

        refreshDelay -= Game.elapsed;
        if (refreshDelay <= 0f) {
            refreshDelay = REFRESH_INTERVAL;
            refresh();
        }
    }

    private void refresh() {
        CoHeroAlly companion = CoHero.findCompanion();
        if (companion == null
                || !companion.isAlive()
                || CoHero.companionClass() != HeroClass.CLERIC
                || Dungeon.level == null) {
            clearRenderedArea();
            return;
        }

        boolean[] area = CoHeroActivityArea.clericCooperationArea();
        if (area == null) {
            clearRenderedArea();
            return;
        }

        if (!Arrays.equals(renderedArea, area)) {
            rebuild(area);
        }
    }

    private void clearRenderedArea() {
        if (renderedArea != null || length > 0) {
            clear();
            renderedArea = null;
        }
    }

    private void rebuild(boolean[] area) {
        clear();
        renderedArea = area.clone();

        int width = Dungeon.level.width();
        float tile = DungeonTilemap.SIZE;

        for (int cell = 0; cell < area.length; cell++) {
            if (!area[cell]) {
                continue;
            }

            float x = (cell % width) * tile;
            float y = (cell / width) * tile;

            ColorBlock fill = new GlowBlock(tile, tile);
            fill.x = x;
            fill.y = y;
            fill.alpha(FILL_ALPHA);
            add(fill);
        }

        for (int cell = 0; cell < area.length; cell++) {
            if (!area[cell]) {
                continue;
            }

            int col = cell % width;
            int row = cell / width;
            float x = col * tile;
            float y = row * tile;

            if (row == 0 || !area[cell - width]) {
                addEdge(x, y, tile, 1f);
            }
            if (row == Dungeon.level.height() - 1 || !area[cell + width]) {
                addEdge(x, y + tile - 1f, tile, 1f);
            }
            if (col == 0 || !area[cell - 1]) {
                addEdge(x, y, 1f, tile);
            }
            if (col == width - 1 || !area[cell + 1]) {
                addEdge(x + tile - 1f, y, 1f, tile);
            }
        }
    }

    private static final class GlowBlock extends ColorBlock {

        GlowBlock(float width, float height) {
            super(width, height, AURA_COLOR);
        }

        @Override
        protected NoosaScript script() {
            return NoosaScriptNoLighting.get();
        }
    }

    private void addEdge(float x, float y, float width, float height) {
        ColorBlock edge = new GlowBlock(width, height);
        edge.x = x;
        edge.y = y;
        edge.alpha(EDGE_ALPHA);
        add(edge);
    }
}
