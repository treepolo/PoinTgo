package com.treepolo.pointgo.clone;

import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * App-wide BLE owner.  It is started by the visitor launcher, so opening the
 * private clone connects before the user enters an analysis page.  Streaming
 * commands are a separate action; stopping a measurement never closes GATT.
 */
public final class SensorConnectionService extends Service {
    private static final String TAG = "PoinTGoConnection";
    public static final String ACTION_CONNECT = "com.treepolo.pointgo.clone.CONNECT";
    public static final String ACTION_START_STREAMING = "com.treepolo.pointgo.clone.START_STREAMING";
    public static final String ACTION_STOP_STREAMING = "com.treepolo.pointgo.clone.STOP_STREAMING";
    public static final String STATUS_ACTION = "com.treepolo.pointgo.clone.BLE_STATUS";
    public static final String RAW_ACTION = "com.treepolo.pointgo.clone.RAW_PACKET";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_CONNECTION = "connection";
    private static final UUID SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID RX_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID TX_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID RAW_NOTIFY_UUID = UUID.fromString("da2e7828-fbce-4e01-ae9e-261174997c48");
    private static final int HCI_NOTIFICATION_BYTES = 61;
    private static final int AOT_HEADER_BYTES = 8;
    private static final int AOT_SAMPLE_BYTES = 12;
    private static final int MAX_AOT_SAMPLES = 20;
    private static final int MAX_NOTIFICATION_BUFFER_BYTES = 256;
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final String LAST_KNOWN_ADDRESS = "D4:7E:7E:48:74:44";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<byte[]> commandProfile = buildCommandProfile();
    private final ArrayList<BluetoothGattCharacteristic> notificationQueue = new ArrayList<>();
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic rxCharacteristic;
    private BluetoothGattCharacteristic txCharacteristic;
    private BluetoothGattCharacteristic rawNotifyCharacteristic;
    private BluetoothGattCharacteristic pendingDescriptorCharacteristic;
    private BluetoothLeScanner scanner;
    private boolean scanning;
    private boolean connectionWanted = true;
    private boolean streamingRequested;
    private boolean commandsStarted;
    private boolean directFallbackAttempted;
    private boolean notificationsReady;
    private boolean mtuRequested;
    private boolean reconnectScheduled;
    private int notificationIndex;
    private int commandIndex;
    private int connectionAttempts;
    private int loggedNotifications;
    private byte[] notificationBuffer = new byte[0];

    public static void ensureStarted(android.content.Context context) {
        Intent intent = new Intent(context, SensorConnectionService.class);
        intent.setAction(ACTION_CONNECT);
        context.startService(intent);
    }

    public static void startStreaming(android.content.Context context) {
        Intent intent = new Intent(context, SensorConnectionService.class);
        intent.setAction(ACTION_START_STREAMING);
        context.startService(intent);
    }

