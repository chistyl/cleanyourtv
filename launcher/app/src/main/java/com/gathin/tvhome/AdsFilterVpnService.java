package com.gathin.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.LinkProperties;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

public class AdsFilterVpnService extends VpnService {
    private static final String TAG = "AdsFilterVpn";
    private static final String CHANNEL = "ad_filter";
    private static final String PREFS = "ad_filter";
    private static final String DNS_CLIENT = "10.13.0.1";
    private static final String DNS_LOCAL = "10.13.0.2";
    private static final String UPSTREAM = "1.1.1.1";
    private static final String DNS_MODE_AUTO = "auto";
    static final String ACTION_REFRESH_NETWORK = "com.gathin.tvhome.action.REFRESH_NETWORK";
    static final String ACTION_STOP = "com.gathin.tvhome.action.STOP_AD_FILTER";
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ParcelFileDescriptor tunnel;
    private Thread worker;
    private volatile Network underlyingNetwork;
    private volatile List<InetAddress> upstreamServers = Collections.singletonList(parseAddress(UPSTREAM));
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_REFRESH_NETWORK.equals(intent.getAction())) {
            if (running.get()) {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                if (cm != null) {
                    Network current = findPhysicalNetwork(cm);
                    if (current != null) updateUnderlyingNetwork(current);
                }
            }
            return START_STICKY;
        }
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
            stopFilter();
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (running.get()) return START_STICKY;
        try {
            startNotice();
            // Track real Wi-Fi/Ethernet networks, excluding the VPN itself. The default
            // network can change while the filter is running, so do not capture it only once.
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) throw new IllegalStateException("Connectivity service unavailable");
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build();
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(Network network) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                    if (isPhysicalInternet(caps)) updateUnderlyingNetwork(network);
                }
                @Override public void onLost(Network network) {
                    if (network.equals(underlyingNetwork)) {
                        underlyingNetwork = null;
                        Network replacement = findPhysicalNetwork(cm);
                        if (replacement != null) updateUnderlyingNetwork(replacement);
                        // Do not change Android's system-wide underlying-network selection.
                        // The DNS socket is explicitly bound to the physical network in forward().
                    }
                }
                @Override public void onCapabilitiesChanged(Network network, NetworkCapabilities caps) {
                    if (isPhysicalInternet(caps)) updateUnderlyingNetwork(network);
                }
                @Override public void onLinkPropertiesChanged(Network network, LinkProperties properties) {
                    if (network.equals(underlyingNetwork)) updateUpstreamServers(network, cm);
                }
            };
            cm.registerNetworkCallback(request, networkCallback);
            Network initial = findPhysicalNetwork(cm);
            if (initial == null) throw new IllegalStateException("No active Wi-Fi or Ethernet network");
            updateUnderlyingNetwork(initial);
            Builder builder = new Builder()
                    .setSession("CleanYourTV 廣告過濾")
                    .setMtu(1500)
                    .addAddress(DNS_CLIENT, 32)
                    .addDnsServer(DNS_LOCAL)
                    .addRoute(DNS_LOCAL, 32);
            tunnel = builder.establish();
            if (tunnel == null) throw new IllegalStateException("VPN establish returned null");
            running.set(true);
            worker = new Thread(this::runDnsLoop, "CleanYourTV-DNS");
            worker.start();
        } catch (Exception e) {
            Log.e(TAG, "Unable to start DNS filter", e);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
            stopSelf();
        }
        return START_STICKY;
    }

    private boolean isPhysicalInternet(NetworkCapabilities caps) {
        return caps != null
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private Network findPhysicalNetwork(ConnectivityManager cm) {
        Network active = cm.getActiveNetwork();
        if (active != null && isPhysicalInternet(cm.getNetworkCapabilities(active))) return active;
        for (Network candidate : cm.getAllNetworks()) {
            if (isPhysicalInternet(cm.getNetworkCapabilities(candidate))) return candidate;
        }
        return null;
    }

    private void updateUnderlyingNetwork(Network network) {
        underlyingNetwork = network;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm != null) updateUpstreamServers(network, cm);
        // Intentionally do not call setUnderlyingNetworks(). This VPN only routes
        // the local DNS endpoint (/32); changing the platform's underlying-network
        // association can disrupt Ethernet routing on some Android TV firmware.
        // forward() protects and binds each upstream DNS socket to this physical network.
    }

    private void updateUpstreamServers(Network network, ConnectivityManager cm) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (!DNS_MODE_AUTO.equals(prefs.getString("dns_mode", DNS_MODE_AUTO))) {
            upstreamServers = Collections.singletonList(parseAddress(UPSTREAM));
            return;
        }
        try {
            LinkProperties properties = cm.getLinkProperties(network);
            List<InetAddress> discovered = new ArrayList<>();
            if (properties != null) {
                for (InetAddress address : properties.getDnsServers()) {
                    if (address != null && !address.isAnyLocalAddress() && !address.isLoopbackAddress()
                            && !address.isLinkLocalAddress() && !address.isMulticastAddress()) {
                        discovered.add(address);
                    }
                }
            }
            if (discovered.isEmpty()) discovered.add(parseAddress(UPSTREAM));
            upstreamServers = Collections.unmodifiableList(discovered);
            Log.i(TAG, "Auto DNS negotiated: " + discovered.size() + " upstream server(s)");
        } catch (Exception e) {
            upstreamServers = Collections.singletonList(parseAddress(UPSTREAM));
            Log.w(TAG, "Unable to read network DNS; using fallback", e);
        }
    }

    private static InetAddress parseAddress(String address) {
        try { return InetAddress.getByName(address); }
        catch (Exception e) { throw new IllegalStateException("Invalid DNS fallback", e); }
    }

    private void startNotice() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "廣告過濾", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("CleanYourTV 廣告過濾")
                .setContentText("本機 DNS 網域過濾正在執行")
                .setOngoing(true);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(7413, b.build(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else startForeground(7413, b.build());
    }

    private void runDnsLoop() {
        byte[] packet = new byte[32767];
        try (FileInputStream in = new FileInputStream(tunnel.getFileDescriptor());
             FileOutputStream out = new FileOutputStream(tunnel.getFileDescriptor())) {
            while (running.get()) {
                int n = in.read(packet);
                if (n <= 0) continue;
                try {
                    byte[] response = processPacket(packet, n);
                    if (response != null) out.write(response);
                } catch (Exception e) {
                    Log.w(TAG, "DNS packet skipped", e);
                }
            }
        } catch (Exception e) {
            if (running.get()) Log.e(TAG, "DNS tunnel stopped", e);
        } finally {
            stopSelf();
        }
    }

    private byte[] processPacket(byte[] p, int length) throws Exception {
        if (length < 28 || (p[0] >> 4 & 15) != 4 || (p[9] & 255) != 17) return null;
        int ihl = (p[0] & 15) * 4;
        if (length < ihl + 8) return null;
        int srcPort = u16(p, ihl), dstPort = u16(p, ihl + 2);
        if (dstPort != 53) return null;
        int udpLen = u16(p, ihl + 4);
        int dnsOff = ihl + 8;
        int dnsLen = Math.min(length - dnsOff, udpLen - 8);
        if (dnsLen < 17) return null;
        byte[] query = new byte[dnsLen];
        System.arraycopy(p, dnsOff, query, 0, dnsLen);
        String domain = questionName(query);
        if (domain == null) return null;

        if (isAllowed(domain)) {
            byte[] answer = forward(query);
            if (answer == null) {
                appendDnsLog(domain, "查詢失敗", "上游 DNS 無回應");
                return null;
            }
            appendDnsLog(domain, "允許", dnsResponseSummary(answer));
            return makeReply(p, ihl, srcPort, answer);
        }
        if (isBlocked(domain)) {
            byte[] blocked = query.clone();
            int flags = u16(blocked, 2);
            put16(blocked, 2, (flags & 0x0100) | 0x8083); // NXDOMAIN; retain RD
            put16(blocked, 6, 0); put16(blocked, 8, 0); put16(blocked, 10, 0);
            appendDnsLog(domain, "已阻擋", "NXDOMAIN（網域不存在回應）");
            return makeReply(p, ihl, srcPort, blocked);
        }
        byte[] answer = forward(query);
        if (answer == null) {
            appendDnsLog(domain, "查詢失敗", "上游 DNS 無回應");
            return null;
        }
        appendDnsLog(domain, "一般通過", dnsResponseSummary(answer));
        return makeReply(p, ihl, srcPort, answer);
    }

    private byte[] forward(byte[] query) {
        Network underlying = underlyingNetwork;
        if (underlying == null) {
            Log.w(TAG, "No physical network available for DNS forwarding");
            return null;
        }
        List<InetAddress> candidates = upstreamServers;
        for (InetAddress dns : candidates) {
            DatagramSocket socket = null;
            try {
                socket = new DatagramSocket();
                if (!protect(socket)) throw new IllegalStateException("Could not protect upstream DNS socket");
                underlying.bindSocket(socket);
                socket.setSoTimeout(1800);
                socket.send(new DatagramPacket(query, query.length, dns, 53));
                byte[] buf = new byte[4096];
                DatagramPacket reply = new DatagramPacket(buf, buf.length);
                socket.receive(reply);
                byte[] out = new byte[reply.getLength()];
                System.arraycopy(reply.getData(), reply.getOffset(), out, 0, out.length);
                return out;
            } catch (Exception e) {
                Log.w(TAG, "DNS upstream failed: " + dns.getHostAddress(), e);
            } finally {
                if (socket != null) socket.close();
            }
        }
        try {
            InetAddress fallback = parseAddress(UPSTREAM);
            if (!candidates.contains(fallback)) {
                DatagramSocket socket = new DatagramSocket();
                try {
                    if (!protect(socket)) throw new IllegalStateException("Could not protect fallback DNS socket");
                    underlying.bindSocket(socket);
                    socket.setSoTimeout(1800);
                    socket.send(new DatagramPacket(query, query.length, fallback, 53));
                    byte[] buf = new byte[4096];
                    DatagramPacket reply = new DatagramPacket(buf, buf.length);
                    socket.receive(reply);
                    byte[] out = new byte[reply.getLength()];
                    System.arraycopy(reply.getData(), reply.getOffset(), out, 0, out.length);
                    return out;
                } finally { socket.close(); }
            }
        } catch (Exception e) {
            Log.w(TAG, "Fallback DNS failed", e);
        }
        return null;
    }

    private byte[] makeReply(byte[] request, int ihl, int clientPort, byte[] dns) {
        int total = 20 + 8 + dns.length;
        byte[] out = new byte[total];
        out[0] = 0x45; put16(out, 2, total); put16(out, 4, 0); put16(out, 6, 0x4000);
        out[8] = 64; out[9] = 17;
        System.arraycopy(request, 16, out, 12, 4); // original destination becomes response source
        System.arraycopy(request, 12, out, 16, 4); // original source becomes response destination
        put16(out, 10, checksum(out, 0, 20));
        put16(out, 20, 53); put16(out, 22, clientPort); put16(out, 24, 8 + dns.length);
        System.arraycopy(dns, 0, out, 28, dns.length);
        return out;
    }

    // Keep a bounded local DNS history so the TV UI can explain each filtering decision.
    private void appendDnsLog(String domain, String result, String response) {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            synchronized (AdsFilterVpnService.class) {
                JSONArray old = new JSONArray(prefs.getString("dns_logs", "[]"));
                JSONArray next = new JSONArray();
                int start = Math.max(0, old.length() - 299);
                for (int i = start; i < old.length(); i++) next.put(old.getJSONObject(i));
                JSONObject item = new JSONObject();
                item.put("time", System.currentTimeMillis());
                item.put("domain", domain);
                item.put("result", result);
                item.put("response", response);
                next.put(item);
                prefs.edit().putString("dns_logs", next.toString()).apply();
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to save DNS history", e);
        }
    }

    private String dnsResponseSummary(byte[] dns) {
        if (dns == null || dns.length < 4) return "回應資料不完整";
        int rcode = dns[3] & 15;
        if (rcode == 3) return "NXDOMAIN（網域不存在）";
        if (rcode == 0) return "NOERROR（DNS 回應正常）";
        return "DNS 錯誤碼：" + rcode;
    }

    private boolean isAllowed(String domain) {
        // User-controlled temporary exceptions take precedence over both bundled and custom block rules.
        return matches(domain, "temporary_allow_domains") || matches(domain, "allow_domains");
    }
    private boolean isBlocked(String domain) {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String raw = p.getString("block_domains", "");
        if (raw == null) return false;
        for (String item : raw.split("\\s+")) {
            String rule = item.trim().toLowerCase(Locale.ROOT);
            if (!rule.isEmpty() && (domain.equals(rule) || domain.endsWith("." + rule))) return true;
        }
        return false;
    }
    private boolean matches(String domain, String key) {
        String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(key, "");
        if (raw == null) return false;
        for (String item : raw.split("\\s+")) {
            String rule = item.trim().toLowerCase(Locale.ROOT);
            if (!rule.isEmpty() && (domain.equals(rule) || domain.endsWith("." + rule))) return true;
        }
        return false;
    }

    private String questionName(byte[] dns) {
        int p = 12; StringBuilder name = new StringBuilder();
        while (p < dns.length) {
            int n = dns[p++] & 255;
            if (n == 0) break;
            if ((n & 0xc0) != 0 || n > 63 || p + n > dns.length) return null;
            if (name.length() > 0) name.append('.');
            for (int i = 0; i < n; i++) {
                int ch = dns[p++] & 255;
                if (ch < 33 || ch > 126) return null;
                name.append((char) ch);
            }
        }
        return name.length() == 0 ? null : name.toString().toLowerCase(Locale.ROOT);
    }
    private int u16(byte[] b, int p) { return ((b[p] & 255) << 8) | (b[p + 1] & 255); }
    private void put16(byte[] b, int p, int v) { b[p] = (byte)(v >> 8); b[p + 1] = (byte)v; }
    private int checksum(byte[] b, int off, int len) {
        long sum = 0;
        for (int i = off; i < off + len; i += 2) {
            int word = (b[i] & 255) << 8;
            if (i + 1 < off + len) word |= b[i + 1] & 255;
            sum += word;
        }
        while ((sum >> 16) != 0) sum = (sum & 0xffff) + (sum >> 16);
        return (int)(~sum) & 0xffff;
    }

    @Override public void onRevoke() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply();
        stopFilter(); super.onRevoke();
    }
    @Override public void onDestroy() { stopFilter(); super.onDestroy(); }
    private void stopFilter() {
        running.set(false);
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm != null && networkCallback != null) {
            try { cm.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) {}
            networkCallback = null;
        }
        underlyingNetwork = null;
        if (tunnel != null) { try { tunnel.close(); } catch (Exception ignored) {} tunnel = null; }
        if (worker != null) { worker.interrupt(); worker = null; }
        stopForeground(true);
        stopSelf();
    }
}
