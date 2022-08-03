package com.example.camera2;

import android.util.Log;
import android.util.Size;

import java.util.ArrayList;

import java.util.List;

public class GetPreviewSize {

    private static final String TAG = "GetPreviewSize";

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
}