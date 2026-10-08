#!/usr/bin/env python3
from pathlib import Path
import re
import sys

from java_patch import java_source

from java_patch import (
    JavaPatchError,
    find_class,
    find_method,
    insert_before,
    replace_literal_count,
    replace_regex_once,
    require_regex_count,
    require_token_count,
)

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_wand_warding.py <WandOfWarding.java>")

path = Path(sys.argv[1])
warding = java_source(path.read_text(encoding="utf-8"))

BUDGET_HELPERS = """\
\tprivate int currentWardEnergy(boolean coHeroOwned) {
\t\tint energy = 0;
\t\tfor (Char ch : Actor.chars()) {
\t\t\tif (ch instanceof Ward && ((Ward) ch).coHeroOwned() == coHeroOwned) {
\t\t\t\tenergy += ((Ward) ch).tier;
\t\t\t}
\t\t}

\t\tif (Stasis.getStasisAlly() instanceof Ward
\t\t\t\t&& ((Ward) Stasis.getStasisAlly()).coHeroOwned() == coHeroOwned) {
\t\t\tenergy += ((Ward) Stasis.getStasisAlly()).tier;
\t\t}
\t\treturn energy;
\t}

\tprivate int maxWardEnergy(Char owner) {
\t\tint max = 0;
\t\tfor (Buff buff : owner.buffs()) {
\t\t\tif (buff instanceof Wand.Charger
\t\t\t\t\t&& ((Charger) buff).wand() instanceof WandOfWarding) {
\t\t\t\tmax += 2 + ((Charger) buff).wand().level();
\t\t\t}
\t\t}
\t\treturn max;
\t}

\tprivate boolean wardBudgetAllows(Char owner, int target, boolean coHeroOwned, boolean logFailure) {
\t\tint current = currentWardEnergy(coHeroOwned);
\t\tint max = maxWardEnergy(owner);
\t\twardAvailable = current < max;

\t\tChar ch = Actor.findChar(target);
\t\tif (ch instanceof Ward) {
\t\t\tWard ward = (Ward) ch;
\t\t\tif (ward.coHeroOwned() != coHeroOwned) {
\t\t\t\tif (logFailure) GLog.w(Messages.get(this, "bad_location"));
\t\t\t\treturn false;
\t\t\t}
\t\t\tif (!wardAvailable && ward.tier <= 3) {
\t\t\t\tif (logFailure) GLog.w(Messages.get(this, "no_more_wards"));
\t\t\t\treturn false;
\t\t\t}
\t\t} else if ((current + 1) > max) {
\t\t\tif (logFailure) GLog.w(Messages.get(this, "no_more_wards"));
\t\t\treturn false;
\t\t}
\t\treturn true;
\t}

\tpublic int coHeroCurrentWardEnergy(Char owner) {
\t\treturn currentWardEnergy(true);
\t}

\tpublic int coHeroMaxWardEnergy(Char owner) {
\t\treturn maxWardEnergy(owner);
\t}

\tpublic boolean coHeroWouldIncreaseWardEnergy(Char owner, int target) {
\t\tif (!coHeroCanZap(owner) || !wardBudgetAllows(owner, target, true, false)) {
\t\t\treturn false;
\t\t}
\t\tChar ch = Actor.findChar(target);
\t\tif (ch instanceof Ward) {
\t\t\tWard ward = (Ward) ch;
\t\t\treturn ward.coHeroOwned() && wardAvailable && ward.tier < 6;
\t\t}
\t\treturn true;
\t}

\t@Override
\tprotected void coHeroPrepareZap(Char owner, int target, Ballistica bolt) {
\t\tif (!wardBudgetAllows(owner, target, true, false)) {
\t\t\tthrow new IllegalStateException("CoHero attempted an invalid ward cast");
\t\t}
\t}

"""

