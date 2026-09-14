package com.treepolo.pointgo.clone;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Visitor-first entry point for the private clone.  The official Flutter
 * screen is still available through the explicit login button; local sensor
 * functions never require an account.
 */
public final class CloneLauncherActivity extends Activity {
    private static final int REQUEST_BLUETOOTH = 7101;
    private static final int BG = Color.rgb(18, 20, 24);
    private static final int FG = Color.rgb(245, 247, 250);
    private static final int MUTED = Color.rgb(177, 184, 196);
    private static final int ACCENT = Color.rgb(52, 152, 219);
    private static final int PURPLE = Color.rgb(177, 119, 224);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setTitle("Poin+T GO 私人分析副本");

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(26), dp(22), dp(28));
        root.setBackgroundColor(BG);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -1));

        TextView title = text("Poin+T GO 私人分析副本", 25, FG);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, lp(-1, -2, 0, 0, 0, dp(8)));
        TextView visitor = text("訪客模式已啟用 · 啟動即自動連線", 16, PURPLE);
        visitor.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(visitor, lp(-1, -2, 0, 0, 0, dp(4)));
        TextView subtitle = text(
                "本機量測、六面校正、逐點圖表、動作次數、1RM、跳躍／反向跳與匯出均可直接使用。\n"
                        + "登入只在需要官方帳號、同步或雲端歷史時使用。",
                14, MUTED);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitle.setLineSpacing(0f, 1.25f);
        root.addView(subtitle, lp(-1, -2, 0, 0, 0, dp(22)));

        Button official = button("原廠相容功能（訪客模式）", PURPLE);
        official.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openAnalyzer(true, false);
            }
        });
        root.addView(official, lp(-1, dp(58), 0, 0, 0, dp(10)));

        Button analyze = button("自由分析（不需選模式）", ACCENT);
        analyze.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openAnalyzer(false, false);
            }
        });
        root.addView(analyze, lp(-1, dp(58), 0, 0, 0, dp(10)));

        Button calibrate = button("直接進入校正精靈", Color.rgb(86, 113, 77));
        calibrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openAnalyzer(true, true);
            }
        });
        root.addView(calibrate, lp(-1, dp(52), 0, 0, 0, dp(18)));

        Button login = button("登入官方帳戶（可選）", Color.rgb(72, 82, 98));
        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openVendorLogin();
            }
        });
        root.addView(login, lp(-1, dp(52), 0, 0, 0, dp(20)));

        TextView note = text(
                "分析模組：通用連續資料、甩球／投擲、角運動、重量訓練／VBT、1RM、跳躍、反向跳／CMJ。\n"
                        + "每個 session 同時保留 raw、校正後、全域加速度、全域速度、角速度、角加速度、"
                        + "四元數、事件、次數與算法版本。",
                13, MUTED);
        note.setLineSpacing(0f, 1.25f);
        root.addView(note, lp(-1, -2, 0, 0, 0, 0));
        TextView build = text("私人測試副本 · 不取代官方 App", 12, Color.rgb(120, 128, 142));
        build.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(build, lp(-1, -2, 0, dp(26), 0, 0));
        setContentView(scroll);
        ensureSensorConnection();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ensureSensorConnection();
    }

    private void ensureSensorConnection() {
        if (Build.VERSION.SDK_INT >= 31) {
            boolean scan = checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED;
            boolean connect = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
            if (!scan || !connect) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH);
                return;
            }
        } else if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_BLUETOOTH);
            return;
        }
        SensorConnectionService.ensureStarted(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != REQUEST_BLUETOOTH) return;
        for (int result : results) {
            if (result != PackageManager.PERMISSION_GRANTED) return;
        }
        SensorConnectionService.ensureStarted(this);
    }

    private void openAnalyzer(boolean officialMode, boolean openCalibration) {
        Intent intent = new Intent(this, MotionAnalyzerActivityV2.class);
        intent.putExtra("officialMode", officialMode);
        intent.putExtra("openCalibration", openCalibration);
        startActivity(intent);
    }

    private void openVendorLogin() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(getPackageName(),
                "kr.piehealthcare.point.sensor.MainActivity"));
        try {
            startActivity(intent);
        } catch (RuntimeException error) {
            android.widget.Toast.makeText(this, "登入頁無法啟動：" + error.getMessage(),
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private Button button(String label, int color) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15f);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackgroundColor(color);
        return button;
    }

    private TextView text(String value, int size, int color) {
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
