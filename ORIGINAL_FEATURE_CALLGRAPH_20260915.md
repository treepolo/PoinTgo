# 原廠 Flutter AOT 功能呼叫邊索引（2026-09-15）

這份報告從原始 call-edge export 逐筆建立功能責任邊界。feature 是符號群組，不是重新命名原始 Dart；`sub_*`、thunk 與 framework target 保留原名。

- 原始 JSONL records：**214,034**
- 含 `from_func` + `target` 的 named call edges：**187,171**
- 其餘 records（沒有完整 named call pair）：**26,863**
- 功能群組：**12**
- 證據等級：A（直接呼叫邊）；群組交集推導流程方向屬 B，不當成完整 source-level control flow。

## 功能群組摘要

| 群組 | source functions | edges | unique targets | 主要可讀 target |
|---|---:|---:|---:|---|
| `app_shell` | 357 | 2,240 | 636 | ModalRoute._maybeDispatchNavigationNotification@286188637_b089c, _$VBTSettingImpl@1234411512_1aa4c8, NavigatorState._flushHistoryUpdates@266124995_abcf0, FirebaseException_12cc2c, FirestorePigeonFirebaseApp_1417a0 |
| `auth` | 25 | 350 | 114 | AuthException_339734, FirebaseAuth.get:currentUser_134604, AuthService._setCurrentAppUserInfo@1205060975_1afd1c, AuthService.signOut_2f5378, FirebaseAuth.get:_delegate@699464419_12dcd0 |
| `device_ble` | 129 | 1,456 | 251 | BleApiImpl._callMethod@859188570_1b5a64, PoinTGoService.updateDeviceState_8a6fac, BleMocapDataParser._generateMapDataForUnknownPacket@859188570_1b95cc, _$AccelCalibrationDataImpl@1117427666_1b8d4c, BleMocapDataParser._generateMapDataForEmptyPacket@859188570_1b9624 |
| `calibration` | 56 | 576 | 122 | CalibrationRecordRepository._loadRecords@1222276897_1b4d8c, AccelCalibrationUtil.resetAccelMeasureStateForOffsetAxisCal_1bbf48, AccelCalibrationUtil._printAccelMeasureStateMapForOffsetAxisCal@859188570_1bc140, CalibrationStateNotifier.clear_1c4774, CalibrationStateNotifier.complete_1c473c |
| `weightlifting` | 198 | 3,283 | 417 | _$WeightliftingImpl@1410498253.get:lifts_10c524, _$WeightliftingImpl@1410498253_247fbc, JsWeightliftingCalculator._callWithEvents@1493039605_248118, Weightlifting.get:bestPeakVelocity_10c458, Weightlifting.get:avgFirstPullVelocity_164028 |
| `vbt` | 241 | 4,318 | 514 | _$VBTImpl@1189259567.get:sets_27d2cc, _$VBTImpl@1189259567_30b0fc, _$VBTSetImpl@1189259567_30b220, VBTContentLocalization|localizedLabel_2e1218, VBTContent.fromIdx_17e5ec |
| `rotation` | 127 | 2,045 | 315 | _$RotationImpl@1401451035.get:sets_3141c0, _$RotationImpl@1401451035_314f1c, RotationDataNotifier._update@1422200199_314ed0, RotationDataNotifier.currentSet_31683c, Transform._createZRotation@224167661_1d12bc |
| `one_rm` | 128 | 1,777 | 336 | _$OneRmTestImpl@1352308653.get:attempts_2e3090, _$OneRmTestImpl@1352308653_2e31e8, OneRmTest.get:weightVelocityMap_2e9cf0, OneRmDataNotifier.get:progressionWeights_2eb68c, OneRmTest.calculateLvp_2e839c |
| `jump_rsi` | 259 | 3,835 | 446 | _$JumpImpl@1259142584.get:records_26b290, _$RsiImpl@1099028742.get:records_1634d0, Rsi.get:top5_1633fc, _$JumpImpl@1259142584_2779a4, _$RsiImpl@1099028742_2b7ba0 |
| `sessions_history` | 469 | 6,425 | 896 | VBTContent.fromIdx_17e5ec, VBTContentLocalization|localizedLabel_2e1218, _$ReplayPhaseImpl@1435425036_273e30, AthleteProfileDataSource._propagateToResult@1190416904_8a204c, SetReplayCaptureService._discardDir@1317488692_272564 |
| `settings_profile` | 243 | 2,690 | 535 | FirebaseFirestore.collection_1414e4, AthleteProfileDataSource._propagateToResult@1190416904_8a204c, FirebaseAuth.get:currentUser_134604, defaultScrollNotificationPredicate_18b2a0, _FixedScrollMetrics&Object&ScrollMetrics@294372966.get:axis_4ac568 |
| `media_cloud` | 785 | 6,911 | 1,148 | CameraException_1e9fe0, FirestoreMessageCodec.writeValue_7bd1f8, Firebase.app_12c698, _extractReplyValueOrThrow@654275743_13a96c, CameraRecordingScaffold_1e708c |

