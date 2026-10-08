#!/usr/bin/env python3
from pathlib import Path
import sys

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
    raise SystemExit("java_patch method scope did not contain the expected body")
if 'String close = "}";' not in source[nested_method.body_start:nested_method.body_end]:
    raise SystemExit("java_patch nested method scope was not resolved correctly")

patched = replace_regex_once(
    source,
    method,
    r"return\s+true\s*;",
    "return false;",
    "target return",
)
if patched.count("return false;") != 3:
    raise SystemExit("method-scoped replacement escaped its selected method")

try:
    find_method(source, "target", None, outer)
except JavaPatchError:
    pass
else:
    raise SystemExit("ambiguous structural method lookup must fail fast")

try:
    replace_regex_once(source, method, r"doesNotExist", "x", "missing semantic anchor")
except JavaPatchError:
    pass
else:
    raise SystemExit("missing scoped semantic anchor must fail fast")

print("Java structural patch helper: OK")
