#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_gamescene.py <GameScene.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

ready_marker = "\t\tcom.spd.cohero.CoHero.onGameSceneReady();"
locator_marker = "\t\tcom.spd.cohero.CoHeroLocator coHeroLocator = new com.spd.cohero.CoHeroLocator("
inventory_tag_field_marker = "\tprivate com.spd.cohero.CoHeroInventoryIndicator coHeroInventory;"
inventory_tag_create_marker = "\t\tcoHeroInventory = new com.spd.cohero.CoHeroInventoryIndicator();"
inventory_tag_state_marker = "\tprivate boolean tagCoHeroInventory = false;"
hazard_marker = "\t\tcom.spd.cohero.CoHeroHazards.warn(pos, delay);"

if (ready_marker in text
        or locator_marker in text
        or inventory_tag_field_marker in text
        or inventory_tag_create_marker in text
        or inventory_tag_state_marker in text
        or hazard_marker in text):
    raise SystemExit("CoHero GameScene hooks are already present")

mob_anchor = (
    "\t\tfor (Mob mob : Dungeon.level.mobs) {\n"
    "\t\t\taddMobSprite( mob );\n"
    "\t\t}\n"
)
if text.count(mob_anchor) != 1:
    raise SystemExit(f"expected exactly one GameScene mob-loading anchor, found {text.count(mob_anchor)}")

inventory_field_anchor = "\tprivate ResumeIndicator resume;\n"
if text.count(inventory_field_anchor) != 1:
    raise SystemExit(
        f"expected exactly one GameScene tag field anchor, found {text.count(inventory_field_anchor)}"
    )

inventory_create_anchor = (
    "\t\tresume = new ResumeIndicator();\n"
    "\t\tresume.camera = uiCamera;\n"
    "\t\tadd( resume );\n"
)
if text.count(inventory_create_anchor) != 1:
    raise SystemExit(
        f"expected exactly one GameScene tag creation anchor, found {text.count(inventory_create_anchor)}"
    )

text = text.replace(
    inventory_field_anchor,
    inventory_field_anchor + inventory_tag_field_marker + "\n",
    1,
)

inventory_create_block = (
    inventory_create_anchor
    + "\n"
    + inventory_tag_create_marker + "\n"
    + "\t\tcoHeroInventory.camera = uiCamera;\n"
    + "\t\tadd( coHeroInventory );\n"
)
text = text.replace(inventory_create_anchor, inventory_create_block, 1)

layout_anchor = (
    "\t\tlayoutTags();\n"
    "\n"
    "\t\tswitch (InterlevelScene.mode) {\n"
)
if text.count(layout_anchor) != 1:
    raise SystemExit(f"expected exactly one GameScene HUD layout anchor, found {text.count(layout_anchor)}")

text = text.replace(mob_anchor, mob_anchor + "\n" + ready_marker + "\n", 1)

locator_block = (
    "\t\tfloat coHeroSafeLeft = insets.left;\n"
    "\t\tfloat coHeroSafeRight = uiCamera.width - insets.right;\n"
    "\t\tfloat coHeroSafeTop = Math.max(menu.bottom(), boss.bottom());\n"
    "\t\tif (uiSize == 0) coHeroSafeTop = Math.max(coHeroSafeTop, status.bottom());\n"
    "\t\tfloat coHeroSafeBottom = toolbar.top();\n"
    "\t\tif (uiSize > 0) coHeroSafeBottom = Math.min(coHeroSafeBottom, status.top());\n"
    "\t\tif (inventory != null && inventory.visible) coHeroSafeBottom = Math.min(coHeroSafeBottom, inventory.top());\n"
    "\t\tcom.spd.cohero.CoHeroLocator coHeroLocator = new com.spd.cohero.CoHeroLocator(\n"
    "\t\t\t\tcoHeroSafeLeft, coHeroSafeTop, coHeroSafeRight, coHeroSafeBottom);\n"
    "\t\tcoHeroLocator.camera = uiCamera;\n"
    "\t\tadd(coHeroLocator);\n\n"
)
text = text.replace(
    layout_anchor,
    locator_block + layout_anchor,
    1,
)

tag_assignment = "\t\t\ttagResume = resume.visible;\n"
if text.count(tag_assignment) != 2:
    raise SystemExit(
        f"expected exactly two GameScene tag assignment anchors, found {text.count(tag_assignment)}"
    )
text = text.replace(
    tag_assignment,
    tag_assignment + "\t\t\ttagCoHeroInventory = coHeroInventory.visible;\n",
)

tag_condition_anchor = (
    "\t\t} else if (tagAttack != attack.active ||\n"
    "\t\t\t\ttagLoot != loot.visible ||\n"
    "\t\t\t\ttagAction != action.visible ||\n"
    "\t\t\t\ttagResume != resume.visible) {\n"
)
if text.count(tag_condition_anchor) != 1:
    raise SystemExit(
        f"expected exactly one GameScene tag condition anchor, found {text.count(tag_condition_anchor)}"
    )
