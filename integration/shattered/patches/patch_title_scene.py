#!/usr/bin/env python3
from pathlib import Path
import sys

from java_patch import java_source

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_title_scene.py <TitleScene.java>")

path = Path(sys.argv[1])
title = java_source(path.read_text(encoding="utf-8"))

title_version_old = 'version = new BitmapText( "v" + Game.version, pixelFont);'
title_version_new = ('com.spd.cohero.CoHeroUpdates.checkForUpdate();\n\t\t'
    'version = new BitmapText( '
    'com.spd.cohero.CoHeroVersion.display(Game.version, '
    'com.spd.cohero.CoHeroUpdates.latestVersion()), pixelFont);')


title_field_old = """\tprivate Image title;
"""
title_field_new = """\tprivate Image title;
\tprivate BitmapText coHeroTitle;
"""

title_layout_old = """\t\ttitle = BannerSprites.get( landscape() ? BannerSprites.Type.TITLE_LAND : BannerSprites.Type.TITLE_PORT);
\t\tadd( title );

\t\tfloat topRegion = Math.max(title.height - 6, h*0.45f);

\t\ttitle.x = insets.left + (w - title.width()) / 2f;
\t\ttitle.y = insets.top + 2 + (topRegion - title.height()) / 2f;

\t\talign(title);
"""
title_layout_new = """\t\ttitle = BannerSprites.get( landscape() ? BannerSprites.Type.TITLE_LAND : BannerSprites.Type.TITLE_PORT);
\t\tadd( title );

\t\tcoHeroTitle = new BitmapText( "CoHero", pixelFont );
\t\tcoHeroTitle.scale.set(PixelScene.align(2.5f));
\t\tcoHeroTitle.hardlight(0xC4D2AF);
\t\tcoHeroTitle.measure();
\t\tadd(coHeroTitle);
\t\t
\t\tfloat brandOverlap = landscape() ? 0 : 12;
\t\tfloat brandHeight = coHeroTitle.height() - brandOverlap;
\t\tfloat topRegion = Math.max(title.height - 6 + brandHeight, h*0.45f);

\t\ttitle.x = insets.left + (w - title.width()) / 2f;
\t\ttitle.y = insets.top + 2 + brandHeight
\t\t\t\t+ (topRegion - brandHeight - title.height()) / 2f;

\t\tcoHeroTitle.x = insets.left + (w - coHeroTitle.width()) / 2f;
\t\tcoHeroTitle.y = title.y - coHeroTitle.height() + brandOverlap;

\t\talign(title);
\t\talign(coHeroTitle);
"""

fade_old = """\t\ttitle.am = alpha;
\t\tleftFB.am = alpha;
"""
fade_new = """\t\ttitle.am = alpha;
\t\tcoHeroTitle.alpha(alpha);
\t\tleftFB.am = alpha;
"""

anchors = [
    ("TitleScene version", title_version_old, title_version_new),
    ("TitleScene CoHero title field", title_field_old, title_field_new),
    ("TitleScene CoHero title layout", title_layout_old, title_layout_new),
    ("TitleScene CoHero title fade", fade_old, fade_new),
]

for label, old, new in anchors:
    if new in title:
        raise SystemExit(f"{label} hook is already present")
    count = title.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one {label} anchor, found {count}")
    title = title.replace(old, new, 1)


# Refresh the version label after the asynchronous release check completes.
update_anchor = "\tprivate float uiAlpha;"
if title.count(update_anchor) != 1:
    raise SystemExit("expected exactly one TitleScene version-update anchor")
update_hook = """
\t@Override
\tpublic void update() {
\t\tsuper.update();
\t\tString display = com.spd.cohero.CoHeroVersion.display(
\t\t\t\tGame.version, com.spd.cohero.CoHeroUpdates.latestVersion());
\t\tif (!display.equals(version.text())) {
\t\t\tfloat right = version.x + version.width();
\t\t\tversion.text(display);
\t\t\tversion.measure();
\t\t\tversion.x = right - version.width();
\t\t}
\t}

"""
title = title.replace(update_anchor, update_hook + update_anchor, 1)

path.write_text(title, encoding="utf-8")
print(f"patched {path}")
