package com.spd.cohero;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.Game;
import com.watabou.noosa.Group;

/**
 * Per-cell visual grid for the Cleric aura.
 *
 * Every cell within Level.distance() <= 8 from the Cleric CoHero is outlined
 * with a very thin translucent grey frame. Shared edges are drawn once so
 * interior grid lines do not become darker than the outer edge.
 */
public final class CoHeroClericRangeGrid extends Group {

    private static final int GRID_COLOR = 0xFFD0D0D0;
    private static final float GRID_ALPHA = 0.14f;
    private static final float LINE_WIDTH = 0.5f;
    private static final float REFRESH_INTERVAL = 0.20f;

    private float refreshDelay;
    private int renderedCenter = -1;
    private int renderedLevelLength = -1;

    public static void install(Group levelVisuals) {
        if (levelVisuals == null) {
            throw new IllegalArgumentException("CoHero Cleric range grid requires level visuals");
        }
        levelVisuals.add(new CoHeroClericRangeGrid());
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
                || Dungeon.level == null
                || companion.pos < 0
                || companion.pos >= Dungeon.level.length()) {
            clearRenderedRange();
            return;
        }

        if (renderedCenter != companion.pos
                || renderedLevelLength != Dungeon.level.length()) {
            rebuild(companion.pos);
        }
    }

    private void clearRenderedRange() {
        if (renderedCenter != -1 || length > 0) {
            clear();
            renderedCenter = -1;
            renderedLevelLength = -1;
        }
    }

    private void rebuild(int center) {
        clear();
        renderedCenter = center;
        renderedLevelLength = Dungeon.level.length();

        int mapWidth = Dungeon.level.width();
        int mapHeight = Dungeon.level.height();
        int centerX = center % mapWidth;
        int centerY = center / mapWidth;
        int range = CoHeroClassTraits.CLERIC_AURA_RANGE;
        float tile = DungeonTilemap.SIZE;

        int minX = Math.max(0, centerX - range);
        int maxX = Math.min(mapWidth - 1, centerX + range);
        int minY = Math.max(0, centerY - range);
        int maxY = Math.min(mapHeight - 1, centerY + range);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                int cell = x + y * mapWidth;
                if (Dungeon.level.distance(center, cell) > range) {
                    continue;
                }

                float worldX = x * tile;
                float worldY = y * tile;

                // Draw top and left for every cell. Bottom/right are only drawn
                // at the outer edge, so shared lines never stack alpha twice.
                addLine(worldX, worldY, tile, LINE_WIDTH);
                addLine(worldX, worldY, LINE_WIDTH, tile);

                if (y == maxY
                        || Dungeon.level.distance(center, cell + mapWidth) > range) {
                    addLine(worldX, worldY + tile - LINE_WIDTH, tile, LINE_WIDTH);
                }
                if (x == maxX
                        || Dungeon.level.distance(center, cell + 1) > range) {
                    addLine(worldX + tile - LINE_WIDTH, worldY, LINE_WIDTH, tile);
                }
            }
        }
    }

    private void addLine(float x, float y, float width, float height) {
        ColorBlock line = new ColorBlock(width, height, GRID_COLOR);
        line.x = x;
        line.y = y;
        line.alpha(GRID_ALPHA);
        add(line);
    }
}
