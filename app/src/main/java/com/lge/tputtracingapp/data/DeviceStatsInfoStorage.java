package com.lge.tputtracingapp.data;

import android.bluetooth.BluetoothClass;
import android.support.annotation.NonNull;

import com.lge.tputtracingapp.service.DeviceLoggingStateChangedListener;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by wonsik.lee on 2017-03-14.
 */

public class DeviceStatsInfoStorage implements DeviceLoggingStateChangedListener {
    private static final String TAG = DeviceStatsInfoStorage.class.getSimpleName();

    private Queue<DeviceStatsInfo> mDeviceStatsQueue;

    private static DeviceStatsInfoStorage mInstance;

    private DeviceStatsInfoStorage() {
        this.mDeviceStatsQueue = new LinkedList<>();
    }

    public DeviceStatsInfoStorage getInstance() {
        if (mInstance == null) {
            mInstance = new DeviceStatsInfoStorage();
        }
        return mInstance;
    }

    public void exportToFile(String fileName) {

    }

    public void add(DeviceStatsInfo deviceStatsInfo) {
        this.mDeviceStatsQueue.add(deviceStatsInfo);
    }

    @Override
    public void onDeviceLoggingStateChanged(boolean b) {
        if (!b) {
            exportToFile(generateFileName());
        }
    }

    private static String generateFileName() {
        return System.currentTimeMillis() + ".txt";
    }


}
