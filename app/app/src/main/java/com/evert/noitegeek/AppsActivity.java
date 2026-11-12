package com.evert.noitegeek;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Todos os apps da TV numa grade, em ordem alfabética. */
public class AppsActivity extends Activity {
    private static final int COLS = 5;
    /** Aberto pelo "+ Adicionar": escolher um app devolve o pacote em PICKED. */
    static final String PICK = "pick";
    static final String PICKED = "pkg";
    private boolean pick;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        pick = getIntent().getBooleanExtra(PICK, false);
        PackageManager pm = getPackageManager();

        List<ResolveInfo> all = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                all.add(ri);
            }
        }
        Collator col = Collator.getInstance(new Locale("pt", "BR"));
        all.sort((a, b) -> col.compare(String.valueOf(a.loadLabel(pm)), String.valueOf(b.loadLabel(pm))));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xF20A0F1C);
        scroll.setClipChildren(false);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        page.setPadding(Ui.dp(48), Ui.dp(28), Ui.dp(48), Ui.dp(40));
        scroll.addView(page);
        page.addView(Ui.text(this, pick ? "Escolha um app pra tela inicial" : "Seus apps", 26, Ui.WHITE, Ui.bold));
        TextView hint = Ui.text(this, pick ? "OK adiciona o app nos atalhos coloridos"
                : "Segure OK num app pra colocar ele na tela inicial", 13, Ui.TEXT3, Ui.regular);
        hint.setPadding(0, Ui.dp(6), 0, 0);
        page.addView(hint);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(COLS);
        grid.setClipChildren(false);
        grid.setPadding(0, Ui.dp(18), 0, 0);
        page.addView(grid);

        for (ResolveInfo ri : all) grid.addView(tile(pm, ri));
        setContentView(scroll);
        if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
    }

    private View tile(PackageManager pm, ResolveInfo ri) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);

        FrameLayout box = new FrameLayout(this);
        box.setBackground(Ui.round(Ui.CARD, 10));
        box.setForeground(Ui.focusRing(10));
        Ui.clipRound(box, 10);
        box.setFocusable(true);
        box.setClickable(true);

        Drawable banner = ri.activityInfo.loadBanner(pm);
        if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
        ImageView img = new ImageView(this);
        if (banner != null) {
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageDrawable(banner);
            box.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            img.setImageDrawable(ri.loadIcon(pm));
            box.addView(img, new FrameLayout.LayoutParams(Ui.dp(48), Ui.dp(48), Gravity.CENTER));
        }
        String pkg = ri.activityInfo.packageName;
        String cls = ri.activityInfo.name;
        String label = String.valueOf(ri.loadLabel(pm));
        box.setOnLongClickListener(v -> {
            java.util.List<String> lines = Shortcuts.load(this);
            if (Shortcuts.contains(lines, pkg)) {
                android.widget.Toast.makeText(this, label + " já está na tela inicial", android.widget.Toast.LENGTH_SHORT).show();
            } else {
                lines.add(Shortcuts.lineFor(this, pkg));
                Shortcuts.save(this, lines);
                android.widget.Toast.makeText(this, label + " foi pra tela inicial", android.widget.Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        box.setOnClickListener(v -> {
            if (pick) {
                setResult(RESULT_OK, new Intent().putExtra(PICKED, pkg));
                finish();
                return;
            }
            try {
                startActivity(new Intent(Intent.ACTION_MAIN).setClassName(pkg, cls)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
                // app sumiu
            }
        });
        Ui.zoomOnFocus(box, 1.08f, null);
        col.addView(box, new LinearLayout.LayoutParams(Ui.dp(152), Ui.dp(86)));

        TextView t = Ui.text(this, String.valueOf(ri.loadLabel(pm)), 12, Ui.TEXT2, Ui.medium);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(Ui.dp(2), Ui.dp(7), Ui.dp(2), 0);
        col.addView(t, new LinearLayout.LayoutParams(Ui.dp(152), -2));

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.setMargins(0, 0, Ui.dp(18), Ui.dp(18));
        col.setLayoutParams(lp);
        return col;
    }
}
