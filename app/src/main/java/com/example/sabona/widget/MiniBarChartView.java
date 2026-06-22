package com.example.sabona.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Mali stubičasti ("bar") grafik sa N kategorija, svaka 0–100%.
 * Koristi se na ekranu Statistika za igre koje imaju VIŠE procenata
 * (Skočko: po pokušaju 1-2/3-4/5-6; Korak po korak: po koraku 1-7) —
 * gde jedan "donut" grafik ne bi imao smisla, ali raspodela po
 * stubićima jasno prikazuje obrazac.
 *
 * Laka, samostalna implementacija preko {@link Canvas#drawRoundRect} —
 * ne zahteva nikakvu eksternu biblioteku.
 */
public class MiniBarChartView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private int[] percents = new int[0]; // svaka vrednost 0–100
    private int barColor = 0xFF426BC2; // @color/blue kao default

    public MiniBarChartView(Context context) {
        super(context);
        init();
    }

    public MiniBarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MiniBarChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        trackPaint.setColor(0xFFE3E7F2); // @color/input_bg — "prazan" deo stubića
        barPaint.setColor(barColor);
    }

    /** Postavi vrednosti (0–100) koje grafik treba da prikaže, jedna po stubiću. */
    public void setPercents(int[] percents) {
        this.percents = percents != null ? percents.clone() : new int[0];
        for (int i = 0; i < this.percents.length; i++) {
            this.percents[i] = Math.max(0, Math.min(100, this.percents[i]));
        }
        invalidate();
    }

    /** Postavi boju stubića (npr. drugačija boja po igri, da se vizuelno razlikuju). */
    public void setBarColor(int color) {
        this.barColor = color;
        barPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (percents.length == 0) return;

        int w = getWidth();
        int h = getHeight();

        float gapPx = 4f * getResources().getDisplayMetrics().density;
        float totalGap = gapPx * (percents.length - 1);
        float barWidth = (w - totalGap) / percents.length;
        float radius = barWidth / 2.5f;

        for (int i = 0; i < percents.length; i++) {
            float left  = i * (barWidth + gapPx);
            float right = left + barWidth;

            // Pozadinska "praznina" stubića (puna visina, svetlo siva)
            rect.set(left, 0, right, h);
            canvas.drawRoundRect(rect, radius, radius, trackPaint);

            // Popunjeni deo, od dna, proporcionalan procentu
            float filledHeight = h * (percents[i] / 100f);
            rect.set(left, h - filledHeight, right, h);
            canvas.drawRoundRect(rect, radius, radius, barPaint);
        }
    }
}