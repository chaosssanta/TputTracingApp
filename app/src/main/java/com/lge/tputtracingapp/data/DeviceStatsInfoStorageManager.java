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
    private static final String[] mColumns = {"No", "PackageName", "Network", "Time", "ReceivedBytes", "SentBytes", "Temperature", "CPU_Occupacy(%)", "CPU0_Freq", "CPU1_Freq", "CPU2_Freq", "CPU3_Freq"};
    private static boolean DBG = true;

    public enum TEST_TYPE {
        DL_TEST, UL_TEST
    }

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
        } catch (InterruptedException | ExecutionException e) {
            Log.d(TAG, "InterruptedException or ExecutionException, e.getMessage: " + e.getMessage());
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
        StringBuilder sb = new StringBuilder();
        int cnt = 0; //initializing

        //make directory
        File sDir = new File(sDirPath+"/TputTracingApp_Logs");
        if (!sDir.exists()) {
            sIsMadeDir = sDir.mkdir();
        }
        Log.d(TAG, "sIsMadeDir: " + sIsMadeDir + ", Directory path for log files: " + sDirPath + "/TputTracingApp_Logs");

        if (!sDir.canWrite()) {
            Log.d(TAG, "Cannot write logs to files");
        }

        //make csv file to write raw data
        File sFile = new File(sDir, fileName+".csv");
        try {
            sFos = new FileOutputStream(sFile);
        } catch (FileNotFoundException e) {
            Log.d(TAG, "FileNotFoundException, e.getMessage(): " + e.getMessage());
            e.printStackTrace();
            return;
        } catch (Exception e) {
            Log.d(TAG, "Exception, e.getMessage(): " + e.getMessage());
            e.printStackTrace();
            return;
        }

        //write raw data to file created before
        try {
            //first, write columns to file.
            sFos.write(makeColumnNameAsByte());
            sFos.flush();

            //second, write each row's data to file.
            while (sIterator.hasNext()) {
                ++cnt;
                DeviceStatsInfo sDeviceStatsInfo = sIterator.next();
                sBuffer = getEachRowDataAsByte(sDeviceStatsInfo, cnt);
                sFos.write(sBuffer, 0, sBuffer.length);
                sFos.flush();
            }
            cnt = 0;
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
                } catch (IOException e) {e.printStackTrace();};
            }
        }
    }

    private byte[] makeColumnNameAsByte() {
        StringBuilder sColumns = new StringBuilder();

        for (int i=0; i<mColumns.length; i++) {
            sColumns.append(mColumns[i]).append(",");
        }
        sColumns.append("\n");

        Log.d(TAG, "sColumns: " + sColumns);
        return sColumns.toString().getBytes();
    }

    private byte[] getEachRowDataAsByte(DeviceStatsInfo deviceStatsInfo, int cnt) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.valueOf(cnt)).append(",")
                .append("packageName").append(",")
                .append("NetworkType ").append(",")
                .append(String.valueOf(deviceStatsInfo.getTimeStamp())).append(",")
                .append(String.valueOf(deviceStatsInfo.getRxBytes())).append(",")
                .append(String.valueOf(deviceStatsInfo.getTxBytes())).append(",")
                .append(deviceStatsInfo.getCpuTemperature()).append(",")
                .append(deviceStatsInfo.getCpuUsage()).append(",")
                .append(deviceStatsInfo.getCpuFrequencyList().get(0)).append(",")
                .append(deviceStatsInfo.getCpuFrequencyList().get(1)).append(",")
                .append(deviceStatsInfo.getCpuFrequencyList().get(2)).append(",")
                .append(deviceStatsInfo.getCpuFrequencyList().get(3))
                .append("\n");

        return sb.toString().getBytes();
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

    public void migrateFromTPutCalculationBufferToRecordBuffer() {
        Log.d(TAG, "migrateFromTPutCalculationBufferToRecordBuffer()");

        Log.d(TAG, "calculationBufferSIze : " + this.mDLTPutCircularArray.size());
        Log.d(TAG, "recordBufferSize : " + this.mDeviceStatsRecordList.size());

        for (int i = 0; i != this.mDLTPutCircularArray.size(); ++i) {
            this.addToStorage(this.mDLTPutCircularArray.get(i).clone());
        }

        Log.d(TAG, "calcul ****************************");
        for (int i = 0; i != this.mDLTPutCircularArray.size(); ++i) {
            Log.d(TAG, this.mDLTPutCircularArray.get(i).toString());
        }

        Log.d(TAG, "record ****************************");
        for (int i = 0; i != this.mDeviceStatsRecordList.size(); ++i) {
            Log.d(TAG, this.mDeviceStatsRecordList.get(i).toString());
        }
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
