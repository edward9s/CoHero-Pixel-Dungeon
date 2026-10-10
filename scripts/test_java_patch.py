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
    # The original Duelist armor ability 'Challenge' uses HeroIcon.CHALLENGE
    # on SPD's shared 16x16 hero-icons sheet; never ship a replacement PNG.
    for required in (
        "import com.shatteredpixel.shatteredpixeldungeon.actors.hero.abilities.duelist.Challenge;",
        "import com.shatteredpixel.shatteredpixeldungeon.ui.HeroIcon;",
        "icon = new HeroIcon(new Challenge());",
        "public static final int HEIGHT = 30;",
        "private static final int EMPTY_COLOR = 0x404955;",
        "icon.y = y + 6f;",
        "count.y = y + height - count.height() - 1f;",
    ):
        if required not in hud_tag:
            raise AssertionError(f"Frameless original Duelist icon layout missing: {required}")
    if any(token in hud_tag for token in (
            "new Image(ICON)", "cohero_combo.png", "firstSword", "secondSword",
            ".angle =", "ICON_SCALE", "FRAME_SIZE", "frameTop", "frameBottom",
            "frameLeft", "frameRight", "icon.scale.set(")):
        raise AssertionError("Ultimate Tag still uses custom or rotated artwork")
    if (root / "core/src/main/assets/interfaces/cohero_combo.png.b64").exists():
        raise AssertionError("Obsolete custom combo PNG must be removed")
    integration_sh = (root / "integration/shattered/apply.sh").read_text(encoding="utf-8")
    if "cohero_combo.png" in integration_sh:
        raise AssertionError("No custom combo icon decoding should remain in the build")
    if ("scene.coHeroCombo.setRect( tagLeft, pos - com.spd.cohero.CoHeroComboIndicator.HEIGHT," not in (
                patch_dir / "patch_gamescene.py").read_text(encoding="utf-8")):
        raise AssertionError("Combo Tag must reserve 30px without colliding with the counter")

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

    # The test option's *visibility* is a separate global flag. Every eligible
    # debug-log false->true transition toggles it; disabling debug never toggles.
    for required in (
        'private static final String TEST_OPTIONS_VISIBLE = "cohero_test_options_visible";',
        "GameSettings.getBoolean(TEST_OPTIONS_VISIBLE, false)",
        "GameSettings.put(TEST_OPTIONS_VISIBLE, !testOptionsVisible())",
        "inventory.weapon() != null || inventory.armor() != null",
        "inventory.ringOne() != null || inventory.ringTwo() != null",
        "item instanceof Waterskin",
        "item instanceof VelvetPouch",
        "if (!hasWaterskin || !hasVelvetPouch)",
    ):
        if required not in settings:
            raise AssertionError(f"Hidden test unlock rules incomplete: {required}")
    for required in (
        "if (checked() && CoHeroSettings.toggleTestOptionsIfEligible(companion))",
        "updateTestOptionVisibility();",
        "autoFillLink.visible = autoFillLink.active = CoHeroSettings.testOptionsVisible();",
        "if (CoHeroSettings.testOptionsVisible())",
    ):
        if required not in tab:
            raise AssertionError(f"Hidden test checkbox visibility incomplete: {required}")

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
        raise AssertionError("Companion stats must show inline name/value pairs")
    for required in (
        "addTwoRowStats(0, startY, layoutWidth)",
        "addTwoRowStats(0, comboBottom + 4, leftWidth)",
        "for (int row = 0; row < 2; row++)",
        "for (int col = 0; col < 3; col++)",
        "float first = stats[0].width()",
        "float second = stats[3].width()",
    ):
        if required not in inventory:
            raise AssertionError(f"Both orientations must explicitly use two stats rows: {required}")
    if "addCompactStats(" in inventory:
        raise AssertionError("Remove former variable-wrapping stats layout")
    # Both orientations must show combo -> stats -> equipment.
    # Landscape keeps this sequence inside the left panel only.
    portrait = inventory[inventory.index("private void layoutPortrait("):
                         inventory.index("private void layoutLandscape(")]
    landscape = inventory[inventory.index("private void layoutLandscape("):
                          inventory.index("private float addEquipment(")]
    constructor = inventory[:inventory.index("private void layoutPortrait(")]
    if ("layoutPortrait(addComboInfo(0, contentY, layoutWidth) + 4)" not in constructor
            or "layoutLandscape(contentY)" not in constructor):
        raise AssertionError("Portrait combo must precede portrait stats")
    if ('addComboInfo(0, startY, leftWidth)' not in landscape
            or 'addTwoRowStats(0, comboBottom + 4, leftWidth)' not in landscape
            or 'addEquipment(0, statsBottom + 5)' not in landscape
            or landscape.index("addComboInfo(") > landscape.index("addTwoRowStats(")
            or landscape.index("addTwoRowStats(") > landscape.index("addEquipment(")
            or "addComboInfo(" in portrait):
        raise AssertionError("Landscape left panel must order combo, stats, equipment")
    # Only Cleric-led (6) ultimates self-cast. The other 30 should open
    # auto-aim targeting, reusing ModAssassinate's preferred-last-target flow.
    # A second Tag press confirms the reticle; manual map selection still works.
    for required in (
        "if (classIndex(Dungeon.hero.heroClass) == 5)",
        "cast(null);",
        "targetSelector = listener;",
        "GameScene.selectCell(listener);",
        "aimedTarget = preferredAutoTarget();",
        "if (targetSelector != null)",
        "GameScene.handleCell(target.pos);",
        "GameScene.cancelCellSelector();",
        "QuickSlotButton.lastTarget instanceof Mob",
        "if (validAutoTarget(last))",
        "for (Mob mob : Dungeon.level.mobs)",
        "Dungeon.level.heroFOV[mob.pos]",
        "mob.sprite != null && mob.sprite.visible && mob.sprite.parent != null",
        "validAttackTarget(mob, hero, companion)",
        "Actor.findChar(mob.pos) == mob",
        "QuickSlotButton.target(target);",
        "clearTargetSelection();",
        "if (cell != null)",
    ):
        if required not in source:
            raise AssertionError(f"Combo auto-target and manual selection rule missing: {required}")
    # Only the cast path consumes a charge; aiming/cancelling must not spend
    # either actor's turn or expose an unseen CoHero-only enemy.
    pre_cast = source[source.index("public static void requestCast()"):
                      source.index("private static void cast(Integer cell)")]
    for forbidden in ("energy -= CAST_COST", "spendComboTurn()", "spendAndNext("):
        if forbidden in pre_cast:
            raise AssertionError(f"Combo aim should not spend resources: {forbidden}")
    if "public static Mob aimTarget()" not in source or "aimedTarget = preferredAutoTarget();" in (
            source[source.index("public static Mob aimTarget()"):
                   source.index("private static boolean validAutoTarget(")]):
        raise AssertionError("HUD must not redo full auto-target search every frame")
    for required in (
        "crosshair = Icons.TARGET.get();",
        "refreshCrosshair();",
        "CoHeroCombo.aimTarget();",
        "crosshair.point(aim.sprite.center(crosshair));",
        "crosshair.remove();",
        "public void destroy()",
    ):
        if required not in hud_tag:
            raise AssertionError(f"Combo auto-target reticle missing: {required}")

    if "companion.canPerformCombo()" not in source:
        raise AssertionError("Combo must not interrupt a pending companion movement/decision")
    for required in (
        "Ballistica.STOP_TARGET | Ballistica.STOP_SOLID",
        "line.subPath(0, line.dist).contains(mob.pos)",
        "Ballistica.STOP_TARGET | Ballistica.STOP_SOLID).collisionPos == mob.pos",
    ):
        if required not in source:
            raise AssertionError(f"Ultimate must not reach enemies behind terrain: {required}")

    # Six main motifs and partner accents remain visual-only, but confirmed
    # combo damage waits until all visible SPD missiles have actually arrived.
    # This is essential at long range: a 0.30s fixed hit can precede the shot.
    fx = (root / "core/src/main/java/com/spd/cohero/CoHeroComboFX.java").read_text(
        encoding="utf-8")
    cast = source[source.index("private static void cast(Integer cell)"):
                  source.index("private static boolean canTargetFromEitherHero(")]
    for required in (
        "energy -= CAST_COST;",
        "hero.busy();",
        "final Mob victim = target;",
        "CoHeroComboFX.play(",
        "new Runnable()",
        "public void run()",
        "performClericUltimate(hero, companion, partnerClass)",
        "performAttackUltimate(hero, companion, victim, mainClass, partnerClass)",
        "companion.spendComboTurn();",
        "hero.spendAndNext(Actor.TICK);",
    ):
        if required not in cast:
            raise AssertionError(f"Combo deferred impact missing: {required}")
    if not (cast.index("energy -= CAST_COST;") < cast.index("hero.busy();")
            < cast.index("CoHeroComboFX.play(") < cast.index("public void run()")
            < cast.index("performAttackUltimate(") < cast.index("spendComboTurn()")
            < cast.index("hero.spendAndNext(")):
        raise AssertionError("Damage and both actor turns must wait for the impact")

    for required in (
        "int companionClass, Runnable onImpact)",
        "private final Runnable onImpact;",
        "hero.sprite.parent.add(new Cue(",
        "private static final float MAIN_START = 0.12f;",
        "private static final float PARTNER_START = 0.30f;",
        "private static final float HIT_MIN_START = 0.62f;",
        "private static final float MAX_PROJECTILE_WAIT = 1.80f;",
        "private static final float HIT_SETTLE = 0.14f;",
        "private static final float HIT_TAIL = 0.30f;",
        "private int pendingMissiles;",
        "cue.pendingMissiles++;",
        "new Callback()",
        "cue.missileArrived();",
        "pendingMissiles--;",
        "stage = 2;",
        "pendingMissiles == 0 || elapsed >= MAX_PROJECTILE_WAIT",
        "stage = 3;",
        "jointImpact(center, heroClass, companionClass);",
        "elapsed >= impactTime + HIT_SETTLE",
        "stage = 4;",
        "onImpact.run();",
        "elapsed >= impactTime + HIT_TAIL",
        "killAndErase()",
        "Dungeon.level.heroFOV[cell]",
        "Dungeon.level != level || Dungeon.hero != hero",
        "recycle(MagicMissile.class)",
        "CellEmitter.center(cell).burst(",
    ):
        if required not in fx:
            raise AssertionError(f"Projectile-synchronized impact rule missing: {required}")
    if fx.count("onImpact.run();") != 2:
        raise AssertionError("Only the actual hit and headless fallback may resolve gameplay")
    cue_update = fx[fx.index("public void update()"):
                    fx.index("private static void primary(")]
    if not (cue_update.index("elapsed >= PARTNER_START")
            < cue_update.index("accent(hero, companion, center, heroClass, companionClass, this);")
            < cue_update.index("pendingMissiles == 0 || elapsed >= MAX_PROJECTILE_WAIT")
            < cue_update.index("stage = 3;")
            < cue_update.index("jointImpact(center, heroClass, companionClass);")
            < cue_update.index("elapsed >= impactTime + HIT_SETTLE")
            < cue_update.index("stage = 4;")
            < cue_update.index("onImpact.run();")
            < cue_update.index("elapsed >= impactTime + HIT_TAIL")):
        raise AssertionError("Last strike must visibly occur after missiles arrive, before damage")
    if "arcaneExplosion(cell, true);" not in fx[fx.index("private static void jointImpact("):
                                               fx.index("private static void arcaneExplosion(")]:
        raise AssertionError("Mage blast must detonate on the real hit, not at launch")
    # Huntress must reuse the actual Prismatic Light wand ray, never the
    # generic LIGHT_MISSILE projectile; both the Hero and companion accent
    # have distinct ray origins and cannot reveal terrain outside Hero FOV.
    for required in (
        "import com.shatteredpixel.shatteredpixeldungeon.effects.Beam;",
        "import com.shatteredpixel.shatteredpixeldungeon.effects.particles.RainbowParticle;",
        "prismaticRay(hero, companion, cell);",
        "prismaticRay(companion, hero, cell);",
        "private static void prismaticRay(Char preferred, Char alternate, int cell)",
        "new Beam.LightRay(source.sprite.center(),",
        "DungeonTilemap.raisedTileCenterToWorld(cell)",
        "Sample.INSTANCE.play(Assets.Sounds.RAY, 0.85f);",
        "Ballistica line = new Ballistica(source.pos, cell, Ballistica.PROJECTILE);",
        "for (int pathCell : line.subPath(0, line.dist))",
        "if (visible(pathCell) && !Dungeon.level.solid[pathCell])",
        "CellEmitter.center(cell).burst(RainbowParticle.BURST, count);",
        "private static void jointImpact(Hero hero, CoHeroAlly companion, int cell,",
        "case 0: // Warrior:",
        "case 2: // Rogue:",
        "case 3: // Huntress:",
        "case 4: // Duelist:",
        "case 5: // Cleric:",
        "impact(cell, 0xE7F5FF, 12, 27);",
        "rainbow(cell, 14);",
    ):
        if required not in fx:
            raise AssertionError(f"Stock prismatic ray or stronger hit motif missing: {required}")
    primary_fx = fx[fx.index("private static void primary("):
                    fx.index("private static void accent(")]
    partner_fx = fx[fx.index("private static void accent("):
                    fx.index("private static void jointImpact(")]
    if "MagicMissile.LIGHT_MISSILE" in primary_fx + partner_fx:
        raise AssertionError("Huntress must not fall back to a normal light missile")
    if fx.count("new Beam.LightRay(") != 1:
        raise AssertionError("Use one shared Prismatic Light ray helper, not copied implementations")
    if fx.index("prismaticRay(hero, companion, cell);") > fx.index("private static void jointImpact("):
        raise AssertionError("Huntress's ray must launch in the primary stage")
    if "jointImpact(hero, companion, center, heroClass, companionClass);" not in cue_update:
        raise AssertionError("All roles must show their distinctive impact before damage")

    # A Mage-led combo should feel like a bomb, while the Mage companion adds a
    # lighter detonation. Neither effect may trigger actual Bomb gameplay.
    for required in (
        "import com.shatteredpixel.shatteredpixeldungeon.effects.particles.BlastParticle;",
        "import com.shatteredpixel.shatteredpixeldungeon.effects.particles.SmokeParticle;",
        "private static void arcaneExplosion(int cell, boolean major)",
        "arcaneExplosion(cell, true);",
        "arcaneExplosion(cell, false);",
        "BlastParticle.FACTORY, major ? 20 : 7",
        "SmokeParticle.FACTORY, major ? 3 : 1",
        "case 1: return Assets.Sounds.BLAST;",
        "Sample.INSTANCE.play(Assets.Sounds.BLAST, 0.45f);",
        "if (heroClass == 5)",
        "if (heroClass != 1)",
        "if (!visible(cell))",
        "new Ballistica(cell, neighbor,",
    ):
        if required not in fx:
            raise AssertionError(f"Missing balanced bomb-style combo effect: {required}")
    if "new Bomb(" in fx or ".explode(" in fx:
        raise AssertionError("Combo FX must not detonate real game-world bombs")
    # Bound each switch to its own Java method, not later impact switches.
    for name, next_name in (("primary", "accent"), ("accent", "jointImpact")):
        begin = fx.index("private static void " + name + "(")
        finish = fx.index("private static void " + next_name + "(", begin)
        arms = __import__("re").findall(r"case [0-5]:", fx[begin:finish])
        if len(arms) != 6:
            raise AssertionError(f"{name} must support all six ordered classes")
    # FX cannot consume game turns directly; one callback fires into the
    # pending cast resolution and Actor scheduling remains in CoHeroCombo.
    for forbidden in ("Actor.", ".spend(", ".next(", ".busy("):
        if forbidden in fx:
            raise AssertionError(f"FX must not consume actor time directly: {forbidden}")

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
