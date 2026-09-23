#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_mages_staff.py <MagesStaff.java>")

path = Path(sys.argv[1])
staff = path.read_text(encoding="utf-8")

staff_anchor = """	public void applyWandChargeBuff(Char owner){
		if (wand != null){
			wand.charge(owner, STAFF_SCALE_FACTOR);
		}
	}
"""
staff_patch = staff_anchor + """
	public Wand coHeroWand() {
		if (wand != null) {
			// Match the stock AC_ZAP curse semantics without routing through execute(Hero,...).
			wand.cursed = cursed || hasCurseEnchant();
		}
		return wand;
	}
"""
if staff.count(staff_anchor) != 1:
    raise SystemExit(f"expected exactly one Mage's Staff charge anchor, found {staff.count(staff_anchor)}")
staff = staff.replace(staff_anchor, staff_patch, 1)


path.write_text(staff, encoding="utf-8")
print(f"patched {path}")
