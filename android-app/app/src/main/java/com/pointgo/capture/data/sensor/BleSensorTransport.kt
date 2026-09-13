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

/**
 * Android BLE transport for the Poin+T NUS service.
 *
 * A blank address scans for the NUS service, so the quick path does not make
 * the user discover or type a MAC address. An address in the advanced panel
 * skips scanning and is useful when the phone has already bonded to a device.
 */
class BleSensorTransport(
    private val context: Context,
    private val serviceUuid: UUID = PoinTGoProtocol.SERVICE_UUID,
    private val notifyCharacteristicUuid: UUID = PoinTGoProtocol.NOTIFY_CHARACTERISTIC_UUID,
    private val commandCharacteristicUuid: UUID = PoinTGoProtocol.COMMAND_CHARACTERISTIC_UUID,
    private val batchDecoder: RawBatchDecoder = PoinTGoFrameDecoder(),
    private val startCommands: List<ByteArray> = PoinTGoProtocol.CAPTURED_HIGH_RATE_START,
    private val stopCommands: List<ByteArray> = PoinTGoProtocol.CAPTURED_STOP,
) : SensorTransport, Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _connectionState = MutableStateFlow<SensorConnectionState>(
        SensorConnectionState.Disconnected,
    )
    private val _samples = MutableSharedFlow<RawSample>(extraBufferCapacity = 2_048)
    private var gatt: BluetoothGatt? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var pendingWrite: CompletableDeferred<Boolean>? = null
    private var pendingDescriptorWrite: CompletableDeferred<Boolean>? = null
    private var activeScan: Pair<BluetoothLeScanner, ScanCallback>? = null

    override val connectionState: StateFlow<SensorConnectionState> = _connectionState.asStateFlow()
    override val samples: SharedFlow<RawSample> = _samples.asSharedFlow()

    @SuppressLint("MissingPermission")
    override suspend fun connect(deviceAddress: String?) {
        if (!hasBluetoothPermission()) {
            _connectionState.value = SensorConnectionState.Error(
                "Bluetooth permission is not granted",
            )
            return
        }
        val adapter = bluetoothAdapter()
        if (adapter == null || !adapter.isEnabled) {
            _connectionState.value = SensorConnectionState.Error(
                "Bluetooth is unavailable or disabled",
            )
            return
        }

        val resolvedAddress = deviceAddress?.trim()?.takeIf { it.isNotEmpty() }
            ?: scanForDevice()?.address
        if (resolvedAddress.isNullOrBlank()) {
            _connectionState.value = SensorConnectionState.Error(
                "No Poin+T sensor was found during the scan",
            )
            return
        }

        closeGatt()
        val device = runCatching { adapter.getRemoteDevice(resolvedAddress) }.getOrNull()
        if (device == null) {
            _connectionState.value = SensorConnectionState.Error("Invalid BLE device address")
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
        val notify = notifyCharacteristic
        if (activeGatt == null || notify == null || commandCharacteristic == null) {
            _connectionState.value = SensorConnectionState.Error(
                "The sensor is not connected or its NUS characteristics are unavailable",
            )
            return
        }

        batchDecoder.reset()
        if (!enableNotifications(activeGatt, notify)) {
            _connectionState.value = SensorConnectionState.Error(
                "Could not enable Poin+T notifications",
            )
            return
        }

        for (command in startCommands) {
            if (!writeCommand(activeGatt, command)) {
                _connectionState.value = SensorConnectionState.Error(
                    "Poin+T rejected a high-rate start command",
                )
                return
            }
            // The vendor app spaces the short control writes by roughly tens
            // of milliseconds; keep the same pacing for firmware variants that
            // do not accept back-to-back writes.
            delay(COMMAND_PACING_MILLIS)
        }
        _connectionState.value = SensorConnectionState.Streaming(
            activeGatt.device.name ?: activeGatt.device.address,
        )
    }

    @SuppressLint("MissingPermission")
    override suspend fun stopStream() {
        val activeGatt = gatt
        if (activeGatt != null) {
            stopCommands.forEach { command ->
                writeCommand(activeGatt, command)
                delay(COMMAND_PACING_MILLIS)
            }
            notifyCharacteristic?.let { disableNotifications(activeGatt, it) }
            _connectionState.value = SensorConnectionState.Connected(
                activeGatt.device.name ?: activeGatt.device.address,
            )
        }
        batchDecoder.reset()
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
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN,
            ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private suspend fun scanForDevice(): BluetoothDevice? = suspendCancellableCoroutine { continuation ->
        val scanner = bluetoothAdapter()?.bluetoothLeScanner
        if (scanner == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        _connectionState.value = SensorConnectionState.Scanning
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(serviceUuid)).build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
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
        runCatching { scanner.startScan(filters, settings, callback) }
            .onFailure {
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
    private suspend fun enableNotifications(
        activeGatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ): Boolean {
        if (!activeGatt.setCharacteristicNotification(characteristic, true)) return false
        val descriptor = characteristic.getDescriptor(PoinTGoProtocol.CLIENT_CHARACTERISTIC_CONFIG_UUID)
            ?: return true
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
    private fun disableNotifications(
        activeGatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
    ) {
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
        commandCharacteristic = null
        pendingWrite?.cancel()
        pendingDescriptorWrite?.cancel()
        pendingWrite = null
        pendingDescriptorWrite = null
    }

    @SuppressLint("MissingPermission")
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = SensorConnectionState.Error(
                    "GATT connection failed ($status)",
                )
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = SensorConnectionState.Connected(
                        gatt.device.name ?: gatt.device.address,
                    )
                    gatt.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    notifyCharacteristic = null
                    commandCharacteristic = null
                    _connectionState.value = SensorConnectionState.Disconnected
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _connectionState.value = SensorConnectionState.Error(
                    "GATT service discovery failed ($status)",
                )
                return
            }
            val service = gatt.getService(serviceUuid)
            if (service == null) {
                _connectionState.value = SensorConnectionState.Error(
                    "Poin+T NUS service was not found",
                )
                return
            }
            notifyCharacteristic = service.getCharacteristic(notifyCharacteristicUuid)
                ?: service.characteristics.firstOrNull { characteristic ->
                    characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ||
                        characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                }
            commandCharacteristic = service.getCharacteristic(commandCharacteristicUuid)
                ?: service.characteristics.firstOrNull { characteristic ->
                    characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                        characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
                }
            if (notifyCharacteristic == null || commandCharacteristic == null) {
                _connectionState.value = SensorConnectionState.Error(
                    "Poin+T NUS notify/write characteristics were not found",
                )
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            pendingDescriptorWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
            pendingDescriptorWrite = null
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            pendingWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
            pendingWrite = null
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            emitNotification(characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            emitNotification(value)
        }
    }

    private fun emitNotification(payload: ByteArray) {
        val decoded = runCatching {
            batchDecoder.decode(payload, System.nanoTime())
        }.getOrDefault(emptyList())
        if (decoded.isEmpty()) return
        scope.launch {
            decoded.forEach { sample -> _samples.emit(sample) }
        }
    }

    private companion object {
        const val SCAN_TIMEOUT_MILLIS = 10_000L
        const val GATT_OPERATION_TIMEOUT_MILLIS = 2_000L
        const val COMMAND_PACING_MILLIS = 35L
    }
}
