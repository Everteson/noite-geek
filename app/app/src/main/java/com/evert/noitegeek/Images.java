package com.evert.noitegeek;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Carregador de imagens enxuto: baixa em 3 threads, guarda em disco (cacheDir/img, até 400 arquivos),
 * decodifica já no tamanho do cartão e em RGB_565 (metade da memória), e mantém um cache em RAM pequeno.
 */
final class Images {
    private Images() {}

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> MEM =
            new LruCache<String, Bitmap>((int) Math.min(24 * 1024, Runtime.getRuntime().maxMemory() / 1024 / 8)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount() / 1024;
                }
            };
    private static File dir;
    private static Context app;

    static void init(Context c) {
        if (dir != null) return;
        app = c.getApplicationContext();
        dir = new File(c.getCacheDir(), "img");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        POOL.execute(Images::trimDisk);
    }

    static void clearMemory() {
        MEM.evictAll();
    }

    /** Imagem da internet, decodificada pra largura de pelo menos widthPx. */
    static void load(ImageView v, String url, int widthPx) {
        load(v, url, widthPx, false);
    }

    /** Arte de dentro do app (assets). */
    static void asset(ImageView v, String path, int widthPx) {
        load(v, path, widthPx, true);
    }

    private static void load(ImageView v, String src, int widthPx, boolean asset) {
        if (src == null || src.isEmpty()) {
            v.setTag(null);
            v.setImageDrawable(null);
            return;
        }
        v.setTag(src);
        Bitmap hit = MEM.get(src);
        if (hit != null) {
            v.setImageBitmap(hit);
            return;
        }
        if (!asset) v.setImageDrawable(null);
        POOL.execute(() -> {
            Bitmap b = asset ? decodeAsset(src, widthPx) : decodeUrl(src, widthPx);
            if (b == null) return;
            MEM.put(src, b);
            MAIN.post(() -> {
                if (src.equals(v.getTag())) v.setImageBitmap(b);
            });
        });
    }

    private static BitmapFactory.Options opts(int srcW, int widthPx) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        int s = 1;
        while (srcW / (s * 2) >= widthPx) s *= 2;
        o.inSampleSize = s;
        return o;
    }

    private static Bitmap decodeAsset(String path, int widthPx) {
        try {
            BitmapFactory.Options b = new BitmapFactory.Options();
            b.inJustDecodeBounds = true;
            try (InputStream in = app.getAssets().open(path)) {
                BitmapFactory.decodeStream(in, null, b);
            }
            try (InputStream in = app.getAssets().open(path)) {
                return BitmapFactory.decodeStream(in, null, opts(b.outWidth, widthPx));
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap decodeUrl(String url, int widthPx) {
        File f = new File(dir, Integer.toHexString(url.hashCode()) + "-" + url.length());
        try {
            if (!f.exists() || f.length() == 0) {
                File tmp = new File(f.getPath() + ".part");
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(12000);
                c.setRequestProperty("User-Agent", "NoiteGeek/1.0 (Android TV)");
                try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[16 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                } finally {
                    c.disconnect();
                }
                if (!tmp.renameTo(f)) return null;
            } else {
                //noinspection ResultOfMethodCallIgnored
                f.setLastModified(System.currentTimeMillis());
            }
            BitmapFactory.Options b = new BitmapFactory.Options();
            b.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), b);
            if (b.outWidth <= 0) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
                return null;
            }
            return BitmapFactory.decodeFile(f.getPath(), opts(b.outWidth, widthPx));
        } catch (Exception e) {
            return null;
        }
    }

    private static void trimDisk() {
        File[] files = dir.listFiles();
        if (files == null || files.length <= 400) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < files.length - 300; i++) {
            //noinspection ResultOfMethodCallIgnored
            files[i].delete();
        }
    }
}
