package com.example.camera2.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Environment;
import android.util.Log;
import android.util.Size;
import android.widget.ImageView;

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
                for (int i = 0; i < sizeMap.length; ++i) {
                    Size itemSize = sizeMap[i];
                    if (itemSize.getHeight() < (deviceWidth + j * 5) && itemSize.getHeight() > (deviceWidth - j * 5)) {
                        if (result != null) {
                            if (Math.abs(deviceHeight - itemSize.getWidth()) < Math.abs(deviceHeight - result.getWidth())) {
                                result = itemSize;
                                continue;
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
            file.mkdir();
        } else if (file.exists() && !file.isDirectory()) {
            Log.e("GetImageFilePath", "'Camera' already exists, but it isn't a directory.");
        }
        File[] dirEpub = file.listFiles();
        imageList.clear();
        for (int i = 0; i < dirEpub.length; ++i) {
            String fileName = dirEpub[i].toString();
            String tmp = Environment.getExternalStorageDirectory().toString() + "/DCIM/Camera";
            if (fileName.charAt(tmp.length() + 1) == '.') {
                continue;
            }
            imageList.add(fileName);
            Log.i("File", "File name = " + fileName);
        }

        return imageList;
    }

    //最后拍摄图片的路径
    public static void setLastImagePath(ArrayList<String> imageList, ImageView imageView) {
        Log.d(TAG, "setLastImagePath");

        imageList = getFilePath();
        if (imageList.isEmpty()) {
            imageView.setImageResource(R.drawable.no_photo);
        } else {
            String path = imageList.get(imageList.size() - 1);
            Bitmap bitmap;
            if (path.contains(".jpg")) {
                bitmap = (Bitmap) BitmapFactory.decodeFile(path);
            } else {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(path);
                bitmap = retriever.getFrameAtTime(1);
            }
            int bitmapW = bitmap.getWidth();
            int bitmapH = bitmap.getHeight();
            if (bitmapW < bitmapH) {
                bitmap = Bitmap.createBitmap(bitmap, 0, (bitmapH - bitmapW) / 2, bitmapW, bitmapW);
            } else {
                bitmap = Bitmap.createBitmap(bitmap, (bitmapW - bitmapH) / 2, 0, bitmapH, bitmapH);
            }
            imageView.setImageBitmap(bitmap);
        }
    }
}
