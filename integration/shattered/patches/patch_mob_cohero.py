#!/usr/bin/env python3
from pathlib import Path
import re
import sys

from java_patch import (
    JavaPatchError,
    find_class,
    find_method,
    insert_after,
    replace_regex_count,
    replace_regex_once,
    require_regex_count,
    require_token_count,
)

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_mob_cohero.py <Mob.java>")

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

PROBE_METHODS = """

\t/**
\t * CoHero tactical probe. Evaluates the mob's real overridden canAttack() semantics from a
\t * hypothetical source cell, then restores the live position immediately.
\t */
\tpublic boolean coHeroCanAttackFrom(int sourcePos, Char enemy) {
\t\tif (enemy == null || !Dungeon.level.insideMap(sourcePos)) {
\t\t\treturn false;
\t\t}
\t\tint livePos = pos;
\t\ttry {
\t\t\tpos = sourcePos;
\t\t\treturn canAttack(enemy);
\t\t} finally {
\t\t\tpos = livePos;
\t\t}
\t}

\t/**
\t * CoHero ranged-damage probe. Standard non-adjacent attacks use damageRoll(), so that is the
\t * default. Mobs with a distinct ranged attack override this method. A negative value means the
\t * ranged effect has no directly comparable damage estimate.
\t */
\tpublic int coHeroRangedDamageRoll(Char enemy) {
\t\treturn damageRoll();
\t}

\t/**
\t * CoHero strategic relationship seam. Returns true only when killing this mob immediately
\t * removes the supplied dependent enemy as part of the mob's own death semantics.
\t */
\tpublic boolean coHeroDeathRemoves(Mob dependent) {
\t\treturn false;
\t}

\t/**
\t * CoHero-only surprise semantics. This deliberately does not feed Mob.surprisedBy(), because
\t * the stock path also records Hero sneak-attack statistics and Hero-specific surprise effects.
\t */
\tpublic boolean coHeroSurprisedBy(Char attacker) {
\t\treturn attacker instanceof com.spd.cohero.CoHeroAlly
\t\t\t\t&& (attacker.invisible > 0
\t\t\t\t\t|| !enemySeen
\t\t\t\t\t|| (fieldOfView != null
\t\t\t\t\t\t&& fieldOfView.length == Dungeon.level.length()
\t\t\t\t\t\t&& !fieldOfView[attacker.pos]))
\t\t\t\t&& attacker.canSurpriseAttack();
\t}

\t/**
\t * CoHero navigation probe for sleeping-enemy wake risk. Uses the mob's live SLEEPING
\t * implementation, including stealth-gameplay overrides, at a hypothetical hostile cell.
\t * The hostile position is restored before returning.
\t */
\tpublic float coHeroSleepingDetectionChanceAt(Char hostile, int hostilePos) {
\t\tif (hostile == null
\t\t\t\t|| state != SLEEPING
\t\t\t\t|| hostile.invisible > 0
\t\t\t\t|| fieldOfView == null
\t\t\t\t|| hostilePos < 0
\t\t\t\t|| hostilePos >= fieldOfView.length
\t\t\t\t|| !fieldOfView[hostilePos]) {
\t\t\treturn 0f;
\t\t}
\t\tif (!(SLEEPING instanceof Sleeping)) {
\t\t\tthrow new IllegalStateException("Mob SLEEPING state is not a Sleeping implementation");
\t\t}

\t\tint livePos = hostile.pos;
\t\ttry {
\t\t\thostile.pos = hostilePos;
\t\t\tif (hostile.flying && distance(hostile) >= 2) {
\t\t\t\treturn 0f;
\t\t\t}
\t\t\treturn Math.max(0f, ((Sleeping) SLEEPING).detectionChance(hostile));
\t\t} finally {
\t\t\thostile.pos = livePos;
\t\t}
\t}

\t/**
\t * Sleeping AI normally enters its hostile scan only when chooseEnemy() produced a visible
\t * target. A sleeping mob can retain a stale Hero target, which makes that gate false even when
\t * CoHero is standing in its FOV. This helper repairs only that gate; the stock Sleeping logic
\t * still owns stealth, invisibility, flying, distance, RNG and the actual awaken transition.
\t */
\tprivate boolean coHeroHostileInFOV() {
\t\tif (fieldOfView == null) {
\t\t\treturn false;
\t\t}
\t\tfor (Char ch : Actor.chars()) {
\t\t\tif (ch instanceof com.spd.cohero.CoHeroAlly
\t\t\t\t\t&& ch.isAlive()
\t\t\t\t\t&& ch.invisible <= 0
\t\t\t\t\t&& ch.alignment != alignment
\t\t\t\t\t&& ch.alignment != Alignment.NEUTRAL
\t\t\t\t\t&& ch.pos >= 0
\t\t\t\t\t&& ch.pos < fieldOfView.length
\t\t\t\t\t&& fieldOfView[ch.pos]) {
\t\t\t\treturn true;
\t\t\t}
\t\t}
\t\treturn false;
\t}
"""

