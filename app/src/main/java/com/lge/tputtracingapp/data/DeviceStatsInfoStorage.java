package com.lge.tputtracingapp.data;

import android.bluetooth.BluetoothClass;

import com.lge.tputtracingapp.service.DeviceLoggingStateChangedListener;

import java.util.ArrayList;

/**
 * Created by wonsik.lee on 2017-03-14.
 */

public class DeviceStatsInfoStorage implements DeviceLoggingStateChangedListener {
    private static final String TAG = DeviceStatsInfoStorage.class.getSimpleName();

    private ArrayList<DeviceStatsInfo> mDeviceStatsList;

    private static DeviceStatsInfoStorage mInstance;

    private DeviceStatsInfoStorage() {
        this.mDeviceStatsList = new ArrayList<DeviceStatsInfo>();
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
        this.mDeviceStatsList.add(deviceStatsInfo);
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
