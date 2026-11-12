package com.evert.noitegeek;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Os atalhos coloridos da tela inicial, editáveis pela TV. Cada linha é "pacote|nome|fundo|texto".
 * Fica salvo no próprio app; sem nada salvo, usa a lista padrão do config.xml.
 */
final class Shortcuts {
    private Shortcuts() {}

    private static final String PREFS = "atalhos";
    private static final String KEY = "lista";

    static List<String> load(Context c) {
        String saved = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null);
        if (saved == null) return defaults(c);
        List<String> out = new ArrayList<>();
        for (String l : saved.split("\n")) if (l.split("\\|").length >= 4) out.add(l);
        return out;
    }

    static List<String> defaults(Context c) {
        return new ArrayList<>(Arrays.asList(c.getResources().getStringArray(R.array.shortcuts)));
    }

    static void save(Context c, List<String> lines) {
        SharedPreferences.Editor e = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        e.putString(KEY, String.join("\n", lines)).apply();
    }

    static void reset(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply();
    }

    static boolean contains(List<String> lines, String pkg) {
        for (String l : lines) if (l.startsWith(pkg + "|")) return true;
        return false;
    }

    /** Linha nova pra um app qualquer: nome do app e a cor média do ícone. */
    static String lineFor(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        String name = pkg;
        int bg = 0xFF2A3350;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            name = String.valueOf(pm.getApplicationLabel(ai)).replace("|", " ").trim();
            bg = iconColor(pm.getApplicationIcon(ai));
        } catch (Exception ignored) {
            // fica com o padrão
        }
        int fg = luminance(bg) > 0.6 ? 0xFF111111 : Color.WHITE;
        return pkg + "|" + name + "|" + hex(bg) + "|" + hex(fg);
    }

    private static int iconColor(Drawable d) {
        Bitmap b = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        d.setBounds(0, 0, 24, 24);
        d.draw(cv);
        long r = 0, g = 0, bl = 0, n = 0;
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                int p = b.getPixel(x, y);
                if (Color.alpha(p) < 200) continue;
                float[] hsv = new float[3];
                Color.colorToHSV(p, hsv);
                int w = hsv[1] > 0.25f ? 4 : 1;  // pesa mais as cores vivas que o branco/cinza
                r += (long) Color.red(p) * w;
                g += (long) Color.green(p) * w;
                bl += (long) Color.blue(p) * w;
                n += w;
            }
        }
        b.recycle();
        if (n == 0) return 0xFF2A3350;
        int col = Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n));
        float[] hsv = new float[3];
        Color.colorToHSV(col, hsv);
        hsv[2] = Math.min(hsv[2], 0.85f);  // nem branco estourado
        return Color.HSVToColor(hsv);
    }

    private static double luminance(int c) {
        return (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0;
    }

    private static String hex(int c) {
        return String.format(Locale.US, "#%06X", c & 0xFFFFFF);
    }
}
