#!/usr/bin/env python3
"""Final call-graph report with raw-line and named-edge counts separated."""

from __future__ import annotations

import json
from collections import Counter, defaultdict
from pathlib import Path

import build_feature_callgraph as base


def load_edges_with_counts():
    raw = 0
    edges = []
    if not base.EDGES.exists():
        return raw, edges
    with base.EDGES.open("r", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            raw += 1
            try:
                row = json.loads(line)
            except json.JSONDecodeError:
                continue
            if row.get("from_func") and row.get("target"):
                edges.append(row)
    return raw, edges


def build():
    raw_count, edges = load_edges_with_counts()
    stats = {}
    examples = defaultdict(list)
    for feature in base.FEATURES:
        selected = [edge for edge in edges if feature in base.classify(str(edge.get("from_func", "")))]
        target_counts = Counter(str(edge.get("target", "")) for edge in selected)
        meaningful = Counter({target: count for target, count in target_counts.items() if any(term in target for term in base.MEANINGFUL_TARGET_TERMS)})
        cross = Counter()
        for edge in selected:
            source_features = base.classify(str(edge.get("from_func", "")))
            target_features = base.classify(str(edge.get("target", "")))
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
        examples[feature] = [edge for edge in selected if any(term in str(edge.get("target")) for term in base.MEANINGFUL_TARGET_TERMS)][:12]

    result = {
        "generated": "2026-09-15",
        "edge_file": base.EDGES.relative_to(base.ROOT).as_posix(),
        "raw_line_count": raw_count,
        "named_call_edge_count": len(edges),
        "records_without_named_call_pair": raw_count - len(edges),
        "feature_count": len(base.FEATURES),
        "features": stats,
        "examples": dict(examples),
    }
    base.OUT_JSON.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")

    lines = [
        "# 原廠 Flutter AOT 功能呼叫邊索引（2026-09-15）", "",
        "這份報告從原始 call-edge export 逐筆建立功能責任邊界。feature 是符號群組，不是重新命名原始 Dart；`sub_*`、thunk 與 framework target 保留原名。", "",
        f"- 原始 JSONL records：**{raw_count:,}**",
        f"- 含 `from_func` + `target` 的 named call edges：**{len(edges):,}**",
        f"- 其餘 records（沒有完整 named call pair）：**{raw_count - len(edges):,}**",
        f"- 功能群組：**{len(base.FEATURES)}**",
        "- 證據等級：A（直接呼叫邊）；群組交集推導流程方向屬 B，不當成完整 source-level control flow。", "",
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
        for edge in examples[feature][:5]:
            lines.append(f"- 例：`{edge.get('from_func')}` → `{edge.get('target')}` ({edge.get('kind', '?')})")
        lines.append("")
    lines += [
        "## 使用限制", "",
        "- 相同 function 可能同時命中多個群組；摘要是搜尋導覽，不是唯一分類。",
        "- AOT call edge 只表達靜態 branch/call 指向，不含 runtime 條件、非同步 stream 的實際觸發順序，也不能還原雲端／伺服器端程式。",
        "- 精確演算法仍需 `ORIGINAL_ALGORITHM_EVIDENCE_20260915.md` 所列的常數池與 runtime golden-vector 對照。", "",
        "完整機器可讀資料：`analysis/feature_callgraph_index.json`。", "",
    ]
    base.OUT_MD.write_text("\n".join(lines), encoding="utf-8")
    print(json.dumps({"raw_line_count": raw_count, "named_call_edge_count": len(edges), "records_without_named_call_pair": raw_count - len(edges), "feature_count": len(base.FEATURES)}, ensure_ascii=False))


if __name__ == "__main__":
    build()
