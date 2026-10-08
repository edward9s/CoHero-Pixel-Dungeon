from dataclasses import dataclass
import re
from typing import Optional, Sequence


class JavaPatchError(RuntimeError):
    pass


@dataclass(frozen=True)
class JavaBlock:
    start: int
    open_brace: int
    close_brace: int

    @property
    def body_start(self):
        return self.open_brace + 1

    @property
    def body_end(self):
        return self.close_brace


def mask_java(source: str) -> str:
    """Masks comments and literals while preserving string length and newlines."""
    out = list(source)
    i = 0
    n = len(source)
    state = "code"

    while i < n:
        if state == "code":
            if source.startswith("//", i):
                out[i] = out[i + 1] = " "
                i += 2
                state = "line"
                continue
            if source.startswith("/*", i):
                out[i] = out[i + 1] = " "
                i += 2
                state = "block"
                continue
            if source.startswith('"""', i):
                for j in range(i, min(i + 3, n)):
                    out[j] = " "
                i += 3
                state = "text"
                continue
            if source[i] == '"':
                out[i] = " "
                i += 1
                state = "string"
                continue
            if source[i] == "'":
                out[i] = " "
                i += 1
                state = "char"
                continue
            i += 1
            continue

        if state == "line":
            if source[i] == "\n":
                state = "code"
            else:
                out[i] = " "
            i += 1
            continue

        if state == "block":
            if source.startswith("*/", i):
                out[i] = out[i + 1] = " "
                i += 2
                state = "code"
            else:
                if source[i] != "\n":
                    out[i] = " "
                i += 1
            continue

        if state == "text":
            if source.startswith('"""', i):
                for j in range(i, min(i + 3, n)):
                    out[j] = " "
                i += 3
                state = "code"
            else:
                if source[i] != "\n":
                    out[i] = " "
                i += 1
            continue

        if state in ("string", "char"):
            quote = '"' if state == "string" else "'"
            if source[i] == "\\":
                if source[i] != "\n":
                    out[i] = " "
                if i + 1 < n:
                    if source[i + 1] != "\n":
                        out[i + 1] = " "
                    i += 2
                else:
                    i += 1
            elif source[i] == quote:
                out[i] = " "
                i += 1
                state = "code"
            else:
                if source[i] != "\n":
                    out[i] = " "
                i += 1

    return "".join(out)


def _matching(masked: str, open_pos: int, open_ch: str, close_ch: str) -> int:
    if open_pos < 0 or open_pos >= len(masked) or masked[open_pos] != open_ch:
        raise JavaPatchError(f"expected {open_ch!r} at {open_pos}")

    depth = 0
    for i in range(open_pos, len(masked)):
        ch = masked[i]
        if ch == open_ch:
            depth += 1
        elif ch == close_ch:
            depth -= 1
            if depth == 0:
                return i

    raise JavaPatchError(f"unmatched {open_ch!r} at {open_pos}")


def _decl_start(source: str, token_pos: int) -> int:
    start = source.rfind("\n", 0, token_pos) + 1
    while start > 0:
        prev_end = start - 1
        prev_start = source.rfind("\n", 0, prev_end) + 1
        line = source[prev_start:prev_end].strip()
        if line.startswith("@"):
            start = prev_start
            continue
        break
    return start


def find_class(source: str, name: str, within: Optional[JavaBlock] = None) -> JavaBlock:
    masked = mask_java(source)
    lo = within.body_start if within else 0
    hi = within.body_end if within else len(source)
    pattern = re.compile(r"\b(?:class|interface|enum|record)\s+" + re.escape(name) + r"\b")

    matches = []
    for match in pattern.finditer(masked, lo, hi):
        brace = masked.find("{", match.end(), hi)
        semi = masked.find(";", match.end(), hi)
        if brace < 0 or (semi >= 0 and semi < brace):
            continue
        close = _matching(masked, brace, "{", "}")
        if close <= hi:
            matches.append(JavaBlock(_decl_start(source, match.start()), brace, close))

    if len(matches) != 1:
        raise JavaPatchError(f"expected exactly one class {name}, found {len(matches)}")
    return matches[0]


def _split_params(masked_params: str):
    params = []
    start = 0
    depth = 0

    for i, ch in enumerate(masked_params):
        if ch in "<([{":
            depth += 1
        elif ch in ">)]}":
            depth = max(0, depth - 1)
        elif ch == "," and depth == 0:
            params.append(masked_params[start:i].strip())
            start = i + 1

    tail = masked_params[start:].strip()
    if tail:
        params.append(tail)
    elif not masked_params.strip():
        params = []
    return params


