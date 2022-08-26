package com.example.camera2.mode;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
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
import android.media.MediaActionSound;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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
    private ImageButton photoMode;
    private ImageButton recordingMode;
    private TextView videoQuality;
    private String quality = "480P";
    private CaptureRequest.Builder mPreviewCaptureRequestBuilder;

    private String mCameraId = "";
    private boolean back = true;
    private CameraDevice mCameraDevice;
    private Size mPreviewSize;
    private CameraCaptureSession mCameraCaptureSession;
    private HandlerThread mHandlerThread;
    private Handler mChildHandler;
    private Handler mMainHandler;
    private boolean isRecording = false;
    private MediaRecorder mMediaRecorder;
    private CameraManager mCameraManager;
    private ImageView mask;
    private int rotation = 0;
    private boolean pause = false;

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
        pause = false;
        getParentFragmentManager().setFragmentResultListener("photoModeData", this, (requestKey, result) -> back = result.getBoolean("BackCam"));
        initRecording();
        mMainHandler = new Handler(Looper.getMainLooper());
        CameraUtil.getThumbnail(mImageView, mMainHandler);
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
        pause = true;
        Bundle result = new Bundle();
        result.putBoolean("BackCam", back);
        getParentFragmentManager().setFragmentResult("videoModeData", result);
        stopRecorder();

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
        photoMode = view.findViewById(R.id.mPhotoMode);
        recordingMode = view.findViewById(R.id.mRecordingMode);
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
                    Log.d(TAG, Integer.parseInt((timer.getText() + "").replace(":", "")) + "");
                    int recordTime = Integer.parseInt((timer.getText() + "").replace(":", ""));
                    if (recordTime >= 1) {
                        CameraUtil.playSound(MediaActionSound.STOP_VIDEO_RECORDING);
                        initRecording();
                        MainActivity.touchEnabled = true;
                        stopRecorder();
                    } else {
                        Toast.makeText(getContext(), "请至少录制1秒", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    CameraUtil.playSound(MediaActionSound.START_VIDEO_RECORDING);
                    noRecording();
                    MainActivity.touchEnabled = false;
                }
                break;
            case R.id.mImageView:
                CameraUtil.openAlbum(getContext());
                break;
            case R.id.mChange:
                change.setClickable(false);
                changeCamera();
                break;
            case R.id.mPhotoMode:
                photoMode.setClickable(false);
                ((MainActivity) requireActivity()).photoMode();
                break;
            case R.id.videoQuality:
                if (videoQuality.getText().equals(getString(R.string.quality480))) {
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

    private void initRecording(){
        recording.setImageResource(R.drawable.recording);
        isRecording = false;
        photoMode.setVisibility(View.VISIBLE);
        recordingMode.setVisibility(View.VISIBLE);
        videoQuality.setVisibility(View.VISIBLE);
        mImageView.setVisibility(View.VISIBLE);
        change.setVisibility(View.VISIBLE);
//        MainActivity.touchEnabled = true;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void noRecording() {
//        MainActivity.touchEnabled = false;
        photoMode.setVisibility(View.GONE);
        recordingMode.setVisibility(View.GONE);
        videoQuality.setVisibility(View.GONE);
        mImageView.setVisibility(View.GONE);
        change.setVisibility(View.GONE);
        isRecording = true;
        recording.setImageResource(R.drawable.stop_recording);
        configSession();
        startRecorder();
    }

    private void initChildHandler() {
        mHandlerThread = new HandlerThread("HandlerThread");
        mHandlerThread.start();
        mChildHandler = new Handler(mHandlerThread.getLooper());
    }

    private void stopBackgroudThread() {
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
            Matrix matrix = CameraUtil.configureTransform(requireActivity(), textureView.getWidth(), textureView.getHeight(), mPreviewSize);
            textureView.setTransform(matrix);

            OrientationEventListener orientationEventListener = new OrientationEventListener(getContext()) {
                @Override
                public void onOrientationChanged(int orientation) {
//                    Log.d(TAG, "onOrientationChanged");
                    if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
                        return;
                    }
                    int tempRotation = rotation;
                    if (orientation > 315 || orientation < 45) {
                        rotation = 0;
                    } else if (orientation > 225 && orientation < 315) {
                        rotation = 1;
                    } else if (orientation > 135 && orientation < 225) {
                        rotation = 2;
                    } else if (orientation > 45 && orientation < 135) {
                        rotation = 3;
                    }
                    if (rotation != tempRotation) {
                        rotationAnim();
                    }
                }
            };
            if (orientationEventListener.canDetectOrientation()) {
                orientationEventListener.enable();
            } else {
                orientationEventListener.disable();
                Log.e(TAG, "当前设备不支持手机旋转！");
            }
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            Log.d(TAG, "onSurfaceTextureDestroyed");
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
//            Log.d(TAG, "onSurfaceTextureUpdated");
        }
    };

    private void setUpCamera() {
        Log.d(TAG, "setupCamera");

        mCameraManager = (CameraManager) requireActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : mCameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
                if (back) {
                    if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                        continue;
                    }
                } else {
                    if (characteristics.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_FRONT) {
                        continue;
                    }
                }
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
                mPreviewSize = getMatchingSize(sizes, quality);
                textureView.setAspectRation(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                mask.setLayoutParams(textureView.getLayoutParams());
                mCameraId = cameraId;
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
                int rotation = requireActivity().getWindowManager().getDefaultDisplay().getOrientation();
                if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
                    textureView.setAspectRation(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                } else {
                    textureView.setAspectRation(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                }
                Matrix matrix = CameraUtil.configureTransform(requireActivity(), textureView.getWidth(), textureView.getHeight(), mPreviewSize);
                textureView.setTransform(matrix);
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
            change.setClickable(true);
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
            MainActivity.touchEnabled = true;
            photoMode.setClickable(true);
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

        back = !back;
        ObjectAnimator anim = ObjectAnimator.ofFloat(change, "rotation", 0f, -180f);
        anim.setDuration(800);
        anim.start();
        closeCamera();
        setUpCamera();
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
        mMediaRecorder.setOrientationHint(CameraUtil.BACK_ORIENTATIONS.get(rotation));
        if (mCameraId.equals("1")) {
            mMediaRecorder.setOrientationHint(CameraUtil.FRONT_ORIENTATIONS.get(rotation));
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
        CameraUtil.scaleAnim(recording, 1f, 0.8f, 1f, 300).start();
        mMediaRecorder.start();
        startTime();
    }

    private void stopRecorder() {
        CameraUtil.scaleAnim(recording, 1f, 0.8f, 1f, 300).start();
        if (mMediaRecorder != null) {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
            mMediaRecorder = null;
        }
        endTime();
        CameraUtil.broadcast(requireActivity());
        CameraUtil.getThumbnail(mImageView, mMainHandler);
        if (!pause) {
            startPreview();
        }
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

    private void rotationAnim() {
        float toValue = 0;
        if (rotation == 0) {
            toValue = 0;
        } else if (rotation == 1) {
            toValue = 90;
        } else if (rotation == 2) {
            toValue = 180;
        } else if (rotation == 3) {
            toValue = -90;
        }
        ObjectAnimator changeAnim = ObjectAnimator.ofFloat(change, "rotation", 0f, toValue);
        ObjectAnimator previewAnim = ObjectAnimator.ofFloat(mImageView, "rotation", 0f, toValue);
        ObjectAnimator buttonAnim = ObjectAnimator.ofFloat(recording, "rotation", 0f, toValue);
        ObjectAnimator ratioAnim = ObjectAnimator.ofFloat(videoQuality, "rotation", 0f, toValue);
        ObjectAnimator photoAnim = ObjectAnimator.ofFloat(photoMode, "rotation", 0f, toValue);
        ObjectAnimator videoAnim = ObjectAnimator.ofFloat(recordingMode, "rotation", 0f, toValue);
        ObjectAnimator timerAnim = ObjectAnimator.ofFloat(timerBg, "rotation", 0f, toValue);
        AnimatorSet set = new AnimatorSet();
        set.play(changeAnim).with(previewAnim).with(buttonAnim).with(ratioAnim).with(photoAnim)
                .with(videoAnim).with(timerAnim);
        set.setDuration(300);
        set.start();
    }
}
