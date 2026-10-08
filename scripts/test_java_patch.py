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
)


def test_helper():
    source = r"""
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
"""

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
                float highestChance = Float.POSITIVE_INFINITY;
                Char closestHostile = null;

                for (Char ch : Actor.chars()){
                    float bestChance = detectionChance(ch);
                    if (ch.invisible > 0) {
                        bestChance = Float.POSITIVE_INFINITY;
                    }
                    if (ch.flying) {
                        bestChance = Float.POSITIVE_INFINITY;
                    }
                    if (bestChance < highestChance){
                        highestChance = bestChance;
                        closestHostile = ch;
                    }
                }

                if (closestHostile != null && Random.Float() < detectionChance(closestHostile)) {
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
        subprocess.run(
            [sys.executable, str(patch_dir / script_name), str(target)],
            check=True,
            capture_output=True,
            text=True,
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
        "Char easiestHostileToDetect = null;",
        "bestChance > highestChance",
        "Random.Float() < highestChance",
        "com.spd.cohero.CoHeroRemoteView.attack(this, enemy.pos);",
        "&& !coHeroSurprisedBy(enemy)",
        "if (mob instanceof com.spd.cohero.CoHeroAlly)",
    )
    if any(token not in patched for token in required):
        raise AssertionError("Mob structural patch missed a required postcondition")
    if "closestHostile" in patched:
        raise AssertionError("Mob structural patch left the obsolete sleeping selector behind")


test_helper()
test_warding_patch()
test_mob_patch()
print("Java structural patch tests: OK")