def find_method(
        source: str,
        name: str,
        param_types: Optional[Sequence[str]] = None,
        within: Optional[JavaBlock] = None) -> JavaBlock:
    masked = mask_java(source)
    lo = within.body_start if within else 0
    hi = within.body_end if within else len(source)
    pattern = re.compile(r"\b" + re.escape(name) + r"\s*\(")

    candidates = []
    for match in pattern.finditer(masked, lo, hi):
        open_paren = masked.find("(", match.start(), match.end())
        close_paren = _matching(masked, open_paren, "(", ")")
        if close_paren >= hi:
            continue

        params = _split_params(masked[open_paren + 1:close_paren])
        if param_types is not None:
            if len(params) != len(param_types):
                continue
            if any(
                    not re.search(
                        r"(?<![\w.])" + re.escape(wanted) + r"(?![\w.])",
                        part)
                    for part, wanted in zip(params, param_types)):
                continue

        cursor = close_paren + 1
        while cursor < hi and masked[cursor].isspace():
            cursor += 1

        if masked.startswith("throws", cursor):
            brace = masked.find("{", cursor + len("throws"), hi)
            semi = masked.find(";", cursor + len("throws"), hi)
            if brace < 0 or (semi >= 0 and semi < brace):
                continue
        elif cursor < hi and masked[cursor] == "{":
            brace = cursor
        else:
            continue

        close = _matching(masked, brace, "{", "}")
        if close > hi:
            continue
        candidates.append(JavaBlock(_decl_start(source, match.start()), brace, close))

    if len(candidates) != 1:
        signature = f"{name}({', '.join(param_types or [])})"
        raise JavaPatchError(
            f"expected exactly one method {signature}, found {len(candidates)}")
    return candidates[0]


def insert_before(source: str, block: JavaBlock, text: str) -> str:
    return source[:block.start] + text + source[block.start:]


def insert_after(source: str, block: JavaBlock, text: str) -> str:
    return source[:block.close_brace + 1] + text + source[block.close_brace + 1:]


def replace_regex_once(
        source: str,
        block: JavaBlock,
        pattern: str,
        replacement: str,
        label: str,
        flags: int = re.MULTILINE | re.DOTALL) -> str:
    body = source[block.body_start:block.body_end]
    matches = list(re.finditer(pattern, body, flags))
    if len(matches) != 1:
        raise JavaPatchError(
            f"expected exactly one {label} in scoped block, found {len(matches)}")

    match = matches[0]
    new_body = body[:match.start()] + match.expand(replacement) + body[match.end():]
    return source[:block.body_start] + new_body + source[block.body_end:]


def replace_regex_count(
        source: str,
        block: JavaBlock,
        pattern: str,
        replacement: str,
        expected: int,
        label: str,
        flags: int = re.MULTILINE | re.DOTALL) -> str:
    body = source[block.body_start:block.body_end]
    regex = re.compile(pattern, flags)
    matches = list(regex.finditer(body))
    if len(matches) != expected:
        raise JavaPatchError(
            f"expected {expected} {label} occurrence(s) in scoped block, found {len(matches)}")

    body, replaced = regex.subn(replacement, body)
    if replaced != expected:
        raise JavaPatchError(
            f"expected to replace {expected} {label} occurrence(s), replaced {replaced}")
    return source[:block.body_start] + body + source[block.body_end:]


def replace_literal_count(
        source: str,
        block: JavaBlock,
        old: str,
        new: str,
        expected: int,
        label: str) -> str:
    body = source[block.body_start:block.body_end]
    count = body.count(old)
    if count != expected:
        raise JavaPatchError(
            f"expected {expected} {label} occurrence(s) in scoped block, found {count}")

    body = body.replace(old, new)
    return source[:block.body_start] + body + source[block.body_end:]


def require_regex_count(
        source: str,
        block: JavaBlock,
        pattern: str,
        expected: int,
        label: str,
        flags: int = re.MULTILINE | re.DOTALL) -> None:
    body = source[block.body_start:block.body_end]
    count = len(list(re.finditer(pattern, body, flags)))
    if count != expected:
        raise JavaPatchError(
            f"expected {expected} {label} occurrence(s) in scoped block, found {count}")



@dataclass(frozen=True)
class JavaToken:
    text: str
    start: int
    end: int


