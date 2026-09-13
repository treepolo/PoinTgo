# Poin+T GO 私人副本工作計畫

## 目標與邊界

- 以手機實際安裝的 `kr.piehealthcare.point.sensor` 1.3.5 split APK 為基底，建立可與原廠 App 並存的私人測試副本。
- 保留原廠 Flutter 介面、登入／模式／既有 BLE 流程；不覆蓋、不解除安裝原廠 App。
- 在原廠 BLE callback 旁加入副本內部資料橋，將 NUS TX 原始封包交給自由記錄與分析頁；不把手機 HCI 擷取檔上傳到 GitHub。
- 以新 application id 與新簽章安裝，原廠更新與帳號資料不共用；涉及原廠服務授權的功能以實機驗證結果為準。

## 五個實作候選（針對已選 D 的副本路線）

| 候選 | 實作方式 | 綜合分數 | 判斷 |
|---|---|---:|---|
| A | 直接修改 Flutter AOT `libapp.so` 的 Dart 畫面與 BLE 狀態機 | 5.4/10 | 可保留單一畫面，但反組譯後難以維護、容易破壞 AOT。 |
| B | 重打包官方 APK，僅在既有 BLE callback 注入 raw broadcast，再新增原生分析 Activity | **8.8/10** | **採用**；重用官方連線與資料流程，新增功能邊界清楚且可回退。 |
| C | 只加入原生分析 Activity，另開第二條 BluetoothGatt 連線 | 7.1/10 | 可快速做出自由記錄，但可能與官方連線互斥。 |
| D | 用 Frida／動態注入官方執行程序 | 6.3/10 | 適合研究，不適合作為可安裝、可恢復的交付 APK。 |
| E | 只修改資源與 Manifest，另以外部 companion App 分析 | 6.0/10 | 保留性高，但不符合使用者要在同一副本內結合功能的要求。 |

## 採用 B 的工作分段

1. 備份與完整性：拉取 base、ABI、語系、density splits，記錄版本與 SHA-256。
2. 離線分析：解包 Manifest、Flutter AOT、`BLEService`、NUS UUID、原始通知 callback 與控制寫入位置。
3. 副本組裝：改 application id／provider authority／label，停用 PairIP license gate，保留 Flutter 資產與原廠類別，加入 `MotionAnalyzerActivity` 與資料橋。
4. 功能修改：自由記錄按鈕、角速度／角加速度／線性加速度時間圖、峰值／RMS、CSV／JSON 匯出；原廠模式入口仍可開啟。
5. 建置與安裝：使用專案隔離 JDK／Android SDK／Gradle／Apktool，產出可與原廠並存的簽名 APK；最後才重新連手機做安裝與 BLE 實測。

## UI／使用流程候選（沿用原本 C+D+E 需求）

| 方案 | 使用流程 | 評分 | 判斷 |
|---|---|---:|---|
| 1 | 副本首頁保留原廠功能，新增「自由分析」入口；進入後一鍵記錄、即時圖表、停止後回放 | **9.4/10** | **採用**；功能完整且新增複雜度最低。 |
| 2 | 在每個原廠模式頁加入「進階分析」抽屜 | 8.5/10 | 與原廠畫面耦合較深，AOT 修改風險較高。 |
| 3 | 首頁分成「原廠功能／資料分析」兩個頁籤 | 8.1/10 | 清楚但多一次切換。 |
| 4 | 連線後顯示可收合的分析浮層 | 7.6/10 | 即時性好，但遮擋原廠畫面。 |
| 5 | 原廠記錄完成後自動跳轉分析頁 | 7.4/10 | 自動化高，但無法完全取代自由記錄。 |

## 進度與驗證檢核表

- [x] 已從手機拉取官方 1.3.5 五個 split APK，且 base SHA-256 與既有分析副本一致。
- [x] 已確認 Flutter AOT (`libapp.so`)、`BLEService`、NUS TX/RX UUID 與 `flutter_onBlePacketReceived` callback。
- [ ] 完成新 application id／簽章／provider authority 的可並存重打包。
- [ ] 完成 raw callback → 副本分析資料橋，並保留原廠 Flutter 事件流。
- [ ] 完成自由分析 Activity、時間圖、峰值／RMS 與 CSV／JSON 匯出。
- [ ] 離線建置通過，APK 簽章驗證通過，原廠副本 Activity 可啟動。
- [ ] 手機實測：副本與原廠 App 並存、原廠功能可開啟、自由記錄收到連續 raw 封包。
- [ ] 手機實測：10 分鐘資料遺失率、停止／重連、匯出檔內容與圖表摘要一致。
- [ ] 每個完成階段建立 Git commit 並推送 `origin/master`。
