package org.fox.ttrss.util;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.graphics.drawable.shapes.Shape;

import androidx.annotation.NonNull;

public final class ArticleInitialDrawable extends ShapeDrawable {
    private static final int[] COLORS = {
            0xfff16364, 0xfff58559, 0xfff9a43e,
            0xffe4c62e, 0xff67bf74, 0xff59a2be,
            0xff2093cd, 0xffad62a7, 0xff805781
    };

    private final String m_text;
    private final Paint m_textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ArticleInitialDrawable(@NonNull String text, int color) {
        super(new OvalShape());
        m_text = text;
        getPaint().setColor(color);
        m_textPaint.setColor(Color.WHITE);
        m_textPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        m_textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public static ArticleInitialDrawable forTitle(@NonNull String title) {
        String initial = title.isEmpty() ? "?" : title.substring(0, 1).toUpperCase();
        int colorIndex = (int) (Math.abs((long) title.hashCode()) % COLORS.length);
        return new ArticleInitialDrawable(initial, COLORS[colorIndex]);
    }

    @Override
    protected void onDraw(Shape shape, Canvas canvas, Paint paint) {
        super.onDraw(shape, canvas, paint);
        float width = getBounds().width();
        float height = getBounds().height();
        m_textPaint.setTextSize(Math.min(width, height) / 2);
        m_textPaint.setAlpha(paint.getAlpha());
        m_textPaint.setColorFilter(paint.getColorFilter());
        float baseline = (height - m_textPaint.ascent() - m_textPaint.descent()) / 2;
        canvas.drawText(m_text, width / 2, baseline, m_textPaint);
    }

    @Override
    public int getIntrinsicWidth() {
        return -1;
    }

    @Override
    public int getIntrinsicHeight() {
        return -1;
    }
}
