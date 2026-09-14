#!/usr/bin/env python3
"""Recursive, evidence-oriented index for Flutter AOT assembly exports.

The assembly exporter places most files below owner/class directories.  This
script deliberately uses recursive enumeration and joins function metadata to
paths by the generated PC suffix, preserving unresolved rows instead of
guessing from similar names.
"""

from __future__ import annotations

import json
import re
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
AOT = ROOT / "analysis" / "aot_unflutter"
ASM = AOT / "asm"
FUNCTIONS = AOT / "functions.jsonl"
OUT_JSON = ROOT / "analysis" / "asm_function_index.json"
OUT_MD = ROOT / "ORIGINAL_AOT_ASM_INVENTORY_20260915.md"

IMPORTANT = [
    "BleMocapDataParser.parseReceivedPacket", "_parseRawImuBatch",
    "_parseRawAccelGyroPrsTmp", "_parseQuatAclVelPos", "_parseQuatAgzVgzPgz",
    "_parseQuaternionPacket", "CommandMeasureRawImuHighRate.command",
    "BleApiImpl.startBleDeviceScan", "BleApiImpl.connectDevice",
    "BleApiImpl.disconnectDevice", "BleApiImpl._onReceivePacketFromBleDevice",
    "DeviceConnectionManager.connect", "DeviceConnectionManager.disconnect",
    "DeviceConnectionManager._onConnectionEvent", "DeviceConnectionManager._onDeviceDiscovered",
    "DeviceConnectionManager._autoRescan", "AccelCalibrationUtil.resetAccelMeasureStateForOffsetAxisCal",
    "isAccelMeasuredAllMocapPositionForBiasCal", "validateAccelXYZAndUpdateMeasureStateForAccelBiasCal",
    "updateMeasureStateForAccelBiasCal", "isAccelMeasuredAllMocapPositionForOffsetAxisCal",
    "validateAccelXYZAndUpdateMeasureStateForOffsetAxisCal", "updateAccelMeasureStateForOffsetAxisCal",
    "_parseAccelOffsetAxisCalibrationPacket", "_parseAccelBiasCalibrationPacket",
    "_parseGyroCalibrationPacket", "JsWeightliftingCalculator._parseMetrics",
    "JsWeightliftingCalculator.processMotionData", "JsWeightliftingCalculator._handleCalibrationComplete",
    "JsVbtCalculator._parseRep", "JsVbtCalculator.flushPendingEmits",
    "JsRotationCalculator._parseRep", "OneRmTest.calculateLvp", "JsOneRmCalculator.calculateLvp",
    "JumpRunner.pushBatch", "detectJumpCountermovement", "JumpFeedbackAnalyzer.analyze",
    "AuthService.authStateChanges", "AuthService.validateSession", "LoginPage.build",
]


def jsonl(path: Path):
    if not path.exists():
        return []
    rows = []
    with path.open("r", encoding="utf-8", errors="replace") as fh:
        for line_no, line in enumerate(fh, 1):
            try:
                rows.append(json.loads(line))
            except json.JSONDecodeError:
                rows.append({"_line": line_no, "_malformed": True})
    return rows


def pc_from_filename(name: str):
    match = re.search(r"_([0-9a-fA-F]+)\.txt$", name)
    return int(match.group(1), 16) if match else None


