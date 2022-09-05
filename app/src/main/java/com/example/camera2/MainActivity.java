package com.example.camera2;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.media.MediaActionSound;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.example.camera2.mode.RecorderVideoFragment;
import com.example.camera2.mode.TakePictureFragment;
import com.example.camera2.util.CameraUtil;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "MainActivity";
    private float mDownX = 0;
    private float mDownY = 0;
    private int mCurrentMode = 0;    //0: photoMode, 1: videoMode
    public static boolean mTouchEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        init();
        fullScreen();
    }

    public void init() {
        TakePictureFragment fragment = new TakePictureFragment();
        getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, fragment).commit();
        mCurrentMode = 0;
        CameraUtil.loadSound(MediaActionSound.SHUTTER_CLICK);
        CameraUtil.loadSound(MediaActionSound.START_VIDEO_RECORDING);
        CameraUtil.loadSound(MediaActionSound.STOP_VIDEO_RECORDING);
    }

    public void fullScreen() {
        View decorView = getWindow().getDecorView();
        int option = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(option);
        getWindow().setStatusBarColor(0x00000000);
        getWindow().setNavigationBarColor(0x00000000);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mTouchEnabled) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mDownX = event.getX();
                mDownY = event.getY();
                Log.d("down", "x: " + mDownX);
            }
            if(event.getAction() == MotionEvent.ACTION_UP) {
                float upX = event.getX();
                float upY = event.getY();
                Log.d("up", "x: " + upX);
                if (Math.abs(mDownY - upY) < Math.abs(mDownX - upX)) {
                    if ((mDownX - upX > 100) && mCurrentMode == 0) {
                        mTouchEnabled = false;
                        videoMode();
                    } else if ((upX - mDownX > 100) && mCurrentMode == 1) {
                        mTouchEnabled = false;
                        photoMode();
                    }
                }
            }
        }
        return super.onTouchEvent(event);
    }

    public void videoMode() {
        Log.d(TAG, "changeToRecord");
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new RecorderVideoFragment()).commit();
        mCurrentMode = 1;
    }

    public void photoMode() {
        Log.d(TAG, "changeToTakePicture");
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new TakePictureFragment()).commit();
        mCurrentMode = 0;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CameraUtil.releaseSound();
    }
}