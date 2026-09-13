package com.treepolo.pointgo.clone;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
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
 * Full offline visitor workbench.  It keeps the vendor NUS bridge used by
 * the first clone, then sends every decoded frame through VendorMotionEngine
 * so the UI and exports expose calibrated/global values instead of only raw
 * peaks.
 */
public final class MotionAnalyzerActivityV2 extends Activity {
    private static final String TAG = "PoinTGoClone";
    private static final String RAW_ACTION = "com.treepolo.pointgo.clone.RAW_PACKET";
    private static final String PROFILE_PREFS = "pointgo.vendor.motion.profile";
    private static final UUID SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID RX_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID TX_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final String LAST_KNOWN_ADDRESS = "D4:7E:7E:48:74:44";
    private static final int REQUEST_BLUETOOTH = 7102;
    private static final int BG = Color.rgb(18, 20, 24);
    private static final int PANEL = Color.rgb(29, 33, 40);
    private static final int FG = Color.rgb(245, 247, 250);
    private static final int MUTED = Color.rgb(177, 184, 196);
    private static final int ACCENT = Color.rgb(52, 152, 219);
    private static final int CYAN = Color.rgb(57, 207, 220);
    private static final int ORANGE = Color.rgb(255, 170, 70);
    private static final int GREEN = Color.rgb(78, 170, 111);
    private static final int PURPLE = Color.rgb(177, 119, 224);

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object sampleLock = new Object();
    private final ArrayList<VendorMotionEngine.DerivedSample> samples = new ArrayList<>();
    private final ArrayList<VendorMotionEngine.RawSample> rawSamples = new ArrayList<>();
    private final ArrayList<byte[]> commandProfile = buildCommandProfile();

    private VendorMotionEngine motionEngine;
    private VendorMotionEngine.Profile profile;
    private VendorMotionEngine.Module selectedModule = VendorMotionEngine.Module.GENERAL;
    private VendorMotionEngine.AnalysisResult analysisResult;
    private VendorMotionEngine.CalibrationSession calibrationSession;

