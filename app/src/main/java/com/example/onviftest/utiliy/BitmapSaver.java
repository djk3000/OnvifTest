package com.example.onviftest.utiliy;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class BitmapSaver {

    private static final String TAG = "BitmapSaver";

    public static boolean saveBitmapToFile(Context context, Bitmap bitmap, String fileName, Bitmap.CompressFormat format) {
        // 检查写入权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Missing WRITE_EXTERNAL_STORAGE permission.");
                return false;
            }
        }

        // 目标文件路径与名称
        String directoryPath = context.getExternalFilesDir(null).getPath();
        String filePath = directoryPath + "/" + fileName + "." + format.name().toLowerCase();

        // 创建File对象
        File directory = new File(directoryPath);
        File imageFile = new File(filePath);

        // 创建目录（如果不存在）
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                Log.e(TAG, "Failed to create directory: " + directoryPath);
                return false;
            }
        }

        try (FileOutputStream outputStream = new FileOutputStream(imageFile)) {
            // 压缩Bitmap并写入文件
            boolean success = bitmap.compress(format, 90 /* quality */, outputStream);
            if (!success) {
                Log.e(TAG, "Failed to compress and write bitmap to file.");
            }
            return success;
        } catch (IOException e) {
            Log.e(TAG, "Error occurred while saving bitmap to file.", e);
            return false;
        }
    }
}