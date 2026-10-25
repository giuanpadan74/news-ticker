package io.github.newsticker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The bar itself. Styles imitate the Italian TV tickers (Nero, Sky TG24, Rai News, TG4,
 * TG5, Studio Aperto): a solid logo block on the left, then headlines scrolling by with
 * separators in the style accent color. In two-line mode a top strip carries the
 * ULTIM'ORA feed behind its flag box while the main line keeps the weighted mix.
 * Branded styles force bold all-caps except Sky TG24, which keeps sentence case
 * (initial cap, names untouched). Redraws via the Choreographer while running.
 */
final class TickerView extends View {
    static final String[] STYLE_NAMES = {"Nero", "Sky TG24", "Rai News", "TG4", "TG5", "Studio Aperto"};
    private static final String[] LOGOS = {"NEWS", "sky tg24", "RaiNews", "TG4", "TG5", "APERTO"};
    /** Per style: {mainBg, mainText, sep, logoBg, logoTextColor, topBg, topText, flagBg, flagText} */
    private static final int[][] C = {
            {0xE0101010, 0xFFFFFFFF, 0xFFFFC93C, 0xFFFFC93C, 0xFF201510, 0xE03A0D0D, 0xFFFFFFFF, 0xFFD32F2E, 0xFFFFFFFF},
            {0xFFF4F4F4, 0xFF1A1A1A, 0xFFE30613, 0xFFF4F4F4, 0xFFFFFFFF, 0xFFFFFFFF, 0xFF1A1A1A, 0xFFE30613, 0xFFFFFFFF},
            {0xFFFFFFFF, 0xFF0050A9, 0xFF0050A9, 0xFFFFFFFF, 0xFF0050A9, 0xFF0050A9, 0xFFFFFFFF, 0xFFE2001A, 0xFFFFFFFF},
            {0xFFFFFFFF, 0xFF111111, 0xFF0064C8, 0xFF0064C8, 0xFFFFFFFF, 0xFF0064C8, 0xFFFFFFFF, 0xFFD81E2E, 0xFFFFFFFF},
            {0xFFFFFFFF, 0xFF14213D, 0xFFD81E2E, 0xFFD81E2E, 0xFFFFFFFF, 0xFFD81E2E, 0xFFFFFFFF, 0xFF14213D, 0xFFFFFFFF},
            {0xFF101010, 0xFFFFFFFF, 0xFFFFD200, 0xFFFFFFFF, 0xFF101010, 0xFFE4002B, 0xFFFFFFFF, 0xFFFFD200, 0xFF111111},
    };
    private static final String FLAG = "ULTIM'ORA";
    /** Official logo bitmaps per style; 0 = draw the text wordmark (Nero). */
    private static final int[] LOGO_RES = {
            0,
            R.drawable.logo_sky, R.drawable.logo_rainews, R.drawable.logo_tg4,
            R.drawable.logo_tg5, R.drawable.logo_studio,
    };

    private final int style;
    private final boolean two;
    private final float d;
    private final Paint fill = new Paint();
    private final Paint main = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint top = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint logo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flag = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();
    private Bitmap logoBmp;
    private float newsBlockW;
    private String[] segs = {};
    private float[] segW = {};
    private float total;
    private String[] breakSegs = {};
    private float[] breakSegW = {};
    private float breakTotal;
    private volatile boolean running;
    private long startNanos;

    TickerView(Context c, int styleIndex, boolean twoLines) {
        super(c);
        style = styleIndex;
        two = twoLines;
        d = c.getResources().getDisplayMetrics().density;
        Typeface tf = style == 0 ? Typeface.create("sans-serif", Typeface.NORMAL)
                : Typeface.create("sans-serif", Typeface.BOLD);
        main.setTypeface(tf);
        top.setTypeface(tf);
        logo.setTypeface(tf);
        flag.setTypeface(tf);
        main.setTextSize((two ? 15f : 17f) * d);
        top.setTextSize(11f * d);
        logo.setTextSize(11f * d);
        flag.setTextSize(10f * d);
        int[] s = C[style];
        main.setColor(s[1]);
        top.setColor(s[6]);
        logo.setColor(s[4]);
        int lr = LOGO_RES[style];
        if (lr != 0) logoBmp = BitmapFactory.decodeResource(getResources(), lr);
        newsBlockW = logo.measureText("NEWS") + 14 * d;   // reference width of the text logo block
    }

    void setContent(CharSequence s) {
        String src = s == null ? "" : s.toString();
        segs = src.isEmpty() ? new String[0] : src.split(Pattern.quote(Sources.SEP));
        for (int i = 0; i < segs.length; i++) segs[i] = caseFor(segs[i]);
        segW = new float[segs.length];
        total = 0;
        for (int i = 0; i < segs.length; i++) {
            segW[i] = main.measureText(segs[i]);
            total += segW[i] + 10 * d;
        }
        startNanos = System.nanoTime();
        invalidate();
    }

