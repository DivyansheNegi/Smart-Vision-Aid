package com.example.smartvisionaid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class OverlayView extends View {

    private List<Detection> detections = new ArrayList<>();
    private String[] labels;

    private final Paint boxPaint;
    private final Paint textPaint;
    private final Paint textBgPaint;

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        boxPaint = new Paint();
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(8f);
        boxPaint.setAntiAlias(true);
        boxPaint.setAlpha(255);

        textPaint = new Paint();
        textPaint.setTextSize(44f);
        textPaint.setAntiAlias(true);
        textPaint.setColor(Color.WHITE);
        textPaint.setFakeBoldText(true);

        textBgPaint = new Paint();
        textBgPaint.setStyle(Paint.Style.FILL);
        textBgPaint.setAntiAlias(true);
        textBgPaint.setAlpha(200);
    }

    public void setResults(List<Detection> detections, String[] labels) {
        this.detections = detections != null ? detections : new ArrayList<>();
        this.labels = labels;
        invalidate();
    }

    // Overload to accept image dimensions (ignored now, kept for compatibility)
    public void setResults(List<Detection> detections, String[] labels,
                           int imageWidth, int imageHeight) {
        setResults(detections, labels);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (detections == null || detections.isEmpty()) return;

        float viewW = getWidth();
        float viewH = getHeight();

        int[] colors = {
                Color.RED, Color.GREEN, Color.CYAN,
                Color.YELLOW, Color.MAGENTA, 0xFFFF6600
        };

        for (Detection d : detections) {
            int color = colors[d.classId % colors.length];
            boxPaint.setColor(color);
            textBgPaint.setColor(color);


            float left   = d.x1 * viewW;
            float top    = d.y1 * viewH;
            float right  = d.x2 * viewW;
            float bottom = d.y2 * viewH;

            if (left >= right || top >= bottom) continue;

            canvas.drawRect(new RectF(left, top, right, bottom), boxPaint);

            String label = (labels != null && d.classId < labels.length)
                    ? labels[d.classId] : "class_" + d.classId;
            String text = label + " " + String.format("%.0f%%", d.confidence * 100);

            float textH = textPaint.getTextSize();
            float textW = textPaint.measureText(text);
            float labelTop = top - textH - 10f;
            if (labelTop < 0) labelTop = top + 4f;

            canvas.drawRect(left, labelTop,
                    left + textW + 16f, labelTop + textH + 10f, textBgPaint);
            canvas.drawText(text, left + 8f, labelTop + textH, textPaint);
        }
    }
}