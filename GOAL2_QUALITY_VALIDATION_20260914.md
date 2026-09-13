# Goal 2 資料品質驗證（2026-09-14）

## 實機環境

- 裝置：Samsung SM-N9810，ADB serial `RFCRC1V6REW`
- 測試套件：`com.treepolo.pointgo.clone`
- 感測器：Poin+T，`D4:7E:7E:48:74:44`
- APK 由專案隔離 JDK／Android SDK／Apktool 建置

## Session 結果

在感測器保持開機等待連線的狀態下，啟動自由分析、記錄後停止並匯出：

| 欄位 | 結果 |
| --- | ---: |
| notificationPackets | 5,710 |
| decodedPackets | 5,681 |
| sampleCount（CSV） | 11,362 |
| sampleCount（JSON） | 11,362 |
| ignoredPackets | 29 |
| malformedPackets | 0 |
| timestampGapsOver40ms | 0 |
| largestGapMs | 19 |
| timestampRegressions | 0 |
| receivedBytes | 346,693 |

`ignoredPackets` 是非 `0x04` 資料封包，未被誤判為樣本；`malformedPackets` 是標記為 `0x04` 但長度／筆數不符合 parser 的封包。時間間隔統計以擴展後的感測器 timestamp 計算，40 ms 以上才列為可能遺失的間隔，沒有把正常 16–19 ms 抖動誤報成掉包。

## 匯出檔

- `analysis/pointgo-session-20260914-051110.csv`
- `analysis/pointgo-session-20260914-051110.json`

JSON 的 `quality` metadata 與 CSV／JSON 樣本筆數已用 PowerShell `ConvertFrom-Json`／`Import-Csv` 交叉核對。算法版本為 `vendor-compatible-reconstruction-2026.09`。

## UI 檢查

- 品質摘要顯示「封包／解碼／樣本」與「忽略／格式異常／大間隔／回退」。
- 停止後開始、停止、匯出控制可用；logcat 無 `FATAL EXCEPTION`。
- 長時段圖表改以全 session 的 min/max envelope 繪製，觸控圖表可顯示時間與當點值。
