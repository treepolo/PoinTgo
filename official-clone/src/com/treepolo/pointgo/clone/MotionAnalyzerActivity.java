package com.treepolo.pointgo.clone;

import android.Manifest;
import android.app.Activity;
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
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Native analysis page added to the vendor APK clone.
 *
 * It uses the same NUS UUIDs and command profile found in the vendor BLE
 * service, but does not require an exercise/mode selection. Existing vendor
 * screens remain available through CloneLauncherActivity.
 */
public final class MotionAnalyzerActivity extends Activity {
    private static final String TAG = "PoinTGoClone";
    private static final String RAW_ACTION = "com.treepolo.pointgo.clone.RAW_PACKET";
    private static final UUID SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    // Last address observed while reverse-engineering the user's Poin+T.
    // It is only a fallback after broad scanning; failures simply return to
    // the normal "not found" status.
    private static final String LAST_KNOWN_ADDRESS = "D4:7E:7E:48:74:44";
    private static final UUID RX_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID TX_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final int REQUEST_BLUETOOTH = 7101;
    private static final int BG = Color.rgb(18, 20, 24);
    private static final int PANEL = Color.rgb(29, 33, 40);
    private static final int FG = Color.rgb(245, 247, 250);
    private static final int MUTED = Color.rgb(177, 184, 196);
    private static final int ACCENT = Color.rgb(52, 152, 219);
    private static final int CYAN = Color.rgb(57, 207, 220);
    private static final int ORANGE = Color.rgb(255, 170, 70);

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayList<Sample> samples = new ArrayList<>();
    private final ArrayList<byte[]> commandProfile = buildCommandProfile();
    private final Object sampleLock = new Object();

    private TextView statusView;
    private TextView countView;
    private TextView peakView;
    private TextView connectionView;
    private AnalyzerGraphView graphView;
    private Button startButton;
    private Button stopButton;
    private Button exportButton;

    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic rxCharacteristic;
    private BluetoothGattCharacteristic txCharacteristic;
    private BluetoothLeScanner scanner;
    private boolean scanning;
    private boolean recording;
    private boolean pendingStart;
    private boolean commandsStarted;
    private boolean ownsGatt;
    private boolean directFallbackAttempted;
    private int commandIndex;
    private int packetCount;
    private int decodedSampleCount;
    private long firstTimestampMillis = Long.MIN_VALUE;
    private long lastTimestampMillis = Long.MIN_VALUE;
    private long timestampWraps;
    private double previousGx;
    private double previousGy;
    private double previousGz;
    private long previousSampleTimeMillis = Long.MIN_VALUE;
    private double peakLinear;
    private double peakAngularVelocity;
    private double peakAngularAcceleration;
    private long lastUiUpdateNanos;

