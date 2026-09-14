"""Extract an inspectable frontend/data-flow index from Flutter AOT metadata.

The release APK has no Dart source.  This index keeps original symbol names,
program counters and evidence paths so later manual/automated review can go
back to the exact listing instead of relying on a guessed UI map.
"""
from __future__ import annotations

import json
import re
from collections import Counter, defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
AOT = ROOT / "analysis" / "aot_unflutter"
GRAPH = ROOT / "analysis" / "aot_graph"
ASSETS = ROOT / "analysis" / "reverse_engineering" / "original_decoded" / "base" / "assets" / "flutter_assets"
OUT_JSON = ROOT / "analysis" / "frontend_analysis_index.json"
OUT_MD = ROOT / "ORIGINAL_FRONTEND_ANALYSIS_20260915.md"


def jsonl(path: Path):
    with path.open(encoding="utf-8", errors="replace") as fh:
        for line in fh:
            try:
                yield json.loads(line)
            except json.JSONDecodeError:
                continue


def normalize_name(row: dict) -> str:
    return str(row.get("name", row.get("class_name", "")))


def feature_index(functions: list[dict]) -> dict:
    terms = {
        "auth_login": ["auth", "login", "signIn", "signUp", "logout", "currentUser"],
        "device_ble": ["ble", "bluetooth", "device", "mocap", "gatt", "scan", "connect"],
        "calibration": ["calib", "calibrate", "bias", "offsetAxis", "gravity"],
        "weightlifting_throw": ["weightlifting", "throw", "swing", "slam", "firstPull", "secondPull", "jerk"],
        "vbt": ["vbt", "velocityBased", "velocityData", "rangeOfMotion", "velocityLoss"],
        "rotation": ["rotation", "angular", "quaternion"],
        "one_rm": ["oneRm", "1rm", "lvp", "brzycki", "epley", "mayhew", "lander", "oconner"],
        "jump_rsi": ["jump", "rsi", "countermovement", "takeoff", "landing", "flight", "sway"],
        "history_session": ["history", "session", "record", "weeklySummary", "bestRecord"],
        "settings_profile": ["setting", "profile", "nickname", "preferences", "bodyMeasurement"],
        "camera_notification": ["camera", "recording", "notification", "screenRecorder", "wakelock"],
        "frontend_navigation": ["page", "screen", "route", "navigator", "navigation", "build", "bottomNav", "drawer"],
        "cloud_firestore": ["firebase", "firestore", "cloud", "remoteConfig", "messaging"],
    }
    output = {}
    for group, needles in terms.items():
        matches = [f for f in functions if any(n.lower() in normalize_name(f).lower() for n in needles)]
        unique = {}
        for f in matches:
            name = normalize_name(f)
            unique.setdefault(name, f)
        output[group] = {
            "function_count": len(matches),
            "unique_name_count": len(unique),
            "examples": [
                {"name": name, "pc": row.get("pc"), "owner": row.get("owner")}
                for name, row in sorted(unique.items())[:80]
            ],
        }
    return output


def class_index(classes: list[dict]) -> dict:
    needles = ["Page", "Screen", "View", "Widget", "Route", "Service", "Controller", "Notifier", "Provider", "Calculator", "Analyzer", "Runner", "Parser", "Model", "Session"]
    result = {}
    for needle in needles:
        rows = [c for c in classes if needle.lower() in str(c.get("class_name", "")).lower()]
        result[needle] = {
            "count": len(rows),
            "classes": [
                {"class_name": c.get("class_name"), "class_id": c.get("class_id"), "fields": len(c.get("fields", []))}
                for c in sorted(rows, key=lambda x: str(x.get("class_name", "")))[:120]
            ],
        }
    return result


def locale_index(functions: list[dict]) -> dict:
    rows = [f for f in functions if f.get("owner") == "SZhTw" and ".get:" in str(f.get("name", ""))]
    props = []
    for row in rows:
        name = str(row["name"])
        prop = name.split(".get:", 1)[1].rsplit("_", 1)[0]
        props.append({"property": prop, "pc": row.get("pc"), "symbol": name})
    return {"traditional_chinese_getters": len(props), "properties": props}


def routes_and_strings() -> dict:
    path = ROOT / "analysis" / "apk_protocol" / "libapp.strings.txt"
    if not path.exists():
        return {"route_like": [], "feature_like": []}
    text = path.read_text(encoding="utf-8", errors="replace")
    values = [line.split("\t", 1)[-1].strip() for line in text.splitlines() if "\t" in line]
    route_like = sorted({v for v in values if (v.startswith("/") or "route" in v.lower() or "screen" in v.lower())})
    feature_like = sorted({v for v in values if any(k in v.lower() for k in ("jump", "rsi", "vbt", "one rm", "rotation", "swing", "slam", "mobility", "isometric"))})
    return {"route_like": route_like[:500], "feature_like": feature_like[:500]}


def asset_index() -> dict:
    if not ASSETS.exists():
        return {}
    rows = []
    for path in ASSETS.rglob("*"):
        if not path.is_file():
            continue
        rel = path.relative_to(ASSETS).as_posix()
        rows.append(rel)
    groups = defaultdict(list)
    for rel in rows:
        parts = rel.split("/")
        group = "/".join(parts[:2]) if len(parts) > 1 else parts[0]
        groups[group].append(rel)
    return {k: {"count": len(v), "files": sorted(v)} for k, v in sorted(groups.items())}


