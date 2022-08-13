package com.example.camera2.mode;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.example.camera2.MainActivity;
import com.example.camera2.R;
import com.example.camera2.util.CameraUtil;
import com.example.camera2.view.AutoFitTextureView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

public class RecorderVideoFragment extends Fragment implements View.OnClickListener {
    private static final String TAG = "RecorderVideoFragment";

    private AutoFitTextureView textureView;
    private ImageButton recording;
    private ImageView mImageView;
    private ImageButton change;
    private Chronometer timer;
    private LinearLayout timerBg;
    private TextView videoQuality;
    private String quality = "480P";
    private CaptureRequest.Builder mPreviewCaptureRequestBuilder;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private String mCameraId = "";
    private CameraDevice mCameraDevice;
    private Size mPreviewSize;
    private CameraCaptureSession mCameraCaptureSession;
    private HandlerThread mHandlerThread;
    private Handler mChildHandler;
    private boolean isRecording = false;
    private MediaRecorder mMediaRecorder;
    private CameraManager mCameraManager;
    private final ArrayList<String> imageList = new ArrayList<>();
    private ImageView mask;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        Log.d(TAG, "onCreateView");

        View view = inflater.inflate(R.layout.fragment_recorder, container, false);

        initView(view);

        return view;
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");

        super.onResume();
        CameraUtil.setLastImagePath(mImageView);
        if (textureView.isAvailable()) {
            setUpCamera();
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");

        super.onPause();
        closeCamera();
    }

    private void initView(View view) {
        Log.d(TAG, "initView");

        textureView = view.findViewById(R.id.mTextureView);
        recording = view.findViewById(R.id.recordingBtn);
        mImageView = view.findViewById(R.id.mImageView);
        change = view.findViewById(R.id.mChange);
        timer = view.findViewById(R.id.timer);
        timerBg = view.findViewById(R.id.timerBg);
        ImageButton photoMode = view.findViewById(R.id.mPhotoMode);
        ImageButton recordingMode = view.findViewById(R.id.mRecordingMode);
        videoQuality = view.findViewById(R.id.videoQuality);
        mask = view.findViewById(R.id.videoMask);

        recording.setOnClickListener(this);
        mImageView.setOnClickListener(this);
        change.setOnClickListener(this);
        photoMode.setOnClickListener(this);
        recordingMode.setOnClickListener(this);
        videoQuality.setOnClickListener(this);
    }