def java_tokens(source: str):
    """Tokenizes Java while ignoring whitespace and comments, preserving literal text."""
    tokens = []
    i = 0
    n = len(source)

    while i < n:
        ch = source[i]

        if ch.isspace():
            i += 1
            continue

        if source.startswith("//", i):
            end = source.find("\n", i + 2)
            i = n if end < 0 else end + 1
            continue

        if source.startswith("/*", i):
            end = source.find("*/", i + 2)
            if end < 0:
                raise JavaPatchError(f"unterminated block comment at {i}")
            i = end + 2
            continue

        if source.startswith('"""', i):
            end = source.find('"""', i + 3)
            if end < 0:
                raise JavaPatchError(f"unterminated Java text block at {i}")
            end += 3
            tokens.append(JavaToken(source[i:end], i, end))
            i = end
            continue

        if ch in ('"', "'"):
            quote = ch
            start = i
            i += 1
            while i < n:
                if source[i] == "\\":
                    i += 2
                    continue
                if source[i] == quote:
                    i += 1
                    break
                i += 1
            else:
                raise JavaPatchError(f"unterminated Java literal at {start}")
            tokens.append(JavaToken(source[start:i], start, i))
            continue

        if ch.isalpha() or ch in "_$":
            start = i
            i += 1
            while i < n and (source[i].isalnum() or source[i] in "_$"):
                i += 1
            tokens.append(JavaToken(source[start:i], start, i))
            continue

        if ch.isdigit():
            start = i
            i += 1
            while i < n and (source[i].isalnum() or source[i] in "._"):
                i += 1
            tokens.append(JavaToken(source[start:i], start, i))
            continue

        # Punctuation/operator characters are intentionally individual tokens. This makes
        # formatting irrelevant without treating changed operators as equivalent.
        tokens.append(JavaToken(ch, i, i + 1))
        i += 1

    return tokens


def _token_sequence_matches(source: str, anchor: str):
    source_tokens = java_tokens(source)
    anchor_tokens = java_tokens(anchor)
    if not anchor_tokens:
        raise JavaPatchError("empty Java anchor")

    needle = [token.text for token in anchor_tokens]
    haystack = [token.text for token in source_tokens]
    width = len(needle)
    matches = []
    for i in range(0, len(haystack) - width + 1):
        if haystack[i:i + width] == needle:
            matches.append((source_tokens[i].start, source_tokens[i + width - 1].end))
    return matches


class JavaSource(str):
    """Drop-in source wrapper with exact-first, token-aware count/replace fallback."""

    def count(self, sub, start=0, end=None):
        actual_end = len(self) if end is None else end
        exact = super().count(sub, start, actual_end)
        if exact or start != 0 or actual_end != len(self) or not isinstance(sub, str):
            return exact
        return len(_token_sequence_matches(self, sub))

    def replace(self, old, new, count=-1):
        exact = super().count(old)
        if exact:
            return JavaSource(super().replace(old, new, count))
        if not isinstance(old, str) or not isinstance(new, str):
            return JavaSource(super().replace(old, new, count))

        matches = _token_sequence_matches(self, old)
        if not matches or count == 0:
            return JavaSource(self)

        if count == 1 and len(matches) != 1:
            raise JavaPatchError(
                f"ambiguous token-aware replacement: expected one match, found {len(matches)}")

        selected = matches if count < 0 else matches[:count]
        result = str(self)
        for start, end in reversed(selected):
            result = result[:start] + new + result[end:]
        return JavaSource(result)


def java_source(source: str) -> JavaSource:
    return JavaSource(source)


def code_match_count(source: str, anchor: str) -> int:
    exact = source.count(anchor)
    if exact:
        return exact
    return len(_token_sequence_matches(source, anchor))


def replace_code_once(source: str, old: str, new: str, label: str) -> str:
    """Exact replacement first; then whitespace/comment-insensitive Java-token fallback."""
    exact = source.count(old)
    if exact == 1:
        return source.replace(old, new, 1)
    if exact > 1:
        raise JavaPatchError(f"expected exactly one {label} anchor, found {exact}")

    matches = _token_sequence_matches(source, old)
    if len(matches) != 1:
        raise JavaPatchError(
            f"expected exactly one {label} anchor, found {len(matches)} token-equivalent matches")

    start, end = matches[0]
    return source[:start] + new + source[end:]


def replace_code_count(
        source: str,
        old: str,
        new: str,
        expected: int,
        label: str) -> str:
    """Replaces an exact number of token-equivalent Java anchors, right-to-left."""
    exact = source.count(old)
    if exact == expected:
        return source.replace(old, new)

    matches = _token_sequence_matches(source, old)
    if len(matches) != expected:
        raise JavaPatchError(
            f"expected {expected} {label} anchor(s), found {len(matches)} token-equivalent matches")

    result = source
    for start, end in reversed(matches):
        result = result[:start] + new + result[end:]
    return result


def insert_after_code_once(source: str, anchor: str, addition: str, label: str) -> str:
    return replace_code_once(source, anchor, anchor + addition, label)


def insert_before_code_once(source: str, anchor: str, addition: str, label: str) -> str:
    return replace_code_once(source, anchor, addition + anchor, label)


def require_token_count(
        source: str,
        token: str,
        expected: int,
        label: Optional[str] = None) -> None:
    count = source.count(token)
    if count != expected:
        raise JavaPatchError(
            f"expected {expected} {(label or token)} occurrence(s), found {count}")