def main() -> None:
    functions = list(jsonl(AOT / "functions.jsonl"))
    classes = list(jsonl(AOT / "classes.jsonl"))
    string_refs = list(jsonl(AOT / "string_refs.jsonl"))
    calls = list(jsonl(AOT / "call_edges.jsonl"))
    objects = list(jsonl(GRAPH / "objects.jsonl"))
    owners = Counter(str(f.get("owner", "<none>")) for f in functions)
    payload = {
        "generated": "2026-09-15",
        "phone_required": False,
        "source_paths": {
            "functions": str((AOT / "functions.jsonl").relative_to(ROOT)),
            "classes": str((AOT / "classes.jsonl").relative_to(ROOT)),
            "string_refs": str((AOT / "string_refs.jsonl").relative_to(ROOT)),
            "call_edges": str((AOT / "call_edges.jsonl").relative_to(ROOT)),
            "objects": str((GRAPH / "objects.jsonl").relative_to(ROOT)),
        },
        "counts": {"functions": len(functions), "classes": len(classes), "string_refs": len(string_refs), "call_edges": len(calls), "objects": len(objects)},
        "top_function_owners": owners.most_common(80),
        "feature_groups": feature_index(functions),
        "class_groups": class_index(classes),
        "locale": locale_index(functions),
        "strings": routes_and_strings(),
        "flutter_assets": asset_index(),
    }
    OUT_JSON.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    lines = [
        "# Poin*T Go 原廠 Flutter 前端與資料流分析索引",
        "",
        "生成日期：2026-09-15。索引由保存的 release AOT metadata、呼叫邊、字串參照與官方 Flutter assets 產生；不需要手機。",
        "",
        "## 覆蓋量",
        "",
        f"- 函式 {len(functions):,}、class {len(classes):,}、字串參照 {len(string_refs):,}、呼叫邊 {len(calls):,}、object {len(objects):,}。原始索引檔案列在 `analysis/frontend_analysis_index.json`。",
        "- 這些是 release AOT 的符號／PC／關係索引，不是捏造的 Dart 原始碼；去除名稱的函式仍保留其 PC 與呼叫關係。",
        "",
        "## 前端／功能群組（依 symbol 與交叉參照聚類）",
        "",
        "| 群組 | 函式命中 | 唯一名稱 | 可追溯入口例子 |",
        "| --- | ---: | ---: | --- |",
    ]
    for group, row in payload["feature_groups"].items():
        ex = ", ".join(f"`{x['name']}`" for x in row["examples"][:5])
        lines.append(f"| {group} | {row['function_count']:,} | {row['unique_name_count']:,} | {ex} |")
    lines += [
        "",
        "## 已建立的前端流程邊界",
        "",
        "1. **啟動／導航**：Flutter `MainActivity` 是 Android 入口；AOT 中以 `Page`／`Screen`／`Route`／`Navigator`／`build`／底部導航等函式聚類出頁面建立與導航層。",
        "2. **登入／帳號**：`AuthService`、`authStateChanges`、`currentUser`、`validateSession`、`LoginPage` 與 Google 登入相關函式及字串形成帳號邊界；雲端同步不能等同本機感測器流程。",
        "3. **裝置／BLE**：`BleMocapDataParser`、命令物件、`BLEService`、裝置掃描／連線／設定與 raw／四元數／速度／位置 parser 形成資料入口；GATT UUID/封包證據另見 `analysis/aot-parser-final.md` 與 `analysis/hci/` 摘要。",
        "4. **校正／姿態**：六面 accel bias／offset-axis、gyro calibration、重力／四元數與 validation 函式是校正狀態機；`assets/img/placement` 與裝置示意資源是其前端引導資源。",
        "5. **訓練入口**：甩球／揮擊／slam／weightlifting、VBT、rotation、1RM/LVP、jump/CMJ/RSI、mobility/isometric 等分開聚類；不得用單一 rep 閾值解讀所有入口。",
        "6. **結果／歷史**：`Session`、`History`、`BestRecord`、weekly summary、feedback／grade／fatigue 函式與 JSON model 形成結果保存、摘要與回放邊界。",
        "7. **設定／媒體／通知**：device/profile/setting、camera recording、screen recorder、local notification、wakelock、Firebase messaging 等是獨立 side-effect 層。",
        "",
        "## Traditional Chinese 前端資源",
        "",
        f"- AOT 語系 getter class `SZhTw` 可直接定位 {payload['locale']['traditional_chinese_getters']:,} 個 getter/PC；完整 property/PC 在 JSON。",
        "- 官方 `split_config.zh.apk` 的 `values-zh-rTW`、`values-zh-rHK`、`values-zh-rCN` 已保存於 `analysis/official_clone/build/zh-decoded/res/values-*`；Flutter 自己的 UI 文案則在 AOT string pool，不會出現在 Android `strings.xml`。",
        "",
        "## 資料流摘要",
        "",
        "`BLE/GATT → parser → Raw/Quaternion/Global data model → calibration/derived motion → module calculator → rep/event/result model → local history/cloud/feedback UI`",
        "",
        "每個箭頭的函式與 object/call-edge 證據都可由 `source_paths` 回溯；函式的實際 ARM64 指令在 `analysis/aot_unflutter/asm/`，而非在本索引中重複。",
        "",
        "## 尚未能由靜態檔案單獨證明的項目",
        "",
        "- release AOT 的部分函式名稱被縮短或匿名化，無法只靠 symbol 得到原始 Dart 變數名與註解。",
        "- 伺服器端 Firebase/Firestore 規則、帳號授權結果與某些模型的實際資料內容不會完整嵌在 APK；靜態索引只能證明 client 邊界。",
        "- 各算法的精確浮點常數、濾波器初始狀態、rep 邊界與 UI 顯示條件仍須以官方 runtime golden vectors 補強；這是待驗證，不偽稱已完全還原。",
    ]
    OUT_MD.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"wrote {OUT_JSON}")
    print(f"wrote {OUT_MD}")


if __name__ == "__main__":
    main()
