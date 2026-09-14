# 原廠 APK 程式／演算法證據表（2026-09-15）

這份文件是「拆包後讀懂到什麼程度」的可核對紀錄。輸入是原廠 split APK 的 `libapp.so`、Flutter AOT metadata、AOT object/string/call graph、ARM64 組譯，以及原廠資源與 Smali。證據等級固定如下：

- **A（直接）**：AOT function/class metadata、組譯列、資源或 Smali 直接存在。
- **B（交叉推論）**：A 證據再加上函式名稱、常數池、欄位讀寫、呼叫邊或頁面所有權，足以界定責任範圍，但尚未證明每個公式。
- **C（待驗證）**：需要從感測器取得可重複的 golden vector，才能確認 byte order、座標方向、濾波、邊界條件或 rep/event 閾值。

## 1. 輸入與執行邊界

| 項目 | 已確認結果 | 等級 |
|---|---|---|
| 原廠套件 | `kr.piehealthcare.point.sensor`，version 1.3.5、versionCode 2026090101 | A |
| split 輸入 | `base.apk`、`split_config.arm64_v8a.apk`、`split_config.en.apk`、`split_config.xxhdpi.apk`、`split_config.zh.apk` | A |
| Flutter 核心 | ARM64 `libapp.so`，16,122,800 bytes，SHA-256 `7ea3d30692b31cf8a2346b422fbe1407663b5bd86693738790fbfa7392fb97b4` | A |
| Flutter 形態 | release/AOT；保留 function/class/PC/size 與機器碼，不保留原始 Dart 區域變數、註解與完整語意型別 | A |
| Android 外殼 | `MainActivity` extends `FlutterActivity`；`configureFlutterEngine` 註冊 plugin，建立 MethodChannel `flavor`；`ScreenRecorderService` 只處理前景通知與錄影服務生命週期 | A |
| 主要邏輯位置 | BLE、校正、各運動計算器、頁面流程在 Flutter AOT，不在可讀的 Android Java/Kotlin 業務層 | B |

完整可重現清冊見 [ORIGINAL_APP_STATIC_INVENTORY_20260915.md](ORIGINAL_APP_STATIC_INVENTORY_20260915.md)；遞迴組譯連結見 [ORIGINAL_AOT_ASM_INVENTORY_20260915.md](ORIGINAL_AOT_ASM_INVENTORY_20260915.md)。

## 2. BLE、封包與資料轉換

| AOT 入口 | PC / size | 目前可讀出的責任 | 等級 |
|---|---:|---|---|
| `BleMocapDataParser.parseReceivedPacket_1b946c` | `0x83fdac` / 352 | 封包型別分派入口 | A/B |
| `_parseRawImuBatch@859188570_1b967c` | `0x83ffbc` / 3228 | 高速 raw IMU batch parser | A |
| `_parseRawAccelGyroPrsTmp@859188570_1bd880` | `0x8441c0` / 2844 | 加速度、陀螺儀、壓力、溫度類資料 parser | A/B |
| `_parseQuatAclVelPos@859188570_1be39c` | `0x844cdc` / 5088 | quaternion／加速度／速度／位置類資料 parser | A/B |
| `_parseQuatAgzVgzPgz@859188570_1bf77c` | `0x8460bc` / 2976 | quaternion／角向與速度類資料 parser | A/B |
| `_parseQuaternionPacket@859188570_1c031c` | `0x846c5c` / 1920 | quaternion packet parser | A |
| `CommandMeasureRawImuHighRate.command_772edc` | `0xdf981c` / 152 | 發出 high-rate raw IMU 測量命令 | A |
| `BleApiImpl.startBleDeviceScan_1b6a20` | `0x83d360` / 76 | 掃描啟動入口 | A |
| `BleApiImpl._onReceivePacketFromBleDevice@859188570_1b70b0` | `0x83d9f0` / 280 | 收到裝置通知後進入 parser/stream | A/B |

