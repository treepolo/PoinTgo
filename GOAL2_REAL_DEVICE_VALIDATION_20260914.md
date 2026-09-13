# Goal 2 最新實機驗證（2026-09-14）

## 環境

- 裝置：Samsung SM-N9810，ADB serial `RFCRC1V6REW`
- 私人副本：`com.treepolo.pointgo.clone`
- 感測器：Poin+T，`D4:7E:7E:48:74:44`
- APK：由專案內隔離 JDK／Android SDK／Apktool 建置，v2/v3 簽章

## 記錄結果

新版固定控制列安裝後，從「原廠相容功能（訪客模式）」直接開始記錄；未登入、未選運動模式。Android BLE log 證實掃描找到感測器、GATT 連線成功、NUS service 探索成功，並啟動 notification。

| 欄位 | 結果 |
| --- | ---: |
| notificationPackets | 2,792 |
| decodedPackets | 2,769 |
| sampleCount（CSV／JSON） | 5,538 |
| ignoredPackets | 23 |
| malformedPackets | 0 |
| timestampGapsOver40ms | 0 |
| largestGapMs | 18 |
| timestampRegressions | 0 |
| durationSeconds | 95.176 |
| linearAccelerationPeak | 0.064 m/s² |
| linearSpeedPeak | 0.008 m/s |
| angularVelocityPeak | 1.654 rad/s |
| angularAccelerationPeak | 4.590 rad/s² |

UI 停止後顯示繁體中文品質摘要、四種峰值與逐點通用分析；沒有選模式或登入要求。匯出按鈕成功產生以下檔案：

- `analysis/pointgo-session-20260914-060341.csv`（1,779,233 bytes）
- `analysis/pointgo-session-20260914-060341.json`（2,904,049 bytes）

JSON 交叉檢查：`algorithmVersion` 為 `vendor-compatible-reconstruction-2026.09`；`profile` 含實際 `accelBias`、`accelScale`、`gyroBias` 與完整性旗標；`quality` 與 CSV／JSON 筆數一致。CSV 標頭包含 raw、校正後、重力、線性／全域加速度、速度、角速度、角加速度與四元數欄位。

## 限制

此結果證明 BLE、解碼、匯出與相容重建流程可在實機運作；算法仍是 `vendor-compatible-reconstruction-2026.09`，尚未以官方同一段輸出建立 golden vector，因此不宣稱位元級相同。含負荷 VBT、甩球／投擲、角運動、跳躍／CMJ 與 30 分鐘穩定性仍列在待驗證清單。
