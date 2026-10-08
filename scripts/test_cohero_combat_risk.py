#!/usr/bin/env python3
"""Compile and exercise the dependency-free combat emergency policy."""
from pathlib import Path
import subprocess
import sys
import tempfile

if len(sys.argv) != 2:
    raise SystemExit("usage: test_cohero_combat_risk.py <repo-root>")

root = Path(sys.argv[1]).resolve()
source = root / "core/src/main/java/com/spd/cohero/CoHeroCombatRisk.java"
tests = root / "scripts/tests/CoHeroCombatRiskChecks.java"
with tempfile.TemporaryDirectory(prefix="cohero-risk-") as directory:
    subprocess.run(["javac", "-d", directory, str(source), str(tests)], check=True)
    subprocess.run(
        ["java", "-cp", directory, "com.spd.cohero.CoHeroCombatRiskChecks"],
        check=True,
    )
