#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 3:
    raise SystemExit(
        "usage: patch_vault_transition_cohero.py <VaultLevel.java> <EscapeCrystal.java>"
    )

vault_level = Path(sys.argv[1])
escape_crystal = Path(sys.argv[2])

vault_text = vault_level.read_text(encoding="utf-8")
crystal_text = escape_crystal.read_text(encoding="utf-8")

marker = "com.spd.cohero.CoHero.captureCompanionStateForForcedTransition"

if marker in vault_text:
    raise SystemExit(f"CoHero VaultLevel transition patch already applied to {vault_level}")
if marker in crystal_text:
    raise SystemExit(
        f"CoHero EscapeCrystal transition patch already applied to {escape_crystal}"
    )

vault_anchor = (
    "\t\tif (ch == Dungeon.hero && (Imp.Quest.isCompleted() && !Imp.Quest.isOld())){\n"
    "\t\t\tbeforeTransition();\n"
)
if vault_text.count(vault_anchor) != 1:
    raise SystemExit(
        f"expected exactly one VaultLevel forced-exit anchor, found {vault_text.count(vault_anchor)}"
    )
vault_text = vault_text.replace(
    vault_anchor,
    "\t\tif (ch == Dungeon.hero && (Imp.Quest.isCompleted() && !Imp.Quest.isOld())){\n"
    "\t\t\tcom.spd.cohero.CoHero.captureCompanionStateForForcedTransition();\n"
    "\t\t\tbeforeTransition();\n",
    1,
)

crystal_anchor = (
    "\t\tif (!Imp.Quest.isOld()) Imp.Quest.complete(score);\n"
    "\n"
    "\t\tLevel.beforeTransition();\n"
)
if crystal_text.count(crystal_anchor) != 1:
    raise SystemExit(
        "expected exactly one EscapeCrystal forced-exit anchor, found "
        f"{crystal_text.count(crystal_anchor)}"
    )
crystal_text = crystal_text.replace(
    crystal_anchor,
    "\t\tif (!Imp.Quest.isOld()) Imp.Quest.complete(score);\n"
    "\n"
    "\t\tcom.spd.cohero.CoHero.captureCompanionStateForForcedTransition();\n"
    "\t\tLevel.beforeTransition();\n",
    1,
)

vault_level.write_text(vault_text, encoding="utf-8")
escape_crystal.write_text(crystal_text, encoding="utf-8")
print(f"patched {vault_level}")
print(f"patched {escape_crystal}")
