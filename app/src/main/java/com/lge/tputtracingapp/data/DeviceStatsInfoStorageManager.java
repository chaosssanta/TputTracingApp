package com.lge.tputtracingapp.data;

import android.os.Environment;
import android.support.v4.util.CircularArray;
import android.util.Log;

import com.lge.tputtracingapp.service.DeviceLoggingStateChangedListener;
import com.lge.tputtracingapp.statsreader.CPUStatsReader;
import com.lge.tputtracingapp.statsreader.NetworkStatsReader;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
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
    private static String[] mColumns = null;
    private static boolean DBG = true;
    private static final String mLineFeed = "\n";
    private static final String mCarriageReturn = "\r";
    private static final String mSeperator = ",";
    private static final String mFileExtention = ".csv";
    private int sCpuCnt = -1; //initializing

    @Override
    public void onMonitoringStarted() {
        Log.d(TAG, "Monitoring started ");
    }

    @Override
    public void onMonitoringStopped() {
        Log.d(TAG, "Monitoring started stopped");

    }

    @Override
    public void onLoggingStarted() {
        Log.d(TAG, "Logging Started");
    }

    @Override
    public void onLoggingStopped() {
        Log.d(TAG, "Logging Stopped");
        this.exportToFile(this.generateFileName());
    }

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
    private static SimpleDateFormat sDateTimeFormatter = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss.SSS");

    public static DeviceStatsInfoStorageManager getInstance() {
        if (mInstance == null) {
            mInstance = new DeviceStatsInfoStorageManager();
        }
        return mInstance;
    }

    public int exportToFile(final String fileName) {
        final LinkedList<DeviceStatsInfo> sTargetList = this.mDeviceStatsRecordList;
        this.mDeviceStatsRecordList = new LinkedList<>();

        Log.d(TAG, "exportToFile(), fileName: " + fileName);
        try {
            if (sTargetList == null || sTargetList.getFirst() == null) {}
        } catch (NoSuchElementException e) {
            Log.d(TAG, "sTargetList is null or First element is not exist.");
            return -1;
        }  catch (Exception e) {return -1;}

        mExecutorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        Log.d(TAG, "available thread cnt: " + Runtime.getRuntime().availableProcessors());

        sCpuCnt = sTargetList.getFirst().getCpuFrequencyList().size();
        Log.d(TAG, "sCpuCnt: " + sCpuCnt);
        makeColumns();

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

        return 0;
    }

    private void handleFileWriting(LinkedList<DeviceStatsInfo> targetList, String fileName) {
        String sDirPath = Environment.getExternalStorageDirectory().getAbsolutePath();

        Iterator<DeviceStatsInfo> sIterator = targetList.iterator();

        byte[] sBuffer = null;
        FileOutputStream sFos = null;
        BufferedOutputStream sBos = null;

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
            Log.d(TAG, "Cannot write logs to dir");
        }

        //make file to write raw data
        File sFile = new File(sDir, fileName + mFileExtention);
        boolean isExistFile = sFile.exists();

        //prepare OutputStream and BufferedOutputStream to write logs to file
        try {
            if (isExistFile)
                sFos = new FileOutputStream(sFile, true); //add logs to already exist file
            else
                sFos = new FileOutputStream(sFile); //add logs to new file

            //to speed up file I/O
            sBos = new BufferedOutputStream(sFos);
        } catch (FileNotFoundException e) {
            Log.d(TAG, "FileNotFoundException, e.getMessage(): " + e.getMessage());
            e.printStackTrace();
            return;
        } catch (Exception e) {
            Log.d(TAG, "Exception, e.getMessage(): " + e.getMessage());
            e.printStackTrace();
            return;
        }

        //write raw data using BufferedOutputStream to file created before
        try {
            if (!isExistFile) {
                //first, write columns to file.
                sBos.write(makeColumnNameAsByte());
                sBos.flush();
            }

            //second, write each row's data to file.
            while (sIterator.hasNext()) {
                ++cnt;
                DeviceStatsInfo sDeviceStatsInfo = sIterator.next();
                sBuffer = getEachRowDataAsByte(sDeviceStatsInfo, cnt);
                sBos.write(sBuffer, 0, sBuffer.length);
                sBos.flush();
            }
            this.flushStoredData();
        } catch (IOException e) {
            Log.d(TAG, "IOException, e.getMessage(): " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            Log.d(TAG, "Exception, e.getMessage(): " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (sBos != null) {
                try {
                    sBos.flush(); sFos.close();
                } catch (IOException e) {e.printStackTrace();}
            }
        }
    }

    private void makeColumns() {
        StringBuilder sb = new StringBuilder();
        sb.append("No").append(mSeperator)
                .append("PackageName").append(mSeperator)
                .append("Network").append(mSeperator)
                .append("Direction").append(mSeperator)
                .append("Time").append(mSeperator)
                .append("ReceivedBytes").append(mSeperator)
                .append("SentBytes").append(mSeperator)
                .append("Temperature").append(mSeperator)
                .append("CPU_Occupacy(%)").append(mSeperator);

        for (int i = 0; i < sCpuCnt; i++) {
            sb.append("CPU0_Freq" + i).append(mSeperator);
        }
        mColumns = sb.toString().split(mSeperator);
    }

    private byte[] makeColumnNameAsByte() {
        StringBuilder sColumns = new StringBuilder();

        for (int i=0; i<mColumns.length; i++) {
            sColumns.append(mColumns[i]).append(mSeperator);
        }
        sColumns.append(mCarriageReturn).append(mLineFeed);
        return sColumns.toString().getBytes();
    }

    private byte[] getEachRowDataAsByte(DeviceStatsInfo deviceStatsInfo, int cnt) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.valueOf(cnt)).append(mSeperator)
                .append("packageName").append(mSeperator)
                .append("NetworkType ").append(mSeperator)
                .append("DL").append(mSeperator)
                //.append(getDate(deviceStatsInfo.getTimeStamp())).append(mSeperator)
                .append(deviceStatsInfo.getTimeStamp()).append(mSeperator)
                .append(String.valueOf(deviceStatsInfo.getRxBytes())).append(mSeperator)
                .append(String.valueOf(deviceStatsInfo.getTxBytes())).append(mSeperator)
                .append(deviceStatsInfo.getCpuTemperature()).append(mSeperator)
                .append(deviceStatsInfo.getCpuUsage()).append(mSeperator);

        for (int i=0; i<sCpuCnt; i++) {
            sb.append(deviceStatsInfo.getCpuFrequencyList().get(i)).append(mSeperator);
        }
        sb.append(mCarriageReturn).append(mLineFeed);

        return sb.toString().getBytes();
    }

    private static String getDate(long milliSeconds) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(milliSeconds);
        return sDateTimeFormatter.format(calendar.getTime());
    }

    public void addToStorage(DeviceStatsInfo deviceStatsInfo) {
        try {
            DeviceStatsInfo d = deviceStatsInfo.clone();
            if (this.mDeviceStatsRecordList.size() == 0) { // if it's the first element.
                this.mPivotTxBytes = d.getTxBytes();
                this.mPivotRxBytes = d.getRxBytes();
                d.setTxBytes(0);
                d.setRxBytes(0);
            } else {
                long tempRx = d.getRxBytes();
                long tempTx = d.getTxBytes();
                d.setTxBytes(tempTx - this.mPivotTxBytes);
                d.setRxBytes(tempRx - this.mPivotRxBytes);
                this.mPivotTxBytes = tempTx;
                this.mPivotRxBytes = tempRx;
            }
            this.mDeviceStatsRecordList.add(d);
        } catch(Exception e){};
    }

    public void addToTPutCalculationBuffer(DeviceStatsInfo deviceStatsInfo) {

        if ((this.mDLTPutCircularArray.size() > 0) &&
            ((this.mDLTPutCircularArray.getLast().getTimeStamp() - this.mDLTPutCircularArray.getFirst().getTimeStamp()) >= TPUT_CALCULATION_UNIT_TIME)) {
                this.mDLTPutCircularArray.popFirst();
        }
        this.mDLTPutCircularArray.addLast(deviceStatsInfo);
    }

    public void migrateFromTPutCalculationBufferToRecordBuffer() {
        Log.d(TAG, "migrateFromTPutCalculationBufferToRecordBuffer()");

        Log.d(TAG, "calculationBufferSIze : " + this.mDLTPutCircularArray.size());
        Log.d(TAG, "recordBufferSize : " + this.mDeviceStatsRecordList.size());

        for (int i = 0; i != this.mDLTPutCircularArray.size() - 1; ++i) {
            try{this.addToStorage(this.mDLTPutCircularArray.get(i).clone());} catch(Exception e){}
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

    public DeviceStatsInfo readCurrentDeviceStatsInfo(int targetUid, String cpuTemperatureFilePath, String cpuClockFilePath) {
        DeviceStatsInfo deviceStatsInfo = new DeviceStatsInfo();
        deviceStatsInfo.setTimeStamp(System.currentTimeMillis());
        deviceStatsInfo.setTxBytes(NetworkStatsReader.getTxBytesByUid(targetUid));
        deviceStatsInfo.setRxBytes(NetworkStatsReader.getRxBytesByUid(targetUid));
        deviceStatsInfo.setCpuTemperature(CPUStatsReader.getThermalInfo(cpuTemperatureFilePath));
        deviceStatsInfo.setCpuFrequencyList(CPUStatsReader.getInstance().getCpuFreq(cpuClockFilePath));
        deviceStatsInfo.setCpuUsage(CPUStatsReader.getInstance().getCpuUsage());
        return deviceStatsInfo;
    }

    private static String generateFileName() {
        return System.currentTimeMillis() + "";
    }

    private void flushStoredData() {
        this.mDeviceStatsRecordList.clear();
    }
}
