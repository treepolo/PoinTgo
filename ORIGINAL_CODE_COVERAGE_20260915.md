# 原廠 Flutter AOT 程式碼覆蓋索引（2026-09-15）

這不是把每個 AOT function 虛構成已還原 Dart；它把全部 function/class 逐筆分到保守的責任類別，讓『哪些已經有語意入口、哪些仍是 framework/obfuscated/unknown』可量化。分類使用符號名稱，精確公式仍須 AOT 組譯與 runtime 對照。

- functions：**46,790**
- classes：**6,696**

| 類別 | functions | 佔比 | code bytes（function size 合計） |
|---|---:|---:|---:|
| `other_named_or_unknown` | 20,090 | 42.94% | 4,148,852 |
| `obfuscated_or_codegen` | 6,019 | 12.86% | 1,620,600 |
| `localization` | 9,611 | 20.54% | 225,860 |
| `motion_algorithms` | 1,948 | 4.16% | 772,332 |
| `auth_cloud_data` | 679 | 1.45% | 212,484 |
| `platform_framework` | 5,922 | 12.66% | 1,514,004 |
| `product_pages` | 1,626 | 3.48% | 548,244 |
| `calibration` | 132 | 0.28% | 20,184 |
| `media_notifications` | 590 | 1.26% | 140,508 |
| `ble_transport` | 173 | 0.37% | 59,400 |

## 重要類別解讀

### `ble_transport`：173 functions（0.37%）
- owners：`DeviceConnectionManager` (29), `BleApiImpl` (26), `BleMocapDataParser` (23), `(none)` (22), `Quaternion` (12), `BleDeviceList` (11), `BleScanStatus` (9), `SensorManager` (8)
- examples：`SensorManager.transmitBlePacket_1b58ac`, `BleApiImpl.transmitBleCommandPacket_1b59a8`, `BleApiImpl._callMethod@859188570_1b5a64`, `BleOperationResult_1b5c24`, `BleApi.BleApi._1b6100`, `BleApiImpl.BleApiImpl._1b615c`, `BleApiImpl._onBleDfuStateUpdated@859188570_1b6658`, `SensorManager.onBleDfuStateUpdated_1b676c`

### `calibration`：132 functions（0.28%）
- owners：`(none)` (30), `AccelCalibrationUtil` (7), `SDe` (6), `SEn` (6), `SEs` (6), `SFr` (6), `SKo` (6), `SZh` (6)
- examples：`SDe.get:dashboard_metricMaxAccel_8134`, `SEn.get:dashboard_metricMaxAccel_8140`, `SEs.get:dashboard_metricMaxAccel_814c`, `SFr.get:dashboard_metricMaxAccel_8158`, `SKo.get:dashboard_metricMaxAccel_8164`, `SZh.get:dashboard_metricMaxAccel_8170`, `SDe.get:device_needCalibrationAfterFirmware_343dc`, `SEn.get:device_needCalibrationAfterFirmware_343e8`

### `motion_algorithms`：1,948 functions（4.16%）
- owners：`(none)` (397), `SEn` (102), `SZh` (100), `SDe` (99), `SFr` (99), `SEs` (97), `SKo` (96), `SJa` (84)
- examples：`SDe.get:mobility_test_hipExternalRotation_3ec`, `SEn.get:mobility_test_hipExternalRotation_3f8`, `SEs.get:mobility_test_hipExternalRotation_404`, `SFr.get:mobility_test_hipExternalRotation_410`, `SJa.get:mobility_test_hipExternalRotation_41c`, `SKo.get:mobility_test_hipExternalRotation_428`, `SZhTw.get:mobility_test_hipExternalRotation_434`, `SZh.get:mobility_test_hipExternalRotation_440`

### `product_pages`：1,626 functions（3.48%）
- owners：`(none)` (210), `SEs` (68), `SFr` (68), `SEn` (67), `SKo` (66), `SDe` (64), `SZh` (64), `SJa` (59)
- examples：`SDe.get:dailyMeasurement_noMeasurements_3bc4`, `SEn.get:dailyMeasurement_noMeasurements_3bd0`, `SEs.get:dailyMeasurement_noMeasurements_3bdc`, `SFr.get:dailyMeasurement_noMeasurements_3be8`, `SJa.get:dailyMeasurement_noMeasurements_3bf4`, `SKo.get:dailyMeasurement_noMeasurements_3c00`, `SZhTw.get:dailyMeasurement_noMeasurements_3c0c`, `SZh.get:dailyMeasurement_noMeasurements_3c18`

