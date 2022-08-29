package com.example.camera2.mode;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ColorDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaActionSound;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;

import com.example.camera2.util.CameraUtil;
import com.example.camera2.FaceDetectListener;
import com.example.camera2.MainActivity;
import com.example.camera2.R;
import com.example.camera2.view.AutoFitTextureView;
import com.example.camera2.view.FaceView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class TakePictureFragment extends Fragment implements View.OnClickListener {
    private static final String TAG = "TakePictureFragment";
    private final String[] mPermissions = {Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO};
    private final List<String> mPermissionList = new ArrayList();
    private View mView;
    private AutoFitTextureView mTextureView;
    private ImageButton mTakePictureBtn;
    private ImageButton mChangeBtn;
    private ImageButton mPhotoMode;
    private ImageButton mRecordingMode;
    private TextView mRatioSelected;
    private PopupWindow mRatio;
    private TextView mRatio1_1;
    private TextView mRatio4_3;
    private TextView mRatioFull;
    private int mWidthRatio = 3;
    private int mHeightRatio = 4;
    private int mDeviceWidth;
    private int mDeviceHeight;
    private ImageButton mDelayBtn;
    private PopupWindow mDelayTimeWindow;
    private TextView mNoDelay;
    private TextView mDelay3;
    private TextView mDelay5;
    private TextView mDelay10;
    private int mDelayState = 0;
    private long mDelayTime = 0;
    private TextView mCountdown;
    private CountDownTimer mCountDownTimer;
    private Animation mAnimation;
    private ImageButton mMirror;
    private boolean mMirrorFlag = true;
    private ImageView mThumbnailView;
    private CameraManager mManager;
    private Size mPreviewSize;
    private Size mPhotoSize;
    private String mCameraId = "";
    private final String BACK_CAMERA_ID = "0";
    private final String FRONT_CAMERA_ID = "1";
    private boolean mBack = true;
    private ImageReader mImageReader;
    private CameraCaptureSession mCaptureSession;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private int mRotation = 0;
    private final int SAVEIMAGE = 0;
    private int mFaceDetectMode;
    private float mScaledWidth;
    private float mScaledHeight;
    private final ArrayList<RectF> mFacesRect = new ArrayList<>();
    private FaceDetectListener mFaceDetectListener;
    private FaceView mFaceView;
    private ImageView mMask;
    private HandlerThread mHandlerThread;
    private Handler mChildHandler;
    private Handler mMainHandler;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        Log.d(TAG, "onCreateView");
        mView = inflater.inflate(R.layout.fragment_take_picture, container, false);
        initView(mView);
        mManager = (CameraManager) requireActivity().getSystemService(Context.CAMERA_SERVICE);
        mTextureView.setSurfaceTextureListener(textureListener);
        getPermission();
        clickEvents();
        return mView;
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        getParentFragmentManager().setFragmentResultListener("videoModeData", this, new FragmentResultListener() {
            @Override
            public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle result) {
                mBack = result.getBoolean("BackCam");
            }
        });
        if (!mBack) {
            mMirror.setVisibility(View.VISIBLE);
        }
        mMainHandler = new Handler(Looper.getMainLooper());
        CameraUtil.getThumbnail(mThumbnailView, mMainHandler);
        if (mTextureView.isAvailable()) {
            setupCamera();
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        Bundle result = new Bundle();
        result.putBoolean("BackCam", mBack);
        getParentFragmentManager().setFragmentResult("photoModeData", result);
        if (mCountDownTimer != null) {
            mCountDownTimer.cancel();
            countdownEnd();
        }
        closeCamera();
    }

    //获取权限
    private void getPermission() {
        Log.d(TAG, "getPermission");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : mPermissions) {
                if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                    mPermissionList.add(permission);
                }
            }
            if (!mPermissionList.isEmpty()) {
                requestPermissions(mPermissionList.toArray(new String[0]), 1);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] mPermissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, mPermissions, grantResults);
        Log.d(TAG, "onRequestPermissionResult");
        if (requestCode == 1 && grantResults.length > 0) {
            List<String> deniedPermissions = new ArrayList<>();
            for (int i = 0; i < grantResults.length; ++i) {
                int result = grantResults[i];
                String permission = mPermissions[i];
                if (result != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permission);
                }
            }
            if (deniedPermissions.isEmpty()) {
                setupCamera();
                openCamera();
            } else {
                getPermission();
            }
        }
    }

    private void initView(View mView) {
        Log.d(TAG, "initView");
        mTextureView = mView.findViewById(R.id.textureView);
        mTakePictureBtn = mView.findViewById(R.id.takePhotoBtn);
        mThumbnailView = mView.findViewById(R.id.imageView);
        mChangeBtn = mView.findViewById(R.id.change);
        mPhotoMode = mView.findViewById(R.id.photoMode);
        mRecordingMode = mView.findViewById(R.id.recordingMode);
        mCountdown = mView.findViewById(R.id.countdown);
        mMirror = mView.findViewById(R.id.mirror);
        initRatio();
        initDelayTime();
        Point point = new Point();
        requireActivity().getWindowManager().getDefaultDisplay().getRealSize(point);
        mDeviceWidth = point.x;
        mDeviceHeight = point.y;
        mFaceView = mView.findViewById(R.id.faceView);
        mMask = mView.findViewById(R.id.photoMask);
    }

    @SuppressLint("InflateParams")
    private void initRatio() {
        mRatioSelected = mView.findViewById(R.id.ratio_selected);
        mRatio = new PopupWindow();
        mRatio.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        mRatio.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mRatio.setFocusable(false);
        mRatio.setOutsideTouchable(true);
        mRatio.setBackgroundDrawable(new ColorDrawable(0x00000000));
        mRatio.setContentView(LayoutInflater.from(getActivity()).inflate(R.layout.select_ratio, null));
        mRatio.setFocusable(true);
        mRatioSelected.setOnClickListener(v -> mRatio.showAsDropDown(mView.findViewById(R.id.ratio_selected), -90, 0));
        mRatio1_1 = mRatio.getContentView().findViewById(R.id.ratio_1_1);
        mRatio4_3 = mRatio.getContentView().findViewById(R.id.ratio_4_3);
        mRatioFull = mRatio.getContentView().findViewById(R.id.ratio_full);
    }

    @SuppressLint("InflateParams")
    private void initDelayTime() {
        mDelayBtn = mView.findViewById(R.id.delay);
        mDelayTimeWindow = new PopupWindow();
        mDelayTimeWindow.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        mDelayTimeWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mDelayTimeWindow.setFocusable(false);
        mDelayTimeWindow.setOutsideTouchable(true);
        mDelayTimeWindow.setBackgroundDrawable(new ColorDrawable(0x00000000));
        mDelayTimeWindow.setContentView(LayoutInflater.from(getActivity()).inflate(R.layout.select_delay_time, null));
        mDelayTimeWindow.setFocusable(true);
        mDelayBtn.setOnClickListener(v -> mDelayTimeWindow.showAsDropDown(mView.findViewById(R.id.delay), -140, 0));
        mNoDelay = mDelayTimeWindow.getContentView().findViewById(R.id.noDelay);
        mDelay3 = mDelayTimeWindow.getContentView().findViewById(R.id.delay3);
        mDelay5 = mDelayTimeWindow.getContentView().findViewById(R.id.delay5);
        mDelay10 = mDelayTimeWindow.getContentView().findViewById(R.id.delay10);
    }

    private void clickEvents() {
        mTakePictureBtn.setOnClickListener(this);
        mChangeBtn.setOnClickListener(this);
        mThumbnailView.setOnClickListener(this);
        mRecordingMode.setOnClickListener(this);
        mRatio1_1.setOnClickListener(this);
        mRatio4_3.setOnClickListener(this);
        mRatioFull.setOnClickListener(this);
        mNoDelay.setOnClickListener(this);
        mDelay3.setOnClickListener(this);
        mDelay5.setOnClickListener(this);
        mDelay10.setOnClickListener(this);
        mMirror.setOnClickListener(this);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View mView) {
        Log.d(TAG, "onClick");
        switch (mView.getId()) {
            case R.id.takePhotoBtn:
                handleTakePhotoEvent();
                break;
            case R.id.change:
                mChangeBtn.setClickable(false);
                changeCamera();
                break;
            case R.id.imageView:
                CameraUtil.openAlbum(getContext());
                break;
            case R.id.mRecordingMode:
                ((MainActivity) requireActivity()).videoMode();
                break;
            case R.id.ratio_1_1:
                handleRatio1_1();
                break;
            case R.id.ratio_4_3:
                handleRatio4_3();
                break;
            case R.id.ratio_full:
                handleRatioFull();
                break;
            case R.id.noDelay:
                handleNoDelay();
                break;
            case R.id.delay3:
                handleDelay3();
                break;
            case R.id.delay5:
                handleDelay5();
                break;
            case R.id.delay10:
                handleDelay10();
                break;
            case R.id.mirror:
                if (mMirrorFlag) {
                    mMirrorFlag = false;
                    mMirror.setImageResource(R.drawable.mirror_off);
                } else {
                    mMirrorFlag = true;
                    mMirror.setImageResource(R.drawable.mirror_on);
                }
                break;
        }
    }

    private void handleTakePhotoEvent() {
        mAnimation = AnimationUtils.loadAnimation(getContext(), R.anim.countdown_timer);
        MainActivity.mTouchEnabled = false;
        mRecordingMode.setClickable(false);
        if (mDelayState == 0) {
            takePhoto();
        } else {
            mCountDownTimer = new CountDownTimer(mDelayTime + 300, 1000) {
                @SuppressLint("SetTextI18n")
                @Override
                public void onTick(long millisUntilFinished) {
                    Log.d(TAG, "CountDownTimer-onTick");
                    mPhotoMode.setVisibility(View.GONE);
                    mRecordingMode.setVisibility(View.GONE);
                    mRatioSelected.setVisibility(View.GONE);
                    mDelayBtn.setVisibility(View.GONE);
                    mThumbnailView.setVisibility(View.GONE);
                    mTakePictureBtn.setVisibility(View.GONE);
                    mChangeBtn.setVisibility(View.GONE);
                    mMirror.setVisibility(View.GONE);
                    mCountdown.setText(millisUntilFinished / 1000 + "");
                    if (millisUntilFinished / 1000 == 0) {
                        mCountdown.setText("");
                    }
                    mCountdown.setVisibility(View.VISIBLE);
                    mCountdown.startAnimation(mAnimation);
                }

                @Override
                public void onFinish() {
                    Log.d(TAG, "CountDownTimer-onFinish");
                    countdownEnd();
                }
            };
            mCountDownTimer.start();
        }
    }

    private void countdownEnd() {
        mAnimation.setFillAfter(false);
        mCountdown.setVisibility(View.GONE);
        takePhoto();
        mPhotoMode.setVisibility(View.VISIBLE);
        mRecordingMode.setVisibility(View.VISIBLE);
        mRatioSelected.setVisibility(View.VISIBLE);
        mDelayBtn.setVisibility(View.VISIBLE);
        mThumbnailView.setVisibility(View.VISIBLE);
        mTakePictureBtn.setVisibility(View.VISIBLE);
        mChangeBtn.setVisibility(View.VISIBLE);
        if (mCameraId.equals(FRONT_CAMERA_ID)) {
            mMirror.setVisibility(View.VISIBLE);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void handleRatio1_1() {
        MainActivity.mTouchEnabled = false;
        mRatioSelected.setText(mRatio1_1.getText());
        mRatio1_1.setTextColor(mRatioSelected.getTextColors());
        mRatio4_3.setTextColor(Color.WHITE);
        mRatioFull.setTextColor(Color.WHITE);
        mRatio.dismiss();
        mWidthRatio = 1;
        mHeightRatio = 1;
        mMask.setVisibility(View.VISIBLE);
        closeCamera();
        setupCamera();
        openCamera();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void handleRatio4_3() {
        MainActivity.mTouchEnabled = false;
        mRatioSelected.setText(mRatio4_3.getText());
        mRatio4_3.setTextColor(mRatioSelected.getTextColors());
        mRatio1_1.setTextColor(Color.WHITE);
        mRatioFull.setTextColor(Color.WHITE);
        mRatio.dismiss();
        mWidthRatio = 3;
        mHeightRatio = 4;
        mMask.setVisibility(View.VISIBLE);
        closeCamera();
        setupCamera();
        openCamera();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void handleRatioFull() {
        MainActivity.mTouchEnabled = false;
        mRatioSelected.setText(mRatioFull.getText());
        mRatioFull.setTextColor(mRatioSelected.getTextColors());
        mRatio1_1.setTextColor(Color.WHITE);
        mRatio4_3.setTextColor(Color.WHITE);
        mRatio.dismiss();
        mWidthRatio = mDeviceWidth;
        mHeightRatio = mDeviceHeight;
        Log.d(TAG, "device++++++++mWidthRatio: " + mWidthRatio + ", mHeightRatio: " + mHeightRatio);
        mMask.setVisibility(View.VISIBLE);
        closeCamera();
        setupCamera();
        openCamera();
    }

    private void handleNoDelay() {
        mDelayBtn.setImageResource(R.drawable.delay_off);
        mNoDelay.setTextColor(mRatioSelected.getTextColors());
        mDelay3.setTextColor(Color.WHITE);
        mDelay5.setTextColor(Color.WHITE);
        mDelay10.setTextColor(Color.WHITE);
        mDelayState = 0;
        mDelayTime = 0;
        mDelayTimeWindow.dismiss();
        mTakePictureBtn.setImageResource(R.drawable.takephoto);
    }

    private void handleDelay3() {
        mDelayBtn.setImageResource(R.drawable.delay_on);
        mDelay3.setTextColor(mRatioSelected.getTextColors());
        mNoDelay.setTextColor(Color.WHITE);
        mDelay5.setTextColor(Color.WHITE);
        mDelay10.setTextColor(Color.WHITE);
        mDelayState = 1;
        mDelayTime = 3000;
        mDelayTimeWindow.dismiss();
        mTakePictureBtn.setImageResource(R.drawable.delay_photo);
    }

    private void handleDelay5() {
        mDelayBtn.setImageResource(R.drawable.delay_on);
        mDelay5.setTextColor(mRatioSelected.getTextColors());
        mNoDelay.setTextColor(Color.WHITE);
        mDelay3.setTextColor(Color.WHITE);
        mDelay10.setTextColor(Color.WHITE);
        mDelayState = 2;
        mDelayTime = 5000;
        mDelayTimeWindow.dismiss();
        mTakePictureBtn.setImageResource(R.drawable.delay_photo);
    }

    private void handleDelay10() {
        mDelayBtn.setImageResource(R.drawable.delay_on);
        mDelay10.setTextColor(mRatioSelected.getTextColors());
        mNoDelay.setTextColor(Color.WHITE);
        mDelay3.setTextColor(Color.WHITE);
        mDelay5.setTextColor(Color.WHITE);
        mDelayState = 3;
        mDelayTime = 10000;
        mDelayTimeWindow.dismiss();
        mTakePictureBtn.setImageResource(R.drawable.delay_photo);
    }

    //TextureView回调
    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int mWidthRatio, int mHeightRatio) {
            Log.d(TAG, "onSurfaceTextureAvailable");
            setupCamera();
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int mWidthRatio, int mHeightRatio) {
            Log.d(TAG, "onSurfaceTextureSizeChanged");
            Matrix matrix = CameraUtil.configureTransform(requireActivity(), mTextureView.getWidth(), mTextureView.getHeight(), mPreviewSize);
            mTextureView.setTransform(matrix);
            OrientationEventListener orientationEventListener = new OrientationEventListener(getContext()) {
                @Override
                public void onOrientationChanged(int orientation) {
                    if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
                        return;
                    }
                    int tempRotation = mRotation;
                    if (orientation > 315 || orientation < 45) {
                        mRotation = 0;
                    } else if (orientation > 225 && orientation < 315) {
                        mRotation = 1;
                    } else if (orientation > 135 && orientation < 225) {
                        mRotation = 2;
                    } else if (orientation > 45 && orientation < 135) {
                        mRotation = 3;
                    }
                    if (mRotation != tempRotation) {
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
        }
    };

    private void initChildHandler() {
        mHandlerThread = new HandlerThread("HandlerThread");
        mHandlerThread.start();
        mChildHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                Bundle bundle;
                if (msg.what == SAVEIMAGE) {
                    bundle = msg.getData();
                    ImageSaver imageSaver = (ImageSaver) bundle.getSerializable("imageSaver");
                    imageSaver.run();
                }
            }
        };
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

    //配置相机
    private void setupCamera() {
        Log.d(TAG, "setupCamera");
        try {
            for (String cameraId : mManager.getCameraIdList()) {
                CameraCharacteristics characteristics = mManager.getCameraCharacteristics(cameraId);
                if (mBack) {
                    if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                        continue;
                    }
                } else {
                    if (characteristics.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_FRONT) {
                        continue;
                    }
                }
                mCameraId = cameraId;
                Log.d(TAG, "mCameraId: " + mCameraId);
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                mPreviewSize = CameraUtil.getOptimalSize(map.getOutputSizes(SurfaceHolder.class), mWidthRatio, mHeightRatio, mDeviceWidth, mDeviceHeight);
                mPhotoSize = CameraUtil.getMaxSize(map.getOutputSizes(ImageFormat.JPEG), mWidthRatio, mHeightRatio, mDeviceWidth, mDeviceHeight);
                mTextureView.setAspectRation(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                mTextureView.setSurfaceTextureListener(textureListener);
                mMask.setLayoutParams(mTextureView.getLayoutParams());
                int[] faceDetectModes = characteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
                int faceDetectCount = characteristics.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT);
                for (int faceDetectMode : faceDetectModes) {
                    Log.d(TAG, "Face detect modes: " + faceDetectMode);
                    if (faceDetectMode == CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL) {
                        Log.d(TAG, "MODE_FULL: " + faceDetectMode);
                        mFaceDetectMode = faceDetectMode;
                        break;
                    } else if (faceDetectMode == CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE) {
                        Log.d(TAG, "MODE_SIMPLE: " + faceDetectMode);
                        mFaceDetectMode = faceDetectMode;
                    } else {
                        Log.d(TAG, "MODE_OFF: " + faceDetectMode);
                        mFaceDetectMode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF;
                    }
                }
                Log.d(TAG, "mFaceDetectMode: " + mFaceDetectMode);
                Log.d(TAG, "faceDetectCount: " + faceDetectCount);
                Rect activeArraySizeRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                mScaledWidth = mPreviewSize.getWidth() / (float) activeArraySizeRect.width();
                mScaledHeight = mPreviewSize.getHeight() / (float) activeArraySizeRect.height();
                Log.d(TAG, "成像区域  " + activeArraySizeRect.width() + "*" + activeArraySizeRect.height());
                Log.d(TAG, "预览区域  " + mPreviewSize.getWidth() + "*" + mPreviewSize.getHeight());
                break;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //打开相机
    private void openCamera() {
        Log.d(TAG, "openCamera");
        initChildHandler();
        try {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            } else {
                int rotation = requireActivity().getWindowManager().getDefaultDisplay().getOrientation();
                if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
                    mTextureView.setAspectRation(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                } else {
                    mTextureView.setAspectRation(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                }
                Matrix matrix = CameraUtil.configureTransform(requireActivity(), mTextureView.getWidth(), mTextureView.getHeight(), mPreviewSize);
                mTextureView.setTransform(matrix);
                mManager.openCamera(mCameraId, stateCallback, null);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        Log.d(TAG, "closeCamera");
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        stopBackgroudThread();
    }

    //打开相机回调
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

    //开启相机预览
    private void startPreview() {
        Log.d(TAG, "startPreview");
        setupImageReader();
        SurfaceTexture mSurfaceTexture = mTextureView.getSurfaceTexture();
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface mPreviewSurface = new Surface(mSurfaceTexture);
        try {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(mPreviewSurface);
            if (mFaceDetectMode != CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_OFF) {
                mPreviewRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, mFaceDetectMode);
            }
            mCameraDevice.createCaptureSession(Arrays.asList(mPreviewSurface, mImageReader.getSurface()), sessionStateCallback, mChildHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //获取摄像头的图像数据
    private void setupImageReader() {
        mImageReader = ImageReader.newInstance(mPhotoSize.getWidth(), mPhotoSize.getHeight(), ImageFormat.JPEG, 2);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Log.d(TAG, "图片已保存");
                MainActivity.mTouchEnabled = true;
                mRecordingMode.setClickable(true);
                Image image = reader.acquireNextImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);

                //拍照后更新缩略图
                BitmapFactory.Options thumbnailDecOption = new BitmapFactory.Options();
                thumbnailDecOption.inSampleSize = CameraUtil.calculateInSampleSize(mPhotoSize,
                        getResources().getDimensionPixelOffset(R.dimen.thumbnailWidth),
                        getResources().getDimensionPixelOffset(R.dimen.thumbnailHeight));
                Bitmap thumbnailBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, thumbnailDecOption);
                if (mMirrorFlag) {
                    Matrix m = new Matrix();
                    m.postScale(-1, 1);
                    thumbnailBitmap = Bitmap.createBitmap(thumbnailBitmap, 0, 0, thumbnailBitmap.getWidth(), thumbnailBitmap.getHeight(), m, true);
                }
                mThumbnailView.setImageBitmap(thumbnailBitmap);

                //保存照片
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
                ImageSaver imageSaver = new ImageSaver(bitmap, bytes);
                image.close();

                Bundle mBundle = new Bundle();
                mBundle.putSerializable("imageSaver", imageSaver);
                Message msg = Message.obtain();
                msg.setData(mBundle);
                msg.what = SAVEIMAGE;
                mChildHandler.sendMessage(msg);
            }
        }, null);
    }

    //CameraCaptureSession状态回调
    private final CameraCaptureSession.StateCallback sessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            Log.d(TAG, "onConfigured");
            mCaptureSession = session;
            repeatPreview();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.e(TAG, "onConfigureFailed");
        }
    };

    //设置不断重复预览
    private void repeatPreview() {
        mPreviewRequestBuilder.setTag(TAG);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        CaptureRequest mPreviewRequest = mPreviewRequestBuilder.build();
        try {
            mCaptureSession.setRepeatingRequest(mPreviewRequest, captureCallback, mChildHandler);
            Thread.sleep(500);
            mMainHandler.post(() -> mMask.setVisibility(View.GONE));
            mChangeBtn.setClickable(true);
            MainActivity.mTouchEnabled = true;
        } catch (CameraAccessException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    //CameraCaptureSession拍照回调
    private final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Log.d(TAG, "onCaptureCompleted");

            handleFaces(result);
        }
    };

    private void takePhoto() {
        Log.d(TAG, "takePhoto");
        CameraUtil.scaleAnim(mTakePictureBtn, 1f, 0.8f, 1f, 300).start();
        CameraUtil.playSound(MediaActionSound.SHUTTER_CLICK);
        try {
            final CaptureRequest.Builder mCaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            mCaptureBuilder.addTarget(mImageReader.getSurface());
            if (mCameraId.equals(FRONT_CAMERA_ID)) {
                mCaptureBuilder.set(CaptureRequest.JPEG_ORIENTATION, CameraUtil.FRONT_ORIENTATIONS.get(mRotation));
            } else {
                mCaptureBuilder.set(CaptureRequest.JPEG_ORIENTATION, CameraUtil.BACK_ORIENTATIONS.get(mRotation));
            }
            mCaptureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mCaptureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            mCaptureSession.capture(mCaptureBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void changeCamera() {
        Log.d(TAG, "changeCamera");
        mBack = !mBack;
        MainActivity.mTouchEnabled = false;
        ObjectAnimator anim = ObjectAnimator.ofFloat(mChangeBtn, "rotation", 0f, -180f);
        anim.setDuration(800);
        anim.start();
        if (mBack) {
            mMirror.setVisibility(View.GONE);
        } else {
            mMirror.setVisibility(View.VISIBLE);
        }
        closeCamera();
        setupCamera();
        openCamera();
    }

    class FaceDetect implements FaceDetectListener {
        @Override
        public void onFaceDetect(Face[] faces, ArrayList<RectF> facesRect) {
            mFaceView.setFaces(facesRect);
        }
    }

    public void setFaceDetectListener(FaceDetectListener listener) {
        this.mFaceDetectListener = listener;
    }

    private void handleFaces(TotalCaptureResult result) {
        Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
        mFacesRect.clear();
        for (Face face : faces) {
            Rect bounds = face.getBounds();
            int left = bounds.left;
            int right = bounds.right;
            int top = bounds.top;
            int bottom = bounds.bottom;
            Log.d("BEFORE", bounds.width() + "*" + bounds.height() + "    left: " + left + "  top: " + top +
                    "  right: " + right + " bottom: " + bottom);
            RectF rawFaceRect;
            float offset;
            if (mScaledWidth > mScaledHeight) {
                //Full
                offset = (bounds.width() * mScaledWidth - bounds.height() * mScaledHeight) / 2;
                rawFaceRect = new RectF((float) left * mScaledWidth, (float) top * mScaledHeight - offset, right * mScaledWidth, bottom * mScaledHeight + offset);
            } else {
                //4:3, 1:1
                offset = (bounds.height() * mScaledHeight - bounds.width() * mScaledWidth) / 2;
                rawFaceRect = new RectF((float) left * mScaledWidth - offset, (float) top * mScaledHeight, right * mScaledWidth + offset, (float) bottom * mScaledHeight);
            }

            RectF resultFaceRect;
            if (mCameraId.equals(BACK_CAMERA_ID)) {
                resultFaceRect = new RectF(mPreviewSize.getHeight() - rawFaceRect.bottom, rawFaceRect.left + mTextureView.getTop(), mPreviewSize.getHeight() - rawFaceRect.top, rawFaceRect.right + mTextureView.getTop());
            } else {
                resultFaceRect = new RectF(mPreviewSize.getHeight() - rawFaceRect.bottom, mPreviewSize.getWidth() - rawFaceRect.right + mTextureView.getTop(), mPreviewSize.getHeight() - rawFaceRect.top, mPreviewSize.getWidth() - rawFaceRect.left + mTextureView.getTop());
            }
            Log.d("TextureView", "top: " + mTextureView.getTop());
            Log.d("AFTER", resultFaceRect.width() + "*" + resultFaceRect.height() +
                    "    left: " + resultFaceRect.left + "  top: " + resultFaceRect.top +
                    "  right: " + resultFaceRect.right + "  bottom: " + resultFaceRect.bottom);
            mFacesRect.add(resultFaceRect);
        }
        setFaceDetectListener(new FaceDetect());
        mFaceDetectListener.onFaceDetect(faces, mFacesRect);
    }

    private class ImageSaver implements Runnable, Serializable {
        private Bitmap mBitmap;
        private byte[] mBytes;
        private final boolean flag = mMirrorFlag;

        public ImageSaver(Bitmap bitmap, byte[] bytes) {
            Log.d(TAG, "ImageSaver");
            mBitmap = bitmap;
            mBytes = bytes;
        }

        @SuppressLint("HandlerLeak")
        @Override
        public void run() {
            if (flag && mCameraId.equals(FRONT_CAMERA_ID)) {
                Matrix m = new Matrix();
                m.postScale(-1, 1);
                mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), m, true);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                mBytes = baos.toByteArray();
            }

            @SuppressLint("SimpleDateFormat")
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
            Date date = new Date(System.currentTimeMillis());
            String sDate = format.format(date);
            String path = Environment.getExternalStorageDirectory().toString() + "/DCIM/Camera/myPicture" + sDate + ".jpg";
            File imageFile = new File(path);
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(imageFile);
                fos.write(mBytes, 0, mBytes.length);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                CameraUtil.broadcast(requireActivity());
            }
        }
    }

    private void rotationAnim() {
        float toValue = 0;
        if (mRotation == 0) {
            toValue = 0;
        } else if (mRotation == 1) {
            toValue = 90;
        } else if (mRotation == 2) {
            toValue = 180;
        } else if (mRotation == 3) {
            toValue = -90;
        }
        ObjectAnimator changeAnim = ObjectAnimator.ofFloat(mChangeBtn, "rotation", 0f, toValue);
        ObjectAnimator previewAnim = ObjectAnimator.ofFloat(mThumbnailView, "rotation", 0f, toValue);
        ObjectAnimator buttonAnim = ObjectAnimator.ofFloat(mTakePictureBtn, "rotation", 0f, toValue);
        ObjectAnimator ratioAnim = ObjectAnimator.ofFloat(mRatioSelected, "rotation", 0f, toValue);
        ObjectAnimator delayAnim = ObjectAnimator.ofFloat(mDelayBtn, "rotation", 0f, toValue);
        ObjectAnimator photoAnim = ObjectAnimator.ofFloat(mPhotoMode, "rotation", 0f, toValue);
        ObjectAnimator videoAnim = ObjectAnimator.ofFloat(mRecordingMode, "rotation", 0f, toValue);
        ObjectAnimator mirrorAnim = ObjectAnimator.ofFloat(mMirror, "rotation", 0f, toValue);
        ObjectAnimator countdownTime = ObjectAnimator.ofFloat(mCountdown, "rotation", 0f, toValue);
        AnimatorSet set = new AnimatorSet();
        set.play(changeAnim).with(previewAnim).with(buttonAnim).with(ratioAnim).with(delayAnim)
                .with(photoAnim).with(videoAnim).with(mirrorAnim).with(countdownTime);
        set.setDuration(300);
        set.start();
    }
}
