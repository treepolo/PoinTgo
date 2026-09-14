#!/usr/bin/env python3
"""Inventory the clean original APK's native manifest, Smali and shell APIs."""

from __future__ import annotations

import json
import re
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DECODE = ROOT / "analysis" / "reverse_engineering" / "original_decoded" / "base"
MANIFEST = DECODE / "AndroidManifest.xml"
OUT_JSON = ROOT / "analysis" / "native_smali_index.json"
OUT_MD = ROOT / "ORIGINAL_NATIVE_SMAILI_ANALYSIS_20260915.md"

ANDROID_NS = "http://schemas.android.com/apk/res/android"
CLASS_RE = re.compile(r"^\.class\s+.*?\s+(L[^;]+;)")
SUPER_RE = re.compile(r"^\.super\s+(L[^;]+;)")
METHOD_RE = re.compile(r"^\.method\s+(.+)$")
STRING_RE = re.compile(r'const-string(?:/jumbo)?\s+\w+,\s+"([^"]*)"')

SEARCH_TERMS = [
    "flavor", "START_RECORDING_SERVICE", "STOP_RECORDING_SERVICE", "screen_recorder_channel",
    "Screen recording ready", "Poin*T GO", "pairip", "firebase", "bluetooth", "mocap",
    "calibration", "recording", "sensor",
]


def descriptor_to_name(descriptor: str):
    return descriptor[1:-1].replace("/", ".") if descriptor.startswith("L") else descriptor


def parse_manifest():
    if not MANIFEST.exists():
        return {}
    root = ET.parse(MANIFEST).getroot()
    attr = lambda node, key: node.attrib.get("{" + ANDROID_NS + "}" + key)
    app = root.find("application")
    components = []
    if app is not None:
        for tag in ("activity", "activity-alias", "service", "receiver", "provider"):
            for node in app.findall(tag):
                components.append({"type": tag, "name": attr(node, "name"), "exported": attr(node, "exported"), "permission": attr(node, "permission")})
    return {
        "package": root.attrib.get("package"),
        "version": {"name": attr(root, "versionName"), "code": attr(root, "versionCode")},
        "permissions": sorted({attr(node, "name") for node in root.findall("uses-permission") if attr(node, "name")}),
        "application": {"name": attr(app, "name") if app is not None else None, "label": attr(app, "label") if app is not None else None, "theme": attr(app, "theme") if app is not None else None},
        "components": components,
    }


def parse_smali():
    files = sorted(DECODE.rglob("*.smali")) if DECODE.exists() else []
    roots = Counter()
    packages = Counter()
    classes = []
    methods = Counter()
    supers = Counter()
    term_hits = defaultdict(list)
    for path in files:
        rel = path.relative_to(ROOT).as_posix()
        dex_root = path.relative_to(DECODE).parts[0] if path.relative_to(DECODE).parts else "base"
        roots[dex_root] += 1
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        class_match = CLASS_RE.search(text, re.MULTILINE)
        descriptor = class_match.group(1) if class_match else None
        name = descriptor_to_name(descriptor) if descriptor else path.stem
        package = name.rsplit(".", 1)[0] if "." in name else ""
        packages[package] += 1
        method_names = []
        for match in METHOD_RE.finditer(text, re.MULTILINE):
            signature = match.group(1).strip()
            method_names.append(signature)
            methods[package] += 1
        super_match = SUPER_RE.search(text, re.MULTILINE)
        if super_match:
            supers[descriptor_to_name(super_match.group(1))] += 1
        for term in SEARCH_TERMS:
            if term.lower() in text.lower():
                term_hits[term].append({"class": name, "path": rel, "method_count": len(method_names)})
        classes.append({"name": name, "descriptor": descriptor, "path": rel, "dex_root": dex_root, "method_count": len(method_names), "methods": method_names[:200]})
    return files, roots, packages, classes, methods, supers, term_hits