### `auth_cloud_data`：679 functions（1.45%）
- owners：`(none)` (165), `AthleteProfileDataSource` (27), `UndoHistoryState` (21), `PersonalProfileDataSource` (18), `MethodChannelFirebaseAuth` (16), `AuthService` (16), `ProfileRepository` (12), `SDe` (11)
- examples：`SDe.get:program_defaultPrograms_644`, `SEn.get:program_defaultPrograms_650`, `SEs.get:program_defaultPrograms_65c`, `SFr.get:program_defaultPrograms_668`, `SJa.get:program_defaultPrograms_674`, `SKo.get:program_defaultPrograms_680`, `SZhTw.get:program_defaultPrograms_68c`, `SZh.get:program_defaultPrograms_698`

### `media_notifications`：590 functions（1.26%）
- owners：`(none)` (126), `AndroidCameraCameraX` (24), `MethodChannelCamera` (21), `VideoPlayerController` (19), `AudioPlayer` (18), `CameraController` (14), `AudioService` (13), `_AudioplayersPlatform&AudioplayersPlatformInterface&MethodChannelAudioplayersPlatform@610091016` (10)
- examples：`AudioLogLevel.compareTo_35800`, `SZhTw.get:isometric_recordWithCamera_37138`, `SZh.get:isometric_recordWithCamera_37144`, `SDe.get:multiSensor_recordWithCamera_455ac`, `CameraRecordingNotifier_7c4ac`, `CameraRecordingNotifier.disposeCalculator_8898c`, `CameraRecordingService.dispose_889e0`, `CameraController.dispose_88b10`

### `localization`：9,611 functions（20.54%）
- owners：`SZh` (1387), `SEs` (1321), `SEn` (1314), `SZhTw` (1142), `(none)` (263), `MaterialLocalizationAr` (46), `MaterialLocalizationLt` (46), `MaterialLocalizationGa` (46)
- examples：`SEn.get:swing_allSwings_b0`, `SEs.get:swing_allSwings_bc`, `SZhTw.get:swing_allSwings_ec`, `SZh.get:swing_allSwings_f8`, `SEn.get:rsi_saved_110`, `SEs.get:rsi_saved_11c`, `SZhTw.get:rsi_saved_14c`, `SZh.get:rsi_saved_158`

### `obfuscated_or_codegen`：6,019 functions（12.86%）
- owners：`(none)` (2024), `VBTPage` (71), `RootPage` (47), `WeightliftingPage` (47), `EditableTextState` (39), `SlamPage` (37), `JumpPage` (30), `RsiPage` (27)
- examples：`sub_8c`, `sub_a58`, `sub_bd4`, `init:_printClosure@10040228_1014`, `sub_1078`, `get:_printClosure@10040228_115c`, `sub_352ec`, `sub_35474`

### `other_named_or_unknown`：20,090 functions（42.94%）
- owners：`(none)` (5342), `SFr` (1346), `SKo` (1341), `SDe` (1278), `SJa` (1250), `EditableTextState` (161), `HtmlTokenizer` (126), `SemanticsConfiguration` (76)
- examples：`SDe.get:swing_allSwings_80`, `SFr.get:swing_allSwings_c8`, `SJa.get:swing_allSwings_d4`, `SKo.get:swing_allSwings_e0`, `SDe.get:rsi_saved_104`, `SFr.get:rsi_saved_128`, `SJa.get:rsi_saved_134`, `SKo.get:rsi_saved_140`

## 邊界

- 類別可重疊的概念在本索引採 first-match；前端 page 與 calculator 的詳細 owner/PC 見 page owner 與 algorithm evidence。
- `obfuscated_or_codegen` 與 `other_named_or_unknown` 不代表未執行；只代表目前沒有足夠穩定的產品語意名稱。
- 這份覆蓋量化與組譯、call graph 一起使用，才能逐步把 unknown 降低；不把分類數字當成完整 source-level comprehension。

完整機器可讀資料：`analysis/code_coverage_index.json`。
