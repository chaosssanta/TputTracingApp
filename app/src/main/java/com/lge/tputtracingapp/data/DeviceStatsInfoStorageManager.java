package com.lge.tputtracingapp.data;

import android.util.Log;

import com.lge.tputtracingapp.service.DeviceLoggingStateChangedListener;

import java.util.LinkedList;

/**
 * Created by wonsik.lee on 2017-03-14.
 */

public class DeviceStatsInfoStorageManager implements DeviceLoggingStateChangedListener {
    private static final String TAG = DeviceStatsInfoStorageManager.class.getSimpleName();

    private LinkedList<DeviceStatsInfo> mDeviceStatsRecordList;

    private static DeviceStatsInfoStorageManager mInstance;

    private DeviceStatsInfoStorageManager() {
        this.mDeviceStatsRecordList = new LinkedList<>();
    }

    public static DeviceStatsInfoStorageManager getInstance() {
        if (mInstance == null) {
            mInstance = new DeviceStatsInfoStorageManager();
        }
        return mInstance;
    }

    public void exportToFile(String fileName) {
        final LinkedList<DeviceStatsInfo> targetList = this.mDeviceStatsRecordList;
        this.mDeviceStatsRecordList = new LinkedList<>();

        new Thread(new Runnable() {
            @Override
            public void run() {
                // TODO : write to file here by chaossannta
            }
        }).start();
    }

    public void add(DeviceStatsInfo deviceStatsInfo) {
        this.mDeviceStatsRecordList.add(deviceStatsInfo);
    }

    @Override
    public void onDeviceLoggingStateChanged(boolean b) {
        Log.d(TAG, "onDeviceLoggingStateChanged(boolean) : " + b);
        if (!b) {
            exportToFile(generateFileName());
        }
    }

    public float getAvgTputForTheLatestSeconds(int seconds, int intervalInMilliseconds) {
        Log.d(TAG, "getAvgTputForTheLastSeconds(int, int) : " + seconds + ", " + intervalInMilliseconds);
        int startIndex = this.mDeviceStatsRecordList.size() - (seconds * 1000 / intervalInMilliseconds + 1);
        if (startIndex < 0) {
            startIndex = 0;
        }

        long rxBytesSum =  this.mDeviceStatsRecordList.getLast().getRxBytes() - this.mDeviceStatsRecordList.get(startIndex).getRxBytes();
        float time = (this.mDeviceStatsRecordList.getLast().getTimeStamp() - this.mDeviceStatsRecordList.get(startIndex).getTimeStamp()) / 1000.0f;

        if (time == 0) {
            return 0;
        }
        return ((rxBytesSum / 1024 / 1024 * 8) / (time));
    }

    private static String generateFileName() {
        return System.currentTimeMillis() + ".txt";
    }
}
