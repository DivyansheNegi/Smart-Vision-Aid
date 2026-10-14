package com.example.smartvisionaid;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

public class FaceRecognizer {
    private static final String TAG = "FaceRecognizer";
    private static final int SIZE = 112;
    private static final int EMBEDDING_DIM = 192;

    // Distance between L2-normalized embeddings. Same person is usually
    // ~0.5-0.9, different people ~1.1+. Watch the logcat distances and tune.
    private static final float MATCH_THRESHOLD = 1.1f;

    private Interpreter interpreter;
    private final SharedPreferences prefs;
    private final Map<String, float[]> enrolledFaces = new HashMap<>();

    public FaceRecognizer(Context context) {
        prefs = context.getSharedPreferences("faces", Context.MODE_PRIVATE);
        try {
            interpreter = new Interpreter(FileUtil.loadMappedFile(context, "mobilefacenet.tflite"));
            Log.d(TAG, "Model loaded. Output shape: "
                    + java.util.Arrays.toString(interpreter.getOutputTensor(0).shape()));
        } catch (Exception e) {
            Log.e(TAG, "mobilefacenet.tflite not found in assets", e);
        }
        loadEnrolled();
        Log.d(TAG, "Enrolled people loaded: " + enrolledFaces.keySet());
    }

    public boolean isReady() {
        return interpreter != null;
    }

    private float[] getEmbedding(Bitmap faceBitmap) {
        Bitmap resized = Bitmap.createScaledBitmap(faceBitmap, SIZE, SIZE, true);
        ByteBuffer input = ByteBuffer.allocateDirect(1 * SIZE * SIZE * 3 * 4);
        input.order(ByteOrder.nativeOrder());
        int[] pixels = new int[SIZE * SIZE];
        resized.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
        for (int p : pixels) {
            input.putFloat((((p >> 16) & 0xFF) - 127.5f) / 128f);
            input.putFloat((((p >> 8) & 0xFF) - 127.5f) / 128f);
            input.putFloat(((p & 0xFF) - 127.5f) / 128f);
        }
        input.rewind();

        float[][] output = new float[1][EMBEDDING_DIM];
        interpreter.run(input, output);
        return l2Normalize(output[0]);
    }

    private static float[] l2Normalize(float[] v) {
        float sum = 0;
        for (float f : v) sum += f * f;
        float norm = (float) Math.sqrt(sum);
        if (norm < 1e-10f) return v;
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i] / norm;
        return out;
    }

    /** Enroll (or improve) a person. Repeated enrolments are averaged. */
    public void enrollPerson(String name, Bitmap faceBitmap) {
        if (interpreter == null) {
            Log.e(TAG, "enrollPerson: model not loaded");
            return;
        }
        float[] newEmb = getEmbedding(faceBitmap);
        float[] existing = enrolledFaces.get(name);

        float[] finalEmb = newEmb;
        if (existing != null) {
            float[] avg = new float[EMBEDDING_DIM];
            for (int i = 0; i < EMBEDDING_DIM; i++) avg[i] = existing[i] + newEmb[i];
            finalEmb = l2Normalize(avg);
        }
        enrolledFaces.put(name, finalEmb);
        saveEmbedding(name, finalEmb);
        Log.d(TAG, "Enrolled: " + name + " (total people: " + enrolledFaces.size() + ")");
    }

    /** Returns the matched name, or null if no enrolled face is close enough. */
    public String recognize(Bitmap faceBitmap) {
        if (interpreter == null) {
            Log.e(TAG, "recognize: model not loaded");
            return null;
        }
        if (enrolledFaces.isEmpty()) {
            Log.d(TAG, "recognize: nobody enrolled yet");
            return null;
        }
        float[] embedding = getEmbedding(faceBitmap);
        String bestMatch = null;
        float bestDist = Float.MAX_VALUE;

        for (Map.Entry<String, float[]> entry : enrolledFaces.entrySet()) {
            float dist = euclideanDistance(embedding, entry.getValue());
            Log.d(TAG, "Distance to " + entry.getKey() + " = " + dist);
            if (dist < bestDist) {
                bestDist = dist;
                bestMatch = entry.getKey();
            }
        }
        boolean matched = bestMatch != null && bestDist < MATCH_THRESHOLD;
        Log.d(TAG, "Best: " + bestMatch + " dist=" + bestDist
                + " threshold=" + MATCH_THRESHOLD + " -> " + (matched ? "MATCH" : "NO MATCH"));
        return matched ? bestMatch : null;
    }

    private float euclideanDistance(float[] a, float[] b) {
        float sum = 0;
        for (int i = 0; i < a.length; i++) sum += (a[i] - b[i]) * (a[i] - b[i]);
        return (float) Math.sqrt(sum);
    }

    private void saveEmbedding(String name, float[] embedding) {
        StringBuilder sb = new StringBuilder();
        for (float f : embedding) sb.append(f).append(",");
        prefs.edit().putString(name, sb.toString()).apply();
    }

    private void loadEnrolled() {
        Map<String, ?> all = prefs.getAll();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            try {
                String[] parts = ((String) e.getValue()).split(",");
                float[] emb = new float[parts.length];
                for (int i = 0; i < parts.length; i++) emb[i] = Float.parseFloat(parts[i]);
                enrolledFaces.put(e.getKey(), l2Normalize(emb)); // also fixes old un-normalized saves
            } catch (Exception ex) {
                Log.e(TAG, "Failed to load enrolled face: " + e.getKey(), ex);
            }
        }
    }
}