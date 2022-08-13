package com.example.camera2;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.camera2.util.CameraUtil;

import java.util.ArrayList;

public class ImageShowActivity extends AppCompatActivity {
    private final static String TAG = "ImageShowActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_show);
        Log.e(TAG, "onCreate success!");

        ArrayList<String> imageList = CameraUtil.getFilePath();
        String lastImagePath = imageList.get(imageList.size() - 1);
        goToGallery(lastImagePath);
    }

    private void goToGallery(String path) {
        Log.e("TAG", "goToGallery success!");

        Uri uri = getMediaUriFromPath(this, path);
        Log.i("TAG", "uri: " + uri);

        Intent intent = new Intent("com.android.camera.action.REVIEW", uri);
        intent.setData(uri);
        startActivity(intent);
        finish();
    }

    @SuppressLint("Range")
    private Uri getMediaUriFromPath(Context context, String path) {
        Log.e("TAG", "getMediaUriFromPath success!");

        Uri uri = null;
        if (path.contains("jpg")) {
            Uri picUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            Cursor cursor = context.getContentResolver().query(picUri, null,
                    MediaStore.Images.Media.DISPLAY_NAME + "= ?",
                    new String[]{path.substring(path.lastIndexOf("/") + 1)}, null);
            if (cursor.moveToFirst()) {
                uri = ContentUris.withAppendedId(picUri, cursor.getLong(cursor.getColumnIndex(MediaStore.Images.Media._ID)));
            }
            cursor.close();
        } else if (path.contains("mp4")) {
            Uri mediaUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            Cursor cursor = context.getContentResolver().query(mediaUri, null,
                    MediaStore.Video.Media.DISPLAY_NAME + "= ?",
                    new String[]{path.substring(path.lastIndexOf("/") + 1)}, null);
            if (cursor.moveToFirst()) {
                uri = ContentUris.withAppendedId(mediaUri, cursor.getLong(cursor.getColumnIndex(MediaStore.Video.Media._ID)));
            }
            cursor.close();
        }
        return uri;
    }
}
