#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_wand_base.py <Wand.java>")

path = Path(sys.argv[1])
wand = path.read_text(encoding="utf-8")

def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one {label} anchor")
    return text.replace(old, new, 1)

if "private transient Char coHeroUser;" in wand:
    raise SystemExit("CoHero Wand seam is already present")

field_anchor = "\tprotected int collisionProperties = Ballistica.MAGIC_BOLT;\n"
field_patch = field_anchor + """
	// Non-Hero caster context used only while the CoHero adapter drives the original wand.
	private transient Char coHeroUser;
"""
wand = replace_once(wand, field_anchor, field_patch, "Wand collision")

proc_old = """	protected void wandProc(Char target, int chargesUsed){
		wandProc(target, buffedLvl(), chargesUsed);
	}
"""
proc_new = """	protected void wandProc(Char target, int chargesUsed){
		// The stock proc hook below is Hero talent/subclass behavior.
		if (!coHeroCasting()) {
			wandProc(target, buffedLvl(), chargesUsed);
		}
	}

	protected boolean coHeroCasting() {
		return coHeroUser != null;
	}

	protected Char zapUser() {
		return coHeroUser != null ? coHeroUser : curUser;
	}

	protected Hero progressionHero() {
		return coHeroUser != null ? Dungeon.hero : curUser;
	}

	protected void coHeroPrepareZap(Char owner, int target, Ballistica bolt) {
		// Optional subclass seam for state normally prepared by Hero-only targeting code.
	}

	public boolean coHeroCanZap(Char owner) {
		return owner != null
				&& owner.isAlive()
				&& owner.buff(MagicImmune.class) == null
				&& !cursed
				&& curCharges >= chargesPerCast();
	}

	public int coHeroChargesPerCast() {
		return chargesPerCast();
	}

	public Ballistica coHeroBallistica(Char owner, int target) {
		if (owner == null) {
			throw new IllegalArgumentException("CoHero wand targeting requires an owner");
		}
		return new Ballistica(owner.pos, target, collisionProperties(target));
	}

	public void coHeroCast(
			final Char owner, int target, boolean showFx, final Callback callback) {
		if (!coHeroCanZap(owner)) {
			throw new IllegalStateException("CoHero attempted to use an unavailable wand");
		}

		final Ballistica bolt = coHeroBallistica(owner, target);
		coHeroUser = owner;
		try {
			// Gameplay is resolved before FX construction. The caller decides whether the actor
			// waits for the FX callback; remote CoHero casts skip real FX and use CoHeroRemoteView.
			coHeroPrepareZap(owner, target, bolt);
			onZap(bolt);
			coHeroFinishZap(owner);

			if (showFx) {
				fx(bolt, callback);
			} else if (callback != null) {
				callback.call();
			}
		} finally {
			// Supported FX methods consume zapUser() synchronously while constructing their
			// visual. Their later callback does not need caster context.
			coHeroUser = null;
		}
	}

	private void coHeroFinishZap(Char owner) {
		curCharges -= chargesPerCast();

		WandOfMagicMissile.MagicCharge magicCharge = owner.buff(WandOfMagicMissile.MagicCharge.class);
		if (magicCharge != null
				&& magicCharge.wandJustApplied() != this
				&& magicCharge.level() == buffedLvl()
				&& buffedLvl() > super.buffedLvl()) {
			magicCharge.detach();
		} else {
			ScrollEmpower empower = owner.buff(ScrollEmpower.class);
			if (empower != null) {
				empower.use();
			}
		}

		updateQuickslot();
	}
"""
wand = replace_once(wand, proc_old, proc_new, "Wand wandProc")

recharge_old = """\t\t\tif (Regeneration.regenOn())
\t\t\t\tpartialCharge += (1f/turnsToCharge) * RingOfEnergy.wandChargeMultiplier(target);
"""
recharge_new = """\t\t\tif (Regeneration.regenOn())
\t\t\t\tpartialCharge += (1f/turnsToCharge)
\t\t\t\t\t\t* RingOfEnergy.wandChargeMultiplier(target)
\t\t\t\t\t\t* com.spd.cohero.CoHeroClassTraits.wandChargeMultiplier(target);
"""
wand = replace_once(wand, recharge_old, recharge_new, "Wand natural recharge")

fx_old = """	public void fx(Ballistica bolt, Callback callback) {
		MagicMissile.boltFromChar( curUser.sprite.parent,
				MagicMissile.MAGIC_MISSILE,
				curUser.sprite,
				bolt.collisionPos,
				callback);
		Sample.INSTANCE.play( Assets.Sounds.ZAP );
	}
"""
fx_new = """	public void fx(Ballistica bolt, Callback callback) {
		Char user = zapUser();
		MagicMissile.boltFromChar( user.sprite.parent,
				MagicMissile.MAGIC_MISSILE,
				user.sprite,
				bolt.collisionPos,
				callback);
		Sample.INSTANCE.play( Assets.Sounds.ZAP );
	}
"""
wand = replace_once(wand, fx_old, fx_new, "Wand fx")


path.write_text(wand, encoding="utf-8")
print(f"patched {path}")
