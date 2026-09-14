# Poin*T Go 原廠 Flutter 前端與資料流分析索引

生成日期：2026-09-15。索引由保存的 release AOT metadata、呼叫邊、字串參照與官方 Flutter assets 產生；不需要手機。

## 覆蓋量

- 函式 46,790、class 6,696、字串參照 42,304、呼叫邊 214,034、object 59,235。原始索引檔案列在 `analysis/frontend_analysis_index.json`。
- 這些是 release AOT 的符號／PC／關係索引，不是捏造的 Dart 原始碼；去除名稱的函式仍保留其 PC 與呼叫關係。

## 前端／功能群組（依 symbol 與交叉參照聚類）

| 群組 | 函式命中 | 唯一名稱 | 可追溯入口例子 |
| --- | ---: | ---: | --- |
| auth_login | 241 | 241 | `AppleAuthProvider.AppleAuthProvider._33af5c`, `AppleAuthProvider_33affc`, `AuthCredential.toString_6a0fdc`, `AuthCredential_130984`, `AuthException_339734` |
| device_ble | 2,329 | 2,329 | `$enumDecodeNullable_88d8ec`, `AccelCalibrationUtil.isAccelMeasuredAllMocapPositionForBiasCal_1bc390`, `AccelCalibrationUtil.isAccelMeasuredAllMocapPositionForOffsetAxisCal_1bd260`, `Action.Action.overridable_4f0174`, `Action._isEnabled@215441002_217a1c` |
| calibration | 285 | 285 | `AccelAxisCalibrationStateNotifier.updateEvent_1c4674`, `AccelAxisCalibrationStateNotifier_1b5384`, `AccelAxisCalibrationWidget.build_1c2a78`, `AccelAxisCalibrationWidget_1b45f4`, `AccelBiasCalibrationEvent._enumToString@0150898_763580` |
| weightlifting_throw | 2,422 | 2,422 | `AthleteProfileDataSource._weightliftingTracesRef@1190416904_89bcd8`, `AthleteProfileDataSource.getWeightliftingTraces_89bb70`, `AthleteProfileDataSource.saveExerciseSessionWithWeightliftingTraces_89d30c`, `AthleteProfileDataSource.saveWeightliftingTraces_89c6e0`, `CameraController._throwIfNotInitialized@627373842_1ea62c` |
| vbt | 2,226 | 2,226 | `AnalyticsVbtLvpChart._buildChartData@1393228830_605564`, `AnalyticsVbtLvpChart._buildRecencyLegend@1393228830_618a2c`, `AnalyticsVbtLvpChart._formatWeight@1393228830_6067d0`, `AnalyticsVbtLvpChart._niceInterval@1393228830_6064b4`, `AnalyticsVbtLvpChart._noData@1393228830_618f8c` |
| rotation | 835 | 835 | `AnimatedRotation.createState_57e374`, `AnimatedRotation_5a69d4`, `AppSettingManager.rotationRepPerformanceKey_31ef00`, `AppSettingManager.saveRotationRepPerformanceKpi_31fb70`, `BleMocapDataParser._parseQuaternionPacket@859188570_1c031c` |
| one_rm | 1,018 | 1,018 | `AnalyticsVbtLvpChart._buildChartData@1393228830_605564`, `AnalyticsVbtLvpChart._buildRecencyLegend@1393228830_618a2c`, `AnalyticsVbtLvpChart._formatWeight@1393228830_6067d0`, `AnalyticsVbtLvpChart._niceInterval@1393228830_6064b4`, `AnalyticsVbtLvpChart._noData@1393228830_618f8c` |
| jump_rsi | 1,862 | 1,862 | `AnalyticsJumpInsight._buildElastic@1391393462_6157e0`, `AnalyticsJumpInsight._buildSymmetry@1391393462_614dd0`, `AnalyticsJumpInsight._chip@1391393462_615644`, `AnalyticsJumpInsight._signed@1391393462_616678`, `AnalyticsJumpInsight.build_61493c` |
| history_session | 1,223 | 1,223 | `AVAudioSessionCategory._enumToString@0150898_75e4a8`, `AVAudioSessionOptions._enumToString@0150898_75e50c`, `AndroidCameraCameraX.stopVideoRecording_80a234`, `AthleteProfileDataSource._buildSessionsQuery@1190416904_8a23fc`, `AthleteProfileDataSource._parseSessions@1190416904_8a070c` |
| settings_profile | 839 | 839 | `AddBodyMeasurementSheet.build_2f9aa8`, `AddBodyMeasurementSheet.handleSubmit_2faacc`, `AddBodyMeasurementSheet_3d1d68`, `AndroidCameraCameraX.createCameraWithSettings_811b78`, `AndroidInitializationSettingsMapper|toMap_8c853c` |
| camera_notification | 528 | 528 | `AndroidCameraCameraX.AndroidCameraCameraX._8c5838`, `AndroidCameraCameraX._bindUseCaseToLifecycle@24493391_230680`, `AndroidCameraCameraX._cameraEvents@24493391_17e6d0`, `AndroidCameraCameraX._configureImageAnalysis@24493391_1eb8ec`, `AndroidCameraCameraX._createCameraClosingObserver@24493391_230d5c` |
| frontend_navigation | 1,961 | 1,961 | `AbstractLayoutBuilder.createElement_67365c`, `AccelAxisCalibrationWidget.build_1c2a78`, `AccentActionCard.build_5c5e28`, `AccountDeletionPage._isPersonalUser@1451476342_338488`, `AccountDeletionPage.build_3381e4` |
| cloud_firestore | 188 | 188 | `AthleteProfileDataSource._measurementFromFirestore@1190416904_886b8c`, `AthleteProfileDataSource._measurementFromFirestore@1190416904_886bc8`, `AthleteProfileDataSource._measurementToFirestore@1190416904_89f008`, `AthleteProfileDataSource._programFromFirestore@1190416904_88b088`, `AthleteProfileDataSource._sessionFromFirestore@1190416904_8a0838` |