### 已直接讀出的 raw IMU batch 形狀

`_parseRawImuBatch` 組譯中有以下機器碼證據：

1. 先讀 typed-data 長度，要求至少 8 bytes。
2. 讀 batch header／base timestamp 與樣本數 `N`。
3. 長度檢查符合 `8 + 12*N`；每筆以 `base + 8 + i*12` 取 6 個 signed 16-bit 值。
4. 常數池出現 `0.0047884033203125`（與 `9.80665*16/32768` 相符）及 `0.06103515625`（與 `2000/32768` 相符），另有 `1e6` 時間縮放常數。
5. 輸出欄位名稱包含 `seq`、`sampleRateHz`、`timeStamp`、`ax`、`ay`、`az`、`gx`、`gy`、`gz`；timestamp 以微秒縮放後形成秒級時間。

以上足以重建「封包長度／整數解碼／量綱」的第一版，但**不能**據此臆測第 4 個 D2 欄位的最終語意、座標正負方向、timestamp wrap 的所有邊界。已知 timestamp wrap 使用 `_rawImuTimestampWrapCountMap@859188570`。舊的 61-byte 擷取通知不符合 `8+12*N`，因此不能拿那批資料直接當作此 parser 的 golden vector；這是 C 級缺口，不是被忽略。

## 3. 裝置生命週期與校正

### 裝置流程

`DeviceConnectionManager` 的 metadata／組譯入口已確認：

- `connect` `0xf2e2b4` / 264、`disconnect` `0x860368` / 280。
- `_subscribeStreams`（`0xf2...` 區段）訂閱連線、掃描、電量、韌體、溫度等事件。
- `_onConnectionEvent` `0xf2cd1c` / 60（thunk）及 `0xf2cd58` / 996（實作）。
- `_onDeviceDiscovered` `0xf2d9e8` / 60（thunk）及 `0xf2da24` / 1200（實作）。
- `_autoRescan` `0xf2d13c` / 296，`BleDeviceList.discover` 亦存在。

這證明「掃描 → 發現 → 連線 → stream 訂閱 → 失敗重掃」在原廠 AOT 中是獨立流程；頁面不是按下測量才臨時建立唯一連線。實際自動重連等待時間、背景限制與閒置策略仍需 C 級 runtime 驗證。

### 加速度／陀螺儀校正

`AccelCalibrationUtil` 直接保留兩套狀態機：

- offset-axis：`resetAccelMeasureStateForOffsetAxisCal` `0x842888` / 504、`isAccelMeasuredAllMocapPositionForOffsetAxisCal` `0x843ba0` / 576、`validate...OffsetAxisCal` `0x843de0` / 492、`updateAccelMeasureStateForOffsetAxisCal` `0x843fcc` / 500。
- accel-bias：`isAccelMeasuredAllMocapPositionForBiasCal` `0x842cd0` / 576、`validateAccelXYZAndUpdateMeasureStateForAccelBiasCal` `0x842f10` / 492、`updateMeasureStateForAccelBiasCal` `0x8430fc` / 500。
- parser 另有 `_parseAccelOffsetAxisCalibrationPacket`、`_parseAccelBiasCalibrationPacket`、`_parseGyroCalibrationPacket`。

命名與狀態更新呼叫邊足以確認「多姿態收集 → XYZ 驗證 → 更新校正狀態」的架構（B）；每個姿態的容許誤差、平均／濾波與寫回格式仍是 C。

## 4. 原廠運動計算器與可移植入口

