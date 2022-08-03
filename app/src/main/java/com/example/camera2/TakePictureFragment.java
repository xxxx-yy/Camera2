package com.example.camera2;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ColorDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class TakePictureFragment extends Fragment implements View.OnClickListener {
    private static final String TAG = "TakePictureFragment";

    private static final SparseIntArray FRONT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray BACK_ORIENTATIONS = new SparseIntArray();

    static {
        FRONT_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        FRONT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        FRONT_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        FRONT_ORIENTATIONS.append(Surface.ROTATION_270, 180);

        BACK_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        BACK_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        BACK_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        BACK_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private View view;
    private TextureView textureView;
    private ImageButton takePicture;
    private ImageButton change;
    private TextView photoMode;
    private TextView recordingMode;
    private TextView ratioSelected;
    private PopupWindow ratio;
    private TextView ratio1_1;
    private TextView ratio4_3;
    private TextView ratioFull;
    private int width = 3;
    private int height = 4;
    private int deviceWidth;
    private int deviceHeight;
    private ImageButton delay;
    private PopupWindow delayTimeWindow;
    private TextView noDelay;
    private TextView delay3;
    private TextView delay5;
    private TextView delay10;
    private int delayState = 0;
    private long delayTime = 0;
    private TextView countdown;
    private ImageButton mirror;
    private boolean mirrorFlag = false;
    private int previewWidth;
    private int previewHeight;
    private ImageView mImageView;
    private String[] permissions = {Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO};
    private List<String> permissionList = new ArrayList();
    private CameraManager mManager;
    private Size mPreviewSize;
    private String mCameraId;
    private Surface mPreviewSurface;
    private ImageReader mImageReader;
    private static CameraCaptureSession mCaptureSession;
    private static CameraDevice mCameraDevice;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private ArrayList<String> imageList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        Log.d(TAG, "onCreateView");

        view = inflater.inflate(R.layout.fragment_take_picture, container, false);

        initView(view);

        mManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        textureView.setSurfaceTextureListener(textureListener);

        getPermission();

        takePicture.setOnClickListener(this);
        change.setOnClickListener(this);
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

        return view;
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");

        super.onResume();
        CameraUtil.setLastImagePath(imageList, mImageView);
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
        closeCamera();
    }

    private void initView(View view) {
        Log.d(TAG, "initView");

        textureView = view.findViewById(R.id.textureView);
        takePicture = view.findViewById(R.id.takePhotoBtn);
        mImageView = view.findViewById(R.id.imageView);
        change = view.findViewById(R.id.change);

        photoMode = view.findViewById(R.id.photoMode);
        recordingMode = view.findViewById(R.id.recordingMode);

        ratioSelected = view.findViewById(R.id.ratio_selected);
        ratio = new PopupWindow();
        ratio.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        ratio.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        ratio.setFocusable(false);
        ratio.setOutsideTouchable(true);
        ratio.setBackgroundDrawable(new ColorDrawable(0x00000000));
        ratio.setContentView(LayoutInflater.from(getActivity()).inflate(R.layout.select_ratio, null));
        ratioSelected.setOnClickListener(v -> {
            ratio.showAsDropDown(view.findViewById(R.id.ratio_selected), -120, 0);
        });
        ratio1_1 = ratio.getContentView().findViewById(R.id.ratio_1_1);
        ratio4_3 = ratio.getContentView().findViewById(R.id.ratio_4_3);
        ratioFull = ratio.getContentView().findViewById(R.id.ratio_full);

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        deviceWidth = displayMetrics.widthPixels;
        deviceHeight = displayMetrics.heightPixels;

        delay = view.findViewById(R.id.delay);
        delayTimeWindow = new PopupWindow();
        delayTimeWindow.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        delayTimeWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        delayTimeWindow.setFocusable(false);
        delayTimeWindow.setOutsideTouchable(true);
        delayTimeWindow.setBackgroundDrawable(new ColorDrawable(0x00000000));
        delayTimeWindow.setContentView(LayoutInflater.from(getActivity()).inflate(R.layout.select_delay_time, null));
        delay.setOnClickListener(v -> {
            delayTimeWindow.showAsDropDown(view.findViewById(R.id.delay), -155, 0);
        });
        noDelay = delayTimeWindow.getContentView().findViewById(R.id.noDelay);
        delay3 = delayTimeWindow.getContentView().findViewById(R.id.delay3);
        delay5 = delayTimeWindow.getContentView().findViewById(R.id.delay5);
        delay10 = delayTimeWindow.getContentView().findViewById(R.id.delay10);

        countdown = view.findViewById(R.id.countdown);

        mirror = view.findViewById(R.id.mirror);
    }

    @Override
    public void onClick(View view) {
        Log.d(TAG, "onClick");

        switch (view.getId()) {
            case R.id.takePhotoBtn:
                if (delayState == 0) {
                    takePhoto();
                } else {
                    new CountDownTimer(delayTime + 300, 1000) {
                        @Override
                        public void onTick(long millisUntilFinished) {
                            countdown.setText(millisUntilFinished / 1000 + "");
                            countdown.setVisibility(View.VISIBLE);
                            countdown.startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.countdown_timer));
                        }

                        @Override
                        public void onFinish() {
                            countdown.setVisibility(View.GONE);
                            takePhoto();
                        }
                    }.start();
                }
                break;
            case R.id.change:
                changeCamera();
                break;
            case R.id.imageView:
                openAlbum();
                break;
            case R.id.photoMode:
                ((MainActivity) getActivity()).changeToTakePicture();
                break;
            case R.id.recordingMode:
                ((MainActivity) getActivity()).changeToRecord();
                break;
            case R.id.ratio_1_1:
                ratioSelected.setText(ratio1_1.getText());
                ratio1_1.setTextColor(ratioSelected.getTextColors());
                ratio4_3.setTextColor(Color.WHITE);
                ratioFull.setTextColor(Color.WHITE);
                ratio.dismiss();
                width = 1;
                height = 1;
                closeCamera();
                setupCamera();
                openCamera();
                break;
            case R.id.ratio_4_3:
                ratioSelected.setText(ratio4_3.getText());
                ratio4_3.setTextColor(ratioSelected.getTextColors());
                ratio1_1.setTextColor(Color.WHITE);
                ratioFull.setTextColor(Color.WHITE);
                ratio.dismiss();
                width = 3;
                height = 4;
                closeCamera();
                setupCamera();
                openCamera();
                break;
            case R.id.ratio_full:
                ratioSelected.setText(ratioFull.getText());
                ratioFull.setTextColor(ratioSelected.getTextColors());
                ratio1_1.setTextColor(Color.WHITE);
                ratio4_3.setTextColor(Color.WHITE);
                ratio.dismiss();
                width = deviceWidth;
                height = deviceHeight;
                Log.d(TAG, "device++++++++width: " + width + ", height: " + height);
                closeCamera();
                setupCamera();
                openCamera();
                break;
            case R.id.noDelay:
                noDelay.setTextColor(ratioSelected.getTextColors());
                delay3.setTextColor(Color.WHITE);
                delay5.setTextColor(Color.WHITE);
                delay10.setTextColor(Color.WHITE);
                delayState = 0;
                delayTime = 0;
                delayTimeWindow.dismiss();
                takePicture.setImageResource(R.drawable.takephoto);
                break;
            case R.id.delay3:
                delay3.setTextColor(ratioSelected.getTextColors());
                noDelay.setTextColor(Color.WHITE);
                delay5.setTextColor(Color.WHITE);
                delay10.setTextColor(Color.WHITE);
                delayState = 1;
                delayTime = 3000;
                delayTimeWindow.dismiss();
                takePicture.setImageResource(R.drawable.delay_photo);
                break;
            case R.id.delay5:
                delay5.setTextColor(ratioSelected.getTextColors());
                noDelay.setTextColor(Color.WHITE);
                delay3.setTextColor(Color.WHITE);
                delay10.setTextColor(Color.WHITE);
                delayState = 2;
                delayTime = 5000;
                delayTimeWindow.dismiss();
                takePicture.setImageResource(R.drawable.delay_photo);
                break;
            case R.id.delay10:
                delay10.setTextColor(ratioSelected.getTextColors());
                noDelay.setTextColor(Color.WHITE);
                delay3.setTextColor(Color.WHITE);
                delay5.setTextColor(Color.WHITE);
                delayState = 3;
                delayTime = 10000;
                delayTimeWindow.dismiss();
                takePicture.setImageResource(R.drawable.delay_photo);
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

    //获取权限
    private void getPermission() {
        Log.d(TAG, "getPermission");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission : permissions) {
                if (ContextCompat.checkSelfPermission(getContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionList.add(permission);
                }
            }
            if (!permissionList.isEmpty()) {
                requestPermissions(permissionList.toArray(new String[permissionList.size()]), 1);
            } else {
                textureView.setSurfaceTextureListener(textureListener);
                CameraUtil.setLastImagePath(imageList, mImageView);
            }
        }
    }

    //权限回调
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionResult");

        if (requestCode == 1 && grantResults.length > 0) {
            List<String> deniedPermissions = new ArrayList<>();
            for (int i = 0; i < grantResults.length; ++i) {
                int result = grantResults[i];
                String permission = permissions[result];
                if (result != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permission);
                }
            }
            if (deniedPermissions.isEmpty()) {
                openCamera();
                CameraUtil.setLastImagePath(imageList, mImageView);
            } else {
                getPermission();
            }
        }
    }

    //TextureView回调
    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable");

            previewWidth = width;
            previewHeight = height;
            setupCamera();
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
//            Log.d(TAG, "onSurfaceTextureUpdated");
        }
    };

    //配置相机
    public void setupCamera() {
        Log.d(TAG, "setupCamera");

        try {
            for (String cameraId : mManager.getCameraIdList()) {
                CameraCharacteristics characteristics = mManager.getCameraCharacteristics(cameraId);
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                mPreviewSize = GetPreviewSize.getOptimalSize(map.getOutputSizes(SurfaceTexture.class), width, height, deviceWidth, deviceHeight);
                textureView.setLayoutParams(new LinearLayout.LayoutParams(mPreviewSize.getHeight(), mPreviewSize.getWidth()));
                textureView.setSurfaceTextureListener(textureListener);
                mCameraId = cameraId;
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
            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            } else {
                mManager.openCamera(mCameraId, stateCallback, null);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //打开相机回调
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
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
        mPreviewSurface = new Surface(mSurfaceTexture);
        try {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(mPreviewSurface);
            mCameraDevice.createCaptureSession(Arrays.asList(mPreviewSurface, mImageReader.getSurface()), sessionStateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //获取摄像头的图像数据
    private void setupImageReader() {
        mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.JPEG, 1);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Log.d(TAG, "图片已保存");

                Image image = reader.acquireNextImage();
                ImageSaver imageSaver = new ImageSaver(getContext(), image);
                new Thread(imageSaver).start();
            }
        }, null);
    }

    //CameraCaptureSession状态回调
    private CameraCaptureSession.StateCallback sessionStateCallback = new CameraCaptureSession.StateCallback() {
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
        Log.d(TAG, "repeatPreview");

        mPreviewRequestBuilder.setTag(TAG);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        mPreviewRequest = mPreviewRequestBuilder.build();
        try {
            mCaptureSession.setRepeatingRequest(mPreviewRequest, null, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public static void closeCamera() {
        Log.d(TAG, "closeCamera");

        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    private void takePhoto() {
        Log.d(TAG, "takePhoto");

        ObjectAnimator scaleXAnim = ObjectAnimator.ofFloat(takePicture, "scaleX", 1f, 0.8f, 1f);
        ObjectAnimator scaleYAnim = ObjectAnimator.ofFloat(takePicture, "scaleY", 1f, 0.8f, 1f);
        AnimatorSet set = new AnimatorSet();
        set.play(scaleXAnim).with(scaleYAnim);
        set.setDuration(300);
        set.start();
        try {
            final CaptureRequest.Builder mCaptureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            mCaptureBuilder.addTarget(mImageReader.getSurface());
            int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
            if (mCameraId.equals(String.valueOf(CameraCharacteristics.LENS_FACING_BACK))) {
                mCaptureBuilder.set(CaptureRequest.JPEG_ORIENTATION, FRONT_ORIENTATIONS.get(rotation));
            } else {
                mCaptureBuilder.set(CaptureRequest.JPEG_ORIENTATION, BACK_ORIENTATIONS.get(rotation));
            }
            mCaptureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            mCaptureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            mCaptureSession.stopRepeating();
            mCaptureSession.capture(mCaptureBuilder.build(), captureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //CameraCaptureSession拍照回调
    private final CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            repeatPreview();
        }
    };

    private void changeCamera() {
        Log.d(TAG, "changeCamera");

        ObjectAnimator anim = ObjectAnimator.ofFloat(change, "rotation", 0f, 180f);
        anim.setDuration(300);
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
        mCameraDevice.close();
        openCamera();
    }

    private void openAlbum() {
        Log.d(TAG, "openAlbum");

        imageList = GetImageFilePath.getFilePath();
        if (!imageList.isEmpty()) {
            Intent intent = new Intent();
            intent.setClass(getContext(), ImageShowActivity.class);
            startActivity(intent);
            closeCamera();
        }
    }

    private class ImageSaver implements Runnable {
        private Image mImage;
        private Context mContext;

        public ImageSaver(Context context, Image image) {
            Log.d(TAG, "ImageSaver");
            mImage = image;
            mContext = context;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);

            if (mirrorFlag) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
                Matrix m = new Matrix();
                m.postScale(-1, 1);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), m, true);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                bytes = baos.toByteArray();
            }

            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
            Date date = new Date(System.currentTimeMillis());
            String path = Environment.getExternalStorageDirectory() + "/DCIM/Camera/myPicture"
                    + format.format(date) + ".jpg";

            File imageFile = new File(path);
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(imageFile);
                fos.write(bytes, 0, bytes.length);
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
                broadcast();
                Message msg = new Message();
                msg.what = 0;
                Bundle mBundle = new Bundle();
                mBundle.putString("myPath", path);
                msg.setData(mBundle);
                handler.sendMessage(msg);
                mImage.close();
            }
        }
    }

    private Handler handler = new Handler(Looper.myLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    Bundle bundle = msg.getData();
                    String myPath = bundle.getString("myPath");
                    imageList.add(myPath);
                    CameraUtil.setLastImagePath(imageList, mImageView);
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + msg.what);
            }
        }
    };

    private void broadcast() {
        Log.d(TAG, "broadcast");

        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/";
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri uri = Uri.fromFile(new File(path));
        intent.setData(uri);
        getActivity().sendBroadcast(intent);
    }
}
