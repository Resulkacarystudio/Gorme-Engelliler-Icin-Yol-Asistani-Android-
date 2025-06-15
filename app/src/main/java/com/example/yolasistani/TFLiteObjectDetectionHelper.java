package com.example.yolasistani;

import android.graphics.RectF;

public class TFLiteObjectDetectionHelper {

    public static class DetectionResult {
        public final RectF boundingBox;
        public final String label;
        public final float score;

        public DetectionResult(RectF boundingBox, String label, float score) {
            this.boundingBox = boundingBox;
            this.label = label;
            this.score = score;
        }
    }
}
