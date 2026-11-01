package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class AdsBlockerActivity extends Activity {
    private static final String OFFICIAL_APK =
            "https://github.com/blokadaorg/blokada/releases/download/archive/blokada4-final.apk";
    // Blokada 4 builds have used different package IDs depending on the official/F-Droid channel.
    private static final String[] BLOKADA_PACKAGES = {
            "org.blokada.origin.alarm",
            "org.blokada.fyra",
            "org.blokada.alarm"
    };

    private TextView status;
    private TextView details;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(Color.rgb(20, 21, 27));
        getWindow().setNavigationBarColor(Color.rgb(20, 21, 27));

        // V210's built-in DNS VPN and Blokada both require Android's single active VPN slot.
        // Stop the old internal engine before handing control to Blokada.
        stopService(new Intent(this, AdsFilterVpnService.class));
        getSharedPreferences("ad_filter", MODE_PRIVATE).edit()
                .putBoolean("enabled", false).apply();
        buildUi();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    private GradientDrawable bg(int color, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(18));
        if (stroke != 0) drawable.setStroke(dp(2), stroke);
        return drawable;
    }

    private android.graphics.drawable.Drawable focusBg(int normal, int focused) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},
                bg(focused, Color.rgb(255, 205, 120)));
        states.addState(new int[]{android.R.attr.state_pressed},
                bg(focused, Color.WHITE));
        states.addState(new int[]{}, bg(normal, Color.TRANSPARENT));
        return states;
    }

    private void keepFocusedVisible(View view) {
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) v.post(() -> v.requestRectangleOnScreen(
                    new android.graphics.Rect(0, 0, v.getWidth(), v.getHeight()), true));
        });
    }

    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private Button button(String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(18);
        button.setAllCaps(false);
        button.setFocusable(true);
        button.setMinHeight(dp(56));
        button.setBackground(focusBg(Color.rgb(48, 51, 60), Color.rgb(74, 79, 92)));
        button.setPadding(dp(18), dp(8), dp(18), dp(8));
        keepFocusedVisible(button);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(22, 24, 31));
        scroll.setFocusable(false);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(30), dp(24), dp(30), dp(30));
        scroll.addView(content);
        setContentView(scroll);

        TextView title = label("阻擋廣告｜Blokada 4", 30);
        title.setTypeface(null, Typeface.BOLD);
        content.addView(title, new LinearLayout.LayoutParams(-1, dp(60)));

        TextView desc = label(
                "本版本改由 Blokada 4 負責廣告與追蹤網域阻擋。請使用下方按鈕開啟已安裝的 Blokada，或下載官方 APK 安裝。",
                18);
        desc.setTextColor(Color.LTGRAY);
        desc.setGravity(Gravity.TOP);
        content.addView(desc, new LinearLayout.LayoutParams(-1, dp(82)));

        status = label("", 20);
        status.setPadding(dp(16), 0, dp(16), 0);
        status.setBackground(bg(Color.rgb(39, 42, 50), 0));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(58));
        sp.topMargin = dp(8);
        content.addView(status, sp);

        details = label("", 16);
        details.setTextColor(Color.LTGRAY);
        details.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, dp(72));
        dp.topMargin = dp(10);
        content.addView(details, dp);

        Button open = button("開啟 Blokada 4", this::openBlokada);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-1, dp(58));
        op.topMargin = dp(12);
        content.addView(open, op);

        Button install = button("下載／安裝 Blokada 4", this::downloadBlokada);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, dp(58));
        ip.topMargin = dp(10);
        content.addView(install, ip);

        TextView note = label(
                "重要：Android 同一時間只能由一個一般 VPN App 取得 VPN 連線。CleanYourTV 內建 DNS 過濾已停用，請在 Blokada 4 首次啟動時允許 VPN 連線，並在 Blokada 內按下啟動按鈕。若電視已啟用其他 VPN，也請先關閉。",
                16);
        note.setTextColor(Color.rgb(255, 205, 120));
        note.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, dp(104));
        np.topMargin = dp(16);
        content.addView(note, np);

        Button back = button("返回", this::finish);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(58));
        bp.topMargin = dp(14);
        content.addView(back, bp);

        refreshStatus();
    }

    private String installedBlokadaPackage() {
        for (String packageName : BLOKADA_PACKAGES) {
            try {
                getPackageManager().getPackageInfo(packageName, 0);
                return packageName;
            } catch (Exception ignored) {
                // Try the next known Blokada 4 distribution package.
            }
        }
        return null;
    }

    private void refreshStatus() {
        if (status == null) return;
        String packageName = installedBlokadaPackage();
        if (packageName == null) {
            status.setText("狀態：尚未偵測到 Blokada 4");
            details.setText("請先下載並安裝 Blokada 4。安裝完成後返回此頁，再選擇「開啟 Blokada 4」。");
        } else {
            status.setText("狀態：已安裝 Blokada 4");
            details.setText("套件：" + packageName +
                    "\n阻擋啟用狀態請以 Blokada 4 App 內顯示為準。");
        }
    }

    private void openBlokada() {
        String packageName = installedBlokadaPackage();
        if (packageName == null) {
            Toast.makeText(this, "尚未安裝 Blokada 4，請先下載安裝。", Toast.LENGTH_LONG).show();
            downloadBlokada();
            return;
        }
        Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch == null) {
            Toast.makeText(this, "找不到 Blokada 4 的啟動畫面。", Toast.LENGTH_LONG).show();
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(launch);
    }

    private void downloadBlokada() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(OFFICIAL_APK));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "無法開啟下載頁面：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
