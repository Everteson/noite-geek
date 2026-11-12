package com.evert.noitegeek;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Painel "Entradas": antena e HDMIs que a própria TV informa (TvInputManager), com o nome que você deu
 * nas Configurações ou o do aparelho ligado (HDMI-CEC). Trocar de entrada é o pedido padrão do Android
 * (VIEW do endereço da entrada, com o tipo de canal), que na TCL quem atende é o app de TV dela.
 */
final class Inputs {
    private Inputs() {}

    static final class Entry {
        final String id;
        final String name;
        final String sub;
        final boolean tuner;

        Entry(String id, String name, String sub, boolean tuner) {
            this.id = id;
            this.name = name;
            this.sub = sub;
            this.tuner = tuner;
        }

        /** "HDMI 1", ou "HDMI 1 · PlayStation 5" quando a TV sabe o aparelho. */
        String label() {
            return tuner || sub.isEmpty() ? name : name + " · " + sub;
        }
    }

    /** Entradas físicas da TV: antena primeiro, depois as HDMI/AV na ordem da TV. */
    static List<Entry> list(Context c) {
        List<Entry> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        List<TvInputInfo> all;
        try {
            all = tim.getTvInputList();
        } catch (Exception e) {
            return out;
        }
        List<Entry> tuners = new ArrayList<>();
        for (TvInputInfo in : all) {
            if (in.getParentId() != null || !isSystem(in)) continue;  // aparelhos CEC entram como nome da porta
            boolean tuner = in.getType() == TvInputInfo.TYPE_TUNER;
            if (!tuner && !in.isPassthroughInput()) continue;
            String name = label(c, in);
            if (tuner && (name.isEmpty() || name.equalsIgnoreCase("TV"))) name = "Antena";
            String sub = tuner ? "Canais da TV aberta" : devices(c, all, in.getId());
            (tuner ? tuners : out).add(new Entry(in.getId(), name, sub, tuner));
        }
        out.sort((x, y) -> Long.compare(portNumber(x.id), portNumber(y.id)));  // a TV lista fora de ordem
        out.addAll(0, tuners);
        return out;
    }

