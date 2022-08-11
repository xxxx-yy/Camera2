package com.example.camera2;

import android.graphics.RectF;
import android.hardware.camera2.params.Face;

import java.util.ArrayList;

public interface FaceDetectListener {
    void onFaceDetect(Face[] faces, ArrayList<RectF> facesRect);
}
