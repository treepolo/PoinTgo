# PoinT Go Analyzer 驗證紀錄

## 已完成

- [x] 解析使用者手機 Bluetooth HCI snoop log（約 2.1 MB、22,180 筆），確認
      Poin+T NUS service、控制寫入序列與 61-byte raw IMU notification 形狀。
- [x] 建立可替換 `SensorTransport`：BLE 掃描、連線、服務探索、斷線清理、
      NUS 狀態通知與自訂 raw IMU 通知特徵訂閱。
- [x] 連線後要求 ATT MTU 247；對較小 MTU 的 raw notification 做分段重組，
      並限制每個 characteristic 的資料邊界，避免狀態回覆污染 raw buffer。
- [x] `PoinTGoFrameDecoder` 支援 61-byte 擷取格式與 Flutter AOT raw-batch
      格式，保存並展開 32-bit 感測器時間戳。
- [x] 以相鄰角速度與感測器時間差計算角加速度，提供即時雙圖、峰值、門檻、
      自由量測、完整 session 回放、標註、可調區段、跨量測比較與 CSV/JSON/SVG 匯出。
- [x] 所有使用者可見介面與 BLE 錯誤訊息改為繁體中文。
- [x] 核心 BLE/解析 Kotlin 原始碼以 Android API 35 stub、Coroutines 1.9.0 與
      JDK 17 完成靜態編譯（僅有 Android deprecated API 警告）。
- [x] `:app:assembleDebug` 與 `:app:testDebugUnitTest` 通過；APK 已由專案內
      隔離 Gradle 8.9/JDK 17/Android SDK 建置並安裝至 SM-N9810。
- [x] 實機確認 Activity 可啟動、繁中 UI 正常顯示，且 BLE 掃描逾時不會崩潰。
- [x] 建立 Git checkpoint，變更可由 Git 還原。

## 尚待感測器狀態允許時完成

- [ ] 關閉原廠 App 後，以同一顆感測器完成一次 raw characteristic 連線與長時間串流，
      驗證 captured start/stop command profile、通知頻率與 decoded sample 數。
- [ ] 驗證重連、10 分鐘資料遺失率與實際通知封包計數。
- [ ] 以靜止、已知旋轉與已知動作校正軸向、量程、重力處理與角加速度噪聲。
- [ ] 將歷史 session 從目前的 in-memory repository 升級為持久化儲存，並在 App 重啟後驗證回放。

上述未完成項目是感測器狀態或產品化儲存的驗證閘門，不把未驗證的量測精度宣稱為已確認。
