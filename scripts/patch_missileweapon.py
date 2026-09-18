#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_missileweapon.py <MissileWeapon.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

field_anchor = "\tpublic MissileWeapon parent;\n"
field_patch = field_anchor + "\n\t// Non-Hero owner context used only while CoHero resolves a thrown attack.\n\tprivate transient Char coHeroUser;\n"
if "private transient Char coHeroUser;" in text:
    raise SystemExit("CoHero MissileWeapon seam is already present")
if text.count(field_anchor) != 1:
    raise SystemExit("expected exactly one MissileWeapon parent anchor")
text = text.replace(field_anchor, field_patch, 1)

damage_old = """\t@Override
\tpublic int damageRoll(Char owner) {
\t\tint damage = augment.damageFactor(super.damageRoll( owner ));
\t\t
\t\tif (owner instanceof Hero) {
"""
damage_new = """\t@Override
\tpublic int damageRoll(Char owner) {
\t\tint baseDamage;
\t\tif (owner instanceof Hero) {
\t\t\tbaseDamage = super.damageRoll(owner);
\t\t} else {
\t\t\t// MissileWeapon.min()/max() include Dungeon.hero's Sharpshooting ring.
\t\t\t// Non-Hero owners must use only the projectile's own level here.
\t\t\tint level = buffedLvl() + RingOfSharpshooting.levelDamageBonus(owner);
\t\t\tbaseDamage = Random.NormalIntRange(
\t\t\t\t\tMath.max(0, min(level)),
\t\t\t\t\tMath.max(0, max(level)));
\t\t}
\t\tint damage = augment.damageFactor(baseDamage);
\t\t
\t\tif (owner instanceof Hero) {
"""
if text.count(damage_old) != 1:
    raise SystemExit("expected exactly one MissileWeapon damageRoll anchor")
text = text.replace(damage_old, damage_new, 1)

durable_old = """\t\tif (Dungeon.hero != null && Dungeon.hero.hasTalent(Talent.DURABLE_PROJECTILES)){
\t\t\tusages *= 1.25f + (0.25f*Dungeon.hero.pointsInTalent(Talent.DURABLE_PROJECTILES));
\t\t}
"""
durable_new = """\t\tif (coHeroUser == null && Dungeon.hero != null && Dungeon.hero.hasTalent(Talent.DURABLE_PROJECTILES)){
\t\t\tusages *= 1.25f + (0.25f*Dungeon.hero.pointsInTalent(Talent.DURABLE_PROJECTILES));
\t\t}
"""
if text.count(durable_old) != 1:
    raise SystemExit("expected exactly one durable projectiles anchor")
text = text.replace(durable_old, durable_new, 1)

sharp_old = """\t\tif (Dungeon.hero != null) {
\t\t\tusages *= RingOfSharpshooting.durabilityMultiplier( Dungeon.hero );
\t\t}
"""
sharp_new = """\t\tif (coHeroUser != null) {
\t\t\tusages *= RingOfSharpshooting.durabilityMultiplier(coHeroUser);
\t\t} else if (Dungeon.hero != null) {
\t\t\tusages *= RingOfSharpshooting.durabilityMultiplier( Dungeon.hero );
\t\t}
"""
if text.count(sharp_old) != 1:
    raise SystemExit("expected exactly one Sharpshooting durability anchor")
text = text.replace(sharp_old, sharp_new, 1)

method_anchor = """\tpublic int defaultQuantity(){
\t\treturn 3;
\t}
"""
method_patch = """\tpublic boolean coHeroResolveThrow(Char owner, Char enemy, boolean hit) {
\t\tif (owner == null || enemy == null) {
\t\t\tthrow new IllegalArgumentException("CoHero throw requires owner and enemy");
\t\t}

\t\tMissileWeapon sourceParent = parent;
\t\tcoHeroUser = owner;
\t\tif (sourceParent != null) sourceParent.coHeroUser = owner;
\t\ttry {
\t\t\tif (hit) {
\t\t\t\trangedHit(enemy, enemy.pos);
\t\t\t} else {
\t\t\t\trangedMiss(enemy.pos);
\t\t\t}
\t\t\treturn durability > 0;
\t\t} finally {
\t\t\tcoHeroUser = null;
\t\t\tif (sourceParent != null) sourceParent.coHeroUser = null;
\t\t}
\t}

""" + method_anchor
if text.count(method_anchor) != 1:
    raise SystemExit("expected exactly one defaultQuantity anchor")
text = text.replace(method_anchor, method_patch, 1)

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