## 可讀的跨責任呼叫例子

### `app_shell`
- `app_shell -> media_cloud`：18 edges
- `app_shell -> settings_profile`：9 edges
- `app_shell -> sessions_history`：8 edges
- `app_shell -> jump_rsi`：1 edges
- 例：`ModalRoute.get:popDisposition_90688` → `_ModalRoute&TransitionRoute&LocalHistoryRoute@286188637.get:popDisposition_90798` (bl)
- 例：`_ModalRoute&TransitionRoute&LocalHistoryRoute@286188637.get:popDisposition_90798` → `_ModalRoute&TransitionRoute&LocalHistoryRoute@286188637.get:willHandlePopInternally_90838` (bl)
- 例：`ModalRoute.didPopNext_b06e4` → `ModalRoute._maybeDispatchNavigationNotification@286188637_b089c` (bl)
- 例：`ModalRoute._maybeDispatchNavigationNotification@286188637_b089c` → `NavigationNotification_b0c2c` (bl)
- 例：`ModalRoute._maybeDispatchNavigationNotification@286188637_b089c` → `Notification.dispatch_b0adc` (bl)

### `auth`
- `auth -> media_cloud`：24 edges
- `auth -> app_shell`：8 edges
- `app_shell -> media_cloud`：4 edges
- `auth -> settings_profile`：2 edges
- `auth -> sessions_history`：1 edges
- 例：`AuthService.authStateChanges_12d198` → `FirebaseAuth.authStateChanges_12d1f0` (bl)
- 例：`AuthService.get:currentUser_1345ac` → `FirebaseAuth.get:currentUser_134604` (bl)
- 例：`FirebaseAuth.get:currentUser_134604` → `FirebaseAuth.get:_delegate@699464419_12dcd0` (bl)
- 例：`FirebaseAuth.get:currentUser_134604` → `FirebaseAuth.get:_delegate@699464419_12dcd0` (bl)
- 例：`AuthService.refreshAppUserInfo_1afc60` → `FirebaseAuth.get:currentUser_134604` (bl)

### `device_ble`
- `device_ble -> calibration`：10 edges
- `device_ble -> app_shell`：3 edges
- `device_ble -> rotation`：2 edges
- 例：`SensorManager.transmitBlePacket_1b58ac` → `BleApiImpl.transmitBleCommandPacket_1b59a8` (bl)
- 例：`BleApiImpl.transmitBleCommandPacket_1b59a8` → `BleApiImpl._callMethod@859188570_1b5a64` (bl)
- 例：`BleApiImpl._callMethod@859188570_1b5a64` → `BleOperationResult_1b5c24` (bl)
- 例：`SensorManager.SensorManager._internal@1116043306_1b5de8` → `BleApi.BleApi._1b6100` (bl)
- 例：`BleApi.BleApi._1b6100` → `BleApiImpl_1c2148` (bl)

### `calibration`
- `calibration -> media_cloud`：2 edges
- 例：`_$WeightliftingCalibrationImpl@1129372492.dyn:toJson_169fe0` → `_$$WeightliftingCalibrationImplToJson@1129372492_16a07c` (bl)
- 例：`CalibrationPopup.build_1b4080` → `GyroCalibrationWidget_1b4600` (bl)
- 例：`CalibrationPopup.build_1b4080` → `AccelAxisCalibrationWidget_1b45f4` (bl)
- 例：`CalibrationRecordRepository.saveCalibrationDate_1b4c8c` → `CalibrationRecordRepository._loadRecords@1222276897_1b4d8c` (bl)
- 例：`BleMocapDataParser._parseAccelBiasCalibrationPacket@859188570_1bb788` → `BleMocapDataParser._printHex@859188570_1bca54` (bl)

