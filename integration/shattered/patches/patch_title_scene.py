#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_title_scene.py <TitleScene.java>")

path = Path(sys.argv[1])
title = path.read_text(encoding="utf-8")

title_version_old = 'version = new BitmapText( "v" + Game.version, pixelFont);'
title_version_new = 'version = new BitmapText( com.spd.cohero.CoHeroVersion.display(Game.version), pixelFont);'


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
\t\tfloat brandHeight = coHeroTitle.height() - 8;
\t\tfloat topRegion = Math.max(title.height - 6 + brandHeight, h*0.45f);

\t\ttitle.x = insets.left + (w - title.width()) / 2f;
\t\ttitle.y = insets.top + 2 + brandHeight
\t\t\t\t+ (topRegion - brandHeight - title.height()) / 2f;

\t\tcoHeroTitle.x = insets.left + (w - coHeroTitle.width()) / 2f;
\t\tcoHeroTitle.y = title.y - coHeroTitle.height() + 8;

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


path.write_text(title, encoding="utf-8")
print(f"patched {path}")
