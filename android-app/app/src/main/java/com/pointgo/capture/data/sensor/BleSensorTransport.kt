package com.pointgo.capture.data.sensor

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.pointgo.capture.data.model.RawSample
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.util.UUID
import kotlin.coroutines.resume

/** BLE transport for the Poin+T NUS control channel and raw IMU channel. */
class BleSensorTransport(
    private val context: Context,
    private val serviceUuid: UUID = PoinTGoProtocol.SERVICE_UUID,
    private val notifyCharacteristicUuid: UUID = PoinTGoProtocol.NOTIFY_CHARACTERISTIC_UUID,
    private val rawNotifyCharacteristicUuid: UUID = RAW_NOTIFY_CHARACTERISTIC_UUID,
    private val commandCharacteristicUuid: UUID = PoinTGoProtocol.COMMAND_CHARACTERISTIC_UUID,
    private val batchDecoder: RawBatchDecoder = PoinTGoFrameDecoder(),
    private val startCommands: List<ByteArray> = PoinTGoProtocol.CAPTURED_HIGH_RATE_START,
    private val stopCommands: List<ByteArray> = PoinTGoProtocol.CAPTURED_STOP,
) : SensorTransport, Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _connectionState = MutableStateFlow<SensorConnectionState>(SensorConnectionState.Disconnected)
    private val _samples = MutableSharedFlow<RawSample>(extraBufferCapacity = 2_048)
    private var gatt: BluetoothGatt? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var rawNotifyCharacteristic: BluetoothGattCharacteristic? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var pendingWrite: CompletableDeferred<Boolean>? = null
    private var pendingDescriptorWrite: CompletableDeferred<Boolean>? = null
    private var activeScan: Pair<BluetoothLeScanner, ScanCallback>? = null
    private var notificationBuffer = ByteArray(0)
    private var servicesRequested = false

    override val connectionState: StateFlow<SensorConnectionState> = _connectionState.asStateFlow()
    override val samples: SharedFlow<RawSample> = _samples.asSharedFlow()

    @SuppressLint("MissingPermission")
    override suspend fun connect(deviceAddress: String?) {
        if (!hasBluetoothPermission()) {
            _connectionState.value = SensorConnectionState.Error("未授予藍牙權限")
            return
        }
        val adapter = bluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            _connectionState.value = SensorConnectionState.Error("藍牙無法使用或目前已關閉")
            return
        }
        val resolvedAddress = deviceAddress?.trim()?.takeIf { it.isNotEmpty() } ?: scanForDevice()?.address
        if (resolvedAddress.isNullOrBlank()) {
            _connectionState.value = SensorConnectionState.Error("掃描期間找不到 Poin+T 感測器")
            return
        }
        closeGatt()
        val device = runCatching { adapter.getRemoteDevice(resolvedAddress) }.getOrNull()
        if (device == null) {
            _connectionState.value = SensorConnectionState.Error("無效的 BLE 裝置位址")
            return
        }
        _connectionState.value = SensorConnectionState.Connecting(resolvedAddress)
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, callback)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect() {
        closeGatt()
        _connectionState.value = SensorConnectionState.Disconnected
    }

    @SuppressLint("MissingPermission")
    override suspend fun startStream() {
        val activeGatt = gatt
        val statusNotify = notifyCharacteristic
        val rawNotify = rawNotifyCharacteristic
        if (activeGatt == null || (statusNotify == null && rawNotify == null) || commandCharacteristic == null) {
            _connectionState.value = SensorConnectionState.Error("感測器尚未連線，或找不到 NUS 特徵")
            return
        }
        batchDecoder.reset()
        resetNotificationBuffer()
        val notificationCharacteristics = listOfNotNull(statusNotify, rawNotify).distinctBy { it.uuid }
        if (!notificationCharacteristics.all { enableNotifications(activeGatt, it) }) {
            _connectionState.value = SensorConnectionState.Error("無法啟用 Poin+T 通知")
            return
        }
        for (command in startCommands) {
            if (!writeCommand(activeGatt, command)) {
                _connectionState.value = SensorConnectionState.Error("Poin+T 拒絕高頻串流啟動命令")
                return
            }
            delay(COMMAND_PACING_MILLIS)
        }
        _connectionState.value = SensorConnectionState.Streaming(activeGatt.device.name ?: activeGatt.device.address)
    }

    @SuppressLint("MissingPermission")
    override suspend fun stopStream() {
        val activeGatt = gatt
        if (activeGatt != null) {
            stopCommands.forEach { command ->
                writeCommand(activeGatt, command)
                delay(COMMAND_PACING_MILLIS)
            }
            listOfNotNull(notifyCharacteristic, rawNotifyCharacteristic)
                .distinctBy { it.uuid }
                .forEach { disableNotifications(activeGatt, it) }
            _connectionState.value = SensorConnectionState.Connected(activeGatt.device.name ?: activeGatt.device.address)
        }
        batchDecoder.reset()
        resetNotificationBuffer()
    }

    override fun close() {
        stopScan()
        closeGatt()
        scope.cancel()
    }

    private fun bluetoothAdapter(): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private fun hasBluetoothPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private suspend fun scanForDevice(): BluetoothDevice? = suspendCancellableCoroutine { continuation ->
        val scanner = bluetoothAdapter()?.bluetoothLeScanner
        if (scanner == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        _connectionState.value = SensorConnectionState.Scanning
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUuid)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                stopScan()
                if (continuation.isActive) continuation.resume(device)
            }
            override fun onScanFailed(errorCode: Int) {
                stopScan()
                if (continuation.isActive) continuation.resume(null)
            }
        }
        activeScan = scanner to callback
        runCatching { scanner.startScan(filters, settings, callback) }.onFailure {
            stopScan()
            if (continuation.isActive) continuation.resume(null)
        }
        scope.launch {
            delay(SCAN_TIMEOUT_MILLIS)
            if (continuation.isActive) {
                stopScan()
                continuation.resume(null)
            }
        }
        continuation.invokeOnCancellation { stopScan() }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        val scan = activeScan ?: return
        runCatching { scan.first.stopScan(scan.second) }
        activeScan = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun enableNotifications(activeGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic): Boolean {
        if (!activeGatt.setCharacteristicNotification(characteristic, true)) return false
        val descriptor = characteristic.getDescriptor(PoinTGoProtocol.CLIENT_CHARACTERISTIC_CONFIG_UUID) ?: return true
        val completion = CompletableDeferred<Boolean>()
        pendingDescriptorWrite = completion
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (!activeGatt.writeDescriptor(descriptor)) {
            pendingDescriptorWrite = null
            return false
        }
        return withTimeoutOrNull(GATT_OPERATION_TIMEOUT_MILLIS) { completion.await() } == true
    }

    @SuppressLint("MissingPermission")
    private fun disableNotifications(activeGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        activeGatt.setCharacteristicNotification(characteristic, false)
        characteristic.getDescriptor(PoinTGoProtocol.CLIENT_CHARACTERISTIC_CONFIG_UUID)?.let { descriptor ->
            descriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            activeGatt.writeDescriptor(descriptor)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeCommand(activeGatt: BluetoothGatt, bytes: ByteArray): Boolean {
        val characteristic = commandCharacteristic ?: return false
        val completion = CompletableDeferred<Boolean>()
        pendingWrite = completion
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.value = bytes
        if (!activeGatt.writeCharacteristic(characteristic)) {
            pendingWrite = null
            return false
        }
        return withTimeoutOrNull(GATT_OPERATION_TIMEOUT_MILLIS) { completion.await() } == true
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        notifyCharacteristic = null
        rawNotifyCharacteristic = null
        commandCharacteristic = null
        pendingWrite?.cancel()
        pendingDescriptorWrite?.cancel()
        pendingWrite = null
        pendingDescriptorWrite = null
        servicesRequested = false
        resetNotificationBuffer()
    }

    private fun discoverServicesOnce(activeGatt: BluetoothGatt) {
        if (servicesRequested) return
        servicesRequested = true
        if (!activeGatt.discoverServices()) Log.w(TAG, "discoverServices() returned false")
    }

    @SuppressLint("MissingPermission")
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                servicesRequested = false
                _connectionState.value = SensorConnectionState.Error("GATT 連線失敗（$status）")
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    servicesRequested = false
                    resetNotificationBuffer()
                    _connectionState.value = SensorConnectionState.Connected(gatt.device.name ?: gatt.device.address)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && gatt.requestMtu(MTU_REQUEST)) {
                        Log.d(TAG, "Requested MTU $MTU_REQUEST")
                        scope.launch {
                            delay(MTU_TIMEOUT_MILLIS)
                            discoverServicesOnce(gatt)
                        }
                    } else {
                        discoverServicesOnce(gatt)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    servicesRequested = false
                    notifyCharacteristic = null
                    rawNotifyCharacteristic = null
                    commandCharacteristic = null
                    resetNotificationBuffer()
                    _connectionState.value = SensorConnectionState.Disconnected
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU changed: mtu=$mtu status=$status")
            discoverServicesOnce(gatt)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = SensorConnectionState.Error("GATT 服務探索失敗（$status）")
                return
            }
            val service = gatt.getService(serviceUuid)
            if (service == null) {
                _connectionState.value = SensorConnectionState.Error("找不到 Poin+T NUS 服務")
                return
            }
            notifyCharacteristic = service.getCharacteristic(notifyCharacteristicUuid)
                ?: service.characteristics.firstOrNull { characteristic ->
                    characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ||
                        characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                }
            rawNotifyCharacteristic = gatt.services.asSequence()
                .flatMap { it.characteristics.asSequence() }
                .firstOrNull { characteristic -> characteristic.uuid == rawNotifyCharacteristicUuid }
                ?: service.characteristics.firstOrNull { characteristic ->
                    characteristic.uuid != notifyCharacteristic?.uuid &&
                        characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                }
            commandCharacteristic = service.getCharacteristic(commandCharacteristicUuid)
                ?: service.characteristics.firstOrNull { characteristic ->
                    characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                        characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
                }
            if ((notifyCharacteristic == null && rawNotifyCharacteristic == null) || commandCharacteristic == null) {
                _connectionState.value = SensorConnectionState.Error("找不到 Poin+T NUS 通知／寫入特徵")
            } else {
                Log.d(TAG, "Characteristics notify=${notifyCharacteristic?.uuid} raw=${rawNotifyCharacteristic?.uuid} write=${commandCharacteristic?.uuid}")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            pendingDescriptorWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
            pendingDescriptorWrite = null
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            pendingWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
            pendingWrite = null
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            emitNotification(characteristic.uuid, characteristic.value)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            emitNotification(characteristic.uuid, value)
        }
    }

    private fun emitNotification(characteristicUuid: UUID, payload: ByteArray) {
        if (payload.isEmpty()) return
        val isRawChannel = characteristicUuid == rawNotifyCharacteristicUuid ||
            (rawNotifyCharacteristic == null && characteristicUuid == notifyCharacteristicUuid)
        val frames = if (isRawChannel) appendNotificationFragment(payload) else listOf(payload)
        if (frames.isEmpty()) {
            Log.d(TAG, "Notification fragment uuid=$characteristicUuid len=${payload.size} first=${hexPreview(payload)}")
            return
        }
        frames.forEach { frame ->
            Log.d(TAG, "Notification frame uuid=$characteristicUuid len=${frame.size} first=${hexPreview(frame)}")
            val decoded = runCatching { batchDecoder.decode(frame, System.nanoTime()) }
                .onFailure { error -> Log.e(TAG, "Frame decode failed len=${frame.size}", error) }
                .getOrDefault(emptyList())
            Log.d(TAG, "Decoded samples=${decoded.size}")
            if (decoded.isNotEmpty()) scope.launch { decoded.forEach { sample -> _samples.emit(sample) } }
        }
    }

    private fun appendNotificationFragment(fragment: ByteArray): List<ByteArray> {
        if (notificationBuffer.isEmpty() && fragment.size >= HCI_NOTIFICATION_BYTES) return listOf(fragment.copyOf())
        val combined = ByteArray(notificationBuffer.size + fragment.size)
        notificationBuffer.copyInto(combined)
        fragment.copyInto(combined, notificationBuffer.size)
        notificationBuffer = combined
        val frames = mutableListOf<ByteArray>()
        while (notificationBuffer.isNotEmpty()) {
            val expectedLength = expectedFrameLength(notificationBuffer) ?: break
            if (notificationBuffer.size < expectedLength) break
            frames += notificationBuffer.copyOfRange(0, expectedLength)
            notificationBuffer = notificationBuffer.copyOfRange(expectedLength, notificationBuffer.size)
        }
        if (notificationBuffer.size > MAX_NOTIFICATION_BUFFER_BYTES) {
            Log.w(TAG, "Dropping oversized notification buffer (${notificationBuffer.size} bytes)")
            resetNotificationBuffer()
        }
        return frames
    }

    private fun expectedFrameLength(buffer: ByteArray): Int? {
        if (buffer.size < 2) return null
        val count = buffer[1].toInt() and 0xff
        val aotLength = AOT_HEADER_BYTES + AOT_SAMPLE_BYTES * count
        return when {
            count in 1..MAX_AOT_SAMPLES && buffer.size >= aotLength -> aotLength
            buffer.size >= HCI_NOTIFICATION_BYTES -> HCI_NOTIFICATION_BYTES
            else -> null
        }
    }

    private fun resetNotificationBuffer() { notificationBuffer = ByteArray(0) }

    private fun hexPreview(bytes: ByteArray): String =
        bytes.take(8).joinToString(separator = " ") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        const val TAG = "PoinTGoBLE"
        const val RAW_NOTIFY_CHARACTERISTIC_UUID_STRING = "da2e7828-fbce-4e01-ae9e-261174997c48"
        val RAW_NOTIFY_CHARACTERISTIC_UUID: UUID = UUID.fromString(RAW_NOTIFY_CHARACTERISTIC_UUID_STRING)
        const val SCAN_TIMEOUT_MILLIS = 10_000L
        const val GATT_OPERATION_TIMEOUT_MILLIS = 2_000L
        const val COMMAND_PACING_MILLIS = 35L
        const val MTU_REQUEST = 247
        const val MTU_TIMEOUT_MILLIS = 1_500L
        const val HCI_NOTIFICATION_BYTES = 61
        const val MAX_NOTIFICATION_BUFFER_BYTES = 256
        const val AOT_HEADER_BYTES = 8
        const val AOT_SAMPLE_BYTES = 12
        const val MAX_AOT_SAMPLES = 20
    }
}
