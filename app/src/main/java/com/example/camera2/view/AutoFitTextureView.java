package com.example.camera2.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;
import android.view.View;

public class AutoFitTextureView extends TextureView {
    private int mRatioW = 0;
    private int mRatioH = 0;

    public AutoFitTextureView(Context context) {
        super(context);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AutoFitTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setAspectRation(int width, int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("width or height can not be negative");
        }
        mRatioW = width;
        mRatioH = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = View.MeasureSpec.getSize(heightMeasureSpec);
        if (mRatioW == 0 || mRatioH == 0) {
            setMeasuredDimension(width, height);
        } else {
            if (width < height * mRatioW / mRatioH) {
                setMeasuredDimension(width, width * mRatioH / mRatioW);
            } else {
                setMeasuredDimension(height * mRatioW / mRatioH, height);
            }
        }
    }
}