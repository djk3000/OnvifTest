package com.example.onviftest;

import android.content.res.AssetManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class Yolov8Ncnn {
    public native boolean loadModel(AssetManager mgr, int modelid, int cpugpu);

    public native void checkImagePose(String path);

    private List<float[]> rectanglesCoordinates = new ArrayList<>();

    static {
        System.loadLibrary("yolov8ncnn");
    }

    private float scale = 1.f;

    private void onData(int[][] data) {
        //自行执行回调后的操作
        Log.i("TAG", "###onData， is=" + data == null ? "null" : "not null");
        if (data == null) return;
        Log.i("TAG", "###onData， length=" + data.length);
        rectanglesCoordinates.clear();
        if (data.length > 0) {
            for (int i = 0; i < data.length; i++) {
                Float x1 = scale * data[i][0];
                Float y1 = scale * data[i][1] - 50;
                Float x2 = scale * data[i][0] + scale * data[i][2];
                Float y2 = scale * data[i][1] + scale * data[i][3];
                rectanglesCoordinates.add(new float[]{x1, y1, x2, y2});
            }
            for (float[] coordinates : rectanglesCoordinates) {
                for (float coordinate : coordinates) {
                    Log.i("TAG", "###onData， coordinate=" + coordinate);
                }
                System.out.println();
            }
        }
    }

    public List<float[]> getRectanglesCoordinates() {
        return rectanglesCoordinates;
    }

    public void setRectanglesCoordinates(List<float[]> rectanglesCoordinates) {
        this.rectanglesCoordinates = rectanglesCoordinates;
    }
}
