package com.example.smartvisionaid;

public class DirectionHelper {
    public static String getDirection(Detection d) {
        float centerX = d.getCenterX(); // normalized 0..1
        if (centerX < 0.33f) return "on your left";
        if (centerX > 0.66f) return "on your right";
        return "ahead";
    }
}