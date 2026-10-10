#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys
import tempfile

if len(sys.argv) != 2:
    raise SystemExit("usage: test_java_patch.py <repo-root>")

root = Path(sys.argv[1]).resolve()
patch_dir = root / "integration" / "shattered" / "patches"
sys.path.insert(0, str(patch_dir))

from java_patch import (  # noqa: E402
    JavaPatchError,
    find_class,
    find_method,
    replace_regex_once,
    replace_code_once,
    java_source,
)


def test_helper():
    source = r'''
class Sample {
    String braces = "{ not code }";
    String block = """
        class Fake {
            void fake() { }
        }
    """;

    // class FakeComment { void fake() {} }
    /* } { class FakeBlock {} */

    @Override
    public boolean target(Hero hero, int cell) {
        if (cell > 0) {
            return true;
        }
        return false;
    }

    public boolean target(Hero hero) {
        return false;
    }

    class Nested {
        public void target() {
            String close = "}";
        }
    }
}
'''

    outer = find_class(source, "Sample")
    method = find_method(source, "target", ("Hero", "int"), outer)
    nested = find_class(source, "Nested", outer)
    nested_method = find_method(source, "target", (), nested)

    if "return true;" not in source[method.body_start:method.body_end]:
        raise AssertionError("java_patch method scope did not contain the expected body")
    if 'String close = "}";' not in source[nested_method.body_start:nested_method.body_end]:
        raise AssertionError("java_patch nested method scope was not resolved correctly")

    patched = replace_regex_once(
        source,
        method,
        r"return\s+true\s*;",
        "return false;",
        "target return",
    )
    if patched.count("return false;") != 3:
        raise AssertionError("method-scoped replacement escaped its selected method")

    try:
        find_method(source, "target", None, outer)
    except JavaPatchError:
        pass
    else:
        raise AssertionError("ambiguous structural method lookup must fail fast")

    try:
        replace_regex_once(source, method, r"doesNotExist", "x", "missing semantic anchor")
    except JavaPatchError:
        pass
    else:
        raise AssertionError("missing scoped semantic anchor must fail fast")

    token_source = """class TokenFixture {
        void f() {
            int value = 1; // upstream comment
            value += 2;
        }
    }"""
    token_anchor = """void f(){
        int value=1;
        value += 2;
    }"""
    token_replacement = """void f() {
        int value = 3;
    }"""
    token_patched = replace_code_once(
        token_source, token_anchor, token_replacement, "token fallback fixture")
    if "int value = 3;" not in token_patched or "value += 2;" in token_patched:
        raise AssertionError("token-aware fallback did not tolerate whitespace/comment drift")

    try:
        replace_code_once(
            "class A { int x; int x; }",
            "int x;",
            "int y;",
            "ambiguous token anchor",
        )
    except JavaPatchError:
        pass
    else:
        raise AssertionError("ambiguous token-equivalent anchors must fail fast")

    wrapped = java_source(token_source)
    if wrapped.count(token_anchor) != 1:
        raise AssertionError("JavaSource.count did not use token-aware fallback")
    if token_anchor not in wrapped:
        raise AssertionError("JavaSource.__contains__ did not use token-aware fallback")
    if wrapped.index(token_anchor) < 0:
        raise AssertionError("JavaSource.index did not use token-aware fallback")
    wrapped_slice = wrapped[:]
    if type(wrapped_slice).__name__ != "JavaSource":
        raise AssertionError("JavaSource slicing must preserve resilient matching")
    wrapped_patched = wrapped.replace(token_anchor, token_replacement, 1)
    if "int value = 3;" not in wrapped_patched:
        raise AssertionError("JavaSource.replace did not use token-aware fallback")
    if java_source("class A { int x = 1; }").count("int x = 2;") != 0:
        raise AssertionError("token-aware fallback must not equate changed Java tokens")

    insertion_old = """void f() {
        int first = 1;
        int last = 2;
    }"""
    insertion_source = """class InsertFixture {
        void f() {
            int first = 1;
            int upstream = 99;
            int last = 2;
        }
    }"""
    insertion_new = insertion_old + "\nvoid coHeroHook() {}\n"
    insertion_patched = java_source(insertion_source).replace(
        insertion_old, insertion_new, 1)
    if "int upstream = 99;" not in insertion_patched or "void coHeroHook()" not in insertion_patched:
        raise AssertionError("safe insertion fallback must preserve unrelated upstream statements")


