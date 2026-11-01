package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class AdsBlockerActivity extends Activity {
    private static final int VPN_REQUEST = 7412;
    private static final String PREFS = "ad_filter";
    private static final String BLOCK = "block_domains";
    private static final String ALLOW = "allow_domains";
    private SharedPreferences prefs;
    private LinearLayout content;
    private TextView status;
    private Button toggle;

    // Bundled starter rules. Allow rules take precedence over block rules.
    private static final String DEFAULT_BLOCK =
            "doubleclick.net\ngooglesyndication.com\ngoogleadservices.com\nadservice.google.com\n" +
            "pagead2.googlesyndication.com\napp-measurement.com\nadcolony.com\nunityads.unity3d.com\n" +
            "applovin.com\nmoatads.com\nadsrvr.org\n" +
            "googleads.g.doubleclick.net\nsecurepubads.g.doubleclick.net\npubads.g.doubleclick.net\n" +
            "av1.xdrig.com\nme.xdrig.com\nwww.xdrig.com\n*.xdrig.com\n" +
            "footprints-pa.googleapis.com\nwww.googleadservices.com\n" +
            "p-app.dhub.hinet.net\nads.aralego.com\nadsv.svc.litv.tv\nacs2.svc.litv.tv\n" +
            "*.svc.litv.tv\n";
    private static final String DEFAULT_ALLOW =
            "vid.dbokutv.com\nfirebaselogging-pa.googleapis.com\npic.dbokutv.com\nevents.ads.vungle.com\n";
    private static final String DEFAULT_RULES_VERSION = "3";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        installBundledRules();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(Color.rgb(20, 21, 27));
        getWindow().setNavigationBarColor(Color.rgb(20, 21, 27));
        buildUi();
    }

    private void installBundledRules() {
        if (DEFAULT_RULES_VERSION.equals(prefs.getString("bundled_rules_version", ""))) return;
        LinkedHashSet<String> block = new LinkedHashSet<>(rules(BLOCK));
        // These shared Google API/video hosts also carry playback manifests, player APIs and media.
        // DNS-only filtering cannot distinguish ad URLs from video URLs on the same host, unlike uBlock.
        // Remove the broad rules from previously saved defaults to prevent breaking video playback.
        block.remove("googlevideo.com");
        block.remove("rr2---sn-un57sne7.googlevideo.com");
        block.remove("eligibility-panelresearch.googlevideo.com");
        block.remove("www.googlevideo.com");
        block.remove("googleapis.com");
        block.remove("www.googleapis.com");
        block.remove("youtubei.googleapis.com");
        LinkedHashSet<String> allow = new LinkedHashSet<>(rules(ALLOW));
        for (String item : DEFAULT_BLOCK.split("\\s+")) {
            String domain = normalize(item);
            if (!domain.isEmpty()) block.add(domain);
        }
        for (String item : DEFAULT_ALLOW.split("\\s+")) {
            String domain = normalize(item);
            if (!domain.isEmpty()) allow.add(domain);
        }
        saveRules(BLOCK, block);
        saveRules(ALLOW, allow);
        prefs.edit().putString("bundled_rules_version", DEFAULT_RULES_VERSION).apply();
    }

    @Override protected void onResume() { super.onResume(); if (content != null) refresh(); }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable bg(int color, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(18));
        if (stroke != 0) d.setStroke(dp(2), stroke);
        return d;
    }
    private TextView label(String text, int size) {
        TextView v = new TextView(this); v.setText(text); v.setTextColor(Color.WHITE);
        v.setTextSize(size); v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    private Button button(String text, Runnable action) {
        Button b = new Button(this); b.setText(text); b.setTextColor(Color.WHITE); b.setTextSize(18);
        b.setAllCaps(false); b.setFocusable(true); b.setMinHeight(dp(54));
        b.setBackground(bg(Color.rgb(48, 51, 60), Color.rgb(105, 108, 118)));
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(22, 24, 31));
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(28), dp(22), dp(28), dp(30));
        scroll.addView(content);
        setContentView(scroll);
        TextView title = label("阻擋廣告", 30); title.setTypeface(null, Typeface.BOLD);
        content.addView(title, new LinearLayout.LayoutParams(-1, dp(58)));
        TextView desc = label("透過本機 DNS 過濾常見廣告網域。首次啟用時，Android 會詢問是否允許 VPN 連線。", 17);
        desc.setTextColor(Color.LTGRAY); desc.setGravity(Gravity.TOP);
        content.addView(desc, new LinearLayout.LayoutParams(-1, dp(68)));
        status = label("", 19); status.setPadding(dp(16), 0, dp(16), 0); status.setBackground(bg(Color.rgb(39,42,50), 0));
        content.addView(status, new LinearLayout.LayoutParams(-1, dp(58)));
        toggle = button("", this::toggleFilter);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, dp(58)); tp.topMargin = dp(12);
        content.addView(toggle, tp);
        TextView note = label("注意：YouTube 影片廣告、加密 DNS／DoH，以及與影片共用網域的廣告，可能無法由 DNS 規則攔截。", 15);
        note.setTextColor(Color.rgb(255, 205, 120)); note.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, dp(58)); np.topMargin = dp(10);
        content.addView(note, np);
        addSection("封鎖規則", "封鎖符合網域及其子網域的 DNS 請求。", BLOCK);
        addSection("允許規則", "允許清單優先於封鎖清單，可用來解除誤擋。", ALLOW);
        Button back = button("返回", this::finish);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(58)); bp.topMargin = dp(20);
        content.addView(back, bp);
        refresh();
    }
    private void addSection(String heading, String hint, String key) {
        TextView h = label(heading, 22); h.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, dp(46)); hp.topMargin = dp(20);
        content.addView(h, hp);
        TextView sub = label(hint, 15); sub.setTextColor(Color.LTGRAY);
        content.addView(sub, new LinearLayout.LayoutParams(-1, dp(32)));
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        list.setTag("list_" + key); content.addView(list);
        Button add = button("＋ 新增網域", () -> promptDomain(key));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(52)); ap.topMargin = dp(8);
        content.addView(add, ap);
    }
    private boolean enabled() { return prefs.getBoolean("enabled", false); }
    private void refresh() {
        if (status == null) return;
        status.setText(enabled() ? "狀態：廣告過濾已啟用" : "狀態：廣告過濾已關閉");
        toggle.setText(enabled() ? "停用廣告阻擋" : "啟用廣告阻擋");
        renderRules(BLOCK); renderRules(ALLOW);
    }
    private void renderRules(String key) {
        LinearLayout list = content.findViewWithTag("list_" + key);
        if (list == null) return;
        list.removeAllViews();
        for (String domain : rules(key)) {
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), 0, dp(8), 0); row.setBackground(bg(Color.rgb(39,42,50), 0));
            TextView name = label(domain, 17); row.addView(name, new LinearLayout.LayoutParams(0, dp(48), 1));
            Button del = new Button(this); del.setText("刪除"); del.setTextSize(14); del.setAllCaps(false);
            del.setTextColor(Color.WHITE); del.setFocusable(true); del.setBackground(bg(Color.rgb(85,47,49), 0));
            del.setOnClickListener(v -> { LinkedHashSet<String> set = new LinkedHashSet<>(rules(key)); set.remove(domain); saveRules(key, set); refresh(); });
            row.addView(del, new LinearLayout.LayoutParams(dp(76), dp(42)));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(50)); rp.topMargin = dp(5);
            list.addView(row, rp);
        }
        if (list.getChildCount() == 0) {
            TextView empty = label("尚未新增自訂網域", 15); empty.setTextColor(Color.LTGRAY);
            list.addView(empty, new LinearLayout.LayoutParams(-1, dp(40)));
        }
    }
    private Set<String> rules(String key) {
        String raw = prefs.getString(key, "");
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw != null) for (String s : raw.split("\\s+")) {
            String d = normalize(s); if (!d.isEmpty()) out.add(d);
        }
        return out;
    }
    private void saveRules(String key, Set<String> set) {
        StringBuilder b = new StringBuilder(); for (String d : set) b.append(d).append('\n');
        prefs.edit().putString(key, b.toString()).apply();
    }
    private String normalize(String value) {
        String d = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        while (d.startsWith(".")) d = d.substring(1);
        while (d.endsWith(".")) d = d.substring(0, d.length()-1);
        if (d.startsWith("http://")) d = d.substring(7);
        if (d.startsWith("https://")) d = d.substring(8);
        int slash = d.indexOf('/'); if (slash >= 0) d = d.substring(0, slash);
        if (d.startsWith("*.") ) d = d.substring(2);
        if (d.length() > 253 || d.isEmpty() || d.contains(" ") || d.contains(":")) return "";
        for (String part : d.split("\\.")) {
            if (part.isEmpty() || part.length() > 63 || !part.matches("[a-z0-9_-]+")) return "";
        }
        return d;
    }
    private void promptDomain(String key) {
        EditText input = new EditText(this); input.setSingleLine(true);
        input.setHint("例如 ads.example.com"); input.setTextColor(Color.WHITE); input.setHintTextColor(Color.GRAY);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this).setTitle(key.equals(BLOCK) ? "新增封鎖網域" : "新增允許網域")
                .setView(input).setNegativeButton("取消", null).setPositiveButton("新增", (d, w) -> {
                    String domain = normalize(input.getText().toString());
                    if (domain.isEmpty()) { Toast.makeText(this, "網域格式不正確", Toast.LENGTH_LONG).show(); return; }
                    LinkedHashSet<String> set = new LinkedHashSet<>(rules(key)); set.add(domain); saveRules(key, set); refresh();
                }).show();
    }
    private void toggleFilter() {
        if (enabled()) {
            Intent stop = new Intent(this, AdsFilterVpnService.class);
            stop.setAction(AdsFilterVpnService.ACTION_STOP);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(stop); else startService(stop);
            prefs.edit().putBoolean("enabled", false).apply();
            refresh();
            return;
        }
        Intent prepare = VpnService.prepare(this);
        if (prepare != null) startActivityForResult(prepare, VPN_REQUEST);
        else startFilter();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == VPN_REQUEST && result == RESULT_OK) startFilter();
    }
    private void startFilter() {
        prefs.edit().putBoolean("enabled", true).apply();
        Intent service = new Intent(this, AdsFilterVpnService.class);
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
        refresh();
    }
}
