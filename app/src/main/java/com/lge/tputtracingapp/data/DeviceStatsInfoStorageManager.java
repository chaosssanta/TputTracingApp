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

    private long mPivotRxBytes = Long.MIN_VALUE;
    private long mPivotTxBytes = Long.MIN_VALUE;

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
        final LinkedList<DeviceStatsInfo> sTargetList = this.mDeviceStatsRecordList;
        this.mDeviceStatsRecordList = new LinkedList<>();

        mExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        Log.d(TAG, "available thread cnt: " + Runtime.getRuntime().availableProcessors());

        Runnable run = new Runnable() {
            @Override
            public void run() {
                handleFileWriting(sTargetList, fileName);
            }
        };
        Future sFuture = mExecutorService.submit(run);

        try {
            sFuture.get(); //if return value were null, it will be good.
            Log.d(TAG, "File writing is completed.");
        } catch (InterruptedException e) {
            Log.d(TAG, "InterruptedException, The thread is interrupted during file writing. e.getMessage: " + e.getMessage());
            e.printStackTrace();
        } catch (ExecutionException e) {
            Log.d(TAG, "ExecutionException. e.getMessage: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            Log.d(TAG, "Exception. e.getMessage: " + e.getMessage());
            e.printStackTrace();
        }
        mExecutorService.shutdown(); //free thread pool.
    }

    private void handleFileWriting(LinkedList<DeviceStatsInfo> targetList, String fileName) {
        String sDirPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        byte[] sBuffer = null;
        Iterator<DeviceStatsInfo> sIterator = targetList.iterator();
        FileOutputStream sFos = null;
        boolean sIsMadeDir = false;

        File sDir = new File(sDirPath+"/TputTracingApp_Logs");
        if (!sDir.exists()) {
            sIsMadeDir = sDir.mkdir();
        }
        Log.d(TAG, "sIsMadeDir: " + sIsMadeDir + ", Directory path for log files: " + sDirPath + "/TputTracingApp_Logs");

        if (!sDir.canWrite() | !sIsMadeDir) {
            Log.d(TAG, "Cannot make log files or fails making directory.");
        }

        File sFile = new File(sDir, fileName);
        try {
            sFos = new FileOutputStream(sFile);
        } catch (FileNotFoundException e) {
            Log.d(TAG, "FileNotFoundException, e.getMessage(): " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            Log.d(TAG, "Exception, e.getMessage(): " + e.getMessage());
            e.printStackTrace();
        }

        try {
            while (sIterator.hasNext()) {
                DeviceStatsInfo sDeviceStatsInfo = sIterator.next();
                Log.d(TAG, "TimeStamp: " + String.valueOf(sDeviceStatsInfo.getTimeStamp()) + ", TxBytes: " + String.valueOf(sDeviceStatsInfo.getTxBytes())
                        + ", RxBytes: " + String.valueOf(sDeviceStatsInfo.getRxBytes()) + ", CPU: " + sDeviceStatsInfo.getCpuFrequencyList().toString()
                        + ", Temp: " + sDeviceStatsInfo.getCpuTemperature() + ", Usage: " + sDeviceStatsInfo.getCpuUsage());
                sBuffer = sIterator.next().toString().getBytes();
                if (sFos != null) {
                    sFos.write(sBuffer, 0, sBuffer.length);
                    sFos.flush();
                }
            }
            if (sFos != null)
                sFos.close();
            this.flushStoredData();
        } catch (IOException e) {
            Log.d(TAG, "IOException, e.getMessage(): " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            Log.d(TAG, "Exception, e.getMessage(): " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (sFos != null) {
                try {
                    sFos.flush();
                    sFos.close();
                } catch (IOException e) {};
            }
        }
    }

    public void addToStorage(DeviceStatsInfo deviceStatsInfo) {
        if (this.mDeviceStatsRecordList.size() == 0) { // if it's the first element.
            this.mPivotTxBytes = deviceStatsInfo.getTxBytes();
            this.mPivotRxBytes = deviceStatsInfo.getRxBytes();
            deviceStatsInfo.setTxBytes(0);
            deviceStatsInfo.setRxBytes(0);
        } else {
            long tempRx = deviceStatsInfo.getRxBytes();
            long tempTx = deviceStatsInfo.getTxBytes();
            deviceStatsInfo.setTxBytes(tempTx - this.mPivotTxBytes);
            deviceStatsInfo.setRxBytes(tempRx - this.mPivotRxBytes);
            this.mPivotTxBytes = tempTx;
            this.mPivotRxBytes = tempRx;
        }
        this.mDeviceStatsRecordList.add(deviceStatsInfo);
    }

    public void addToTPutCalculationBuffer(DeviceStatsInfo deviceStatsInfo) {
        DeviceStatsInfo d = deviceStatsInfo.clone();
        if ((this.mDLTPutCircularArray.size() > 0) &&
            ((this.mDLTPutCircularArray.getLast().getTimeStamp() - this.mDLTPutCircularArray.getFirst().getTimeStamp()) >= TPUT_CALCULATION_UNIT_TIME)) {
                this.mDLTPutCircularArray.popFirst();
        }
        this.mDLTPutCircularArray.addLast(d);
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

        Log.d(TAG, "T-put : " + tput);
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
