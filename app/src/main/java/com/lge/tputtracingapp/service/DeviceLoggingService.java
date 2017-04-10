package com.lge.tputtracingapp.service;

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

import com.lge.tputtracingapp.data.DeviceStatsInfo;
import com.lge.tputtracingapp.data.DeviceStatsInfoStorageManager;

import java.util.ArrayList;

import lombok.Setter;
import lombok.experimental.Accessors;

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

    public static final String SHARED_PREFERENCES_KEY_THRESHOLD_TIME = "threshold_time";
    private static final int SHARED_PREFERENCES_DEFAULT_THRESHOLD_TIME = 5;

    public static final String SHARED_PREFERENCES_KEY_TEST_TYPE = "test_type";
    public static final int SHARED_PREFERENCES_DL_DIRECTION = 0;
    public static final int SHARED_PREFERENCES_UL_DIRECTION = 1;

    public static final String SHARED_PREFERENCES_KEY_SELECTED_PACKAGE_NAME = "selected_package_name";

    private static final int EVENT_START_MONITORING = 0x10;
    private static final int EVENT_STOP_MONITORING = 0x11;
    private static final int EVENT_START_LOGGING = 0x12;
    private static final int EVENT_STOP_LOGGING = 0x13;
    private static final int EVENT_GET_CURRENT_STATS_INFO = 0x15;
    private static final int EVENT_LOG_CURRENT_STATS_INFO = 0x16;
    private static final float TPUT_THRESHOLD = 1.0f;

    private Handler mServiceLogicHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
            case EVENT_START_MONITORING:
                Log.d(TAG, "EVENT_START_MONITORING handled");
                for (DeviceLoggingStateChangedListener l : mDeviceLoggingStateListenerList) {
                    l.onMonitoringStarted();
                }
                sendEmptyMessage(EVENT_GET_CURRENT_STATS_INFO);
                break;

            case EVENT_STOP_MONITORING:
                Log.d(TAG, "EVENT_STOP_MONITORING handled");

                for (DeviceLoggingStateChangedListener l : mDeviceLoggingStateListenerList) {
                    l.onMonitoringStopped();
                }

                // remove all messages
                removeMessages(EVENT_START_MONITORING);
                removeMessages(EVENT_START_LOGGING);
                removeMessages(EVENT_STOP_LOGGING);
                removeMessages(EVENT_GET_CURRENT_STATS_INFO);
                removeMessages(EVENT_LOG_CURRENT_STATS_INFO);

                //DeviceStatsInfoStorageManager.getInstance().exportToFile(System.currentTimeMillis() + "");
                break;

            case EVENT_START_LOGGING:
                Log.d(TAG, "EVENT_START_LOGGING");

                for (DeviceLoggingStateChangedListener l : mDeviceLoggingStateListenerList) {
                    l.onLoggingStarted();
                }

                DeviceStatsInfoStorageManager.getInstance(DeviceLoggingService.this.getApplicationContext()).migrateFromTPutCalculationBufferToRecordBuffer();
                DeviceStatsInfoStorageManager.getInstance(DeviceLoggingService.this.getApplicationContext()).addToStorage((DeviceStatsInfo) msg.obj);
                sendEmptyMessageDelayed(EVENT_LOG_CURRENT_STATS_INFO, mLoggingInterval);
                break;

            case EVENT_STOP_LOGGING:
                Log.d(TAG, "EVENT_STOP_LOGGING");

                for (DeviceLoggingStateChangedListener l : mDeviceLoggingStateListenerList) {
                    l.onLoggingStopped();
                }
                //DeviceStatsInfoStorageManager.getInstance().exportToFile(System.currentTimeMillis() + "");
                sendEmptyMessageDelayed(EVENT_START_MONITORING, mLoggingInterval);
                break;

            case EVENT_LOG_CURRENT_STATS_INFO: {
                Log.d(TAG, "EVENT_LOG_CURRENT_STATS_INFO");
                DeviceStatsInfo sDeviceStatsInfo = DeviceStatsInfoStorageManager.getInstance(DeviceLoggingService.this.getApplicationContext()).readCurrentDeviceStatsInfo(mTargetUid, mCPUTemperatureFilePath, mCPUClockFilePath, mTargetPackageName, mDirection, mNetworkType);

                DeviceStatsInfoStorageManager.getInstance(DeviceLoggingService.this.getApplicationContext()).addToTPutCalculationBuffer(sDeviceStatsInfo);
                DeviceStatsInfoStorageManager.getInstance(DeviceLoggingService.this.getApplicationContext()).addToStorage(sDeviceStatsInfo);

                if (DeviceStatsInfoStorageManager.getInstance(DeviceLoggingService.this.getApplicationContext()).getAvgTputFromTpuCalculationBuffer(mDirection) < TPUT_THRESHOLD) {
                    sendEmptyMessage(EVENT_STOP_LOGGING);
                } else {
                    sendEmptyMessageDelayed(EVENT_LOG_CURRENT_STATS_INFO, mLoggingInterval);
                }

                break;
            }
            case EVENT_GET_CURRENT_STATS_INFO: {
                Log.d(TAG, "EVENT_GET_CURRENT_STATS_INFO");

                DeviceStatsInfo sDeviceStatsInfo = DeviceStatsInfoStorageManager.getInstance(DeviceLoggingService.this.getApplicationContext()).readCurrentDeviceStatsInfo(mTargetUid, mCPUTemperatureFilePath, mCPUClockFilePath, mTargetPackageName, mDirection, mNetworkType);
                DeviceStatsInfoStorageManager.getInstance(DeviceLoggingService.this.getApplicationContext()).addToTPutCalculationBuffer(sDeviceStatsInfo);

                // if the avg t-put exceeds threshold, it's time to start logging.
                if (DeviceStatsInfoStorageManager.getInstance(DeviceLoggingService.this.getApplicationContext()).getAvgTputFromTpuCalculationBuffer(mDirection) > TPUT_THRESHOLD) {
                    Message eventMessage = this.obtainMessage(EVENT_START_LOGGING);
                    eventMessage.obj = sDeviceStatsInfo;
                    sendMessage(eventMessage);
                } else {
                    sendEmptyMessageDelayed(EVENT_GET_CURRENT_STATS_INFO, mLoggingInterval);
                }
            }
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
    @Setter private DeviceStatsInfoStorageManager.TEST_TYPE mDirection;
    @Setter private int mNetworkType;

    private ArrayList<DeviceLoggingStateChangedListener> mDeviceLoggingStateListenerList;

    // constructor
    public DeviceLoggingService() {
        Log.d(TAG, "DeviceLoggingService()");
        this.mLoggingInterval = 1000;
    }

    public void setOnLoggingStateChangedListener(DeviceLoggingStateChangedListener dlsc) {
        this.mDeviceLoggingStateListenerList.add(dlsc);
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences sSharedPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, MODE_PRIVATE);
        String sPackageName, sCpuFilePath, sThermalFilePath, sSelectedPackageName;
        int sInterval, sThresholdTime;
        DeviceStatsInfoStorageManager.TEST_TYPE sDirection;
        //test start
        int sNetworkType = 14; //hard coding, eg,.LTE is 14.
        //test end

        if (intent == null) {
            sPackageName = sSharedPreferences.getString(SHARED_PREFERENCES_KEY_PACKAGE_NAME, SHARED_PREFERENCES_DEFAULT_PACKAGE_NAME);
//            selectedPackageName = sharedPreferences.getString(SHARED_PREFERENCES_KEY_SELECTED_PACKAGE_NAME, "");
            sCpuFilePath = sSharedPreferences.getString(SHARED_PREFERENCES_KEY_CPU_CLOCK_FILE_PATH, SHARED_PREFERENCES_DEFAULT_CPU_CLOCK_FILE_PATH);
            sThermalFilePath = sSharedPreferences.getString(SHARED_PREFERENCES_KEY_THERMAL_FILE_PATH, SHARED_PREFERENCES_DEFAULT_THERMAL_FILE_PATH);
            sInterval = sSharedPreferences.getInt(SHARED_PREFERENCES_KEY_INTERVAL, SHARED_PREFERENCES_DEFAULT_INTERVAL);
            sThresholdTime = sSharedPreferences.getInt(SHARED_PREFERENCES_KEY_THRESHOLD_TIME, SHARED_PREFERENCES_DEFAULT_THRESHOLD_TIME);
            sDirection = sSharedPreferences.getInt(SHARED_PREFERENCES_KEY_TEST_TYPE, SHARED_PREFERENCES_DL_DIRECTION) == SHARED_PREFERENCES_DL_DIRECTION ? DeviceStatsInfoStorageManager.TEST_TYPE.DL_TEST : DeviceStatsInfoStorageManager.TEST_TYPE.UL_TEST;
        } else {
            sPackageName = intent.getStringExtra(SHARED_PREFERENCES_KEY_PACKAGE_NAME);
//            selectedPackageName = intent.getStringExtra(SHARED_PREFERENCES_KEY_SELECTED_PACKAGE_NAME);
            sCpuFilePath = intent.getStringExtra(SHARED_PREFERENCES_KEY_CPU_CLOCK_FILE_PATH);
            sThermalFilePath = intent.getStringExtra(SHARED_PREFERENCES_KEY_THERMAL_FILE_PATH);
            sInterval = intent.getIntExtra(SHARED_PREFERENCES_KEY_INTERVAL, SHARED_PREFERENCES_DEFAULT_INTERVAL);
            sThresholdTime = intent.getIntExtra(SHARED_PREFERENCES_KEY_THRESHOLD_TIME, SHARED_PREFERENCES_DEFAULT_THRESHOLD_TIME);
            sDirection = intent.getIntExtra(SHARED_PREFERENCES_KEY_TEST_TYPE, SHARED_PREFERENCES_DL_DIRECTION) == SHARED_PREFERENCES_DL_DIRECTION ? DeviceStatsInfoStorageManager.TEST_TYPE.DL_TEST: DeviceStatsInfoStorageManager.TEST_TYPE.UL_TEST;

            SharedPreferences.Editor sEditor = sSharedPreferences.edit();
            sEditor.putString(SHARED_PREFERENCES_KEY_PACKAGE_NAME, sPackageName);
            sEditor.putString(SHARED_PREFERENCES_KEY_CPU_CLOCK_FILE_PATH, sCpuFilePath);
            sEditor.putString(SHARED_PREFERENCES_KEY_THERMAL_FILE_PATH, sThermalFilePath);
            sEditor.putInt(SHARED_PREFERENCES_KEY_INTERVAL, sInterval);
            sEditor.putInt(SHARED_PREFERENCES_KEY_THRESHOLD_TIME, sThresholdTime);
            sEditor.putInt(SHARED_PREFERENCES_KEY_TEST_TYPE, (sDirection == DeviceStatsInfoStorageManager.TEST_TYPE.DL_TEST) ? 0 : 1);
            sEditor.commit();
        }

        this.mDeviceLoggingStateListenerList = new ArrayList<>();
        this.setOnLoggingStateChangedListener(DeviceStatsInfoStorageManager.getInstance(this.getApplicationContext()));

        startMonitoringDeviceStats(sPackageName, sInterval, sCpuFilePath, sThermalFilePath, sThresholdTime, sDirection, sNetworkType);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        this.mServiceLogicHandler.removeMessages(EVENT_GET_CURRENT_STATS_INFO);
        this.mServiceLogicHandler.removeMessages(EVENT_LOG_CURRENT_STATS_INFO);
        super.onDestroy();
    }

    // monitoring controller
    private void startMonitoringDeviceStats(String targetPackageName, int loggingInterval, String cpuClockFilePath, String thermalFilePath, int dlCompleteDecisionTimeThreshold, DeviceStatsInfoStorageManager.TEST_TYPE direction, int networkType) {
        Message sMsg = this.mServiceLogicHandler.obtainMessage();
        sMsg.what = EVENT_START_MONITORING;

        setTargetPackageName(targetPackageName);
        setTargetUid(DeviceLoggingService.getUidByPackageName(this, this.mTargetPackageName));
        setLoggingInterval(loggingInterval);
        setCPUClockFilePath(cpuClockFilePath);
        setCPUTemperatureFilePath(thermalFilePath);
        setDLCompleteDecisionTimeThreshold(dlCompleteDecisionTimeThreshold);
        setDirection(direction);
        setNetworkType(networkType);

        Log.d(TAG, "Start Logging based on the following information :");
        Log.d(TAG, "Direction : " + this.mDirection);
        Log.d(TAG, "TargetPackageName : " + this.mTargetPackageName);
        Log.d(TAG, "TargetUid : " + this.mTargetUid);
        Log.d(TAG, "CPU Temperature file path : " + this.mCPUTemperatureFilePath);
        Log.d(TAG, "CPU clock file path : " + this.mCPUClockFilePath);
        Log.d(TAG, "DL Complete time threshold value : " + this.mDLCompleteDecisionTimeThreshold);
        Log.d(TAG, "NetworkType : " + this.mNetworkType);

        this.mServiceLogicHandler.sendMessage(sMsg);
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