    /** O número no fim do id da entrada (".../HW15" -> 15), que segue a ordem das portas. */
    private static long portNumber(String id) {
        int i = id.length();
        while (i > 0 && Character.isDigit(id.charAt(i - 1))) i--;
        try {
            return i < id.length() ? Long.parseLong(id.substring(i)) : Long.MAX_VALUE;
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    private static boolean isSystem(TvInputInfo in) {
        ApplicationInfo ai = in.getServiceInfo() != null ? in.getServiceInfo().applicationInfo : null;
        return ai != null && (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
    }

    private static String label(Context c, TvInputInfo in) {
        CharSequence custom = in.loadCustomLabel(c);
        if (custom != null && custom.length() > 0) return custom.toString();
        CharSequence l = in.loadLabel(c);
        return l == null ? "" : l.toString();
    }

    /** Nomes dos aparelhos HDMI-CEC ligados nessa porta ("PlayStation 5"), se a TV souber. */
    private static String devices(Context c, List<TvInputInfo> all, String parent) {
        StringBuilder sb = new StringBuilder();
        for (TvInputInfo in : all) {
            if (!parent.equals(in.getParentId())) continue;
            String n = label(c, in);
            if (n.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(n);
        }
        return sb.toString();
    }

    private static final String PREFS = "entradas";

    /** A última entrada aberta por aqui (pra voltar pro videogame ou PC), ou null. */
    static String lastId(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("ultima", null);
    }

    /** Na primeira vez, põe as portas HDMI/AV nos atalhos (dá pra mover ou remover depois). */
    static boolean addPortsOnce(Context c, java.util.Collection<Entry> entries) {
        android.content.SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.getBoolean("portas_nos_atalhos", false) || entries.isEmpty()) return false;
        List<Entry> ports = new ArrayList<>();
        for (Entry e : entries) if (!e.tuner) ports.add(e);
        ports.sort((x, y) -> Long.compare(portNumber(x.id), portNumber(y.id)));
        List<String> lines = Shortcuts.load(c);
        for (Entry e : ports) {
            if (!Shortcuts.contains(lines, "input:" + e.id)) lines.add(Shortcuts.lineForInput(e.id, e.name));
        }
        Shortcuts.save(c, lines);
        p.edit().putBoolean("portas_nos_atalhos", true).apply();
        return true;
    }

    /** Liga a entrada. Prefere o app de TV da marca; sem ele, quem atender o pedido padrão. */
    static boolean open(Activity a, String inputId, String preferPkg) {
        a.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("ultima", inputId).apply();
        Uri u = TvContract.buildChannelUriForPassthroughInput(inputId);
        Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(u, TvContract.Channels.CONTENT_ITEM_TYPE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PackageManager pm = a.getPackageManager();
        Intent mine = new Intent(i).setPackage(preferPkg);
        try {
            if (preferPkg != null && pm.resolveActivity(mine, 0) != null) {
                a.startActivity(mine);
            } else {
                a.startActivity(i);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Mostra o painel ao lado do menu lateral. */
    static void show(Activity a, String preferPkg, Runnable fallback) {
        List<Entry> list = list(a);
        if (list.isEmpty()) {
            fallback.run();
            return;
        }
        Dialog d = new Dialog(a, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(14), Ui.dp(16), Ui.dp(14), Ui.dp(14));
        panel.setBackground(Ui.round(0xF2111729, 16));
        TextView title = Ui.text(a, "Entradas", 18, Ui.WHITE, Ui.bold);
        title.setPadding(Ui.dp(6), 0, 0, Ui.dp(10));
        panel.addView(title);
        String last = lastId(a);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(last)) {
                Entry e = list.remove(i);
                list.add(0, new Entry(e.id, e.name, "Última usada" + (e.sub.isEmpty() ? "" : " · " + e.sub), e.tuner));
                break;
            }
        }
        View first = null;
        for (Entry e : list) {
            View row = row(a, e, v -> {
                d.dismiss();
                if (!open(a, e.id, preferPkg)) fallback.run();
            });
            row.setOnLongClickListener(v -> {  // segurar OK: põe ou tira dos atalhos coloridos
                List<String> lines = Shortcuts.load(a);
                String key = "input:" + e.id;
                boolean had = false;
                for (int k = lines.size() - 1; k >= 0; k--) {
                    if (lines.get(k).startsWith(key + "|")) {
                        lines.remove(k);
                        had = true;
                    }
                }
                if (!had) lines.add(Shortcuts.lineForInput(e.id, e.name));
                Shortcuts.save(a, lines);
                android.widget.Toast.makeText(a, e.name + (had ? " saiu dos atalhos" : " foi pros atalhos"),
                        android.widget.Toast.LENGTH_SHORT).show();
                if (a instanceof MainActivity) ((MainActivity) a).refreshShortcuts();
                return true;
            });
            panel.addView(row);
            if (first == null) first = row;
        }
        TextView hint = Ui.text(a, "Segure OK pra pôr ou tirar dos atalhos", 11, Ui.TEXT3, Ui.regular);
        hint.setPadding(Ui.dp(6), Ui.dp(10), 0, 0);
        panel.addView(hint);
        d.setContentView(panel, new LinearLayout.LayoutParams(Ui.dp(260), -2));
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.x = Ui.dp(84);
            lp.dimAmount = 0.45f;
            w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        d.show();
        if (first != null) first.requestFocus();
    }

    private static View row(Activity a, Entry e, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(a);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(10), Ui.dp(8), Ui.dp(10), Ui.dp(8));
        r.setFocusable(true);
        r.setClickable(true);
        r.setBackground(focusBg());
        ImageView ic = new ImageView(a);
        ic.setImageResource(e.tuner ? R.drawable.ic_tv : R.drawable.ic_input);
        ic.setColorFilter(Ui.ACCENT);
        r.addView(ic, new LinearLayout.LayoutParams(Ui.dp(22), Ui.dp(22)));
        LinearLayout txt = new LinearLayout(a);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(Ui.dp(12), 0, 0, 0);
        txt.addView(Ui.text(a, e.name, 15, Ui.WHITE, Ui.bold));
        if (!e.sub.isEmpty()) {
            TextView s = Ui.text(a, e.sub, 11, Ui.TEXT2, Ui.regular);
            s.setPadding(0, Ui.dp(3), 0, 0);
            txt.addView(s);
        }
        r.addView(txt);
        r.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(4);
        r.setLayoutParams(lp);
        return r;
    }

    private static android.graphics.drawable.Drawable focusBg() {
        android.graphics.drawable.StateListDrawable s = new android.graphics.drawable.StateListDrawable();
        android.graphics.drawable.GradientDrawable on = Ui.round(0x33FF7A1A, 10);
        on.setStroke(Ui.dp(1.5f), Ui.ACCENT);
        s.addState(new int[]{android.R.attr.state_focused}, on);
        s.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return s;
    }
}