    private TextView statusView;
    private TextView connectionView;
    private TextView countView;
    private TextView peakView;
    private TextView profileView;
    private TextView analysisView;
    private TextView eventView;
    private Spinner moduleSpinner;
    private EditText bodyMassField;
    private EditText loadField;
    private AnalyzerGraphView graphView;
    private Button startButton;
    private Button stopButton;
    private Button exportButton;
    private AlertDialog calibrationDialog;
    private TextView calibrationDialogStatus;

    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic rxCharacteristic;
    private BluetoothGattCharacteristic txCharacteristic;
    private BluetoothLeScanner scanner;
    private boolean scanning;
    private boolean recording;
    private boolean commandsStarted;
    private boolean ownsGatt;
    private boolean directFallbackAttempted;
    private int connectionAttempts;
    private int commandIndex;
    private int packetCount;
    private long receivedByteCount;
    private int decodedPacketCount;
    private int decodedSampleCount;
    private int ignoredPacketCount;
    private int malformedPacketCount;
    private int timestampGapCount;
    private int timestampRegressionCount;
    private long largestGapMillis;
    private long firstTimestampMillis = Long.MIN_VALUE;
    private long lastTimestampMillis = Long.MIN_VALUE;
    private long lastExpandedTimestampMillis = Long.MIN_VALUE;
    private long timestampWraps;
    private double peakLinear;
    private double peakLinearSpeed;
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
            if (!recording || gatt != null || !looksLikeSensor(result)) return;
            BluetoothDevice device = result.getDevice();
            connectionAttempts++;
            stopScan();
            setStatus("找到感測器，正在連線…");
            try {
                ownsGatt = true;
                gatt = device.connectGatt(MotionAnalyzerActivityV2.this, false,
                        gattCallback, BluetoothDevice.TRANSPORT_LE);
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
                }, 220L);            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                commandsStarted = false;
                gatt = null;
                rxCharacteristic = null;
                txCharacteristic = null;
                postStatus("感測器已斷線（" + status + "）", "未連線");
                if (recording && connectionAttempts < 3) {
                    main.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (recording && gatt == null) beginScan();
                        }
                    }, 1400L);
                }
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
        public void onDescriptorWrite(BluetoothGatt callbackGatt,
                                      BluetoothGattDescriptor descriptor, int status) {
            if (!CCCD_UUID.equals(descriptor.getUuid())) return;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                setStatus("資料通知已啟用，準備自由記錄…");
            } else {
                setStatus("資料通知啟用失敗（" + status + "）");
            }
            beginCommandProfile();
        }

        @Override
        public void onMtuChanged(BluetoothGatt callbackGatt, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) setStatus("MTU " + mtu + "，開始自由記錄…");
            beginCommandProfile();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt callbackGatt,
                                             BluetoothGattCharacteristic characteristic) {
            byte[] value = characteristic.getValue();
            if (value != null) ingestOnMain(value);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt callbackGatt,
                                             BluetoothGattCharacteristic characteristic,
                                             byte[] value) {
            if (value != null) ingestOnMain(value);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        profile = VendorMotionEngine.Profile.load(
                getSharedPreferences(PROFILE_PREFS, MODE_PRIVATE));
        motionEngine = new VendorMotionEngine(profile);
        final boolean officialMode = getIntent().getBooleanExtra("officialMode", false);
        final boolean openCalibration = getIntent().getBooleanExtra("openCalibration", false);
        setTitle(officialMode ? "原廠相容功能（訪客模式）" : "自由動作分析");

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(16), dp(14), dp(22));
        root.setBackgroundColor(BG);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -1));

        TextView title = label(officialMode ? "原廠相容功能（訪客模式）" : "自由動作分析", 24, FG);
        root.addView(title, lp(-1, -2, 0, 0, 0, dp(4)));
        TextView subtitle = label(
                officialMode
                        ? "未登入也能使用本機量測、校正、分析與匯出；帳號功能仍可登入"
                        : "不需選擇運動模式 · 使用原廠 NUS 資料通道 · 完整保存逐點資料",
                13, MUTED);
        root.addView(subtitle, lp(-1, -2, 0, 0, 0, dp(10)));

        connectionView = label("未連線", 14, MUTED);
        root.addView(connectionView, lp(-1, -2, 0, 0, 0, dp(3)));
        statusView = label("按「開始記錄」即可掃描並啟動感測器", 14, MUTED);
        root.addView(statusView, lp(-1, -2, 0, 0, 0, dp(9)));

        profileView = label("校正 profile：" + profile.describe(), 12, MUTED);
        profileView.setLineSpacing(0f, 1.2f);
        root.addView(profileView, lp(-1, -2, 0, 0, 0, dp(7)));

        LinearLayout profileActions = new LinearLayout(this);
        profileActions.setOrientation(LinearLayout.HORIZONTAL);
        Button calibrateButton = actionButton("校正與算法", PURPLE);
        calibrateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showCalibrationDialog();
            }
        });
        Button loginButton = actionButton("登入官方帳戶（可選）", Color.rgb(72, 82, 98));
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openVendorLogin();
            }
        });
        profileActions.addView(calibrateButton, rowLp(0, 1f, dp(5)));
        profileActions.addView(loginButton, rowLp(0, 1f, 0));
        root.addView(profileActions, lp(-1, dp(50), 0, 0, 0, dp(9)));

        LinearLayout moduleRow = new LinearLayout(this);
        moduleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView moduleLabel = label("分析模組", 13, MUTED);
        moduleRow.addView(moduleLabel, new LinearLayout.LayoutParams(dp(74), -2));
        moduleSpinner = new Spinner(this);
        ArrayList<String> moduleLabels = new ArrayList<>();
        for (VendorMotionEngine.Module module : VendorMotionEngine.Module.values()) {
            moduleLabels.add(module.getLabel());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, moduleLabels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        moduleSpinner.setAdapter(adapter);
        moduleSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                       int position, long id) {
                selectedModule = VendorMotionEngine.Module.values()[position];
                recalculateAnalysis(true);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                selectedModule = VendorMotionEngine.Module.GENERAL;
            }
        });
        moduleRow.addView(moduleSpinner, new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(moduleRow, lp(-1, dp(50), 0, 0, 0, dp(4)));

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView massLabel = label("體重 kg", 12, MUTED);
        inputRow.addView(massLabel, new LinearLayout.LayoutParams(dp(56), -2));
        bodyMassField = numberField("75");
        inputRow.addView(bodyMassField, rowLp(0, 1f, dp(5)));
        TextView loadLabel = label("負荷 kg", 12, MUTED);
        inputRow.addView(loadLabel, new LinearLayout.LayoutParams(dp(56), -2));
        loadField = numberField("20");
        inputRow.addView(loadField, rowLp(0, 1f, 0));
        root.addView(inputRow, lp(-1, dp(50), 0, 0, 0, dp(7)));


        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        startButton = actionButton("開始記錄", ACCENT);
        stopButton = actionButton("停止", Color.rgb(106, 75, 75));
        exportButton = actionButton("匯出 CSV／JSON", GREEN);
        stopButton.setEnabled(false);
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
        root.addView(actions, lp(-1, dp(54), 0, 0, 0, dp(9)));

        graphView = new AnalyzerGraphView(this);
        root.addView(graphView, lp(-1, dp(730), 0, 0, 0, dp(10)));

        countView = label("封包 0 · 樣本 0", 14, FG);
        root.addView(countView, lp(-1, -2, 0, 0, 0, dp(4)));
        peakView = label("線性加速度峰值 0.000 m/s² · 線性速度峰值 0.000 m/s\n"
                + "角速度峰值 0.000 rad/s · 角加速度峰值 0.000 rad/s²", 13, FG);
        peakView.setLineSpacing(0f, 1.2f);
        root.addView(peakView, lp(-1, -2, 0, 0, 0, dp(8)));
        analysisView = label("逐點分析：尚未有資料", 13, FG);
        analysisView.setLineSpacing(0f, 1.16f);
        root.addView(analysisView, lp(-1, -2, 0, 0, 0, dp(8)));
        eventView = label("事件：尚未有資料", 12, MUTED);
        eventView.setLineSpacing(0f, 1.16f);
        root.addView(eventView, lp(-1, -2, 0, 0, 0, dp(10)));



        Button backToLauncher = actionButton("返回訪客工作台", Color.rgb(70, 76, 88));
        backToLauncher.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        root.addView(backToLauncher, lp(-1, dp(50), 0, 0, 0, 0));

        IntentFilter filter = new IntentFilter(RAW_ACTION);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(rawReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(rawReceiver, filter);
        }
        setContentView(scroll);
        if (openCalibration) {
            main.postDelayed(new Runnable() {
                @Override
                public void run() {
                    showCalibrationDialog();
                }
            }, 350L);
        }
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(rawReceiver);
        } catch (IllegalArgumentException ignored) {
            // Activity was not registered.
        }
        stopScan();
        if (ownsGatt && gatt != null) {
            try {
                gatt.close();
            } catch (RuntimeException ignored) {
                // Bluetooth stack already closed.
            }
        }
        super.onDestroy();
    }

    private void startRecording() {
        if (recording) return;
        if (!hasBluetoothPermission()) {
            requestBluetoothPermission();
            return;
        }
        resetSession();
        recording = true;
        commandsStarted = false;
        directFallbackAttempted = false;
        connectionAttempts = 0;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        setStatus("準備記錄：不需要先選模式…");
        if (!reuseExistingGatt()) beginScan();
    }

    private void stopRecording() {
        if (!recording) return;
        recording = false;
        commandsStarted = false;
        stopScan();
        writeBytes(new byte[]{0x03, 0x03});
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        setStatus("已停止；可在上方切換模組、回放圖表或匯出完整資料");
        recalculateAnalysis(true);
        updateUi(true);
    }

    private void requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH);
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_BLUETOOTH);
        }
    }

    private boolean hasBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_BLUETOOTH) return;
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
                BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
                if (manager == null || manager.getConnectionState(device, BluetoothProfile.GATT)
                        != BluetoothProfile.STATE_CONNECTED) continue;
                gatt = candidateGatt;
                ownsGatt = false;
                findCharacteristics(candidateGatt);
                if (txCharacteristic == null || rxCharacteristic == null) return false;
                try {
                    candidateGatt.setCharacteristicNotification(txCharacteristic, true);
                } catch (SecurityException ignored) {
                    // The command write reports the concrete error.
                }
                connectionView.setText("沿用原廠連線 · " + safeName(device));
                main.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        beginCommandProfile();
                    }
                }, 250L);
                setStatus("沿用原廠連線，開始記錄…");
                return true;
            }
        } catch (Throwable error) {
            Log.d(TAG, "reuse vendor GATT unavailable: " + error.getMessage());
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
            scanner.startScan(new ArrayList<android.bluetooth.le.ScanFilter>(),
                    new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                    scanCallback);
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
            connectionAttempts++;
            ownsGatt = true;
            gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
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
        String normalized = address == null ? "" : address.replace('_', ':').toLowerCase(Locale.US);
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

    private void findCharacteristics(BluetoothGatt callbackGatt) {
        rxCharacteristic = null;
        txCharacteristic = null;
        for (BluetoothGattService service : callbackGatt.getServices()) {
            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                if (RX_UUID.equals(characteristic.getUuid())) rxCharacteristic = characteristic;
                if (TX_UUID.equals(characteristic.getUuid())) txCharacteristic = characteristic;
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
            setStatus("記錄中：等待原始資料並套用原廠相容算法…");
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
        receivedByteCount += packet.length;
        if ((packet[0] & 0xff) != 0x04) {
            ignoredPacketCount++;
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
            decodedPacketCount++;
            decodedSampleCount += decoded;
        } else {
            malformedPacketCount++;
        }
        updateUi(false);
    }
    private int decodeCapturedPacket(byte[] packet) {
        int[] recordA = new int[13];
        int[] recordB = new int[13];
        for (int field = 0; field < 13; field++) {
            recordA[field] = i16(packet, 1 + field * 4);
            recordB[field] = i16(packet, 3 + field * 4);
        }
        addDecodedSample(recordA, u32(packet, 53));
        addDecodedSample(recordB, u32(packet, 57));
        return 2;
    }

    private int decodeAotPacket(byte[] packet, int count) {
        long baseMicros = u32(packet, 4);
        for (int index = 0; index < count; index++) {
            int offset = 8 + index * 12;
            int[] fields = new int[6];
            for (int field = 0; field < 6; field++) fields[field] = i16(packet, offset + field * 2);
            addAotSample(fields, baseMicros + index * 16_667L);
        }
        return count;
    }

    private void addDecodedSample(int[] fields, long timestampMillis) {
        double accScale = 9.80665 * 4.0 / 32768.0;
        double gyroScale = (2000.0 / 32768.0) * Math.PI / 180.0;
        addSample(timestampMillis, fields[0] * accScale, fields[1] * accScale,
                fields[2] * accScale, fields[3] * gyroScale, fields[4] * gyroScale,
                fields[5] * gyroScale);
    }

    private void addAotSample(int[] fields, long timestampMicros) {
        double accScale = 0.0047884033203125;
        double gyroScale = (2000.0 / 32768.0) * Math.PI / 180.0;
        addSample(timestampMicros / 1000L, fields[0] * accScale, fields[1] * accScale,
                fields[2] * accScale, fields[3] * gyroScale, fields[4] * gyroScale,
                fields[5] * gyroScale);
    }

    private void addSample(long rawTimestampMillis, double ax, double ay, double az,
                           double gx, double gy, double gz) {
        long timestamp = rawTimestampMillis & 0xffff_ffffL;
        long previousExpanded = lastExpandedTimestampMillis;
        if (lastTimestampMillis != Long.MIN_VALUE && timestamp < lastTimestampMillis
                && lastTimestampMillis - timestamp > 0x8000_0000L) {
            timestampWraps++;
        }
        long expanded = timestamp + timestampWraps * 0x1_0000_0000L;
        if (previousExpanded != Long.MIN_VALUE) {
            long deltaMillis = expanded - previousExpanded;
            if (deltaMillis < 0L) {
                timestampRegressionCount++;
            } else {
                if (deltaMillis > 40L) timestampGapCount++;
                largestGapMillis = Math.max(largestGapMillis, deltaMillis);
            }
        }
        if (firstTimestampMillis == Long.MIN_VALUE) firstTimestampMillis = expanded;
        VendorMotionEngine.RawSample raw = new VendorMotionEngine.RawSample(
                expanded, ax, ay, az, gx, gy, gz);
        VendorMotionEngine.DerivedSample derived = motionEngine.process(raw);
        synchronized (sampleLock) {
            rawSamples.add(raw);
            samples.add(derived);
        }
        peakLinear = Math.max(peakLinear, derived.linearMagnitude);
        peakLinearSpeed = Math.max(peakLinearSpeed, derived.linearSpeed);
        peakAngularVelocity = Math.max(peakAngularVelocity, derived.angularVelocityMagnitude);
        peakAngularAcceleration = Math.max(peakAngularAcceleration,
                derived.angularAccelerationMagnitude);
        lastExpandedTimestampMillis = expanded;
        lastTimestampMillis = timestamp;
    }

    private void resetSession() {
        synchronized (sampleLock) {
            samples.clear();
            rawSamples.clear();
        }
        motionEngine.reset();
        packetCount = 0;
        receivedByteCount = 0L;
        decodedPacketCount = 0;
        decodedSampleCount = 0;
        ignoredPacketCount = 0;
        malformedPacketCount = 0;
        timestampGapCount = 0;
        timestampRegressionCount = 0;
        largestGapMillis = 0L;
        firstTimestampMillis = Long.MIN_VALUE;
        lastTimestampMillis = Long.MIN_VALUE;
        lastExpandedTimestampMillis = Long.MIN_VALUE;
        timestampWraps = 0L;
        peakLinear = 0.0;
        peakLinearSpeed = 0.0;
        peakAngularVelocity = 0.0;
        peakAngularAcceleration = 0.0;
        analysisResult = null;
        updateUi(true);
    }

    private void updateUi(boolean force) {
        long now = System.nanoTime();
        if (!force && now - lastUiUpdateNanos < 100_000_000L) return;
        lastUiUpdateNanos = now;
        countView.setText(String.format(Locale.US,
                "封包 %d · 解碼 %d · 樣本 %d\n資料品質：忽略 %d · 格式異常 %d · 大間隔 %d（最大 %d ms） · 回退 %d",
                packetCount, decodedPacketCount, decodedSampleCount, ignoredPacketCount,
                malformedPacketCount, timestampGapCount, largestGapMillis, timestampRegressionCount));
        peakView.setText(String.format(Locale.US,
                "線性加速度峰值 %.3f m/s² · 線性速度峰值 %.3f m/s\n"
                        + "角速度峰值 %.3f rad/s · 角加速度峰值 %.3f rad/s²",
                peakLinear, peakLinearSpeed, peakAngularVelocity, peakAngularAcceleration));
        recalculateAnalysis(false);
        graphView.invalidate();
    }

    private void recalculateAnalysis(boolean force) {
        if (!force && samples.size() < 2) return;
        ArrayList<VendorMotionEngine.DerivedSample> copy;
        synchronized (sampleLock) {
            copy = new ArrayList<>(samples);
        }
        if (copy.size() < 2) {
            analysisView.setText("逐點分析：尚未有足夠資料");
            eventView.setText("事件：尚未有資料");
            analysisResult = null;
            return;
        }
        analysisResult = VendorMotionEngine.analyze(copy, selectedModule,
                parseNumber(bodyMassField, 75.0), parseNumber(loadField, 20.0));
        analysisView.setText(analysisResult.summary());
        StringBuilder events = new StringBuilder("事件／次數：");
        if (analysisResult.events.isEmpty()) {
            events.append("目前沒有符合門檻的事件（可切換模組或持續記錄）");
        } else {
            int shown = Math.min(8, analysisResult.events.size());
            for (int index = 0; index < shown; index++) {
                VendorMotionEngine.Event event = analysisResult.events.get(index);
                events.append("\n").append(index + 1).append(". ")
                        .append(event.label).append(" ")
                        .append(String.format(Locale.US, "%.2f–%.2f 秒，值 %.3f",
                                event.startSeconds, event.endSeconds, event.value));
            }
            if (analysisResult.events.size() > shown) {
                events.append("\n… 共 ").append(analysisResult.events.size()).append(" 個事件");
            }
        }
        eventView.setText(events.toString());
        profileView.setText("校正 profile：" + profile.describe());
    }

    private void exportSession() {
        ArrayList<VendorMotionEngine.DerivedSample> copy;
        synchronized (sampleLock) {
            copy = new ArrayList<>(samples);
        }
        if (copy.isEmpty()) {
            Toast.makeText(this, "目前沒有可匯出的資料", Toast.LENGTH_SHORT).show();
            return;
        }
        recalculateAnalysis(true);
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

    private void writeCsv(File file, List<VendorMotionEngine.DerivedSample> values) throws Exception {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write("timestamp_ms,elapsed_s,raw_ax_mps2,raw_ay_mps2,raw_az_mps2,"
                    + "calibrated_ax_mps2,calibrated_ay_mps2,calibrated_az_mps2,"
                    + "gravity_ax_mps2,gravity_ay_mps2,gravity_az_mps2,"
                    + "linear_ax_mps2,linear_ay_mps2,linear_az_mps2,"
                    + "world_ax_mps2,world_ay_mps2,world_az_mps2,"
                    + "velocity_x_mps,velocity_y_mps,velocity_z_mps,linear_speed_mps,"
                    + "gyro_x_radps,gyro_y_radps,gyro_z_radps,"
                    + "angular_ax_radps2,angular_ay_radps2,angular_az_radps2,"
                    + "linear_acceleration_magnitude_mps2,angular_velocity_magnitude_radps,"
                    + "angular_acceleration_magnitude_radps2,quaternion_w,quaternion_x,"
                    + "quaternion_y,quaternion_z\n");
            for (VendorMotionEngine.DerivedSample sample : values) {
                writer.write(String.format(Locale.US,
                        "%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,"
                                + "%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,"
                                + "%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,"
                                + "%.6f,%.6f,%.6f,%.6f\n",
                        sample.timestampMillis, sample.elapsedSeconds,
                        sample.rawAx, sample.rawAy, sample.rawAz,
                        sample.calibratedAx, sample.calibratedAy, sample.calibratedAz,
                        sample.gravityAx, sample.gravityAy, sample.gravityAz,
                        sample.linearAx, sample.linearAy, sample.linearAz,
                        sample.worldAx, sample.worldAy, sample.worldAz,
                        sample.velocityX, sample.velocityY, sample.velocityZ, sample.linearSpeed,
                        sample.gx, sample.gy, sample.gz,
                        sample.angularAx, sample.angularAy, sample.angularAz,
                        sample.linearMagnitude, sample.angularVelocityMagnitude,
                        sample.angularAccelerationMagnitude,
                        sample.quaternionW, sample.quaternionX, sample.quaternionY,
                        sample.quaternionZ));
            }
        }
    }

    private void writeJson(File file, List<VendorMotionEngine.DerivedSample> values) throws Exception {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write("{\"source\":\"Poin+T GO private clone\",\"algorithmVersion\":\"");
            writer.write(jsonEscape(VendorMotionEngine.ALGORITHM_VERSION));
            writer.write("\",\"profile\":");
            writer.write(profile.toJson());
            writer.write(",\"module\":\"");
            writer.write(jsonEscape(selectedModule.name()));
            writer.write("\",\"analysis\":");
            writer.write(analysisResult == null ? "null" : analysisResult.toJson());
            writer.write(String.format(Locale.US,
                    ",\"quality\":{\"notificationPackets\":%d,\"decodedPackets\":%d,\"sampleCount\":%d,\"ignoredPackets\":%d,\"malformedPackets\":%d,\"timestampGapsOver40ms\":%d,\"largestGapMs\":%d,\"timestampRegressions\":%d,\"receivedBytes\":%d},\"samples\":[",
                    packetCount, decodedPacketCount, decodedSampleCount, ignoredPacketCount,
                    malformedPacketCount, timestampGapCount, largestGapMillis,
                    timestampRegressionCount, receivedByteCount));
            for (int index = 0; index < values.size(); index++) {
                if (index > 0) writer.write(",");
                VendorMotionEngine.DerivedSample sample = values.get(index);
                writer.write(String.format(Locale.US,
                        "{\"timestampMs\":%d,\"elapsedSeconds\":%.6f,"
                                + "\"rawAcceleration\":[%.6f,%.6f,%.6f],"
                                + "\"calibratedAcceleration\":[%.6f,%.6f,%.6f],"
                                + "\"gravity\":[%.6f,%.6f,%.6f],"
                                + "\"linearAcceleration\":[%.6f,%.6f,%.6f],"
                                + "\"globalAcceleration\":[%.6f,%.6f,%.6f],"
                                + "\"globalVelocity\":[%.6f,%.6f,%.6f],"
                                + "\"linearSpeed\":%.6f,"
                                + "\"angularVelocity\":[%.6f,%.6f,%.6f],"
                                + "\"angularAcceleration\":[%.6f,%.6f,%.6f],"
                                + "\"quaternion\":[%.6f,%.6f,%.6f,%.6f]}",
                        sample.timestampMillis, sample.elapsedSeconds,
                        sample.rawAx, sample.rawAy, sample.rawAz,
                        sample.calibratedAx, sample.calibratedAy, sample.calibratedAz,
                        sample.gravityAx, sample.gravityAy, sample.gravityAz,
                        sample.linearAx, sample.linearAy, sample.linearAz,
                        sample.worldAx, sample.worldAy, sample.worldAz,
                        sample.velocityX, sample.velocityY, sample.velocityZ,
                        sample.linearSpeed,
                        sample.gx, sample.gy, sample.gz,
                        sample.angularAx, sample.angularAy, sample.angularAz,
                        sample.quaternionW, sample.quaternionX, sample.quaternionY,
                        sample.quaternionZ));
            }
            writer.write("]}");
        }
    }

    private void showCalibrationDialog() {
        if (calibrationDialog != null && calibrationDialog.isShowing()) return;
        calibrationSession = new VendorMotionEngine.CalibrationSession();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(4));
        TextView instructions = label(
                "這裡保留原廠六面校正概念。先按「開始記錄」讓感測器送資料，"
                        + "再把裝置固定在平面，逐一按下各面取樣；每面取最近 1 秒資料。\n\n"
                        + "也可以先做靜止陀螺儀偏置校正。完成後 profile 會保存在本機，"
                        + "下一次啟動仍可直接使用。",
                13, MUTED);
        instructions.setLineSpacing(0f, 1.2f);
        content.addView(instructions, lp(-1, -2, 0, 0, 0, dp(8)));
        calibrationDialogStatus = label("六面狀態：尚未取樣", 12, FG);
        calibrationDialogStatus.setLineSpacing(0f, 1.15f);
        content.addView(calibrationDialogStatus, lp(-1, -2, 0, 0, 0, dp(8)));
        for (final VendorMotionEngine.Face face : VendorMotionEngine.Face.values()) {
            Button faceButton = actionButton("取樣：" + face.getLabel(), Color.rgb(62, 82, 105));
            faceButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    captureCalibrationFace(face);
                }
            });
            content.addView(faceButton, lp(-1, dp(42), 0, 0, 0, dp(4)));
        }
        Button stillButton = actionButton("以最近資料完成靜止陀螺儀校正", Color.rgb(87, 108, 76));
        stillButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                calibrateStill();
            }
        });
        content.addView(stillButton, lp(-1, dp(46), 0, dp(4), 0, dp(4)));
        Button finishButton = actionButton("完成六面校正並儲存 profile", PURPLE);
        finishButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finishSixFaceCalibration();
            }
        });
        content.addView(finishButton, lp(-1, dp(46), 0, 0, 0, 0));
        calibrationDialog = new AlertDialog.Builder(this)
                .setTitle("原廠相容校正")
                .setView(content)
                .setNegativeButton("關閉", null)
                .create();
        calibrationDialog.setOnDismissListener(dialog -> calibrationDialog = null);
        calibrationDialog.show();
    }

    private void captureCalibrationFace(VendorMotionEngine.Face face) {
        ArrayList<VendorMotionEngine.RawSample> copy;
        synchronized (sampleLock) {
            copy = new ArrayList<>(rawSamples);
        }
        int count = calibrationSession.add(face, copy);
        if (calibrationDialogStatus != null) {
            calibrationDialogStatus.setText("六面狀態：" + calibrationSession.status()
                    + "\n本次取樣 " + count + " 筆");
        }
        if (count < 8) {
            Toast.makeText(this, "資料太少，請保持該面朝上並持續記錄", Toast.LENGTH_SHORT).show();
        }
    }

    private void calibrateStill() {
        ArrayList<VendorMotionEngine.RawSample> copy;
        synchronized (sampleLock) {
            copy = new ArrayList<>(rawSamples);
        }
        int from = Math.max(0, copy.size() - 120);
        if (copy.size() - from < 8) {
            Toast.makeText(this, "請先靜止記錄至少 8 筆資料", Toast.LENGTH_SHORT).show();
            return;
        }
        profile = VendorMotionEngine.calibrateStill(profile, copy.subList(from, copy.size()));
        profile.save(getSharedPreferences(PROFILE_PREFS, MODE_PRIVATE));
        motionEngine.setProfile(profile);
        profileView.setText("校正 profile：" + profile.describe());
        Toast.makeText(this, "陀螺儀偏置已儲存", Toast.LENGTH_SHORT).show();
    }

    private void finishSixFaceCalibration() {
        if (!calibrationSession.isComplete()) {
            Toast.makeText(this, "六個面都要各取樣一次（每面至少 8 筆）", Toast.LENGTH_LONG).show();
            return;
        }
        profile = calibrationSession.finish(profile);
        profile.save(getSharedPreferences(PROFILE_PREFS, MODE_PRIVATE));
        motionEngine.setProfile(profile);
        profileView.setText("校正 profile：" + profile.describe());
        Toast.makeText(this, "六面校正完成，已儲存本機 profile", Toast.LENGTH_LONG).show();
        if (calibrationDialog != null) calibrationDialog.dismiss();
    }

    private void openVendorLogin() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(getPackageName(),
                "kr.piehealthcare.point.sensor.MainActivity"));
        try {
            startActivity(intent);
        } catch (RuntimeException error) {
            Toast.makeText(this, "登入頁無法啟動：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private double parseNumber(EditText field, double fallback) {
        if (field == null) return fallback;
        try {
            double result = Double.parseDouble(field.getText().toString().trim());
            return Double.isNaN(result) || Double.isInfinite(result) ? fallback : result;
        } catch (RuntimeException error) {
            return fallback;
        }
    }

    private EditText numberField(String value) {
        EditText field = new EditText(this);
        field.setText(value);
        field.setTextColor(FG);
        field.setTextSize(13f);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return field;
    }

    private String safeName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name == null || name.length() == 0 ? device.getAddress() : name;
        } catch (SecurityException error) {
            return "BLE 裝置";
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
        if (statusView != null) statusView.setText(value);
    }

    private void postStatus(final String status, final String connection) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (statusView != null) statusView.setText(status);
                if (connectionView != null) connectionView.setText(connection);
            }
        });
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

    private LinearLayout.LayoutParams lp(int width, int height, int left, int top,
                                         int right, int bottom) {
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

    private static int i16(byte[] bytes, int offset) {
        int raw = (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
        return (raw & 0x8000) == 0 ? raw : raw - 0x1_0000;
    }

    private static long u32(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffL) | ((bytes[offset + 1] & 0xffL) << 8)
                | ((bytes[offset + 2] & 0xffL) << 16) | ((bytes[offset + 3] & 0xffL) << 24);
    }

    private static String jsonEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
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

    private final class AnalyzerGraphView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private int cursorIndex = -1;

        AnalyzerGraphView(Context context) {
            super(context);
            setBackgroundColor(PANEL);
            setFocusable(true);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            float panelHeight = height / 4f;
            ArrayList<VendorMotionEngine.DerivedSample> copy;
            synchronized (sampleLock) {
                copy = new ArrayList<>(samples);
            }
            drawPanel(canvas, 0f, panelHeight * 0f, width, panelHeight,
                    "線性加速度（m/s²）", 0, ACCENT, copy);
            drawPanel(canvas, 0f, panelHeight * 1f, width, panelHeight,
                    "線性速度（m/s）", 1, GREEN, copy);
            drawPanel(canvas, 0f, panelHeight * 2f, width, panelHeight,
                    "角速度（rad/s）", 2, CYAN, copy);
            drawPanel(canvas, 0f, panelHeight * 3f, width, panelHeight,
                    "角加速度（rad/s²）", 3, ORANGE, copy);
        }

        private void drawPanel(Canvas canvas, float left, float top, float width, float height,
                               String title, int metric, int color,
                               ArrayList<VendorMotionEngine.DerivedSample> values) {
            float padLeft = dp(40);
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
            canvas.drawText(title + " · 全時段", left + dp(10), top + dp(15), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(Color.rgb(65, 73, 85));
            for (int line = 0; line <= 4; line++) {
                float y = chartTop + (chartBottom - chartTop) * line / 4f;
                canvas.drawLine(chartLeft, y, chartRight, y, paint);
            }
            if (values.size() < 2) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(MUTED);
                canvas.drawText("等待資料…", chartLeft + dp(10), (chartTop + chartBottom) / 2f, paint);
                return;
            }
            double max = 0.0;
            for (VendorMotionEngine.DerivedSample value : values) {
                max = Math.max(max, metricValue(value, metric));
            }
            if (max < 0.001) max = 1.0;
            drawSeries(canvas, values, chartLeft, chartTop, chartRight, chartBottom,
                    max, metric, color);
            drawEventMarkers(canvas, values, chartLeft, chartTop, chartRight, chartBottom);
            drawCursor(canvas, values, chartLeft, chartTop, chartRight, chartBottom, max, metric);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(MUTED);
            paint.setTextSize(dp(10));
            canvas.drawText(String.format(Locale.US, "%.2f", max), left + dp(4), chartTop + dp(4), paint);
            canvas.drawText("0", left + dp(20), chartBottom + dp(4), paint);
            canvas.drawText("0.00 s", chartLeft, chartBottom + dp(4), paint);
            canvas.drawText(String.format(Locale.US, "%.2f s",
                    values.get(values.size() - 1).elapsedSeconds), chartRight - dp(45),
                    chartBottom + dp(4), paint);
        }

        /** Draw a min/max envelope per pixel bucket so a long recording remains complete
         * and peaks are not lost while avoiding a huge one-point Path. */
        private void drawSeries(Canvas canvas, ArrayList<VendorMotionEngine.DerivedSample> values,
                                float left, float top, float right, float bottom,
                                double max, int metric, int color) {
            int count = values.size();
            int pixelBuckets = Math.max(1, (int) (right - left));
            int bucketCount = Math.min(count, pixelBuckets * 2);
            path.reset();
            boolean first = true;
            for (int bucket = 0; bucket < bucketCount; bucket++) {
                int from = bucket * count / bucketCount;
                int to = Math.max(from + 1, (bucket + 1) * count / bucketCount);
                double min = Double.POSITIVE_INFINITY;
                double peak = Double.NEGATIVE_INFINITY;
                for (int index = from; index < to && index < count; index++) {
                    double value = metricValue(values.get(index), metric);
                    min = Math.min(min, value);
                    peak = Math.max(peak, value);
                }
                float x = left + (right - left) * bucket / Math.max(1f, bucketCount - 1f);
                float yMin = bottom - (float) Math.min(1.0, Math.max(0.0, min / max))
                        * (bottom - top);
                float yPeak = bottom - (float) Math.min(1.0, Math.max(0.0, peak / max))
                        * (bottom - top);
                if (first) {
                    path.moveTo(x, yMin);
                    first = false;
                } else {
                    path.lineTo(x, yMin);
                }
                path.lineTo(x, yPeak);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.6f));
            paint.setColor(color);
            canvas.drawPath(path, paint);
        }

        private void drawEventMarkers(Canvas canvas,
                                      ArrayList<VendorMotionEngine.DerivedSample> values,
                                      float left, float top, float right, float bottom) {
            if (analysisResult == null || analysisResult.events.isEmpty()) return;
            paint.setStrokeWidth(dp(1f));
            paint.setStyle(Paint.Style.STROKE);
            for (VendorMotionEngine.Event event : analysisResult.events) {
                if (event.startIndex < 0 || event.startIndex >= values.size()) continue;
                float x = left + (right - left) * event.startIndex
                        / Math.max(1f, values.size() - 1f);
                paint.setColor(event.kind.contains("landing") ? ORANGE : PURPLE);
                canvas.drawLine(x, top, x, bottom, paint);
            }
        }

        private void drawCursor(Canvas canvas,
                                ArrayList<VendorMotionEngine.DerivedSample> values,
                                float left, float top, float right, float bottom,
                                double max, int metric) {
            if (cursorIndex < 0 || cursorIndex >= values.size()) return;
            VendorMotionEngine.DerivedSample sample = values.get(cursorIndex);
            float x = left + (right - left) * cursorIndex
                    / Math.max(1f, values.size() - 1f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1f));
            paint.setColor(Color.WHITE);
            canvas.drawLine(x, top, x, bottom, paint);
            double value = metricValue(sample, metric);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(dp(10));
            paint.setColor(Color.WHITE);
            String text = String.format(Locale.US, "%.2f s  %.3f", sample.elapsedSeconds, value);
            float textX = Math.min(Math.max(left, x + dp(4)), right - dp(105));
            canvas.drawText(text, textX, top + dp(12), paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN
                    || event.getAction() == MotionEvent.ACTION_MOVE) {
                ArrayList<VendorMotionEngine.DerivedSample> copy;
                synchronized (sampleLock) {
                    copy = new ArrayList<>(samples);
                }
                if (copy.size() >= 2) {
                    float left = dp(40);
                    float right = getWidth() - dp(10);
                    float ratio = (event.getX() - left) / Math.max(1f, right - left);
                    cursorIndex = Math.max(0, Math.min(copy.size() - 1,
                            Math.round(ratio * (copy.size() - 1))));
                    invalidate();
                }
                return true;
            }
            return true;
        }

        private double metricValue(VendorMotionEngine.DerivedSample sample, int metric) {
            if (metric == 0) return sample.linearMagnitude;
            if (metric == 1) return sample.linearSpeed;
            if (metric == 2) return sample.angularVelocityMagnitude;
            return sample.angularAccelerationMagnitude;
        }
    }
}