    private final BroadcastReceiver rawReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!RAW_ACTION.equals(intent.getAction())) return;
            byte[] packet = intent.getByteArrayExtra("packet");
            if (packet != null) ingestOnMain(packet);
        }
    };

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (!recording || gatt != null) return;
            if (!looksLikeSensor(result)) return;
            BluetoothDevice device = result.getDevice();
            stopScan();
            setStatus("找到感測器，正在連線…");
            try {
                ownsGatt = true;
                gatt = device.connectGatt(
                        MotionAnalyzerActivity.this,
                        false,
                        gattCallback,
                        BluetoothDevice.TRANSPORT_LE);
                connectionView.setText("連線中 · " + safeName(device));
            } catch (SecurityException error) {
                setStatus("沒有藍牙連線權限：" + error.getMessage());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            scanning = false;
            setStatus("掃描失敗（" + errorCode + "），請重試");
        }
    };

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt callbackGatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                gatt = callbackGatt;
                postStatus("已連線，正在探索服務…", "已連線");
                main.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            callbackGatt.discoverServices();
                        } catch (SecurityException error) {
                            setStatus("服務探索權限不足");
                        }
                    }
                }, 220L);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                commandsStarted = false;
                postStatus("感測器已斷線（" + status + "）", "未連線");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt callbackGatt, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                setStatus("服務探索失敗（" + status + "）");
                return;
            }
            findCharacteristics(callbackGatt);
            if (txCharacteristic == null || rxCharacteristic == null) {
                setStatus("找不到 NUS 資料通道");
                return;
            }
            try {
                callbackGatt.setCharacteristicNotification(txCharacteristic, true);
                BluetoothGattDescriptor cccd = txCharacteristic.getDescriptor(CCCD_UUID);
                if (cccd != null) {
                    cccd.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    callbackGatt.writeDescriptor(cccd);
                } else {
                    beginCommandProfile();
                }
                callbackGatt.requestMtu(247);
            } catch (SecurityException error) {
                setStatus("啟用感測器通知失敗：" + error.getMessage());
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt callbackGatt, BluetoothGattDescriptor descriptor, int status) {
            if (descriptor.getUuid().equals(CCCD_UUID)) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    setStatus("資料通知已啟用，準備自由記錄…");
                } else {
                    setStatus("資料通知啟用失敗（" + status + "）");
                }
                beginCommandProfile();
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt callbackGatt, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                setStatus("MTU " + mtu + "，開始自由記錄…");
            }
            beginCommandProfile();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt callbackGatt, BluetoothGattCharacteristic characteristic) {
            byte[] value = characteristic.getValue();
            if (value != null) ingestOnMain(value);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt callbackGatt, BluetoothGattCharacteristic characteristic, byte[] value) {
            if (value != null) ingestOnMain(value);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        SensorConnectionService.ensureStarted(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setTitle("自由動作分析");

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(18), dp(14), dp(22));
        root.setBackgroundColor(BG);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -1));

        TextView title = label("自由動作分析", 25, FG);
        root.addView(title, lp(-1, -2, 0, 0, 0, dp(4)));
        TextView subtitle = label("不需選擇運動模式 · 使用原廠 NUS 資料通道", 13, MUTED);
        root.addView(subtitle, lp(-1, -2, 0, 0, 0, dp(12)));

        connectionView = label("未連線", 14, MUTED);
        root.addView(connectionView, lp(-1, -2, 0, 0, 0, dp(4)));
        statusView = label("開啟頁面即自動連線；按「開始自由記錄」才開始取樣", 14, MUTED);
        root.addView(statusView, lp(-1, -2, 0, 0, 0, dp(12)));

        graphView = new AnalyzerGraphView(this);
        root.addView(graphView, lp(-1, dp(360), 0, 0, 0, dp(12)));

        countView = label("封包 0 · 樣本 0", 14, FG);
        root.addView(countView, lp(-1, -2, 0, 0, 0, dp(4)));
        peakView = label("線性峰值 0.000 m/s²\n角速度峰值 0.000 rad/s · 角加速度峰值 0.000 rad/s²", 14, FG);
        peakView.setLineSpacing(0f, 1.2f);
        root.addView(peakView, lp(-1, -2, 0, 0, 0, dp(12)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        startButton = actionButton("開始自由記錄", ACCENT);
        stopButton = actionButton("停止", Color.rgb(106, 75, 75));
        exportButton = actionButton("匯出 CSV／JSON", Color.rgb(64, 120, 82));
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startRecording();
            }
        });
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopRecording();
            }
        });
        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                exportSession();
            }
        });
        actions.addView(startButton, rowLp(0, 1f, dp(3)));
        actions.addView(stopButton, rowLp(0, 1f, dp(3)));
        actions.addView(exportButton, rowLp(0, 1f, 0));
        root.addView(actions, lp(-1, dp(54), 0, 0, 0, dp(10)));

        Button vendor = actionButton("開啟原廠功能", Color.rgb(70, 76, 88));
        vendor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(
                        getPackageName(),
                        "kr.piehealthcare.point.sensor.MainActivity"));
                startActivity(intent);
            }
        });
        root.addView(vendor, lp(-1, dp(52), 0, 0, 0, 0));

        IntentFilter filter = new IntentFilter(RAW_ACTION);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(rawReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(rawReceiver, filter);
        }
        setContentView(scroll);
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(rawReceiver);
        } catch (IllegalArgumentException ignored) {
            // Activity was never resumed far enough to register the receiver.
        }
        stopScan();
        if (ownsGatt && gatt != null) {
            try {
                gatt.close();
            } catch (RuntimeException ignored) {
                // Bluetooth stack is already gone.
            }
        }
        super.onDestroy();
    }

    private void startRecording() {
        if (recording) return;
        if (!hasBluetoothPermission()) {
            pendingStart = true;
            requestBluetoothPermission();
            return;
        }
        resetSession();
        recording = true;
        commandsStarted = false;
        directFallbackAttempted = false;
        setStatus("準備自由記錄…");
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        if (!reuseExistingGatt()) {
            beginScan();
        }
    }

    private void stopRecording() {
        if (!recording) return;
        recording = false;
        commandsStarted = false;
        stopScan();
        writeBytes(new byte[]{0x03, 0x03});
        setStatus("已停止；可回放目前圖表或匯出資料");
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        updateUi(true);
    }

    private void requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
            }, REQUEST_BLUETOOTH);
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_BLUETOOTH);
        }
    }

    private boolean hasBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_BLUETOOTH) return;
        pendingStart = false;
        for (int result : results) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                setStatus("需要藍牙權限才能讀取感測器");
                return;
            }
        }
        startRecording();
    }

    private boolean reuseExistingGatt() {
        try {
            Class<?> managerClass = Class.forName("b3.i");
            Field mapField = managerClass.getField("d");
            Object value = mapField.get(null);
            if (!(value instanceof Map)) return false;
            for (Object candidate : ((Map<?, ?>) value).values()) {
                if (!(candidate instanceof BluetoothGatt)) continue;
                BluetoothGatt candidateGatt = (BluetoothGatt) candidate;
                BluetoothDevice device = candidateGatt.getDevice();
                BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
                if (bluetoothManager == null
                        || bluetoothManager.getConnectionState(device, BluetoothProfile.GATT)
                        != BluetoothProfile.STATE_CONNECTED) continue;
                gatt = candidateGatt;
                ownsGatt = false;
                findCharacteristics(candidateGatt);
                if (txCharacteristic == null || rxCharacteristic == null) return false;
                try {
                    candidateGatt.setCharacteristicNotification(txCharacteristic, true);
                } catch (SecurityException ignored) {
                    // The following command write will report the actual error.
                }
                connectionView.setText("沿用原廠連線 · " + safeName(device));
                main.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        beginCommandProfile();
                    }
                }, 250L);
                setStatus("沿用原廠連線，開始自由記錄…");
                return true;
            }
        } catch (Throwable error) {
            // The clone can still use its own BluetoothGatt connection.
        }
        return false;
    }

    private void beginScan() {
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            setStatus("請先開啟手機藍牙");
            return;
        }
        try {
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) {
                setStatus("手機不支援 BLE 掃描");
                return;
            }
            // Some firmware revisions omit the NUS UUID from the advertising
            // payload while waiting for a connection. Scan broadly, then apply
            // the Poin+T name/service/OUI check in looksLikeSensor().
            List<ScanFilter> filters = new ArrayList<>();
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            scanner.startScan(filters, settings, scanCallback);
            scanning = true;
            connectionView.setText("掃描感測器中…");
            main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (scanning) {
                        stopScan();
                        if (!connectKnownDevice()) {
                            setStatus("掃描逾時，請確認感測器已開機後重試");
                        }
                    }
                }
            }, 12_000L);
        } catch (SecurityException error) {
            setStatus("BLE 掃描權限不足：" + error.getMessage());
        }
    }

    private boolean connectKnownDevice() {
        if (directFallbackAttempted || gatt != null) return false;
        directFallbackAttempted = true;
        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) return false;
        try {
            BluetoothDevice device = adapter.getRemoteDevice(LAST_KNOWN_ADDRESS);
            ownsGatt = true;
            gatt = device.connectGatt(
                    MotionAnalyzerActivity.this,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE);
            connectionView.setText("嘗試直連 · " + LAST_KNOWN_ADDRESS);
            setStatus("掃描不到裝置，正在嘗試已知 Poin+T 位址…");
            return true;
        } catch (IllegalArgumentException | SecurityException error) {
            Log.d(TAG, "known-address fallback failed: " + error.getMessage());
            return false;
        }
    }

    private boolean looksLikeSensor(ScanResult result) {
        BluetoothDevice device = result.getDevice();
        String name = safeName(device).toLowerCase(Locale.US);
        String address = device.getAddress();
        String normalizedAddress = address == null
                ? ""
                : address.replace('_', ':').toLowerCase(Locale.US);
        if (name.contains("poin") || name.contains("point")
                || normalizedAddress.startsWith("d4:7e:7e")) {
            Log.d(TAG, "Poin+T candidate name=" + safeName(device) + " address=" + address);
            return true;
        }
        ScanRecord record = result.getScanRecord();
        if (record != null && record.getServiceUuids() != null) {
            for (ParcelUuid uuid : record.getServiceUuids()) {
                if (SERVICE_UUID.equals(uuid.getUuid())) return true;
            }
        }
        // The vendor filter is authoritative; accept a nameless result when
        // the platform has already matched the NUS service filter.
        return record != null && record.getServiceData(new ParcelUuid(SERVICE_UUID)) != null;
    }

    private void stopScan() {
        if (!scanning || scanner == null) return;
        try {
            scanner.stopScan(scanCallback);
        } catch (SecurityException ignored) {
            // Scan is already gone or permission was revoked.
        }
        scanning = false;
    }

    private void findCharacteristics(BluetoothGatt callbackGatt) {
        rxCharacteristic = null;
        txCharacteristic = null;
        for (BluetoothGattService service : callbackGatt.getServices()) {
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                UUID uuid = characteristic.getUuid();
                if (RX_UUID.equals(uuid)) rxCharacteristic = characteristic;
                if (TX_UUID.equals(uuid)) txCharacteristic = characteristic;
            }
        }
    }

    private void beginCommandProfile() {
        if (!recording || commandsStarted || gatt == null || rxCharacteristic == null) return;
        commandsStarted = true;
        commandIndex = 0;
        sendNextCommand();
    }

    private void sendNextCommand() {
        if (!recording || gatt == null || rxCharacteristic == null) return;
        if (commandIndex >= commandProfile.size()) {
            setStatus("自由記錄中：等待原始資料…");
            return;
        }
        byte[] command = commandProfile.get(commandIndex++);
        writeBytes(command);
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
                gatt.writeCharacteristic(
                        rxCharacteristic,
                        bytes,
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            } else {
                rxCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
                rxCharacteristic.setValue(bytes);
                gatt.writeCharacteristic(rxCharacteristic);
            }
        } catch (SecurityException error) {
            setStatus("寫入感測器命令失敗：" + error.getMessage());
        }
    }

    private void ingestOnMain(final byte[] packet) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    ingestOnMain(packet);
                }
            });
            return;
        }
        if (!recording || packet.length == 0) return;
        packetCount++;
        if ((packet[0] & 0xff) != 0x04) {
            updateUi(false);
            return;
        }
        int decoded = 0;
        if (packet.length == 61) {
            decoded = decodeCapturedPacket(packet);
        } else {
            int count = packet.length > 1 ? packet[1] & 0xff : 0;
            if (count > 0 && packet.length == 8 + count * 12) {
                decoded = decodeAotPacket(packet, count);
            }
        }
        if (decoded > 0) {
            decodedSampleCount += decoded;
            updateUi(false);
        }
    }

    private int decodeCapturedPacket(byte[] packet) {
        int[] recordA = new int[13];
        int[] recordB = new int[13];
        for (int field = 0; field < 13; field++) {
            recordA[field] = i16(packet, 1 + field * 4);
            recordB[field] = i16(packet, 3 + field * 4);
        }
        long timestampA = u32(packet, 53);
        long timestampB = u32(packet, 57);
        addDecodedSample(recordA, timestampA);
        addDecodedSample(recordB, timestampB);
        return 2;
    }

    private int decodeAotPacket(byte[] packet, int count) {
        long baseMicros = u32(packet, 4);
        for (int index = 0; index < count; index++) {
            int offset = 8 + index * 12;
            int[] fields = new int[6];
            for (int field = 0; field < fields.length; field++) {
                fields[field] = i16(packet, offset + field * 2);
            }
            addAotSample(fields, baseMicros + index * 16_667L);
        }
        return count;
    }

    private void addDecodedSample(int[] fields, long timestampMillis) {
        double accScale = 9.80665 * 4.0 / 32768.0;
        double gyroScale = (2000.0 / 32768.0) * Math.PI / 180.0;
        addSample(
                timestampMillis,
                fields[0] * accScale,
                fields[1] * accScale,
                fields[2] * accScale,
                fields[3] * gyroScale,
                fields[4] * gyroScale,
                fields[5] * gyroScale);
    }

    private void addAotSample(int[] fields, long timestampMicros) {
        double accScale = 0.0047884033203125;
        double gyroScale = (2000.0 / 32768.0) * Math.PI / 180.0;
        addSample(
                timestampMicros / 1000L,
                fields[0] * accScale,
                fields[1] * accScale,
                fields[2] * accScale,
                fields[3] * gyroScale,
                fields[4] * gyroScale,
                fields[5] * gyroScale);
    }

    private void addSample(long rawTimestampMillis, double ax, double ay, double az,
                           double gx, double gy, double gz) {
        long timestamp = rawTimestampMillis & 0xffff_ffffL;
        if (lastTimestampMillis != Long.MIN_VALUE
                && timestamp < lastTimestampMillis
                && lastTimestampMillis - timestamp > 0x8000_0000L) {
            timestampWraps++;
        }
        long expanded = timestamp + timestampWraps * 0x1_0000_0000L;
        if (firstTimestampMillis == Long.MIN_VALUE) firstTimestampMillis = expanded;
        double elapsed = (expanded - firstTimestampMillis) / 1000.0;
        double dt = previousSampleTimeMillis == Long.MIN_VALUE
                ? 1.0 / 120.0
                : (expanded - previousSampleTimeMillis) / 1000.0;
        if (dt <= 0.0 || dt > 1.0) dt = 1.0 / 120.0;
        double angularAx = previousSampleTimeMillis == Long.MIN_VALUE ? 0.0 : (gx - previousGx) / dt;
        double angularAy = previousSampleTimeMillis == Long.MIN_VALUE ? 0.0 : (gy - previousGy) / dt;
        double angularAz = previousSampleTimeMillis == Long.MIN_VALUE ? 0.0 : (gz - previousGz) / dt;
        double linearMagnitude = magnitude(ax, ay, az);
        double angularVelocityMagnitude = magnitude(gx, gy, gz);
        double angularAccelerationMagnitude = magnitude(angularAx, angularAy, angularAz);
        synchronized (sampleLock) {
            samples.add(new Sample(
                    expanded,
                    elapsed,
                    ax, ay, az,
                    gx, gy, gz,
                    angularAx, angularAy, angularAz,
                    linearMagnitude,
                    angularVelocityMagnitude,
                    angularAccelerationMagnitude));
        }
        peakLinear = Math.max(peakLinear, linearMagnitude);
        peakAngularVelocity = Math.max(peakAngularVelocity, angularVelocityMagnitude);
        peakAngularAcceleration = Math.max(peakAngularAcceleration, angularAccelerationMagnitude);
        previousGx = gx;
        previousGy = gy;
        previousGz = gz;
        previousSampleTimeMillis = expanded;
        lastTimestampMillis = timestamp;
    }

    private void resetSession() {
        synchronized (sampleLock) {
            samples.clear();
        }
        packetCount = 0;
        decodedSampleCount = 0;
        firstTimestampMillis = Long.MIN_VALUE;
        lastTimestampMillis = Long.MIN_VALUE;
        timestampWraps = 0L;
        previousSampleTimeMillis = Long.MIN_VALUE;
        peakLinear = 0.0;
        peakAngularVelocity = 0.0;
        peakAngularAcceleration = 0.0;
        updateUi(true);
    }

    private void updateUi(boolean force) {
        long now = System.nanoTime();
        if (!force && now - lastUiUpdateNanos < 100_000_000L) return;
        lastUiUpdateNanos = now;
        countView.setText("封包 " + packetCount + " · 樣本 " + decodedSampleCount);
        peakView.setText(String.format(
                Locale.US,
                "線性峰值 %.3f m/s²\n角速度峰值 %.3f rad/s · 角加速度峰值 %.3f rad/s²",
                peakLinear,
                peakAngularVelocity,
                peakAngularAcceleration));
        graphView.invalidate();
    }

    private void exportSession() {
        ArrayList<Sample> copy;
        synchronized (sampleLock) {
            copy = new ArrayList<>(samples);
        }
        if (copy.isEmpty()) {
            Toast.makeText(this, "目前沒有可匯出的資料", Toast.LENGTH_SHORT).show();
            return;
        }
        File directory = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (directory == null) directory = getFilesDir();
        if (!directory.exists() && !directory.mkdirs()) {
            Toast.makeText(this, "無法建立匯出資料夾", Toast.LENGTH_LONG).show();
            return;
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File csv = new File(directory, "pointgo-session-" + stamp + ".csv");
        File json = new File(directory, "pointgo-session-" + stamp + ".json");
        try {
            writeCsv(csv, copy);
            writeJson(json, copy);
            Toast.makeText(this, "已匯出：" + csv.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            Toast.makeText(this, "匯出失敗：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void writeCsv(File file, List<Sample> values) throws Exception {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write("timestamp_ms,elapsed_s,ax_mps2,ay_mps2,az_mps2,linear_magnitude_mps2,gx_radps,gy_radps,gz_radps,angular_velocity_magnitude_radps,angular_ax_radps2,angular_ay_radps2,angular_az_radps2,angular_acceleration_magnitude_radps2\n");
            for (Sample sample : values) {
                writer.write(String.format(
                        Locale.US,
                        "%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f\n",
                        sample.timestampMillis,
                        sample.elapsedSeconds,
                        sample.ax,
                        sample.ay,
                        sample.az,
                        sample.linearMagnitude,
                        sample.gx,
                        sample.gy,
                        sample.gz,
                        sample.angularVelocityMagnitude,
                        sample.angularAx,
                        sample.angularAy,
                        sample.angularAz,
                        sample.angularAccelerationMagnitude));
            }
        }
    }

    private void writeJson(File file, List<Sample> values) throws Exception {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write("{\"source\":\"Poin+T GO private clone\",\"samples\":[");
            for (int index = 0; index < values.size(); index++) {
                if (index > 0) writer.write(",");
                Sample sample = values.get(index);
                writer.write(String.format(
                        Locale.US,
                        "{\"timestampMs\":%d,\"elapsedSeconds\":%.6f,\"acceleration\":[%.6f,%.6f,%.6f],\"linearMagnitude\":%.6f,\"angularVelocity\":[%.6f,%.6f,%.6f],\"angularAcceleration\":[%.6f,%.6f,%.6f],\"angularAccelerationMagnitude\":%.6f}",
                        sample.timestampMillis,
                        sample.elapsedSeconds,
                        sample.ax,
                        sample.ay,
                        sample.az,
                        sample.linearMagnitude,
                        sample.gx,
                        sample.gy,
                        sample.gz,
                        sample.angularAx,
                        sample.angularAy,
                        sample.angularAz,
                        sample.angularAccelerationMagnitude));
            }
            writer.write("]}");
        }
    }

    private void setStatus(final String value) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    setStatus(value);
                }
            });
            return;
        }
        statusView.setText(value);
    }

    private void postStatus(final String status, final String connection) {
        main.post(new Runnable() {
            @Override
            public void run() {
                statusView.setText(status);
                connectionView.setText(connection);
            }
        });
    }

    private String safeName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name == null || name.length() == 0 ? device.getAddress() : name;
        } catch (SecurityException error) {
            return "BLE 裝置";
        }
    }

    private Button actionButton(String value, int color) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(13f);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackgroundColor(color);
        return button;
    }

    private TextView label(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout.LayoutParams lp(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return params;
    }

    private LinearLayout.LayoutParams rowLp(int width, float weight, int rightMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, -1, weight);
        params.setMargins(0, 0, rightMargin, 0);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static double magnitude(double x, double y, double z) {
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static int i16(byte[] bytes, int offset) {
        int raw = (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
        return (raw & 0x8000) == 0 ? raw : raw - 0x1_0000;
    }

    private static long u32(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffL)
                | ((bytes[offset + 1] & 0xffL) << 8)
                | ((bytes[offset + 2] & 0xffL) << 16)
                | ((bytes[offset + 3] & 0xffL) << 24);
    }

    private static ArrayList<byte[]> buildCommandProfile() {
        ArrayList<byte[]> commands = new ArrayList<>();
        // The vendor app sends these initialization writes before the
        // high-rate profile. They were missing from the first companion app.
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

    private final class AnalyzerGraphView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        AnalyzerGraphView(Context context) {
            super(context);
            setBackgroundColor(PANEL);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            drawPanel(canvas, 0f, 0f, width, height * 0.48f, "線性加速度合成值 (m/s²)", 0);
            drawPanel(canvas, 0f, height * 0.52f, width, height * 0.48f, "角速度／角加速度 (rad/s, rad/s²)", 1);
        }

        private void drawPanel(Canvas canvas, float left, float top, float width, float height,
                               String title, int metric) {
            float padLeft = dp(38);
            float padRight = dp(10);
            float padTop = dp(22);
            float padBottom = dp(18);
            float chartLeft = left + padLeft;
            float chartTop = top + padTop;
            float chartRight = left + width - padRight;
            float chartBottom = top + height - padBottom;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(34, 39, 47));
            canvas.drawRect(left, top, left + width, top + height, paint);
            paint.setTextSize(dp(12));
            paint.setColor(MUTED);
            canvas.drawText(title, left + dp(10), top + dp(15), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(Color.rgb(65, 73, 85));
            for (int line = 0; line <= 4; line++) {
                float y = chartTop + (chartBottom - chartTop) * line / 4f;
                canvas.drawLine(chartLeft, y, chartRight, y, paint);
            }
            ArrayList<Sample> copy;
            synchronized (sampleLock) {
                copy = new ArrayList<>(samples);
            }
            if (copy.size() < 2) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(MUTED);
                canvas.drawText("等待資料…", chartLeft + dp(10), (chartTop + chartBottom) / 2f, paint);
                return;
            }
            int start = Math.max(0, copy.size() - 600);
            double max = 0.0;
            for (int index = start; index < copy.size(); index++) {
                Sample sample = copy.get(index);
                if (metric == 0) {
                    max = Math.max(max, sample.linearMagnitude);
                } else {
                    max = Math.max(max, Math.max(sample.angularVelocityMagnitude, sample.angularAccelerationMagnitude));
                }
            }
            if (max < 0.001) max = 1.0;
            if (metric == 0) {
                drawSeries(canvas, copy, start, chartLeft, chartTop, chartRight, chartBottom, max, 0, ACCENT);
            } else {
                drawSeries(canvas, copy, start, chartLeft, chartTop, chartRight, chartBottom, max, 1, CYAN);
                drawSeries(canvas, copy, start, chartLeft, chartTop, chartRight, chartBottom, max, 2, ORANGE);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(CYAN);
                canvas.drawRect(chartRight - dp(100), top + dp(6), chartRight - dp(92), top + dp(13), paint);
                paint.setColor(MUTED);
                canvas.drawText("角速度", chartRight - dp(86), top + dp(13), paint);
                paint.setColor(ORANGE);
                canvas.drawRect(chartRight - dp(52), top + dp(6), chartRight - dp(44), top + dp(13), paint);
                paint.setColor(MUTED);
                canvas.drawText("角加速度", chartRight - dp(39), top + dp(13), paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(MUTED);
            paint.setTextSize(dp(10));
            canvas.drawText(String.format(Locale.US, "%.2f", max), left + dp(4), chartTop + dp(4), paint);
            canvas.drawText("0", left + dp(20), chartBottom + dp(4), paint);
        }

        private void drawSeries(Canvas canvas, ArrayList<Sample> values, int start,
                                float left, float top, float right, float bottom,
                                double max, int series, int color) {
            path.reset();
            int count = values.size() - start;
            for (int index = start; index < values.size(); index++) {
                Sample sample = values.get(index);
                double value;
                if (series == 0) value = sample.linearMagnitude;
                else if (series == 1) value = sample.angularVelocityMagnitude;
                else value = sample.angularAccelerationMagnitude;
                float x = left + (right - left) * (index - start) / Math.max(1f, count - 1f);
                float y = bottom - (float) Math.min(1.0, Math.max(0.0, value / max)) * (bottom - top);
                if (index == start) path.moveTo(x, y); else path.lineTo(x, y);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.6f));
            paint.setColor(color);
            canvas.drawPath(path, paint);
        }
    }

    private static final class Sample {
        final long timestampMillis;
        final double elapsedSeconds;
        final double ax;
        final double ay;
        final double az;
        final double gx;
        final double gy;
        final double gz;
        final double angularAx;
        final double angularAy;
        final double angularAz;
        final double linearMagnitude;
        final double angularVelocityMagnitude;
        final double angularAccelerationMagnitude;

        Sample(long timestampMillis, double elapsedSeconds,
               double ax, double ay, double az,
               double gx, double gy, double gz,
               double angularAx, double angularAy, double angularAz,
               double linearMagnitude, double angularVelocityMagnitude,
               double angularAccelerationMagnitude) {
            this.timestampMillis = timestampMillis;
            this.elapsedSeconds = elapsedSeconds;
            this.ax = ax;
            this.ay = ay;
            this.az = az;
            this.gx = gx;
            this.gy = gy;
            this.gz = gz;
            this.angularAx = angularAx;
            this.angularAy = angularAy;
            this.angularAz = angularAz;
            this.linearMagnitude = linearMagnitude;
            this.angularVelocityMagnitude = angularVelocityMagnitude;
            this.angularAccelerationMagnitude = angularAccelerationMagnitude;
        }
    }
}
