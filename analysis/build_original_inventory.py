"""Build a reproducible static inventory for the official Poin*T Go APK.

This intentionally does not touch a phone.  It reads the preserved official
base/split APKs and the already decoded/AOT analysis tree, then writes compact
JSON and Markdown evidence under the repository.
"""
from __future__ import annotations

import hashlib
import json
import re
import zipfile
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
APK_DIR = ROOT / "analysis" / "official_clone" / "original"
DECODED = ROOT / "analysis" / "official_clone" / "decoded" / "base"
AOT = ROOT / "analysis" / "aot_unflutter"
ARM64 = ROOT / "analysis" / "apk_protocol" / "unpacked_arm64" / "lib" / "arm64-v8a" / "libapp.so"
OUT_JSON = ROOT / "analysis" / "original_app_inventory.json"
OUT_MD = ROOT / "ORIGINAL_APP_STATIC_INVENTORY_20260915.md"


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for block in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def apk_inventory(path: Path) -> dict:
    with zipfile.ZipFile(path) as zf:
        names = zf.namelist()
        cats = Counter()
        for name in names:
            if name.startswith("lib/"):
                cats["native_lib"] += 1
            elif name.startswith("assets/flutter_assets/"):
                cats["flutter_asset"] += 1
            elif name.startswith("res/"):
                cats["android_resource"] += 1
            elif name.endswith(".dex"):
                cats["dex"] += 1
            elif name.startswith("META-INF/"):
                cats["signature_or_metadata"] += 1
            else:
                cats["other"] += 1
        notable = [
            n
            for n in names
            if n in {
                "AndroidManifest.xml",
                "resources.arsc",
                "classes.dex",
                "assets/flutter_assets/NativeAssetsManifest.json",
                "assets/flutter_assets/isolate_snapshot_data",
                "assets/flutter_assets/kernel_blob.bin",
            }
            or n.endswith("/libapp.so")
            or n.endswith("/libflutter.so")
        ]
        return {
            "file": str(path.relative_to(ROOT)),
            "bytes": path.stat().st_size,
            "sha256": sha256(path),
            "zip_entries": len(names),
            "categories": dict(sorted(cats.items())),
            "notable_entries": sorted(notable),
        }


def file_stats(root: Path) -> dict:
    files = [p for p in root.rglob("*") if p.is_file()]
    by_ext = Counter(p.suffix.lower() or "<none>" for p in files)
    by_top = Counter(p.relative_to(root).parts[0] for p in files if p.relative_to(root).parts)
    return {
        "files": len(files),
        "bytes": sum(p.stat().st_size for p in files),
        "by_extension": dict(sorted(by_ext.items())),
        "by_top_level": dict(sorted(by_top.items())),
    }


def flutter_stats() -> dict:
    root = DECODED / "assets" / "flutter_assets"
    files = [p for p in root.rglob("*") if p.is_file()]
    groups = Counter()
    examples = {}
    for p in files:
        rel = p.relative_to(root).as_posix()
        group = rel.split("/", 1)[0] if "/" in rel else rel
        groups[group] += 1
        examples.setdefault(group, []).append(rel)
    return {
        "files": len(files),
        "bytes": sum(p.stat().st_size for p in files),
        "top_level_counts": dict(sorted(groups.items())),
        "examples": {k: sorted(v)[:12] for k, v in sorted(examples.items())},
    }


def aot_stats() -> dict:
    result = {}
    for name in ("classes.jsonl", "functions.jsonl", "index.jsonl", "string_refs.jsonl", "call_edges.jsonl", "objects.jsonl", "edges.jsonl", "code_map.jsonl"):
        path = AOT / name
        result[name] = {"bytes": path.stat().st_size, "lines": sum(1 for _ in path.open("rb"))}
    asm_files = list((AOT / "asm").glob("*.txt"))
    result["asm_txt"] = {"files": len(asm_files), "bytes": sum(p.stat().st_size for p in asm_files)}
    important = [
        "BleMocapDataParser",
        "Command",
        "GlobalAcceleration",
        "GlobalVelocity",
        "RotationQuaternion",
        "JsWeightliftingCalculator",
        "JsVbtCalculator",
        "JsRotationCalculator",
        "JsOneRmCalculator",
        "OneRmTest",
        "JumpRunner",
        "JumpFeedbackAnalyzer",
        "AuthService",
        "LoginPage",
        "History",
        "Settings",
    ]
    function_text = (AOT / "functions.jsonl").read_text(encoding="utf-8", errors="replace")
    asm_names = {p.stem for p in asm_files}
    result["important_symbol_hits"] = {
        key: {
            "function_lines": function_text.count(key),
            "asm_files": sum(1 for stem in asm_names if key.lower() in stem.lower()),
        }
        for key in important
    }
    # The decoded string file is a compact, inspectable front-end/algorithm index.
    string_file = ROOT / "analysis" / "apk_protocol" / "libapp.strings.txt"
    if string_file.exists():
        text = string_file.read_text(encoding="utf-8", errors="replace")
        keywords = [
            "登入", "Google", "裝置", "校正", "跳", "RSI", "1RM", "velocity", "acceleration",
            "rotation", "throw", "weight", "history", "setting", "firebase", "BLE",
        ]
        result["string_file"] = {
            "bytes": string_file.stat().st_size,
            "lines": len(text.splitlines()),
            "keyword_hits": {k: len(re.findall(re.escape(k), text, flags=re.IGNORECASE)) for k in keywords},
        }
    return result


