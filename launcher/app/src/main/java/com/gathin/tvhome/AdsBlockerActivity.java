package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.VpnService;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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
    private Button updateLists;
    private TextView listStatus;

    // Bundled starter rules. Allow rules take precedence over block rules.
    private static final String DEFAULT_BLOCK =
            "doubleclick.net\ngooglesyndication.com\ngoogleadservices.com\nadservice.google.com\n" +
            "app-measurement.com\nadcolony.com\nunityads.unity3d.com\n" +
            "applovin.com\nmoatads.com\nadsrvr.org\n" +
            "";
    private static final String DEFAULT_ALLOW = "";
    private static final String DEFAULT_RULES_VERSION = "10";

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

        // Remove only the exact domains the user previously asked to add.
        // Preserve all other custom rules and all downloaded subscription rules.
        String[] removeFromAllow = {
                "vid.dbokutv.com", "firebaselogging-pa.googleapis.com",
                "pic.dbokutv.com", "events.ads.vungle.com"
        };
        String[] removeFromBlock = {
                "googleads.g.doubleclick.net", "securepubads.g.doubleclick.net",
                "pubads.g.doubleclick.net", "pagead2.googlesyndication.com",
                "av1.xdrig.com", "me.xdrig.com", "www.xdrig.com", "xdrig.com",
                "googlevideo.com", "footprints-pa.googleapis.com", "www.googleapis.com",
                "www.googleadservices.com", "youtubei.googleapis.com",
                "rr2---sn-un57sne7.googlevideo.com", "eligibility-panelresearch.googlevideo.com",
                "www.googlevideo.com", "p-app.dhub.hinet.net", "ads.aralego.com",
                "adsv.svc.litv.tv", "acs2.svc.litv.tv", "googleapis.com", "svc.litv.tv"
        };
        LinkedHashSet<String> allow = new LinkedHashSet<>(rules(ALLOW));
        for (String domain : removeFromAllow) allow.remove(domain);
        saveRules(ALLOW, allow);

        LinkedHashSet<String> block = new LinkedHashSet<>(rules(BLOCK));
        for (String domain : removeFromBlock) block.remove(domain);
        for (String item : DEFAULT_BLOCK.split("\\s+")) {
            String domain = normalize(item);
            if (!domain.isEmpty()) block.add(domain);
        }
        saveRules(BLOCK, block);
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
    private android.graphics.drawable.Drawable focusBg(int normal, int focused) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, bg(focused, Color.rgb(255, 205, 120)));
        states.addState(new int[]{android.R.attr.state_pressed}, bg(focused, Color.WHITE));
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
        TextView v = new TextView(this); v.setText(text); v.setTextColor(Color.WHITE);
        v.setTextSize(size); v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    private Button button(String text, Runnable action) {
        Button b = new Button(this); b.setText(text); b.setTextColor(Color.WHITE); b.setTextSize(18);
        b.setAllCaps(false); b.setFocusable(true); b.setMinHeight(dp(54));
        b.setBackground(focusBg(Color.rgb(48, 51, 60), Color.rgb(74, 79, 92)));
        b.setPadding(dp(18), dp(8), dp(18), dp(8));
        keepFocusedVisible(b);
        b.setOnClickListener(v -> action.run());
        return b;
    }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(22, 24, 31));
        scroll.setFocusable(false);
        scroll.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(28), dp(22), dp(28), dp(30));
        scroll.addView(content);
        setContentView(scroll);
        TextView title = label("阻擋廣告", 30); title.setTypeface(null, Typeface.BOLD);
        content.addView(title, new LinearLayout.LayoutParams(-1, dp(58)));
        TextView desc = label("使用 uBlock Origin 清單擷取可由 DNS 阻擋的廣告／追蹤網域。首次啟用時，Android 會詢問是否允許 VPN 連線。", 17);
        desc.setTextColor(Color.LTGRAY); desc.setGravity(Gravity.TOP);
        content.addView(desc, new LinearLayout.LayoutParams(-1, dp(68)));
        status = label("", 19); status.setPadding(dp(16), 0, dp(16), 0); status.setBackground(bg(Color.rgb(39,42,50), 0));
        content.addView(status, new LinearLayout.LayoutParams(-1, dp(58)));
        toggle = button("", this::toggleFilter);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, dp(58)); tp.topMargin = dp(12);
        content.addView(toggle, tp);
        updateLists = button("更新 uBlock 過濾清單", this::updateFilterLists);
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(-1, dp(58)); up.topMargin = dp(10);
        content.addView(updateLists, up);
        listStatus = label("過濾清單：尚未下載", 15); listStatus.setTextColor(Color.LTGRAY);
        content.addView(listStatus, new LinearLayout.LayoutParams(-1, dp(42)));
        Button diagnostics = button("查看 DNS 診斷紀錄", this::showDnsDiagnostics);
        LinearLayout.LayoutParams diagParams = new LinearLayout.LayoutParams(-1, dp(54)); diagParams.topMargin = dp(8);
        content.addView(diagnostics, diagParams);
        TextView note = label("注意：此版本採用 uBlock Origin 清單，但本機 DNS 僅能攔截網域層級規則；網頁元素隱藏、腳本及網址路徑規則不支援。YouTube 影片廣告、加密 DNS／DoH 及與影片共用網域的廣告，可能無法攔截。", 15);
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
        int cachedBlock = AdsFilterListManager.load(this, 'B').size();
        int cachedAllow = AdsFilterListManager.load(this, 'A').size();
        long updated = prefs.getLong("filter_lists_updated_at", 0L);
        if (updated == 0L) listStatus.setText("過濾清單：尚未下載");
        else listStatus.setText("已載入網域規則：封鎖 " + cachedBlock + "／允許 " + cachedAllow + "；更新時間 " +
                android.text.format.DateFormat.format("yyyy/MM/dd HH:mm", updated));
        renderRules(BLOCK); renderRules(ALLOW);
    }
    private void showDnsDiagnostics() {
        String raw = prefs.getString("dns_diagnostics", "");
        if (raw == null || raw.trim().isEmpty()) raw = "目前沒有 DNS 封鎖或連線錯誤紀錄。";
        TextView message = new TextView(this);
        message.setText(raw);
        message.setTextColor(Color.WHITE);
        message.setTextSize(14);
        message.setTextIsSelectable(true);
        message.setPadding(dp(16), dp(12), dp(16), dp(12));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(message);
        new AlertDialog.Builder(this)
                .setTitle("DNS 診斷紀錄（最近 60 筆）")
                .setMessage("僅記錄 DNS 網域、封鎖來源與連線錯誤，不記錄影片網址。請重現播放問題後再查看。")
                .setView(scroll)
                .setPositiveButton("清除紀錄", (dialog, which) -> {
                    prefs.edit().remove("dns_diagnostics").remove("dns_diagnostics_seen").apply();
                    Toast.makeText(this, "診斷紀錄已清除", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("關閉", null)
                .show();
    }

    private void updateFilterLists() {
        updateLists.setEnabled(false);
        updateLists.setText("正在下載與整理過濾規則…");
        AdsFilterListManager.update(this, (blocked, allowed, error) -> {
            updateLists.setEnabled(true);
            updateLists.setText("更新過濾清單（EasyList + EasyPrivacy）");
            if (error != null && error.startsWith("PARTIAL:")) {
                Toast.makeText(this, "部分更新完成：封鎖 " + blocked + "、允許 " + allowed + " 個網域。\n" + error.substring(8), Toast.LENGTH_LONG).show();
            } else if (error != null) {
                Toast.makeText(this, "更新失敗：" + error, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "更新完成：封鎖 " + blocked + "、允許 " + allowed + " 個網域。", Toast.LENGTH_LONG).show();
            }
            if (enabled()) {
                Intent reload = new Intent(this, AdsFilterVpnService.class);
                reload.setAction(AdsFilterVpnService.ACTION_RELOAD_LISTS);
                if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(reload); else startService(reload);
            }
            refresh();
        });
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
            del.setTextColor(Color.WHITE); del.setFocusable(true); del.setMinHeight(dp(48));
            del.setBackground(focusBg(Color.rgb(85,47,49), Color.rgb(126,58,62)));
            keepFocusedVisible(del);
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
        input.setFocusable(true); input.setFocusableInTouchMode(true);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(focusBg(Color.rgb(39,42,50), Color.rgb(54,59,70)));
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
