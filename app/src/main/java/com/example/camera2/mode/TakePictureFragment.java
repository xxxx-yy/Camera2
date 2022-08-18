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
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.Surface;
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
import com.example.camera2.view.AutoFitTextureView;
import com.example.camera2.FaceDetectListener;
import com.example.camera2.view.FaceView;
import com.example.camera2.MainActivity;
import com.example.camera2.R;

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

    private final String[] permissions = {Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO};
    private final List<String> permissionList = new ArrayList();

    private View view;
    private AutoFitTextureView textureView;
    private ImageButton takePictureBtn;
    private ImageButton changeBtn;
    private ImageButton photoMode;
    private ImageButton recordingMode;
    private TextView ratioSelected;
    private PopupWindow ratio;
    private TextView ratio1_1;
    private TextView ratio4_3;
    private TextView ratioFull;
    private int width = 3;
    private int height = 4;
    private int deviceWidth;
    private int deviceHeight;
    private ImageButton delayBtn;
    private PopupWindow delayTimeWindow;
    private TextView noDelay;
    private TextView delay3;
    private TextView delay5;
    private TextView delay10;
    private int delayState = 0;
    private long delayTime = 0;
    private TextView countdown;
    private CountDownTimer countDownTimer;
    private Animation animation;
    private ImageButton mirror;
    private boolean mirrorFlag = true;
    private ImageView mImageView;
    private CameraManager mManager;
    private Size mPreviewSize;
    private Size mPhotoSize;
    private String mCameraId = "";
    private ImageReader mImageReader;
    private CameraCaptureSession mCaptureSession;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private final ArrayList<String> imageList = new ArrayList<>();

    private int rotation = 0;

    private final int SAVEIMAGE = 0;

    private int mFaceDetectMode;
    private float scaledWidth;
    private float scaledHeight;
    private float sizeScaledWidth;
    private float sizeScaledHeight;
    private final ArrayList<RectF> mFacesRect = new ArrayList<>();
    private FaceDetectListener mFaceDetectListener;
    private FaceView faceView;
    private ImageView mask;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        Log.d(TAG, "onCreateView");

        view = inflater.inflate(R.layout.fragment_take_picture, container, false);

        getPermission();

        initView(view);

        mManager = (CameraManager) requireActivity().getSystemService(Context.CAMERA_SERVICE);
        textureView.setSurfaceTextureListener(textureListener);

        clickEvents();

        return view;
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");

        super.onResume();
        getParentFragmentManager().setFragmentResultListener("videoModeData", this, new FragmentResultListener() {
            @Override
            public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle result) {
                mCameraId = result.getString("CamID");
            }
        });
        CameraUtil.setLastImagePath(mImageView);
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");

        super.onPause();
        Bundle result = new Bundle();
        result.putString("CamID", mCameraId);
        getParentFragmentManager().setFragmentResult("photoModeData", result);
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countdownEnd();
        }
        closeCamera();
    }

    //获取权限
    private void getPermission() {
        Log.d(TAG, "getPermission");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionList.add(permission);
                }
            }
            if (!permissionList.isEmpty()) {
                requestPermissions(permissionList.toArray(new String[0]), 1);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionResult");

        if (requestCode == 1 && grantResults.length > 0) {
            List<String> deniedPermissions = new ArrayList<>();

            for (int i = 0; i < grantResults.length; ++i) {
                int result = grantResults[i];
                String permission = permissions[i];
                if (result != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permission);
                }
            }
            if (deniedPermissions.isEmpty()) {
                openCamera();
            } else {
                getPermission();
            }
        }
    }

    private void initView(View view) {
        Log.d(TAG, "initView");

        textureView = view.findViewById(R.id.textureView);
        takePictureBtn = view.findViewById(R.id.takePhotoBtn);
        mImageView = view.findViewById(R.id.imageView);
        changeBtn = view.findViewById(R.id.change);
        photoMode = view.findViewById(R.id.photoMode);
        recordingMode = view.findViewById(R.id.recordingMode);
        countdown = view.findViewById(R.id.countdown);
        mirror = view.findViewById(R.id.mirror);
        initRatio();
        initDelayTime();
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        deviceWidth = displayMetrics.widthPixels;
        deviceHeight = displayMetrics.heightPixels;
        faceView = view.findViewById(R.id.faceView);
        mask = view.findViewById(R.id.photoMask);
    }

    @SuppressLint("InflateParams")
    private void initRatio() {
        ratioSelected = view.findViewById(R.id.ratio_selected);
        ratio = new PopupWindow();
        ratio.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        ratio.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        ratio.setFocusable(false);
        ratio.setOutsideTouchable(true);
        ratio.setBackgroundDrawable(new ColorDrawable(0x00000000));
        ratio.setContentView(LayoutInflater.from(getActivity()).inflate(R.layout.select_ratio, null));
        ratio.setFocusable(true);
        ratioSelected.setOnClickListener(v -> ratio.showAsDropDown(view.findViewById(R.id.ratio_selected), -120, 0));
        ratio1_1 = ratio.getContentView().findViewById(R.id.ratio_1_1);
        ratio4_3 = ratio.getContentView().findViewById(R.id.ratio_4_3);
        ratioFull = ratio.getContentView().findViewById(R.id.ratio_full);
    }

    @SuppressLint("InflateParams")
    private void initDelayTime() {
        delayBtn = view.findViewById(R.id.delay);
        delayTimeWindow = new PopupWindow();
        delayTimeWindow.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        delayTimeWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        delayTimeWindow.setFocusable(false);
        delayTimeWindow.setOutsideTouchable(true);
        delayTimeWindow.setBackgroundDrawable(new ColorDrawable(0x00000000));
        delayTimeWindow.setContentView(LayoutInflater.from(getActivity()).inflate(R.layout.select_delay_time, null));
        delayTimeWindow.setFocusable(true);
        delayBtn.setOnClickListener(v -> delayTimeWindow.showAsDropDown(view.findViewById(R.id.delay), -155, 0));
        noDelay = delayTimeWindow.getContentView().findViewById(R.id.noDelay);
        delay3 = delayTimeWindow.getContentView().findViewById(R.id.delay3);
        delay5 = delayTimeWindow.getContentView().findViewById(R.id.delay5);
        delay10 = delayTimeWindow.getContentView().findViewById(R.id.delay10);
    }

    private void clickEvents() {
        takePictureBtn.setOnClickListener(this);
        changeBtn.setOnClickListener(this);
        mImageView.setOnClickListener(this);
        photoMode.setOnClickListener(this);
        recordingMode.setOnClickListener(this);
        ratio1_1.setOnClickListener(this);
        ratio4_3.setOnClickListener(this);
        ratioFull.setOnClickListener(this);
        noDelay.setOnClickListener(this);
        delay3.setOnClickListener(this);
        delay5.setOnClickListener(this);
        delay10.setOnClickListener(this);
        mirror.setOnClickListener(this);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View view) {
        Log.d(TAG, "onClick");

        switch (view.getId()) {
            case R.id.takePhotoBtn:
                handleTakePhotoEvent();
                break;
            case R.id.change:
                changeCamera();
                break;
            case R.id.imageView:
                CameraUtil.openAlbum(getContext());
                break;
            case R.id.recordingMode:
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
                if (mirrorFlag) {
                    mirrorFlag = false;
                    mirror.setImageResource(R.drawable.mirror_off);
                } else {
                    mirrorFlag = true;
                    mirror.setImageResource(R.drawable.mirror_on);
                }
                break;
        }
    }

    private void handleTakePhotoEvent() {
        animation = AnimationUtils.loadAnimation(getContext(), R.anim.countdown_timer);
        MainActivity.touchEnabled = false;
        recordingMode.setClickable(false);
        if (delayState == 0) {
            takePhoto();
        } else {
            countDownTimer = new CountDownTimer(delayTime + 300, 1000) {
                @SuppressLint("SetTextI18n")
                @Override
                public void onTick(long millisUntilFinished) {
                    Log.d(TAG, "CountDownTimer-onTick");
                    photoMode.setVisibility(View.GONE);
                    recordingMode.setVisibility(View.GONE);
                    ratioSelected.setVisibility(View.GONE);
                    delayBtn.setVisibility(View.GONE);
                    mImageView.setVisibility(View.GONE);
                    takePictureBtn.setVisibility(View.GONE);
                    changeBtn.setVisibility(View.GONE);
                    mirror.setVisibility(View.GONE);

                    countdown.setText(millisUntilFinished / 1000 + "");
                    if (millisUntilFinished / 1000 == 0) {
                        countdown.setText("");
                    }
                    countdown.setVisibility(View.VISIBLE);
                    countdown.startAnimation(animation);
                }

                @Override
                public void onFinish() {
                    Log.d(TAG, "CountDownTimer-onFinish");
                    countdownEnd();
                }
            };
            countDownTimer.start();
        }
    }
    private void countdownEnd() {
        animation.setFillAfter(false);
        countdown.setVisibility(View.GONE);
        takePhoto();
        photoMode.setVisibility(View.VISIBLE);
        recordingMode.setVisibility(View.VISIBLE);
        ratioSelected.setVisibility(View.VISIBLE);
        delayBtn.setVisibility(View.VISIBLE);
        mImageView.setVisibility(View.VISIBLE);
        takePictureBtn.setVisibility(View.VISIBLE);
        changeBtn.setVisibility(View.VISIBLE);
        if (mCameraId.equals(String.valueOf(CameraCharacteristics.LENS_FACING_BACK))) {
            mirror.setVisibility(View.VISIBLE);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void handleRatio1_1() {
        MainActivity.touchEnabled = false;
        ratioSelected.setText(ratio1_1.getText());
        ratio1_1.setTextColor(ratioSelected.getTextColors());
        ratio4_3.setTextColor(Color.WHITE);
        ratioFull.setTextColor(Color.WHITE);
        ratio.dismiss();
        width = 1;
        height = 1;
        mask.setVisibility(View.VISIBLE);
        closeCamera();
        setupCamera();
        openCamera();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void handleRatio4_3() {
        MainActivity.touchEnabled = false;
        ratioSelected.setText(ratio4_3.getText());
        ratio4_3.setTextColor(ratioSelected.getTextColors());
        ratio1_1.setTextColor(Color.WHITE);
        ratioFull.setTextColor(Color.WHITE);
        ratio.dismiss();
        width = 3;
        height = 4;
        mask.setVisibility(View.VISIBLE);
        closeCamera();
        setupCamera();
        openCamera();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void handleRatioFull() {
        MainActivity.touchEnabled = false;
        ratioSelected.setText(ratioFull.getText());
        ratioFull.setTextColor(ratioSelected.getTextColors());
        ratio1_1.setTextColor(Color.WHITE);
        ratio4_3.setTextColor(Color.WHITE);
        ratio.dismiss();
        width = deviceWidth;
        height = deviceHeight;
        Log.d(TAG, "device++++++++width: " + width + ", height: " + height);
        mask.setVisibility(View.VISIBLE);
        closeCamera();
        setupCamera();
        openCamera();
    }

    private void handleNoDelay() {
        delayBtn.setImageResource(R.drawable.delay_off);
        noDelay.setTextColor(ratioSelected.getTextColors());
        delay3.setTextColor(Color.WHITE);
        delay5.setTextColor(Color.WHITE);
        delay10.setTextColor(Color.WHITE);
        delayState = 0;
        delayTime = 0;
        delayTimeWindow.dismiss();
        takePictureBtn.setImageResource(R.drawable.takephoto);
    }

    private void handleDelay3() {
        delayBtn.setImageResource(R.drawable.delay_on);
        delay3.setTextColor(ratioSelected.getTextColors());
        noDelay.setTextColor(Color.WHITE);
        delay5.setTextColor(Color.WHITE);
        delay10.setTextColor(Color.WHITE);
        delayState = 1;
        delayTime = 3000;
        delayTimeWindow.dismiss();
        takePictureBtn.setImageResource(R.drawable.delay_photo);
    }

    private void handleDelay5() {
        delayBtn.setImageResource(R.drawable.delay_on);
        delay5.setTextColor(ratioSelected.getTextColors());
        noDelay.setTextColor(Color.WHITE);
        delay3.setTextColor(Color.WHITE);
        delay10.setTextColor(Color.WHITE);
        delayState = 2;
        delayTime = 5000;
        delayTimeWindow.dismiss();
        takePictureBtn.setImageResource(R.drawable.delay_photo);
    }

    private void handleDelay10() {
        delayBtn.setImageResource(R.drawable.delay_on);
        delay10.setTextColor(ratioSelected.getTextColors());
        noDelay.setTextColor(Color.WHITE);
        delay3.setTextColor(Color.WHITE);
        delay5.setTextColor(Color.WHITE);
        delayState = 3;
        delayTime = 10000;
        delayTimeWindow.dismiss();
        takePictureBtn.setImageResource(R.drawable.delay_photo);
    }

    //TextureView回调
    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable");

            setupCamera();
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

    //配置相机
    @RequiresApi(api = Build.VERSION_CODES.N)
    private void setupCamera() {
        Log.d(TAG, "setupCamera");

        String tempId = "";
        if (!mCameraId.equals("")) {
            tempId = mCameraId;
        }
        try {
            for (String cameraId : mManager.getCameraIdList()) {
                CameraCharacteristics characteristics = mManager.getCameraCharacteristics(cameraId);

                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                mPreviewSize = CameraUtil.getOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height, deviceWidth, deviceHeight);
                mPhotoSize = CameraUtil.getMaxSize(map.getOutputSizes(SurfaceTexture.class), width, height, deviceWidth, deviceHeight);
                textureView.setAspectRation(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                textureView.setSurfaceTextureListener(textureListener);
                mask.setLayoutParams(textureView.getLayoutParams());
                mCameraId = cameraId;
                if (!tempId.equals("")) {
                    mCameraId = tempId;
                }

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
                scaledWidth = mPreviewSize.getWidth() / (float) activeArraySizeRect.width();
                scaledHeight = mPreviewSize.getHeight() / (float) activeArraySizeRect.height();
                float previewRatio = (float) mPreviewSize.getWidth() / mPreviewSize.getHeight();
                float activeArraySizeRectRatio = (float) activeArraySizeRect.width() / activeArraySizeRect.height();
                if (previewRatio != activeArraySizeRectRatio) {
                    sizeScaledWidth = sizeScaledHeight = Math.max(scaledWidth, scaledHeight);
                } else {
                    sizeScaledWidth = scaledWidth;
                    sizeScaledHeight = scaledHeight;
                }
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
        SurfaceTexture mSurfaceTexture = textureView.getSurfaceTexture();
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        Surface mPreviewSurface = new Surface(mSurfaceTexture);
        try {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(mPreviewSurface);
            if (mFaceDetectMode != CameraCharacteristics.STATISTICS_FACE_DETECT_MODE_OFF) {
                mPreviewRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, mFaceDetectMode);
            }
            mCameraDevice.createCaptureSession(Arrays.asList(mPreviewSurface, mImageReader.getSurface()), sessionStateCallback, null);
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

                MainActivity.touchEnabled = true;
                recordingMode.setClickable(true);
                Image image = reader.acquireNextImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
                ImageSaver imageSaver = new ImageSaver(bitmap, bytes);
                image.close();
//                if (bitmap != null && !bitmap.isRecycled()) {
//                    bitmap.recycle();
//                    bitmap = null;
//                }

                Bundle mBundle = new Bundle();
                mBundle.putSerializable("imageSaver", imageSaver);
                Message msg = Message.obtain();
                msg.setData(mBundle);
                msg.what = SAVEIMAGE;
                handler.sendMessage(msg);
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
//        Log.d(TAG, "repeatPreview");

        mPreviewRequestBuilder.setTag(TAG);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        CaptureRequest mPreviewRequest = mPreviewRequestBuilder.build();
        try {
            mCaptureSession.setRepeatingRequest(mPreviewRequest, captureCallback, null);
            Thread.sleep(500);
            mask.setVisibility(View.GONE);
            MainActivity.touchEnabled = true;
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

        ObjectAnimator scaleXAnim = ObjectAnimator.ofFloat(takePictureBtn, "scaleX", 1f, 0.8f, 1f);
        ObjectAnimator scaleYAnim = ObjectAnimator.ofFloat(takePictureBtn, "scaleY", 1f, 0.8f, 1f);
        AnimatorSet set = new AnimatorSet();
        set.play(scaleXAnim).with(scaleYAnim);
        set.setDuration(300);
        set.start();
        MediaActionSound sound = new MediaActionSound();
        sound.play(MediaActionSound.SHUTTER_CLICK);
        try {
            final CaptureRequest.Builder mCaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            mCaptureBuilder.addTarget(mImageReader.getSurface());
            if (mCameraId.equals(String.valueOf(CameraCharacteristics.LENS_FACING_BACK))) {
                mCaptureBuilder.set(CaptureRequest.JPEG_ORIENTATION, CameraUtil.FRONT_ORIENTATIONS.get(rotation));
            } else {
                mCaptureBuilder.set(CaptureRequest.JPEG_ORIENTATION, CameraUtil.BACK_ORIENTATIONS.get(rotation));
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

        MainActivity.touchEnabled = false;
        ObjectAnimator anim = ObjectAnimator.ofFloat(changeBtn, "rotation", 0f, 180f);
        anim.setDuration(1000);
        anim.start();
        if (mCameraId.equals(String.valueOf(CameraCharacteristics.LENS_FACING_BACK))) {
            Log.d(TAG, "前置转后置");
            mCameraId = String.valueOf(CameraCharacteristics.LENS_FACING_FRONT);
            mirror.setVisibility(View.GONE);
        } else {
            Log.d(TAG, "后置转前置");
            mCameraId = String.valueOf(CameraCharacteristics.LENS_FACING_BACK);
            mirror.setVisibility(View.VISIBLE);
        }
        closeCamera();
        openCamera();
    }

    class FaceDetect implements FaceDetectListener {
        @Override
        public void onFaceDetect(Face[] faces, ArrayList<RectF> facesRect) {
            faceView.setFaces(facesRect);
        }
    }

    public void setFaceDetectListener(FaceDetectListener listener) {
        this.mFaceDetectListener = listener;
    }

    private void handleFaces(TotalCaptureResult result) {
//        Log.d(TAG, "handleFaces");

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
            if (scaledWidth > scaledHeight) {
                rawFaceRect = new RectF((float) left * sizeScaledWidth, (float) top * scaledHeight, right * sizeScaledWidth, (float) top * scaledHeight + bounds.height() * sizeScaledHeight);
            } else {
                rawFaceRect = new RectF((float) left * scaledWidth, (float) top * sizeScaledHeight, left * scaledWidth + bounds.width() * sizeScaledWidth, (float) bottom * sizeScaledHeight);
            }

            RectF resultFaceRect;
            if (mCameraId.equals(String.valueOf(CameraCharacteristics.LENS_FACING_FRONT))) {
                resultFaceRect = new RectF(mPreviewSize.getHeight() - rawFaceRect.bottom, rawFaceRect.left + textureView.getTop(), mPreviewSize.getHeight() - rawFaceRect.top, rawFaceRect.right + textureView.getTop());
            } else {
                resultFaceRect = new RectF(mPreviewSize.getHeight() - rawFaceRect.bottom, mPreviewSize.getWidth() - rawFaceRect.right + textureView.getTop(), mPreviewSize.getHeight() - rawFaceRect.top, mPreviewSize.getWidth() - rawFaceRect.left + textureView.getTop());
            }
            Log.d("TextureView", "top: " + textureView.getTop());
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
        private final boolean flag = mirrorFlag;

        public ImageSaver(Bitmap bitmap, byte[] bytes) {
            Log.d(TAG, "ImageSaver");
            mBitmap = bitmap;
            mBytes = bytes;
        }

        @SuppressLint("HandlerLeak")
        @Override
        public void run() {
            if (flag && mCameraId.equals(String.valueOf(CameraCharacteristics.LENS_FACING_BACK))) {
                Matrix m = new Matrix();
                m.postScale(-1, 1);
                mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), m, true);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                mBytes = baos.toByteArray();
            }

            //TODO AndroidQ之后 保存图片路径
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
                imageList.add(path);
                CameraUtil.setLastImagePath(mImageView);
            }
        }
    }

    @SuppressLint("HandlerLeak")
    private final Handler handler = new Handler() {
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
        ObjectAnimator changeAnim = ObjectAnimator.ofFloat(changeBtn, "rotation", 0f, toValue);
        ObjectAnimator previewAnim = ObjectAnimator.ofFloat(mImageView, "rotation", 0f, toValue);
        ObjectAnimator buttonAnim = ObjectAnimator.ofFloat(takePictureBtn, "rotation", 0f, toValue);
        ObjectAnimator ratioAnim = ObjectAnimator.ofFloat(ratioSelected, "rotation", 0f, toValue);
        ObjectAnimator delayAnim = ObjectAnimator.ofFloat(delayBtn, "rotation", 0f, toValue);
        ObjectAnimator photoAnim = ObjectAnimator.ofFloat(photoMode, "rotation", 0f, toValue);
        ObjectAnimator videoAnim = ObjectAnimator.ofFloat(recordingMode, "rotation", 0f, toValue);
        ObjectAnimator mirrorAnim = ObjectAnimator.ofFloat(mirror, "rotation", 0f, toValue);
        ObjectAnimator countdownTime = ObjectAnimator.ofFloat(countdown, "rotation", 0f, toValue);
        AnimatorSet set = new AnimatorSet();
        set.play(changeAnim).with(previewAnim).with(buttonAnim).with(ratioAnim).with(delayAnim)
                .with(photoAnim).with(videoAnim).with(mirrorAnim).with(countdownTime);
        set.setDuration(300);
        set.start();
    }
}
