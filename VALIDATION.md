# PoinT Go Analyzer 驗證紀錄

## 已完成

- [x] 以使用者手機的 Bluetooth HCI snoop log 取得並解析約 2.1 MB、22,180
      筆 HCI record；確認 Poin*T 的 NUS service、notify/write characteristic
      與 61-byte raw notification 形狀。
- [x] 建立可替換的 `SensorTransport`，加入 BLE 掃描、連線、服務探索、通知
      訂閱、控制寫入與斷線清理。
- [x] `PoinTGoFrameDecoder` 支援 61-byte 擷取格式與 Flutter AOT raw-batch
      格式，保存並展開 32-bit 感測器時間戳。
- [x] 將角速度按感測器時間差分為角加速度，並提供線性／角加速度峰值、即時
      雙圖、自由量測、完整 session 重播、註記、可調區段、全 session/區段比較
      與 CSV/JSON/SVG 匯出。
- [x] 核心 BLE/解析 Kotlin 原始碼以 Android API 35 stub、Coroutines 1.9.0
      與 JDK 17 完成靜態編譯（僅有 Android deprecated API 警告）。
- [x] 6 個純 Kotlin/JUnit 測試通過（封包解析、signed little-endian、畸形
      封包拒絕、CSV/JSON/SVG 輸出）。
- [x] 工作區隔離工具鏈完成 `:app:assembleDebug` 與
      `:app:testDebugUnitTest`；產出 `app-debug.apk`。
- [x] 以已授權 ADB 安裝最新 APK 至 SM-N9810，Activity 啟動成功；BLE 掃描
      逾時會顯示錯誤且不崩潰。
- [x] 建立 Git checkpoint（本次功能提交另附於計畫 checkpoint 之後）。

## 尚待感測器可用時完成

- [ ] 關閉原廠 App 讓感測器可被新 App 連線，驗證 captured start/stop command
      profile 與通知持續性。
- [ ] 驗證重連、10 分鐘資料遺失率，以及實際通知頻率／封包計數。
- [ ] 以靜止、已知旋轉與已知動作校正軸向、量程、重力處理與角加速度噪聲。
- [ ] 將歷史 session 從目前的 in-memory repository 升級為持久化儲存，並在
      App 重啟後驗證回放。

上述未完成項目是感測器狀態或產品化儲存的驗證閘門，不代表把未驗證的感測器
命令或量測精度宣稱為已確認。
