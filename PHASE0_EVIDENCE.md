# Phase 0：官方相容功能證據

日期：2026-09-14

這份文件記錄私人副本可以安全移植的官方 APK 證據。反組譯結果只作為資料欄位、事件名稱與入口的追溯來源；在沒有實機 golden vector 前，不把重建公式宣稱為官方原始實作。

## 官方封包與語系

- 官方套件：`kr.piehealthcare.point.sensor`，版本 1.3.5。
- 已保存的 base/split APK：`analysis/official_clone/original/`。
- Flutter AOT：Dart 3.10.7，函式索引及反組譯在 `analysis/aot_unflutter/`。
- `split_config.zh.apk` 內含 `values-zh-rTW`、`values-zh-rHK`、`values-zh-rCN`；原本的副本建置腳本只重建 base，沒有把此 split 的 values 合併，所以 AndroidX/Google 介面會退回英文。AOT 內的 Traditional Chinese `SZhTw` 字串仍然正確，並非亂碼來源。

## 校正與連續資料

AOT 函式索引確認以下資料層：

- `CommandStartAccelCal`、`CommandCalibrateAccelLeft/Right/Front/Back/Top/Bottom`、`CommandCompleteAccelCal`：六面加速度校正命令。
- `AccelCalibrationUtil.updateMeasureStateForAccelBiasCal`、`updateAccelMeasureStateForOffsetAxisCal`、對應的 `validate...` 和 `isAccelMeasuredAllMocapPosition...`：六面狀態與有效性判斷。
- `BleMocapDataParser._parseAccelBiasCalibrationPacket`、`_parseAccelOffsetAxisCalibrationPacket`、`_parseGyroCalibrationPacket`：裝置回傳校正資料的解析入口。
- `BleMocapDataParser._parseRawImuBatch`、`_parseRawAccelGyroPrsTmp`、`_parseQuatAclVelPos`、`_parseQuatAgzVgzPgz`、`_parseQuaternionPacket`：raw IMU、四元數、加速度、速度、位置等資料入口。
- `GlobalAcceleration`、`GlobalVelocity`、`RotationQuaternion` 與 `PoinTGOMotionData` 資料模型存在於 AOT；因此自由分析要保存「校正後／全域」資料，不能只保存 raw magnitude。

## 動作模組入口

- 甩球／投擲／重量訓練：`JsWeightliftingCalculator.processMotionData`、`toWeightliftingMotionData`、`_parseMetrics`。可追溯欄位包含 first/second/third pull、jerk、hook、velocityData 與 successRate。
- VBT：`JsVbtCalculator._parseRep` 與 `MotionDataConverter.toVbtMotionData`。解析欄位包含 `rangeOfMotion`、`velocityLoss`、`meanPowerPerMass`、`peakTimestamp`；每個 rep 要保存區段與逐點 velocityData。
- 角運動：`JsRotationCalculator._parseRep` 與 `MotionDataConverter.toRotationMotionData`；AOT 另外有 `RotationQuaternion` 和 rotation performance/grade 資料模型。
- 1RM：`OneRmTest.calculateLvp`、`OneRmTest.weightVelocityMap`、`JsOneRmCalculator.calculateLvp`；結果模型包含 attempts、weight/velocity map 與 LVP 可計算狀態。
- 跳躍／反向跳：`JumpRunner.pushBatch`、`startSession`、`initialize`、`detectJumpCountermovement`、`computeJumpComSway`、`computeLandingAbsorbSec`、`JumpDataNotifier.attachPoseMetrics`、`JumpFeedbackAnalyzer`。AOT 資料模型包含 jump records、takeoff/landing、飛行時間、COM sway、landing absorb 與 RSI 相關輸出。
- 次數／事件：`markRepBoundary` 與各模組的 rep/result model 表示次數是按模組定義判定，不應以同一個閾值函式冒充所有動作。

## 登入邊界

反組譯索引有 `AuthService.authStateChanges`、`currentUser`、`validateSession`、`LoginPage.build` 與 Google 登入處理，但沒有 `signInAnonymously`、guest token 或 visitor auth API。故私人副本採：

1. 啟動後建立本機 guest session，允許 BLE、校正、分析、回放、匯出。
2. 需要帳號、同步或雲端歷史時，仍可按「登入官方帳戶」進入原廠登入頁。
3. 不修改官方套件，也不偽造 Firebase/Google 身分。

## 實作標示

新引擎會在每筆 session 寫入 `algorithmVersion`、`calibrationProfile`、`source` 與 `confidence`。能由 AOT 與封包直接驗證的部分標為 `vendor-compatible`；尚待官方 golden vector 對齊的部分標為 `vendor-compatible-reconstruction`，以免把合理的物理重建誤稱為官方原式。