try:
    if "wardBudgetAllows(" in warding or "coHeroOwned = coHeroCasting()" in warding:
        raise JavaPatchError("CoHero Warding integration is already present")

    outer = find_class(warding, "WandOfWarding")
    try_to_zap = find_method(warding, "tryToZap", ("Hero", "int"), outer)
    original_try_body = warding[try_to_zap.body_start:try_to_zap.body_end]

    # These are semantic prerequisites, not a full-source snapshot. Formatting, comments and
    # unrelated prefix guards may change without invalidating the patch.
    budget_start_pattern = r"\bint\s+currentWardEnergy\s*=\s*0\s*;"
    for pattern, label in (
            (budget_start_pattern, "current ward-energy accumulator"),
            (r"\bint\s+maxWardEnergy\s*=\s*0\s*;", "maximum ward-energy accumulator"),
            (r"\bwardAvailable\s*=\s*\(\s*currentWardEnergy\s*<\s*maxWardEnergy\s*\)\s*;",
             "ward-availability assignment")):
        require_regex_count(
            warding, try_to_zap, pattern, 1, label, flags=re.MULTILINE | re.DOTALL)

    budget_start_match = re.search(budget_start_pattern, original_try_body)
    if budget_start_match is None:
        raise JavaPatchError("Warding budget start disappeared after prerequisite validation")

    budget_region = original_try_body[budget_start_match.start():]
    terminal_return_pattern = (
        r"\breturn\s+super\.tryToZap\s*\(\s*owner\s*,\s*target\s*\)\s*;"
    )
    terminal_returns = re.findall(
        terminal_return_pattern, budget_region, flags=re.MULTILINE | re.DOTALL)
    if len(terminal_returns) != 1:
        raise JavaPatchError(
            "expected exactly one terminal stock tryToZap return in the Warding budget region, "
            f"found {len(terminal_returns)}"
        )

    had_cursed_guard = re.search(
        r"\bif\s*\(\s*cursed\s*\)",
        original_try_body[:budget_start_match.start()],
    ) is not None

    # Keep any upstream preconditions at the start of tryToZap (for example Shattered v4.0.2's
    # cursed-wand early return) and replace only the stock ward-budget region.
    warding = insert_before(warding, try_to_zap, BUDGET_HELPERS)
    outer = find_class(warding, "WandOfWarding")
    try_to_zap = find_method(warding, "tryToZap", ("Hero", "int"), outer)
    warding = replace_regex_once(
        warding,
        try_to_zap,
        r"(?m)^[ \t]*int\s+currentWardEnergy\s*=\s*0\s*;"
        r".*?"
        r"^[ \t]*return\s+super\.tryToZap\s*\(\s*owner\s*,\s*target\s*\)\s*;",
        "\t\treturn wardBudgetAllows(owner, target, false, true)"
        " && super.tryToZap(owner, target);",
        "stock Warding budget region",
    )

    outer = find_class(warding, "WandOfWarding")
    try_to_zap = find_method(warding, "tryToZap", ("Hero", "int"), outer)
    if had_cursed_guard:
        require_regex_count(
            warding, try_to_zap, r"\bif\s*\(\s*cursed\s*\)",
            1, "preserved cursed-Warding guard")

    on_zap = find_method(warding, "onZap", ("Ballistica",), outer)
    warding = replace_regex_once(
        warding,
        on_zap,
        r"(?m)^(?P<i>[ \t]*)if\s*\(\s*ch\s+instanceof\s+Ward\s*\)\s*\{\s*$",
        r"""\g<i>if (ch instanceof Ward){
\g<i>\tif (((Ward) ch).coHeroOwned() != coHeroCasting()) {
\g<i>\t\tGLog.w(Messages.get(this, "bad_location"));
\g<i>\t\tDungeon.level.pressCell(target);
\g<i>\t\treturn;
\g<i>\t}""",
        "Warding ownership target",
    )

    outer = find_class(warding, "WandOfWarding")
    on_zap = find_method(warding, "onZap", ("Ballistica",), outer)
    warding = replace_regex_once(
        warding,
        on_zap,
        r"(?m)^(?P<i>[ \t]*)ward\.wandLevel\s*=\s*buffedLvl\s*\(\s*\)\s*;\s*$",
        r"""\g<i>ward.wandLevel = buffedLvl();
\g<i>ward.coHeroOwned = coHeroCasting();""",
        "Warding ownership create",
    )

    outer = find_class(warding, "WandOfWarding")
    fx = find_method(warding, "fx", ("Ballistica", "Callback"), outer)
    fx_body = warding[fx.body_start:fx.body_end]
    if "Char user = zapUser();" in fx_body:
        raise JavaPatchError("Warding fx caster integration is already present")
    warding = (
        warding[:fx.body_start]
        + "\n\t\tChar user = zapUser();"
        + warding[fx.body_start:]
    )
    outer = find_class(warding, "WandOfWarding")
    fx = find_method(warding, "fx", ("Ballistica", "Callback"), outer)
    warding = replace_literal_count(
        warding, fx, "curUser.sprite", "user.sprite", 2, "Warding fx curUser.sprite")

    outer = find_class(warding, "WandOfWarding")
    ward_class = find_class(warding, "Ward", outer)
    warding = replace_regex_once(
        warding,
        ward_class,
        r"(?m)^(?P<i>[ \t]*)public\s+int\s+totalZaps\s*=\s*0\s*;\s*$",
        r"""\g<i>public int totalZaps = 0;
\g<i>private boolean coHeroOwned = false;

\g<i>public boolean coHeroOwned() {
\g<i>\treturn coHeroOwned;
\g<i>}

\g<i>public boolean coHeroDismiss(Char owner) {
\g<i>\tif (!(owner instanceof com.spd.cohero.CoHeroAlly)
\g<i>\t\t\t|| !coHeroOwned
\g<i>\t\t\t|| !isAlive()
\g<i>\t\t\t|| !Dungeon.level.adjacent(owner.pos, pos)) {
\g<i>\t\treturn false;
\g<i>\t}
\g<i>\tdie(null);
\g<i>\treturn true;
\g<i>}""",
        "Warding ownership field",
    )

    outer = find_class(warding, "WandOfWarding")
    ward_class = find_class(warding, "Ward", outer)
    warding = replace_regex_once(
        warding,
        ward_class,
        r'(?m)^(?P<i>[ \t]*)private\s+static\s+final\s+String\s+TOTAL_ZAPS\s*=\s*"total_zaps"\s*;\s*$',
        r"""\g<i>private static final String TOTAL_ZAPS = "total_zaps";
\g<i>private static final String COHERO_OWNED = "cohero_owned";""",
        "Warding ownership key",
    )

    outer = find_class(warding, "WandOfWarding")
    ward_class = find_class(warding, "Ward", outer)
    store = find_method(warding, "storeInBundle", ("Bundle",), ward_class)
    warding = replace_regex_once(
        warding,
        store,
        r"(?m)^(?P<i>[ \t]*)bundle\.put\s*\(\s*TOTAL_ZAPS\s*,\s*totalZaps\s*\)\s*;\s*$",
        r"""\g<i>bundle.put(TOTAL_ZAPS, totalZaps);
\g<i>bundle.put(COHERO_OWNED, coHeroOwned);""",
        "Warding ownership store",
    )

    outer = find_class(warding, "WandOfWarding")
    ward_class = find_class(warding, "Ward", outer)
    restore = find_method(warding, "restoreFromBundle", ("Bundle",), ward_class)
    warding = replace_regex_once(
        warding,
        restore,
        r"(?m)^(?P<i>[ \t]*)totalZaps\s*=\s*bundle\.getInt\s*\(\s*TOTAL_ZAPS\s*\)\s*;\s*$",
        r"""\g<i>totalZaps = bundle.getInt(TOTAL_ZAPS);
\g<i>coHeroOwned = bundle.getBoolean(COHERO_OWNED);""",
        "Warding ownership restore",
    )

    # Postconditions assert the actual integration contract rather than the old source spelling.
    for token, count, label in (
            ("private boolean wardBudgetAllows(", 1, "ward-budget helper"),
            ("return wardBudgetAllows(owner, target, false, true)", 1, "Hero ward-budget route"),
            ("ward.coHeroOwned = coHeroCasting();", 1, "ward ownership assignment"),
            ("private boolean coHeroOwned = false;", 1, "ward ownership field"),
            ('private static final String COHERO_OWNED = "cohero_owned";', 1, "ward ownership key"),
            ("bundle.put(COHERO_OWNED, coHeroOwned);", 1, "ward ownership persistence"),
            ("coHeroOwned = bundle.getBoolean(COHERO_OWNED);", 1, "ward ownership restore"),
            ("Char user = zapUser();", 1, "Warding fx caster")):
        require_token_count(warding, token, count, label)

except JavaPatchError as error:
    raise SystemExit(str(error))

path.write_text(warding, encoding="utf-8")
print(f"patched {path}")
