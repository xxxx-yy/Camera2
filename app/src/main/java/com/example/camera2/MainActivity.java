package com.example.camera2;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.example.camera2.mode.RecorderVideoFragment;
import com.example.camera2.mode.TakePictureFragment;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private float downX = 0;
    private float downY = 0;
    private int currentMode = 0;    //0: photoMode, 1: videoMode
    public static boolean touchEnabled = true;

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
        currentMode = 0;
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
        if (touchEnabled) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downX = event.getX();
                downY = event.getY();
                Log.d("down", "x: " + downX);
            }
            if(event.getAction() == MotionEvent.ACTION_UP) {
                float upX = event.getX();
                float upY = event.getY();
                Log.d("up", "x: " + upX);

                if (Math.abs(downY - upY) < Math.abs(downX - upX)) {
                    if ((downX - upX > 100) && currentMode == 0) {
                        videoMode();
                    } else if ((upX - downX > 100) && currentMode == 1) {
                        photoMode();
                    }
                }
            }
        }

        return super.onTouchEvent(event);
    }

    public void videoMode() {
        Log.d(TAG, "changeToRecord");

        RecorderVideoFragment fragment = new RecorderVideoFragment();
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
        currentMode = 1;
    }

    public void photoMode() {
        Log.d(TAG, "changeToTakePicture");

        TakePictureFragment fragment = new TakePictureFragment();
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
        currentMode = 0;
    }
}