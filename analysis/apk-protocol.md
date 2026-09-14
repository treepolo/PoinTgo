# Poin*T Go APK／BLE 協定偵察

> 偵察日期：2026-09-13（Windows 主機、Android 13 實機）  
> 範圍：只讀取已安裝的 Poin*T Go APK 與 Bluetooth/GATT 系統診斷；未讀取或修改 App 使用者資料，也未向感測器寫入未知命令。

## 1. APK 來源與靜態分析

- Android package：`kr.piehealthcare.point.sensor`
- versionName：`1.3.5`
- versionCode：`2026090101`
- ABI：`arm64-v8a`
- 已安裝 APK：`base.apk`、`split_config.arm64_v8a.apk`、`split_config.en.apk`、`split_config.xxhdpi.apk`、`split_config.zh.apk`
- 已拉取檔案：`analysis/apk_protocol/`；只使用 `adb shell pm path` 取得 APK 路徑，再以 `adb pull` 拉 APK。
- `base.apk` SHA-256：`94D764AA9234F2687EA4C129A4A3645C15B968E191229AEB4466E1C14C7F7A10`
- `split_config.arm64_v8a.apk` SHA-256：`7E8F0CDE58813901855C7DF9162D9F76EE80A936FD6FEDC0B786FEE959D33DFD`

App 是 Flutter AOT：主要 Dart 邏輯位於 arm64 split 的 `lib/arm64-v8a/libapp.so`，不是一般可直接從 `classes.dex` 讀到的 Java BLE 程式。從 AOT 字串與符號線索可確認下列自有 BLE 模組：

- `package:flutter_mocap_lib/ble_api.dart`
- `ble_sdk.dart`、`ble_mocap_commands.dart`
- `ble_mocap_data_hex_code.dart`
- `ble_mocap_data_parser_utils.dart`
- `ble_listener_packet_receive.dart`、`ble_listener_device_discovery.dart`

已找到的 parser／資料模型名稱包括：

- `parseReceivedPacket`、`parsePacketType`
- `_parseMeasureResponsePacket`
- `_parseRawImuBatch`
- `_parseRawAccelGyroPrsTmp`
- `_parseQuatAgzVgzPgz`
- `_parseQuaternionPacket`
- `RawImuBatch`、`RawImuStreamEvent`
- `rawSensorDataAccelGyroPrsTmp`、`sensorStateAccelGyro`
- `PoinTGOMotionData`、`PoinTGOMotionLog`

這表示原 App 的二進位中確實包含「原始 IMU 批次」及加速度／陀螺儀／壓力／溫度解析路徑；但本次只做字串、ELF section 與 DEX 檢查，沒有進行 AArch64 反組譯，因此尚未可靠還原封包 magic/header、欄位 offset、大小端、比例係數或命令 checksum。

## 2. 已確認的 BLE 服務與特徵值

裝置掃描結果顯示名稱為 `Poin+T`，位址為 `D4:7E:7E:48:74:44`。App 的 Bluetooth 掃描 filter 明確使用 Nordic UART Service（NUS）主服務：

| 用途（依 NUS 標準角色） | UUID | GATT handle／屬性 | 結論 |
|---|---|---|---|
| NUS 主服務 | `6e400001-b5a3-f393-e0a9-e50e24dcca9e` | `0x0010`–`0x0015` | App 掃描 filter 明確鎖定此服務 |
| 手機寫入感測器（NUS RX） | `6e400002-b5a3-f393-e0a9-e50e24dcca9e` | value `0x0012`，`WRITE` | 傳送測量／控制命令的候選通道 |
| 感測器通知手機（NUS TX） | `6e400003-b5a3-f393-e0a9-e50e24dcca9e` | value `0x0014`，`READ + NOTIFY`；CCCD `0x0015` | 原始測量回傳的候選通道 |

注意：有些 SDK 以 peripheral 視角把 002 稱 RX、003 稱 TX；上表同時寫出手機方向，以免在實作時接反。`0x08` 為 WRITE，`0x12` 為 READ|NOTIFY。

同一份 GATT cache 還有一個非 NUS 的自訂服務，語意尚未確認：