text = text.replace(
    tag_condition_anchor,
    "\t\t} else if (tagAttack != attack.active ||\n"
    "\t\t\t\ttagLoot != loot.visible ||\n"
    "\t\t\t\ttagAction != action.visible ||\n"
    "\t\t\t\ttagResume != resume.visible ||\n"
    "\t\t\t\ttagCoHeroInventory != coHeroInventory.visible) {\n",
    1,
)

tag_appearing_anchor = (
    "\t\t\tboolean tagAppearing = (attack.active && !tagAttack) ||\n"
    "\t\t\t\t\t\t\t\t\t(loot.visible && !tagLoot) ||\n"
    "\t\t\t\t\t\t\t\t\t(action.visible && !tagAction) ||\n"
    "\t\t\t\t\t\t\t\t\t(resume.visible && !tagResume);\n"
)
if text.count(tag_appearing_anchor) != 1:
    raise SystemExit(
        f"expected exactly one GameScene tag appearing anchor, found {text.count(tag_appearing_anchor)}"
    )
text = text.replace(
    tag_appearing_anchor,
    "\t\t\tboolean tagAppearing = (attack.active && !tagAttack) ||\n"
    "\t\t\t\t\t\t\t\t\t(loot.visible && !tagLoot) ||\n"
    "\t\t\t\t\t\t\t\t\t(action.visible && !tagAction) ||\n"
    "\t\t\t\t\t\t\t\t\t(resume.visible && !tagResume) ||\n"
    "\t\t\t\t\t\t\t\t\t(coHeroInventory.visible && !tagCoHeroInventory);\n",
    1,
)

tag_state_anchor = "\tprivate boolean tagResume    = false;\n"
if text.count(tag_state_anchor) != 1:
    raise SystemExit(
        f"expected exactly one GameScene tag state anchor, found {text.count(tag_state_anchor)}"
    )
text = text.replace(
    tag_state_anchor,
    tag_state_anchor + inventory_tag_state_marker + "\n",
    1,
)

tag_layout_anchor = (
    "\t\tif (scene.tagResume) {\n"
    "\t\t\tscene.resume.setRect( tagLeft, pos - Tag.SIZE, tagWidth, Tag.SIZE );\n"
    "\t\t\tscene.resume.flip(tagsOnLeft);\n"
    "\t\t}\n"
)
if text.count(tag_layout_anchor) != 1:
    raise SystemExit(
        f"expected exactly one GameScene resume tag layout anchor, found {text.count(tag_layout_anchor)}"
    )
text = text.replace(
    tag_layout_anchor,
    "\t\tif (scene.tagCoHeroInventory) {\n"
    "\t\t\tscene.coHeroInventory.setRect( tagLeft, pos - Tag.SIZE, tagWidth, Tag.SIZE );\n"
    "\t\t\tscene.coHeroInventory.flip(tagsOnLeft);\n"
    "\t\t\tpos = scene.coHeroInventory.top();\n"
    "\t\t}\n\n"
    + tag_layout_anchor,
    1,
)

tag_bounds_anchor = (
    "\t}\n"
    "\t\n"
    "\t@Override\n"
    "\tprotected void onBackPressed() {\n"
)
if text.count(tag_bounds_anchor) != 1:
    raise SystemExit(
        f"expected exactly one GameScene post-tag-layout anchor, found {text.count(tag_bounds_anchor)}"
    )

tag_bounds_methods = (
    "\t}\n\n"
    "\tpublic static RectF coHeroTagBounds() {\n"
    "\t\tif (scene == null) return null;\n\n"
    "\t\tRectF bounds = null;\n"
    "\t\tif (scene.tagAttack) bounds = includeCoHeroTag(bounds, scene.attack);\n"
    "\t\tif (scene.tagLoot) bounds = includeCoHeroTag(bounds, scene.loot);\n"
    "\t\tif (scene.tagAction) bounds = includeCoHeroTag(bounds, scene.action);\n"
    "\t\tif (scene.tagCoHeroInventory) bounds = includeCoHeroTag(bounds, scene.coHeroInventory);\n"
    "\t\tif (scene.tagResume) bounds = includeCoHeroTag(bounds, scene.resume);\n"
    "\t\treturn bounds;\n"
    "\t}\n\n"
    "\tprivate static RectF includeCoHeroTag(RectF bounds, Tag tag) {\n"
    "\t\tRectF rect = new RectF(tag.left(), tag.top(), tag.right(), tag.bottom());\n"
    "\t\treturn bounds == null ? rect : bounds.union(rect);\n"
    "\t}\n"
    "\t\n"
    "\t@Override\n"
    "\tprotected void onBackPressed() {\n"
)
text = text.replace(tag_bounds_anchor, tag_bounds_methods, 1)

targeted_anchor = (
    "\tpublic static TargetedCell targetedCell(int pos, float delay){\n"
)
if text.count(targeted_anchor) != 1:
    raise SystemExit(
        f"expected exactly one GameScene targeted-cell anchor, found {text.count(targeted_anchor)}"
    )
text = text.replace(
    targeted_anchor,
    targeted_anchor + hazard_marker + "\n",
    1,
)

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
