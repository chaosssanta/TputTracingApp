package com.lge.tputtracingapp.data;

import android.os.Environment;
import android.util.Log;
import com.lge.tputtracingapp.service.DeviceLoggingStateChangedListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

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
    private static final Executor mExecutor = Executors.newFixedThreadPool(1);

    public static DeviceStatsInfoStorageManager getInstance() {
        if (mInstance == null) {
            mInstance = new DeviceStatsInfoStorageManager();
        }
        return mInstance;
    }

    public void exportToFile(final String fileName) {
        final LinkedList<DeviceStatsInfo> targetList = this.mDeviceStatsRecordList;
        this.mDeviceStatsRecordList = new LinkedList<>();

        Runnable run = new Runnable() {
            @Override
            public void run() {
                handleFileWriting(targetList, fileName);
            }
        };
        mExecutor.execute(run);
    }

    private void handleFileWriting(LinkedList<DeviceStatsInfo> targetList, String fileName) {
        String dirPath = Environment.getExternalStorageDirectory().getPath();
        byte[] buffer = null;
        Iterator<DeviceStatsInfo> sIterator = targetList.iterator();
        FileOutputStream fos = null;

        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdir();
        }

        File file = new File(dir, fileName);
        try {
            fos = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        try {
            while (sIterator.hasNext()) {
                buffer = sIterator.next().toString().getBytes();
                fos.write(buffer, 0, buffer.length);
                fos.flush();
            }
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
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