### `weightlifting`
- `weightlifting -> media_cloud`：10 edges
- `weightlifting -> sessions_history`：10 edges
- `weightlifting -> calibration`：6 edges
- `weightlifting -> app_shell`：6 edges
- 例：`Weightlifting.get:bestPeakVelocity_10c458` → `_$WeightliftingImpl@1410498253.get:lifts_10c524` (bl)
- 例：`Weightlifting.get:bestPeakVelocity_10c458` → `_$WeightliftingImpl@1410498253.get:lifts_10c524` (bl)
- 例：`_$WeightliftingImpl@1410498253.dyn:toJson_10c580` → `_$$WeightliftingImplToJson@1410498253_10c5c8` (bl)
- 例：`_$WeightliftingLiftImpl@1410498253.dyn:toJson_10c838` → `_$$WeightliftingLiftImplToJson@1410498253_10c8a0` (bl)
- 例：`_$WeightliftingBestRecordImpl@1115002182.dyn:toJson_1396f8` → `_$$WeightliftingBestRecordImplToJson@1115002182_139760` (bl)

### `vbt`
- `vbt -> app_shell`：7 edges
- `vbt -> sessions_history`：5 edges
- `vbt -> media_cloud`：4 edges
- `vbt -> calibration`：1 edges
- 例：`_$VBTSettingImpl@1234411512.dyn:toJson_892e0` → `_$$VBTSettingImplToJson@1234411512_89328` (bl)
- 例：`_$VbtSetImpl@1129372492.dyn:toJson_119d04` → `_$$VbtSetImplToJson@1129372492_119d4c` (bl)
- 例：`_$VbtRepImpl@1129372492.dyn:toJson_11a01c` → `_$$VbtRepImplToJson@1129372492_11a084` (bl)
- 例：`_$WorkoutVbtResultImpl@1129372492.dyn:toJson_11a754` → `_$$WorkoutVbtResultImplToJson@1129372492_11a79c` (bl)
- 例：`_$$WorkoutVbtResultImplToJson@1129372492_11a79c` → `_$WorkoutVbtResultImpl@1129372492.get:weightliftingDrillIdxs_11aa84` (bl)

### `rotation`
- `rotation -> sessions_history`：5 edges
- `rotation -> app_shell`：3 edges
- `app_shell -> sessions_history`：1 edges
- `rotation -> media_cloud`：1 edges
- 例：`_$WorkoutRotationResultImpl@1129372492.dyn:toJson_164c28` → `_$$WorkoutRotationResultImplToJson@1129372492_164c70` (bl)
- 例：`_$WorkoutRotationSetImpl@1129372492.dyn:toJson_164e20` → `_$$WorkoutRotationSetImplToJson@1129372492_164e88` (bl)
- 例：`_$WorkoutRotationRepImpl@1129372492.dyn:toJson_1650b0` → `_$$WorkoutRotationRepImplToJson@1129372492_1650f8` (bl)
- 例：`_$ProgramRotationDataImpl@1128248788.dyn:toJson_16fdf4` → `_$$ProgramRotationDataImplToJson@1128248788_171514` (bl)
- 例：`_$RotationQuaternionImpl@1097462921.dyn:toJson_1b9070` → `_$$RotationQuaternionImplToJson@1097462921_1b9208` (bl)

### `one_rm`
- `one_rm -> vbt`：9 edges
- `one_rm -> sessions_history`：6 edges
- `one_rm -> media_cloud`：2 edges
- `one_rm -> app_shell`：2 edges
- 例：`_$OneRmBestRecordImpl@1115002182.dyn:toJson_13a0f4` → `_$$OneRmBestRecordImplToJson@1115002182_13a13c` (bl)
- 例：`_$OneRmBestRecordFromJson@1115002182_13e10c` → `_$$OneRmBestRecordImplFromJson@1115002182_13e138` (bl)
- 例：`_$$OneRmBestRecordImplFromJson@1115002182_13e138` → `_$OneRmBestRecordImpl@1115002182_13e24c` (bl)
- 例：`_$WorkoutOneRmResultImpl@1129372492.dyn:toJson_16fd1c` → `_$$WorkoutOneRmResultImplToJson@1129372492_17007c` (bl)
- 例：`_$ProgramOneRmDataImpl@1128248788.dyn:toJson_16fecc` → `_$$ProgramOneRmDataImplToJson@1128248788_17188c` (bl)

### `jump_rsi`
- `jump_rsi -> sessions_history`：13 edges
- `jump_rsi -> app_shell`：8 edges
- `jump_rsi -> media_cloud`：3 edges
- 例：`_$JumpSettingImpl@1234411512.dyn:toJson_89068` → `_$$JumpSettingImplToJson@1234411512_890b0` (bl)
- 例：`_$$JumpSettingImplToJson@1234411512_890b0` → `_$JumpSettingImpl@1234411512.get:lastBoxHeightM_8919c` (bl)
- 例：`_$RsiBestRecordImpl@1115002182.dyn:toJson_139e94` → `_$$RsiBestRecordImplToJson@1115002182_139efc` (bl)
- 例：`_$JumpBestRecordImpl@1115002182.dyn:toJson_13a288` → `_$$JumpBestRecordImplToJson@1115002182_13a2f0` (bl)
- 例：`normalizeRsiBestRecord_13c2e8` → `_$RsiBestRecordImpl@1115002182_13c3e8` (bl)

