package com.lge.tputtracingapp.service;

import com.lge.tputtracingapp.dto.DeviceStatsInfo;
import com.lge.tputtracingapp.logger.CPUStatsReader;
import com.lge.tputtracingapp.logger.NetworkStatsReader;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;

@Accessors(prefix = "m")
public class MonitoringService extends Service {

    private static String TAG = MonitoringService.class.getSimpleName();

    private static final int EVENT_LOG_NOW = 0x10;
    private static final int EVENT_STOP_LOGGING = 0x11;
    private static final int EVENT_START_LOGGING = 0x12;
        
    public class ServiceBinder extends Binder {
        public MonitoringService getService() {
            return MonitoringService.this;
        }
    }
    
    private Handler mServiceLogicHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case EVENT_STOP_LOGGING:
                Log.d(TAG, "EVENT_STOP_LOGGING handled");
                if (this.hasMessages(EVENT_LOG_NOW)) {
                    removeMessages(EVENT_LOG_NOW);
                    if (mLoggingStateChangedListener != null) {
                        mLoggingStateChangedListener.onLoggingStateChanged(false);
                    }
                }
                break;

            case EVENT_START_LOGGING:
                sendEmptyMessage(EVENT_LOG_NOW);
                if (mLoggingStateChangedListener != null) {
                    mLoggingStateChangedListener.onLoggingStateChanged(true);
                }
                break;
                
            case EVENT_LOG_NOW:
                DeviceStatsInfo seg = new DeviceStatsInfo();
                seg.setTxBytes(NetworkStatsReader.getTxBytesByUid(mTargetUid));
                seg.setRxBytes(NetworkStatsReader.getRxBytesByUid(mTargetUid));
                seg.setCpuTemperature(CPUStatsReader.getThermalInfo(mCPUTemperatureFilePath));

                ArrayList<Integer> tmpList = new ArrayList<Integer>();
                tmpList.add(101010);
                tmpList.add(333333);
                tmpList.add(454454);

                seg.setCpuFrequencyList(tmpList);

                Log.d(TAG, seg.toString());

                sendEmptyMessageDelayed(EVENT_LOG_NOW, mLoggingInterval);
                break;
            default:
                break;
            }
        }
    };

    @Setter private int mLoggingInterval;
    @Setter private String mTargetPackageName;
    @Setter private int mTargetUid;
    @Setter private String mCPUClockFilePath;
    @Setter private String mCPUTemperatureFilePath;
    @Setter private int mCPUCoreCount;
    @Setter private LoggingStateChangedListener mLoggingStateChangedListener;

    private IBinder mServiceBinder;
    
    // constructor
    public MonitoringService() {
        Log.d(TAG, "MonitoringService()");
        this.mServiceBinder = new ServiceBinder();
        this.mLoggingInterval = 1000;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
    }
    
    // monitoring controller
    public void startLogging(String targetPackageName, int loggingInterval, String cpuClockFilePath, String thermalFilePath, int cpuCoreCount) {
        Message msg = this.mServiceLogicHandler.obtainMessage();
        msg.what = EVENT_START_LOGGING;

        setTargetPackageName(targetPackageName);
        setTargetUid(MonitoringService.getUidByPackageName(this, this.mTargetPackageName));
        setLoggingInterval(loggingInterval);
        setCPUClockFilePath(cpuClockFilePath);
        setCPUTemperatureFilePath(thermalFilePath);
        setCPUCoreCount(cpuCoreCount);

        Log.d(TAG, "Start Logging based on the following information :");
        Log.d(TAG, "TargetPackageName : " + this.mTargetPackageName);
        Log.d(TAG, "TargetUid : " + this.mTargetUid);
        Log.d(TAG, "CPU Temperature file path : " + this.mCPUTemperatureFilePath);
        Log.d(TAG, "CPU clock file path : " + this.mCPUClockFilePath);
        Log.d(TAG, "Num of cores : " + this.mCPUCoreCount);

        this.mServiceLogicHandler.sendMessage(msg);
    }
    
    public void stopLogging() {
        Message msg = this.mServiceLogicHandler.obtainMessage();
        msg.what = EVENT_STOP_LOGGING;
        this.mServiceLogicHandler.sendMessage(msg);
    }
    
    public boolean isMonitoringInProgress() {
        if (this.mServiceLogicHandler.hasMessages(EVENT_LOG_NOW)) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind(Intent)");
        return this.mServiceBinder;
    }

    // static method
    private static int getUidByPackageName(Context context, String packageName) {
        try {
            return context.getPackageManager().getApplicationInfo(packageName, PackageManager.GET_META_DATA).uid;
        } catch (NameNotFoundException e) {
            return -1;
        }
    }
}
