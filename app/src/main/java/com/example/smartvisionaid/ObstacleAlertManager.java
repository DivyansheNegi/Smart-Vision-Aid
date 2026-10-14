package com.example.smartvisionaid;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class ObstacleAlertManager {
    public static final float DANGER_DISTANCE_M = 1.0f;
    private static final long ALERT_COOLDOWN_MS = 1500;

    private final Vibrator vibrator;
    private long lastAlertTime = 0;

    public ObstacleAlertManager(Context context) {
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    public boolean shouldAlert(float distanceMeters) {
        if (distanceMeters < 0 || distanceMeters > DANGER_DISTANCE_M) return false;
        long now = System.currentTimeMillis();
        if (now - lastAlertTime < ALERT_COOLDOWN_MS) return false;
        lastAlertTime = now;
        return true;
    }

    public void vibrateAlert() {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(300);
        }
    }
}