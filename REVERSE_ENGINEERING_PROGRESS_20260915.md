# 原廠 APK 拆包／反編譯進度（2026-09-15）

## 進度檢核表

- [x] Phase A：固定原廠 split APK 輸入、SHA-256、package/version、clean Apktool decode。
- [x] Phase B：清冊 Flutter assets、locale 資源、Dex/Smali、原生 plugin/service 外殼。
- [x] Phase C：建立 Flutter AOT functions/classes/strings、objects、edges、code map 與 ARM64 組譯資料。
- [x] Phase D（靜態部分）：建立前端 page owner、導航／登入／裝置／校正／測量／session/replay 的責任索引。
- [x] Phase E（checkpoint）：將可重現的靜態清冊、前端索引、組譯索引與演算法證據表寫入 repo，準備 GitHub checkpoint。
- [ ] Phase D/E（動態驗證）：用感測器 golden vectors 對照 parser、校正、rep/event、角速度／角加速度與原廠畫面輸出。

## 本輪已產出

| 產物 | 用途 |
|---|---|
| [REVERSE_ENGINEERING_PLAN.md](REVERSE_ENGINEERING_PLAN.md) | 五種方法評分與採用的 A 方案、分階段完成定義 |
| [ORIGINAL_APP_STATIC_INVENTORY_20260915.md](ORIGINAL_APP_STATIC_INVENTORY_20260915.md) | split、manifest、assets、Dex/Smali、AOT 檔案統計 |
| [ORIGINAL_FRONTEND_ANALYSIS_20260915.md](ORIGINAL_FRONTEND_ANALYSIS_20260915.md) | AOT symbol clustering 與產品流程邊界 |
| [ORIGINAL_PAGE_SYMBOLS_20260915.md](ORIGINAL_PAGE_SYMBOLS_20260915.md) | page owner/class、build 方法與 PC 索引 |
| [ORIGINAL_AOT_ASM_INVENTORY_20260915.md](ORIGINAL_AOT_ASM_INVENTORY_20260915.md) | 4.7 萬個遞迴 ARM64 組譯檔與 function-PC 連結 |
| [ORIGINAL_ALGORITHM_EVIDENCE_20260915.md](ORIGINAL_ALGORITHM_EVIDENCE_20260915.md) | BLE parser、校正、裝置生命週期、運動計算器與前端責任的 A/B/C 證據表 |

## 可重現工具

- `analysis/build_original_inventory_clean.py`：以 clean 原廠 decode 產生靜態清冊。
- `analysis/build_frontend_analysis_index.py`：由 AOT metadata 產生前端／功能 symbol 索引。
- `analysis/build_page_owner_index.py`：產生 page owner 索引。
- `analysis/build_asm_index_final.py`：遞迴讀取組譯首列的實際 PC，避免把檔名 offset 當成執行位址。

## 邊界與下一步

release Flutter AOT 可以快速解出機器碼與符號責任邊界，但不能無損還原原始 Dart 變數／註解，也不能只靠命名宣稱公式已證明。剩餘工作是 runtime golden-vector：固定同一段 BLE 通知與姿態校正輸入，逐欄對照原廠 raw/derived output、時間戳、事件與圖表。手機在這個靜態階段不需要持續連線。
