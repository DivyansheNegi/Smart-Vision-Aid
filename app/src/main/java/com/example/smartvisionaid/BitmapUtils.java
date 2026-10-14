package com.example.smartvisionaid;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.util.Log;
import androidx.camera.core.ImageProxy;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class BitmapUtils {
    private static final String TAG = "BitmapUtils";

    public static Bitmap fromImageProxy(ImageProxy imageProxy) {
        try {
            int width  = imageProxy.getWidth();
            int height = imageProxy.getHeight();

            ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
            ByteBuffer yBuf  = planes[0].getBuffer();
            ByteBuffer uBuf  = planes[1].getBuffer();
            ByteBuffer vBuf  = planes[2].getBuffer();

            int yRowStride    = planes[0].getRowStride();
            int uvRowStride   = planes[1].getRowStride();
            int uvPixelStride = planes[1].getPixelStride();

            // Allocate exact NV21 size
            byte[] nv21 = new byte[width * height * 3 / 2];

            // Copy Y — strip row padding
            int pos = 0;
            for (int row = 0; row < height; row++) {
                yBuf.position(row * yRowStride);
                yBuf.get(nv21, pos, width);
                pos += width;
            }

            // Interleave V,U
            for (int row = 0; row < height / 2; row++) {
                for (int col = 0; col < width / 2; col++) {
                    int idx = row * uvRowStride + col * uvPixelStride;
                    nv21[pos++] = vBuf.get(idx);
                    nv21[pos++] = uBuf.get(idx);
                }
            }

            YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuv.compressToJpeg(new Rect(0, 0, width, height), 95, out);
            Bitmap bmp = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size());
            if (bmp == null) { Log.e(TAG, "decode returned null"); return null; }

            int rot = imageProxy.getImageInfo().getRotationDegrees();
            if (rot != 0) {
                Matrix m = new Matrix();
                m.postRotate(rot);
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            }
            return bmp;
        } catch (Exception e) {
            Log.e(TAG, "fromImageProxy failed", e);
            return null;
        }
    }
}