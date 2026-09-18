#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 4:
    raise SystemExit("usage: patch_wands.py <Wand.java> <DamageWand.java> <WandOfMagicMissile.java>")

wand_path = Path(sys.argv[1])
damage_path = Path(sys.argv[2])
magic_path = Path(sys.argv[3])

wand = wand_path.read_text(encoding="utf-8")
damage = damage_path.read_text(encoding="utf-8")
magic = magic_path.read_text(encoding="utf-8")

if "private transient Char coHeroUser;" in wand:
    raise SystemExit("CoHero Wand seam is already present")

field_anchor = "\tprotected int collisionProperties = Ballistica.MAGIC_BOLT;\n"
field_patch = field_anchor + "\n\t// Non-Hero caster context used only by the explicit CoHero wand adapter.\n\tprivate transient Char coHeroUser;\n"
if wand.count(field_anchor) != 1:
    raise SystemExit("expected exactly one Wand collision anchor")
wand = wand.replace(field_anchor, field_patch, 1)

proc_old = """\tprotected void wandProc(Char target, int chargesUsed){
\t\twandProc(target, buffedLvl(), chargesUsed);
\t}
"""
proc_new = """\tprotected void wandProc(Char target, int chargesUsed){
\t\t// The stock proc hook below is entirely Hero talent/subclass behavior.
\t\t// CoHero deliberately has no Hero talent adapter, so skip it for non-Hero casts.
\t\tif (coHeroUser == null) {
\t\t\twandProc(target, buffedLvl(), chargesUsed);
\t\t}
\t}

\tprotected Char zapUser() {
\t\treturn coHeroUser != null ? coHeroUser : curUser;
\t}

\tpublic boolean coHeroCanZap(Char owner) {
\t\treturn owner != null
\t\t\t\t&& owner.buff(MagicImmune.class) == null
\t\t\t\t&& isIdentified()
\t\t\t\t&& !cursed
\t\t\t\t&& curCharges >= chargesPerCast();
\t}

\tpublic int coHeroCollisionPos(Char owner, int target) {
\t\tif (owner == null) {
\t\t\tthrow new IllegalArgumentException("CoHero wand targeting requires an owner");
\t\t}
\t\treturn new Ballistica(owner.pos, target, collisionProperties(target)).collisionPos;
\t}

\tpublic void coHeroZap(Char owner, int target) {
\t\tif (!coHeroCanZap(owner)) {
\t\t\tthrow new IllegalStateException("CoHero attempted to use an unavailable wand");
\t\t}

\t\tBallistica bolt = new Ballistica(owner.pos, target, collisionProperties(target));
\t\tif (bolt.collisionPos != target) {
\t\t\tthrow new IllegalStateException("CoHero wand target is no longer reachable");
\t\t}

\t\tcoHeroUser = owner;
\t\ttry {
\t\t\tonZap(bolt);
\t\t\tcurCharges -= chargesPerCast();

\t\t\tWandOfMagicMissile.MagicCharge magicCharge = owner.buff(WandOfMagicMissile.MagicCharge.class);
\t\t\tif (magicCharge != null
\t\t\t\t\t&& magicCharge.wandJustApplied() != this
\t\t\t\t\t&& magicCharge.level() == buffedLvl()
\t\t\t\t\t&& buffedLvl() > super.buffedLvl()) {
\t\t\t\tmagicCharge.detach();
\t\t\t}

\t\t\tupdateQuickslot();
\t\t} finally {
\t\t\tcoHeroUser = null;
\t\t}
\t}
"""
if wand.count(proc_old) != 1:
    raise SystemExit("expected exactly one Wand wandProc anchor")
wand = wand.replace(proc_old, proc_new, 1)

damage_old = """\tpublic int damageRoll(int lvl){
\t\tint dmg = Hero.heroDamageIntRange(min(lvl), max(lvl));
\t\tWandEmpower emp = Dungeon.hero.buff(WandEmpower.class);
\t\tif (emp != null){
\t\t\tdmg += emp.dmgBoost;
\t\t\temp.left--;
\t\t\tif (emp.left <= 0) {
\t\t\t\temp.detach();
\t\t\t}
\t\t\tSample.INSTANCE.play(Assets.Sounds.HIT_STRONG, 0.75f, 1.2f);
\t\t}
\t\treturn dmg;
\t}
"""
damage_new = """\tpublic int damageRoll(int lvl){
\t\tboolean heroCast = zapUser() == Dungeon.hero;
\t\tint dmg = heroCast
\t\t\t\t? Hero.heroDamageIntRange(min(lvl), max(lvl))
\t\t\t\t: Random.NormalIntRange(min(lvl), max(lvl));
\t\t// Hero-only RNG and WandEmpower must not leak into a CoHero cast.
\t\tif (heroCast) {
\t\t\tWandEmpower emp = Dungeon.hero.buff(WandEmpower.class);
\t\t\tif (emp != null){
\t\t\t\tdmg += emp.dmgBoost;
\t\t\t\temp.left--;
\t\t\t\tif (emp.left <= 0) {
\t\t\t\t\temp.detach();
\t\t\t\t}
\t\t\t\tSample.INSTANCE.play(Assets.Sounds.HIT_STRONG, 0.75f, 1.2f);
\t\t\t}
\t\t}
\t\treturn dmg;
\t}
"""
if damage.count(damage_old) != 1:
    raise SystemExit("expected exactly one DamageWand damageRoll anchor")
damage = damage.replace(damage_old, damage_new, 1)

random_import_anchor = "import com.watabou.noosa.audio.Sample;\n"
if damage.count(random_import_anchor) != 1:
    raise SystemExit("expected exactly one DamageWand Sample import anchor")
damage = damage.replace(
    random_import_anchor,
    random_import_anchor + "import com.watabou.utils.Random;\n",
    1,
)

magic_old = """\t\t\t//apply the magic charge buff if we have another wand in inventory of a lower level, or already have the buff
\t\t\tfor (Wand.Charger wandCharger : curUser.buffs(Wand.Charger.class)){
\t\t\t\tif (wandCharger.wand().buffedLvl() < buffedLvl() || curUser.buff(MagicCharge.class) != null){
\t\t\t\t\tBuff.prolong(curUser, MagicCharge.class, MagicCharge.DURATION).setup(this);
\t\t\t\t\tbreak;
\t\t\t\t}
\t\t\t}
"""
magic_new = """\t\t\t//apply the magic charge buff if we have another wand in inventory of a lower level, or already have the buff
\t\t\tChar user = zapUser();
\t\t\tfor (Wand.Charger wandCharger : user.buffs(Wand.Charger.class)){
\t\t\t\tif (wandCharger.wand().buffedLvl() < buffedLvl() || user.buff(MagicCharge.class) != null){
\t\t\t\t\tBuff.prolong(user, MagicCharge.class, MagicCharge.DURATION).setup(this);
\t\t\t\t\tbreak;
\t\t\t\t}
\t\t\t}
"""
if magic.count(magic_old) != 1:
    raise SystemExit("expected exactly one MagicMissile user anchor")
magic = magic.replace(magic_old, magic_new, 1)

wand_path.write_text(wand, encoding="utf-8")
damage_path.write_text(damage, encoding="utf-8")
magic_path.write_text(magic, encoding="utf-8")
print(f"patched {wand_path}")
print(f"patched {damage_path}")
print(f"patched {magic_path}")