    public static void stopStreaming(android.content.Context context) {
        Intent intent = new Intent(context, SensorConnectionService.class);
        intent.setAction(ACTION_STOP_STREAMING);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        connectionWanted = true;
        ensureConnection();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        connectionWanted = true;
        String action = intent == null ? ACTION_CONNECT : intent.getAction();
        if (ACTION_START_STREAMING.equals(action)) {
            streamingRequested = true;
            broadcastStatus("開始記錄：等待感測器資料…", "已連線／準備記錄");
            ensureConnection();
            beginCommandProfile();
        } else if (ACTION_STOP_STREAMING.equals(action)) {
            streamingRequested = false;
            commandsStarted = false;
            commandIndex = 0;
            writeBytes(new byte[]{0x03, 0x03});
            broadcastStatus("已停止記錄；感測器連線保持中", connectionLabel());
        } else {
            broadcastStatus("正在自動連線感測器…", connectionLabel());
            ensureConnection();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        connectionWanted = false;
        stopScan();
        if (gatt != null) {
            try {
                gatt.close();
            } catch (RuntimeException ignored) {
                // Bluetooth stack already closed.
            }
            gatt = null;
        }
        super.onDestroy();
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (!connectionWanted || gatt != null || !looksLikeSensor(result)) return;
            BluetoothDevice device = result.getDevice();
            stopScan();
            connectionAttempts++;
            broadcastStatus("找到感測器，正在連線…", "連線中 · " + safeName(device));
            try {
                gatt = device.connectGatt(SensorConnectionService.this, false,
                        gattCallback, BluetoothDevice.TRANSPORT_LE);
            } catch (SecurityException error) {
                broadcastStatus("沒有藍牙連線權限：" + error.getMessage(), "未連線");
                scheduleReconnect();
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            scanning = false;
            broadcastStatus("掃描失敗（" + errorCode + "），持續等待感測器…", "未連線");
            scheduleReconnect();
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt callbackGatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED
                    && status == BluetoothGatt.GATT_SUCCESS) {
                gatt = callbackGatt;
                notificationsReady = false;
                mtuRequested = false;
                broadcastStatus("已連線，正在探索服務…", "已連線");
                main.postDelayed(new Runnable() {
                    @Override public void run() {
                        try { callbackGatt.discoverServices(); }
                        catch (SecurityException error) { broadcastStatus("服務探索權限不足", "已連線"); }
                    }
                }, 220L);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                commandsStarted = false;
                notificationsReady = false;
                mtuRequested = false;
                rxCharacteristic = null;
                txCharacteristic = null;
                rawNotifyCharacteristic = null;
                notificationQueue.clear();
                resetNotificationBuffer();
                if (gatt == callbackGatt) gatt = null;
                broadcastStatus("感測器已斷線（" + status + "），持續自動重連…", "未連線");
                scheduleReconnect();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt callbackGatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                broadcastStatus("服務探索失敗（" + status + "），持續重試…", "已連線");
                scheduleReconnect();
                return;
            }
            findCharacteristics(callbackGatt);
            Log.d(TAG, "Characteristics notify=" + uuidOf(txCharacteristic)
                    + " raw=" + uuidOf(rawNotifyCharacteristic)
                    + " write=" + uuidOf(rxCharacteristic));
            if (rxCharacteristic == null || (txCharacteristic == null && rawNotifyCharacteristic == null)) {
                broadcastStatus("找不到 Poin+T 通知／寫入特徵，持續重試…", "已連線");
                scheduleReconnect();
                return;
            }
            prepareNotifications(callbackGatt);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt callbackGatt,
                                      BluetoothGattDescriptor descriptor, int status) {
            if (!CCCD_UUID.equals(descriptor.getUuid())) return;
            pendingDescriptorCharacteristic = null;
            notificationIndex++;
            writeNextNotificationDescriptor();
        }

        @Override
        public void onMtuChanged(BluetoothGatt callbackGatt, int mtu, int status) {
            Log.d(TAG, "MTU changed mtu=" + mtu + " status=" + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastStatus("MTU " + mtu + "；連線保持中", connectionLabel());
            }
            beginCommandProfile();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt callbackGatt,
                                             BluetoothGattCharacteristic characteristic) {
            handleCharacteristicNotification(characteristic, characteristic.getValue());
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt callbackGatt,
                                             BluetoothGattCharacteristic characteristic,
                                             byte[] value) {
            handleCharacteristicNotification(characteristic, value);
        }
    };

    private void prepareNotifications(BluetoothGatt callbackGatt) {
        notificationsReady = false;
        notificationQueue.clear();
        notificationIndex = 0;
        pendingDescriptorCharacteristic = null;
        addNotificationCharacteristic(txCharacteristic);
        addNotificationCharacteristic(rawNotifyCharacteristic);
        for (BluetoothGattCharacteristic characteristic : notificationQueue) {
            try {
                callbackGatt.setCharacteristicNotification(characteristic, true);
            } catch (SecurityException error) {
                broadcastStatus("啟用感測器通知失敗：" + error.getMessage(), "已連線");
            }
        }
        writeNextNotificationDescriptor();
    }