| 服務／特徵值 | Handle／屬性 | 目前判斷 |
|---|---|---|
| Service `8d53dc1d-1db7-4cd3-868b-8a527460aa84` | `0x0016`–`0x0019` | 感測器可能的輔助通道，但尚未能由名稱或程式碼確認 |
| Characteristic `da2e7828-fbce-4e01-ae9e-261174997c48` | value `0x0018`，`WRITE_NO_RESPONSE + NOTIFY`；CCCD `0x0019` | 不要在未知格式下主動寫入；需以實際通知封包比對 |

證據位於 `analysis/apk_protocol/bluetooth_manager_dumpsys.txt`：掃描 filter 約第 252 行；NUS GATT cache 約第 1299–1315 行；`Poin+T` 裝置名稱約第 8161 行。因這次連線在 service discovery／link timeout 後反覆重連，Android 沒有為目前位址保留可直接對應的 `gatt_cache_d47e7e487444` 檔名；因此 GATT cache 與本裝置的關聯是由「App 的 NUS filter + Poin+T 名稱 + 同次 GATT 結構」推得，仍應以一次乾淨連線的通知封包做最後確認。

## 3. App 已暴露的命令與原始資料線索

AOT 字串中可見：

- `CommandMeasureRawImuHighRate`
- `CommandMeasureQuaternionAccelVelocityPosition`
- `CommandStopMeasure`
- `SensorMeasureCommand`
- `measureCommandResponse`
- `resetRawImuTimestampState`
- `CommandFirmwareVersionQuery`
- 各種 gyro／accel calibration 命令

原始資料模型與演算法呼叫線索包含：

```text
sampleRateHz
ax, ay, az
gx, gy, gz
timestamp
RawImuBatch
rawImuHighRate
```

二進位中的 JavaScript bridge 字串還顯示會以 `sampleRateHz` 建立 `SwingCalculator`，並將每筆 `accel.{x,y,z}`、`gyro.{x,y,z}`、`timestamp` 傳入處理。因此若能擷取 NUS TX（003）完整批次，後續可以在新 App／外部分析器中保留原始時間序列，再計算角速度、角加速度與峰值，而不必只依賴原 App 的峰值欄位。

## 4. 尚未解出的部分

本次靜態／系統診斷尚不能安全回答：

1. 每一種 packet type 的 magic/header、長度、batch count 與封包分片規則。
2. `ax/ay/az`、`gx/gy/gz` 的 raw 整數格式、大小端、量綱與比例係數。
3. timestamp 的單位、wrap 規則，以及實際 `sampleRateHz`（字串有名稱，但未找到固定數值）。
4. `CommandMeasureRawImuHighRate` 的實際 bytes、是否需要模式／握手／checksum。
5. NUS 003 與自訂 0018 哪一個承載 raw IMU；目前 App logcat 沒有印出通知 payload。

## 5. 建議的下一個最小安全步驟

在不清除 log、不寫入未知命令的前提下，做一次「乾淨連線 + 已知 App 操作」的 BLE notification capture：

1. 讓 App 連上 `Poin+T`，先在 003 開啟 CCCD notification。
2. 使用 App 目前已有的測量流程觸發一小段記錄，同時保存每個 notification 的時間與十六進位 payload。
3. 以 packet 長度、重複欄位與 `RawImuBatch` parser 線索做差分；確認是否跨 MTU 分片，再還原欄位與比例。
4. 只有在封包格式確認後，才實作 `CommandMeasureRawImuHighRate` 或替代的自動測量流程。

目前不建議直接對 002 或 0018 猜寫命令，也不建議修改／重簽原 APK；先取得一段真實 raw payload，能大幅降低誤觸校正或韌體控制命令的風險。

## 6. 產物

- APK 與 split：`analysis/apk_protocol/`
- AOT ASCII strings：`analysis/apk_protocol/libapp.strings.txt`
- DEX strings：`analysis/apk_protocol/classes.strings.txt`
- 完整 Bluetooth 系統診斷：`analysis/apk_protocol/bluetooth_manager_dumpsys.txt`

本次沒有拉取 `/data/user/0/kr.piehealthcare.point.sensor`、外部儲存資料或任何使用者內容，也沒有修改手機設定、App 或感測器狀態。
