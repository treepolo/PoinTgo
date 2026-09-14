# Goal 2 連線生命週期驗證（2026-09-14）

## 本輪固定需求

- App 開啟即由共用 `SensorConnectionService` 自動連線；不以 V2 頁面或「開始記錄」作為連線觸發。
- 所有 clone Activity（入口、原廠相容／自由分析 V2、legacy entry）都設定 `FLAG_KEEP_SCREEN_ON`。
- 「開始記錄」只切換取樣命令；「停止記錄」只送停止命令，保留 GATT 與 notification 連線。
- 沒有閒置自動斷線計時器。服務使用 `START_STICKY`，只有服務被真正銷毀時才關閉 GATT。

## 實作檢核

| 項目 | 實作位置 | 結果 |
|---|---|---|
| 開啟入口即啟動 BLE service | `CloneLauncherActivity.onCreate/onResume` | 通過 |
| 背景回到前景仍維持服務 | `SensorConnectionService`, `START_STICKY` | 通過 |
| 全 App 長亮 | 三個 Activity 的 `FLAG_KEEP_SCREEN_ON` | 通過 |
| V2 開始／停止與連線分離 | `MotionAnalyzerActivityV2` 呼叫 `startStreaming/stopStreaming` | 通過 |
| 停止後保留 GATT | `SensorConnectionService.ACTION_STOP_STREAMING` | 通過 |
| AOT／61-byte HCI raw frame 重組 | `appendNotificationFragment` | 通過 |

## 建置與自動驗證

使用專案內隔離的 Android/JDK 工具鏈：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\official-clone\build-goal2.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\official-clone\test-vendor-engine.ps1
```

結果：APK v2/v3 簽章驗證通過；`VENDOR_ENGINE_TEST_OK`。

## 實機驗證（RFCRC1V6REW）

- 啟動 clone launcher 後，log 依序出現掃描、GATT connected、service discovery、NUS TX／raw notify 特徵、CCCD notification 與 MTU 247。
- launcher UI 顯示「訪客模式已啟用 · 啟動即自動連線」；`dumpsys window` 的 `mHoldScreenWindow` 指向 `CloneLauncherActivity`。
- 一次感測器保持喚醒的記錄：`封包 1595`、`解碼 1579`、`樣本 3158`，最大間隔 18 ms；線性加速度峰值 0.064 m/s²、線性速度峰值 0.011 m/s、角速度峰值 1.129 rad/s、角加速度峰值 11.575 rad/s²。
- 停止後 UI 顯示「已停止記錄；感測器連線保持中」，`dumpsys activity services` 顯示 `SensorConnectionService` `startRequested=true`、`delayedStop=false`、`stopIfKilled=false`。

感測器若長時間沒有被喚醒，硬體本身可能進入待機；這不會觸發 App 的閒置斷線邏輯。重新喚醒感測器後，服務會依既有自動重連流程重新建立連線。
