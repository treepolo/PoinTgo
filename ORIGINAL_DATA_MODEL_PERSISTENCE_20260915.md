# 原廠資料模型／Persistence 邊界（2026-09-15）

Flutter AOT 的前端不只畫 UI，也包含 session、動作結果、profile 與 Firestore 序列化。以下是由 function metadata、call edges 與命名得到的責任清單；方法名稱與 PC/size 是 A 級，欄位完整語意與雲端 security rule 不在 APK，標為 B/C。

## Repository／DataSource 入口

| 領域 | 已定位入口（PC / size） | 可核對責任 | 等級 |
|---|---|---|---|
| 設定 | `SettingRepositoryImpl.fetchSetting_8ca818` (`0xf51158` / 348)、`saveSetting_1aa400` (`0x830d40` / 128) | 讀寫 app setting | A |
| Auth／profile | `AuthRepositoryImpl.handleLogin_3bc318` (`0xa42c58` / 3884)、`getAppUserInfo_1b014c` (`0x836a8c` / 1180)、`updateAppUserInfo_1b10b4` (`0x8379f4` / 480) | 登入後 user/team/profile 建立與更新 | A/B |
| Profile | `ProfileRepository.getProfile_136fec` (`0x7bd92c` / 1552)、`_profileFromDoc_1384b4` (`0x7bedf4` / 1896)、`updateProfile_1af1cc` (`0x835b0c` / 1080)、`_profileToMap_1b0d44` (`0x837684` / 776) | Firestore document ↔ profile model | A |
| Team／program | `TeamRepositoryImpl.getTeamsForUser_142c80` (`0x7c95c0` / 960)、`getTeam_143060` (`0x7c99a0` / 1080)、`AthleteProfileDataSource.startProgram_8876c8` (`0xf0e008` / 860)、`createProgram_88aed0` (`0xf11810` / 88) | 團隊、指派／建立訓練 program | A/B |
| 校正紀錄 | `CalibrationRecordRepository.saveCalibrationDate_1b4c8c` (`0x83b5cc` / 224)、`_loadRecords_1b4d8c` (`0x83b6cc` / 156)、`getLastCalibrationDate_8a573c` (`0xf2c07c` / 204) | 校正日期／是否需要重新校正 | A |

## 動作結果 Repository

| 動作 | 儲存入口 | 等級 |
|---|---|---|
| Isometric | `IsometricRepositoryImpl.saveIsometric_256ab8` (`0x8dd3f8` / 1564) | A |
| Jump | `JumpRepositoryImpl.saveJump_2b2c3c` (`0x93957c` / 1392) | A |
| Mobility | `MobilityRepositoryImpl.fetchPresets_2c8d4c` (`0x94f68c` / 120)、`saveMobilityContentPreset_2c97fc` (`0x95013c` / 184)、`saveMobility_2d334c` (`0x959c8c` / 1908)、`deletePreset_2d5478` (`0x95bdb8` / 60) | A |
| 1RM／LVP | `OneRmRepositoryImpl.saveOneRmTest_2ef2b8` (`0x975bf8` / 1620)、`LVPRepositoryImpl.saveLVP_2ef92c` (`0x97626c` / 864)、`getLatestLvp_3992ac` (`0xa1fbec` / 60) | A |
| Rotation | `RotationRepositoryImpl.saveRotation_31e810` (`0x9a5150` / 1012) | A |
| RSI | `RsiRepositoryImpl.saveRsi_32a4cc` (`0x9b0e0c` / 1664) | A |
| Throws／Swing | `ThrowsRepositoryImpl.saveThrows_348958` (`0x9cf298` / 1228)、`SwingRepositoryImpl.saveSwing_35ceec` (`0x9e382c` / 1164) | A |
| VBT | `VBTRepositoryImpl.saveVBT_3943fc` (`0xa1ad3c` / 2904) | A |
| Weightlifting | `WeightliftingRepositoryImpl.saveWeightlifting_3b0068` (`0xa369a8` / 1608) | A |

這張表證明原廠各運動不是共用單一「峰值加速度」欄位；每個運動有自己的結果模型與 persistence 入口。要加入角速度／角加速度，後續應把 parser／calculator 序列與相應 session trace 一起保留，而不是只改一個顯示欄位（B）。

## Session／Firestore 轉換鏈

`AthleteProfileDataSource` 直接保留下列方法：

- `getWeightliftingTraces_89bb70`、`saveWeightliftingTraces_89c6e0`。
- `saveExerciseSessionWithWeightliftingTraces_89d30c`、`_sessionToFirestore_89d44c`。
- `watchExerciseSessions_89fbb4`、`_parseSessions_8a070c`、`_sessionFromFirestore_8a0874`、`_parseExercises_8a0e58`。
- `saveExerciseSession_8a2c00`、`updateExerciseSession_8a27f8`、`deleteExerciseSession_89d984`。
- `addBodyMeasurement_89ef7c`、`_measurementToFirestore_89f008`、`_measurementFromFirestore_886bc8`。

`PersonalProfileDataSource` 有對應的 serialize/save/watch/delete 方法，代表個人帳戶與教練／athlete profile 共享同一資料模型邊界。由此可重建的靜態資料流是：

`BLE通知 → parser → calculator events/metrics → 運動結果 model → session trace → Firestore map → history/detail/replay UI`

`_parseExercises` 與 `_sessionFromFirestore` 的完整欄位映射可由 ARM64 逐列還原，但尚未以實際帳戶資料與原廠 UI 輸出做 golden-vector；欄位缺省值、版本相容與 server rule 屬 C。

## 前端消費者

`SessionDetailPage`、`History`／replay 相關 page 與 `AnalyticsVbtLvpChart`、`AnalyticsJumpInsight` 等 symbol 透過 call edges 連到上述 repository/model。這表示前端的圖表與回放不是獨立 mock；它們消費同一份 session/result 資料。完整 page owner 與 call-edge 交叉索引見：

- [ORIGINAL_PAGE_SYMBOLS_20260915.md](ORIGINAL_PAGE_SYMBOLS_20260915.md)
- [ORIGINAL_FEATURE_CALLGRAPH_20260915.md](ORIGINAL_FEATURE_CALLGRAPH_20260915.md)

## 明確邊界

1. AOT 中可以直接確認 repository 入口、Firestore map/from-map、watch/save/delete 方法；不能從 APK 直接得到 Firebase security rules、Cloud Functions 或後端 schema migration。
2. function 名稱中的 `save`／`parse` 是責任證據，不足以證明每個欄位的單位或 server-side 觸發副作用。
3. 後續實作若要保留原廠功能，應以這些入口建立相容 adapter，並把未確認欄位標成 unknown，直到 golden-vector 對照完成。
