#!/usr/bin/env python3
from pathlib import Path
import sys

from java_patch import java_source

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_level_mobs.py <Level.java>")

path = Path(sys.argv[1])
text = java_source(path.read_text(encoding="utf-8"))
old = "\t\tbundle.put( MOBS, mobs );"
new = "\t\tcom.spd.cohero.CoHero.storeLevelMobs(bundle, MOBS, mobs);"

if new in text:
    raise SystemExit("CoHero level mob save hook is already present")

count = text.count(old)
if count != 1:
    raise SystemExit(f"expected exactly one Level mob save anchor, found {count}")

text = text.replace(old, new, 1)

ward_vision_old = """			for (Mob m : mobs){
				if (m instanceof WandOfWarding.Ward
						|| m instanceof WandOfRegrowth.Lotus
						|| m instanceof SpiritHawk.HawkAlly
						|| m.buff(PowerOfMany.PowerBuff.class) != null){
"""

ward_vision_new = """			for (Mob m : mobs){
				if (m instanceof WandOfWarding.Ward
						&& ((WandOfWarding.Ward) m).coHeroOwned()) {
					continue;
				}
				if (m instanceof WandOfWarding.Ward
						|| m instanceof WandOfRegrowth.Lotus
						|| m instanceof SpiritHawk.HawkAlly
						|| m.buff(PowerOfMany.PowerBuff.class) != null){
"""

if "coHeroOwned()) {" in text:
    raise SystemExit("CoHero-owned ward Hero-FOV exclusion is already present")

if text.count(ward_vision_old) != 1:
    raise SystemExit(
        f"expected exactly one Level ward-vision anchor, found {text.count(ward_vision_old)}"
    )

text = text.replace(ward_vision_old, ward_vision_new, 1)

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
