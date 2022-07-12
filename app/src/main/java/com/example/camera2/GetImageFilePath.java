package com.example.camera2;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;

public class GetImageFilePath {
    static ArrayList<String> imageList = new ArrayList<>();

    public static ArrayList<String> getFilePath() {
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
}
