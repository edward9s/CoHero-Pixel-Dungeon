package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.Game;
import com.watabou.noosa.Group;

import java.util.Arrays;

/**
 * Thin, transparent boundary marker for the Cleric cooperation area.
 *
 * This deliberately uses ColorBlock's normal NoosaScript path: Visual.alpha()
 * must remain effective. Do not switch these blocks to NoosaScriptNoLighting,
 * whose shader ignores Noosa lighting/alpha values entirely.
 *
 * Only the actual boundary is drawn. Interior floor tiles are never covered.
 */
public final class CoHeroClericBoundary extends Group {

    private static final int AURA_COLOR = 0xFFFFF0B8;
    private static final float HALO_ALPHA = 0.06f;
    private static final float EDGE_ALPHA = 0.20f;
    private static final float HALO_WIDTH = 2f;
    private static final float EDGE_WIDTH = 1f;
    private static final float REFRESH_INTERVAL = 0.20f;

    private float refreshDelay;
    private boolean[] renderedArea;

    public static void install(Group levelVisuals) {
        if (levelVisuals == null) {
            throw new IllegalArgumentException("CoHero Cleric boundary requires level visuals");
        }
        levelVisuals.add(new CoHeroClericBoundary());
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

        int mapWidth = Dungeon.level.width();
        int mapHeight = Dungeon.level.height();
        float tile = DungeonTilemap.SIZE;

        for (int cell = 0; cell < area.length; cell++) {
            if (!area[cell]) {
                continue;
            }

            int col = cell % mapWidth;
            int row = cell / mapWidth;
            float x = col * tile;
            float y = row * tile;

            boolean top = row == 0 || !area[cell - mapWidth];
            boolean bottom = row == mapHeight - 1 || !area[cell + mapWidth];
            boolean left = col == 0 || !area[cell - 1];
            boolean right = col == mapWidth - 1 || !area[cell + 1];

            if (top) {
                addHorizontalBoundary(x, y, tile, false);
            }
            if (bottom) {
                addHorizontalBoundary(x, y + tile, tile, true);
            }
            if (left) {
                addVerticalBoundary(x, y, tile, false);
            }
            if (right) {
                addVerticalBoundary(x + tile, y, tile, true);
            }
        }
    }

    private void addHorizontalBoundary(float x, float boundaryY, float length, boolean bottom) {
        float haloY = bottom ? boundaryY - HALO_WIDTH : boundaryY;
        addBlock(x, haloY, length, HALO_WIDTH, HALO_ALPHA);

        float edgeY = bottom ? boundaryY - EDGE_WIDTH : boundaryY;
        addBlock(x, edgeY, length, EDGE_WIDTH, EDGE_ALPHA);
    }

    private void addVerticalBoundary(float boundaryX, float y, float length, boolean right) {
        float haloX = right ? boundaryX - HALO_WIDTH : boundaryX;
        addBlock(haloX, y, HALO_WIDTH, length, HALO_ALPHA);

        float edgeX = right ? boundaryX - EDGE_WIDTH : boundaryX;
        addBlock(edgeX, y, EDGE_WIDTH, length, EDGE_ALPHA);
    }

    private void addBlock(float x, float y, float width, float height, float alpha) {
        ColorBlock block = new ColorBlock(width, height, AURA_COLOR);
        block.x = x;
        block.y = y;
        block.alpha(alpha);
        add(block);
    }
}
