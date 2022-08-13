package com.example.camera2.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;

public class FaceView extends View {

    private final String TAG = "FaceView";
    private Paint mPaint;
    private ArrayList<RectF> mFaces;

    public FaceView(Context context) {
        super(context);
        init();
    }

    public FaceView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FaceView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void init() {
        Log.d(TAG, "init()");

        int mColor = 0xFFFF8811;
        mPaint = new Paint();
        mPaint.setColor(mColor);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(3f);
        mPaint.setAntiAlias(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

//        Log.d(TAG, "onDraw()");
        if (mFaces != null) {
            for (int i = 0; i < mFaces.size(); ++i) {
                canvas.drawRect(mFaces.get(i), mPaint);
            }
        }
    }

    public void setFaces(ArrayList<RectF> faces) {
        this.mFaces = faces;
        invalidate();
    }
}
