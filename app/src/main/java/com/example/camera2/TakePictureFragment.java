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
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class TakePictureFragment extends Fragment implements View.OnClickListener {
    private static final String TAG = "TakePictureFragment";

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private View view;
    private TextureView textureView;
    private ImageButton takePicture;
    private ImageButton change;
    private TextView photoMode;
    private TextView recordingMode;
    private ImageView mImageView;
    private String[] permissions = { Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO };
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
        Log.d(TAG, "onCreateView: success");

        view = inflater.inflate(R.layout.fragment_take_picture, container, false);

        initView(view);

        mManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        textureView.setSurfaceTextureListener(textureListener);

        takePicture.setOnClickListener(this);
        change.setOnClickListener(this);
        mImageView.setOnClickListener(this);

        getPermission();

        return view;
    }

    private void initView(View view) {
        Log.d(TAG, "initView: success");

        textureView = view.findViewById(R.id.textureView);
        takePicture = view.findViewById(R.id.takePhotoBtn);
        mImageView = view.findViewById(R.id.imageView);
        change = view.findViewById(R.id.change);

        photoMode = view.findViewById(R.id.photoMode);
        recordingMode = view.findViewById(R.id.recordingMode);
        photoMode.setOnClickListener(v -> {
            ((MainActivity)getActivity()).changeToTakePicture();
        });
        recordingMode.setOnClickListener(v -> {
            ((MainActivity)getActivity()).changeToRecord();
        });
    }

    @Override
    public void onClick(View view) {
        Log.d(TAG, "onClick: success");

        switch (view.getId()) {
            case R.id.takePhotoBtn:
                takePhoto();
                break;
            case R.id.change:
                changeCamera();
                break;
            case R.id.imageView:
                openAlbum();
                break;
        }
    }

    //获取权限
    private void getPermission() {
        Log.d(TAG, "getPermission: success");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String permission: permissions) {
                if (ContextCompat.checkSelfPermission(getContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                    permissionList.add(permission);
                }
            }
            if (!permissionList.isEmpty()) {
                requestPermissions(permissionList.toArray(new String[permissionList.size()]), 1);
            } else {
                textureView.setSurfaceTextureListener(textureListener);
                setLastImagePath();
            }
        }
    }

    //权限回调
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionResult: success");

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
                setLastImagePath();
            } else {
                getPermission();
            }
        }
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume: success");
        super.onResume();
        setLastImagePath();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause: success");

        super.onPause();
        closeCamera();
    }

    //TextureView回调
    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable: success");

            setupCamera();
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
        }
    };

    //配置相机
    private void setupCamera() {
        Log.d(TAG, "setupCamera: success");

        try {
            for (String cameraId: mManager.getCameraIdList()) {
                CameraCharacteristics characteristics = mManager.getCameraCharacteristics(cameraId);
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                mPreviewSize = getOptimalSize(map.getOutputSizes(SurfaceTexture.class), 720, 960);
                textureView.setSurfaceTextureListener(textureListener);
                mCameraId = cameraId;
                break;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //获取最佳预览尺寸
    private Size getOptimalSize(Size[] sizeMap, int width, int height) {
        Log.d(TAG, "getOptimalSize: success");

        List<Size> sizeList = new ArrayList<>();
        for (Size option: sizeMap) {
            if (width > height) {
                if (option.getWidth() > width && option.getHeight() > height) {
                    sizeList.add(option);
                }
            } else {
                if (option.getWidth() > height && option.getHeight() > width) {
                    sizeList.add(option);
                }
            }
        }
        if (sizeList.size() > 0) {
            return Collections.min(sizeList, new Comparator<Size>() {
                @Override
                public int compare(Size size, Size t1) {
                    return Long.signum(size.getWidth() * size.getHeight() - t1.getWidth() * t1.getHeight());
                }
            });
        }
        return sizeMap[0];
    }

    //打开相机
    private void openCamera() {
        Log.d(TAG, "openCamera: success");

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
            Log.d(TAG, "CameraDevice.StateCallback： onDisconnected");
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int i) {
            Log.d(TAG, "CameraDevice.StateCallback： onError");
        }
    };

    //开启相机预览
    private void startPreview() {
        Log.d(TAG, "startPreview: success");

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
            mCaptureSession = session;
            repeatPreview();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

        }
    };

    //设置不断重复预览
    private void repeatPreview() {
        Log.d(TAG, "repeatPreview: success");

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
        Log.d(TAG, "closeCamera: success");

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
        Log.d(TAG, "takePhoto: success");

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
            mCaptureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
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

    //最后拍摄图片的路径
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

    private void openAlbum() {
        Log.d(TAG, "openAlbum: success");

        ArrayList<String> imgList = new ArrayList<>();
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
            Log.d(TAG, "ImageSaver: success");
            mImage = image;
            mContext = context;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
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
                    setLastImagePath();
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + msg.what);
            }
        }
    };

    private void broadcast() {
        Log.d(TAG, "broadcast: success");

        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/";
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri uri = Uri.fromFile(new File(path));
        intent.setData(uri);
        getActivity().sendBroadcast(intent);
    }
}
