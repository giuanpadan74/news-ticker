package io.github.newsticker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** iOS-style toggle switch drawn in code; the D-pad OK key clicks it like any button. */
final class SwitchView extends View {
    private static final int TRACK_ON = 0xFF30D158;    // iOS green
    private static final int TRACK_OFF = 0xFF48484A;
    private static final int THUMB = 0xFFFFFFFF;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumb = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private float pos;   // thumb position 0..1
    private boolean on;

    SwitchView(Context c) {
        super(c);
        setClickable(true);
        thumb.setColor(THUMB);
    }

    void setOn(boolean v) {
        on = v;
        pos = v ? 1f : 0f;
        invalidate();
    }

    boolean isOn() {
        return on;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        float d = getResources().getDisplayMetrics().density;
        setMeasuredDimension((int) (50 * d), (int) (30 * d));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float d = getResources().getDisplayMetrics().density;
        float r = getHeight() / 2f;
        box.set(0, 0, getWidth(), getHeight());
        track.setColor(on ? TRACK_ON : TRACK_OFF);
        canvas.drawRoundRect(box, r, r, track);
        float t = getHeight() - 6 * d;
        float x = 3 * d + pos * (getWidth() - t - 6 * d);
        canvas.drawCircle(x + t / 2f, getHeight() / 2f, t / 2f - d, thumb);
    }
}
