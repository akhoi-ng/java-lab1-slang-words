"""Verify a cleaned slang.txt against the original.

Usage: python tools/verify_slang.py <original> <cleaned>

1. Structural checks on the cleaned file.
2. Every (slang, meaning) pair of the original must still exist in the cleaned
   file, except those changed on purpose by the tables in fix_slang.py.
"""
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fix_slang import HEADER, KEY_FIXES, MEANING_FIXES  # noqa: E402


def pairs_of(path):
    """Independent, lenient parser -> (set of (key, meaning), set of keys)."""
    raw = open(path, "rb").read()
    lines = raw.decode("utf-8").splitlines()[1:]
    pairs, keys, last_key = set(), set(), None
    for line in lines:
        if not line.strip():
            continue
        if "`" in line:
            k, v = line.split("`", 1)
            last_key = k.strip()
            keys.add(last_key)
            parts = v.split("|")
        else:  # continuation line of the previous slang word
            parts = [line]
        for p in parts:
            if p.strip():
                pairs.add((last_key, p.strip()))
    return pairs, keys


def structural_errors(path):
    errors = []
    raw = open(path, "rb").read()
    text = raw.decode("utf-8")  # raises if not valid UTF-8
    if raw.startswith(b"\xef\xbb\xbf"):
        errors.append("file starts with a BOM")
    if text.replace("\r\n", "").count("\n") or text.replace("\r\n", "").count("\r"):
        errors.append("mixed line endings")
    lines = text.split("\r\n")
    if lines[-1] != "":
        errors.append("missing final newline")
    lines = lines[:-1]
    if lines[0] != HEADER:
        errors.append(f"bad header {lines[0]!r}")
    seen = {}
    for n, line in enumerate(lines[1:], 2):
        if line.count("`") != 1:
            errors.append(f"line {n}: expected exactly one backtick: {line!r}")
            continue
        k, v = line.split("`")
        if not k or k != k.strip():
            errors.append(f"line {n}: bad key {k!r}")
        if k in seen:
            errors.append(f"line {n}: duplicate key {k!r} (first at line {seen[k]})")
        seen[k] = n
        parts = v.split("| ")
        for p in parts:
            if not p or p != p.strip() or "|" in p:
                errors.append(f"line {n}: bad meaning separator/spacing: {line!r}")
                break
        if len(parts) != len(set(parts)):
            errors.append(f"line {n}: repeated meaning: {line!r}")
        if re.search(r"[^\x20-\x7e]", line) and k not in ("®", "™") \
                and k not in ("ELNS", "EVT", "GMBH", "ILM"):
            errors.append(f"line {n}: unexpected non-ASCII character: {line!r}")
    return errors, len(seen)


def main(orig, clean):
    errors, n_keys = structural_errors(clean)

    old_pairs, old_keys = pairs_of(orig)
    new_pairs, new_keys = pairs_of(clean)

    meaning_fix = {(KEY_FIXES.get(k, k), old): new for k, old, new in MEANING_FIXES}
    expected = set()
    for k, m in old_pairs:
        k = KEY_FIXES.get(k, k)
        expected.add((k, meaning_fix.get((k, m), m)))
    expected_keys = {KEY_FIXES.get(k, k) for k in old_keys}

    lost = sorted(expected - new_pairs)
    extra = sorted(new_pairs - expected)
    if lost:
        errors += [f"LOST pair: {p!r}" for p in lost]
    if extra:
        errors += [f"UNEXPECTED pair: {p!r}" for p in extra]
    if expected_keys != new_keys:
        errors.append(f"key set differs: missing={expected_keys - new_keys} extra={new_keys - expected_keys}")

    print(f"original: {len(old_keys)} unique slang words, {len(old_pairs)} (slang, meaning) pairs")
    print(f"cleaned : {len(new_keys)} unique slang words, {len(new_pairs)} (slang, meaning) pairs, {n_keys} lines")
    print("\nIntentional changes (original -> cleaned):")
    for k, v in KEY_FIXES.items():
        print(f"  key  {k!r:16} -> {v!r}")
    for k, old, new in MEANING_FIXES:
        print(f"  {k:8} {old!r} -> {new!r}")

    if errors:
        print(f"\nFAILED with {len(errors)} problem(s):")
        for e in errors:
            print("  " + e)
        sys.exit(1)
    print("\nOK: structure valid, no (slang, meaning) pair lost or added beyond the fix tables.")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
