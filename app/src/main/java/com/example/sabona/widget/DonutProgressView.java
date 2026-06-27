package com.example.sabona.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Mali kružni ("donut") grafik koji vizuelno prikazuje procenat uspešnosti
 * (0–100). Koristi se na ekranu Statistika (tab profila), pored brojčanog
 * prikaza procenta za svaku igru.
 *
 * Laka, samostalna implementacija preko {@link Canvas#drawArc} — ne
 * zahteva nikakvu eksternu biblioteku za grafike.
 */
public class DonutProgressView extends View {

    private static final float STROKE_WIDTH_DP = 6f;

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint       = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();

    private int percent = 0; // 0–100
    private int progressColor = 0xFF426BC2; // @color/blue kao default

    public DonutProgressView(Context context) {
        super(context);
        init();
    }

    public DonutProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DonutProgressView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float strokeWidthPx = STROKE_WIDTH_DP * getResources().getDisplayMetrics().density;

        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(strokeWidthPx);
        backgroundPaint.setColor(0xFFE3E7F2); // @color/input_bg

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidthPx);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(progressColor);

        textPaint.setColor(0xFF0B1957); // @color/dark_blue
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
    }

    /** Postavi procenat (0–100) koji grafik treba da prikaže. */
    public void setPercent(int percent) {
        this.percent = Math.max(0, Math.min(100, percent));
        invalidate();
    }

    /** Postavi boju luka (npr. drugačija boja po igri, da se vizuelno razlikuju). */
    public void setProgressColor(int color) {
        this.progressColor = color;
        progressPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float strokeWidthPx = STROKE_WIDTH_DP * getResources().getDisplayMetrics().density;
        float inset = strokeWidthPx / 2f + 1f;
        arcBounds.set(inset, inset, w - inset, h - inset);
        textPaint.setTextSize(h * 0.28f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Pozadinski (sivi) krug — kompletan, predstavlja "100%"
        canvas.drawArc(arcBounds, 0, 360, false, backgroundPaint);

        // Obojeni luk — predstavlja trenutni procenat, počinje od vrha (-90°)
        float sweepAngle = percent / 100f * 360f;
        canvas.drawArc(arcBounds, -90, sweepAngle, false, progressPaint);

        // Broj u centru
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f - (textPaint.ascent() + textPaint.descent()) / 2f;
        canvas.drawText(percent + "%", centerX, centerY, textPaint);
    }
}