def dex_stats() -> dict:
    smali = DECODED / "smali"
    smali_dirs = [DECODED / "smali", DECODED / "smali_classes2", DECODED / "smali_classes3"]
    rows = {}
    for root in smali_dirs:
        files = list(root.rglob("*.smali")) if root.exists() else []
        rows[root.name] = {
            "files": len(files),
            "bytes": sum(p.stat().st_size for p in files),
            "packages": len({p.parent.relative_to(root).as_posix() for p in files}),
        }
    dex_files = list((ROOT / "analysis" / "official_clone" / "native-dex").glob("*.dex"))
    rows["native_dex"] = {"files": len(dex_files), "bytes": sum(p.stat().st_size for p in dex_files)}
    return rows


def manifest_facts() -> dict:
    path = DECODED / "AndroidManifest.xml"
    text = path.read_text(encoding="utf-8", errors="replace")
    components = re.findall(r"(?:android:name|name)=\"([^\"]+)\"", text)
    permissions = re.findall(r"android:name=\"(android\.permission\.[^\"]+|com\.google[^\"]+|kr\.piehealthcare[^\"]+)\"", text)
    return {
        "path": str(path.relative_to(ROOT)),
        "package": re.search(r'package="([^"]+)"', text).group(1),
        "application": re.search(r'android:name="([^"]+)"', text).group(1),
        "component_count": len(components),
        "components": sorted(set(components)),
        "permissions": sorted(set(permissions)),
        "lines": len(text.splitlines()),
    }


def main() -> None:
    apks = [apk_inventory(p) for p in sorted(APK_DIR.glob("*.apk"))]
    decoded = file_stats(DECODED)
    payload = {
        "generated": "2026-09-15",
        "source": "official Poin*T Go 1.3.5 preserved APK set",
        "phone_required": False,
        "official_apks": apks,
        "decoded_base": decoded,
        "manifest": manifest_facts(),
        "dex_smali": dex_stats(),
        "flutter_assets": flutter_stats(),
        "aot": aot_stats(),
        "libapp_so": {
            "file": str(ARM64.relative_to(ROOT)),
            "bytes": ARM64.stat().st_size if ARM64.exists() else None,
            "sha256": sha256(ARM64) if ARM64.exists() else None,
        },
    }
    OUT_JSON.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    lines = [
        "# Poin*T Go 原廠靜態拆包／反編譯／反組譯清冊",
        "",
        "生成日期：2026-09-15。此清冊只讀專案內保存的 APK 與分析輸出，不需要手機。",
        "",
        "## 輸入 APK",
        "",
        "| 檔案 | bytes | SHA-256 | ZIP entries | 分類數 |",
        "| --- | ---: | --- | ---: | --- |",
    ]
    for row in apks:
        lines.append(f"| `{row['file']}` | {row['bytes']:,} | `{row['sha256']}` | {row['zip_entries']:,} | `{json.dumps(row['categories'], ensure_ascii=False)}` |")
    lines += [
        "",
        "## Manifest／Dex／資源",
        "",
        f"- package：`{payload['manifest']['package']}`；application：`{payload['manifest']['application']}`；manifest {payload['manifest']['lines']} 行、{payload['manifest']['component_count']} 個 name/component；權限 {len(payload['manifest']['permissions'])} 個。",
        f"- 解包 base：{decoded['files']:,} 檔、{decoded['bytes']:,} bytes；副檔名：`{json.dumps(decoded['by_extension'], ensure_ascii=False)}`。",
        f"- Smali：`{json.dumps(payload['dex_smali'], ensure_ascii=False)}`。",
        "",
        "## Flutter 前端資源",
        "",
        f"- `assets/flutter_assets`：{payload['flutter_assets']['files']:,} 檔、{payload['flutter_assets']['bytes']:,} bytes；第一層分類：`{json.dumps(payload['flutter_assets']['top_level_counts'], ensure_ascii=False)}`。",
        "- 代表性資源包括 `assets/img/device`、`assets/img/exercise`、`assets/img/placement`、`assets/icon/features`、音效、WantedSans 字型、JS bridge、TFLite／pose model 與 shader；完整清單在 JSON。",
        "",
        "## Flutter AOT／ARM64",
        "",
    ]
    for key, row in payload['aot'].items():
        if isinstance(row, dict) and "lines" in row:
            lines.append(f"- `{key}`：{row['lines']:,} lines、{row['bytes']:,} bytes。")
        elif isinstance(row, dict) and "files" in row:
            lines.append(f"- `{key}`：{row['files']:,} files、{row['bytes']:,} bytes。")
    lines += [
        f"- `libapp.so`：{payload['libapp_so']['bytes']:,} bytes，SHA-256 `{payload['libapp_so']['sha256']}`。",
        "- 重要入口命中與字串 keyword 計數詳見 `analysis/original_app_inventory.json`；每個結論仍需以 AOT listing／物件池／呼叫邊原文追溯。",
        "",
        "## 證據邊界",
        "",
        "- APK／資源／Smali／AOT bytes 是直接證據；由 symbol/字串聚類推導的前端頁面或算法意義是交叉推論。",
        "- Flutter release AOT 不含原始 Dart 變數名、註解與完整型別語義；因此本清冊證明拆包與反組譯輸出完整，不把尚未有 runtime golden vector 的公式宣稱為官方原式。",
        "- 原始 APK 及此清冊不含手機必要步驟；手機只在後續要驗證官方 runtime 輸出、權限、登入或真實 BLE 行為時使用。",
    ]
    OUT_MD.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {OUT_JSON}")
    print(f"wrote {OUT_MD}")


if __name__ == "__main__":
    main()