WARDING_TEMPLATE = r"""
class WandOfWarding {
    private boolean wardAvailable = true;

    public boolean tryToZap(Hero owner, int target) {
__CURSED_GUARD__
        int currentWardEnergy = 0;
        for (Char ch : Actor.chars()){
            if (ch instanceof Ward){
                currentWardEnergy += ((Ward) ch).tier;
            }
        }
        if (Stasis.getStasisAlly() instanceof Ward){
            currentWardEnergy += ((Ward) Stasis.getStasisAlly()).tier;
        }

        int maxWardEnergy = 0;
        for (Buff buff : curUser.buffs()){
            if (buff instanceof Wand.Charger){
                if (((Charger) buff).wand() instanceof WandOfWarding){
                    maxWardEnergy += 2 + ((Charger) buff).wand().level();
                }
            }
        }

        wardAvailable = (currentWardEnergy < maxWardEnergy);
        Char ch = Actor.findChar(target);
        if (ch instanceof Ward){
            if (!wardAvailable && ((Ward) ch).tier <= 3){
                GLog.w(Messages.get(this, "no_more_wards"));
                return false;
            }
        } else if ((currentWardEnergy + 1) > maxWardEnergy) {
            GLog.w(Messages.get(this, "no_more_wards"));
            return false;
        }

        return super.tryToZap(owner, target);
    }

    public void onZap(Ballistica bolt) {
        int target = bolt.collisionPos;
        Char ch = Actor.findChar(target);
        if (ch != null){
            if (ch instanceof Ward){
                if (wardAvailable) {
                    ((Ward) ch).upgrade(buffedLvl());
                }
            }
        } else {
            Ward ward = new Ward();
            ward.pos = target;
            ward.wandLevel = buffedLvl();
        }
    }

    public void fx(Ballistica bolt, Callback callback) {
        MagicMissile m = MagicMissile.boltFromChar(
                curUser.sprite.parent,
                MagicMissile.WARD,
                curUser.sprite,
                bolt.collisionPos,
                callback);
    }

    public static class Ward {
        public int totalZaps = 0;

        private static final String TOTAL_ZAPS = "total_zaps";

        public void storeInBundle(Bundle bundle) {
            bundle.put(TOTAL_ZAPS, totalZaps);
        }

        public void restoreFromBundle(Bundle bundle) {
            totalZaps = bundle.getInt(TOTAL_ZAPS);
        }
    }
}
"""


MOB_FIXTURE = r"""
class Mob {
    protected boolean canAttack(Char enemy) {
        return false;
    }

    protected class Sleeping implements AiState {
        public boolean act(boolean enemyInFOV, boolean justAlerted) {
            if (enemyInFOV || (enemy != null && enemy.invisible > 0)) {
                float detectionAggregate = Float.POSITIVE_INFINITY;
                Char candidate = null;

                for (Char ch : Actor.chars()){
                    float candidateChance = detectionChance(ch);
                    if (ch.invisible > 0) {
                        candidateChance = Float.POSITIVE_INFINITY;
                    }
                    if (ch.flying) {
                        candidateChance = Float.POSITIVE_INFINITY;
                    }
                    if (candidateChance < detectionAggregate){
                        detectionAggregate = candidateChance;
                        candidate = ch;
                    }
                }

                if (candidate != null && Random.Float() < detectionChance(candidate)) {
                    return true;
                }
            }
            return true;
        }
    }

    protected boolean doAttack(Char enemy) {
        if (sprite.visible) {
            return false;
        } else {
            attack(enemy);
            return true;
        }
    }

    public int defenseSkill(Char enemy) {
        if (!surprisedBy(enemy)
                && paralysed == 0) {
            return defenseSkill;
        }
        return 0;
    }

    public static void holdAllies(Level level, int holdFromPos) {
        for (Mob mob : level.mobs.toArray(new Mob[0])) {
            level.mobs.remove(mob);
        }
    }
}
"""


