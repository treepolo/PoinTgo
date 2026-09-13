# Poin+T Go 私人副本：硬體驗證 checkpoint

日期：2026-09-14

本紀錄對應 APK：`official-clone/build/pointgo-clone-debug.apk`  
SHA-256：`21A050714CDB595075CA8CA5F7CB9CA888964FCDEDADA32A73B0174C3DBF3B89`

## 測試環境

- Samsung SM-N9810、Android 13（ADB serial `RFCRC1V6REW`）
- 感測器 `Poin+T`，位址 `D4:7E:7E:48:74:44`
- 副本套件 `com.treepolo.pointgo.clone`，versionName `1.3.5`

## 連線與資料流

感測器重新開機並進入等待連線後，副本掃描到：

```text
Poin+T candidate name=Poin+T address=D4:7E:7E:48:74:44
```

GATT `onClientConnectionState`、服務探索、NUS TX/RX、通知啟用、MTU 247，以及原廠命令序列寫入均回傳 `status=0`。

停止自由記錄時分析頁顯示：

| 項目 | 實測值 |
| --- | ---: |
| 封包 | 1367 |
| 樣本 | 2696 |
| 線性加速度峰值 | 11.970 m/s² |
| 角速度峰值 | 0.253 rad/s |
| 角加速度峰值 | 3.297 rad/s² |

記錄期間圖表持續更新，停止後曲線與峰值保留。

## 匯出

手機產生以下檔案：

```text
/sdcard/Android/data/com.treepolo.pointgo.clone/files/Documents/pointgo-session-20260914-024606.csv
/sdcard/Android/data/com.treepolo.pointgo.clone/files/Documents/pointgo-session-20260914-024606.json
```

大小分別為 347,127 bytes 與 708,218 bytes。CSV header 已確認包含 timestamp、三軸線性加速度/合成量、三軸角速度/合成量、三軸角加速度/合成量；JSON 包含相同樣本與 `source` 欄位。

## 操作備註

感測器若長時間未重開可能停止廣播，Android 對歷史位址會回傳 GATT `133`。重新開關感測器並等待連線後，副本會先做未過濾掃描，掃描不到時再嘗試已知位址。原廠 App 與副本可並存，副本仍保留「開啟原廠功能」入口。
