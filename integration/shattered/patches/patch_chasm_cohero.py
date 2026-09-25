#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_chasm_cohero.py <Chasm.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

mob_old = """
\tpublic static void mobFall( Mob mob ) {
\t\tif (mob.isAlive()) {
\t\t\tBuff.prolong(mob, Trap.HazardAssistTracker.class, Trap.HazardAssistTracker.DURATION);
\t\t\tmob.die( Chasm.class );
\t\t}
\t\t
\t\tif (mob.sprite != null) ((MobSprite)mob.sprite).fall();
\t}
"""

mob_new = """
\tpublic static void mobFall( Mob mob ) {
\t\tif (mob instanceof com.spd.cohero.CoHeroAlly) {
\t\t\t// Falling changes floors for the whole party, but landing consequences belong
\t\t\t// to the actor that actually fell.
\t\t\tcom.spd.cohero.CoHero.markCompanionChasmFall(false);
\t\t\theroFall(mob.pos);
\t\t\treturn;
\t\t}

\t\tif (mob.isAlive()) {
\t\t\tBuff.prolong(mob, Trap.HazardAssistTracker.class, Trap.HazardAssistTracker.DURATION);
\t\t\tmob.die( Chasm.class );
\t\t}
\t\t
\t\tif (mob.sprite != null) ((MobSprite)mob.sprite).fall();
\t}
"""

land_old = """
\tpublic static void heroLand() {
\t\t
\t\tHero hero = Dungeon.hero;
\t\t
\t\tElixirOfFeatherFall.FeatherBuff b = hero.buff(ElixirOfFeatherFall.FeatherBuff.class);
\t\t
\t\tif (b != null){
\t\t\thero.sprite.emitter().burst( Speck.factory( Speck.JET ), 20);
\t\t\tb.processFall();
\t\t\treturn;
\t\t}
\t\t
\t\tPixelScene.shake( 4, 1f );

\t\tDungeon.level.occupyCell(hero );
\t\tBuff.prolong( hero, Cripple.class, Cripple.DURATION );

\t\t//The lower the hero's HP, the more bleed and the less upfront damage.
\t\t//Hero has a 50% chance to bleed out at 66% HP, and begins to risk instant-death at 25%
\t\tBuff.affect( hero, Bleeding.class).set( Math.round(hero.HT / (6f + (6f*(hero.HP/(float)hero.HT)))), Chasm.class);
\t\thero.damage( Math.max( hero.HP / 2, Random.NormalIntRange( hero.HP / 2, hero.HT / 4 )), new Chasm() );
\t}
"""

land_new = """
\tpublic static void heroLand() {

\t\tboolean companionFell = com.spd.cohero.CoHero.hasPendingCompanionChasmLanding();
\t\tif (companionFell) {
\t\t\tboolean heroAlsoFell = com.spd.cohero.CoHero.companionChasmFallIncludesHero();
\t\t\tcom.spd.cohero.CoHeroAlly companion =
\t\t\t\t\tcom.spd.cohero.CoHero.consumeCompanionChasmLanding();
\t\t\tapplyLandingEffects(companion);

\t\t\t// Landing damage can end the run through CoHero death. Do not continue resolving
\t\t\t// Hero-only effects after the run has already ended.
\t\t\tif (!Dungeon.hero.isAlive()) {
\t\t\t\treturn;
\t\t\t}

\t\t\tif (!heroAlsoFell) {
\t\t\t\tPixelScene.shake( 4, 1f );
\t\t\t\treturn;
\t\t\t}
\t\t}

\t\tHero hero = Dungeon.hero;
\t\t
\t\tElixirOfFeatherFall.FeatherBuff b = hero.buff(ElixirOfFeatherFall.FeatherBuff.class);
\t\t
\t\tif (b != null){
\t\t\t// Feather Fall protects only the real Hero. If CoHero also fell, its landing
\t\t\t// consequences were already applied above.
\t\t\tif (companionFell) {
\t\t\t\tPixelScene.shake( 4, 1f );
\t\t\t}
\t\t\thero.sprite.emitter().burst( Speck.factory( Speck.JET ), 20);
\t\t\tb.processFall();
\t\t\treturn;
\t\t}
\t\t
\t\tPixelScene.shake( 4, 1f );
\t\tapplyLandingEffects(hero);
\t}

\tpublic static void applyLandingEffects(
\t\t\tcom.shatteredpixel.shatteredpixeldungeon.actors.Char target ) {

\t\tDungeon.level.occupyCell(target);
\t\tBuff.prolong( target, Cripple.class, Cripple.DURATION );

\t\t// The lower the target's HP, the more bleed and the less upfront damage.
\t\tBuff.affect( target, Bleeding.class).set(
\t\t\t\tMath.round(target.HT / (6f + (6f*(target.HP/(float)target.HT)))),
\t\t\t\tChasm.class);
\t\ttarget.damage(
\t\t\t\tMath.max(target.HP / 2, Random.NormalIntRange(target.HP / 2, target.HT / 4)),
\t\t\t\tnew Chasm());
\t}
"""

if "Falling changes floors for the whole party" in text:
    raise SystemExit("CoHero chasm landing-owner seam is already present")
for name, old in (("mobFall", mob_old), ("heroLand", land_old)):
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one Chasm.{name} anchor, found {text.count(old)}")

text = text.replace(mob_old, mob_new, 1)
text = text.replace(land_old, land_new, 1)

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
