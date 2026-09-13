# Goal 2 實機驗證 checkpoint

日期：2026-09-14

## 測試環境

- Samsung SM-N9810、Android 13、ADB serial `RFCRC1V6REW`
- 感測器 `Poin+T`，位址 `D4:7E:7E:48:74:44`
- 私人副本套件 `com.treepolo.pointgo.clone`，V2 自由分析頁
- 官方 App 在測試前已停止，避免同時持有 GATT

## V2 自有 GATT 與資料流

感測器開機並停在等待連線後，副本完成掃描、NUS 服務探索、TX 通知啟用、MTU 交換與既有命令序列。一次連續記錄約 88 秒，停止時畫面顯示：

| 項目 | 實測值 |
| --- | ---: |
| 封包 | 2,582 |
| 樣本 | 5,118 |
| 線性加速度峰值 | 0.039 m/s² |
| 線性速度峰值 | 0.006 m/s |
| 角速度峰值 | 1.481 rad/s |
| 角加速度峰值 | 3.493 rad/s² |

圖表在記錄期間持續更新，停止後曲線、逐點分析與峰值保留；通用模組判定次數為 0（本次未施加動作門檻）。

## 匯出驗證

手機實際產生並拉回分析資料夾：

- `pointgo-session-20260914-044009.csv`：1,642,128 bytes、5,118 筆資料列
- `pointgo-session-20260914-044009.json`：2,681,246 bytes、5,118 個 samples

CSV header 涵蓋 raw、校正後、重力、三軸線性／全域加速度、全域速度、線性速度、三軸角速度、三軸角加速度、合成峰值與四元數。JSON 另含 `analysis`、`profile` 與 `algorithmVersion`（`vendor-compatible-reconstruction-2026.09`）。

## 尚未完成

- 官方畫面與受控動作 golden vector／誤差量化。
- 實際六面固定姿態校正與各動作模組（甩球／投擲、角運動、VBT、1RM、跳躍／CMJ）受控 session。
- 30 分鐘長時間、背景切換、timestamp wrap 與遺失率測試。
