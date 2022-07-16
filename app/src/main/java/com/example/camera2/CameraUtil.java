package com.example.camera2;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.Log;
import android.widget.ImageView;

import java.util.ArrayList;

public class CameraUtil {

    private static final String TAG = "CameraUtil";

    //最后拍摄图片的路径
    public static void setLastImagePath(ArrayList<String> imageList, ImageView imageView) {
        Log.d(TAG, "setLastImagePath");

        imageList = GetImageFilePath.getFilePath();
        if (imageList.isEmpty()) {
            imageView.setImageResource(R.drawable.no_photo);
        } else {
            String path = imageList.get(imageList.size() - 1);
            if (path.contains(".jpg")) {
                setImageBitmap(path, imageView);
            } else {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(path);
                Bitmap bitmap = retriever.getFrameAtTime(1);
                imageView.setImageBitmap(bitmap);
            }
        }
    }

    public static void setImageBitmap(String path, ImageView imageView) {
        Log.d(TAG, "setImageBitmap");

        Bitmap bitmap = (Bitmap) BitmapFactory.decodeFile(path);
        imageView.setImageBitmap(bitmap);
    }
}
