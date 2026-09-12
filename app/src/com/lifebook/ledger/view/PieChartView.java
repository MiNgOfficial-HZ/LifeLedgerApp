package com.lifebook.ledger.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.lifebook.ledger.R;

import java.util.ArrayList;
import java.util.List;

/** 环形占比图 */
public class PieChartView extends View {

    public static class Slice {
        public String label;
        public long value;
        public int color;
    }

    private final List<Slice> slices = new ArrayList<>();
    private String centerLabel = "";
    private String centerValue = "";
    private float anim = 1f;

    public interface OnSliceTapListener {
        void onSliceTap(Slice s);
    }

    private OnSliceTapListener tapListener;

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PieChartView(Context context) {
        super(context);
    }

    public PieChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setData(List<Slice> data, String label, String value) {
        slices.clear();
        if (data != null) slices.addAll(data);
        centerLabel = label;
        centerValue = value;
        anim = 0f;
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(650);
        va.addUpdateListener(animation -> {
            anim = (Float) animation.getAnimatedValue();
            invalidate();
        });
        va.start();
    }

    public void setOnSliceTap(OnSliceTapListener l) {
        this.tapListener = l;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP && tapListener != null && !slices.isEmpty()) {
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            float radius = Math.min(w, h) / 2f - dp(10);
            float dx = e.getX() - cx;
            float dy = e.getY() - cy;
            float dist = (float) Math.hypot(dx, dy);
            if (dist >= radius * 0.60f && dist <= radius) {
                float ta = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 90 + 360) % 360);
                long total = 0;
                for (Slice s : slices) total += s.value;
                if (total > 0) {
                    float acc = 0f;
                    for (Slice s : slices) {
                        float sw = 360f * s.value / total;
                        if (sw <= 0) continue;
                        if (ta >= acc && ta < acc + sw) {
                            tapListener.onSliceTap(s);
                            break;
                        }
                        acc += sw;
                    }
                }
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float radius = Math.min(w, h) / 2f - dp(10);
        RectF rect = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);

        long total = 0;
        for (Slice s : slices) total += s.value;
        if (total <= 0 || slices.isEmpty()) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(sp(13));
            textPaint.setColor(getResources().getColor(R.color.icon_gray));
            canvas.drawText("暂无数据，先记一笔吧", cx, cy, textPaint);
            return;
        }
        float start = -90f;
        float gap = slices.size() > 1 ? 1.4f : 0f;
        for (Slice s : slices) {
            float sweep = 360f * s.value / total * anim;
            if (sweep <= 0) continue;
            arcPaint.setColor(s.color);
            canvas.drawArc(rect, start + gap / 2f, Math.max(0f, sweep - gap), true, arcPaint);
            start += sweep;
        }
        arcPaint.setColor(getResources().getColor(R.color.card_bg));
        canvas.drawCircle(cx, cy, radius * 0.60f, arcPaint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sp(12));
        textPaint.setColor(getResources().getColor(R.color.icon_gray));
        canvas.drawText(centerLabel, cx, cy - sp(3), textPaint);
        textPaint.setTextSize(sp(15));
        textPaint.setFakeBoldText(true);
        textPaint.setColor(getResources().getColor(R.color.text_main));
        canvas.drawText(centerValue, cx, cy + sp(16), textPaint);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }
}
