#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_offscreen_enemy_actions.py <mobs-dir>")

mobs_dir = Path(sys.argv[1])

# Stock special actions that already have a synchronous fallback branch. Their only bug in
# CoHero is that union-FOV sprite visibility can select the blocking visual branch even though
# Hero cannot see the action. Keep the stock Hero-visible path, otherwise take the synchronous
# gameplay branch. Mob.java itself is patched centrally by patch_mob_cohero.py.
expected_counts = {
    "DM100.java": 1,
    "DM200.java": 2,
    "DM201.java": 1,
    "DM300.java": 4,
    "Guard.java": 2,
    "Golem.java": 2,
    "Spinner.java": 2,
    "Warlock.java": 1,
    "Elemental.java": 2,
    "CrystalWisp.java": 1,
    "Shaman.java": 1,
    "YogFist.java": 1,
    "quest/vault/VaultBossElemental.java": 1,
}

old_visibility = "sprite.visible || enemy.sprite.visible"
hero_visibility = (
    "!coHeroPresentationPending() "
    "&& (com.spd.cohero.CoHero.heroCanSee(pos) "
    "|| com.spd.cohero.CoHero.heroCanSee(enemy.pos))"
)

for relative, expected in expected_counts.items():
    path = mobs_dir / relative
    text = path.read_text(encoding="utf-8")
    actual = text.count(old_visibility)
    if actual != expected:
        raise SystemExit(
            f"expected {expected} offscreen-action visibility anchor(s) in {path}, found {actual}"
        )
    path.write_text(text.replace(old_visibility, hero_visibility), encoding="utf-8")
    print(f"patched {path}")

# Necromancer's skeleton-support zap checks only sprite.visible and therefore becomes blocking
# when the sprite is visible through CoHero FOV. Split gameplay from callback completion; remote
# support resolves immediately instead of borrowing the stock blocking zap callback.
necromancer_path = mobs_dir / "Necromancer.java"
necromancer = necromancer_path.read_text(encoding="utf-8")

necro_complete_old = """	public void onZapComplete(){
		if (mySkeleton == null || mySkeleton.sprite == null || !mySkeleton.isAlive()){
			return;
		}
		
		//heal skeleton first
		if (mySkeleton.HP < mySkeleton.HT){

			if (sprite.visible || mySkeleton.sprite.visible) {
				sprite.parent.add(new Beam.HealthRay(sprite.center(), mySkeleton.sprite.center()));
				Sample.INSTANCE.play( Assets.Sounds.RAY );
			}
			
			mySkeleton.HP = Math.min(mySkeleton.HP + mySkeleton.HT/5, mySkeleton.HT);
			if (mySkeleton.sprite.visible) {
				mySkeleton.sprite.showStatusWithIcon( CharSprite.POSITIVE, Integer.toString( mySkeleton.HT/5 ), FloatingText.HEALING );
			}
			
		//otherwise give it adrenaline
		} else if (mySkeleton.buff(Adrenaline.class) == null) {

			if (sprite.visible || mySkeleton.sprite.visible) {
				sprite.parent.add(new Beam.HealthRay(sprite.center(), mySkeleton.sprite.center()));
				Sample.INSTANCE.play( Assets.Sounds.RAY );
			}
			
			Buff.affect(mySkeleton, Adrenaline.class, 3f);
		}
		
		next();
	}
"""

necro_complete_new = """	private void resolveSkeletonSupportZap() {
		if (mySkeleton == null || mySkeleton.sprite == null || !mySkeleton.isAlive()){
			return;
		}

		//heal skeleton first
		if (mySkeleton.HP < mySkeleton.HT){

			if (sprite.visible || mySkeleton.sprite.visible) {
				sprite.parent.add(new Beam.HealthRay(sprite.center(), mySkeleton.sprite.center()));
				Sample.INSTANCE.play( Assets.Sounds.RAY );
			}

			mySkeleton.HP = Math.min(mySkeleton.HP + mySkeleton.HT/5, mySkeleton.HT);
			if (mySkeleton.sprite.visible) {
				mySkeleton.sprite.showStatusWithIcon( CharSprite.POSITIVE, Integer.toString( mySkeleton.HT/5 ), FloatingText.HEALING );
			}

		//otherwise give it adrenaline
		} else if (mySkeleton.buff(Adrenaline.class) == null) {

			if (sprite.visible || mySkeleton.sprite.visible) {
				sprite.parent.add(new Beam.HealthRay(sprite.center(), mySkeleton.sprite.center()));
				Sample.INSTANCE.play( Assets.Sounds.RAY );
			}

			Buff.affect(mySkeleton, Adrenaline.class, 3f);
		}
	}

	public void onZapComplete(){
		resolveSkeletonSupportZap();
		next();
	}
"""

if necromancer.count(necro_complete_old) != 1:
    raise SystemExit(
        f"expected exactly one Necromancer support completion block, found {necromancer.count(necro_complete_old)}"
    )
necromancer = necromancer.replace(necro_complete_old, necro_complete_new, 1)

necro_attack_old = """					//zap skeleton
					if (mySkeleton.HP < mySkeleton.HT || mySkeleton.buff(Adrenaline.class) == null) {
						if (sprite != null && sprite.visible){
							sprite.zap(mySkeleton.pos);
							return false;
						} else {
							onZapComplete();
						}
					}
"""

