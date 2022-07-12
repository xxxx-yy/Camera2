package com.example.camera2;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.DisplayMetrics;
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

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

public class RecorderVideoFragment extends Fragment implements View.OnClickListener {
    private static final String TAG = "RecorderVideoFragment";

    private TextureView textureView;
    private ImageButton recording;
    private ImageView mImageView;
    private ImageButton change;
    private Chronometer timer;
    private LinearLayout timerBg;
    private CaptureRequest.Builder mPreviewCaptureRequestBuilder;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private String mCameraId;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraCaptureSession;
    private HandlerThread mHandlerThread;
    private Handler mChildHandler;
    private boolean isVisible = false;
    private boolean isRecording = false;
    private MediaRecorder mMediaRecorder;
    private CameraManager mCameraManager;
    private ArrayList<String> imageList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        Log.d(TAG, "onCreateView: success");

        View view = inflater.inflate(R.layout.fragment_recorder, container, false);

        initView(view);

        recording.setOnClickListener(this);
        mImageView.setOnClickListener(this);
        change.setOnClickListener(this);

        return view;
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            isVisible = true;
            Log.d(TAG, "setUserVisibleHint: true");
            setLastImagePath();
            initChildHandler();
            if (textureView.isAvailable()) {
                setUpCamera();
                openCamera();
            } else {
                textureView.setSurfaceTextureListener(textureListener);
            }
        } else {
            Log.d(TAG, TAG + " releaseCamera");
            isVisible = false;
            closeCamera();
            return;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isVisible) {
            setLastImagePath();
            initChildHandler();
            if (textureView.isAvailable()) {
                openCamera();
            } else {
                textureView.setSurfaceTextureListener(textureListener);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        closeCamera();
    }

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
                openAlbum();
                break;
            case R.id.mChange:
                changeCamera();
                break;
        }
    }

    private void initView(View view) {
        Log.d(TAG, "initView: success");

        textureView = view.findViewById(R.id.mTextureView);
        recording = view.findViewById(R.id.recordingBtn);
        mImageView = view.findViewById(R.id.mImageView);
        change = view.findViewById(R.id.mChange);
        timer = view.findViewById(R.id.timer);
        timerBg = view.findViewById(R.id.timerBg);
    }

    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable: success");

            setUpCamera();
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            Log.d(TAG, "onSurfaceTextureSizeChanged: success");
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            Log.d(TAG, "onSurfaceTextureDestroyed: success");
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
            Log.d(TAG, "onSurfaceTextureUpdated: success");
        }
    };

    private void setUpCamera() {
        Log.d(TAG, "setupCamera: success");

        mCameraManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : mCameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                mCameraId = cameraId;
                break;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        Log.d(TAG, "openCamera: success");

        try {
            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
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
        if (mHandlerThread != null) {
            stopBackgroudThread();
        }
    }

    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "CameraDevice.StateCallback： onOpened");

            mCameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.d(TAG, "CameraDevice.StateCallback： onDisconnected");
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int i) {
            Log.d(TAG, "CameraDevice.StateCallback： onError");
        }
    };

    private void setLastImagePath() {
        Log.d(TAG, "setLastImagePath: success");

        imageList = GetImageFilePath.getFilePath();
        if (imageList.isEmpty()) {
            mImageView.setImageResource(R.drawable.no_photo);
        } else {
            String path = imageList.get(imageList.size() - 1);
            if (path.contains(".jpg")) {
                setImageBitmap(path);
            } else {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(path);
                Bitmap bitmap = retriever.getFrameAtTime(1);
                mImageView.setImageBitmap(bitmap);
            }
        }
    }

    private void setImageBitmap(String path) {
        Log.d(TAG, "setImageBitmap: success");

        Bitmap bitmap = (Bitmap) BitmapFactory.decodeFile(path);
        mImageView.setImageBitmap(bitmap);
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

    private void startPreview() {
        Log.d(TAG, "startPreview: success");

        SurfaceTexture mSurfaceTexture = textureView.getSurfaceTexture();
        Size cameraSize = getMatchingSize();
        mSurfaceTexture.setDefaultBufferSize(cameraSize.getWidth(), cameraSize.getHeight());
        Surface previewSurface = new Surface(mSurfaceTexture);
        try {
            mPreviewCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewCaptureRequestBuilder.addTarget(previewSurface);
            mCameraDevice.createCaptureSession(Collections.singletonList(previewSurface), sessionStateCallback, mChildHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private Size getMatchingSize() {
        Size selectSize = null;
        try {
            CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            int deviceWidth = displayMetrics.widthPixels;
            int deviceHeight = displayMetrics.heightPixels;
            for (int j = 0; j < 40; ++j) {
                for (int i = 0; i < sizes.length; ++i) {
                    Size itemSize = sizes[i];
                    if (itemSize.getHeight() < (deviceWidth + j * 5) && itemSize.getHeight() > (deviceWidth - j * 5)) {
                        if (selectSize != null) {
                            if (Math.abs(deviceHeight - itemSize.getWidth()) < Math.abs(deviceHeight - selectSize.getWidth())) {
                                selectSize = itemSize;
                                continue;
                            }
                        } else {
                            selectSize = itemSize;
                        }
                    }
                }
                if (selectSize != null) {
                    break;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "getMatchingSize: 选择的分辨率宽度 = " +selectSize.getWidth());
        Log.d(TAG, "getMatchingSize: 选择的分辨率高度 = " +selectSize.getHeight());

        return selectSize;
    }

    private CameraCaptureSession.StateCallback sessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            mCameraCaptureSession = cameraCaptureSession;
            updatePreview();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
            Log.e(TAG, getActivity().getApplicationContext() + ": Faileedsa");

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
        Size cameraSize = getMatchingSize();
        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(cameraSize.getWidth(), cameraSize.getHeight());
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
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        Date date = new Date(System.currentTimeMillis());
        File file = new File(Environment.getExternalStorageDirectory().toString() + "/DCIM/camera/myVideo" + format.format(date) + ".mp4");
        if (file.exists()) {
            file.delete();
        }
        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
        }
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        Size size = getMatchingSize();
        mMediaRecorder.setVideoSize(size.getWidth(), size.getHeight());
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

    private CameraCaptureSession.StateCallback mSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
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

        }
    };

    private CameraCaptureSession.CaptureCallback mSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
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
        broadcast();
        setLastImagePath();
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

    private void broadcast() {
        Log.d(TAG, "broadcast: success");

        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/";
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri uri = Uri.fromFile(new File(path));
        intent.setData(uri);
        getActivity().sendBroadcast(intent);
    }

    private void openAlbum() {
        Log.d(TAG, "openAlbum: success");

        ArrayList<String> imgList = new ArrayList<>();
        imageList = GetImageFilePath.getFilePath();
        if (!imageList.isEmpty()) {
            Intent intent = new Intent();
            intent.setClass(getContext(), ImageShowActivity.class);
            startActivity(intent);
        }
    }

    private void changeCamera() {
        Log.d(TAG, "changeCamera: success");

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
        mCameraDevice.close();
        openCamera();
    }
}