## 已建立的前端流程邊界

1. **啟動／導航**：Flutter `MainActivity` 是 Android 入口；AOT 中以 `Page`／`Screen`／`Route`／`Navigator`／`build`／底部導航等函式聚類出頁面建立與導航層。
2. **登入／帳號**：`AuthService`、`authStateChanges`、`currentUser`、`validateSession`、`LoginPage` 與 Google 登入相關函式及字串形成帳號邊界；雲端同步不能等同本機感測器流程。
3. **裝置／BLE**：`BleMocapDataParser`、命令物件、`BLEService`、裝置掃描／連線／設定與 raw／四元數／速度／位置 parser 形成資料入口；GATT UUID/封包證據另見 `analysis/aot-parser-final.md` 與 `analysis/hci/` 摘要。
4. **校正／姿態**：六面 accel bias／offset-axis、gyro calibration、重力／四元數與 validation 函式是校正狀態機；`assets/img/placement` 與裝置示意資源是其前端引導資源。
5. **訓練入口**：甩球／揮擊／slam／weightlifting、VBT、rotation、1RM/LVP、jump/CMJ/RSI、mobility/isometric 等分開聚類；不得用單一 rep 閾值解讀所有入口。
6. **結果／歷史**：`Session`、`History`、`BestRecord`、weekly summary、feedback／grade／fatigue 函式與 JSON model 形成結果保存、摘要與回放邊界。
7. **設定／媒體／通知**：device/profile/setting、camera recording、screen recorder、local notification、wakelock、Firebase messaging 等是獨立 side-effect 層。

## Traditional Chinese 前端資源

- AOT 語系 getter class `SZhTw` 可直接定位 1,095 個 getter/PC；完整 property/PC 在 JSON。
- 官方 `split_config.zh.apk` 的 `values-zh-rTW`、`values-zh-rHK`、`values-zh-rCN` 已保存於 `analysis/official_clone/build/zh-decoded/res/values-*`；Flutter 自己的 UI 文案則在 AOT string pool，不會出現在 Android `strings.xml`。

## 資料流摘要

`BLE/GATT → parser → Raw/Quaternion/Global data model → calibration/derived motion → module calculator → rep/event/result model → local history/cloud/feedback UI`

每個箭頭的函式與 object/call-edge 證據都可由 `source_paths` 回溯；函式的實際 ARM64 指令在 `analysis/aot_unflutter/asm/`，而非在本索引中重複。

## 尚未能由靜態檔案單獨證明的項目

- release AOT 的部分函式名稱被縮短或匿名化，無法只靠 symbol 得到原始 Dart 變數名與註解。
- 伺服器端 Firebase/Firestore 規則、帳號授權結果與某些模型的實際資料內容不會完整嵌在 APK；靜態索引只能證明 client 邊界。
- 各算法的精確浮點常數、濾波器初始狀態、rep 邊界與 UI 顯示條件仍須以官方 runtime golden vectors 補強；這是待驗證，不偽稱已完全還原。
