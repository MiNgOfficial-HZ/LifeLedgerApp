package com.lifebook.ledger.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.lifebook.ledger.R;
import com.lifebook.ledger.util.U;

/** 本月每日支出柱状图 */
public class BarChartView extends View {

    private long[] values = new long[31];
    private float anim = 1f;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BarChartView(Context context) {
        super(context);
    }

    public BarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setData(long[] v) {
        values = v != null ? v : new long[31];
        anim = 0f;
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(650);
        va.addUpdateListener(animation -> {
            anim = (Float) animation.getAnimatedValue();
            invalidate();
        });
        va.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float top = dp(16);
        float bottom = dp(18);
        float chartH = Math.max(1f, h - top - bottom);
        float baseY = h - bottom;

        long max = 0;
        int maxIdx = -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] > max) {
                max = values[i];
                maxIdx = i;
            }
        }
        if (max <= 0) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(sp(13));
            textPaint.setColor(getResources().getColor(R.color.icon_gray));
            canvas.drawText("本月暂无支出记录", w / 2f, h / 2f, textPaint);
            return;
        }
        linePaint.setColor(getResources().getColor(R.color.divider));
        linePaint.setStrokeWidth(dp(1));
        for (int g = 1; g <= 3; g++) {
            float y = top + chartH * g / 4f;
            canvas.drawLine(0, y, w, y, linePaint);
        }
        linePaint.setColor(getResources().getColor(R.color.hairline));
        canvas.drawLine(0, baseY, w, baseY, linePaint);

        float slot = w / 31f;
        float barW = slot * 0.62f;
        float gap = slot * 0.19f;
        for (int i = 0; i < 31; i++) {
            float hh = (float) values[i] / max * chartH * anim;
            if (hh <= 0) continue;
            RectF rf = new RectF(slot * i + gap, baseY - hh, slot * i + gap + barW, baseY);
            int barColor = i == maxIdx
                    ? getResources().getColor(R.color.primary)
                    : applyAlpha(getResources().getColor(R.color.primary), 100);
            barPaint.setColor(barColor);
            canvas.drawRoundRect(rf, barW / 2f, barW / 2f, barPaint);
        }
        if (maxIdx >= 0 && anim > 0.6f) {
            float x = slot * maxIdx + gap + barW / 2f;
            float y = top + chartH - (float) values[maxIdx] / max * chartH * anim - dp(4);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTextSize(sp(9));
            textPaint.setColor(getResources().getColor(R.color.primary));
            textPaint.setFakeBoldText(true);
            canvas.drawText(U.money(values[maxIdx]), x, y, textPaint);
        }
        textPaint.setFakeBoldText(false);
        textPaint.setTextSize(sp(9));
        textPaint.setColor(getResources().getColor(R.color.icon_gray));
        textPaint.setTextAlign(Paint.Align.CENTER);
        for (int d = 1; d <= 31; d += 5) {
            float x = slot * (d - 1) + gap + barW / 2f;
            canvas.drawText(String.valueOf(d), x, h - dp(4), textPaint);
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }

    private static int applyAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
