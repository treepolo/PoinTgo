# 原廠 APK Flutter AOT 組譯索引（2026-09-15）

unflutter 會依 owner/class 把組譯檔放在遞迴子目錄；本索引使用遞迴掃描。組譯檔名尾端是 codegen offset，不是 function metadata 的執行 PC，因此實際從每個檔案的第一個組譯列（`0x... bytes instruction`）讀取 PC，再做精確連接。

## 可重現統計

- 組譯根目錄：`analysis/aot_unflutter/asm/`
- 遞迴 `.txt` 組譯檔：**46,790**，**107,073,436 bytes**
- owner/class 目錄：**4,474**
- 含可解析 PC 的組譯檔：**46,790**；未解析：**0**
- AOT function metadata：**46,790**
- 有組譯檔連結：**46,790**；尚未連結：**0**

## 重要入口（直接證據）

PC、size 與檔案路徑均來自本地原廠 `libapp.so` 的 AOT export；路徑是相對 repo 的可重現位置。

### `BleMocapDataParser.parseReceivedPacket`
- `BleMocapDataParser.parseReceivedPacket_1b946c` · PC `0x83fdac` · size `352` · `analysis/aot_unflutter/asm/BleMocapDataParser/parseReceivedPacket_1b946c.txt`

### `_parseRawImuBatch`
- `BleMocapDataParser._parseRawImuBatch@859188570_1b967c` · PC `0x83ffbc` · size `3228` · `analysis/aot_unflutter/asm/BleMocapDataParser/_parseRawImuBatch@859188570_1b967c.txt`

### `_parseRawAccelGyroPrsTmp`
- `BleMocapDataParser._parseRawAccelGyroPrsTmp@859188570_1bd880` · PC `0x8441c0` · size `2844` · `analysis/aot_unflutter/asm/BleMocapDataParser/_parseRawAccelGyroPrsTmp@859188570_1bd880.txt`

### `_parseQuatAclVelPos`
- `BleMocapDataParser._parseQuatAclVelPos@859188570_1be39c` · PC `0x844cdc` · size `5088` · `analysis/aot_unflutter/asm/BleMocapDataParser/_parseQuatAclVelPos@859188570_1be39c.txt`

### `_parseQuatAgzVgzPgz`
- `BleMocapDataParser._parseQuatAgzVgzPgz@859188570_1bf77c` · PC `0x8460bc` · size `2976` · `analysis/aot_unflutter/asm/BleMocapDataParser/_parseQuatAgzVgzPgz@859188570_1bf77c.txt`

### `_parseQuaternionPacket`
- `BleMocapDataParser._parseQuaternionPacket@859188570_1c031c` · PC `0x846c5c` · size `1920` · `analysis/aot_unflutter/asm/BleMocapDataParser/_parseQuaternionPacket@859188570_1c031c.txt`

### `CommandMeasureRawImuHighRate.command`
- `CommandMeasureRawImuHighRate.command_772edc` · PC `0xdf981c` · size `152` · `analysis/aot_unflutter/asm/CommandMeasureRawImuHighRate/command_772edc.txt`

### `BleApiImpl.startBleDeviceScan`
- `BleApiImpl.startBleDeviceScan_1b6a20` · PC `0x83d360` · size `76` · `analysis/aot_unflutter/asm/BleApiImpl/startBleDeviceScan_1b6a20.txt`

### `BleApiImpl.connectDevice`
- `BleApiImpl.connectDevice_8a7ebc` · PC `0xf2e7fc` · size `136` · `analysis/aot_unflutter/asm/BleApiImpl/connectDevice_8a7ebc.txt`

### `BleApiImpl.disconnectDevice`
- `BleApiImpl.disconnectDevice_1d9cd4` · PC `0x860614` · size `136` · `analysis/aot_unflutter/asm/BleApiImpl/disconnectDevice_1d9cd4.txt`

### `BleApiImpl._onReceivePacketFromBleDevice`
- `BleApiImpl._onReceivePacketFromBleDevice@859188570_1b70b0` · PC `0x83d9f0` · size `280` · `analysis/aot_unflutter/asm/BleApiImpl/_onReceivePacketFromBleDevice@859188570_1b70b0.txt`

### `DeviceConnectionManager.connect`
- `DeviceConnectionManager.connect_8a7974` · PC `0xf2e2b4` · size `264` · `analysis/aot_unflutter/asm/DeviceConnectionManager/connect_8a7974.txt`

### `DeviceConnectionManager.disconnect`
- `DeviceConnectionManager.disconnect_1d9a28` · PC `0x860368` · size `280` · `analysis/aot_unflutter/asm/DeviceConnectionManager/disconnect_1d9a28.txt`

