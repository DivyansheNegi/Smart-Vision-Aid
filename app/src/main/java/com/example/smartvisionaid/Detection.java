package com.example.smartvisionaid;

public class Detection {
    public float x1, y1, x2, y2;  // corner coordinates
    public float confidence;
    public int classId;

    public Detection(float x1, float y1, float x2, float y2, float confidence, int classId) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.confidence = confidence;
        this.classId = classId;
    }

    public float getCenterX() { return (x1 + x2) / 2f; }
    public float getCenterY() { return (y1 + y2) / 2f; }
    public float getWidth()   { return x2 - x1; }
    public float getHeight()  { return y2 - y1; }
}