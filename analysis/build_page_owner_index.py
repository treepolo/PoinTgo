"""List concrete product-page owners and their AOT methods."""
from __future__ import annotations

import json
from collections import OrderedDict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FUNCTIONS = ROOT / "analysis" / "aot_unflutter" / "functions.jsonl"
CLASSES = ROOT / "analysis" / "aot_unflutter" / "classes.jsonl"
OUT_JSON = ROOT / "analysis" / "page_owner_index.json"
OUT_MD = ROOT / "ORIGINAL_PAGE_SYMBOLS_20260915.md"

PRODUCT = [
    "RootPage", "ProfileDashboardPage", "SensorManagePage", "WeightliftingPage", "SwingPage", "SlamPage",
    "VBTPage", "RotationPage", "OneRmPage", "OneRmMeasurePage", "OneRmCameraMeasurePage", "JumpPage",
    "JumpPendingPage", "RsiPage", "RsiPendingPage", "MobilityPage", "IsometricPage", "SessionExecutionPage",
    "SessionDetailPage", "SettingsPage", "LoginPage", "ExerciseSelectPage", "ProgramListPage", "ProgramDetailPage",
    "ProgramCreatePage", "ProfileDashboardPage", "ProfileFormPage", "ProfileSelectPage", "SetReplayListPage",
    "SetReplayPlayerPage", "DevSensorRecordsPage", "MultiSensorTestPage", "AccountDeletionPage", "WhatsNewPage",
]


def read(path: Path):
    with path.open(encoding="utf-8", errors="replace") as fh:
        for line in fh:
            try:
                yield json.loads(line)
            except json.JSONDecodeError:
                pass


def main() -> None:
    functions = list(read(FUNCTIONS))
    classes = {row.get("class_name"): row for row in read(CLASSES)}
    result = OrderedDict()
    for owner in PRODUCT:
        rows = [r for r in functions if r.get("owner") == owner]
        if not rows and owner not in classes:
            continue
        result[owner] = {
            "class_id": classes.get(owner, {}).get("class_id"),
            "field_count": len(classes.get(owner, {}).get("fields", [])),
            "method_count": len(rows),
            "methods": [
                {"name": r.get("name"), "pc": r.get("pc"), "size": r.get("size"), "param_count": r.get("param_count")}
                for r in rows
            ],
        }
    OUT_JSON.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    lines = [
        "# Poin*T Go 原廠產品頁面 AOT symbol 索引",
        "",
        "本表直接從 `functions.jsonl` 的 `owner` 與 `classes.jsonl` 產生；每個 PC 可在 `analysis/aot_unflutter/asm/<Owner>/` 找到對應 ARM64 listing。",
        "",
        "| 頁面／入口 owner | class id | fields | methods | 主要方法（最多 8 個） |",
        "| --- | ---: | ---: | ---: | --- |",
    ]
    for owner, row in result.items():
        methods = ", ".join(f"`{m['name']}` @ `{m['pc']}`" for m in row["methods"][:8])
        lines.append(f"| `{owner}` | {row['class_id'] or ''} | {row['field_count']} | {row['method_count']} | {methods} |")
    lines += [
        "",
        "## 解讀",
        "",
        "- `build` 是 Flutter widget tree 建構入口；`Pending`／`Measure`／`Execution`／`Detail`／`Replay` 後綴代表同一產品流程的不同狀態或結果頁，而不是單一總頁。",
        "- 頁面 owner 與 notifier／service 的 call edge 需一起追蹤；AOT 反編譯不會自動保留原始 Dart 路由表，因此本表是可追溯的頁面 symbol 基線，不把名稱直接當成完整導航圖。",
        "- `LoginPage` 與 `AuthService` 的登入入口存在；`SensorManagePage`／`DeviceSelector`／`DeviceConnectionManager` 形成裝置流程；動作頁與對應 calculator 形成分析流程；`SessionDetailPage`／`SetReplay*` 形成回放流程。",
    ]
    OUT_MD.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {OUT_JSON}")
    print(f"wrote {OUT_MD}")


if __name__ == "__main__":
    main()
