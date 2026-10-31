package com.gathin.tvhome;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.UUID;

/** Temporary same-Wi-Fi upload page used to transfer a phone photo to the TV as its wallpaper. */
final class LocalWallpaperServer extends Thread {
    interface Listener { void onUploaded(Uri uri); }
    private final Context context;
    private final Listener listener;
    private final String token = UUID.randomUUID().toString().replace("-", "");
    private volatile boolean running = true;
    private ServerSocket server;
    private int port;

    LocalWallpaperServer(Context context, Listener listener) {
        super("tv-wallpaper-upload");
        this.context = context.getApplicationContext();
        this.listener = listener;
        setDaemon(true);
    }

    String getUrl() {
        long end = System.currentTimeMillis() + 2500;
        while (port == 0 && isAlive() && System.currentTimeMillis() < end) {
            try { Thread.sleep(25); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
        }
        return "http://" + localIpv4() + ":" + port + "/?token=" + token;
    }

    @Override public void run() {
        try (ServerSocket ss = new ServerSocket(0)) {
            server = ss; port = ss.getLocalPort();
            while (running) {
                try (Socket socket = ss.accept()) { handle(socket); }
                catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
    }

    void stopServer() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) { }
    }

    private void handle(Socket socket) throws Exception {
        socket.setSoTimeout(15000);
        InputStream in = socket.getInputStream();
        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
        int state = 0, value;
        while ((value = in.read()) != -1) {
            headerBytes.write(value);
            if ((state == 0 && value == '\r') || (state == 2 && value == '\r')) state++;
            else if ((state == 1 && value == '\n') || (state == 3 && value == '\n')) state++;
            else state = value == '\r' ? 1 : 0;
            if (state == 4) break;
            if (headerBytes.size() > 16384) return;
        }
        String headers = headerBytes.toString(StandardCharsets.ISO_8859_1.name());
        String[] lines = headers.split("\r\n");
        if (lines.length == 0) return;
        String[] request = lines[0].split(" ");
        if (request.length < 2) return;
        String method = request[0], path = request[1];
        int length = 0;
        for (String line : lines) if (line.toLowerCase().startsWith("content-length:")) {
            try { length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim()); } catch (Exception ignored) { }
        }
        String query = path.contains("?") ? path.substring(path.indexOf('?') + 1) : "";
        if (!query.contains("token=" + token)) { respond(socket, 403, "Forbidden", "驗證失敗，請重新掃描電視上的 QR Code。"); return; }
        if ("GET".equals(method)) { respondHtml(socket); return; }
        if (!"POST".equals(method) || !path.startsWith("/upload")) { respond(socket, 404, "Not found", "找不到頁面"); return; }
        if (length < 1 || length > 20 * 1024 * 1024) { respond(socket, 413, "File too large", "圖片不可超過 20 MB。"); return; }
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int n = in.read(data, offset, length - offset);
            if (n < 0) break;
            offset += n;
        }
        if (offset != length) { respond(socket, 400, "Bad request", "圖片傳輸不完整，請重試。"); return; }
        BitmapFactory.Options opts = new BitmapFactory.Options(); opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        if (opts.outWidth <= 0 || opts.outHeight <= 0) { respond(socket, 415, "Unsupported", "檔案不是有效圖片。"); return; }
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (dir == null) dir = context.getFilesDir();
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "tv-wallpaper-" + System.currentTimeMillis() + ".jpg");
        try (OutputStream out = new java.io.FileOutputStream(file)) { out.write(data); out.flush(); }
        Uri uri = Uri.fromFile(file);
        respond(socket, 200, "OK", "圖片已傳送到電視，可以關閉此頁面。");
        new android.os.Handler(context.getMainLooper()).post(() -> listener.onUploaded(uri));
    }

    private void respondHtml(Socket socket) throws Exception {
        String html = "<!doctype html><html lang='zh-Hant'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<meta charset='utf-8'><title>傳送桌布到電視</title><body style='font:18px sans-serif;background:#171b23;color:white;padding:24px'>"
                + "<h2>傳送圖片到電視</h2><p>選擇手機中的圖片，傳送後會直接設定為電視桌布。</p>"
                + "<input id='f' type='file' accept='image/*' style='font-size:18px;margin:20px 0'>"
                + "<p><button id='b' style='font-size:20px;padding:14px 24px'>上傳並設定桌布</button></p><p id='s'></p>"
                + "<script>document.getElementById('b').onclick=async()=>{let f=document.getElementById('f').files[0],s=document.getElementById('s');if(!f){s.textContent='請先選擇圖片';return;}if(f.size>20971520){s.textContent='圖片不可超過20MB';return;}s.textContent='傳送中…';try{let r=await fetch('/upload?token=" + token + "',{method:'POST',headers:{'Content-Type':f.type||'application/octet-stream'},body:f});s.textContent=await r.text();}catch(e){s.textContent='傳送失敗，請確認手機與電視連接同一個 Wi-Fi。';}};</script>"
                + "</body></html>";
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        OutputStream out = socket.getOutputStream();
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(body); out.flush();
    }

    private void respond(Socket socket, int code, String status, String message) throws Exception {
        byte[] body = ("<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width'><body style='font:20px sans-serif;padding:24px'>" + message + "</body>").getBytes(StandardCharsets.UTF_8);
        OutputStream out = socket.getOutputStream();
        out.write(("HTTP/1.1 " + code + " " + status + "\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(body); out.flush();
    }

    private static String localIpv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<java.net.InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) return address.getHostAddress();
                }
            }
        } catch (Exception ignored) { }
        return "127.0.0.1";
    }
}
