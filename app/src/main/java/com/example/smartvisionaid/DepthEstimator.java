package com.example.smartvisionaid;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Runs MiDaS-small for monocular relative depth estimation.
 * Output is a relative depth map (higher = closer), NOT meters.
 * Use the calibration constants below (fit from your own measurements)
 * to convert to an approximate real-world distance.
 */
public class DepthEstimator {
    private static final String TAG = "DepthEstimator";
    private static final int SIZE = 256;

    // TODO: replace these with values fit from YOUR calibration measurements
    // distance_m ≈ CALIB_A / (rawDepthValue + CALIB_B)
    private static final float CALIB_A = 120f;
    private static final float CALIB_B = 5f;

    private Interpreter interpreter;
    private final ByteBuffer inputBuffer;
    private final int[] pixelBuffer = new int[SIZE * SIZE];

    public DepthEstimator(Context context) {
        inputBuffer = ByteBuffer.allocateDirect(1 * SIZE * SIZE * 3 * 4);
        inputBuffer.order(ByteOrder.nativeOrder());
        try {
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            interpreter = new Interpreter(
                    FileUtil.loadMappedFile(context, "midas_small.tflite"), options);
        } catch (Exception e) {
            Log.e(TAG, "midas_small.tflite not found in assets", e);
        }
    }

    /** Returns a SIZE x SIZE relative depth map, or null if the model failed to load. */
    public float[][] estimateDepthMap(Bitmap bitmap) {
        if (interpreter == null) return null;

        Bitmap resized = Bitmap.createScaledBitmap(bitmap, SIZE, SIZE, true);
        resized.getPixels(pixelBuffer, 0, SIZE, 0, 0, SIZE, SIZE);

        inputBuffer.rewind();
        // ImageNet normalization, standard for MiDaS
        for (int pixel : pixelBuffer) {
            inputBuffer.putFloat((((pixel >> 16) & 0xFF) / 255f - 0.485f) / 0.229f);
            inputBuffer.putFloat((((pixel >> 8) & 0xFF) / 255f - 0.456f) / 0.224f);
            inputBuffer.putFloat(((pixel & 0xFF) / 255f - 0.406f) / 0.225f);
        }
        inputBuffer.rewind();

        float[][][] output = new float[1][SIZE][SIZE];
        interpreter.run(inputBuffer, output);
        return output[0];
    }

    /** Sample raw depth at a normalized (0-1) point in the frame. */
    public float getRawDepthAtPoint(float[][] depthMap, float normX, float normY) {
        if (depthMap == null) return -1f;
        int x = Math.max(0, Math.min(SIZE - 1, (int) (normX * SIZE)));
        int y = Math.max(0, Math.min(SIZE - 1, (int) (normY * SIZE)));
        return depthMap[y][x];
    }

    /** Convert a raw depth value to an approximate distance in meters using your calibration. */
    public float toMeters(float rawDepth) {
        if (rawDepth < 0) return -1f;
        return CALIB_A / (rawDepth + CALIB_B);
    }
}