def run_patch(script_name, source):
    with tempfile.TemporaryDirectory() as tmp:
        target = Path(tmp) / "Target.java"
        target.write_text(source, encoding="utf-8")
        result = subprocess.run(
            [sys.executable, str(patch_dir / script_name), str(target)],
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            raise AssertionError(
                f"{script_name} failed on fixture:\n"
                f"stdout:\n{result.stdout}\n"
                f"stderr:\n{result.stderr}"
            )
        return target.read_text(encoding="utf-8")


def test_warding_patch():
    no_guard = WARDING_TEMPLATE.replace("__CURSED_GUARD__", "")
    patched = run_patch("patch_wand_warding.py", no_guard)
    required = (
        "private boolean wardBudgetAllows(",
        "return wardBudgetAllows(owner, target, false, true)",
        "ward.coHeroOwned = coHeroCasting();",
        'private static final String COHERO_OWNED = "cohero_owned";',
        "bundle.put(COHERO_OWNED, coHeroOwned);",
        "coHeroOwned = bundle.getBoolean(COHERO_OWNED);",
        "Char user = zapUser();",
    )
    if any(token not in patched for token in required):
        raise AssertionError("Warding structural patch missed a required postcondition")

    cursed_guard = """        if (cursed) {
            return super.tryToZap(owner, target);
        }
"""
    with_guard = WARDING_TEMPLATE.replace("__CURSED_GUARD__", cursed_guard)
    patched = run_patch("patch_wand_warding.py", with_guard)
    if cursed_guard.strip() not in patched:
        raise AssertionError("Warding structural patch did not preserve an upstream prefix guard")


def test_mob_patch():
    patched = run_patch("patch_mob_cohero.py", MOB_FIXTURE)
    required = (
        "public boolean coHeroCanAttackFrom(",
        "public float coHeroSleepingDetectionChanceAt(",
        "|| coHeroHostileInFOV())",
        "Char candidate = null;",
        "candidateChance > detectionAggregate",
        "Random.Float() < detectionAggregate",
        "com.spd.cohero.CoHeroRemoteView.attack(this, enemy.pos);",
        "&& !coHeroSurprisedBy(enemy)",
        "if (mob instanceof com.spd.cohero.CoHeroAlly)",
    )
    if any(token not in patched for token in required):
        raise AssertionError("Mob structural patch missed a required postcondition")
    if "candidateChance = Float.POSITIVE_INFINITY" in patched:
        raise AssertionError("Mob structural patch left the old stealth exclusion semantics behind")


def test_all_java_patch_scripts_use_resilient_source():
    apply_text = (root / "integration" / "shattered" / "apply.sh").read_text(encoding="utf-8")
    script_names = sorted(set(__import__("re").findall(
        r'\$patches/(patch_[A-Za-z0-9_]+\.py)', apply_text)))
    non_java = {"patch_app_package.py", "patch_android_manifest.py", "patch_messages.py"}
    java_scripts = [name for name in script_names if name not in non_java]

    if len(java_scripts) < 50:
        raise AssertionError("unexpectedly small Java patch inventory")

    for name in java_scripts:
        script = (patch_dir / name).read_text(encoding="utf-8")
        if not __import__("re").search(
                r"(?m)^from\s+java_patch\s+import\s+[^\n]*\bjava_source\b",
                script):
            raise AssertionError(f"{name} does not import java_source")
        read_lines = [line for line in script.splitlines() if ".read_text(" in line]
        if not read_lines:
            raise AssertionError(f"{name} has no Java source read to wrap")
        for line in read_lines:
            if "java_source(" not in line:
                raise AssertionError(
                    f"{name} reads Java source without java_source wrapper: {line.strip()}")



def test_non_java_patch_resilience():
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)

        gradle = tmp / "build.gradle"
        gradle.write_text(
            'ext {\n'
            '    appName   =   "Shattered Pixel Dungeon"\n'
            '    appPackageName = "com.shatteredpixel.shatteredpixeldungeon"\n'
            '}\n',
            encoding="utf-8",
        )
        subprocess.run(
            [sys.executable, str(patch_dir / "patch_app_package.py"), str(gradle)],
            check=True,
            capture_output=True,
            text=True,
        )
        gradle_text = gradle.read_text(encoding="utf-8")
        if "CoShattered Pixel Dungeon" not in gradle_text:
            raise AssertionError("Gradle identity patch did not tolerate spacing/quote drift")
        if "com.shatteredpixel.shatteredpixeldungeon.cohero" not in gradle_text:
            raise AssertionError("Gradle package patch did not tolerate spacing/quote drift")

        manifest = tmp / "AndroidManifest.xml"
        manifest.write_text(
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n'
            '  <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>\n'
            '  <application android:label="@string/app_name" />\n'
            '</manifest>\n',
            encoding="utf-8",
        )
        subprocess.run(
            [sys.executable, str(patch_dir / "patch_android_manifest.py"), str(manifest)],
            check=True,
            capture_output=True,
            text=True,
        )
        manifest_text = manifest.read_text(encoding="utf-8")
        for permission in (
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
        ):
            if manifest_text.count(permission) != 1:
                raise AssertionError(
                    f"Manifest semantic permission patch failed for {permission}"
                )

        target_dir = tmp / "target_messages"
        source_dir = tmp / "cohero_messages"
        target_dir.mkdir()
        source_dir.mkdir()
        (source_dir / "misc.properties").write_text(
            "cohero.alpha=Alpha %s\ncohero.beta=Beta %d\n",
            encoding="utf-8",
        )
        (source_dir / "misc_zh.properties").write_text(
            "cohero.beta=乙 %d\ncohero.alpha=甲 %s\n",
            encoding="utf-8",
        )
        for name in ("misc.properties", "misc_zh.properties", "misc_new.properties"):
            (target_dir / name).write_text("stock.key=Stock\n", encoding="utf-8")

        subprocess.run(
            [
                sys.executable,
                str(patch_dir / "patch_messages.py"),
                str(target_dir),
                str(source_dir),
            ],
            check=True,
            capture_output=True,
            text=True,
        )
        if "甲 %s" not in (target_dir / "misc_zh.properties").read_text(encoding="utf-8"):
            raise AssertionError("message patch rejected harmless localized key reordering")
        if "Alpha %s" not in (target_dir / "misc_new.properties").read_text(encoding="utf-8"):
            raise AssertionError("new upstream locale did not fall back to base CoHero messages")



