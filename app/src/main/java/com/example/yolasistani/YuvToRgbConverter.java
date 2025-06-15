package com.example.yolasistani;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.media.Image;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import androidx.camera.core.ImageProxy;
import java.nio.ByteBuffer;

public class YuvToRgbConverter {
    private final RenderScript rs;
    private final ScriptIntrinsicYuvToRGB scriptYuvToRgb;
    private Allocation yuvAllocation, rgbAllocation;
    private Bitmap rgbBitmap;
    private int width = -1;
    private int height = -1;

    public YuvToRgbConverter(Context context) {
        rs = RenderScript.create(context);
        scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
    }

    public synchronized void yuvToRgb(Image image, Bitmap outputBitmap) {
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Image format must be YUV_420_888");
        }

        if (image.getWidth() != width || image.getHeight() != height) {
            width = image.getWidth();
            height = image.getHeight();
            yuvAllocation = Allocation.createSized(rs, Element.U8(rs), width * height * 3 / 2);
            rgbAllocation = Allocation.createFromBitmap(rs, outputBitmap);
            rgbBitmap = outputBitmap;
        }

        ByteBuffer yuvBuffer = convertYUV420ToNV21(image);
        yuvAllocation.copyFrom(yuvBuffer.array());

        scriptYuvToRgb.setInput(yuvAllocation);
        scriptYuvToRgb.forEach(rgbAllocation);
        rgbAllocation.copyTo(outputBitmap);
    }

    private ByteBuffer convertYUV420ToNV21(Image imgYUV420) {
        ByteBuffer nv21Buffer;
        int width = imgYUV420.getWidth();
        int height = imgYUV420.getHeight();
        Image.Plane[] planes = imgYUV420.getPlanes();
        byte[] data = new byte[width * height * 3 / 2];

        int ySize = width * height;
        int uSize = ySize / 4;

        ByteBuffer yBuffer = planes[0].getBuffer(); // Y
        ByteBuffer uBuffer = planes[1].getBuffer(); // U
        ByteBuffer vBuffer = planes[2].getBuffer(); // V

        int rowStride = planes[0].getRowStride();
        assert (planes[0].getPixelStride() == 1);

        int pos = 0;

        if (rowStride == width) { // optimized case
            yBuffer.get(data, 0, ySize);
            pos += ySize;
        } else {
            for (int row = 0; row < height; row++) {
                yBuffer.position(row * rowStride);
                yBuffer.get(data, pos, width);
                pos += width;
            }
        }

        rowStride = planes[2].getRowStride();
        int pixelStride = planes[2].getPixelStride();

        for (int row = 0; row < height / 2; row++) {
            int vPos = row * rowStride;
            int uPos = row * planes[1].getRowStride();
            for (int col = 0; col < width / 2; col++) {
                data[pos++] = vBuffer.get(vPos);
                data[pos++] = uBuffer.get(uPos);
                vPos += pixelStride;
                uPos += planes[1].getPixelStride();
            }
        }

        nv21Buffer = ByteBuffer.wrap(data);
        return nv21Buffer;
    }
    private Bitmap adjustContrast(Bitmap bitmap, float contrast) {
        Bitmap result = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());

        for (int x = 0; x < bitmap.getWidth(); x++) {
            for (int y = 0; y < bitmap.getHeight(); y++) {
                int pixel = bitmap.getPixel(x, y);

                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);

                // Kontrast formülü
                r = (int) (((((r / 255.0f) - 0.5f) * contrast) + 0.5f) * 255.0f);
                g = (int) (((((g / 255.0f) - 0.5f) * contrast) + 0.5f) * 255.0f);
                b = (int) (((((b / 255.0f) - 0.5f) * contrast) + 0.5f) * 255.0f);

                // Sınırlandır
                r = Math.min(255, Math.max(0, r));
                g = Math.min(255, Math.max(0, g));
                b = Math.min(255, Math.max(0, b));

                result.setPixel(x, y, Color.rgb(r, g, b));
            }
        }

        return result;
    }

}
