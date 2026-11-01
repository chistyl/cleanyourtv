package com.chistyl.sponsorblocktv;

import android.app.Activity;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.MulticastSocket;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Inet4Address;
import java.util.Enumeration;
import java.net.SocketTimeoutException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private TextView status;
    private Button scan;
    private EditText pairingCode;
    private Button pairButton;
    private WifiManager.MulticastLock multicastLock;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifi != null) {
            multicastLock = wifi.createMulticastLock("sponsorblock-dial");
            multicastLock.setReferenceCounted(false);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(56, 36, 56, 36);
        root.setBackgroundColor(0xff101318);

        TextView title = new TextView(this);
        title.setText("SponsorBlock TV");
        title.setTextColor(0xffffffff);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(-1, 72));

        TextView intro = new TextView(this);
        intro.setText("改用 YouTube Lounge 配對\n不再依賴電視自我 SSDP 搜尋。請先到官方 YouTube 的「設定 → 連結電視與手機／使用電視代碼」取得配對碼，再輸入下方。");
        intro.setTextColor(0xffc6ccd5);
        intro.setTextSize(18);
        intro.setPadding(0, 8, 0, 18);
        root.addView(intro);

        pairingCode = new EditText(this);
        pairingCode.setSingleLine(true);
        pairingCode.setInputType(InputType.TYPE_CLASS_NUMBER);
        pairingCode.setHint("輸入 YouTube 電視配對碼");
        pairingCode.setTextColor(0xffffffff);
        pairingCode.setHintTextColor(0xff9ba6b5);
        pairingCode.setTextSize(22);
        pairingCode.setPadding(18, 8, 18, 8);
        pairingCode.setBackgroundColor(0xff273344);
        root.addView(pairingCode, new LinearLayout.LayoutParams(520, 68));

        pairButton = new Button(this);
        pairButton.setText("使用 YouTube Lounge 配對");
        pairButton.setTextSize(18);
        pairButton.setOnClickListener(v -> pairWithLoungeCode());
        root.addView(pairButton, new LinearLayout.LayoutParams(520, 68));

        scan = new Button(this);
        scan.setText("舊版 DIAL 搜尋（備用）");
        scan.setTextSize(16);
        scan.setOnClickListener(v -> startScan());
        root.addView(scan, new LinearLayout.LayoutParams(420, 60));

        status = new TextView(this);
        status.setText("尚未配對。\n\n配對成功後，下一階段會建立 Lounge 連線並讀取播放狀態。");
        status.setTextColor(0xffffffff);
        status.setTextSize(17);
        status.setPadding(0, 26, 0, 0);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        pairButton.requestFocus();
    }


    private void pairWithLoungeCode() {
        String code = pairingCode.getText().toString().replaceAll("[^0-9]", "");
        if (code.length() < 8 || code.length() > 16) {
            status.setText("配對碼格式不正確，請輸入 YouTube 電視畫面顯示的數字代碼。");
            pairingCode.requestFocus();
            return;
        }
        pairButton.setEnabled(false);
        status.setText("正在向 YouTube Lounge 驗證配對碼…");
        worker.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://www.youtube.com/api/lounge/pairing/get_screen");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(12000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Origin", "https://www.youtube.com");
                String body = "pairing_code=" + java.net.URLEncoder.encode(code, "UTF-8");
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
                int http = conn.getResponseCode();
                InputStream stream = http >= 200 && http < 400 ? conn.getInputStream() : conn.getErrorStream();
                String response = "";
                if (stream != null) {
                    try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                        byte[] b = new byte[4096]; int n;
                        while ((n = in.read(b)) > 0 && out.size() < 256 * 1024) out.write(b, 0, n);
                        response = out.toString("UTF-8");
                    }
                }
                if (http < 200 || http >= 300) {
                    final String msg = "YouTube Lounge 回應 HTTP " + http + "\n" + response.substring(0, Math.min(500, response.length()));
                    runOnUiThread(() -> status.setText(msg));
                    return;
                }
                String screenId = jsonString(response, "screenId");
                String loungeToken = jsonString(response, "loungeToken");
                String screenName = jsonString(response, "name");
                if (screenId == null || loungeToken == null) {
                    final String msg = "收到回應，但找不到 screenId／loungeToken。\n" + response.substring(0, Math.min(500, response.length()));
                    runOnUiThread(() -> status.setText(msg));
                    return;
                }
                getSharedPreferences("lounge_auth", MODE_PRIVATE).edit()
                        .putString("screen_id", screenId)
                        .putString("lounge_token", loungeToken)
                        .putString("screen_name", screenName == null ? "YouTube TV" : screenName)
                        .apply();
                final String success = "YouTube Lounge 配對成功！\n\n裝置：" + (screenName == null ? "YouTube TV" : screenName)
                        + "\nscreen ID：" + screenId + "\n\n已安全保存在此電視。下一階段將測試 Lounge 連線與播放狀態讀取。";
                runOnUiThread(() -> status.setText(success));
            } catch (Exception e) {
                final String msg = "配對連線失敗：" + e.getClass().getSimpleName() + " - " + String.valueOf(e.getMessage());
                runOnUiThread(() -> status.setText(msg));
            } finally {
                if (conn != null) conn.disconnect();
                runOnUiThread(() -> pairButton.setEnabled(true));
            }
        });
    }

    private String jsonString(String json, String key) {
        Matcher m = Pattern.compile("[\\\"]" + Pattern.quote(key) + "[\\\"]\\s*:\\s*[\\\"]([^\\\"]*)[\\\"]").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private void startScan() {
        scan.setEnabled(false);
        status.setText("正在檢查本機 YouTube TV 服務…");
        worker.execute(() -> {
            Map<String, Device> found = new LinkedHashMap<>();
            try {
                if (multicastLock != null && !multicastLock.isHeld()) multicastLock.acquire();
                byte[] request = ("M-SEARCH * HTTP/1.1\r\n"
                        + "HOST: 239.255.255.250:1900\r\n"
                        + "MAN: \"ssdp:discover\"\r\n"
                        + "MX: 3\r\n"
                        + "ST: urn:dial-multiscreen-org:service:dial:1\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII);
                InetAddress group = InetAddress.getByName("239.255.255.250");
                Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
                while (nets.hasMoreElements()) {
                    NetworkInterface ni = nets.nextElement();
                    if (!ni.isUp() || ni.isLoopback() || !ni.supportsMulticast()) continue;
                    boolean hasIpv4 = false;
                    Enumeration<InetAddress> addrs = ni.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        InetAddress a = addrs.nextElement();
                        if (a instanceof Inet4Address && !a.isLinkLocalAddress()) { hasIpv4 = true; break; }
                    }
                    if (!hasIpv4) continue;
                    try (MulticastSocket socket = new MulticastSocket()) {
                        socket.setSoTimeout(900);
                        socket.setNetworkInterface(ni);
                        socket.setTimeToLive(2);
                        socket.send(new DatagramPacket(request, request.length, group, 1900));
                        long until = System.currentTimeMillis() + 3200;
                        while (System.currentTimeMillis() < until) {
                            byte[] buf = new byte[8192];
                            DatagramPacket packet = new DatagramPacket(buf, buf.length);
                            try { socket.receive(packet); }
                            catch (SocketTimeoutException e) { continue; }
                            String response = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                            String location = header(response, "LOCATION");
                            if (location == null || found.containsKey(location)) continue;
                            Device device = readDevice(location, packet.getAddress().getHostAddress());
                            if (device != null && device.youtube) found.put(location, device);
                        }
                    } catch (Exception interfaceError) {
                        // Continue scanning other interfaces; Ethernet and Wi-Fi can behave differently.
                    }
                }
            } catch (Exception e) {
                final String error = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
                runOnUiThread(() -> { status.setText("搜尋失敗：\n" + error + "\n\n請確認 Wi-Fi／有線網路與區域網路探索未被隔離。"); scan.setEnabled(true); });
                releaseMulticast();
                return;
            }
            releaseMulticast();
            runOnUiThread(() -> {
                if (found.isEmpty()) {
                    status.setText("沒有找到可辨識的官方 YouTube TV。\n\n請確認 YouTube App 已在電視上開啟過，並確認兩台裝置位於同一個區域網路。部分路由器會阻擋 SSDP 多播探索。");
                } else {
                    StringBuilder out = new StringBuilder("找到 " + found.size() + " 台 YouTube TV：\n\n");
                    for (Device d : found.values()) {
                        out.append("裝置：").append(d.name).append("\nIP：").append(d.ip)
                           .append("\n配對識別碼：").append(d.screenId == null ? "未提供" : d.screenId)
                           .append("\nDIAL：").append(d.appUrl == null ? "已偵測" : d.appUrl)
                           .append("\n\n");
                    }
                    out.append("目前完成 DIAL 服務探索；尚未配對或控制播放。");
                    status.setText(out.toString());
                }
                scan.setEnabled(true);
            });
        });
    }


    // When this helper itself runs on the TV, SSDP multicast may not loop back to
    // the same device. Probe the TV's own IPv4 interfaces and the standard DIAL
    // HTTP service directly before relying on multicast discovery.
    private Device probeLocalDial() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address) || address.isLinkLocalAddress()) continue;
                    String ip = address.getHostAddress();
                    String[] paths = {"/ssdp/device-desc.xml", "/dd.xml", "/device.xml"};
                    for (String path : paths) {
                        try {
                            String base = "http://" + ip + ":8008";
                            HttpURLConnection conn = (HttpURLConnection) new URL(base + path).openConnection();
                            conn.setConnectTimeout(700); conn.setReadTimeout(900);
                            conn.setRequestMethod("GET");
                            int code = conn.getResponseCode();
                            String xml = "";
                            if (code >= 200 && code < 300) {
                                try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                                    byte[] b = new byte[2048]; int n;
                                    while ((n = in.read(b)) > 0 && out.size() < 128 * 1024) out.write(b, 0, n);
                                    xml = out.toString("UTF-8");
                                }
                            }
                            String appBase = conn.getHeaderField("Application-URL");
                            conn.disconnect();
                            if (appBase == null) appBase = tag(xml, "application-url");
                            if (appBase == null) continue;
                            String youtubeUrl = appBase.replaceAll("/+$", "") + "/YouTube";
                            String appXml = httpGetWithOrigin(youtubeUrl);
                            if (!appXml.toLowerCase(Locale.ROOT).contains("youtube")) continue;
                            String screenId = tag(appXml, "screenId");
                            if (screenId == null) screenId = tag(appXml, "screenId");
                            String name = tag(xml, "friendlyName");
                            return new Device(name == null ? "本機 Android TV / Google TV" : name,
                                    ip, youtubeUrl, screenId, true);
                        } catch (Exception ignored) { }
                    }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private String httpGetWithOrigin(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(1200); c.setReadTimeout(1200); c.setRequestMethod("GET");
        c.setRequestProperty("Origin", "https://www.youtube.com");
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[2048]; int n;
            while ((n = in.read(b)) > 0 && out.size() < 128 * 1024) out.write(b, 0, n);
            return out.toString("UTF-8");
        } finally { c.disconnect(); }
    }

    private Device readDevice(String location, String ip) {
        try {
            String xml = httpGet(location);
            String appBase = tag(xml, "application-url");
            String name = tag(xml, "friendlyName");
            if (appBase == null) return null;
            String youtubeUrl = appBase.replaceAll("/+$", "") + "/YouTube";
            String appXml = httpGet(youtubeUrl);
            boolean youtube = appXml.toLowerCase(Locale.ROOT).contains("youtube");
            if (!youtube) return null;
            String screenId = tag(appXml, "screenId");
            if (screenId == null) screenId = attribute(appXml, "screenId");
            return new Device(name == null ? "Android TV / Google TV" : name, ip, youtubeUrl, screenId, true);
        } catch (Exception ignored) { return null; }
    }

    private String httpGet(String address) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(1800); c.setReadTimeout(1800); c.setRequestMethod("GET");
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) > 0 && out.size() < 256 * 1024) out.write(b, 0, n);
            return out.toString("UTF-8");
        } finally { c.disconnect(); }
    }

    private String header(String response, String key) {
        for (String line : response.split("\\r?\\n")) {
            int p = line.indexOf(':');
            if (p > 0 && line.substring(0, p).trim().equalsIgnoreCase(key)) return line.substring(p + 1).trim();
        }
        return null;
    }
    private String tag(String xml, String name) {
        Matcher m = Pattern.compile("<(?:\\w+:)?" + name + "\\b[^>]*>(.*?)</(?:\\w+:)?" + name + ">", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(xml);
        return m.find() ? m.group(1).replaceAll("<[^>]+>", "").trim() : null;
    }
    private String attribute(String xml, String name) {
        Matcher m = Pattern.compile(name + "\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(xml);
        return m.find() ? m.group(1) : null;
    }
    private void releaseMulticast() {
        try { if (multicastLock != null && multicastLock.isHeld()) multicastLock.release(); } catch (Exception ignored) {}
    }
    @Override protected void onDestroy() {
        releaseMulticast();
        worker.shutdownNow();
        super.onDestroy();
    }
    private static class Device {
        final String name, ip, appUrl, screenId; final boolean youtube;
        Device(String n, String i, String a, String s, boolean y) { name=n; ip=i; appUrl=a; screenId=s; youtube=y; }
    }
}
