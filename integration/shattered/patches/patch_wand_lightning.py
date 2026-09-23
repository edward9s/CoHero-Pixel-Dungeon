#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_wand_lightning.py <WandOfLightning.java>")

path = Path(sys.argv[1])
lightning = path.read_text(encoding="utf-8")

def replace_once(text, old, new, label):
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one {label} anchor")
    return text.replace(old, new, 1)

# Lightning: its fx prepares combat state, so all caster references must follow the adapter context.
if "curUser" not in lightning:
    raise SystemExit("expected Lightning curUser references")
lightning = lightning.replace("curUser", "zapUser()")
lightning = replace_once(
    lightning,
    "if (n == Dungeon.hero && PathFinder.distance[i] > 1)",
    "if (n == zapUser() && PathFinder.distance[i] > 1)",
    "Lightning caster arc safety",
)
lightning = replace_once(
    lightning,
    "if (!zapUser().isAlive()) {",
    "if (!zapUser().isAlive() && zapUser() == Dungeon.hero) {",
    "Lightning Hero death handling",
)

lightning_fx_old = """	@Override
	public void fx(Ballistica bolt, Callback callback) {

		affected.clear();
		arcs.clear();

		int cell = bolt.collisionPos;

		Char ch = Actor.findChar( cell );
		if (ch != null) {
			if (ch instanceof DwarfKing){
				Statistics.qualifiedForBossChallengeBadge = false;
			}

			affected.add( ch );
			arcs.add( new Lightning.Arc(zapUser().sprite.center(), ch.sprite.center()));
			arc(ch);
		} else {
			arcs.add( new Lightning.Arc(zapUser().sprite.center(), DungeonTilemap.raisedTileCenterToWorld(bolt.collisionPos)));
			CellEmitter.center( cell ).burst( SparkParticle.FACTORY, 3 );
		}

		//don't want to wait for the effect before processing damage.
		zapUser().sprite.parent.addToFront( new Lightning( arcs, null ) );
		Sample.INSTANCE.play( Assets.Sounds.LIGHTNING );
		callback.call();
	}
"""
lightning_fx_new = """	private void prepareCoHeroZapState(Ballistica bolt) {
		affected.clear();
		arcs.clear();

		int cell = bolt.collisionPos;
		Char ch = Actor.findChar(cell);
		if (ch != null) {
			if (ch instanceof DwarfKing) {
				Statistics.qualifiedForBossChallengeBadge = false;
			}
			affected.add(ch);
			arcs.add(new Lightning.Arc(zapUser().sprite.center(), ch.sprite.center()));
			arc(ch);
		} else {
			arcs.add(new Lightning.Arc(
					zapUser().sprite.center(),
					DungeonTilemap.raisedTileCenterToWorld(bolt.collisionPos)));
		}
	}

	@Override
	protected void coHeroPrepareZap(Char owner, int target, Ballistica bolt) {
		prepareCoHeroZapState(bolt);
	}

	@Override
	public void fx(Ballistica bolt, Callback callback) {
		if (!coHeroCasting()) {
			prepareCoHeroZapState(bolt);
		}

		if (Actor.findChar(bolt.collisionPos) == null) {
			CellEmitter.center(bolt.collisionPos).burst(SparkParticle.FACTORY, 3);
		}

		//don't want to wait for the effect before processing damage.
		zapUser().sprite.parent.addToFront(new Lightning(arcs, null));
		Sample.INSTANCE.play(Assets.Sounds.LIGHTNING);
		callback.call();
	}
"""
lightning = replace_once(lightning, lightning_fx_old, lightning_fx_new, "Lightning CoHero prepare/fx")


path.write_text(lightning, encoding="utf-8")
print(f"patched {path}")