### `DeviceConnectionManager._onConnectionEvent`
- `DeviceConnectionManager._onConnectionEvent@1123470780_8a63dc` · PC `0xf2cd1c` · size `60` · `analysis/aot_unflutter/asm/DeviceConnectionManager/_onConnectionEvent@1123470780_8a63dc.txt`
- `DeviceConnectionManager._onConnectionEvent@1123470780_8a6418` · PC `0xf2cd58` · size `996` · `analysis/aot_unflutter/asm/DeviceConnectionManager/_onConnectionEvent@1123470780_8a6418.txt`

### `DeviceConnectionManager._onDeviceDiscovered`
- `DeviceConnectionManager._onDeviceDiscovered@1123470780_8a70a8` · PC `0xf2d9e8` · size `60` · `analysis/aot_unflutter/asm/DeviceConnectionManager/_onDeviceDiscovered@1123470780_8a70a8.txt`
- `DeviceConnectionManager._onDeviceDiscovered@1123470780_8a70e4` · PC `0xf2da24` · size `1200` · `analysis/aot_unflutter/asm/DeviceConnectionManager/_onDeviceDiscovered@1123470780_8a70e4.txt`

### `DeviceConnectionManager._autoRescan`
- `DeviceConnectionManager._autoRescan@1123470780_8a67fc` · PC `0xf2d13c` · size `296` · `analysis/aot_unflutter/asm/DeviceConnectionManager/_autoRescan@1123470780_8a67fc.txt`

### `AccelCalibrationUtil.resetAccelMeasureStateForOffsetAxisCal`
- `AccelCalibrationUtil.resetAccelMeasureStateForOffsetAxisCal_1bbf48` · PC `0x842888` · size `504` · `analysis/aot_unflutter/asm/AccelCalibrationUtil/resetAccelMeasureStateForOffsetAxisCal_1bbf48.txt`

### `isAccelMeasuredAllMocapPositionForBiasCal`
- `AccelCalibrationUtil.isAccelMeasuredAllMocapPositionForBiasCal_1bc390` · PC `0x842cd0` · size `576` · `analysis/aot_unflutter/asm/AccelCalibrationUtil/isAccelMeasuredAllMocapPositionForBiasCal_1bc390.txt`

### `validateAccelXYZAndUpdateMeasureStateForAccelBiasCal`
- `AccelCalibrationUtil.validateAccelXYZAndUpdateMeasureStateForAccelBiasCal_1bc5d0` · PC `0x842f10` · size `492` · `analysis/aot_unflutter/asm/AccelCalibrationUtil/validateAccelXYZAndUpdateMeasureStateForAccelBiasCal_1bc5d0.txt`

### `updateMeasureStateForAccelBiasCal`
- `AccelCalibrationUtil.updateMeasureStateForAccelBiasCal_1bc7bc` · PC `0x8430fc` · size `500` · `analysis/aot_unflutter/asm/AccelCalibrationUtil/updateMeasureStateForAccelBiasCal_1bc7bc.txt`

### `isAccelMeasuredAllMocapPositionForOffsetAxisCal`
- `AccelCalibrationUtil.isAccelMeasuredAllMocapPositionForOffsetAxisCal_1bd260` · PC `0x843ba0` · size `576` · `analysis/aot_unflutter/asm/AccelCalibrationUtil/isAccelMeasuredAllMocapPositionForOffsetAxisCal_1bd260.txt`

### `validateAccelXYZAndUpdateMeasureStateForOffsetAxisCal`
- `AccelCalibrationUtil.validateAccelXYZAndUpdateMeasureStateForOffsetAxisCal_1bd4a0` · PC `0x843de0` · size `492` · `analysis/aot_unflutter/asm/AccelCalibrationUtil/validateAccelXYZAndUpdateMeasureStateForOffsetAxisCal_1bd4a0.txt`

### `updateAccelMeasureStateForOffsetAxisCal`
- `AccelCalibrationUtil.updateAccelMeasureStateForOffsetAxisCal_1bd68c` · PC `0x843fcc` · size `500` · `analysis/aot_unflutter/asm/AccelCalibrationUtil/updateAccelMeasureStateForOffsetAxisCal_1bd68c.txt`

### `_parseAccelOffsetAxisCalibrationPacket`
- `BleMocapDataParser._parseAccelOffsetAxisCalibrationPacket@859188570_1bcc40` · PC `0x843580` · size `1568` · `analysis/aot_unflutter/asm/BleMocapDataParser/_parseAccelOffsetAxisCalibrationPacket@859188570_1bcc40.txt`

