package com.gathin.tvhome;

import android.content.Context;
import android.util.Log;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/**
 * DNS-compatible subset importer for uBlock/ABP filter lists.
 * Hostname-wide rules are imported; URL paths, cosmetic rules, scriptlets,
 * regex rules and resource-type rules require a browser request engine and are skipped.
 */
final class AdsFilterListManager {
    private static final String TAG = "AdsFilterLists";
    private static final String FILE_NAME = "adblock-domain-rules.txt";
    private static final String[] SOURCES = {
        "https://easylist.to/easylist/easylist.txt",
        "https://easylist.to/easylist/easyprivacy.txt"
    };
    private static final Pattern ABP_RULE = Pattern.compile("^(\\@@)?\\|\\|([a-z0-9.-]+)\\^(?:\\$.*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOST = Pattern.compile("(?i)^[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?$");
    private AdsFilterListManager() {}
    interface Callback { void onComplete(int blocked, int allowed, String error); }

    static void update(Context context, Callback callback) {
        new Thread(() -> {
            LinkedHashSet<String> block = new LinkedHashSet<>(), allow = new LinkedHashSet<>();
            String error = null;
            try {
                for (String source : SOURCES) parse(download(source), block, allow);
                if (block.isEmpty()) throw new IllegalStateException("下載完成，但沒有可用的網域規則");
                File target = new File(context.getFilesDir(), FILE_NAME);
                File temp = new File(context.getFilesDir(), FILE_NAME + ".tmp");
                try (FileOutputStream out = new FileOutputStream(temp)) {
                    for (String d : block) out.write(("B " + d + "\n").getBytes(StandardCharsets.UTF_8));
                    for (String d : allow) out.write(("A " + d + "\n").getBytes(StandardCharsets.UTF_8));
                    out.getFD().sync();
                }
                if (target.exists() && !target.delete()) throw new IllegalStateException("無法替換舊規則檔");
                if (!temp.renameTo(target)) throw new IllegalStateException("無法儲存規則檔");
                context.getSharedPreferences("ad_filter", Context.MODE_PRIVATE).edit()
                    .putLong("filter_lists_updated_at", System.currentTimeMillis()).apply();
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                Log.w(TAG, "Filter list update failed", e);
            }
            if (callback != null) {
                final String err = error; final int b = block.size(), a = allow.size();
                new android.os.Handler(context.getMainLooper()).post(() -> callback.onComplete(b, a, err));
            }
        }, "CleanYourTV-FilterListUpdate").start();
    }

    private static String download(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "CleanYourTV/1.0 (DNS filter list importer)");
        c.setInstanceFollowRedirects(true);
        try {
            int status = c.getResponseCode();
            if (status != 200) throw new IllegalStateException("清單下載失敗 HTTP " + status);
            StringBuilder body = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line; int total = 0;
                while ((line = r.readLine()) != null) {
                    total += line.length();
                    if (total > 12 * 1024 * 1024) throw new IllegalStateException("過濾清單超過 12 MB 安全上限");
                    body.append(line).append('\n');
                }
            }
            return body.toString();
        } finally { c.disconnect(); }
    }

    private static void parse(String text, Set<String> block, Set<String> allow) {
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("[") || line.startsWith("#")) continue;
            Matcher m = ABP_RULE.matcher(line);
            if (m.matches()) {
                String domain = normalize(m.group(2));
                if (!domain.isEmpty()) (m.group(1) == null ? block : allow).add(domain);
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length >= 2 && (parts[0].equals("0.0.0.0") || parts[0].equals("127.0.0.1") || parts[0].equals("::"))) {
                String domain = normalize(parts[1]);
                if (!domain.isEmpty() && !domain.equals("localhost")) block.add(domain);
            }
        }
    }

    private static String normalize(String value) {
        String d = value.toLowerCase(Locale.ROOT);
        while (d.endsWith(".")) d = d.substring(0, d.length() - 1);
        if (d.length() > 253 || !HOST.matcher(d).matches() || d.contains("..")) return "";
        for (String label : d.split("\\.")) if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) return "";
        return d;
    }

    static Set<String> load(Context context, char type) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.isFile()) return out;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) if (line.length() > 2 && line.charAt(0) == type && line.charAt(1) == ' ') out.add(line.substring(2));
        } catch (Exception e) { Log.w(TAG, "Cannot read cached filter rules", e); }
        return out;
    }
}