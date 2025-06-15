package com.example.yolasistani;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class OverlayView extends View {

    private List<DetectionResult> results = new ArrayList<>();
    private final Paint boxPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint shadowPaint = new Paint();
    private int imageWidth = 300;  // Model input genişliği
    private int imageHeight = 300; // Model input yüksekliği

    private int previewHeight = 425; // TextureView yüksekliği (dp değil: px olarak gelecek)

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        boxPaint.setColor(Color.BLUE);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(13f);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(50f); // Daha büyük yazı
        textPaint.setStyle(Paint.Style.FILL_AND_STROKE); // Kalınlaştır
        textPaint.setStrokeWidth(2f); // Kenar vurgusu gibi

        shadowPaint.setColor(Color.WHITE);
        shadowPaint.setTextSize(50f); // Aynı şekilde büyüt
        shadowPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        shadowPaint.setStrokeWidth(3f); // Gölge daha kalın olsun
    }


    public void setResults(List<DetectionResult> results, int imageWidth, int imageHeight) {
        this.results = results;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (results == null || results.isEmpty()) return;

        int canvasWidth = getWidth();
        int textureHeightPx = dpToPx(getContext(), 425);
        int canvasHeight = getHeight();

        // Ortalamak için yukarıdan boşluk bırak
        float verticalOffset = (canvasHeight - textureHeightPx) / 2f;

        float scaleX = (float) canvasWidth / imageWidth;
        float scaleY = (float) textureHeightPx / imageHeight;

        for (DetectionResult result : results) {
            RectF bbox = result.getBoundingBox();

            float left = bbox.left * scaleX;
            float top = bbox.top * scaleY + verticalOffset;
            float right = bbox.right * scaleX;
            float bottom = bbox.bottom * scaleY + verticalOffset;

            RectF scaledBox = new RectF(left, top, right, bottom);
            canvas.drawRect(scaledBox, boxPaint);

            String labelText = result.getLabel() + " (" + Math.round(result.getScore() * 100) + "%)";
            canvas.drawText(labelText, scaledBox.left + 5, scaledBox.top - 10, shadowPaint);
            canvas.drawText(labelText, scaledBox.left + 4, scaledBox.top - 12, textPaint);
        }
    }


    private int dpToPx(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    public static class DetectionResult {
        private final RectF boundingBox;
        private final String label;
        private final float score;

        public DetectionResult(RectF boundingBox, String label, float score) {
            this.boundingBox = boundingBox;
            this.label = label;
            this.score = score;
        }

        public RectF getBoundingBox() {
            return boundingBox;
        }

        public String getLabel() {
            return label;
        }

        public float getScore() {
            return score;
        }
    }
}
