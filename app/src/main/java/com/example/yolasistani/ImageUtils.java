package com.example.yolasistani;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import androidx.camera.core.ImageProxy;

public class ImageUtils {

    private static YuvToRgbConverter yuvToRgbConverter;

    public static Bitmap imageProxyToBitmap(Context context, ImageProxy imageProxy) {
        if (yuvToRgbConverter == null) {
            yuvToRgbConverter = new YuvToRgbConverter(context);
        }

        Bitmap bitmap = Bitmap.createBitmap(imageProxy.getWidth(), imageProxy.getHeight(), Bitmap.Config.ARGB_8888);
        yuvToRgbConverter.yuvToRgb(imageProxy.getImage(), bitmap);

        // Döndürme işlemi
        Matrix matrix = new Matrix();
        matrix.postRotate(imageProxy.getImageInfo().getRotationDegrees());
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        return bitmap;
    }
}
