#!/usr/bin/env python3
"""Measure semantic coverage of the original Flutter AOT symbol inventory."""

from __future__ import annotations

import json
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
AOT = ROOT / "analysis" / "aot_unflutter"
FUNCTIONS = AOT / "functions.jsonl"
CLASSES = AOT / "classes.jsonl"
OUT_JSON = ROOT / "analysis" / "code_coverage_index.json"
OUT_MD = ROOT / "ORIGINAL_CODE_COVERAGE_20260915.md"

CATEGORIES = [
    ("ble_transport", ["Ble", "BLE", "Mocap", "DeviceConnection", "DeviceEvent", "RawImu", "Quaternion"]),
    ("calibration", ["Calibration", "Accel", "Gyro", "BiasCal", "OffsetAxis"]),
    ("motion_algorithms", ["Weightlifting", "Vbt", "VBT", "Rotation", "OneRm", "Lvp", "Jump", "Rsi", "Countermovement", "Velocity", "MotionData"]),
    ("product_pages", ["Page", "Screen", "Route", "Navigator", "Analytics", "Chart", "Replay", "Pending", "Measure"]),
    ("auth_cloud_data", ["Auth", "Firebase", "Firestore", "Firestore", "AthleteProfile", "DataSource", "Repository", "Session", "History", "Program"]),
    ("media_notifications", ["Camera", "Video", "Audio", "Recorder", "Notification", "ImagePicker", "ScreenRecord"]),
    ("localization", ["SZh", "SEn", "SEs", "Localization", "localizedLabel", "ContentLocalization"]),
    ("platform_framework", ["Flutter", "Android", "MethodChannel", "Platform", "Render", "Widget", "Element", "Listenable", "Stream", "Future", "Json", "String", "Int", "Double", "Object", "Type"]),
]


def read_jsonl(path: Path):
    rows = []
    if not path.exists():
        return rows
    with path.open("r", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            try:
                rows.append(json.loads(line))
            except json.JSONDecodeError:
                continue
    return rows


def categorize(name: str):
    for category, terms in CATEGORIES:
        if any(term in name for term in terms):
            return category
    if name.startswith("sub_") or name.startswith("#") or "Closure" in name or "closure" in name:
        return "obfuscated_or_codegen"
    return "other_named_or_unknown"


def build():
    funcs = read_jsonl(FUNCTIONS)
    classes = read_jsonl(CLASSES)
    counts = Counter()
    sizes = Counter()
    owners = defaultdict(Counter)
    examples = defaultdict(list)
    for row in funcs:
        name = str(row.get("name", ""))
        category = categorize(name)
        counts[category] += 1
        sizes[category] += int(row.get("size") or 0)
        owner = str(row.get("owner") or "(none)")
        owners[category][owner] += 1
        if len(examples[category]) < 20:
            examples[category].append({"name": name, "pc": row.get("pc"), "size": row.get("size"), "owner": row.get("owner")})

    total = len(funcs) or 1
    result = {
        "generated": "2026-09-15",
        "function_count": len(funcs),
        "class_count": len(classes),
        "categories": {
            category: {
                "function_count": counts[category],
                "percent_of_functions": round(100 * counts[category] / total, 2),
                "code_bytes_sum": sizes[category],
                "top_owners": [{"owner": owner, "count": count} for owner, count in owners[category].most_common(30)],
                "examples": examples[category],
            }
            for category, _terms in CATEGORIES
        },
        "obfuscated_or_codegen": {
            "function_count": counts["obfuscated_or_codegen"],
            "percent_of_functions": round(100 * counts["obfuscated_or_codegen"] / total, 2),
            "code_bytes_sum": sizes["obfuscated_or_codegen"],
            "top_owners": [{"owner": owner, "count": count} for owner, count in owners["obfuscated_or_codegen"].most_common(30)],
            "examples": examples["obfuscated_or_codegen"],
        },
        "other_named_or_unknown": {
            "function_count": counts["other_named_or_unknown"],
            "percent_of_functions": round(100 * counts["other_named_or_unknown"] / total, 2),
            "code_bytes_sum": sizes["other_named_or_unknown"],
            "top_owners": [{"owner": owner, "count": count} for owner, count in owners["other_named_or_unknown"].most_common(30)],
            "examples": examples["other_named_or_unknown"],
        },
    }
    OUT_JSON.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")

    lines = [
        "# 原廠 Flutter AOT 程式碼覆蓋索引（2026-09-15）", "",
        "這不是把每個 AOT function 虛構成已還原 Dart；它把全部 function/class 逐筆分到保守的責任類別，讓『哪些已經有語意入口、哪些仍是 framework/obfuscated/unknown』可量化。分類使用符號名稱，精確公式仍須 AOT 組譯與 runtime 對照。", "",
        f"- functions：**{len(funcs):,}**",
        f"- classes：**{len(classes):,}**", "",
        "| 類別 | functions | 佔比 | code bytes（function size 合計） |",
        "|---|---:|---:|---:|",
    ]
    for category in list(counts.keys()):
        if category not in result["categories"] and category not in ("obfuscated_or_codegen", "other_named_or_unknown"):
            continue
        data = result.get("categories", {}).get(category) or result.get(category)
        lines.append(f"| `{category}` | {data['function_count']:,} | {data['percent_of_functions']:.2f}% | {data['code_bytes_sum']:,} |")

    lines += ["", "## 重要類別解讀", ""]
    for category in ("ble_transport", "calibration", "motion_algorithms", "product_pages", "auth_cloud_data", "media_notifications", "localization", "obfuscated_or_codegen", "other_named_or_unknown"):
        data = result.get("categories", {}).get(category) or result.get(category)
        lines.append(f"### `{category}`：{data['function_count']:,} functions（{data['percent_of_functions']:.2f}%）")
        lines.append("- owners：" + ", ".join(f"`{item['owner']}` ({item['count']})" for item in data["top_owners"][:8]))
        lines.append("- examples：" + ", ".join(f"`{item['name']}`" for item in data["examples"][:8]))
        lines.append("")
    lines += [
        "## 邊界", "",
        "- 類別可重疊的概念在本索引採 first-match；前端 page 與 calculator 的詳細 owner/PC 見 page owner 與 algorithm evidence。",
        "- `obfuscated_or_codegen` 與 `other_named_or_unknown` 不代表未執行；只代表目前沒有足夠穩定的產品語意名稱。",
        "- 這份覆蓋量化與組譯、call graph 一起使用，才能逐步把 unknown 降低；不把分類數字當成完整 source-level comprehension。", "",
        "完整機器可讀資料：`analysis/code_coverage_index.json`。", "",
    ]
    OUT_MD.write_text("\n".join(lines), encoding="utf-8")
    print(json.dumps({"function_count": len(funcs), "class_count": len(classes), "obfuscated_or_codegen": counts["obfuscated_or_codegen"], "other_named_or_unknown": counts["other_named_or_unknown"]}, ensure_ascii=False))


if __name__ == "__main__":
    build()