| 功能 | 已定位的 AOT 入口 | 目前結論 |
|---|---|---|
| Weightlifting | `JsWeightliftingCalculator._parseMetrics` `0x8cf950` / 14,740；`processMotionData` `0x8d3b38` / 396；`_handlePhaseChange` `0x8cf4d8` / 244；`_handleLiftDetected` `0x8cf7a4` / 428；`_handleCalibrationComplete` `0x8cf044` / 1,160 | 原廠有 phase/event/metrics 管線；可作為自由分析的演算法移植目標（A/B） |
| VBT | `JsVbtCalculator._parseRep` `0x962630` / 4,108；`flushPendingEmits` `0xa0132c` / 1,040；`initialize` `0x961708` / 780 | 有獨立 rep parser、pending event flush、sensor calibration warning（A） |
| Rotation | `JsRotationCalculator._parseRep` `0x99c370` / 3,448；`clearSet` `0x99b874`；`flush` `0x99b970`；`initialize` `0x9a9e20` | 有獨立旋轉 rep／set 生命週期（A/B） |
| 1RM | `OneRmTest.calculateLvp` `0x96ecdc` / 924；`get:weightVelocityMap` `0x970630` / 684；`JsOneRmCalculator.calculateLvp` `0x976f28` / 256 | 速度—負重對應與 LVP 計算可定位，但公式係數仍需 C |
| Jump / RSI | `JumpRunner.pushBatch` `0x8fe2f0` / 920；`startSession` `0x8ff120` / 536；`detectJumpCountermovement` `0x902fec` / 912；`JumpFeedbackAnalyzer.analyze` `0x92c8a4` / 1,112 | 有 batch/session、反向動作偵測與回饋分析（A/B） |
| 共用設定 | `JsWeightliftingCalculator.setCalibration`、`setBarbellWeight`、`setStartPosition`、`setCatchStyle`、`setLiftType` | 演算法不是單純裸 raw stream；會接收校正、器材重量與動作設定（A） |

這些入口是「可以移植／包裝的原廠責任邊界」，不是已經還原成可編譯 Dart。要輸出角速度、角加速度與時間序列，仍需確認 parser 的角速度欄位、校正後座標與 calculator event 的採樣時序，並以同一段感測器資料對照原廠結果。

## 5. 前端頁面與資料流所有權

頁面 class/方法已由 AOT classes/functions 建立索引；重要 owner 包含 `RootPage`、`SensorManagePage`、`LoginPage`、`WeightliftingPage`、`VBTPage`、`RotationPage`、`OneRmPage`、`JumpPage`、`RsiPage`、`MobilityPage`、`IsometricPage`、`SessionExecutionPage`、`SessionDetailPage`、`SetReplayListPage`、`SetReplayPlayerPage`、`SettingsPage`、`MultiSensorTestPage`。每個 page 的 `build` 及 notifier/service 呼叫邊已保留於 `analysis/page_owner_index.json` 與 [ORIGINAL_PAGE_SYMBOLS_20260915.md](ORIGINAL_PAGE_SYMBOLS_20260915.md)。

可讀出的流程邊界是：啟動／導航 → 登入狀態 → 裝置管理／校正 → 測量入口 → 動作 calculator → session/detail/replay → 設定與匯出。這讓後續自由分析 UI 能重用原廠資料流，而不是只複製一張圖表（B）。Firebase/auth、通知、相機等相依服務的伺服器端規則不在 APK 內，不能宣稱已完全拆出後端（A/C 邊界）。

## 6. 明確未完成／不可假裝已知的部分

1. 原始 Dart source、區域變數、註解與完整型別名稱不可由 release AOT 無損還原。
2. parser 第 4 個 D2 欄位、座標左右定義、封包 type 表與所有 timestamp wrap 邊界尚缺 golden vector。
3. 校正每個姿態的數值門檻、濾波、寫回格式尚缺可重複輸入。
4. Weightlifting/VBT/Rotation/1RM/Jump 的每個公式係數、rep 邊界與事件抑制規則尚缺 runtime 對照。
5. Firebase／登入後端與原廠雲端 API 的伺服器實作不包含在 APK；目前只能分析客戶端呼叫邊界。

下一個可驗證階段是建立「同一段 BLE 通知 → 原廠結果 → 自訂 parser/calculator 結果」的 golden-vector 表。未完成這一步前，本文件不把 B 級責任邊界誇大成公式已完全證明。