def build():
    manifest = parse_manifest()
    files, roots, packages, classes, methods, supers, term_hits = parse_smali()
    own = [row for row in classes if row["name"].startswith("kr.piehealthcare.point.sensor")]
    native_classes = [row for row in classes if not any(row["name"].startswith(prefix) for prefix in ("android.", "java.", "javax.", "kotlin.", "androidx."))]
    result = {
        "generated": "2026-09-15",
        "decode_root": DECODE.relative_to(ROOT).as_posix(),
        "manifest": manifest,
        "smali_file_count": len(files),
        "class_count": len(classes),
        "own_package_class_count": len(own),
        "non_platform_class_count": len(native_classes),
        "dex_roots": dict(roots),
        "top_packages": [{"package": p, "class_count": c} for p, c in packages.most_common(100)],
        "top_superclasses": [{"super": s, "count": c} for s, c in supers.most_common(50)],
        "term_hits": {term: hits[:200] for term, hits in term_hits.items()},
        "classes": classes,
    }
    OUT_JSON.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")

    lines = [
        "# 原廠 Android 原生外殼／Smali 索引（2026-09-15）", "",
        "輸入是 clean Apktool decode 的原廠 `base.apk`；clone decode 沒有混入。這份報告只描述可直接從 Manifest/Smali 讀到的 Android 層，不把 Flutter AOT 的推論冒充成 Java/Kotlin source。", "",
        "## Manifest", "",
        f"- package：`{manifest.get('package')}`",
        f"- application：`{manifest.get('application', {}).get('name')}`",
        f"- permissions：**{len(manifest.get('permissions', []))}**",
        f"- components：**{len(manifest.get('components', []))}**",
        "",
        "| type | name | exported | permission |",
        "|---|---|---|---|",
    ]
    for component in manifest.get("components", []):
        lines.append(f"| `{component.get('type')}` | `{component.get('name')}` | `{component.get('exported')}` | `{component.get('permission') or ''}` |")
    lines += ["", "## Smali 統計", "", f"- Smali files：**{len(files):,}**；classes：**{len(classes):,}**；原廠 package classes：**{len(own):,}**", "", "| Dex root | files |", "|---|---:|"]
    for root, count in roots.items():
        lines.append(f"| `{root}` | {count:,} |")
    lines += ["", "### 主要 package", "", "| package | classes |", "|---|---:|"]
    for package, count in packages.most_common(40):
        lines.append(f"| `{package}` | {count:,} |")
    lines += ["", "## 原生 API／字串命中", "", "命中只代表該常數或字串確實存在於 Smali；它是 A 級存在證據，不代表每個呼叫情境都已由靜態分析證明。", ""]
    for term in SEARCH_TERMS:
        hits = term_hits.get(term, [])
        lines.append(f"### `{term}`：{len(hits)} classes")
        for hit in hits[:10]:
            lines.append(f"- `{hit['class']}` · `{hit['path']}` · methods `{hit['method_count']}`")
        if len(hits) > 10:
            lines.append(f"- 其餘 {len(hits) - 10} 筆見 JSON。")
        lines.append("")
    lines += [
        "## 可核對結論", "",
        "- `MainActivity` 的 Flutter 外殼、plugin 註冊與 `flavor` MethodChannel 是原生層入口；產品頁面與運動邏輯在 Flutter AOT。",
        "- `ScreenRecorderService` 的 START/STOP action、前景通知 channel 與 service 生命週期可由 Smali 直接核對。",
        "- Android 原生清冊完整不等於伺服器端完整：Firebase/Auth/雲端規則不在 APK；其客戶端邊界另見 AOT call graph。", "",
        "完整機器可讀資料：`analysis/native_smali_index.json`。", "",
    ]
    OUT_MD.write_text("\n".join(lines), encoding="utf-8")
    print(json.dumps({"smali_file_count": len(files), "class_count": len(classes), "own_package_class_count": len(own)}, ensure_ascii=False))


if __name__ == "__main__":
    build()
