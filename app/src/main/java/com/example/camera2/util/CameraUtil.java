package com.example.camera2.util;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.media.MediaActionSound;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.widget.ImageView;

import com.example.camera2.ImageShowActivity;
import com.example.camera2.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CameraUtil {
    private static final String TAG = "CameraUtil";
    public static final SparseIntArray FRONT_ORIENTATIONS = new SparseIntArray();
    public static final SparseIntArray BACK_ORIENTATIONS = new SparseIntArray();
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
    private static final MediaActionSound mMediaActionSound = new MediaActionSound();

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
            Log.d(TAG, "preview choose--------width: " + result.getHeight() + ", height: " + result.getWidth());
            return result;
        } else {
            float deviceRatio = (float) deviceWidth / deviceHeight;
            for (Size itemSize: sizeMap) {
                float ratioList = (float) itemSize.getHeight() / itemSize.getWidth();
                if (result == null) {
                    result = itemSize;
                } else {
                    float resultRatio = (float) result.getHeight() / result.getWidth();
                    if (Math.abs(ratioList - deviceRatio) < Math.abs(resultRatio - deviceRatio)) {
                        result = itemSize;
                    }
                }
            }
            List<Size> resultList = new ArrayList<>();
            for (Size itemSize: sizeMap) {
                if (((float) itemSize.getHeight() / itemSize.getWidth()) == ((float) result.getHeight() / result.getWidth())) {
                    resultList.add(itemSize);
                }
            }
            if (resultList.size() > 0) {
                for (int i = 0; i < resultList.size(); ++i) {
                    Log.d(TAG, "-----resultList[" + i + "]-----" + resultList.get(i).getHeight() + "*" + resultList.get(i).getWidth());
                    if (Math.abs(resultList.get(i).getWidth() * resultList.get(i).getHeight() - deviceWidth * deviceHeight) <
                            Math.abs(result.getWidth() * result.getHeight() - deviceWidth * deviceHeight)) {
                        result = resultList.get(i);
                    }
                }

            }
            if (result != null) {
                Log.d(TAG, "preview Full choose--------width: " + result.getHeight());
                Log.d(TAG, "preview Full choose--------height: " + result.getWidth());
                return result;
            }
        }
        return sizeMap[0];
    }

    //获取照片尺寸
    public static Size getMaxSize(Size[] sizeMap, int width, int height, int deviceWidth, int deviceHeight) {
        Log.d(TAG, "getMaxSize");
        List<Size> sizeList = new ArrayList<>();
        Size result = null;
        for (Size option: sizeMap) {
            //摄像头分辨率的高度对应屏幕的宽度
            if ((float) option.getHeight() / option.getWidth() == (float) width / height) {
                sizeList.add(option);
            }
        }
        if (sizeList.size() > 0) {
            Collections.sort(sizeList, new Comparator<Size>() {
                @Override
                public int compare(Size o1, Size o2) {
                    return o1.getWidth() * o1.getHeight() - o2.getWidth() * o2.getHeight();
                }
            });
            Collections.reverse(sizeList);
            result = sizeList.get(0);
            Log.d(TAG, "photo choose--------width: " + result.getHeight() + ", height: " + result.getWidth());
            return result;
        } else {
            float deviceRatio = (float) deviceWidth / deviceHeight;
            for (Size itemSize: sizeMap) {
                float ratioList = (float) itemSize.getHeight() / itemSize.getWidth();
                if (result == null) {
                    result = itemSize;
                } else {
                    float resultRatio = (float) result.getHeight() / result.getWidth();
                    if (Math.abs(ratioList - deviceRatio) < Math.abs(resultRatio - deviceRatio)) {
                        result = itemSize;
                    }
                }
            }
            if (result != null) {
                Log.d(TAG, "photo Full choose--------width: " + result.getHeight());
                Log.d(TAG, "photo Full choose--------height: " + result.getWidth());
                return result;
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
        int size = 0;
        if (dirEpub != null) {
            for (File f: dirEpub) {
                try {
                    FileInputStream fis = new FileInputStream(f);
                    size = fis.available();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (size > 0) {
                    String fileName = f.getName();
                    //过滤删除至回收站中的照片视频
                    if (fileName.startsWith(".")) {
                        continue;
                    }
                    imageList.add(f.toString());
                    Log.i("File", "File name = " + fileName);
                } else {
                    if (f.delete()) {
                        Log.d(TAG, "delete success");
                    } else {
                        Log.d(TAG, "delete fail");
                    }
                }
            }
        }
        return imageList;
    }

    public static void getThumbnail(ImageView imageView, Handler handler) {
        Log.d(TAG, "getThumbnail");
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
            handler.post(() -> imageView.setImageBitmap(bitmap));
        }
    }

    public static int calculateInSampleSize(Size picSize, int reqHeight, int reqWidth) {
        Log.d(TAG, "calculateInSampleSize");
        int height = picSize.getWidth();
        int width = picSize.getHeight();
        int inSampleSize = 1;
        if(height > reqHeight || width > reqWidth){
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
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

    public static AnimatorSet scaleAnim(Object target, float v1, float v2, float v3, long duration) {
        @SuppressLint("ObjectAnimatorBinding")
        ObjectAnimator scaleXAnim = ObjectAnimator.ofFloat(target, "scaleX", v1, v2, v3);
        @SuppressLint("ObjectAnimatorBinding")
        ObjectAnimator scaleYAnim = ObjectAnimator.ofFloat(target, "scaleY", v1, v2, v3);
        AnimatorSet set = new AnimatorSet();
        set.play(scaleXAnim).with(scaleYAnim);
        set.setDuration(duration);
        return set;
    }

    public static void loadSound(int type) {
        mMediaActionSound.load(type);
    }

    public static void playSound(int type) {
        mMediaActionSound.play(type);
    }

    public static void releaseSound() {
        mMediaActionSound.release();
    }
}