    void setBreaking(CharSequence s) {
        String src = s == null ? "" : s.toString();
        breakSegs = src.isEmpty() ? new String[0] : src.split(Pattern.quote(Sources.SEP));
        for (int i = 0; i < breakSegs.length; i++) breakSegs[i] = caseFor(breakSegs[i]);
        breakSegW = new float[breakSegs.length];
        breakTotal = 0;
        for (int i = 0; i < breakSegs.length; i++) {
            breakSegW[i] = top.measureText(breakSegs[i]);
            breakTotal += breakSegW[i] + 10 * d;
        }
        startNanos = System.nanoTime();
        invalidate();
    }

    /** Nero keeps the text as typed; the other branded skins force all-caps. */
    private String caseFor(String s) {
        if (style == 0) return s;
        if (style == 1) return sentenceCase(s);
        return s.toUpperCase(Locale.ROOT);
    }

    /**
     * Sky TG24 style: initial cap as sentence opens and names keep their own capitals
     * (the feed text flows through untouched), only a shouting all-caps title is
     * lowered to a sentence.
     */
    private static String sentenceCase(String s) {
        String t = s.trim();
        if (t.isEmpty()) return s;
        String base = t;
        String up = t.toUpperCase(Locale.ROOT);
        boolean hasLetter = false;
        for (int i = 0; i < t.length(); i++) {
            if (Character.isLetter(t.charAt(i))) { hasLetter = true; break; }
        }
        if (hasLetter && t.length() > 3 && t.equals(up)) {
            base = t.toLowerCase(Locale.ROOT);
        }
        return Character.toUpperCase(base.charAt(0)) + base.substring(1);
    }

    void animate(boolean run) {
        running = run;
        if (run) {
            startNanos = System.nanoTime();
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        int[] s = C[style];
        int topH = two ? (int) (22 * d) : 0;

        // bands, then the logo block over both lines
        fill.setColor(s[0]);
        canvas.drawRect(0, topH, w, h, fill);
        if (two) {
            fill.setColor(s[5]);
            canvas.drawRect(0, 0, w, topH, fill);
        }
        String lg = LOGOS[style];
        float logoW;
        if (logoBmp != null) {
            // the block never exceeds twice the NEWS text block; the logo letterboxes inside
            float maxW = 2 * newsBlockW;
            float scale = Math.min((h - 10 * d) / logoBmp.getHeight(),
                    (maxW - 12 * d) / logoBmp.getWidth());
            float bw = logoBmp.getWidth() * scale;
            float bh = logoBmp.getHeight() * scale;
            logoW = maxW;
            fill.setColor(s[3]);
            canvas.drawRect(0, 0, logoW, h, fill);
            dst.set((logoW - bw) / 2f, (h - bh) / 2f, (logoW + bw) / 2f, (h + bh) / 2f);
            canvas.drawBitmap(logoBmp, null, dst, null);
        } else {
            logoW = newsBlockW;
            fill.setColor(s[3]);
            canvas.drawRect(0, 0, logoW, h, fill);
            canvas.drawText(lg, 7 * d, h / 2f - (logo.descent() + logo.ascent()) / 2f, logo);
        }

        // main weighted line
        scroll(canvas, segs, segW, total, main, s[2],
                logoW + 10 * d, topH + 1 * d, w - 10 * d, h - 1 * d, 42 * d);

        // breaking strip: flag box + its own faster scroll
        if (two) {
            float fh = topH - 6 * d;
            float fw = flag.measureText(FLAG) + 12 * d;
            float fy = (topH - fh) / 2f;
            float fx = logoW + 6 * d;
            fill.setColor(s[7]);
            canvas.drawRect(fx, fy, fx + fw, fy + fh, fill);
            flag.setColor(s[8]);
            canvas.drawText(FLAG, fx + 6 * d, fy + fh / 2f - (flag.descent() + flag.ascent()) / 2f, flag);
            scroll(canvas, breakSegs, breakSegW, breakTotal, top, s[2],
                    fx + fw + 8 * d, 1 * d, w - 8 * d, topH - 1 * d, 55 * d);
        }

        if (running && isAttachedToWindow()) postInvalidateOnAnimation();
    }

    /** One scrolling line: segments pass by, each followed by a dot in the accent color. */
    private void scroll(Canvas canvas, String[] parts, float[] widths, float sum, Paint p,
                        int sepColor, float x0, float y0, float x1, float y1, float speed) {
        if (parts.length == 0 || sum <= 0 || x1 <= x0) return;
        int save = canvas.save();
        canvas.clipRect(x0, y0, x1, y1);
        float phase = -((System.nanoTime() - startNanos) / 1_000_000_000f * speed) % sum;
        if (phase > 0) phase -= sum;
        canvas.translate(x0, y0 + (y1 - y0) / 2f - (p.descent() + p.ascent()) / 2f);
        float x = phase;
        int i = 0;
        int guard = parts.length * 2 + 8;   // enough to cover the region once
        while (x < x1 - x0 && guard-- > 0) {
            canvas.drawText(parts[i], x, 0, p);
            x += widths[i] + 5 * d;
            fill.setColor(sepColor);
            canvas.drawCircle(x, 0, 2.2f * d, fill);
            x += 5 * d;
            i = (i + 1) % parts.length;
        }
        canvas.restore();
    }
}