### `sessions_history`
- `sessions_history -> vbt`：43 edges
- `sessions_history -> jump_rsi`：37 edges
- `sessions_history -> app_shell`：28 edges
- `sessions_history -> media_cloud`：23 edges
- `sessions_history -> weightlifting`：22 edges
- `sessions_history -> rotation`：11 edges
- `sessions_history -> one_rm`：10 edges
- `sessions_history -> settings_profile`：7 edges
- `vbt -> app_shell`：4 edges
- `weightlifting -> media_cloud`：3 edges
- 例：`_$HistorySettingImpl@1234411512.dyn:toJson_896bc` → `_$$HistorySettingImplToJson@1234411512_89704` (bl)
- 例：`_$$HistorySettingImplToJson@1234411512_89704` → `_$HistorySettingImpl@1234411512.get:setPerformanceKpis_89884` (bl)
- 例：`_ModalRoute&TransitionRoute&LocalHistoryRoute@286188637.get:popDisposition_90798` → `_ModalRoute&TransitionRoute&LocalHistoryRoute@286188637.get:willHandlePopInternally_90838` (bl)
- 例：`NavigatorState._flushHistoryUpdates@266124995_abcf0` → `NavigatorState._flushObserverNotifications@266124995_afc40` (bl)
- 例：`NavigatorState._flushHistoryUpdates@266124995_abcf0` → `_HistoryProperty@266124995.update_accec` (bl)

### `settings_profile`
- `settings_profile -> media_cloud`：39 edges
- `settings_profile -> sessions_history`：11 edges
- `settings_profile -> auth`：10 edges
- `settings_profile -> app_shell`：10 edges
- `settings_profile -> weightlifting`：5 edges
- `weightlifting -> media_cloud`：5 edges
- `sessions_history -> media_cloud`：4 edges
- `settings_profile -> rotation`：3 edges
- `sessions_history -> jump_rsi`：2 edges
- 例：`_updateUserSettingsData@17065589_95594` → `PlatformDispatcher._updateUserSettingsData@17065589_955e8` (bl)
- 例：`_updateUserSettingsData@17065589_95d30` → `_updateUserSettingsData@17065589_95594` (bl)
- 例：`ModalRoute._maybeDispatchNavigationNotification@286188637_b089c` → `NavigationNotification_b0c2c` (bl)
- 例：`ModalRoute._maybeDispatchNavigationNotification@286188637_b089c` → `Notification.dispatch_b0adc` (bl)
- 例：`ModalRoute._maybeDispatchNavigationNotification@286188637_b0aa4` → `ModalRoute._maybeDispatchNavigationNotification@286188637_b089c` (bl)

### `media_cloud`
- `media_cloud -> app_shell`：12 edges
- `media_cloud -> sessions_history`：4 edges
- `media_cloud -> settings_profile`：3 edges
- `settings_profile -> rotation`：3 edges
- `settings_profile -> app_shell`：2 edges
- `auth -> app_shell`：2 edges
- `media_cloud -> rotation`：2 edges
- `settings_profile -> sessions_history`：2 edges
- `one_rm -> sessions_history`：1 edges
- 例：`CameraRecordingNotifier.disposeCalculator_8898c` → `CameraRecordingService.dispose_889e0` (bl)
- 例：`CameraRecordingService.dispose_889e0` → `CameraController.dispose_560170` (bl)
- 例：`CameraController.dispose_88b10` → `CameraController.dispose_560170` (bl)
- 例：`_$CameraRecordingSettingImpl@1234411512.dyn:toJson_88f5c` → `_$$CameraRecordingSettingImplToJson@1234411512_88fa4` (bl)
- 例：`ModalRoute._maybeDispatchNavigationNotification@286188637_b089c` → `NavigationNotification_b0c2c` (bl)

## 使用限制

- 相同 function 可能同時命中多個群組；摘要是搜尋導覽，不是唯一分類。
- AOT call edge 只表達靜態 branch/call 指向，不含 runtime 條件、非同步 stream 的實際觸發順序，也不能還原雲端／伺服器端程式。
- 精確演算法仍需 `ORIGINAL_ALGORITHM_EVIDENCE_20260915.md` 所列的常數池與 runtime golden-vector 對照。

完整機器可讀資料：`analysis/feature_callgraph_index.json`。