try:
    if "coHeroCanAttackFrom(" in text:
        raise JavaPatchError("CoHero Mob integration is already present")

    mob = find_class(text, "Mob")
    can_attack = find_method(text, "canAttack", ("Char",), mob)

    # Preserve the host's actual canAttack implementation and attach CoHero probes after it.
    text = insert_after(text, can_attack, PROBE_METHODS)

    mob = find_class(text, "Mob")
    sleeping = find_class(text, "Sleeping", mob)
    sleeping_act = find_method(text, "act", ("boolean", "boolean"), sleeping)

    text = replace_regex_once(
        text,
        sleeping_act,
        r"if\s*\(\s*enemyInFOV\s*\|\|\s*"
        r"\(\s*enemy\s*!=\s*null\s*&&\s*enemy\.invisible\s*>\s*0\s*\)\s*\)\s*\{",
        """if (enemyInFOV
\t\t\t\t\t|| (enemy != null && enemy.invisible > 0)
\t\t\t\t\t|| coHeroHostileInFOV()) {""",
        "Sleeping wake gate",
    )

    mob = find_class(text, "Mob")
    sleeping = find_class(text, "Sleeping", mob)
    sleeping_act = find_method(text, "act", ("boolean", "boolean"), sleeping)
    text = replace_regex_once(
        text,
        sleeping_act,
        r"(?m)^(?P<i>[ \t]*)float\s+highestChance\s*=\s*Float\.POSITIVE_INFINITY\s*;\s*$",
        r"\g<i>float highestChance = 0f;",
        "Sleeping highest detection chance initializer",
    )

    mob = find_class(text, "Mob")
    sleeping = find_class(text, "Sleeping", mob)
    sleeping_act = find_method(text, "act", ("boolean", "boolean"), sleeping)
    text = replace_regex_once(
        text,
        sleeping_act,
        r"(?m)^(?P<i>[ \t]*)Char\s+closestHostile\s*=\s*null\s*;\s*$",
        r"\g<i>Char easiestHostileToDetect = null;",
        "Sleeping hostile candidate",
    )

    mob = find_class(text, "Mob")
    sleeping = find_class(text, "Sleeping", mob)
    sleeping_act = find_method(text, "act", ("boolean", "boolean"), sleeping)
    text = replace_regex_count(
        text,
        sleeping_act,
        r"bestChance\s*=\s*Float\.POSITIVE_INFINITY\s*;",
        "bestChance = 0f;",
        2,
        "Sleeping stealth exclusion",
    )

    mob = find_class(text, "Mob")
    sleeping = find_class(text, "Sleeping", mob)
    sleeping_act = find_method(text, "act", ("boolean", "boolean"), sleeping)
    text = replace_regex_once(
        text,
        sleeping_act,
        r"if\s*\(\s*bestChance\s*<\s*highestChance\s*\)\s*\{"
        r"\s*highestChance\s*=\s*bestChance\s*;"
        r"\s*closestHostile\s*=\s*ch\s*;"
        r"\s*\}",
        """if (bestChance > highestChance){
\t\t\t\t\t\t\thighestChance = bestChance;
\t\t\t\t\t\t\teasiestHostileToDetect = ch;
\t\t\t\t\t\t}""",
        "Sleeping easiest-hostile selection",
    )

    mob = find_class(text, "Mob")
    sleeping = find_class(text, "Sleeping", mob)
    sleeping_act = find_method(text, "act", ("boolean", "boolean"), sleeping)
    text = replace_regex_once(
        text,
        sleeping_act,
        r"if\s*\(\s*closestHostile\s*!=\s*null\s*"
        r"&&\s*Random\.Float\s*\(\s*\)\s*<\s*"
        r"detectionChance\s*\(\s*closestHostile\s*\)\s*\)",
        "if (easiestHostileToDetect != null && Random.Float() < highestChance)",
        "Sleeping wake roll",
    )

    mob = find_class(text, "Mob")
    do_attack = find_method(text, "doAttack", ("Char",), mob)
    text = replace_regex_once(
        text,
        do_attack,
        r"(?m)^(?P<i>[ \t]*)attack\s*\(\s*enemy\s*\)\s*;\s*$",
        r"""\g<i>com.spd.cohero.CoHeroRemoteView.attack(this, enemy.pos);
\g<i>attack( enemy );""",
        "Mob remote attack resolution",
    )

    mob = find_class(text, "Mob")
    defense_skill = find_method(text, "defenseSkill", ("Char",), mob)
    text = replace_regex_once(
        text,
        defense_skill,
        r"!\s*surprisedBy\s*\(\s*enemy\s*\)\s*"
        r"&&\s*paralysed\s*==\s*0",
        """!surprisedBy(enemy)
\t\t\t\t&& !coHeroSurprisedBy(enemy)
\t\t\t\t&& paralysed == 0""",
        "Mob CoHero surprise defense",
    )

    mob = find_class(text, "Mob")
    hold_allies = find_method(text, "holdAllies", ("Level", "int"), mob)
    text = replace_regex_once(
        text,
        hold_allies,
        r"(?m)^(?P<i>[ \t]*)for\s*\(\s*Mob\s+mob\s*:\s*"
        r"level\.mobs\.toArray\s*\(\s*new\s+Mob\s*\[\s*0\s*\]\s*\)\s*\)\s*\{\s*$",
        r"""\g<i>for (Mob mob : level.mobs.toArray( new Mob[0] )) {
\g<i>\t// CoHero owns its own cross-floor lifecycle/state and must never enter the stock
\g<i>\t// heldAllies transport, otherwise special-floor exclusion can be bypassed.
\g<i>\tif (mob instanceof com.spd.cohero.CoHeroAlly) {
\g<i>\t\tcontinue;
\g<i>\t}""",
        "Mob held-allies exclusion",
    )

    # Postconditions validate the resulting behavior contract.
    for token, count, label in (
            ("public boolean coHeroCanAttackFrom(", 1, "Mob attack probe"),
            ("public int coHeroRangedDamageRoll(", 1, "Mob ranged-damage probe"),
            ("public boolean coHeroDeathRemoves(", 1, "Mob dependent-death probe"),
            ("public float coHeroSleepingDetectionChanceAt(", 1, "Mob sleeping detection probe"),
            ("com.spd.cohero.CoHeroRemoteView.attack(this, enemy.pos);", 1, "remote attack hook"),
            ("&& !coHeroSurprisedBy(enemy)", 1, "CoHero surprise defense"),
            ("if (mob instanceof com.spd.cohero.CoHeroAlly)", 1, "held-allies exclusion")):
        require_token_count(text, token, count, label)

    mob = find_class(text, "Mob")
    sleeping = find_class(text, "Sleeping", mob)
    sleeping_act = find_method(text, "act", ("boolean", "boolean"), sleeping)
    for pattern, expected, label in (
            (r"\bcoHeroHostileInFOV\s*\(\s*\)", 1, "Sleeping CoHero FOV gate"),
            (r"\bChar\s+easiestHostileToDetect\s*=\s*null\s*;", 1,
             "Sleeping easiest-hostile candidate"),
            (r"bestChance\s*>\s*highestChance", 1, "Sleeping maximum-detection selection"),
            (r"Random\.Float\s*\(\s*\)\s*<\s*highestChance", 1,
             "Sleeping wake probability")):
        require_regex_count(text, sleeping_act, pattern, expected, label)

    require_regex_count(
        text, sleeping_act, r"\bclosestHostile\b", 0, "obsolete closest-hostile selection")

except JavaPatchError as error:
    raise SystemExit(str(error))

path.write_text(text, encoding="utf-8")
print(f"patched {path}")
