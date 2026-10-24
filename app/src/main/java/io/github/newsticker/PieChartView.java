package io.github.newsticker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.List;

/**
 * Donut chart of the enabled feeds' percent shares, drawn straight on the Canvas so the
 * APK stays library-free. One slice per feed in palette color; a small angular gap keeps
 * the Material look. Static picture: no focus, no touch.
 */
final class PieChartView extends View {
    static final class Slice {
        final int color;
        final float percent;   // 0..100

        Slice(int color, float percent) {
            this.color = color;
            this.percent = percent;
        }
    }

    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint center = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private List<Slice> slices = List.of();
    private String centerText = "";

    PieChartView(Context c) {
        super(c);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.BUTT);
        center.setColor(0xFF8E8E93);
        center.setTextAlign(Paint.Align.CENTER);
    }

    void setSlices(List<Slice> s) {
        slices = s == null ? List.of() : s;
        invalidate();
    }

    void setCenterText(String s) {
        centerText = s == null ? "" : s;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float d = getResources().getDisplayMetrics().density;
        float avail = Math.min(getWidth(), getHeight()) - 4 * d;
        if (avail <= 0) return;
        float stroke = avail / 3.6f;
        arc.setStrokeWidth(stroke);
        // box = outer bounds of the ring; the stroke is centered on the path, so the
        // outer edge lands exactly on `avail` and never clips
        float ring = avail - stroke;
        float x0 = (getWidth() - ring) / 2f;
        float y0 = (getHeight() - ring) / 2f;
        box.set(x0, y0, x0 + ring, y0 + ring);

        float total = 0;
        for (Slice s : slices) total += s.percent;
        if (total <= 0) {                       // nothing enabled: muted empty ring
            arc.setColor(0x30FFFFFF);
            canvas.drawArc(box, 0, 360, false, arc);
            return;
        }
        float start = -90f;                     // start at 12 o'clock
        for (Slice s : slices) {
            float sweep = 360f * s.percent / total;
            arc.setColor(s.color);
            canvas.drawArc(box, start, Math.max(sweep - 2f, 0.5f), false, arc);
            start += sweep;
        }
        if (!centerText.isEmpty()) {
            center.setTextSize(12 * d);
            canvas.drawText(centerText, getWidth() / 2f,
                    getHeight() / 2f - (center.descent() + center.ascent()) / 2f, center);
        }
    }
}