def test_combo_attack_patch_and_catalog():
    with tempfile.TemporaryDirectory() as tmp:
        source = Path(tmp) / "Char.java"
        source.write_text(
            "class Char {\n"
            "    void attack(Char enemy, int effectiveDamage) {\n"
            "        enemy.damage( effectiveDamage, this );\n"
            "    }\n"
            "}\n", encoding="utf-8")
        command = [sys.executable, str(patch_dir / "patch_combo_attack.py"), str(source)]
        subprocess.run(command, check=True, capture_output=True, text=True)
        patched = source.read_text(encoding="utf-8")
        if patched.count("CoHeroCombo.onAttack(this, enemy, coHeroComboHpBefore)") != 1:
            raise AssertionError("Combo hook must run exactly once after an applied attack")
        if patched.index("int coHeroComboHpBefore") > patched.index("enemy.damage("):
            raise AssertionError("Combo damage hook must capture HP before damage")
        if subprocess.run(command, capture_output=True, text=True).returncode == 0:
            raise AssertionError("Re-applying combo hook must fail fast")

    # The combo patch must be part of the unattended integration pipeline.
    integration = (root / "integration/shattered/apply.sh").read_text(encoding="utf-8")
    if 'patch_combo_attack.py' not in integration:
        raise AssertionError("Combo attack hook is missing from apply.sh")

    source = (root / "core/src/main/java/com/spd/cohero/CoHeroCombo.java").read_text(
        encoding="utf-8")
    for required in (
        "MAX_ENERGY = 180",
        "CAST_COST = 60",
        "PAIR_WINDOW = 3f",
        "gain(8);",
        "gain(5);",
        "gain(4);",
        "hero.spendAndNext(Actor.TICK)",
        "companion.spendComboTurn()",
    ):
        if required not in source:
            raise AssertionError(f"Combo gameplay invariant missing: {required}")

    # A missing Link field is normal in saves from before the feature existed.
    # Keep saved values and fail fast for corrupt/out-of-range values.
    restore_start = source.index("public static void restore(Bundle bundle)")
    restore_end = source.index("public static void onLevelChanged()", restore_start)
    restore = source[restore_start:restore_end]
    if "bundle.contains(ENERGY_KEY) ? bundle.getInt(ENERGY_KEY) : 0" not in restore:
        raise AssertionError("Old saves must start with zero Link instead of failing")
    if "energy < 0 || energy > MAX_ENERGY" not in restore:
        raise AssertionError("Existing out-of-range Link values must still fail")
    if "clearTransientState()" not in restore:
        raise AssertionError("Restoring a save must discard transient combo pairing")

    wand_patch = (patch_dir / "patch_wand_base.py").read_text(encoding="utf-8")
    if wand_patch.count("CoHeroCombo.onWandZap(") != 2:
        raise AssertionError("Both Hero and CoHero Wand cast paths must score Link")
    if "onWandZap(Wand wand, Char caster," not in source:
        raise AssertionError("Wand damage window is missing")
    if "recordAttack(caster, primary, before.get(primary), true)" not in source:
        raise AssertionError("Wand cast must choose exactly one pairing candidate")
    if "recordAttack(caster, mob, before.get(mob), false)" not in source:
        raise AssertionError("Secondary wand victims must be excluded from pairing")

    hud_tag = (root / "core/src/main/java/com/spd/cohero/CoHeroComboIndicator.java").read_text(
        encoding="utf-8")
    if 'energy + "/" + CoHeroCombo.MAX_ENERGY' not in hud_tag:
        raise AssertionError("Link Tag must display current/max values")
    if "CoHeroCombo.requestCast()" not in hud_tag:
        raise AssertionError("Link Tag must initiate the ultimate")
    for required in (
        "ColorBlock[] notches = new ColorBlock[3]",
        "int charges = energy / CoHeroCombo.CAST_COST",
        "CHARGE_COLORS[charges]",
        "onLongClick()",
        "CoHeroCombo.skillDescription()",
        "count.measure()",
        "Math.min(0.68f",
    ):
        if required not in hud_tag:
            raise AssertionError(f"Three-stage ultimate Tag missing: {required}")
    if "notches[i].y = y + 3f;" not in hud_tag:
        raise AssertionError("Link charge notches must sit two pixels below their original edge")
    # The icon is a real 16x16 PNG, not rotated ColorBlocks that can bleed outside.
    if 'new Image(ICON)' not in hud_tag or 'interfaces/cohero_combo.png' not in hud_tag:
        raise AssertionError("Ultimate Tag must use its dedicated pixel-art asset")
    if any(token in hud_tag for token in ("firstSword", "secondSword", ".angle =")):
        raise AssertionError("Ultimate Tag still uses out-of-bounds rotated primitives")
    encoded_icon = root / "core/src/main/assets/interfaces/cohero_combo.png.b64"
    icon = __import__("base64").b64decode(encoded_icon.read_text(encoding="ascii").strip(),
                                          validate=True)
    if not icon.startswith(b"\x89PNG\r\n\x1a\n") or icon[16:24] != bytes.fromhex("0000001000000010"):
        raise AssertionError("Combo icon must be a valid 16x16 PNG")
    # The charge notches occupy the top of the Tag. Keep the icon's first
    # four pixel rows completely transparent so no blade/gem covers the bars.
    # A 16x16 8-bit RGBA PNG has one 65-byte filtered scanline per row.
    import zlib
    if icon[24] != 8 or icon[25] != 6:
        raise AssertionError("Combo icon must be 8-bit RGBA")
    cursor = 8
    compressed = bytearray()
    while cursor < len(icon):
        size = int.from_bytes(icon[cursor:cursor + 4], "big")
        chunk_type = icon[cursor + 4:cursor + 8]
        if chunk_type == b"IDAT":
            compressed.extend(icon[cursor + 8:cursor + 8 + size])
        cursor += size + 12
    pixels = zlib.decompress(bytes(compressed))
    if len(pixels) != 16 * 65 or any(
            pixels[y * 65 + 1:(y + 1) * 65] != bytes(64)
            for y in range(4)):
        raise AssertionError("Combo icon overlaps three top charge notches")

    integration_sh = (root / "integration/shattered/apply.sh").read_text(encoding="utf-8")
    if 'cohero_combo.png.b64' not in integration_sh or 'dest.write_bytes(png)' not in integration_sh:
        raise AssertionError("Integration must materialize the combo texture")

    # Test-only refill starts on the next non-ready Hero.act, not on click or UI redraw.
    settings = (root / "core/src/main/java/com/spd/cohero/CoHeroSettings.java").read_text(encoding="utf-8")
    tab = (root / "core/src/main/java/com/spd/cohero/CoHeroSettingsTab.java").read_text(encoding="utf-8")
    hero_patch = (patch_dir / "patch_hero.py").read_text(encoding="utf-8")
    if ("GameSettings.getBoolean(AUTO_FILL_LINK, false)" not in settings
            or 'new CheckBox(CoHeroMessages.get("settings.auto_fill_link"))' not in tab
            or "CoHeroSettings.setAutoFillLinkEnabled(checked())" not in tab):
        raise AssertionError("Opt-in Link test flag must be disabled by default and editable")
    if ('if (!ready) com.spd.cohero.CoHeroCombo.onHeroTurn();' not in hero_patch
            or "CoHeroSettings.autoFillLinkEnabled() && energy < CAST_COST" not in source
            or "energy = MAX_ENERGY;" not in source):
        raise AssertionError("Link test refill must occur on next Hero turn under 60 only")

    game_scene = (patch_dir / "patch_gamescene.py").read_text(encoding="utf-8")
    for required in ("combo_tag_create_marker", "tagCoHeroCombo", "scene.coHeroCombo.flip(tagsOnLeft)"):
        if required not in game_scene:
            raise AssertionError(f"Combo Tag layout is incomplete: {required}")
    inventory = (root / "core/src/main/java/com/spd/cohero/WndCompanionInventory.java").read_text(
        encoding="utf-8")
    if "CoHeroCombo.skillDescription()" not in inventory:
        raise AssertionError("Inventory must explain its class-pair ultimate")
    if ("CoHeroCombo.requestCast()" in inventory
            or 'CoHeroMessages.get("combo.energy"' in inventory):
        raise AssertionError("Inventory must not contain a combo meter or cast button")
    if "addStatCell(" in inventory or 'labels[i] + " " + values[i]' not in inventory:
        raise AssertionError("Companion stats must be inline label/value text without fixed columns")
    if "addCompactStats(0, startY, layoutWidth)" not in inventory:
        raise AssertionError("Portrait stats must use compact localized flow")
    if "addCompactStats(0, startY, leftWidth)" not in inventory:
        raise AssertionError("Landscape stats must use compact localized flow")
    if "companion.canPerformCombo()" not in source:
        raise AssertionError("Combo must not interrupt a pending companion movement/decision")
    for required in (
        "Ballistica.STOP_TARGET | Ballistica.STOP_SOLID",
        "line.subPath(0, line.dist).contains(mob.pos)",
        "Ballistica.STOP_TARGET | Ballistica.STOP_SOLID).collisionPos == mob.pos",
    ):
        if required not in source:
            raise AssertionError(f"Ultimate must not reach enemies behind terrain: {required}")

    # Ultimate VFX are 6 main motifs + 6 partner accents: a display-only
    # Noosa cue; never a game Actor, asynchronous combat action, or saved state.
    fx = (root / "core/src/main/java/com/spd/cohero/CoHeroComboFX.java").read_text(
        encoding="utf-8")
    combo = source
    if "CoHeroComboFX.play(" not in combo:
        raise AssertionError("Confirmed ultimate must start visual presentation")
    if combo.index("CoHeroComboFX.play(") > combo.index("performClericUltimate(hero, companion, partnerClass)"):
        raise AssertionError("FX must capture its target before the skill can kill it")
    for required in (
        "private static void primary(",
        "private static void accent(",
        "private static final class Cue extends Visual",
        "private static final float MAIN_START = 0.12f;",
        "private static final float PARTNER_START = 0.30f;",
        "private static final float TOTAL_DURATION = 0.58f;",
        "Dungeon.level.heroFOV[cell]",
        "Dungeon.level != level || Dungeon.hero != hero",
        "killAndErase()",
        "recycle(MagicMissile.class)",
        "CellEmitter.center(cell).burst(",
    ):
        if required not in fx:
            raise AssertionError(f"Missing composable, non-blocking combo FX: {required}")
    # Bound each switch to its own Java method to avoid counting cases from elsewhere.
    for name, next_name in (("primary", "accent"), ("accent", "casterFlare")):
        begin = fx.index("private static void " + name + "(")
        finish = fx.index("private static void " + next_name + "(", begin)
        arms = __import__("re").findall(r"case [0-5]:", fx[begin:finish])
        if len(arms) != 6:
            raise AssertionError(f"{name} must support all six ordered classes")
    for forbidden in ("Actor.", ".spend(", ".next(", ".busy(", "Callback"):
        if forbidden in fx:
            raise AssertionError(f"FX must not alter gameplay or wait for animation: {forbidden}")

    from importlib.util import spec_from_file_location, module_from_spec
    spec = spec_from_file_location("cohero_messages_patch", patch_dir / "patch_messages.py")
    module = module_from_spec(spec)
    spec.loader.exec_module(module)
    source_dir = root / "messages"
    base = dict(module.parse_messages(
        (source_dir / "misc.properties").read_text(encoding="utf-8"),
        source_dir / "misc.properties"))
    names = [
        f"cohero.combo.skill.{h}.{c}"
        for h in range(6) for c in range(6)
    ]
    if len({base.get(key) for key in names}) != 36 or any(key not in base for key in names):
        raise AssertionError("Every ordered 6x6 class pair must have a unique skill name")
    # Dynamically validate exceptional ordered-pair text lookups. A missing
    # key would show "No Text Found" for an otherwise usable ultimate.
    special_block = source[
        source.index("if ((heroClass == 0 && partnerClass == 5)"):
        source.index('effects += " " + CoHeroMessages.get(',
                     source.index("if ((heroClass == 0 && partnerClass == 5)"))
    ]
    for hero_class, companion_class in __import__("re").findall(
            r"heroClass == (\d+) && partnerClass == (\d+)", special_block):
        key = f"cohero.combo.detail.extra.{hero_class}.{companion_class}"
        if key not in base:
            raise AssertionError(f"Missing localized special ultimate effect: {key}")
    if "cohero.combo.detail.partner.3.5" not in base:
        raise AssertionError("Huntress + Cleric needs shield-specific effect text")

    for locale in source_dir.glob("misc*.properties"):
        values = dict(module.parse_messages(locale.read_text(encoding="utf-8"), locale))
        if values.keys() != base.keys():
            raise AssertionError(f"Combo message keys differ for {locale.name}")
        for key in base:
            if module.placeholders(values[key]) != module.placeholders(base[key]):
                raise AssertionError(f"Combo message placeholder mismatch: {locale.name}:{key}")

test_combo_attack_patch_and_catalog()
test_helper()
test_warding_patch()
test_mob_patch()
test_all_java_patch_scripts_use_resilient_source()
test_non_java_patch_resilience()
print("Integration patch resilience tests: OK")
