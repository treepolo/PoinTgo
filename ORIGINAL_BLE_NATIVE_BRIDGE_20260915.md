# 原廠 BLE 原生橋接證據（2026-09-15）

這份文件把原廠 APK 的 Android BLE plugin 與 Flutter AOT parser 接起來。所有 method/channel/UUID 都直接來自 clean Apktool Smali；封包欄位與演算法仍以 AOT 證據表為準。

## Flutter plugin 邊界

`GeneratedPluginRegistrant.smali` 建立 `La3/n;` 並以 `flutter_mocap_lib` plugin 註冊；錯誤字串明確寫出原始 plugin 名 `com.example.flutter_mocap_lib.BleSdkPlugin`。`a3/n.smali` 建立：

- MethodChannel：`com.motion-capture.pie-healthcare/method`
- EventChannel：`com.motion-capture.pie-healthcare/event`
- Android adapter state receiver：`android.bluetooth.adapter.action.STATE_CHANGED`

MethodChannel 中可直接讀到的命令與參數：

| command | 參數／結果線索 | 等級 |
|---|---|---|
| `flutter_initSdk` | 初始化 SDK/service | A |
| `flutter_startScan` | 檢查 Bluetooth scan permission、adapter、避免重複掃描 | A |
| `flutter_stopScan` | 停止 scanner callback | A |
| `flutter_connectDevice` | 參數 `deviceUUID`；若未先掃描則回錯 | A |
| `flutter_disconnectDevice` | 參數 `deviceUUID` | A |
| `flutter_transmitBlePacket` | 參數 `deviceUUID`、`packetToTransmit`；寫入已連線裝置 | A |
| `flutter_checkPermissionsGranted` | 回傳權限檢查結果；涉及 `BLUETOOTH_SCAN`／`CONNECT` 等 | A |
| `flutter_startBleDfu` | `deviceUUID`、`firmwarePath`；啟動 DFU | A |

EventChannel 有 `eventSinkBuffer` 狀態；因此掃描、連線與裝置通知是 stream，不是只在測量按鈕期間同步讀取。具體事件 payload 欄位要再對 AOT `DeviceEventListener`／`BleMocapDataParser` 做 golden-vector 對照（C）。

## BLE service 與 characteristic

`com/example/flutter_mocap_lib/service/BLEService.smali` 建立 `ScanFilter`：

- service UUID：`6e400001-b5a3-f393-e0a9-e50e24dcca9e`
- scan mode：常數 `1`（Android balanced scan mode）
- `BluetoothLeScanner.startScan(filters, settings, callback)`；停止時呼叫 `stopScan`。
- service 是 Android foreground `Service`，manifest component 為 `com.example.flutter_mocap_lib.service.BLEService`，`exported=false`。

`d3.1/b.smali`／相關 GATT code 直接建立 Nordic UART 型 characteristic UUID：

- `6e400002-b5a3-f393-e0a9-e50e24dcca9e`：寫入命令／資料的 characteristic（由 `writeCharacteristic` 路徑使用）。
- `6e400003-b5a3-f393-e0a9-e50e24dcca9e`：通知／接收 characteristic（由 notification path 使用）。
- CCCD：`00002902-0000-1000-8000-00805f9b34fb`。

這組 UUID 與 `flutter_transmitBlePacket`、`flutter_startScan` 的直接呼叫邊界，已足以解釋為什麼自訂 app 必須先維持 plugin/service 的連線，再開始記錄；但 NUS payload 的 type、timestamp 與六個 IMU 欄位仍由 Flutter AOT parser 決定，不應只靠 UUID 猜測。

## 權限與錯誤分支

Smali 直接包含 `BLUETOOTH_SCAN`、`BLUETOOTH_CONNECT`、`BLUETOOTH`、`BLUETOOTH_ADMIN`、`ACCESS_FINE_LOCATION` 與 `FOREGROUND_SERVICE_HEALTH` 檢查。可讀錯誤包括：

- `Bluetooth scan permission not granted`
- `Bluetooth is turned off`
- `BLE scan is already in progress`
- `Device with given device-uuid not scanned before`
- `Device with given device-uuid not connected`

這些是 A 級錯誤條件；掃描逾時、背景限制與實際重試等待時間尚未從靜態 code 證明，列為 C。

## DFU 與 telemetry 的區分

`Q4/a.smali` 定義另一組 DFU/SMP UUID：

- service：`8D53DC1D-1DB7-4CD3-868B-8A527460AA84`
- characteristic：`DA2E7828-FBCE-4E01-AE9E-261174997C48`

GATT discovery 的錯誤字串是 `Device does not support SMP service`／`SMP characteristic`。這一組不是上述 IMU telemetry 的 NUS service，不能拿來當 raw sensor notification characteristic。

## 與 Flutter AOT 的連接

原生橋接只負責權限、掃描、連線、GATT write/notify 與事件送入 Flutter；`BleMocapDataParser.parseReceivedPacket`、raw IMU batch、quaternion／速度／位置 parser 與各運動計算器在 `libapp.so`。因此完整移植需要同時保留本文件的 native bridge 邊界與 [ORIGINAL_ALGORITHM_EVIDENCE_20260915.md](ORIGINAL_ALGORITHM_EVIDENCE_20260915.md) 的 AOT parser/algorithm 邊界。
