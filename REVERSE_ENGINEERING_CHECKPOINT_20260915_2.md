# 原廠 APK 反向工程 checkpoint 2（2026-09-15）

## 本 checkpoint 增加的可核對成果

- native Smali 完整索引：15,400 files/classes；clean Manifest 34 components、33 permissions；確認原廠自己的 Android 外殼 class 只有 `MainActivity` 與 `ScreenRecorderService`。
- BLE 原生橋接：從 `flutter_mocap_lib` plugin (`a3/n`) 讀出 MethodChannel/EventChannel、掃描／連線／斷線／傳輸／DFU 命令、權限錯誤分支與 UART/DFU UUID。
- AOT 功能呼叫圖：以原始 214,034 JSONL records 分開統計 187,171 個 named call edges，按 app shell、auth、BLE、校正、運動 calculator、session、profile、media 等 12 群組建立跨責任摘要。
- AOT 全量覆蓋量：46,790 functions、6,696 classes 分到保守責任類別；另外標出 obfuscated/codegen 與 other/unknown，避免把名稱搜尋誤當成完全理解。
- 資料模型／Persistence：定位設定、auth/profile/team、校正紀錄、Isometric/Jump/Mobility/1RM/Rotation/RSI/Throws/Swing/VBT/Weightlifting repository 與 session↔Firestore 轉換入口。

## 新產物

- [ORIGINAL_BLE_NATIVE_BRIDGE_20260915.md](ORIGINAL_BLE_NATIVE_BRIDGE_20260915.md)
- [ORIGINAL_NATIVE_SMAILI_ANALYSIS_20260915.md](ORIGINAL_NATIVE_SMAILI_ANALYSIS_20260915.md)
- [ORIGINAL_FEATURE_CALLGRAPH_20260915.md](ORIGINAL_FEATURE_CALLGRAPH_20260915.md)
- [ORIGINAL_CODE_COVERAGE_20260915.md](ORIGINAL_CODE_COVERAGE_20260915.md)
- [ORIGINAL_DATA_MODEL_PERSISTENCE_20260915.md](ORIGINAL_DATA_MODEL_PERSISTENCE_20260915.md)

機器可讀 JSON 與重跑腳本位於 `analysis/`；原始 APK、clean decode 與 AOT dump 仍以本機固定 hash 的輸入保存，不把大型二進位 APK 硬塞進 Git。

## 尚未可宣稱完成的部分

靜態責任邊界已大幅覆蓋，但 release Flutter AOT 不含原始 Dart 變數／註解，且精確 parser 欄位、校正門檻、座標方向、rep/event 邊界與雲端規則尚缺 runtime golden-vector。Goal 仍保持 active，下一步是逐條建立可重複的原廠輸入→輸出對照，而不是把剩餘未知欄位猜完。
