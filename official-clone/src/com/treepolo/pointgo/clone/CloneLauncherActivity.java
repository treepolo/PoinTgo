package com.treepolo.pointgo.clone;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Small native shell added to the repacked vendor APK.
 *
 * The original Flutter MainActivity remains in the APK and is opened from
 * here, so the vendor's existing exercise/mode workflows stay available.
 * The second entry opens the new no-mode motion analyzer.
 */
public final class CloneLauncherActivity extends Activity {
    private static final int BG = Color.rgb(18, 20, 24);
    private static final int FG = Color.rgb(245, 247, 250);
    private static final int MUTED = Color.rgb(177, 184, 196);
    private static final int ACCENT = Color.rgb(52, 152, 219);

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        setTitle("Poin+T GO 分析副本");

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(28));
        root.setBackgroundColor(BG);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -1));

        TextView title = text("Poin+T GO 分析副本", 26, FG);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, lp(-1, -2, 0, 0, 0, dp(10)));

        TextView subtitle = text("保留原廠功能，加入自由動作分析", 16, MUTED);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(subtitle, lp(-1, -2, 0, 0, 0, dp(24)));

        Button analyze = button("自由分析（不需選模式）");
        analyze.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(CloneLauncherActivity.this, MotionAnalyzerActivity.class));
            }
        });
        root.addView(analyze, lp(-1, dp(58), 0, 0, 0, dp(12)));

        Button vendor = button("開啟原廠功能");
        vendor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openVendorActivity();
            }
        });
        root.addView(vendor, lp(-1, dp(58), 0, 0, 0, dp(20)));

        TextView note = text(
                "原廠功能會以原本的 Poin*T GO 畫面開啟。\n"
                        + "自由分析可直接記錄線性加速度、角速度與角加速度，"
                        + "並提供即時圖表、峰值、RMS 與 CSV／JSON 匯出。",
                14,
                MUTED);
        note.setLineSpacing(0f, 1.25f);
        root.addView(note, lp(-1, -2, 0, 0, 0, 0));

        TextView build = text("私人測試副本 · 不取代官方 App", 12, Color.rgb(120, 128, 142));
        build.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(build, lp(-1, -2, 0, dp(26), 0, 0));

        setContentView(scroll);
    }

    private void openVendorActivity() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                getPackageName(),
                "kr.piehealthcare.point.sensor.MainActivity"));
        try {
            startActivity(intent);
        } catch (RuntimeException error) {
            android.widget.Toast.makeText(
                    this,
                    "原廠畫面無法啟動：" + error.getMessage(),
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private Button button(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(16f);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackgroundColor(ACCENT);
        return button;
    }

    private TextView text(String value, int size, int color) {
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
