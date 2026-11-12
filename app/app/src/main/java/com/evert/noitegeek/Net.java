package com.evert.noitegeek;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** HTTP mínimo e um cache de texto em arquivo (o último JSON bom fica salvo pra abrir na hora). */
final class Net {
    private Net() {}

    static String get(String url, int timeoutMs) throws IOException {
        return request(url, null, timeoutMs);
    }

    static String postJson(String url, String body, int timeoutMs) throws IOException {
        return request(url, body, timeoutMs);
    }

    private static String request(String url, String body, int timeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setRequestProperty("User-Agent", "NoiteGeek/1.0 (Android TV)");
            c.setRequestProperty("Accept", "application/json");
            if (body != null) {
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream o = c.getOutputStream()) {
                    o.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = c.getResponseCode();
            if (code / 100 != 2) throw new IOException("HTTP " + code + " em " + url);
            try (InputStream in = c.getInputStream()) {
                return new String(readAll(in), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    // --- cache de texto ---

    static File file(Context c, String name) {
        return new File(c.getFilesDir(), name + ".json");
    }

    static String read(Context c, String name) {
        File f = file(c, name);
        if (!f.exists()) return null;
        try (InputStream in = new FileInputStream(f)) {
            return new String(readAll(in), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    static void write(Context c, String name, String text) {
        File f = file(c, name);
        File tmp = new File(f.getPath() + ".tmp");
        try (OutputStream o = new FileOutputStream(tmp)) {
            o.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.renameTo(f);
    }

    /** Milissegundos desde a última vez que esse cache foi salvo (muito grande se nunca foi). */
    static long age(Context c, String name) {
        File f = file(c, name);
        return f.exists() ? System.currentTimeMillis() - f.lastModified() : Long.MAX_VALUE / 2;
    }
}
