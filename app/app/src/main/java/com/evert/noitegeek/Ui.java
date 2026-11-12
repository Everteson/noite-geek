package com.evert.noitegeek;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ScrollView;
import android.widget.TextView;

/** Pequenos ajudantes de layout (tudo em dp; a TV tem 960x540 dp). */
final class Ui {
    private Ui() {}

    static final int WHITE = Color.WHITE;
    static final int TEXT2 = 0xFFB8C2D6;
    static final int TEXT3 = 0xFF8A94A8;
    static final int ACCENT = 0xFFFF7A1A;
    static final int CARD = 0xFF151C2E;

    static float density;
    static Typeface regular, medium, bold;

    static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
        regular = c.getResources().getFont(R.font.outfit_regular);
        medium = c.getResources().getFont(R.font.outfit_medium);
        bold = c.getResources().getFont(R.font.outfit_bold);
    }

    static int dp(float v) {
        return Math.round(v * density);
    }

    static TextView text(Context c, String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    static GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    /** Borda branca só quando focado (vai como foreground do cartão). */
    static Drawable focusRing(float radiusDp) {
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(Color.TRANSPARENT);
        ring.setCornerRadius(dp(radiusDp));
        ring.setStroke(dp(2.5f), WHITE);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, ring);
        s.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return s;
    }

    static void clipRound(View v, float radiusDp) {
        final int r = dp(radiusDp);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        v.setClipToOutline(true);
    }

    /** Cresce um pouco e sobe quando ganha foco; listener extra opcional. */
    static void zoomOnFocus(View v, float scale, View.OnFocusChangeListener extra) {
        v.setOnFocusChangeListener((view, has) -> {
            view.animate().scaleX(has ? scale : 1f).scaleY(has ? scale : 1f)
                    .translationZ(has ? dp(6) : 0).setDuration(140).start();
            if (extra != null) extra.onFocusChange(view, has);
        });
    }

    /** ScrollView que não rola sozinho atrás do foco: quem rola é a tela, fileira por fileira. */
    static final class SnapScroll extends ScrollView {
        public SnapScroll(Context c) {
            super(c);
        }

        public SnapScroll(Context c, AttributeSet a) {
            super(c, a);
        }

        @Override
        protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
            return 0;
        }
    }
}
