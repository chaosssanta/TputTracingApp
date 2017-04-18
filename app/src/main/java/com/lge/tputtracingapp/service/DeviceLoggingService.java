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

    private static final int EVENT_FIRE_UP_MONITORING_LOOP = 0x10;
    private static final int EVENT_TERMINATE_MONITORING_LOOP = 0x11;

    private static final int EVENT_ENTER_IDLE_MONITORING_STATE = 0x12;
    private static final int EVENT_EXIT_IDLE_MONITORING_STATE = 0x13;

    private static final int EVENT_ENTER_RECORDING_STATE = 0x14;
    private static final int EVENT_EXIT_RECORDING_STATE = 0x15;

    private static final int EVENT_READ_DEVICE_STATS_INFO = 0x18;
    private static final int EVENT_RECORD_CURRENT_STATS_INFO = 0x19;

    private static final float TPUT_THRESHOLD = 1.0f;

    private Handler mServiceLogicHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case EVENT_FIRE_UP_MONITORING_LOOP:
                Log.d(TAG, "EVENT_FIRE_UP_MONITORING_LOOP");
                for (DeviceMonitoringStateChangedListener l : mDeviceLoggingStateListenerList) {
                    l.onMonitoringLoopStarted();
                }
                sendEmptyMessage(EVENT_ENTER_IDLE_MONITORING_STATE);
                break;

                case EVENT_TERMINATE_MONITORING_LOOP:
                    Log.d(TAG, "EVENT_TERMINATE_MONITORING_LOOP");
                    if (this.hasMessages(EVENT_RECORD_CURRENT_STATS_INFO)) {
                        Log.d(TAG, "it was in logging state, hence calling onRecordingStopped() callbacks");
                        removeMessages(EVENT_RECORD_CURRENT_STATS_INFO);
                        sendEmptyMessage(EVENT_EXIT_RECORDING_STATE);
                        sendEmptyMessage(EVENT_EXIT_IDLE_MONITORING_STATE);
                        break;
                    } else {
                        for (DeviceMonitoringStateChangedListener l : mDeviceLoggingStateListenerList) {
                            l.onMonitoringLoopStopped();
                        }

                        // remove all messages
                        removeMessages(EVENT_ENTER_IDLE_MONITORING_STATE);
                        removeMessages(EVENT_EXIT_RECORDING_STATE);
                        removeMessages(EVENT_READ_DEVICE_STATS_INFO);
                        removeMessages(EVENT_RECORD_CURRENT_STATS_INFO);
                    }
                    break;

                case EVENT_ENTER_IDLE_MONITORING_STATE:
                    Log.d(TAG, "EVENT_ENTER_IDLE_MONITORING_STATE ");
                    sendEmptyMessage(EVENT_READ_DEVICE_STATS_INFO);
                    break;

                case EVENT_EXIT_IDLE_MONITORING_STATE:
                    Log.d(TAG, "EVENT_EXIT_IDLE_MONITORING_STATE ");
                    removeMessages(EVENT_ENTER_IDLE_MONITORING_STATE);
                    removeMessages(EVENT_FIRE_UP_MONITORING_LOOP);
                    removeMessages(EVENT_ENTER_RECORDING_STATE);
                    removeMessages(EVENT_READ_DEVICE_STATS_INFO);
                    removeMessages(EVENT_RECORD_CURRENT_STATS_INFO);

                    sendEmptyMessage(EVENT_TERMINATE_MONITORING_LOOP);
                    break;

                case EVENT_READ_DEVICE_STATS_INFO:
                    Log.d(TAG, "EVENT_READ_DEVICE_STATS_INFO");

                    DeviceStatsInfo sDeviceStatsInfo = DeviceStatsInfoStorageManager.getInstance(DeviceLoggingService.this.getApplicationContext()).readCurrentDeviceStatsInfo(mTargetUid, mCPUTemperatureFilePath, mCPUClockFilePath, mTargetPackageName, mDirection);
                    DeviceStatsInfoStorageManager.getInstance(DeviceLoggingService.this.getApplicationContext()).addToTPutCalculationBuffer(sDeviceStatsInfo);

                    // if the avg t-put exceeds threshold, it's time to start logging.
                    if (DeviceStatsInfoStorageManager.getInstance(DeviceLoggingService.this.getApplicationContext()).getAvgTputFromTpuCalculationBuffer(mDirection) > TPUT_THRESHOLD) {
                        Message eventMessage = this.obtainMessage(EVENT_ENTER_RECORDING_STATE);
                        eventMessage.obj = sDeviceStatsInfo;
                        sendMessage(eventMessage);
                    } else {
                        sendEmptyMessageDelayed(EVENT_READ_DEVICE_STATS_INFO, mLoggingInterval);
                    }
                    break;

                case EVENT_ENTER_RECORDING_STATE:
                    Log.d(TAG, "EVENT_START_RECORDING_DEVICE_STATS_INFO");

                    for (DeviceMonitoringStateChangedListener l : mDeviceLoggingStateListenerList) {
                        l.onRecordingStarted();
                    }

                    DeviceStatsInfoStorageManager.getInstance(DeviceLoggingService.this.getApplicationContext()).migrateFromTPutCalculationBufferToRecordBuffer();
                    DeviceStatsInfoStorageManager.getInstance(DeviceLoggingService.this.getApplicationContext()).addToStorage((DeviceStatsInfo) msg.obj);
                    sendEmptyMessageDelayed(EVENT_RECORD_CURRENT_STATS_INFO, mLoggingInterval);
                    break;

                case EVENT_RECORD_CURRENT_STATS_INFO:
                    Log.d(TAG, "EVENT_RECORD_CURRENT_STATS_INFO");
                    sDeviceStatsInfo = DeviceStatsInfoStorageManager.getInstance(DeviceLoggingService.this.getApplicationContext()).readCurrentDeviceStatsInfo(mTargetUid, mCPUTemperatureFilePath, mCPUClockFilePath, mTargetPackageName, mDirection);

                    DeviceStatsInfoStorageManager.getInstance(DeviceLoggingService.this.getApplicationContext()).addToTPutCalculationBuffer(sDeviceStatsInfo);
                    DeviceStatsInfoStorageManager.getInstance(DeviceLoggingService.this.getApplicationContext()).addToStorage(sDeviceStatsInfo);

                    if (DeviceStatsInfoStorageManager.getInstance(DeviceLoggingService.this.getApplicationContext()).getAvgTputFromTpuCalculationBuffer(mDirection) < TPUT_THRESHOLD) {
                        sendEmptyMessage(EVENT_EXIT_RECORDING_STATE);
                    } else {
                        sendEmptyMessageDelayed(EVENT_RECORD_CURRENT_STATS_INFO, mLoggingInterval);
                    }

                    break;

                case EVENT_EXIT_RECORDING_STATE:
                    Log.d(TAG, "EVENT_EXIT_RECORDING_STATE");

                    for (DeviceMonitoringStateChangedListener l : mDeviceLoggingStateListenerList) {
                        l.onRecordingStopped();
                    }

                    sendEmptyMessageDelayed(EVENT_ENTER_IDLE_MONITORING_STATE, mLoggingInterval);
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
    //@Setter private int mNetworkType;

    private ArrayList<DeviceMonitoringStateChangedListener> mDeviceLoggingStateListenerList;

    // constructor
    public DeviceLoggingService() {
        Log.d(TAG, "DeviceLoggingService()");
        this.mLoggingInterval = 1000;
    }

    public void setOnLoggingStateChangedListener(DeviceMonitoringStateChangedListener dlsc) {
        this.mDeviceLoggingStateListenerList.add(dlsc);
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences sSharedPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, MODE_PRIVATE);
        String sPackageName, sCpuFilePath, sThermalFilePath;
        int sInterval, sThresholdTime;
        DeviceStatsInfoStorageManager.TEST_TYPE sDirection;
        //test start
        int sNetworkType = 14; //hard coding, eg,.LTE is 14.
        //test end

        if (intent == null) {
            sPackageName = sSharedPreferences.getString(SHARED_PREFERENCES_KEY_PACKAGE_NAME, SHARED_PREFERENCES_DEFAULT_PACKAGE_NAME);
            sCpuFilePath = sSharedPreferences.getString(SHARED_PREFERENCES_KEY_CPU_CLOCK_FILE_PATH, SHARED_PREFERENCES_DEFAULT_CPU_CLOCK_FILE_PATH);
            sThermalFilePath = sSharedPreferences.getString(SHARED_PREFERENCES_KEY_THERMAL_FILE_PATH, SHARED_PREFERENCES_DEFAULT_THERMAL_FILE_PATH);
            sInterval = sSharedPreferences.getInt(SHARED_PREFERENCES_KEY_INTERVAL, SHARED_PREFERENCES_DEFAULT_INTERVAL);
            sThresholdTime = sSharedPreferences.getInt(SHARED_PREFERENCES_KEY_THRESHOLD_TIME, SHARED_PREFERENCES_DEFAULT_THRESHOLD_TIME);
            sDirection = sSharedPreferences.getInt(SHARED_PREFERENCES_KEY_TEST_TYPE, SHARED_PREFERENCES_DL_DIRECTION) == SHARED_PREFERENCES_DL_DIRECTION ? DeviceStatsInfoStorageManager.TEST_TYPE.DL_TEST : DeviceStatsInfoStorageManager.TEST_TYPE.UL_TEST;
        } else {
            sPackageName = intent.getStringExtra(SHARED_PREFERENCES_KEY_PACKAGE_NAME);
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

        //this.mServiceLogicHandler.sendEmptyMessage(EVENT_EXIT_IDLE_MONITORING_STATE);
        this.mServiceLogicHandler.sendEmptyMessage(EVENT_TERMINATE_MONITORING_LOOP);
        super.onDestroy();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind()");
        return super.onUnbind(intent);
    }

    // monitoring controller
    private void startMonitoringDeviceStats(String targetPackageName, int loggingInterval, String cpuClockFilePath, String thermalFilePath, int dlCompleteDecisionTimeThreshold, DeviceStatsInfoStorageManager.TEST_TYPE direction, int networkType) {
        Message sMsg = this.mServiceLogicHandler.obtainMessage();
        //sMsg.what = EVENT_ENTER_IDLE_MONITORING_STATE;
        sMsg.what = EVENT_FIRE_UP_MONITORING_LOOP;

        setTargetPackageName(targetPackageName);
        setTargetUid(DeviceLoggingService.getUidByPackageName(this, this.mTargetPackageName));
        setLoggingInterval(loggingInterval);
        setCPUClockFilePath(cpuClockFilePath);
        setCPUTemperatureFilePath(thermalFilePath);
        setDLCompleteDecisionTimeThreshold(dlCompleteDecisionTimeThreshold);
        setDirection(direction);

        Log.d(TAG, "Start Logging based on the following information :");
        Log.d(TAG, "\t\tDirection : " + this.mDirection);
        Log.d(TAG, "\t\tTargetPackageName : " + this.mTargetPackageName);
        Log.d(TAG, "\t\tTargetUid : " + this.mTargetUid);
        Log.d(TAG, "\t\tCPU Temperature file path : " + this.mCPUTemperatureFilePath);
        Log.d(TAG, "\t\tCPU clock file path : " + this.mCPUClockFilePath);
        Log.d(TAG, "\t\tDL Complete time threshold value : " + this.mDLCompleteDecisionTimeThreshold);

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
