package com.lge.tputtracingapp.service;

import com.lge.tputtracingapp.data.DeviceStatsInfo;
import com.lge.tputtracingapp.data.DeviceStatsInfoStorage;
import com.lge.tputtracingapp.statsreader.CPUStatsReader;
import com.lge.tputtracingapp.statsreader.NetworkStatsReader;

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

    public static final String SHARED_PREFERENCES_NAME = "device_Logging_service_pref";

    public static final String SHARED_PREFERENCES_KEY_PACKAGE_NAME = "package_name";
    private static final String SHARED_PREFERENCES_DEFAULT_PACKAGE_NAME = "com.google.android.youtube";

    public static final String SHARED_PREFERENCES_KEY_CPU_CLOCK_FILE_PATH = "cpu_file_path";
    private static final String SHARED_PREFERENCES_DEFAULT_CPU_CLOCK_FILE_PATH = "/sys/devices/system/cpu/";

    public static final String SHARED_PREFERENCES_KEY_THERMAL_FILE_PATH = "thermal_file_path";
    private static final String SHARED_PREFERENCES_DEFAULT_THERMAL_FILE_PATH = "/sys/class/hwmon/hwmon2/device/xo_therm";

    public static final String SHARED_PREFERENCES_KEY_INTERVAL = "interval";
    private static final int SHARED_PREFERENCES_DEFAULT_INTERVAL = 1000;

    public static final String SHARED_PREFERENCES_THRESHOLD_TIME = "threshold_time";

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
                // TODO : Below codes in the EVENT_LOG_NOW codes will be replaced with DeviceStatsInfoStorage functions.
                /*
                * DeviceStatsInfoStorage will be a singleton class which manage the cpu, thermal, and network stats info.
                * it will store all the data that is logged and export them on demand,
                * Hence the below is limited only to a test purpose, and surely will be removed eventually
                *
                * By letting DeviceStatsInfoStorage manage actual data logging and exporting,
                * DeviceLoggingService can be seperated from device data gathering task.
                * */
                DeviceStatsInfo deviceStatsInfo = new DeviceStatsInfo();
                deviceStatsInfo.setTimeStamp(System.currentTimeMillis());
                deviceStatsInfo.setTxBytes(NetworkStatsReader.getTxBytesByUid(mTargetUid));
                deviceStatsInfo.setRxBytes(NetworkStatsReader.getRxBytesByUid(mTargetUid));
                deviceStatsInfo.setCpuTemperature(CPUStatsReader.getThermalInfo(mCPUTemperatureFilePath));
                deviceStatsInfo.setCpuFrequencyList(CPUStatsReader.getCpuFreq(mCPUClockFilePath));
                Log.d(TAG, mCPUClockFilePath);
                Log.d(TAG, deviceStatsInfo.toString());

                DeviceStatsInfoStorage.getInstance().add(deviceStatsInfo);

                Log.d(TAG, "T-put : " + DeviceStatsInfoStorage.getInstance().getAvgTputForTheLatestSeconds(mDLCompleteDecisionTimeThreshold, mLoggingInterval) + "");

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
    @Setter private int mDLCompleteDecisionTimeThreshold = 3;

    // constructor
    public DeviceLoggingService() {
        Log.d(TAG, "DeviceLoggingService()");
        this.mLoggingInterval = 1000;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences sharedPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, MODE_PRIVATE);
        String packageName, cpuFilePath, thermalFilePath;
        int interval, sThresholdTime;
        if (intent == null) {
            packageName = sharedPreferences.getString(SHARED_PREFERENCES_KEY_PACKAGE_NAME, SHARED_PREFERENCES_DEFAULT_PACKAGE_NAME);
            cpuFilePath = sharedPreferences.getString(SHARED_PREFERENCES_KEY_CPU_CLOCK_FILE_PATH, SHARED_PREFERENCES_DEFAULT_CPU_CLOCK_FILE_PATH);
            thermalFilePath = sharedPreferences.getString(SHARED_PREFERENCES_KEY_THERMAL_FILE_PATH, SHARED_PREFERENCES_DEFAULT_THERMAL_FILE_PATH);
            interval = sharedPreferences.getInt(SHARED_PREFERENCES_KEY_INTERVAL, SHARED_PREFERENCES_DEFAULT_INTERVAL);
        } else {
            packageName = intent.getStringExtra(SHARED_PREFERENCES_KEY_PACKAGE_NAME);
            cpuFilePath = intent.getStringExtra(SHARED_PREFERENCES_KEY_CPU_CLOCK_FILE_PATH);
            thermalFilePath = intent.getStringExtra(SHARED_PREFERENCES_KEY_THERMAL_FILE_PATH);
            interval = intent.getIntExtra(SHARED_PREFERENCES_KEY_INTERVAL, SHARED_PREFERENCES_DEFAULT_INTERVAL);
            sThresholdTime = intent.getIntExtra(SHARED_PREFERENCES_THRESHOLD_TIME, 5);

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(SHARED_PREFERENCES_KEY_PACKAGE_NAME, packageName);
            editor.putString(SHARED_PREFERENCES_KEY_CPU_CLOCK_FILE_PATH, cpuFilePath);
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
