package com.example.smartvisionaid;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class YoloDetector {
    public String[] getLabels() {
        return labels;
    }

    private Interpreter interpreter;
    private String[] labels;

    private static final String TAG = "YOLODEBUG";
    private static final int    SIZE  = 640;
    private static final float  CONF_THRESHOLD = 0.45f;
    private static final float  IOU_THRESHOLD  = 0.30f;
    private static final int    ANCHORS = 8400;

    private final ByteBuffer inputBuffer;
    private final ByteBuffer outputBuffer;
    private final int[]      pixelBuffer = new int[SIZE * SIZE];

    public YoloDetector(Context context) {
        // Labels must load BEFORE we size the output buffer, since the buffer
        // size depends on how many classes your model was trained on.
        loadLabels(context);

        inputBuffer  = ByteBuffer.allocateDirect(1 * SIZE * SIZE * 3 * 4);
        inputBuffer.order(ByteOrder.nativeOrder());
        outputBuffer = ByteBuffer.allocateDirect(1 * (4 + labels.length) * ANCHORS * 4);
        outputBuffer.order(ByteOrder.nativeOrder());

        loadModel(context);
    }

    private void loadModel(Context context) {
        try {
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            interpreter = new Interpreter(
                    FileUtil.loadMappedFile(context, "best_float32.tflite"), options);

            Log.d(TAG, "Input:  " + Arrays.toString(interpreter.getInputTensor(0).shape())
                    + " " + interpreter.getInputTensor(0).dataType());
            Log.d(TAG, "Output: " + Arrays.toString(interpreter.getOutputTensor(0).shape())
                    + " " + interpreter.getOutputTensor(0).dataType());

            ByteBuffer testIn = ByteBuffer.allocateDirect(1 * SIZE * SIZE * 3 * 4);
            testIn.order(ByteOrder.nativeOrder());
            for (int i = 0; i < SIZE * SIZE * 3; i++) testIn.putFloat(0.5f);
            testIn.rewind();

            ByteBuffer testOut = ByteBuffer.allocateDirect(1 * (4 + labels.length) * ANCHORS * 4);
            testOut.order(ByteOrder.nativeOrder());

            interpreter.run(testIn, testOut);
            testOut.rewind();

            float[] first5 = new float[5];
            for (int i = 0; i < 5; i++) first5[i] = testOut.getFloat();
            Log.d(TAG, "Self-test first 5 output values: " + Arrays.toString(first5));

        } catch (Exception e) {
            Log.e(TAG, "Model load failed", e);
        }
    }

    private void loadLabels(Context context) {
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(context.getAssets().open("labels.txt")))) {
            List<String> list = new ArrayList<>();
            String line;
            while ((line = r.readLine()) != null) {
                String t = line.trim();
                if (!t.isEmpty()) list.add(t);
            }
            labels = list.toArray(new String[0]);
            Log.d(TAG, "Labels loaded: " + labels.length);
        } catch (IOException e) {
            Log.e(TAG, "Labels load failed", e);
            labels = new String[0];
        }
    }

    public String getLabelForClass(int id) {
        if (labels != null && id >= 0 && id < labels.length) return labels[id];
        return "class_" + id;
    }

    public List<Detection> detect(Bitmap bitmap) {
        if (interpreter == null || labels.length == 0) return new ArrayList<>();

        Bitmap resized = Bitmap.createScaledBitmap(bitmap, SIZE, SIZE, true);
        resized.getPixels(pixelBuffer, 0, SIZE, 0, 0, SIZE, SIZE);

        inputBuffer.rewind();
        for (int pixel : pixelBuffer) {
            inputBuffer.putFloat(((pixel >> 16) & 0xFF) / 255f);
            inputBuffer.putFloat(((pixel >>  8) & 0xFF) / 255f);
            inputBuffer.putFloat(( pixel  & 0xFF) / 255f);
        }
        inputBuffer.rewind();

        outputBuffer.rewind();
        interpreter.run(inputBuffer, outputBuffer);
        outputBuffer.rewind();

        int numClasses = labels.length;
        int numRows = 4 + numClasses;
        float[][] out = new float[numRows][ANCHORS];
        for (int row = 0; row < numRows; row++)
            for (int col = 0; col < ANCHORS; col++)
                out[row][col] = outputBuffer.getFloat();

        List<Detection> candidates = new ArrayList<>();
        for (int a = 0; a < ANCHORS; a++) {
            float bestScore = 0f;
            int   bestClass = 0;
            for (int c = 0; c < numClasses; c++) {
                float s = out[4 + c][a];
                if (s > bestScore) { bestScore = s; bestClass = c; }
            }
            if (bestScore < CONF_THRESHOLD) continue;

            float cx = out[0][a];
            float cy = out[1][a];
            float w  = out[2][a];
            float h  = out[3][a];

            float x1 = clamp(cx - w / 2f);
            float y1 = clamp(cy - h / 2f);
            float x2 = clamp(cx + w / 2f);
            float y2 = clamp(cy + h / 2f);

            candidates.add(new Detection(x1, y1, x2, y2, bestScore, bestClass));
        }

        List<Detection> kept = nonMaxSuppression(candidates);
        Log.d(TAG, "Candidates: " + candidates.size() + "  After NMS: " + kept.size());
        return kept;
    }

    private List<Detection> nonMaxSuppression(List<Detection> dets) {
        dets.sort((a, b) -> Float.compare(b.confidence, a.confidence));
        boolean[]       suppressed = new boolean[dets.size()];
        List<Detection> kept       = new ArrayList<>();
        for (int i = 0; i < dets.size(); i++) {
            if (suppressed[i]) continue;
            kept.add(dets.get(i));
            for (int j = i + 1; j < dets.size(); j++)
                if (!suppressed[j] && iou(dets.get(i), dets.get(j)) > IOU_THRESHOLD)
                    suppressed[j] = true;
        }
        return kept;
    }

    private float iou(Detection a, Detection b) {
        float ix1 = Math.max(a.x1, b.x1), iy1 = Math.max(a.y1, b.y1);
        float ix2 = Math.min(a.x2, b.x2), iy2 = Math.min(a.y2, b.y2);
        float inter = Math.max(0, ix2-ix1) * Math.max(0, iy2-iy1);
        return inter / ((a.x2-a.x1)*(a.y2-a.y1) + (b.x2-b.x1)*(b.y2-b.y1) - inter + 1e-6f);
    }

    private float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
}