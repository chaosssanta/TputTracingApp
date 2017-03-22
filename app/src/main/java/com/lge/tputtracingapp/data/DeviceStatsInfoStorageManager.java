package com.lge.tputtracingapp.data;

import android.os.Environment;
import android.support.v4.util.CircularArray;
import android.util.Log;
import com.lge.tputtracingapp.service.DeviceLoggingStateChangedListener;
import com.lge.tputtracingapp.statsreader.CPUStatsReader;
import com.lge.tputtracingapp.statsreader.NetworkStatsReader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by wonsik.lee on 2017-03-14.
 */

public class DeviceStatsInfoStorageManager implements DeviceLoggingStateChangedListener {
    private static final String TAG = DeviceStatsInfoStorageManager.class.getSimpleName();
    private static final int TPUT_CALCULATION_UNIT_TIME = 3000;

    private static DeviceStatsInfoStorageManager mInstance;

    private LinkedList<DeviceStatsInfo> mDeviceStatsRecordList;
    private CircularArray<DeviceStatsInfo> mDLTPutCircularArray;

    private DeviceStatsInfoStorageManager() {
        this.mDeviceStatsRecordList = new LinkedList<>();
        this.mDLTPutCircularArray = new CircularArray<>();
    }
    private ExecutorService mExecutorService = null;

    public static DeviceStatsInfoStorageManager getInstance() {
        if (mInstance == null) {
            mInstance = new DeviceStatsInfoStorageManager();
        }
        return mInstance;
    }

    public void exportToFile(final String fileName) {
        final LinkedList<DeviceStatsInfo> targetList = this.mDeviceStatsRecordList;
        this.mDeviceStatsRecordList = new LinkedList<>();

        mExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        Runnable run = new Runnable() {
            @Override
            public void run() {
                handleFileWriting(targetList, fileName);
            }
        };
        Future mFuture = mExecutorService.submit(run);

        try {
            mFuture.get(); //if return value were null, it will be good.
            Log.d(TAG, "File writing is completed.");
        } catch (InterruptedException e) {
            Log.d(TAG, "InterruptedException, The thread is interrupted during file writing. e.getMessage: " + e.getMessage());
            e.printStackTrace();
        } catch (ExecutionException e) {
            Log.d(TAG, "ExecutionException. e.getMessage: " + e.getMessage());
            e.printStackTrace();
        }
        mExecutorService.shutdown();
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
            this.flushStoredData();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addToStorage(DeviceStatsInfo deviceStatsInfo) {
        this.mDeviceStatsRecordList.add(deviceStatsInfo);
    }

    public void addToTputCalculationBuffer(DeviceStatsInfo deviceStatsInfo) {
        if ((this.mDLTPutCircularArray.size() > 0) &&
            ((this.mDLTPutCircularArray.getLast().getTimeStamp() - this.mDLTPutCircularArray.getFirst().getTimeStamp()) >= TPUT_CALCULATION_UNIT_TIME)) {
                this.mDLTPutCircularArray.popFirst();
        }
        this.mDLTPutCircularArray.addLast(deviceStatsInfo);
    }

    public enum TEST_TYPE {
        DL_TEST, UL_TEST
    }

    public float getAvgTputFromTpuCalculationBuffer(TEST_TYPE type) {
        float tput = 0.0f;

        if (this.mDLTPutCircularArray.size() >= 0) {
            if (this.mDLTPutCircularArray.getFirst().hashCode() != this.mDLTPutCircularArray.getLast().hashCode()) {
                long duration = this.mDLTPutCircularArray.getLast().getTimeStamp() - this.mDLTPutCircularArray.getFirst().getTimeStamp();
                long bytes = 0;
                if (type == TEST_TYPE.DL_TEST) {
                    bytes = this.mDLTPutCircularArray.getLast().getRxBytes() - this.mDLTPutCircularArray.getFirst().getRxBytes();
                } else {
                    bytes = this.mDLTPutCircularArray.getLast().getTxBytes() - this.mDLTPutCircularArray.getFirst().getTxBytes();
                }

                tput = (bytes / 1024 / 1024 * 8)/(duration / 1000.0f);
            }
        }
        return tput;
    }

    @Override
    public void onDeviceLoggingStateChanged(boolean b) {
        Log.d(TAG, "onDeviceLoggingStateChanged(boolean) : " + b);
        if (!b) {
            exportToFile(generateFileName());
        }
    }

    public DeviceStatsInfo readCurrentDeviceStatsInfo(int targetUid, String cpuTemperatureFilePath, String cpuClockFilePath) {
        DeviceStatsInfo deviceStatsInfo = new DeviceStatsInfo();
        deviceStatsInfo.setTimeStamp(System.currentTimeMillis());
        deviceStatsInfo.setTxBytes(NetworkStatsReader.getTxBytesByUid(targetUid));
        deviceStatsInfo.setRxBytes(NetworkStatsReader.getRxBytesByUid(targetUid));
        deviceStatsInfo.setCpuTemperature(CPUStatsReader.getThermalInfo(cpuTemperatureFilePath));
        deviceStatsInfo.setCpuFrequencyList(CPUStatsReader.getCpuFreq(cpuClockFilePath));
        deviceStatsInfo.setCpuUsage(CPUStatsReader.getCpuUsage());
        return deviceStatsInfo;
    }

    private static String generateFileName() {
        return System.currentTimeMillis() + ".txt";
    }

    private void flushStoredData() {
        this.mDeviceStatsRecordList.clear();
    }
}
