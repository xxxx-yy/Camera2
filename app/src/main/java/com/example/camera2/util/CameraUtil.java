package com.example.camera2.util;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.View;
import android.widget.ImageView;

import com.example.camera2.ImageShowActivity;
import com.example.camera2.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CameraUtil {

    private static final String TAG = "CameraUtil";

    //获取最佳预览尺寸
    public static Size getOptimalSize(Size[] sizeMap, int width, int height, int deviceWidth, int deviceHeight) {
        Log.d(TAG, "getOptimalSize");

        List<Size> sizeList = new ArrayList<>();
        Size result = null;
        for (Size option: sizeMap) {
            Log.d(TAG, "sizeMap--------width: " + option.getHeight() + ", height: " + option.getWidth());
            //摄像头分辨率的高度对应屏幕的宽度
            if ((float) option.getHeight() / option.getWidth() == (float) width / height) {
                sizeList.add(option);
                Log.d(TAG, "sizeList--------width: " + option.getHeight() + ", height: " + option.getWidth());
            }
        }

        Log.d(TAG, "Device--------width: " + deviceWidth);
        Log.d(TAG, "Device--------height: " + deviceHeight);
        if (sizeList.size() > 0) {
            result = sizeList.get(0);
            for (int i = 1; i < sizeList.size(); ++i) {
                if (Math.abs(sizeList.get(i).getHeight() - deviceWidth)
                        < Math.abs(result.getHeight() - deviceWidth)) {
                    result = sizeList.get(i);
                }
            }
            Log.d(TAG, "choose--------width: " + result.getHeight() + ", height: " + result.getWidth());
            return result;
        } else {
            for (int j = 1; j < 41; ++j) {
                for (Size itemSize : sizeMap) {
                    if (itemSize.getHeight() < (deviceWidth + j * 5) && itemSize.getHeight() > (deviceWidth - j * 5)) {
                        if (result != null) {
                            if (Math.abs(deviceHeight - itemSize.getWidth()) < Math.abs(deviceHeight - result.getWidth())) {
                                result = itemSize;
                            }
                        } else {
                            result = itemSize;
                        }
                    }
                }
                if (result != null) {
                    Log.e(TAG, "Full选择的分辨率宽度 = " + result.getHeight());
                    Log.e(TAG, "Full选择的分辨率高度 = " + result.getWidth());
                    return result;
                }
            }
        }
        return sizeMap[0];
    }

    public static ArrayList<String> getFilePath() {
        ArrayList<String> imageList = new ArrayList<>();
        File file = new File(Environment.getExternalStorageDirectory().toString() + "/DCIM/Camera");
        if (!file.exists()) {
            boolean res = file.mkdir();
            if (res) {
                Log.d(TAG, "mkdir success");
            } else {
                Log.e(TAG, "mkdir fail");
            }
        } else if (file.exists() && !file.isDirectory()) {
            Log.e("GetImageFilePath", "'Camera' already exists, but it isn't a directory.");
        }
        File[] dirEpub = file.listFiles();
        if (dirEpub != null) {
            for (File value : dirEpub) {
                String fileName = value.toString();
                String tmp = Environment.getExternalStorageDirectory().toString() + "/DCIM/Camera";
                if (fileName.charAt(tmp.length() + 1) == '.') {
                    continue;
                }
                imageList.add(fileName);
                Log.i("File", "File name = " + fileName);
            }
        }

        return imageList;
    }

    //最后拍摄图片的路径
    public static void setLastImagePath(ImageView imageView) {
        Log.d(TAG, "setLastImagePath");

        //TODO bitmap未销毁 内存泄漏
        ArrayList<String> imageList = getFilePath();
        if (imageList.isEmpty()) {
            imageView.setImageResource(R.drawable.no_photo);
        } else {
            String path = imageList.get(imageList.size() - 1);
            Bitmap bitmap;
            if (path.contains(".jpg")) {
                bitmap = BitmapFactory.decodeFile(path);
            } else {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(path);
                bitmap = retriever.getFrameAtTime(1);
            }
            imageView.setImageBitmap(bitmap);

//            if (bitmap != null && !bitmap.isRecycled()) {
//                bitmap.recycle();
//                bitmap = null;
//            }
        }
    }

    public static void openAlbum(Context context) {
        Log.d(TAG, "openAlbum");

        ArrayList<String> imageList = CameraUtil.getFilePath();
        if (!imageList.isEmpty()) {
            Intent intent = new Intent();
            intent.setClass(context, ImageShowActivity.class);
            context.startActivity(intent);
        }
    }

    public static void broadcast(Activity activity) {
        Log.d(TAG, "broadcast");

        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/";
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri uri = Uri.fromFile(new File(path));
        intent.setData(uri);
        activity.sendBroadcast(intent);
    }

    public static Matrix configureTransform(Activity activity, int width, int height, Size previewSize) {
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, width, height);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) height / previewSize.getHeight(),
                    (float) width / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        return matrix;
    }

    public static void rotationAnim(View view, int rotation) {
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
        ObjectAnimator changeAnim = ObjectAnimator.ofFloat(view.findViewById(R.id.change), "rotation", 0f, toValue);
        ObjectAnimator previewAnim = ObjectAnimator.ofFloat(view.findViewById(R.id.imageView), "rotation", 0f, toValue);
        ObjectAnimator buttonAnim = ObjectAnimator.ofFloat(view.findViewById(R.id.takePhotoBtn), "rotation", 0f, toValue);
        ObjectAnimator ratioAnim = ObjectAnimator.ofFloat(view.findViewById(R.id.ratio_selected), "rotation", 0f, toValue);
        ObjectAnimator ratio1_1Anim = ObjectAnimator.ofFloat(view.findViewById(R.id.ratio_1_1), "rotation", 0f, toValue);
        ObjectAnimator ratio4_3Anim = ObjectAnimator.ofFloat(view.findViewById(R.id.ratio_4_3), "rotation", 0f, toValue);
        ObjectAnimator ratioFullAnim = ObjectAnimator.ofFloat(view.findViewById(R.id.ratio_full), "rotation", 0f, toValue);
        ObjectAnimator delayAnim = ObjectAnimator.ofFloat(view.findViewById(R.id.delay), "rotation", 0f, toValue);
        ObjectAnimator delayOffAnim = ObjectAnimator.ofFloat(view.findViewById(R.id.noDelay), "rotation", 0f, toValue);
//        ObjectAnimator delay3Anim = ObjectAnimator.ofFloat(LayoutInflater.from(activity).inflate(R.layout.select_delay_time, null).findViewById(R.id.delay3), "rotation", 0f, toValue);
        ObjectAnimator delay5Anim = ObjectAnimator.ofFloat(view.findViewById(R.id.delay5), "rotation", 0f, toValue);
        ObjectAnimator delay10Anim = ObjectAnimator.ofFloat(view.findViewById(R.id.delay10), "rotation", 0f, toValue);
        ObjectAnimator photoAnim = ObjectAnimator.ofFloat(view.findViewById(R.id.photoMode), "rotation", 0f, toValue);
        ObjectAnimator videoAnim = ObjectAnimator.ofFloat(view.findViewById(R.id.recordingMode), "rotation", 0f, toValue);
        ObjectAnimator mirrorAnim = ObjectAnimator.ofFloat(view.findViewById(R.id.mirror), "rotation", 0f, toValue);
        AnimatorSet set = new AnimatorSet();
        set.play(changeAnim).with(previewAnim).with(buttonAnim).with(ratioAnim).with(ratio1_1Anim)
                .with(ratio4_3Anim).with(ratioFullAnim).with(delayAnim).with(delayOffAnim)
                .with(delay5Anim).with(delay10Anim).with(photoAnim).with(videoAnim).with(mirrorAnim);
        set.setDuration(300);
        set.start();
    }
}
