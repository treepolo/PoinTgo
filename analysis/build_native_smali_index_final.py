#!/usr/bin/env python3
"""Corrected runner for the native Smali index.

The first draft passed ``re.MULTILINE`` as a regex search position, which
missed class declarations at byte zero.  Keep the original report generator
and replace only its parser with a flag-correct implementation.
"""

from __future__ import annotations

import re
from collections import Counter, defaultdict
from pathlib import Path

import build_native_smali_index as base

CLASS_RE = re.compile(r"^\.class\s+.*?\s+(L[^;]+;)", re.MULTILINE)
SUPER_RE = re.compile(r"^\.super\s+(L[^;]+;)", re.MULTILINE)
METHOD_RE = re.compile(r"^\.method\s+(.+)$", re.MULTILINE)


def parse_smali_fixed():
    files = sorted(base.DECODE.rglob("*.smali")) if base.DECODE.exists() else []
    roots = Counter()
    packages = Counter()
    classes = []
    methods = Counter()
    supers = Counter()
    term_hits = defaultdict(list)
    for path in files:
        rel = path.relative_to(base.ROOT).as_posix()
        rel_to_decode = path.relative_to(base.DECODE)
        dex_root = rel_to_decode.parts[0] if rel_to_decode.parts else "base"
        roots[dex_root] += 1
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        class_match = CLASS_RE.search(text)
        descriptor = class_match.group(1) if class_match else None
        name = base.descriptor_to_name(descriptor) if descriptor else path.stem
        package = name.rsplit(".", 1)[0] if "." in name else ""
        packages[package] += 1
        method_names = [match.group(1).strip() for match in METHOD_RE.finditer(text)]
        methods[package] += len(method_names)
        super_match = SUPER_RE.search(text)
        if super_match:
            supers[base.descriptor_to_name(super_match.group(1))] += 1
        lowered = text.lower()
        for term in base.SEARCH_TERMS:
            if term.lower() in lowered:
                term_hits[term].append({"class": name, "path": rel, "method_count": len(method_names)})
        classes.append({"name": name, "descriptor": descriptor, "path": rel,
                        "dex_root": dex_root, "method_count": len(method_names),
                        "methods": method_names[:200]})
    return files, roots, packages, classes, methods, supers, term_hits


if __name__ == "__main__":
    base.parse_smali = parse_smali_fixed
    base.build()
