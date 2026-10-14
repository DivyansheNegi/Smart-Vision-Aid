package com.example.smartvisionaid;

import java.util.HashMap;
import java.util.Map;

public class DistanceEstimator {

    private static final Map<String, Float> REAL_WIDTH = new HashMap<>();
    static {
        REAL_WIDTH.put("person", 0.45f);
        REAL_WIDTH.put("chair", 0.45f);
        REAL_WIDTH.put("car", 1.8f);
        REAL_WIDTH.put("bicycle", 0.6f);
        REAL_WIDTH.put("motorcycle", 0.8f);
        REAL_WIDTH.put("bottle", 0.07f);
        REAL_WIDTH.put("cup", 0.08f);
        REAL_WIDTH.put("laptop", 0.35f);
        REAL_WIDTH.put("dog", 0.3f);
        REAL_WIDTH.put("cat", 0.25f);
        REAL_WIDTH.put("door", 0.9f);
        REAL_WIDTH.put("dining table", 1.0f);
        REAL_WIDTH.put("tv", 1.0f);
        REAL_WIDTH.put("pothole", 0.4f);
        REAL_WIDTH.put("staircase", 1.2f);
        REAL_WIDTH.put("manhole", 0.6f);
        REAL_WIDTH.put("pole", 0.15f);
    }
    private static final float DEFAULT_WIDTH = 0.4f;

    private static float focalLengthPx(int imageWidthPx) {
        double fovRad = Math.toRadians(65.0);
        return (float) ((imageWidthPx / 2.0) / Math.tan(fovRad / 2.0));
    }

    public static float estimateDistanceMeters(String label, float bboxWidthNorm, int imageWidthPx) {
        if (bboxWidthNorm <= 0.001f || imageWidthPx <= 0) return -1f;
        Float rw = REAL_WIDTH.get(label);
        float realWidth = rw != null ? rw : DEFAULT_WIDTH;
        float focalPx = focalLengthPx(imageWidthPx);
        float bboxWidthPx = bboxWidthNorm * imageWidthPx;
        return (realWidth * focalPx) / bboxWidthPx;
    }

    public static String describeDistance(float meters) {
        if (meters < 0) return "";
        if (meters < 0.6f) return "very close";
        if (meters < 1.5f) return "close";
        if (meters < 3.0f) return "nearby";
        return "far away";
    }
}