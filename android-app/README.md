# PointGo Analyzer Android app

這是獨立於原廠 Poin*T Go 的 Android/Compose companion app，application id
為 `com.pointgo.capture`。它把感測器資料保存成可重播的量測 session，並提供
C+D+E 流程：

- C：即時線性加速度、角速度與角加速度圖表、目前值、峰值與門檻警示。
- D：一鍵自由量測；進階面板才需要輸入 BLE 位址，留白會自動掃描。
- E：session 重播、標註、兩次量測峰值比較，以及 CSV/JSON/SVG 分享匯出。

## BLE 實作

`BleSensorTransport` 使用 Poin*T 的 Nordic UART Service UUID，並以 HCI
snoop capture 中觀察到的控制寫入序列啟動高頻串流。`PoinTGoFrameDecoder`
同時支援原廠 Flutter AOT raw-batch 合約與實際擷取到的 61-byte NUS 通知，
並保留感測器時間戳；角加速度由相鄰角速度樣本按時間微分取得。

控制命令仍是可替換的 profile（見 `PoinTGoProtocol.kt`）。不同韌體或模式上線
前，請先用實機確認啟動/停止命令與量程；本工作區已產出並安裝 debug APK，
但仍未宣稱完成長時間串流與量測精度校正。

## 建置與驗證

若要自行重建，可用 Android Studio（自帶 JDK、Android SDK 與 Gradle）開啟此
資料夾。本次驗證使用工作區內隔離的 Gradle 8.9、JDK 17 與 Android API
35/build-tools 35，不依賴使用者本機既有安裝；`:app:assembleDebug` 與
`:app:testDebugUnitTest` 已成功，產物位於
`app/build/outputs/apk/debug/app-debug.apk`。核心 BLE/解析與 6 個 JUnit 測試
也已通過；實機 smoke test 確認 Activity 可啟動且掃描逾時會顯示錯誤而不崩潰。
