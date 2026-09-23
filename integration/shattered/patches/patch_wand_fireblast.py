#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_wand_fireblast.py <WandOfFireblast.java>")

path = Path(sys.argv[1])
fireblast = path.read_text(encoding="utf-8")

def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one {label} anchor")
    return text.replace(old, new, 1)

# Fireblast: cone logic is generic; its visual source must use the actual caster.
expected_user_lines = (
    "((MagicMissile)curUser.sprite.parent.recycle( MagicMissile.class )).reset(",
    "curUser.sprite,",
    "MagicMissile.boltFromChar( curUser.sprite.parent,",
    "curUser.sprite,",
)
actual_user_lines = tuple(line.strip() for line in fireblast.splitlines() if "curUser" in line)
if sorted(actual_user_lines) != sorted(expected_user_lines):
    raise SystemExit("unexpected Fireblast curUser references")
fireblast = fireblast.replace("curUser", "zapUser()")

fireblast_fx_old = """	@Override
	public void fx(Ballistica bolt, Callback callback) {
		//need to perform flame spread logic here so we can determine what cells to put flames in.

		// 5/7/9 distance
		int maxDist = 3 + 2*chargesPerCast();

		cone = new ConeAOE( bolt,
				maxDist,
				30 + 20*chargesPerCast(),
				Ballistica.STOP_TARGET | Ballistica.STOP_SOLID | Ballistica.IGNORE_SOFT_SOLID);

		//cast to cells at the tip, rather than all cells, better performance.
"""
fireblast_fx_new = """	private void prepareCoHeroCone(Ballistica bolt) {
		int maxDist = 3 + 2*chargesPerCast();
		cone = new ConeAOE(
				bolt,
				maxDist,
				30 + 20*chargesPerCast(),
				Ballistica.STOP_TARGET | Ballistica.STOP_SOLID | Ballistica.IGNORE_SOFT_SOLID);
	}

	@Override
	protected void coHeroPrepareZap(Char owner, int target, Ballistica bolt) {
		prepareCoHeroCone(bolt);
	}

	@Override
	public void fx(Ballistica bolt, Callback callback) {
		if (!coHeroCasting()) {
			prepareCoHeroCone(bolt);
		}

		//cast to cells at the tip, rather than all cells, better performance.
"""
fireblast = replace_once(fireblast, fireblast_fx_old, fireblast_fx_new, "Fireblast CoHero prepare/fx")


path.write_text(fireblast, encoding="utf-8")
print(f"patched {path}")
