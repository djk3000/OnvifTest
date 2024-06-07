package com.example.onviftest.utiliy;

import android.content.Context;

import com.example.onviftest.model.Camera;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ConfigManger {
    /**
     * 读取配置文件
     *
     * @param context
     * @return
     */
    public static Camera loadDeviceFromJson(Context context) {
        File externalFilesDir = context.getExternalFilesDir(null);
        File configFile = new File(externalFilesDir, "config.json");

        // 如果文件不存在，尝试创建它
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                // 文件创建失败时，可以决定返回null或者抛出异常
                return null;
            }
        }

        // 读取文件内容并尝试反序列化为Device对象
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            StringBuilder jsonString = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonString.append(line);
            }
            Gson gson = new Gson();
            return gson.fromJson(jsonString.toString(), Camera.class);
        } catch (IOException | JsonSyntaxException e) {
            // 如果解析出错或读取时发生其他I/O异常，打印堆栈跟踪
            e.printStackTrace();
            // 可以选择返回null或者抛出异常，根据具体需求来定
            return null;
        }
    }

    /**
     * 写入
     *
     * @param context
     * @param camera
     */
    public static void saveDeviceToJson(Context context, Camera camera) {
        // 获取外部文件目录，用于存放应用专属文件
        File externalFilesDir = context.getExternalFilesDir(null);

        // 构建config.json文件路径
        File configFile = new File(externalFilesDir, "config.json");

        try {
            // 如果文件不存在，则创建它
            if (!configFile.exists()) {
                configFile.createNewFile();
            }

            // 使用Gson将Device对象转换为JSON字符串
            Gson gson = new Gson();
            String json = gson.toJson(camera);

            // 将JSON字符串写入文件
            FileWriter writer = new FileWriter(configFile);
            writer.write(json);
            writer.flush(); // 刷新缓冲区
            writer.close(); // 关闭写入器

            // 根据需要，可以添加成功保存的提示或日志
            // Log.d("FileHelper", "Device saved to config.json");
        } catch (IOException e) {
            // 处理解析或写入文件时发生的异常
            e.printStackTrace();
            // 根据需要处理错误情况，比如提示用户或记录日志
            // Log.e("FileHelper", "Error writing to file", e);
        }
    }
}
