package com.gathin.tvhome;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
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
        // uBlock Origin default filter lists (DNS-compatible hostname rules are imported).
        "https://ublockorigin.github.io/uAssets/filters/filters.txt",
        "https://ublockorigin.github.io/uAssets/filters/privacy.txt",
        "https://ublockorigin.github.io/uAssets/filters/badware.txt",
        "https://ublockorigin.github.io/uAssets/filters/quick-fixes.txt",
        "https://ublockorigin.github.io/uAssets/filters/unbreak.txt",
        // uBlock Origin's default third-party lists.
        "https://ublockorigin.github.io/uAssets/thirdparties/easylist.txt",
        "https://ublockorigin.github.io/uAssets/thirdparties/easyprivacy.txt",
        "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=1&mimetype=plaintext"
    };
    private static final Pattern ABP_RULE = Pattern.compile("^(\\@@)?\\|\\|([a-z0-9.-]+)\\^(?:\\$important)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOST = Pattern.compile("(?i)^[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?$");
    private AdsFilterListManager() {}
    interface Callback { void onComplete(int blocked, int allowed, String error); }

    static void update(Context context, Callback callback) {
        new Thread(() -> {
            LinkedHashSet<String> block = new LinkedHashSet<>(), allow = new LinkedHashSet<>();
            String error = null;
            try {
                List<String> failedSources = new ArrayList<>();
                List<Network> downloadNetworks = getPhysicalInternetNetworks(context);
                Log.i(TAG, "Filter download networks available: " + downloadNetworks.size());
                int successfulSources = 0;
                for (String source : SOURCES) {
                    try {
                        String body = downloadWithRetry(source, downloadNetworks);
                        int before = block.size() + allow.size();
                        parse(body, block, allow);
                        if (block.size() + allow.size() > before || !body.trim().isEmpty()) successfulSources++;
                        else failedSources.add(source + "（沒有可解析規則）");
                    } catch (Exception sourceError) {
                        failedSources.add(source + "（" + safeMessage(sourceError) + "）");
                        Log.w(TAG, "Skipping unavailable filter source: " + source, sourceError);
                    }
                }
                if (successfulSources == 0 || block.isEmpty()) {
                    throw new IllegalStateException("所有過濾清單來源都無法取得有效資料；已保留上一版清單。");
                }
                File target = new File(context.getFilesDir(), FILE_NAME);
                File temp = new File(context.getFilesDir(), FILE_NAME + ".tmp");
                File backup = new File(context.getFilesDir(), FILE_NAME + ".bak");
                try (FileOutputStream out = new FileOutputStream(temp)) {
                    for (String d : block) out.write(("B " + d + "\n").getBytes(StandardCharsets.UTF_8));
                    for (String d : allow) out.write(("A " + d + "\n").getBytes(StandardCharsets.UTF_8));
                    out.getFD().sync();
                }
                if (backup.exists() && !backup.delete()) throw new IllegalStateException("無法清除舊備份規則檔");
                if (target.exists() && !target.renameTo(backup)) throw new IllegalStateException("無法備份舊規則檔");
                if (!temp.renameTo(target)) {
                    if (backup.exists()) backup.renameTo(target);
                    throw new IllegalStateException("無法儲存新規則檔；已嘗試還原舊清單");
                }
                if (backup.exists()) backup.delete();
                context.getSharedPreferences("ad_filter", Context.MODE_PRIVATE).edit()
                    .putLong("filter_lists_updated_at", System.currentTimeMillis()).apply();
                if (!failedSources.isEmpty()) {
                    StringBuilder warning = new StringBuilder("PARTIAL: 成功取得 ")
                            .append(successfulSources).append("/").append(SOURCES.length)
                            .append(" 個來源；失敗來源：");
                    for (int i = 0; i < failedSources.size(); i++) {
                        if (i > 0) warning.append("；");
                        warning.append(failedSources.get(i));
                    }
                    error = warning.toString();
                }
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

    /** Prefer a physical Wi-Fi/Ethernet Network so list downloads do not depend on the local DNS VPN route. */
    private static List<Network> getPhysicalInternetNetworks(Context context) {
        List<Network> result = new ArrayList<>();
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return result;
            Network active = cm.getActiveNetwork();
            if (active != null) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(active);
                if (isPhysicalInternet(caps)) result.add(active);
            }
            for (Network network : cm.getAllNetworks()) {
                if (result.contains(network)) continue;
                if (isPhysicalInternet(cm.getNetworkCapabilities(network))) result.add(network);
            }
        } catch (Exception e) {
            Log.w(TAG, "Cannot enumerate physical networks; using default route", e);
        }
        return result;
    }

    private static boolean isPhysicalInternet(NetworkCapabilities caps) {
        return caps != null
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR));
    }

    private static String downloadWithRetry(String address, List<Network> networks) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (networks != null && !networks.isEmpty()) {
                for (Network network : networks) {
                    try {
                        return download(address, network);
                    } catch (Exception e) {
                        last = e;
                        Log.w(TAG, "Download failed on physical network " + network + " (attempt " + attempt + ")", e);
                    }
                }
            } else {
                try {
                    return download(address, null);
                } catch (Exception e) {
                    last = e;
                    Log.w(TAG, "Download failed on default network (attempt " + attempt + ")", e);
                }
            }
            if (attempt < 3) {
                try {
                    Thread.sleep(700L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw interrupted;
                }
            }
        }
        throw last == null ? new IOException("下載失敗") : last;
    }

    private static String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private static String download(String address, Network network) throws Exception {
        URL url = new URL(address);
        HttpURLConnection c = (HttpURLConnection) (network == null ? url.openConnection() : network.openConnection(url));
        c.setConnectTimeout(18000); c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) CleanYourTV/1.0");
        c.setRequestProperty("Accept", "text/plain,*/*");
        c.setRequestProperty("Accept-Encoding", "identity");
        c.setInstanceFollowRedirects(true);
        try {
            int status = c.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + status);
            StringBuilder body = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line; int total = 0;
                while ((line = r.readLine()) != null) {
                    total += line.length();
                    if (total > 12 * 1024 * 1024) throw new IOException("清單超過 12 MB 安全上限");
                    body.append(line).append('\n');
                }
            }
            if (body.length() == 0) throw new IOException("來源回傳空白內容");
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

    /** Removes every cached block rule while preserving cached allow exceptions. */
    static void clearBlocked(Context context) {
        File target = new File(context.getFilesDir(), FILE_NAME);
        File temp = new File(context.getFilesDir(), FILE_NAME + ".tmp");
        try {
            if (!target.isFile()) return;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(target), StandardCharsets.UTF_8));
                 FileOutputStream out = new FileOutputStream(temp)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("A ")) out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                }
                out.getFD().sync();
            }
            if (target.exists() && !target.delete()) throw new IOException("Cannot delete old rules file");
            if (!temp.renameTo(target)) throw new IOException("Cannot save cleared rules file");
        } catch (Exception e) {
            Log.w(TAG, "Cannot clear cached block rules", e);
            if (temp.exists()) temp.delete();
        }
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