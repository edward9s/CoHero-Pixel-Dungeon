#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_mob_exp.py <Mob.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

field_anchor = "\tprotected boolean alerted = false;\n"
field_patch = field_anchor + "\n\tprivate Object coHeroDeathCause;\n"
if "private Object coHeroDeathCause;" in text:
    raise SystemExit("CoHero Mob death-cause field is already present")
if text.count(field_anchor) != 1:
    raise SystemExit(
        f"expected exactly one Mob death-cause field anchor, found {text.count(field_anchor)}"
    )
text = text.replace(field_anchor, field_patch, 1)

die_anchor = "\t@Override\n\tpublic void die( Object cause ) {\n"
die_patch = die_anchor + "\n\t\tcoHeroDeathCause = cause;\n"
if "coHeroDeathCause = cause;" in text:
    raise SystemExit("CoHero Mob death-cause capture is already present")
if text.count(die_anchor) != 1:
    raise SystemExit(
        f"expected exactly one Mob death-cause capture anchor, found {text.count(die_anchor)}"
    )
text = text.replace(die_anchor, die_patch, 1)

replacements = [
    (
        "\t\t\t\tint exp = Dungeon.hero.lvl <= maxLvl ? EXP : 0;",
        "\t\t\t\tint exp = com.spd.cohero.CoHero.killExp(this, coHeroDeathCause);",
        "Mob EXP level gate",
    ),
    (
        "exp == 0 && maxLvl > 0 && EXP > 0 && Dungeon.hero.lvl < Hero.MAX_LEVEL){",
        "exp == 0 && maxLvl > 0 && EXP > 0 && com.spd.cohero.CoHero.killLevel(coHeroDeathCause) < Hero.MAX_LEVEL){",
        "Mob ascension EXP level gate",
    ),
    (
        "\t\t\t\tif (exp > 0) {\n"
        "\t\t\t\t\tDungeon.hero.sprite.showStatusWithIcon(CharSprite.POSITIVE, Integer.toString(exp), FloatingText.EXPERIENCE);\n"
        "\t\t\t\t}\n"
        "\t\t\t\tDungeon.hero.earnExp(exp, getClass());",
        "\t\t\t\tcom.spd.cohero.CoHero.awardKillExp(this, coHeroDeathCause, exp);",
        "Mob EXP recipient",
    ),
]

for old, new, label in replacements:
    if new in text:
        raise SystemExit(f"CoHero {label} hook is already present")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one {label} anchor, found {count}")
    text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
