#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_wand_regrowth.py <WandOfRegrowth.java>")

path = Path(sys.argv[1])
regrowth = path.read_text(encoding="utf-8")

def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one {label} anchor")
    return text.replace(old, new, 1)

# Regrowth: preserve cone generation and plant logic, but prepare the target without Hero.tryToZap.
if "curUser" not in regrowth:
    raise SystemExit("expected Regrowth curUser references")
regrowth = regrowth.replace("curUser", "zapUser()")
regrowth_anchor = """	@Override
	public void onZap(Ballistica bolt) {
"""
regrowth_patch = """	@Override
	protected void coHeroPrepareZap(Char owner, int target, Ballistica bolt) {
		this.target = target;
		prepareCoHeroCone(bolt);
	}

""" + regrowth_anchor
regrowth = replace_once(regrowth, regrowth_anchor, regrowth_patch, "Regrowth prepare")

regrowth_fx_old = """	public void fx(Ballistica bolt, Callback callback) {

		// 4/6/8 distance
		int maxDist = 2 + 2*chargesPerCast();

		cone = new ConeAOE( bolt,
				maxDist,
				20 + 10*chargesPerCast(),
				Ballistica.STOP_SOLID | Ballistica.STOP_TARGET);

		//cast to cells at the tip, rather than all cells, better performance.
"""
regrowth_fx_new = """	private void prepareCoHeroCone(Ballistica bolt) {
		int maxDist = 2 + 2*chargesPerCast();
		cone = new ConeAOE(
				bolt,
				maxDist,
				20 + 10*chargesPerCast(),
				Ballistica.STOP_SOLID | Ballistica.STOP_TARGET);
	}

	public void fx(Ballistica bolt, Callback callback) {
		if (!coHeroCasting()) {
			prepareCoHeroCone(bolt);
		}

		//cast to cells at the tip, rather than all cells, better performance.
"""
regrowth = replace_once(regrowth, regrowth_fx_old, regrowth_fx_new, "Regrowth CoHero prepare/fx")


path.write_text(regrowth, encoding="utf-8")
print(f"patched {path}")
