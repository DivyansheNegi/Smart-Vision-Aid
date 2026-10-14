package com.example.smartvisionaid;

import android.graphics.Bitmap;
import android.graphics.Rect;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.Collections;
import java.util.List;

public class FaceDetectorHelper {
    private final FaceDetector detector;

    public interface OnFacesDetected {
        void onDetected(List<Face> faces);
    }

    public FaceDetectorHelper() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build();
        detector = FaceDetection.getClient(options);
    }

    public void detect(Bitmap bitmap, OnFacesDetected callback) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        detector.process(image)
                .addOnSuccessListener(callback::onDetected)
                .addOnFailureListener(e -> callback.onDetected(Collections.emptyList()));
    }

    public static Bitmap cropFace(Bitmap bitmap, Rect box) {
        int left = Math.max(0, box.left);
        int top = Math.max(0, box.top);
        int right = Math.min(bitmap.getWidth(), box.right);
        int bottom = Math.min(bitmap.getHeight(), box.bottom);
        if (right <= left || bottom <= top) return bitmap;
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
    }
}