    private void addNotificationCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (characteristic == null) return;
        int notifyMask = BluetoothGattCharacteristic.PROPERTY_NOTIFY
                | BluetoothGattCharacteristic.PROPERTY_INDICATE;
        if ((characteristic.getProperties() & notifyMask) == 0) return;
        for (BluetoothGattCharacteristic existing : notificationQueue) {
            if (existing.getUuid().equals(characteristic.getUuid())) return;
        }
        notificationQueue.add(characteristic);
    }

    private void writeNextNotificationDescriptor() {
        if (gatt == null) return;
        while (notificationIndex < notificationQueue.size()) {
            BluetoothGattCharacteristic characteristic = notificationQueue.get(notificationIndex);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
            if (descriptor == null) {
                notificationIndex++;
                continue;
            }
            pendingDescriptorCharacteristic = characteristic;
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            try {
                if (!gatt.writeDescriptor(descriptor)) {
                    notificationIndex++;
                    pendingDescriptorCharacteristic = null;
                    continue;
                }
            } catch (SecurityException error) {
                notificationIndex++;
                pendingDescriptorCharacteristic = null;
                broadcastStatus("啟用感測器通知失敗：" + error.getMessage(), "已連線");
                continue;
            }
            return;
        }
        notificationsReady = true;
        broadcastStatus("資料通知已啟用（狀態／raw）；連線保持中", connectionLabel());
        requestMtuOnce();
        beginCommandProfile();
    }

    private void requestMtuOnce() {
        if (gatt == null || mtuRequested) return;
        mtuRequested = true;
        try {
            if (!gatt.requestMtu(247)) Log.d(TAG, "requestMtu returned false");
        } catch (SecurityException error) {
            Log.d(TAG, "requestMtu denied", error);
        }
    }

    private void handleCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                                  byte[] payload) {
        if (payload == null || payload.length == 0) return;
        boolean startsRawFrame = (payload[0] & 0xff) == 0x04;
        boolean rawChannel = (rawNotifyCharacteristic != null
                && rawNotifyCharacteristic.getUuid().equals(characteristic.getUuid()))
                || (TX_UUID.equals(characteristic.getUuid())
                && (startsRawFrame || notificationBuffer.length > 0));
        if (loggedNotifications < 24) {
            Log.d(TAG, "notification uuid=" + characteristic.getUuid()
                    + " raw=" + rawChannel + " len=" + payload.length
                    + " first=" + hexPreview(payload));
            loggedNotifications++;
        }
        if (!rawChannel) {
            broadcastRaw(payload);
            return;
        }
        ArrayList<byte[]> frames = appendNotificationFragment(payload);
        for (byte[] frame : frames) broadcastRaw(frame);
    }

    private ArrayList<byte[]> appendNotificationFragment(byte[] fragment) {
        ArrayList<byte[]> frames = new ArrayList<>();
        if (fragment == null || fragment.length == 0) return frames;
        byte[] combined = new byte[notificationBuffer.length + fragment.length];
        System.arraycopy(notificationBuffer, 0, combined, 0, notificationBuffer.length);
        System.arraycopy(fragment, 0, combined, notificationBuffer.length, fragment.length);
        notificationBuffer = combined;
        while (notificationBuffer.length > 0) {
            if ((notificationBuffer[0] & 0xff) != 0x04) {
                int next = indexOfPacketType(notificationBuffer);
                if (next < 0) {
                    notificationBuffer = new byte[0];
                    break;
                }
                byte[] resync = new byte[notificationBuffer.length - next];
                System.arraycopy(notificationBuffer, next, resync, 0, resync.length);
                notificationBuffer = resync;
                if (notificationBuffer.length < 2) break;
            }
            int expected = expectedFrameLength(notificationBuffer);
            if (expected <= 0 || notificationBuffer.length < expected) break;
            byte[] frame = new byte[expected];
            System.arraycopy(notificationBuffer, 0, frame, 0, expected);
            frames.add(frame);
            byte[] remainder = new byte[notificationBuffer.length - expected];
            System.arraycopy(notificationBuffer, expected, remainder, 0, remainder.length);
            notificationBuffer = remainder;
        }
        if (notificationBuffer.length > MAX_NOTIFICATION_BUFFER_BYTES) {
            Log.w(TAG, "Dropping oversized notification buffer (" + notificationBuffer.length + " bytes)");
            resetNotificationBuffer();
        }
        return frames;
    }

    private int expectedFrameLength(byte[] buffer) {
        if (buffer.length < 2) return -1;
        int count = buffer[1] & 0xff;
        int aotLength = AOT_HEADER_BYTES + AOT_SAMPLE_BYTES * count;
        if (buffer.length >= HCI_NOTIFICATION_BYTES
                && (count < 5 || buffer.length < aotLength)) return HCI_NOTIFICATION_BYTES;
        if (count >= 1 && count <= MAX_AOT_SAMPLES && buffer.length >= aotLength) {
            return aotLength;
        }
        if (buffer.length >= HCI_NOTIFICATION_BYTES) return HCI_NOTIFICATION_BYTES;
        return -1;
    }

    private int indexOfPacketType(byte[] bytes) {
        for (int index = 1; index < bytes.length; index++) {
            if ((bytes[index] & 0xff) == 0x04) return index;
        }
        return -1;
    }

    private void resetNotificationBuffer() {
        notificationBuffer = new byte[0];
    }

    private void ensureConnection() {
        if (!connectionWanted || gatt != null || scanning) {
            beginCommandProfile();
            return;
        }
        if (!hasBluetoothPermission()) {
            broadcastStatus("需要藍牙權限才能自動連線", "未連線");
            return;
        }
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            broadcastStatus("請開啟手機藍牙，App 會持續等待連線", "未連線");
            scheduleReconnect();
            return;
        }
        try {
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) {
                broadcastStatus("手機不支援 BLE 掃描，持續等待…", "未連線");
                scheduleReconnect();
                return;
            }
            scanner.startScan(new ArrayList<ScanFilter>(),
                    new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                    scanCallback);
            scanning = true;
            broadcastStatus("App 已啟動自動掃描；不必先按開始記錄", "掃描感測器中…");
            main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!scanning || !connectionWanted || gatt != null) return;
                    stopScan();
                    if (!connectKnownDevice()) scheduleReconnect();
                }
            }, 12_000L);
        } catch (SecurityException error) {
            broadcastStatus("BLE 掃描權限不足：" + error.getMessage(), "未連線");
            scheduleReconnect();
        }
    }

    private boolean connectKnownDevice() {
        if (!connectionWanted || gatt != null) return false;
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) return false;
        try {
            BluetoothDevice device = adapter.getRemoteDevice(LAST_KNOWN_ADDRESS);
            directFallbackAttempted = true;
            connectionAttempts++;
            gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            broadcastStatus("正在嘗試已知 Poin+T 位址…", "連線中 · " + LAST_KNOWN_ADDRESS);
            return true;
        } catch (IllegalArgumentException | SecurityException error) {
            Log.d(TAG, "known-address fallback failed: " + error.getMessage());
            return false;
        }
    }

    private void scheduleReconnect() {
        if (!connectionWanted || reconnectScheduled) return;
        reconnectScheduled = true;
        if (!connectionWanted) return;
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                reconnectScheduled = false;
                ensureConnection();
            }
        }, 2_000L);
    }

    private void beginCommandProfile() {
        if (!streamingRequested || commandsStarted || !notificationsReady || gatt == null || rxCharacteristic == null) return;
        commandsStarted = true;
        commandIndex = 0;
        sendNextCommand();
    }

    private void sendNextCommand() {
        if (!streamingRequested || !commandsStarted || gatt == null || rxCharacteristic == null) return;
        if (commandIndex >= commandProfile.size()) {
            broadcastStatus("記錄中：套用原廠相容算法並保存逐點資料", "已連線／記錄中");
            return;
        }
        writeBytes(commandProfile.get(commandIndex++));
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                sendNextCommand();
            }
        }, 70L);
    }

    private void writeBytes(byte[] bytes) {
        if (gatt == null || rxCharacteristic == null || bytes == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeCharacteristic(rxCharacteristic, bytes,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            } else {
                rxCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                rxCharacteristic.setValue(bytes);
                gatt.writeCharacteristic(rxCharacteristic);
            }
        } catch (SecurityException error) {
            broadcastStatus("寫入感測器命令失敗：" + error.getMessage(), connectionLabel());
        }
    }

    private void findCharacteristics(BluetoothGatt callbackGatt) {
        rxCharacteristic = null;
        txCharacteristic = null;
        rawNotifyCharacteristic = null;
        BluetoothGattService nusService = callbackGatt.getService(SERVICE_UUID);
        int notifyMask = BluetoothGattCharacteristic.PROPERTY_NOTIFY
                | BluetoothGattCharacteristic.PROPERTY_INDICATE;
        for (BluetoothGattService service : callbackGatt.getServices()) {
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                if (RX_UUID.equals(characteristic.getUuid())) rxCharacteristic = characteristic;
                if (TX_UUID.equals(characteristic.getUuid())) txCharacteristic = characteristic;
                if (RAW_NOTIFY_UUID.equals(characteristic.getUuid())) {
                    rawNotifyCharacteristic = characteristic;
                }
            }
        }
        if (rawNotifyCharacteristic == null && nusService != null) {
            for (BluetoothGattCharacteristic characteristic : nusService.getCharacteristics()) {
                if (!characteristic.getUuid().equals(TX_UUID)
                        && (characteristic.getProperties() & notifyMask) != 0) {
                    rawNotifyCharacteristic = characteristic;
                    break;
                }
            }
        }
    }

    private boolean looksLikeSensor(ScanResult result) {
        BluetoothDevice device = result.getDevice();
        String name = safeName(device).toLowerCase(java.util.Locale.US);
        String address = device.getAddress();
        String normalized = address == null ? "" : address.replace('_', ':').toLowerCase(java.util.Locale.US);
        if (name.contains("poin") || name.contains("point") || normalized.startsWith("d4:7e:7e")) {
            return true;
        }
        ScanRecord record = result.getScanRecord();
        if (record != null && record.getServiceUuids() != null) {
            for (ParcelUuid uuid : record.getServiceUuids()) {
                if (SERVICE_UUID.equals(uuid.getUuid())) return true;
            }
        }
        return record != null && record.getServiceData(new ParcelUuid(SERVICE_UUID)) != null;
    }

    private void stopScan() {
        if (!scanning || scanner == null) return;
        try {
            scanner.stopScan(scanCallback);
        } catch (SecurityException ignored) {
            // Scan already gone.
        }
        scanning = false;
    }

    private boolean hasBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void broadcastRaw(byte[] packet) {
        if (packet == null || packet.length == 0) return;
        Intent intent = new Intent(RAW_ACTION);
        intent.setPackage(getPackageName());
        intent.putExtra("packet", packet);
        sendBroadcast(intent);
    }

    private void broadcastStatus(String status, String connection) {
        Intent intent = new Intent(STATUS_ACTION);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_STATUS, status);
        intent.putExtra(EXTRA_CONNECTION, connection);
        sendBroadcast(intent);
    }

    private String uuidOf(BluetoothGattCharacteristic characteristic) {
        return characteristic == null ? "-" : characteristic.getUuid().toString();
    }

    private String hexPreview(byte[] bytes) {
        StringBuilder value = new StringBuilder();
        int count = Math.min(bytes.length, 8);
        for (int index = 0; index < count; index++) {
            if (index > 0) value.append(' ');
            value.append(String.format(Locale.US, "%02x", bytes[index] & 0xff));
        }
        return value.toString();
    }

    private String connectionLabel() {
        return gatt == null ? "未連線" : "已連線";
    }

    private String safeName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name == null || name.length() == 0 ? device.getAddress() : name;
        } catch (SecurityException error) {
            return "BLE 裝置";
        }
    }

    private static ArrayList<byte[]> buildCommandProfile() {
        ArrayList<byte[]> commands = new ArrayList<>();
        commands.add(new byte[]{0x01, 0x00});
        commands.add(new byte[]{0x02, 0x01});
        commands.add(new byte[]{0x06, 0x01});
        int[][] profile = new int[][]{
                {0x05, 0x01, 0x04}, {0x03, 0x03}, {0x03, 0x00},
                {0x05, 0x01, 0x06}, {0x05, 0x01, 0x04}, {0x03, 0x03},
                {0x03, 0x00}, {0x05, 0x01, 0x06}, {0x05, 0x01, 0x04},
                {0x03, 0x00}, {0x05, 0x01, 0x06}, {0x05, 0x01, 0x08},
                {0x04, 0x03, 0x01}, {0x04, 0x01, 0x01}, {0x04, 0x01, 0x06},
                {0x04, 0x01, 0x07}, {0x04, 0x01, 0x04}, {0x04, 0x01, 0x05},
                {0x04, 0x01, 0x02}, {0x04, 0x01, 0x03}, {0x04, 0x01, 0x08},
                {0x05, 0x01, 0x06}, {0x05, 0x01, 0x04}, {0x03, 0x03}
        };
        for (int[] command : profile) {
            byte[] bytes = new byte[command.length];
            for (int index = 0; index < command.length; index++) bytes[index] = (byte) command[index];
            commands.add(bytes);
        }
        return commands;
    }
}
