package com.chistyl.sponsorblocktv;

import android.app.Activity;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
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
        intro.setText("本機官方 YouTube TV 偵測\n此程式安裝在電視本機，會先直接檢查電視自己的 DIAL 服務，再嘗試區域網路探索。");
        intro.setTextColor(0xffc6ccd5);
        intro.setTextSize(18);
        intro.setPadding(0, 8, 0, 18);
        root.addView(intro);

        scan = new Button(this);
        scan.setText("搜尋 YouTube TV");
        scan.setTextSize(18);
        scan.setOnClickListener(v -> startScan());
        root.addView(scan, new LinearLayout.LayoutParams(420, 64));

        status = new TextView(this);
        status.setText("尚未搜尋。\n\n此版本只負責 DIAL 裝置探索與配對資訊讀取，尚未啟用播放控制或 SponsorBlock 自動跳段。");
        status.setTextColor(0xffffffff);
        status.setTextSize(17);
        status.setPadding(0, 26, 0, 0);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        scan.requestFocus();
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
                try (DatagramSocket socket = new DatagramSocket()) {
                    socket.setSoTimeout(1200);
                    socket.setBroadcast(true);
                    InetAddress group = InetAddress.getByName("239.255.255.250");
                    socket.send(new DatagramPacket(request, request.length, group, 1900));
                    long until = System.currentTimeMillis() + 5500;
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
