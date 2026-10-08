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


test_helper()
test_warding_patch()
test_mob_patch()
test_all_java_patch_scripts_use_resilient_source()
test_non_java_patch_resilience()
print("Integration patch resilience tests: OK")
