package com.evert.noitegeek;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    static final String CRUNCHYROLL = "com.crunchyroll.crunchyroid";
    static final String YOUTUBE = "com.google.android.youtube.tv";
    static final String LIVE_TV = "com.tcl.tv";

    private static final long ANIME_TTL = 3 * 3600_000L;   // animes em alta: a cada 3 h
    private static final long YT_TTL = 15 * 60_000L;       // feed do PC: a cada 15 min (só rede de casa)
    private static final long RETRY = 10 * 60_000L;        // depois de uma falha, espera 10 min

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Locale br = new Locale("pt", "BR");

    private ImageView bg, bannerImg;
    private TextView greet, greetSub, clock, date, bannerTitle, bannerSub;
    private Ui.SnapScroll scroll;
    private LinearLayout content, shortcutRow;
    private Section crCont, hist, crList, anime, rec, subs;
    private Period period;
    private String shortcutSignature = "";
    private long animeTry, ytTry;
    private boolean animeBusy, ytBusy;
    private BroadcastReceiver tick;

    /** Uma fileira: título + cartões. Escondida quando não tem nada. */
    private final class Section {
        final LinearLayout root, row;
        String rendered = "";
        String pending;

        Section(String title, int iconRes, int iconColor) {
            root = new LinearLayout(MainActivity.this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setClipChildren(false);
            root.setClipToPadding(false);
            root.setPadding(0, Ui.dp(14), 0, 0);
            LinearLayout head = new LinearLayout(MainActivity.this);
            head.setGravity(Gravity.CENTER_VERTICAL);
            ImageView ic = new ImageView(MainActivity.this);
            ic.setImageResource(iconRes);
            ic.setColorFilter(iconColor);
            head.addView(ic, new LinearLayout.LayoutParams(Ui.dp(20), Ui.dp(20)));
            TextView t = Ui.text(MainActivity.this, title + "  ›", 17, Ui.WHITE, Ui.bold);
            t.setPadding(Ui.dp(8), 0, 0, 0);
            head.addView(t);
            root.addView(head);
            HorizontalScrollView hs = new HorizontalScrollView(MainActivity.this);
            hs.setHorizontalScrollBarEnabled(false);
            hs.setClipChildren(false);
            hs.setClipToPadding(false);
            hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
            row = new LinearLayout(MainActivity.this);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            row.setPadding(Ui.dp(4), Ui.dp(10), Ui.dp(24), Ui.dp(8));
            hs.addView(row);
            root.addView(hs);
            root.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        Images.init(this);
        setContentView(build());
        applyPeriod(true);
        buildShortcuts();
        String a = Net.read(this, "anime");
        if (a != null) renderAnime(a);
        String y = Net.read(this, "feed");
        if (y != null) renderYoutube(y);
        main.post(this::focusFirst);
    }

    @Override
    protected void onResume() {
        super.onResume();
        tick = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                updateClock();
                applyPeriod(false);
                // TV parada na tela inicial: a cada minuto vê se alguma lista venceu (só baixa se venceu)
                // e aplica as que chegaram enquanto o cursor estava na fileira, se ele já saiu dela.
                refreshIfStale();
                flushPending(true);
            }
        };
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(tick, f);
        updateClock();
        applyPeriod(false);
        buildShortcuts();
        refreshIfStale();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tick != null) unregisterReceiver(tick);
        tick = null;
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Fileiras que chegaram enquanto o foco estava nelas: aplica agora, com a tela escondida.
        flushPending(false);
    }

    /** Aplica as listas que ficaram esperando; onlyUnfocused = só nas fileiras sem o cursor. */
    private void flushPending(boolean onlyUnfocused) {
        for (Section s : new Section[]{crCont, hist, crList, anime, rec, subs}) {
            if (s.pending == null || (onlyUnfocused && s.root.hasFocus())) continue;
            String p = s.pending;
            s.pending = null;
            if (s == anime) renderAnime(p); else renderYoutube(p);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Apertou Home já estando aqui: volta pro topo.
        scroll.smoothScrollTo(0, 0);
        focusFirst();
    }

    @Override
    public void onBackPressed() {
        // Tela inicial: Voltar não sai daqui, só sobe pro topo.
        scroll.smoothScrollTo(0, 0);
        focusFirst();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) Images.clearMemory();
    }

    // ------------------------------------------------------------------ layout

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0A0F1C);

        bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x11000000, 0x66070A14, 0xB805070F}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout body = new LinearLayout(this);
        body.setClipChildren(false);
        root.addView(body, new FrameLayout.LayoutParams(-1, -1));

        body.addView(buildSidebar(), new LinearLayout.LayoutParams(Ui.dp(72), -1));

        scroll = new Ui.SnapScroll(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        // Corta tudo na borda do menu lateral: fileiras roladas pro lado e cartões com zoom não
        // passam por cima dele (dentro da área de conteúdo o zoom continua livre).
        scroll.setClipChildren(true);
        scroll.setClipToPadding(true);
        scroll.setFillViewport(true);
        body.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1f));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        content.setPadding(Ui.dp(16), Ui.dp(14), Ui.dp(26), Ui.dp(260));
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));

        content.addView(buildHeader());
        content.addView(buildBanner());

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipChildren(false);
        hs.setClipToPadding(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        shortcutRow = new LinearLayout(this);
        shortcutRow.setClipChildren(false);
        shortcutRow.setClipToPadding(false);
        shortcutRow.setPadding(Ui.dp(4), Ui.dp(14), Ui.dp(24), Ui.dp(4));
        hs.addView(shortcutRow);
        content.addView(hs);

        crCont = new Section("Continuar na Crunchyroll", R.drawable.ic_history, 0xFFF47521);
        hist = new Section("Continuar no YouTube", R.drawable.ic_history, Ui.WHITE);
        crList = new Section("Sua lista na Crunchyroll", R.drawable.ic_play, 0xFFF47521);
        anime = new Section("Animes em alta na Crunchyroll", R.drawable.ic_star, 0xFFF47521);
        rec = new Section("Recomendados pra você no YouTube", R.drawable.ic_play, 0xFFFF3B30);
        subs = new Section("Novos das suas inscrições", R.drawable.ic_play, 0xFFFF3B30);
        content.addView(crCont.root);
        content.addView(hist.root);
        content.addView(crList.root);
        content.addView(anime.root);
        content.addView(rec.root);
        content.addView(subs.root);
        return root;
    }

    private View buildSidebar() {
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setGravity(Gravity.CENTER_HORIZONTAL);
        side.setPadding(0, Ui.dp(16), 0, Ui.dp(16));
        side.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xCC070A14, 0x88070A14}));
        side.setClipChildren(false);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_gamepad);
        logo.setColorFilter(Ui.ACCENT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(30), Ui.dp(30));
        lp.bottomMargin = Ui.dp(22);
        side.addView(logo, lp);

        side.addView(sideItem(R.drawable.ic_home, "Início", v -> {
            scroll.smoothScrollTo(0, 0);
            focusFirst();
        }));
        side.addView(sideItem(R.drawable.ic_apps, "Apps",
                v -> startActivity(new Intent(this, AppsActivity.class))));
        // Antena e HDMIs num painel; sem entradas na TV (ou sem o serviço), abre o app de TV como antes.
        View inputsItem = sideItem(R.drawable.ic_input, "Entradas", v -> Inputs.show(this, LIVE_TV, this::openLiveTv));
        inputsItem.setOnLongClickListener(v -> {  // segurar OK: volta direto pra última entrada usada
            String last = Inputs.lastId(this);
            if (last == null || !Inputs.open(this, last, LIVE_TV)) Inputs.show(this, LIVE_TV, this::openLiveTv);
            return true;
        });
        side.addView(inputsItem);
        side.addView(sideItem(R.drawable.ic_settings, "Ajustes", v -> openSettings()));
        return side;
    }

    private View sideItem(int icon, String label, View.OnClickListener click) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setFocusable(true);
        item.setClickable(true);
        item.setOnClickListener(click);
        GradientDrawable on = Ui.round(0x33FF7A1A, 12);
        on.setStroke(Ui.dp(1.5f), Ui.ACCENT);
        android.graphics.drawable.StateListDrawable sl = new android.graphics.drawable.StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_focused}, on);
        sl.addState(new int[]{}, new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        item.setBackground(sl);
        ImageView ic = new ImageView(this);
        ic.setImageResource(icon);
        ic.setColorFilter(0xFFDDE3EE);
        item.addView(ic, new LinearLayout.LayoutParams(Ui.dp(22), Ui.dp(22)));
        TextView t = Ui.text(this, label, 10, 0xFFDDE3EE, Ui.medium);
        t.setPadding(0, Ui.dp(4), 0, 0);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        item.addView(t, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(58), Ui.dp(56));
        lp.bottomMargin = Ui.dp(8);
        item.setLayoutParams(lp);
        Ui.zoomOnFocus(item, 1.05f, null);
        return item;
    }

    private View buildHeader() {
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        greet = Ui.text(this, "", 28, Ui.WHITE, Ui.bold);
        greetSub = Ui.text(this, "", 13, Ui.TEXT2, Ui.regular);
        greetSub.setPadding(0, Ui.dp(4), 0, 0);
        left.addView(greet);
        left.addView(greetSub);
        h.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));

        clock = Ui.text(this, "", 24, Ui.WHITE, Ui.bold);
        h.addView(clock);
        View bar = new View(this);
        bar.setBackgroundColor(0x55FFFFFF);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.dp(1), Ui.dp(20));
        bl.setMargins(Ui.dp(12), 0, Ui.dp(12), 0);
        h.addView(bar, bl);
        date = Ui.text(this, "", 14, Ui.TEXT2, Ui.medium);
        h.addView(date);

        ImageView gear = new ImageView(this);
        gear.setImageResource(R.drawable.ic_settings);
        gear.setColorFilter(Ui.WHITE);
        gear.setPadding(Ui.dp(7), Ui.dp(7), Ui.dp(7), Ui.dp(7));
        gear.setFocusable(true);
        gear.setClickable(true);
        gear.setBackground(Ui.round(0x22FFFFFF, 18));
        gear.setForeground(Ui.focusRing(18));
        gear.setContentDescription("Ajustes");
        gear.setOnClickListener(v -> openSettings());
        Ui.zoomOnFocus(gear, 1.1f, (v, has) -> {
            if (has) scroll.smoothScrollTo(0, 0);
        });
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(Ui.dp(36), Ui.dp(36));
        gl.leftMargin = Ui.dp(16);
        h.addView(gear, gl);
        return h;
    }

    private View buildBanner() {
        FrameLayout b = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(190));
        lp.topMargin = Ui.dp(12);
        b.setLayoutParams(lp);
        b.setBackground(Ui.round(Ui.CARD, 16));
        Ui.clipRound(b, 16);

        bannerImg = new ImageView(this);
        bannerImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        b.addView(bannerImg, new FrameLayout.LayoutParams(-1, -1));

        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x00000000, 0x00000000, 0x99000000, 0x66000000, 0x10000000}));
        b.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setGravity(Gravity.CENTER_VERTICAL);
        bannerTitle = Ui.text(this, "", 44, Ui.WHITE, Ui.bold);
        bannerTitle.setShadowLayer(Ui.dp(8), 0, Ui.dp(2), 0xAA000000);
        bannerSub = Ui.text(this, "", 14, 0xFFE6EAF2, Ui.regular);
        bannerSub.setLineSpacing(Ui.dp(3), 1f);
        bannerSub.setPadding(0, Ui.dp(10), 0, 0);
        bannerSub.setShadowLayer(Ui.dp(6), 0, Ui.dp(1), 0xCC000000);
        txt.addView(bannerTitle);
        txt.addView(bannerSub);
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-2, -1);
        tl.leftMargin = Ui.dp(330);
        b.addView(txt, tl);
        return b;
    }

    // ------------------------------------------------------------------ período e relógio

    private void applyPeriod(boolean force) {
        Period p = Period.now();
        boolean art = force || !p.sameArt(period);
        boolean words = force || period == null || !period.key.equals(p.key);
        period = p;
        if (art) {
            Images.asset(bannerImg, "art/" + p.artName() + "-banner.jpg", Ui.dp(840));
        }
        if (words || bg.getDrawable() == null) {
            Images.asset(bg, "art/fundo-" + p.key + ".jpg", 1280);
        }
        if (words) {
            greet.setText(p.greeting);
            greetSub.setText(p.subtitle);
            SpannableStringBuilder s = new SpannableStringBuilder(p.word + " ");
            int start = s.length();
            s.append("Geek");
            s.setSpan(new ForegroundColorSpan(Ui.ACCENT), start, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            bannerTitle.setText(s);
            bannerSub.setText(p.banner);
        }
    }

    private void updateClock() {
        Date now = new Date();
        clock.setText(new SimpleDateFormat("HH:mm", br).format(now));
        String d = new SimpleDateFormat("EEE, d 'de' MMM", br).format(now).replace(".", "");
        date.setText(Character.toUpperCase(d.charAt(0)) + d.substring(1));
    }

    // ------------------------------------------------------------------ atalhos de apps

    private static final int PICK_APP = 7;
    private List<String> shortcutLines = new ArrayList<>();
    private final List<Integer> shortcutShown = new ArrayList<>();  // posição na lista de cada atalho visível
    private int moving = -1;  // atalho sendo movido (índice em shortcutLines), -1 = nenhum

    private void buildShortcuts() {
        buildShortcuts(-1);
    }

    /** Chamado pelo painel de Entradas depois de pôr/tirar uma entrada dos atalhos. */
    void refreshShortcuts() {
        buildShortcuts(-1);
    }

    /** Monta os atalhos; focusLine >= 0 põe o foco nesse atalho depois. */
    private void buildShortcuts(int focusLine) {
        PackageManager pm = getPackageManager();
        shortcutLines = Shortcuts.load(this);
        List<String[]> found = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        StringBuilder sig = new StringBuilder("m" + moving + ";");
        java.util.Map<String, Inputs.Entry> inputs = new java.util.HashMap<>();
        for (Inputs.Entry e : Inputs.list(this)) inputs.put(e.id, e);
        if (Inputs.addPortsOnce(this, inputs.values())) shortcutLines = Shortcuts.load(this);
        for (int i = 0; i < shortcutLines.size(); i++) {
            String[] f = shortcutLines.get(i).split("\\|");
            if (f.length < 4) continue;
            if (Shortcuts.isInput(f[0])) {
                Inputs.Entry e = inputs.get(Shortcuts.inputId(f[0]));
                if (e == null) continue;
                f[1] = e.label();  // nome atual: o que você deu na TV, ou o aparelho ligado (CEC)
                sig.append(f[1]).append(';');
            } else if (launchIntent(f[0]) == null) {
                continue;
            }
            found.add(f);
            idx.add(i);
            sig.append(shortcutLines.get(i)).append(';');
        }
        if (focusLine < 0 && sig.toString().equals(shortcutSignature)) return;
        shortcutSignature = sig.toString();
        shortcutShown.clear();
        shortcutShown.addAll(idx);
        shortcutRow.removeAllViews();
        View toFocus = null;
        for (int k = 0; k < found.size(); k++) {
            String[] f = found.get(k);
            final int line = idx.get(k);
            boolean grabbed = line == moving;
            LinearLayout pill = new LinearLayout(this);
            pill.setGravity(Gravity.CENTER_VERTICAL);
            pill.setPadding(Ui.dp(11), 0, Ui.dp(11), 0);
            pill.setFocusable(true);
            pill.setClickable(true);
            pill.setLongClickable(true);
            GradientDrawable back = Ui.round(parseColor(f[2], Ui.CARD), 12);
            if (grabbed) back.setStroke(Ui.dp(3), Ui.ACCENT);
            pill.setBackground(back);
            pill.setForeground(Ui.focusRing(12));
            if (grabbed) pill.addView(arrow("◀", 0, Ui.dp(8)));
            ImageView ic = new ImageView(this);
            boolean isInput = Shortcuts.isInput(f[0]);
            if (isInput) {
                ic.setImageResource(R.drawable.ic_input);
                ic.setColorFilter(Ui.ACCENT);
                ic.setPadding(Ui.dp(2), Ui.dp(2), Ui.dp(2), Ui.dp(2));
            } else {
                try {
                    ic.setImageDrawable(pm.getApplicationIcon(f[0]));
                } catch (PackageManager.NameNotFoundException ignored) {
                    // instalado sem ícone: fica só o nome
                }
            }
            pill.addView(ic, new LinearLayout.LayoutParams(Ui.dp(28), Ui.dp(28)));
            // Entradas fechadas ficam iguais: o número da porta ao lado do ícone diferencia ("HDMI 2" -> 2).
            TextView num = null;
            if (isInput) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\s*$").matcher(f[1].split(" · ")[0]);
                String n = m.find() ? m.group(1) : f[1].substring(0, Math.min(2, f[1].length()));
                num = Ui.text(this, n, 15, Ui.WHITE, Ui.bold);
                num.setPadding(Ui.dp(5), 0, Ui.dp(2), 0);
                num.setVisibility(grabbed ? View.GONE : View.VISIBLE);
                pill.addView(num);
            }
            final TextView portNum = num;
            // Só o ícone; o nome aparece quando o atalho está selecionado (ou sendo movido).
            TextView t = Ui.text(this, f[1], 15, parseColor(f[3], Ui.WHITE), Ui.bold);
            t.setPadding(Ui.dp(9), 0, Ui.dp(5), 0);
            t.setVisibility(grabbed ? View.VISIBLE : View.GONE);
            pill.addView(t);
            if (grabbed) pill.addView(arrow("▶", Ui.dp(8), 0));
            final String pkg = f[0];
            final String name = f[1];
            pill.setOnClickListener(v -> {
                if (moving >= 0) {
                    stopMoving();
                } else if (Shortcuts.isInput(pkg)) {
                    if (!Inputs.open(this, Shortcuts.inputId(pkg), LIVE_TV)) openLiveTv();
                } else {
                    launch(pkg);
                }
            });
            pill.setOnLongClickListener(v -> {
                if (moving < 0) shortcutMenu(line, name);
                return true;
            });
            Ui.zoomOnFocus(pill, 1.07f, (v, has) -> {
                t.setVisibility(has || line == moving ? View.VISIBLE : View.GONE);
                if (portNum != null) portNum.setVisibility(has || line == moving ? View.GONE : View.VISIBLE);
                if (has) {
                    scroll.smoothScrollTo(0, 0);
                    keepVisible(pill);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.dp(44));
            lp.rightMargin = Ui.dp(10);
            shortcutRow.addView(pill, lp);
            if (line == focusLine) toFocus = pill;
        }
        if (moving < 0) shortcutRow.addView(addPill());
        if (toFocus != null) toFocus.requestFocus();
    }

    /** Depois de abrir o nome, garante que o atalho inteiro ficou à vista na fileira. */
    private void keepVisible(View v) {
        v.post(() -> v.requestRectangleOnScreen(new android.graphics.Rect(-Ui.dp(12), 0,
                v.getWidth() + Ui.dp(12), v.getHeight()), false));
    }

    private static int parseColor(String s, int fallback) {
        try {
            return Color.parseColor(s);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private TextView arrow(String s, int padLeft, int padRight) {
        TextView a = Ui.text(this, s, 13, Ui.WHITE, Ui.bold);
        a.setShadowLayer(Ui.dp(3), 0, 0, 0xCC000000);
        a.setPadding(padLeft, 0, padRight, 0);
        return a;
    }

    /** O "+ Adicionar" no fim da fileira. */
    private View addPill() {
        LinearLayout pill = new LinearLayout(this);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(Ui.dp(15), 0, Ui.dp(15), 0);
        pill.setFocusable(true);
        pill.setClickable(true);
        GradientDrawable back = Ui.round(0x22FFFFFF, 12);
        back.setStroke(Ui.dp(1.5f), 0x66FFFFFF);
        pill.setBackground(back);
        pill.setForeground(Ui.focusRing(12));
        TextView t = Ui.text(this, "+", 20, Ui.WHITE, Ui.bold);
        pill.addView(t);
        pill.setId(View.generateViewId());
        pill.setNextFocusRightId(pill.getId());  // fim da fileira: ▶ não pula pra fileira de baixo
        pill.setOnClickListener(v -> pickApp());
        Ui.zoomOnFocus(pill, 1.07f, (v, has) -> {
            t.setText(has ? "+  Adicionar" : "+");
            t.setTextSize(has ? 15 : 20);
            if (has) {
                scroll.smoothScrollTo(0, 0);
                keepVisible(pill);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.dp(44));
        lp.rightMargin = Ui.dp(12);
        pill.setLayoutParams(lp);
        return pill;
    }

    /** Segurar OK num atalho: mover, remover, adicionar, voltar ao padrão. */
    private void shortcutMenu(int line, String name) {
        String[] opts = {"Mover", "Remover da tela inicial", "Adicionar outro app", "Voltar aos atalhos padrão"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(name)
                .setItems(opts, (d, which) -> {
                    if (which == 0) {
                        startMoving(line);
                    } else if (which == 1) {
                        removeShortcut(line);
                    } else if (which == 2) {
                        pickApp();
                    } else {
                        Shortcuts.reset(this);
                        buildShortcuts(-1);
                        focusFirst();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void startMoving(int line) {
        moving = line;
        buildShortcuts(line);
        Toast.makeText(this, "◀ ▶ pra mover  ·  OK pra soltar", Toast.LENGTH_LONG).show();
    }

    private void stopMoving() {
        int line = moving;
        moving = -1;
        Shortcuts.save(this, shortcutLines);
        buildShortcuts(line);
    }

    /** Troca o atalho sendo movido com o vizinho visível (dir = -1 esquerda, +1 direita). */
    private void moveBy(int dir) {
        int pos = shortcutShown.indexOf(moving);
        int to = pos + dir;
        if (pos < 0 || to < 0 || to >= shortcutShown.size()) return;
        int a = shortcutShown.get(pos), b = shortcutShown.get(to);
        String tmp = shortcutLines.get(a);
        shortcutLines.set(a, shortcutLines.get(b));
        shortcutLines.set(b, tmp);
        Shortcuts.save(this, shortcutLines);
        moving = b;
        buildShortcuts(b);
    }

    private void removeShortcut(int line) {
        if (line < 0 || line >= shortcutLines.size()) return;
        int pos = Math.max(0, shortcutShown.indexOf(line));
        shortcutLines.remove(line);
        Shortcuts.save(this, shortcutLines);
        buildShortcuts(-1);
        int n = shortcutRow.getChildCount();
        if (n > 0) shortcutRow.getChildAt(Math.min(pos, n - 1)).requestFocus();
    }

    private void pickApp() {
        try {
            startActivityForResult(new Intent(this, AppsActivity.class).putExtra(AppsActivity.PICK, true), PICK_APP);
        } catch (Exception ignored) {
            // nada
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != PICK_APP || res != RESULT_OK || data == null) return;
        String pkg = data.getStringExtra(AppsActivity.PICKED);
        if (pkg == null || pkg.isEmpty()) return;
        List<String> lines = Shortcuts.load(this);
        int line = -1;
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).startsWith(pkg + "|")) line = i;
        if (line < 0) {
            lines.add(Shortcuts.lineFor(this, pkg));
            line = lines.size() - 1;
            Shortcuts.save(this, lines);
        }
        final int focus = line;
        main.post(() -> buildShortcuts(focus));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (moving >= 0) {
            boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (down) moveBy(-1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down) moveBy(1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                    if (!down) stopMoving();
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    return true;  // enquanto move, não sai da fileira
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    private void focusFirst() {
        if (shortcutRow.getChildCount() > 0) shortcutRow.getChildAt(0).requestFocus();
    }

    // ------------------------------------------------------------------ fileiras

    private void refreshIfStale() {
        long now = System.currentTimeMillis();
        if (!animeBusy && Net.age(this, "anime") > ANIME_TTL && now - animeTry > RETRY) {
            animeBusy = true;
            animeTry = now;
            io.execute(() -> {
                try {
                    String json = Net.postJson(Feeds.ANILIST, Feeds.ANIME_QUERY, 15000);
                    if (!Feeds.parseAnime(json).isEmpty()) {
                        Net.write(this, "anime", json);
                        main.post(() -> renderAnime(json));
                    }
                } catch (Exception ignored) {
                    // sem internet: fica o que já estava
                } finally {
                    main.post(() -> animeBusy = false);
                }
            });
        }
        String url = getString(R.string.feed_url);
        if (!url.isEmpty() && !ytBusy && Net.age(this, "feed") > YT_TTL && now - ytTry > RETRY) {
            ytBusy = true;
            ytTry = now;
            String full = url + "?token=" + Uri.encode(getString(R.string.feed_token));
            io.execute(() -> {
                try {
                    String json = Net.get(full, 6000);
                    Net.write(this, "feed", json);
                    main.post(() -> renderYoutube(json));
                } catch (Exception ignored) {
                    // PC desligado: mostra a última lista guardada
                } finally {
                    main.post(() -> ytBusy = false);
                }
            });
        }
    }

    private void renderAnime(String json) {
        if (json.equals(anime.rendered)) return;
        if (anime.root.hasFocus()) {
            anime.pending = json;
            return;
        }
        anime.rendered = json;
        fill(anime, Feeds.parseAnime(json), true);
    }

    private void renderYoutube(String json) {
        Section[] all = {crCont, hist, crList, rec, subs};
        String[] keys = {"cr_cont", "hist", "cr_list", "rec", "subs"};
        for (int i = 0; i < all.length; i++) {
            Section s = all[i];
            if (json.equals(s.rendered)) continue;
            if (s.root.hasFocus()) {
                s.pending = json;
                continue;
            }
            s.rendered = json;
            List<Feeds.Item> items = keys[i].startsWith("cr_")
                    ? Feeds.parseCrunchy(json, keys[i], 20)
                    : Feeds.parseYoutube(json, keys[i], s == hist ? 10 : 20);
            fill(s, items, false);
        }
    }

    private void fill(Section s, List<Feeds.Item> items, boolean poster) {
        s.row.removeAllViews();
        for (Feeds.Item it : items) {
            s.row.addView(poster ? posterCard(s, it) : videoCard(s, it));
        }
        s.root.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void snapTo(Section s) {
        scroll.smoothScrollTo(0, Math.max(0, s.root.getTop() - Ui.dp(6)));
    }

    /** Capa em pé (anime). */
    private View posterCard(Section s, Feeds.Item it) {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(Ui.round(Ui.CARD, 10));
        card.setForeground(Ui.focusRing(10));
        Ui.clipRound(card, 10);
        card.setFocusable(true);
        card.setClickable(true);
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        Images.load(img, it.image, Ui.dp(104));

        View fade = new View(this);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x00000000, 0xE6000000}));
        card.addView(fade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(Ui.dp(7), 0, Ui.dp(7), Ui.dp(7));
        TextView t = Ui.text(this, it.title, 11.5f, Ui.WHITE, Ui.bold);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        txt.addView(t);
        if (!it.badge.isEmpty()) {
            TextView b = Ui.text(this, it.badge, 10, 0xFFFFB37A, Ui.medium);
            b.setPadding(0, Ui.dp(3), 0, 0);
            txt.addView(b);
        }
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        card.addView(txt, tl);

        card.setOnClickListener(v -> openAnime(it.title));
        Ui.zoomOnFocus(card, 1.08f, (v, has) -> {
            if (has) snapTo(s);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(104), Ui.dp(148));
        lp.rightMargin = Ui.dp(12);
        card.setLayoutParams(lp);
        return card;
    }

    /** Miniatura deitada (YouTube) com título e canal embaixo. */
    private View videoCard(Section s, Feeds.Item it) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);

        FrameLayout thumb = new FrameLayout(this);
        thumb.setBackground(Ui.round(Ui.CARD, 10));
        thumb.setForeground(Ui.focusRing(10));
        Ui.clipRound(thumb, 10);
        thumb.setFocusable(true);
        thumb.setClickable(true);
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.addView(img, new FrameLayout.LayoutParams(-1, -1));
        Images.load(img, it.image, Ui.dp(172));
        if (!it.badge.isEmpty()) {
            TextView d = Ui.text(this, it.badge, 10, Ui.WHITE, Ui.medium);
            d.setBackground(Ui.round(0xCC000000, 4));
            d.setPadding(Ui.dp(5), Ui.dp(2), Ui.dp(5), Ui.dp(2));
            FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
            dl.setMargins(0, 0, Ui.dp(6), Ui.dp(6));
            thumb.addView(d, dl);
        }
        if (it.progress > 0.01f) {  // barrinha laranja de quanto já assistiu
            View track = new View(this);
            track.setBackgroundColor(0x66FFFFFF);
            thumb.addView(track, new FrameLayout.LayoutParams(-1, Ui.dp(3), Gravity.BOTTOM));
            View done = new View(this);
            done.setBackgroundColor(0xFFF47521);
            thumb.addView(done, new FrameLayout.LayoutParams(
                    Math.round(Ui.dp(172) * Math.min(1f, it.progress)), Ui.dp(3), Gravity.BOTTOM | Gravity.START));
        }
        thumb.setOnClickListener(v -> {
            if (it.crunchy) openEpisode(it.key); else openVideo(it.key);
        });
        Ui.zoomOnFocus(thumb, 1.07f, (v, has) -> {
            if (has) snapTo(s);
        });
        col.addView(thumb, new LinearLayout.LayoutParams(Ui.dp(172), Ui.dp(97)));

        TextView t = Ui.text(this, it.title, 12, Ui.WHITE, Ui.medium);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(Ui.dp(2), Ui.dp(8), Ui.dp(2), 0);
        col.addView(t, new LinearLayout.LayoutParams(Ui.dp(172), -2));
        TextView c = Ui.text(this, it.subtitle, 10.5f, Ui.TEXT3, Ui.regular);
        c.setSingleLine(true);
        c.setEllipsize(TextUtils.TruncateAt.END);
        c.setPadding(Ui.dp(2), Ui.dp(3), Ui.dp(2), 0);
        col.addView(c, new LinearLayout.LayoutParams(Ui.dp(172), -2));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(14);
        col.setLayoutParams(lp);
        return col;
    }

    // ------------------------------------------------------------------ abrir coisas

    private Intent launchIntent(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    private void launch(String pkg) {
        Intent i = launchIntent(pkg);
        if (i != null) start(i);
    }

    private boolean start(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void openAnime(String title) {
        // A busca da Crunchyroll não tem a categoria DEFAULT, então só abre chamando a tela direto.
        Intent search = new Intent(Intent.ACTION_SEARCH)
                .setClassName(CRUNCHYROLL, "com.crunchyroll.crunchyroid.main.ui.MainActivity")
                .putExtra(SearchManager.QUERY, title);
        if (!start(search)) launch(CRUNCHYROLL);
    }

    /** Abre o episódio direto no player da Crunchyroll, de onde parou. */
    private void openEpisode(String id) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("crunchyroll://episode/" + id)).setPackage(CRUNCHYROLL);
        if (!start(i)) launch(CRUNCHYROLL);
    }

    private void openVideo(String id) {
        Uri u = Uri.parse("https://www.youtube.com/watch?v=" + id);
        if (!start(new Intent(Intent.ACTION_VIEW, u).setPackage(YOUTUBE))) start(new Intent(Intent.ACTION_VIEW, u));
    }

    private void openLiveTv() {
        Intent i = launchIntent(LIVE_TV);
        if (i == null || !start(i)) start(new Intent(Settings.ACTION_SETTINGS));
    }

    private void openSettings() {
        start(new Intent(Settings.ACTION_SETTINGS));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Botão Menu do controle abre os ajustes da TV.
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            openSettings();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
