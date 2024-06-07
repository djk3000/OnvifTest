package com.example.onviftest.model;

import java.util.ArrayList;
import java.util.List;

public class Camera {
    private int currentDevice;
    private ArrayList<Device> deviceList;

    public int getCurrentDevice() {
        return currentDevice;
    }

    public void setCurrentDevice(int currentDevice) {
        this.currentDevice = currentDevice;
    }

    public ArrayList<Device> getDeviceList() {
        return deviceList;
    }

    public void setDeviceList(ArrayList<Device> deviceList) {
        this.deviceList = deviceList;
    }
}