### `_parseAccelBiasCalibrationPacket`
- `BleMocapDataParser._parseAccelBiasCalibrationPacket@859188570_1bb788` · PC `0x8420c8` · size `1984` · `analysis/aot_unflutter/asm/BleMocapDataParser/_parseAccelBiasCalibrationPacket@859188570_1bb788.txt`

### `_parseGyroCalibrationPacket`
- `BleMocapDataParser._parseGyroCalibrationPacket@859188570_1bb5ec` · PC `0x841f2c` · size `412` · `analysis/aot_unflutter/asm/BleMocapDataParser/_parseGyroCalibrationPacket@859188570_1bb5ec.txt`

### `JsWeightliftingCalculator._parseMetrics`
- `JsWeightliftingCalculator._parseMetrics@1493039605_249010` · PC `0x8cf950` · size `14740` · `analysis/aot_unflutter/asm/JsWeightliftingCalculator/_parseMetrics@1493039605_249010.txt`

### `JsWeightliftingCalculator.processMotionData`
- `JsWeightliftingCalculator.processMotionData_24d1f8` · PC `0x8d3b38` · size `396` · `analysis/aot_unflutter/asm/JsWeightliftingCalculator/processMotionData_24d1f8.txt`

### `JsWeightliftingCalculator._handleCalibrationComplete`
- `JsWeightliftingCalculator._handleCalibrationComplete@1493039605_248704` · PC `0x8cf044` · size `1160` · `analysis/aot_unflutter/asm/JsWeightliftingCalculator/_handleCalibrationComplete@1493039605_248704.txt`

### `JsVbtCalculator._parseRep`
- `JsVbtCalculator._parseRep@1345406029_2dbcf0` · PC `0x962630` · size `4108` · `analysis/aot_unflutter/asm/JsVbtCalculator/_parseRep@1345406029_2dbcf0.txt`

### `JsVbtCalculator.flushPendingEmits`
- `JsVbtCalculator.flushPendingEmits_37a9ec` · PC `0xa0132c` · size `1040` · `analysis/aot_unflutter/asm/JsVbtCalculator/flushPendingEmits_37a9ec.txt`

### `JsRotationCalculator._parseRep`
- `JsRotationCalculator._parseRep@1421269909_315a30` · PC `0x99c370` · size `3448` · `analysis/aot_unflutter/asm/JsRotationCalculator/_parseRep@1421269909_315a30.txt`

### `OneRmTest.calculateLvp`
- `OneRmTest.calculateLvp_2e839c` · PC `0x96ecdc` · size `924` · `analysis/aot_unflutter/asm/OneRmTest/calculateLvp_2e839c.txt`

### `JsOneRmCalculator.calculateLvp`
- `JsOneRmCalculator.calculateLvp_2f05e8` · PC `0x976f28` · size `256` · `analysis/aot_unflutter/asm/JsOneRmCalculator/calculateLvp_2f05e8.txt`

### `JumpRunner.pushBatch`
- `JumpRunner.pushBatch_2779b0` · PC `0x8fe2f0` · size `920` · `analysis/aot_unflutter/asm/JumpRunner/pushBatch_2779b0.txt`

### `detectJumpCountermovement`
- `detectJumpCountermovement_27c6ac` · PC `0x902fec` · size `912` · `analysis/aot_unflutter/asm/detectJumpCountermovement_27c6ac.txt`

### `JumpFeedbackAnalyzer.analyze`
- `JumpFeedbackAnalyzer.analyze_2a5f64` · PC `0x92c8a4` · size `1112` · `analysis/aot_unflutter/asm/JumpFeedbackAnalyzer/analyze_2a5f64.txt`

### `AuthService.authStateChanges`
- `AuthService.authStateChanges_12d198` · PC `0x7b3ad8` · size `56` · `analysis/aot_unflutter/asm/AuthService/authStateChanges_12d198.txt`

### `AuthService.validateSession`
- `AuthService.validateSession_8c7924` · PC `0xf4e264` · size `572` · `analysis/aot_unflutter/asm/AuthService/validateSession_8c7924.txt`

### `LoginPage.build`
- `LoginPage.build_3bb3e0` · PC `0xa41d20` · size `1344` · `analysis/aot_unflutter/asm/LoginPage/build_3bb3e0.txt`

## 解讀邊界

- 這是 release Flutter AOT；metadata 保留 function/class/PC/size，但不會還原原始 Dart 的區域變數、註解或完整型別名稱。
- 組譯連結證明入口與機器碼區段的對應，不單獨證明演算法公式；公式仍需常數池、欄位讀寫、呼叫邊與 runtime golden vectors。
- `functions_without_asm_match` 是 export 命名或 thunk 的工具缺口，不等同於功能不存在。

完整機器可讀資料：`analysis/asm_function_index.json`。
