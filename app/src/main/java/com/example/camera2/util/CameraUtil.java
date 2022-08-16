package com.example.camera2.util;

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
import android.util.SparseIntArray;
import android.view.Surface;
import android.widget.ImageView;

import com.example.camera2.ImageShowActivity;
import com.example.camera2.R;

import java.io.File;
import java.util.ArrayList;
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
}
