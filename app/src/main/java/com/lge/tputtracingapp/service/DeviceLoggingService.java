package com.lge.tputtracingapp.service;

import com.lge.tputtracingapp.dto.DeviceStatsInfo;
import com.lge.tputtracingapp.logger.CPUStatsReader;
import com.lge.tputtracingapp.logger.NetworkStatsReader;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;

@Accessors(prefix = "m")
public class DeviceLoggingService extends Service {

    private static String TAG = DeviceLoggingService.class.getSimpleName();

    private static final int EVENT_LOG_NOW = 0x10;
    private static final int EVENT_STOP_LOGGING = 0x11;
    private static final int EVENT_START_LOGGING = 0x12;
        


    private Handler mServiceLogicHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case EVENT_STOP_LOGGING:
                Log.d(TAG, "EVENT_STOP_LOGGING handled");
                if (this.hasMessages(EVENT_LOG_NOW)) {
                    removeMessages(EVENT_LOG_NOW);
                }
                break;

            case EVENT_START_LOGGING:
                sendEmptyMessage(EVENT_LOG_NOW);
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

    // constructor
    public DeviceLoggingService() {
        Log.d(TAG, "DeviceLoggingService()");
        this.mLoggingInterval = 1000;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
    }

    public static final String SHARED_PREFERENCES_NAME = "device_Logging_service_pref";
    public static final String SHARED_PREFERENCES_KEY_PACKAGE_NAME = "package_name";
    public static final String SHARED_PREFERENCES_DEFAULT_PACKAGE_NAME = "com.google.android.youtube";

    public static final String SHARED_PREFERENCES_KEY_CPU_FILE_PATH = "thermal_file_path";
    public static final String SHARED_PREFERENCES_DEFAULT_CPU_FILE_PATH = "";

    public static final String SHARED_PREFERENCES_KEY_THERMAL_FILE_PATH = "cpu_file_path";
    public static final String SHARED_PREFERENCES_DEFAULT_THERMAL_FILE_PATH = "";

    public static final String SHARED_PREFERENCES_KEY_INTERVAL = "interval";
    public static final int SHARED_PREFERENCES_DEFAULT_INTERVAL = 1000;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, MODE_PRIVATE);
        String packageName, cpuFilePath, thermalFilePath;
        int interval;
        if (intent == null) {
            packageName = sharedPreferences.getString(SHARED_PREFERENCES_KEY_PACKAGE_NAME, SHARED_PREFERENCES_DEFAULT_PACKAGE_NAME);
            cpuFilePath = sharedPreferences.getString(SHARED_PREFERENCES_KEY_CPU_FILE_PATH, SHARED_PREFERENCES_DEFAULT_CPU_FILE_PATH);
            thermalFilePath = sharedPreferences.getString(SHARED_PREFERENCES_KEY_THERMAL_FILE_PATH, SHARED_PREFERENCES_DEFAULT_THERMAL_FILE_PATH);
            interval = sharedPreferences.getInt(SHARED_PREFERENCES_KEY_INTERVAL, SHARED_PREFERENCES_DEFAULT_INTERVAL);
        } else {
            packageName = intent.getStringExtra("package_name");
            cpuFilePath = intent.getStringExtra("cpu_file_path");
            thermalFilePath = intent.getStringExtra("thermal_file_path");
            interval = intent.getIntExtra("interval", 1000);

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(SHARED_PREFERENCES_KEY_PACKAGE_NAME, packageName);
            editor.putString(SHARED_PREFERENCES_KEY_CPU_FILE_PATH, cpuFilePath);
            editor.putString(SHARED_PREFERENCES_KEY_THERMAL_FILE_PATH, thermalFilePath);
            editor.putInt(SHARED_PREFERENCES_KEY_INTERVAL, interval);
            editor.commit();
        }

        startLogging(packageName, interval, cpuFilePath, thermalFilePath);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        if (this.mServiceLogicHandler.hasMessages(EVENT_LOG_NOW)) {
            this.mServiceLogicHandler.removeMessages(EVENT_LOG_NOW);
        }
        this.mServiceLogicHandler.sendEmptyMessage(EVENT_STOP_LOGGING);
        super.onDestroy();
    }

    // monitoring controller
    private void startLogging(String targetPackageName, int loggingInterval, String cpuClockFilePath, String thermalFilePath) {
        Message msg = this.mServiceLogicHandler.obtainMessage();
        msg.what = EVENT_START_LOGGING;

        setTargetPackageName(targetPackageName);
        setTargetUid(DeviceLoggingService.getUidByPackageName(this, this.mTargetPackageName));
        setLoggingInterval(loggingInterval);
        setCPUClockFilePath(cpuClockFilePath);
        setCPUTemperatureFilePath(thermalFilePath);

        Log.d(TAG, "Start Logging based on the following information :");
        Log.d(TAG, "TargetPackageName : " + this.mTargetPackageName);
        Log.d(TAG, "TargetUid : " + this.mTargetUid);
        Log.d(TAG, "CPU Temperature file path : " + this.mCPUTemperatureFilePath);
        Log.d(TAG, "CPU clock file path : " + this.mCPUClockFilePath);

        this.mServiceLogicHandler.sendMessage(msg);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
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