necro_attack_new = """					//zap skeleton
					if (mySkeleton.HP < mySkeleton.HT || mySkeleton.buff(Adrenaline.class) == null) {
						boolean heroVisible = !coHeroPresentationPending()
								&& (com.spd.cohero.CoHero.heroCanSee(pos)
								|| com.spd.cohero.CoHero.heroCanSee(mySkeleton.pos));
						if (sprite != null && heroVisible){
							sprite.zap(mySkeleton.pos);
							return false;
						}

						resolveSkeletonSupportZap();
					}
"""

if necromancer.count(necro_attack_old) != 1:
    raise SystemExit(
        f"expected exactly one Necromancer support zap branch, found {necromancer.count(necro_attack_old)}"
    )
necromancer = necromancer.replace(necro_attack_old, necro_attack_new, 1)
necromancer_path.write_text(necromancer, encoding="utf-8")
print(f"patched {necromancer_path}")

# Ripper Demon leap is another stock exception: it always returns false and waits for the jump
# callback even when Hero cannot see it. Extract gameplay resolution and make remote jump visual
# best-effort only.
ripper_path = mobs_dir / "RipperDemon.java"
ripper = ripper_path.read_text(encoding="utf-8")

ripper_helper_anchor = """	private int leapPos = -1;
	private float leapCooldown = 0;

	public class Hunting extends Mob.Hunting {
"""

ripper_helper_patch = """	private int leapPos = -1;
	private float leapCooldown = 0;

	private void resolveLeapGameplay(Char leapVictim, int leapLanding, int endPos) {
		if (leapVictim != null && alignment != leapVictim.alignment){
			if (hit(RipperDemon.this, leapVictim, Char.INFINITE_ACCURACY, false)) {
				Buff.affect(leapVictim, Bleeding.class).set(0.75f * damageRoll());
				leapVictim.sprite.flash();
				Sample.INSTANCE.play(Assets.Sounds.HIT);
			} else {
				leapVictim.sprite.showStatus( CharSprite.NEUTRAL, leapVictim.defenseVerb() );
				Sample.INSTANCE.play(Assets.Sounds.MISS);
			}
		}

		if (endPos != leapLanding){
			Actor.add(new Pushing(RipperDemon.this, leapLanding, endPos));
		}

		pos = endPos;
		leapPos = -1;
		Dungeon.level.occupyCell(RipperDemon.this);
	}

	public class Hunting extends Mob.Hunting {
"""

if ripper.count(ripper_helper_anchor) != 1:
    raise SystemExit(
        f"expected exactly one RipperDemon helper anchor, found {ripper.count(ripper_helper_anchor)}"
    )
ripper = ripper.replace(ripper_helper_anchor, ripper_helper_patch, 1)

ripper_leap_old = """				//do leap
				sprite.visible = Dungeon.level.heroFOV[pos] || Dungeon.level.heroFOV[leapPos] || Dungeon.level.heroFOV[endPos];
				sprite.jump(pos, leapPos, new Callback() {
					@Override
					public void call() {

						if (leapVictim != null && alignment != leapVictim.alignment){
							if (hit(RipperDemon.this, leapVictim, Char.INFINITE_ACCURACY, false)) {
								Buff.affect(leapVictim, Bleeding.class).set(0.75f * damageRoll());
								leapVictim.sprite.flash();
								Sample.INSTANCE.play(Assets.Sounds.HIT);
							} else {
								leapVictim.sprite.showStatus( CharSprite.NEUTRAL, leapVictim.defenseVerb() );
								Sample.INSTANCE.play(Assets.Sounds.MISS);
							}
						}

						if (endPos != leapPos){
							Actor.add(new Pushing(RipperDemon.this, leapPos, endPos));
						}

						pos = endPos;
						leapPos = -1;
						sprite.idle();
						Dungeon.level.occupyCell(RipperDemon.this);
						next();
					}
				});
				return false;
"""

ripper_leap_new = """				//do leap
				final int leapStart = pos;
				final int leapLanding = leapPos;
				boolean heroVisible = !com.spd.cohero.CoHeroPresentation.isPending(RipperDemon.this)
						&& (com.spd.cohero.CoHero.heroCanSee(leapStart)
						|| com.spd.cohero.CoHero.heroCanSee(leapLanding)
						|| com.spd.cohero.CoHero.heroCanSee(endPos));

				if (heroVisible) {
					sprite.visible = true;
					sprite.jump(leapStart, leapLanding, new Callback() {
						@Override
						public void call() {
							resolveLeapGameplay(leapVictim, leapLanding, endPos);
							sprite.idle();
							next();
						}
					});
					return false;
				}

				boolean showRemote = com.spd.cohero.CoHeroPresentation.shouldShow(
						leapStart, leapLanding, endPos)
						&& com.spd.cohero.CoHeroPresentation.tryBegin(RipperDemon.this);
				if (showRemote) {
					sprite.visible = true;
					sprite.jump(leapStart, leapLanding, new Callback() {
						@Override
						public void call() {
							sprite.idle();
							com.spd.cohero.CoHeroPresentation.complete(RipperDemon.this);
							sprite.place(pos);
						}
					});
				}

				resolveLeapGameplay(leapVictim, leapLanding, endPos);
				if (!showRemote) {
					sprite.place(endPos);
				}
				return true;
"""

if ripper.count(ripper_leap_old) != 1:
    raise SystemExit(
        f"expected exactly one RipperDemon leap block, found {ripper.count(ripper_leap_old)}"
    )
ripper = ripper.replace(ripper_leap_old, ripper_leap_new, 1)
ripper_path.write_text(ripper, encoding="utf-8")
print(f"patched {ripper_path}")
