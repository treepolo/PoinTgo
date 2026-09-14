#!/usr/bin/env python3
"""Join unflutter's recursive assembly files to AOT function metadata.

Assembly filenames contain a Dart/codegen offset, while ``functions.jsonl``
uses the executable PC.  The first printable assembly line carries the real
PC (for example ``0x0083fdac ...``), so this final index keys on that line.
"""

from __future__ import annotations

import json
import re
from collections import Counter, defaultdict
from pathlib import Path

from build_asm_index_v2 import IMPORTANT, ROOT, AOT, ASM, FUNCTIONS, OUT_JSON, OUT_MD, jsonl


PC_RE = re.compile(rb"(?:^|\n)\s*(0x[0-9a-fA-F]{5,})\s+[0-9a-fA-F]{2}\s+[0-9a-fA-F]{2}", re.MULTILINE)


def read_asm_pc(path: Path):
    # Most files are ordinary Win32 paths.  Retry the extended-length form for
    # generated owner names that exceed MAX_PATH.
    data = b""
    try:
        with path.open("rb") as fh:
            data = fh.read(8192)
    except OSError:
        try:
            with open("\\\\?\\" + str(path), "rb") as fh:
                data = fh.read(8192)
        except OSError:
            return None
    match = PC_RE.search(data)
    return int(match.group(1), 16) if match else None


def file_size(path: Path):
    try:
        return path.stat().st_size
    except OSError:
        try:
            return Path("\\\\?\\" + str(path)).stat().st_size
        except OSError:
            return 0


def build():
    asm_files = sorted(ASM.rglob("*.txt")) if ASM.exists() else []
    by_pc = defaultdict(list)
    by_stem = defaultdict(list)
    owners = Counter()
    total_bytes = 0
    unreadable = 0
    for path in asm_files:
        rel = path.relative_to(ROOT).as_posix()
        total_bytes += file_size(path)
        owners[path.parent.name] += 1
        by_stem[path.name[:-4]].append(rel)
        pc = read_asm_pc(path)
        if pc is None:
            unreadable += 1
        else:
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
        "assembly_files_without_pc": unreadable,
        "function_count": len(funcs),
        "functions_with_asm_match": matched,
        "functions_without_asm_match": len(funcs) - matched,
        "largest_owners": [{"owner": owner, "asm_txt_count": count} for owner, count in owners.most_common(30)],
        "important": important,
    }
    OUT_JSON.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")

    lines = [
        "# 原廠 APK Flutter AOT 組譯索引（2026-09-15）", "",
        "unflutter 會依 owner/class 把組譯檔放在遞迴子目錄；本索引使用遞迴掃描。組譯檔名尾端是 codegen offset，不是 function metadata 的執行 PC，因此實際從每個檔案的第一個組譯列（`0x... bytes instruction`）讀取 PC，再做精確連接。", "",
        "## 可重現統計", "",
        f"- 組譯根目錄：`{ASM.relative_to(ROOT).as_posix()}/`",
        f"- 遞迴 `.txt` 組譯檔：**{len(asm_files):,}**，**{total_bytes:,} bytes**",
        f"- owner/class 目錄：**{len(owners):,}**",
        f"- 含可解析 PC 的組譯檔：**{len(asm_files) - unreadable:,}**；未解析：**{unreadable:,}**",
        f"- AOT function metadata：**{len(funcs):,}**",
        f"- 有組譯檔連結：**{matched:,}**；尚未連結：**{len(funcs) - matched:,}**", "",
        "## 重要入口（直接證據）", "",
        "PC、size 與檔案路徑均來自本地原廠 `libapp.so` 的 AOT export；路徑是相對 repo 的可重現位置。", "",
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
    print(json.dumps({k: result[k] for k in ("recursive_asm_txt_count", "recursive_asm_txt_bytes", "assembly_files_without_pc", "function_count", "functions_with_asm_match", "functions_without_asm_match")}, ensure_ascii=False))


if __name__ == "__main__":
    build()
