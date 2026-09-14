#!/usr/bin/env python3
"""Summarise the original Flutter AOT call graph by product responsibility.

This is a static call-edge index, not a claim that an obfuscated ``sub_*``
target has a recovered Dart name.  It is useful for locating the boundaries
between pages, BLE, calibration, auth, sessions and motion calculators.
"""

from __future__ import annotations

import json
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EDGES = ROOT / "analysis" / "aot_unflutter" / "call_edges.jsonl"
OUT_JSON = ROOT / "analysis" / "feature_callgraph_index.json"
OUT_MD = ROOT / "ORIGINAL_FEATURE_CALLGRAPH_20260915.md"

FEATURES = {
    "app_shell": ["RootPage", "MainActivity", "App", "Navigation", "Route"],
    "auth": ["AuthService", "LoginPage", "signInWith", "validateSession", "currentUser"],
    "device_ble": ["BleApi", "BleMocap", "BleDevice", "DeviceConnection", "DeviceEvent", "SensorManage", "discover", "connectDevice"],
    "calibration": ["Calibration", "AccelCalibration", "GyroCalibration", "calibrate", "CalibrationUtil"],
    "weightlifting": ["Weightlifting", "JsWeightlifting", "SlamPage", "SwingPage"],
    "vbt": ["Vbt", "VBT", "VelocityBased", "VBTPage"],
    "rotation": ["Rotation", "JsRotation"],
    "one_rm": ["OneRm", "OneRM", "Lvp"],
    "jump_rsi": ["Jump", "Rsi", "RSI", "Countermovement"],
    "sessions_history": ["Session", "History", "Replay", "SetDetail", "Exercise"],
    "settings_profile": ["Settings", "Profile", "Notification", "AccountDeletion"],
    "media_cloud": ["Camera", "Image", "Video", "Firebase", "Firestore", "Storage", "Notification"],
}

MEANINGFUL_TARGET_TERMS = [
    "Ble", "Device", "Calibration", "Auth", "Session", "History", "Replay",
    "Weightlifting", "Vbt", "VBT", "Rotation", "OneRm", "Jump", "Rsi",
    "Profile", "Settings", "Firebase", "Firestore", "Camera", "Notification",
    "RawImu", "Quaternion", "Motion", "Metrics", "Rep", "Phase", "Velocity",
]


def classify(name: str):
    return [feature for feature, terms in FEATURES.items() if any(term in name for term in terms)]


def load_edges():
    result = []
    if not EDGES.exists():
        return result
    with EDGES.open("r", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            try:
                row = json.loads(line)
            except json.JSONDecodeError:
                continue
            if row.get("from_func") and row.get("target"):
                result.append(row)
    return result


def build():
    edges = load_edges()
    stats = {}
    examples = defaultdict(list)
    for feature in FEATURES:
        selected = [edge for edge in edges if feature in classify(str(edge.get("from_func", "")))]
        target_counts = Counter(str(edge.get("target", "")) for edge in selected)
        meaningful = Counter({
            target: count for target, count in target_counts.items()
            if any(term in target for term in MEANINGFUL_TARGET_TERMS)
        })
        cross = Counter()
        for edge in selected:
            source_features = classify(str(edge.get("from_func", "")))
            target_features = classify(str(edge.get("target", "")))
            for target_feature in target_features:
                if target_feature not in source_features:
                    cross[f"{source_features[0] if source_features else feature} -> {target_feature}"] += 1
        stats[feature] = {
            "source_function_count": len({edge.get("from_func") for edge in selected}),
            "edge_count": len(selected),
            "unique_target_count": len(target_counts),
            "top_targets": [{"target": target, "count": count} for target, count in target_counts.most_common(25)],
            "meaningful_targets": [{"target": target, "count": count} for target, count in meaningful.most_common(30)],
            "cross_feature_edges": [{"edge": key, "count": count} for key, count in cross.most_common(20)],
        }
        meaningful_examples = [edge for edge in selected if any(term in str(edge.get("target")) for term in MEANINGFUL_TARGET_TERMS)]
        examples[feature] = meaningful_examples[:12]

    result = {
        "generated": "2026-09-15",
        "edge_file": EDGES.relative_to(ROOT).as_posix(),
        "edge_count": len(edges),
        "feature_count": len(FEATURES),
        "features": stats,
        "examples": dict(examples),
    }
    OUT_JSON.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")

    lines = [
        "# 原廠 Flutter AOT 功能呼叫邊索引（2026-09-15）", "",
        "這份報告從 `analysis/aot_unflutter/call_edges.jsonl` 逐筆建立功能責任邊界。feature 是符號群組，不是重新命名原始 Dart；`sub_*`、thunk 與 framework target 會保留原名。", "",
        f"- 輸入 call edges：**{len(edges):,}**",
        f"- 功能群組：**{len(FEATURES)}**",
        "- 證據等級：A（直接呼叫邊）；由群組交集推導的流程方向屬 B，不把它當成完整 source-level control flow。", "",
        "## 功能群組摘要", "",
        "| 群組 | source functions | edges | unique targets | 主要可讀 target |",
        "|---|---:|---:|---:|---|",
    ]
    for feature, data in stats.items():
        targets = ", ".join(item["target"] for item in data["meaningful_targets"][:5]) or "（主要是 framework/sub_*）"
        lines.append(f"| `{feature}` | {data['source_function_count']:,} | {data['edge_count']:,} | {data['unique_target_count']:,} | {targets} |")

    lines += ["", "## 可讀的跨責任呼叫例子", ""]
    for feature, data in stats.items():
        lines.append(f"### `{feature}`")
        cross = data["cross_feature_edges"]
        if cross:
            for item in cross[:10]:
                lines.append(f"- `{item['edge']}`：{item['count']:,} edges")
        else:
            lines.append("- 沒有以目前保守符號群組判定的跨群組邊；不推測。")
        if examples[feature]:
            for edge in examples[feature][:5]:
                lines.append(f"- 例：`{edge.get('from_func')}` → `{edge.get('target')}` ({edge.get('kind', '?')})")
        lines.append("")

    lines += [
        "## 使用限制", "",
        "- 相同 function 可能同時命中多個群組（例如 VBTPage 與 Session）；摘要是搜尋導覽，不是唯一分類。",
        "- AOT call edge 只表達靜態 branch/call 指向，不含 runtime 條件、非同步 stream 的實際觸發順序，也不能還原雲端／伺服器端程式。",
        "- 精確演算法仍需 `ORIGINAL_ALGORITHM_EVIDENCE_20260915.md` 所列的常數池與 runtime golden-vector 對照。", "",
        "完整機器可讀資料：`analysis/feature_callgraph_index.json`。", "",
    ]
    OUT_MD.write_text("\n".join(lines), encoding="utf-8")
    print(json.dumps({"edge_count": len(edges), "feature_count": len(FEATURES)}, ensure_ascii=False))


if __name__ == "__main__":
    build()
