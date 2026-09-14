# Goal 2 演算法事件階段驗證

日期：2026-09-14

## 本階段完成

- [x] 甩球／投擲改為獨立的線性速度遲滯偵測：啟動、結束、釋放峰值與次數分開保留。
- [x] 角運動改為獨立的主導陀螺軸與帶正負方向的遲滯偵測；方向變化以獨立事件保留。
- [x] VBT 每次動作新增離心、向心與整次 rep 事件；既有逐 rep 速度、幅度、功率、RFD、減速率與節奏量保留。
- [x] 1RM 保留既有 Epley、Brzycki、Lander、Mayhew、O'Conner、LVP，並加入 Lombardi、Wathan、公式平均／上下界／差距與 LVP 輸入欄位。
- [x] 跳躍／反向跳新增起跳、向心、飛行、落地事件的相對中間量：起跳速度、落地速度、飛行峰值速度、反向下沉深度、向心時間、平均／最佳跳高與接觸時間。
- [x] V2 事件文字改為顯示整段事件清單；圖表仍以整段資料的 min/max envelope 呈現，並可觸控游標讀取時間與數值。
- [x] 舊自由分析入口的說明同步為「開啟頁面即自動連線；開始按鈕只開始取樣」。

## 自動化驗證

命令：

`official-clone\\test-vendor-engine.ps1`

結果：

`VENDOR_ENGINE_TEST_OK`

覆蓋：

- 六面校正、校正品質與 still-gyro。
- 600 筆衍生資料的線性／角速度、線性／角加速度與 JSON。
- 甩球／投擲與角運動的 3 次事件判定。
- VBT 3 次 rep 及離心／向心事件。
- 1RM 七種公式、LVP 與公式彙總欄位。
- 跳躍／CMJ 2 次、反向下沉／向心事件與 phase metrics。

建置：

`official-clone\\build-goal2.ps1`

結果：隔離 JDK／Android SDK／Apktool 建置成功，產生
`official-clone\\build\\pointgo-clone-debug.apk`。

實機：

- ADB serial：`RFCRC1V6REW`
- APK 可安裝並解析出 `com.treepolo.pointgo.clone`。
- `SensorConnectionService` 在背景仍存在；未增加閒置自動斷線。
- 本次未把感測器動作數據誤記為 golden vector；需以官方畫面同步的受控動作再做誤差比對。

## 尚未宣稱的事項

`vendor-compatible-reconstruction-2026.09` 仍代表「依 AOT／資料模型重建的相容算法」。在取得官方同一段受控輸入與輸出前，不宣稱逐位元等同官方 AOT。尚待實機受控動作、六面實測、30 分鐘背景穩定與封包遺失／timestamp wrap 長測。