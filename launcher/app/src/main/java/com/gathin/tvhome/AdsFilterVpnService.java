package com.gathin.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class AdsFilterVpnService extends VpnService {
    private static final String TAG = "AdsFilterVpn";
    private static final String CHANNEL = "ad_filter";
    private static final String PREFS = "ad_filter";
    private static final String DNS_CLIENT = "10.13.0.1";
    private static final String DNS_LOCAL = "10.13.0.2";
    private static final String UPSTREAM = "1.1.1.1";
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ParcelFileDescriptor tunnel;
    private Thread worker;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (running.get()) return START_STICKY;
        try {
            startNotice();
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
            stopSelf();
        }
        return START_STICKY;
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
            if (answer == null) return null;
            return makeReply(p, ihl, srcPort, answer);
        }
        if (isBlocked(domain)) {
            byte[] blocked = query.clone();
            int flags = u16(blocked, 2);
            put16(blocked, 2, (flags & 0x0100) | 0x8083); // NXDOMAIN; retain RD
            put16(blocked, 6, 0); put16(blocked, 8, 0); put16(blocked, 10, 0);
            return makeReply(p, ihl, srcPort, blocked);
        }
        byte[] answer = forward(query);
        return answer == null ? null : makeReply(p, ihl, srcPort, answer);
    }

    private byte[] forward(byte[] query) {
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            if (!protect(socket)) throw new IllegalStateException("Could not protect upstream DNS socket");
            socket.setSoTimeout(3500);
            byte[] ip = new byte[4]; byte[] raw = InetAddress.getByName(UPSTREAM).getAddress();
            System.arraycopy(raw, 0, ip, 0, 4);
            socket.send(new DatagramPacket(query, query.length, InetAddress.getByAddress(ip), 53));
            byte[] buf = new byte[4096];
            DatagramPacket reply = new DatagramPacket(buf, buf.length);
            socket.receive(reply);
            byte[] out = new byte[reply.getLength()];
            System.arraycopy(reply.getData(), reply.getOffset(), out, 0, out.length);
            return out;
        } catch (Exception e) {
            Log.w(TAG, "Upstream DNS failed", e); return null;
        } finally { if (socket != null) socket.close(); }
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

    private boolean isAllowed(String domain) { return matches(domain, "allow_domains"); }
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

    @Override public void onRevoke() { stopFilter(); super.onRevoke(); }
    @Override public void onDestroy() { stopFilter(); super.onDestroy(); }
    private void stopFilter() {
        running.set(false);
        if (tunnel != null) { try { tunnel.close(); } catch (Exception ignored) {} tunnel = null; }
        if (worker != null) { worker.interrupt(); worker = null; }
        stopForeground(true);
    }
}