    @SuppressLint("NonConstantResourceId")
    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.recordingBtn:
                if (isRecording) {
                    recording.setImageResource(R.drawable.recording);
                    stopRecorder();
                    isRecording = false;
                } else {
                    isRecording = true;
                    recording.setImageResource(R.drawable.stop_recording);
                    configSession();
                    startRecorder();
                }
                break;
            case R.id.mImageView:
                CameraUtil.openAlbum(getContext());
                break;
            case R.id.mChange:
                changeCamera();
                break;
            case R.id.photoMode:
                ((MainActivity) requireActivity()).photoMode();
                break;
            case R.id.recordingMode:
                ((MainActivity) requireActivity()).videoMode();
                break;
            case R.id.videoQuality:
                if (videoQuality.getText() == getString(R.string.quality480)) {
                    quality = getString(R.string.quality720);
                } else {
                    quality = getString(R.string.quality480);
                }
                videoQuality.setText(quality);
                mask.setVisibility(View.VISIBLE);
                closeCamera();
                setUpCamera();
                openCamera();
                break;
        }
    }

    private void initChildHandler() {
        mHandlerThread = new HandlerThread("HandlerThread");
        mHandlerThread.start();
        mChildHandler = new Handler(mHandlerThread.getLooper());
    }

    public void stopBackgroudThread() {
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            try {
                mHandlerThread.join();
                mHandlerThread = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable");

            setUpCamera();
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            Log.d(TAG, "onSurfaceTextureSizeChanged");
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            Log.d(TAG, "onSurfaceTextureDestroyed");
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
            Log.d(TAG, "onSurfaceTextureUpdated");
        }
    };

    private void setUpCamera() {
        Log.d(TAG, "setupCamera");

        mCameraManager = (CameraManager) requireActivity().getSystemService(Context.CAMERA_SERVICE);
        String tempId = "";
        if (!mCameraId.equals("")) {
            tempId = mCameraId;
        }
        try {
            for (String cameraId : mCameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
                mPreviewSize = getMatchingSize(sizes, quality);
                textureView.setAspectRation(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                mask.setLayoutParams(textureView.getLayoutParams());
                mCameraId = cameraId;
                if (!tempId.equals("")) {
                    mCameraId = tempId;
                }
                break;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        Log.d(TAG, "openCamera");

        initChildHandler();
        try {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            } else {
                mCameraManager.openCamera(mCameraId, stateCallback, null);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        stopPreview();
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mMediaRecorder != null) {
            mMediaRecorder.release();
            mMediaRecorder = null;
        }
        stopBackgroudThread();
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "CameraDevice.StateCallback： onOpened");

            mCameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.e(TAG, "CameraDevice.StateCallback： onDisconnected");
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int i) {
            Log.e(TAG, "CameraDevice.StateCallback： onError");
        }
    };

    private void startPreview() {
        Log.d(TAG, "startPreview");

        SurfaceTexture mSurfaceTexture = textureView.getSurfaceTexture();
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(mSurfaceTexture);
        try {
            mPreviewCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewCaptureRequestBuilder.addTarget(previewSurface);
            mCameraDevice.createCaptureSession(Collections.singletonList(previewSurface), sessionStateCallback, mChildHandler);
            Thread.sleep(450);
            mask.setVisibility(View.GONE);
        } catch (CameraAccessException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private Size getMatchingSize(Size[] sizes, String quality) {
        Size selectSize = null;
        for (Size itemSize: sizes) {
            if (quality.equals(getString(R.string.quality480))) {
                if ((itemSize.getWidth() == 640 && itemSize.getHeight() == 480) ||
                        (itemSize.getWidth() == 720 && itemSize.getHeight() == 480)) {
                    selectSize = itemSize;
                }
            } else {
                if ((itemSize.getWidth() == 1280 && itemSize.getHeight() == 720)) {
                    selectSize = itemSize;
                }
            }
        }

        Log.d(TAG, "getMatchingSize: 选择的分辨率宽度 = " + (selectSize != null ? selectSize.getHeight() : 0));
        Log.d(TAG, "getMatchingSize: 选择的分辨率高度 = " + (selectSize != null ? selectSize.getWidth() : 0));

        return selectSize;
    }

    private final CameraCaptureSession.StateCallback sessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            Log.d(TAG, "sessionStateCallback onConfigured");
            mCameraCaptureSession = cameraCaptureSession;
            updatePreview();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
            Log.e(TAG, "sessionStateCallback onConfigureFailed");

        }
    };

    private void updatePreview() {
        if (mCameraDevice == null) {
            return;
        }
        try {
            mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            mCameraCaptureSession.setRepeatingRequest(mPreviewCaptureRequestBuilder.build(), null, mChildHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void stopPreview() {
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
    }

    private void changeCamera() {
        Log.d(TAG, "changeCamera");

        ObjectAnimator anim = ObjectAnimator.ofFloat(change, "rotation", 0f, 180f);
        anim.setDuration(300);
        anim.start();
        if (mCameraId.equals(String.valueOf(CameraCharacteristics.LENS_FACING_BACK))) {
            Log.d(TAG, "前置转后置");
            mCameraId = String.valueOf(CameraCharacteristics.LENS_FACING_FRONT);
        } else {
            Log.d(TAG, "后置转前置");
            mCameraId = String.valueOf(CameraCharacteristics.LENS_FACING_BACK);
        }
        closeCamera();
        openCamera();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void configSession() {
        try {
            if (mCameraCaptureSession != null) {
                mCameraCaptureSession.stopRepeating();
                mCameraCaptureSession.close();
                mCameraCaptureSession = null;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        configMediaRecorder();
        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);
        Surface recorderSurface = mMediaRecorder.getSurface();
        try {
            mPreviewCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mPreviewCaptureRequestBuilder.addTarget(previewSurface);
            mPreviewCaptureRequestBuilder.addTarget(recorderSurface);
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, recorderSurface), mSessionStateCallback, mChildHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void configMediaRecorder() {
        @SuppressLint("SimpleDateFormat")
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        Date date = new Date(System.currentTimeMillis());
        File file = new File(Environment.getExternalStorageDirectory().toString() + "/DCIM/Camera/myVideo" + format.format(date) + ".mp4");
        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
        }
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setVideoSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setOutputFile(file.getAbsoluteFile());
        mMediaRecorder.setVideoEncodingBitRate(8 * 1024 * 1920);
        mMediaRecorder.setOrientationHint(90);
        if (mCameraId.equals("1")) {
            mMediaRecorder.setOrientationHint(270);
        }
        Surface surface = new Surface(textureView.getSurfaceTexture());
        mMediaRecorder.setPreviewDisplay(surface);
        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private final CameraCaptureSession.StateCallback mSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            Log.d(TAG, "mSessionStateCallback onConfigured");
            mCameraCaptureSession = session;
            updatePreview();
            try {
                mCameraCaptureSession.setRepeatingRequest(mPreviewCaptureRequestBuilder.build(), mSessionCaptureCallback, mChildHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.e(TAG, "mSessionStateCallback onConfigureFailed");

        }
    };

    private final CameraCaptureSession.CaptureCallback mSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            Log.d(TAG, "onCaptureStarted");
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            Log.d(TAG, "onCaptureProgressed");
            super.onCaptureProgressed(session, request, partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            Log.d(TAG, "onCaptureCompleted");
            super.onCaptureCompleted(session, request, result);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            Log.e(TAG, "onCaptureFailed");
            super.onCaptureFailed(session, request, failure);
        }
    };

    private void startRecorder() {
        ObjectAnimator scaleXAnim = ObjectAnimator.ofFloat(recording, "scaleX", 1f, 0.8f, 1f);
        ObjectAnimator scaleYAnim = ObjectAnimator.ofFloat(recording, "scaleY", 1f, 0.8f, 1f);
        AnimatorSet set = new AnimatorSet();
        set.play(scaleXAnim).with(scaleYAnim);
        set.setDuration(300);
        set.start();
        mMediaRecorder.start();
        startTime();
    }

    private void stopRecorder() {
        ObjectAnimator scaleXAnim = ObjectAnimator.ofFloat(recording, "scaleX", 1f, 0.8f, 1f);
        ObjectAnimator scaleYAnim = ObjectAnimator.ofFloat(recording, "scaleY", 1f, 0.8f, 1f);
        AnimatorSet set = new AnimatorSet();
        set.play(scaleXAnim).with(scaleYAnim);
        set.setDuration(300);
        set.start();
        if (mMediaRecorder != null) {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
            endTime();
        }
        CameraUtil.broadcast(requireActivity());
        CameraUtil.setLastImagePath(mImageView);
        startPreview();
    }

    private void startTime() {
        timerBg.setVisibility(View.VISIBLE);
        timer.setBase(SystemClock.elapsedRealtime());
        timer.start();
    }

    private void endTime() {
        timer.stop();
        timerBg.setVisibility(View.GONE);
        timer.setBase(SystemClock.elapsedRealtime());
    }
}