def build():
    # rglob can enumerate names that exceed the Win32 path limit.  Use the
    # enumerated Path for identity, and an extended prefix for stat fallback.
    asm_files = sorted(ASM.rglob("*.txt")) if ASM.exists() else []
    by_pc = defaultdict(list)
    by_stem = defaultdict(list)
    owners = Counter()
    total_bytes = 0
    for path in asm_files:
        rel = path.relative_to(ROOT).as_posix()
        try:
            size = path.stat().st_size
        except OSError:
            try:
                size = Path("\\\\?\\" + str(path)).stat().st_size
            except OSError:
                size = 0
        total_bytes += size
        owners[path.parent.name] += 1
        stem = path.name[:-4]
        by_stem[stem].append(rel)
        pc = pc_from_filename(path.name)
        if pc is not None:
            by_pc[pc].append(rel)

    funcs = jsonl(FUNCTIONS)
    joined = []
    matched = 0
    for row in funcs:
        pc = row.get("pc")
        if isinstance(pc, str):
            try:
                pc = int(pc, 0)
            except ValueError:
                pc = None
        paths = list(by_pc.get(pc, [])) if pc is not None else []
        if not paths:
            name = str(row.get("name", ""))
            paths = list(by_stem.get(name, []))
            if not paths and "@" in name:
                paths = list(by_stem.get(name.split("@", 1)[0], []))
        paths = sorted(set(paths))
        matched += bool(paths)
        joined.append({"name": row.get("name"), "owner": row.get("owner"),
                       "pc": row.get("pc"), "size": row.get("size"),
                       "asm_paths": paths})

    important = [{"term": term, "functions": [r for r in joined if term in str(r.get("name", ""))]}
                 for term in IMPORTANT]
    result = {
        "generated": "2026-09-15",
        "aot_asm_root": ASM.relative_to(ROOT).as_posix(),
        "recursive_asm_txt_count": len(asm_files),
        "recursive_asm_txt_bytes": total_bytes,
        "owner_count": len(owners),
        "function_count": len(funcs),
        "functions_with_asm_match": matched,
        "functions_without_asm_match": len(funcs) - matched,
        "largest_owners": [{"owner": owner, "asm_txt_count": count} for owner, count in owners.most_common(30)],
        "important": important,
    }
    OUT_JSON.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")

    lines = [
        "# 原廠 APK Flutter AOT 組譯索引（2026-09-15）", "",
        "unflutter 會依 owner/class 把組譯檔放在遞迴子目錄；本索引使用遞迴掃描，並以 function metadata 的 AOT PC 對組譯檔名末端位址做精確連接。找不到時保留缺口，不以相似名稱冒充證據。", "",
        "## 可重現統計", "",
        f"- 組譯根目錄：`{ASM.relative_to(ROOT).as_posix()}/`",
        f"- 遞迴 `.txt` 組譯檔：**{len(asm_files):,}**，**{total_bytes:,} bytes**",
        f"- owner/class 目錄：**{len(owners):,}**",
        f"- AOT function metadata：**{len(funcs):,}**",
        f"- 有組譯檔連結：**{matched:,}**；尚未連結：**{len(funcs) - matched:,}**", "",
        "## 重要入口（直接證據）", "",
        "PC、size 與檔案路徑均來自本地原廠 `libapp.so` 的 AOT export。", "",
    ]
    for group in important:
        lines.append(f"### `{group['term']}`")
        if not group["functions"]:
            lines += ["- 找不到同名 metadata；保留此缺口。", ""]
            continue
        for row in group["functions"][:20]:
            paths = ", ".join(row["asm_paths"][:3]) or "（未找到組譯檔）"
            lines.append(f"- `{row['name']}` · PC `{row['pc']}` · size `{row['size']}` · `{paths}`")
        if len(group["functions"]) > 20:
            lines.append(f"- 其餘同群 function：{len(group['functions']) - 20} 筆（完整資料見 JSON）。")
        lines.append("")
    lines += [
        "## 解讀邊界", "",
        "- 這是 release Flutter AOT；metadata 保留 function/class/PC/size，但不會還原原始 Dart 的區域變數、註解或完整型別名稱。",
        "- 組譯連結證明入口與機器碼區段的對應，不單獨證明演算法公式；公式仍需常數池、欄位讀寫、呼叫邊與 runtime golden vectors。",
        "- `functions_without_asm_match` 是 export 命名或 thunk 的工具缺口，不等同於功能不存在。", "",
        "完整機器可讀資料：`analysis/asm_function_index.json`。", "",
    ]
    OUT_MD.write_text("\n".join(lines), encoding="utf-8")
    print(json.dumps({k: result[k] for k in ("recursive_asm_txt_count", "recursive_asm_txt_bytes", "function_count", "functions_with_asm_match", "functions_without_asm_match")}, ensure_ascii=False))


if __name__ == "__main__":
    build()
