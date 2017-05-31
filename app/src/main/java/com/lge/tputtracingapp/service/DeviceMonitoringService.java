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
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;

import com.lge.tputtracingapp.IDeviceMonitoringService;
import com.lge.tputtracingapp.IDeviceMonitoringServiceCallback;
import com.lge.tputtracingapp.data.DeviceStatsInfo;
import com.lge.tputtracingapp.data.DeviceStatsInfoStorageManager;

import java.util.ArrayList;
import java.util.LinkedList;

import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(prefix = "m")
public class DeviceMonitoringService extends Service {

    private static String TAG = DeviceMonitoringService.class.getSimpleName();

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

    public static final int THEMRMAL_XO = 0;
    public static final int THERMAL_VTS = 1;

    private static final int EVENT_FIRE_UP_MONITORING_LOOP = 0x10;
    private static final int EVENT_TERMINATE_MONITORING_LOOP = 0x11;

    private static final int EVENT_ENTER_IDLE_MONITORING_STATE = 0x12;
    private static final int EVENT_EXIT_IDLE_MONITORING_STATE = 0x13;

    private static final int EVENT_ENTER_RECORDING_STATE = 0x14;
    private static final int EVENT_EXIT_RECORDING_STATE = 0x15;

    private static final int EVENT_READ_DEVICE_STATS_INFO = 0x18;
    private static final int EVENT_RECORD_CURRENT_STATS_INFO = 0x19;

    private static final float TPUT_THRESHOLD = 1.0f;

    private ArrayList<DeviceMonitoringStateChangedListener> mDeviceLoggingStateListenerList;

    private Handler mServiceLogicHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case EVENT_FIRE_UP_MONITORING_LOOP:
                    Log.d(TAG, "EVENT_FIRE_UP_MONITORING_LOOP");
                    for (DeviceMonitoringStateChangedListener l : mDeviceLoggingStateListenerList) {
                        l.onDeviceMonitoringLoopStarted();
                    }

                    int N = mCallbacks.beginBroadcast();

                    for (int i = 0; i < N; ++i) {
                        try {
                            mCallbacks.getBroadcastItem(i).onMonitoringStarted();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    mCallbacks.finishBroadcast();

                    sendEmptyMessage(EVENT_ENTER_IDLE_MONITORING_STATE);
                break;

                case EVENT_TERMINATE_MONITORING_LOOP:
                    Log.d(TAG, "EVENT_TERMINATE_MONITORING_LOOP");
                    if (this.hasMessages(EVENT_RECORD_CURRENT_STATS_INFO)) {
                        Log.d(TAG, "it was in logging state, hence calling onDeviceRecordingStopped() mCallbacks");
                        removeMessages(EVENT_RECORD_CURRENT_STATS_INFO);
                        sendEmptyMessage(EVENT_EXIT_RECORDING_STATE);
                        sendEmptyMessage(EVENT_EXIT_IDLE_MONITORING_STATE);
                        break;
                    } else {
                        for (DeviceMonitoringStateChangedListener l : mDeviceLoggingStateListenerList) {
                            l.onDeviceMonitoringLoopStopped();
                        }

                        N = mCallbacks.beginBroadcast();

                        for (int i = 0; i < N; ++i) {
                            try {
                                mCallbacks.getBroadcastItem(i).onMonitoringStopped();
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                        mCallbacks.finishBroadcast();

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
                    DeviceStatsInfoStorageManager dsis = DeviceStatsInfoStorageManager.getInstance(DeviceMonitoringService.this.getApplicationContext());

                    DeviceStatsInfo sDeviceStatsInfo = dsis.readCurrentDeviceStatsInfo(mTargetUid, mCPUTemperatureFilePath, mCPUClockFilePath, mTargetPackageName, mDirection, mThermalType);
                    DeviceStatsInfoStorageManager.getInstance(DeviceMonitoringService.this.getApplicationContext()).addToTPutCalculationBuffer(sDeviceStatsInfo);

                    // if the avg t-put exceeds threshold, it's time to start logging.
                    Log.d(TAG, "t-put : " + DeviceStatsInfoStorageManager.getInstance(DeviceMonitoringService.this.getApplicationContext()).getAvgTputFromTpuCalculationBuffer(mDirection) + " Mbps");

                    if (DeviceStatsInfoStorageManager.getInstance(DeviceMonitoringService.this.getApplicationContext()).getAvgTputFromTpuCalculationBuffer(mDirection) > TPUT_THRESHOLD) {
                        Message eventMessage = this.obtainMessage(EVENT_ENTER_RECORDING_STATE);
                        eventMessage.obj = sDeviceStatsInfo;
                        sendMessage(eventMessage);
                    } else {
                        sendEmptyMessageDelayed(EVENT_READ_DEVICE_STATS_INFO, mLoggingInterval);
                    }
                    break;

                case EVENT_ENTER_RECORDING_STATE:
                    Log.d(TAG, "EVENT_ENTER_RECORDING_STATE");

                    for (DeviceMonitoringStateChangedListener l : mDeviceLoggingStateListenerList) {
                        l.onDeviceRecordingStarted();
                    }

                    N = mCallbacks.beginBroadcast();

                    for (int i = 0; i < N; ++i) {
                        try {
                            mCallbacks.getBroadcastItem(i).onRecordingStarted();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    mCallbacks.finishBroadcast();

                    DeviceStatsInfoStorageManager.getInstance(DeviceMonitoringService.this.getApplicationContext()).migrateFromTPutCalculationBufferToRecordBuffer();
                    DeviceStatsInfoStorageManager.getInstance(DeviceMonitoringService.this.getApplicationContext()).addToStorage((DeviceStatsInfo) msg.obj);
                    sendEmptyMessageDelayed(EVENT_RECORD_CURRENT_STATS_INFO, mLoggingInterval);
                    break;

                case EVENT_RECORD_CURRENT_STATS_INFO:
                    sDeviceStatsInfo = DeviceStatsInfoStorageManager.getInstance(DeviceMonitoringService.this.getApplicationContext()).readCurrentDeviceStatsInfo(mTargetUid, mCPUTemperatureFilePath, mCPUClockFilePath, mTargetPackageName, mDirection, mThermalType) ;

                    Log.d(TAG, "EVENT_RECORD_CURRENT_STATS_INFO : " + DeviceStatsInfoStorageManager.getInstance(DeviceMonitoringService.this.getApplicationContext()).getAvgTputFromTpuCalculationBuffer(mDirection) + " Mbps");

                    DeviceStatsInfoStorageManager.getInstance(DeviceMonitoringService.this.getApplicationContext()).addToTPutCalculationBuffer(sDeviceStatsInfo);
                    DeviceStatsInfoStorageManager.getInstance(DeviceMonitoringService.this.getApplicationContext()).addToStorage(sDeviceStatsInfo);

                    if (DeviceStatsInfoStorageManager.getInstance(DeviceMonitoringService.this.getApplicationContext()).getAvgTputFromTpuCalculationBuffer(mDirection) < TPUT_THRESHOLD) {
                        sendEmptyMessage(EVENT_EXIT_RECORDING_STATE);
                    } else {
                        sendEmptyMessageDelayed(EVENT_RECORD_CURRENT_STATS_INFO, mLoggingInterval);
                    }

                    break;

                case EVENT_EXIT_RECORDING_STATE:
                    Log.d(TAG, "EVENT_EXIT_RECORDING_STATE");

                    N = mCallbacks.beginBroadcast();

                    for (int i = 0; i < N; ++i) {
                        try {
                            DeviceStatsInfoStorageManager manager = DeviceStatsInfoStorageManager.getInstance(DeviceMonitoringService.this.getApplicationContext());
                            // onRecordingStopped(float overallTput, long duration, long totalTxBytes, long totalRxBytes, int callCount)

                            Log.d(TAG, "*********************** Test Result Start ******************************");
                            LinkedList<DeviceStatsInfo> list = DeviceStatsInfoStorageManager.getInstance(DeviceMonitoringService.this.getApplicationContext()).getDeviceStatsRecordList();
                            int index = 0;
                            for (DeviceStatsInfo info : list) {
                                Log.d(TAG, index++ + "i : " + info.getTimeStamp() + ", " + info.getRxBytes());
                            }
                            Log.d(TAG, "");
                            float tput = manager.getCurrentTestAvgTput(mDirection);
                            long duration = manager.getCurrentTestDurationTime(mDirection);
                            long tx = manager.getCurrentTestTotalTxBytes();
                            long rx = manager.getCurrentTestTotalRxBytes();
                            Log.d(TAG, "");
                            Log.d(TAG, "");
                            Log.d(TAG, "rx : " + rx + ", duration : " + duration + "");
                            Log.d(TAG, "tput : " + tput + " Mbps");
                            Log.d(TAG, "*********************** Test Result End *************************************");

                            mCallbacks.getBroadcastItem(i).onRecordingStopped(tput, duration, tx, rx, manager.getCurrentTestCallCount());
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                    mCallbacks.finishBroadcast();

                    for (DeviceMonitoringStateChangedListener l : mDeviceLoggingStateListenerList) {
                        l.onDeviceRecordingStopped();
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
    @Setter private int mDLCompleteDecisionTimeThreshold;
    @Setter private DeviceStatsInfoStorageManager.TEST_TYPE mDirection;
    @Setter private DeviceStatsInfoStorageManager.THERMAL_TYPE mThermalType;

    private boolean mIsInMonitoring = false;

    // constructor
    public DeviceMonitoringService() {
        Log.d(TAG, "DeviceMonitoringService()");
        this.mLoggingInterval = 1000;
    }

    public void setOnLoggingStateChangedListener(DeviceMonitoringStateChangedListener dlsc) {
        if (this.mDeviceLoggingStateListenerList == null) {
            this.mDeviceLoggingStateListenerList = new ArrayList<>();
        }
        this.mDeviceLoggingStateListenerList.add(dlsc);
        Log.d(TAG, "setOnLoggingStateChangedListener()");
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
        this.setOnLoggingStateChangedListener(DeviceStatsInfoStorageManager.getInstance(this.getApplicationContext()));
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();
    }



    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind()");
        SharedPreferences sSharedPreferences = getSharedPreferences(SHARED_PREFERENCES_NAME, MODE_PRIVATE);

        /*setTargetPackageName(intent.getStringExtra(SHARED_PREFERENCES_KEY_PACKAGE_NAME));
        setTargetUid(DeviceMonitoringService.getUidByPackageName(this.getApplicationContext(), this.mTargetPackageName));
        setCPUClockFilePath(intent.getStringExtra(SHARED_PREFERENCES_KEY_CPU_CLOCK_FILE_PATH));
        setCPUTemperatureFilePath(intent.getStringExtra(SHARED_PREFERENCES_KEY_THERMAL_FILE_PATH));
        setLoggingInterval(intent.getIntExtra(SHARED_PREFERENCES_KEY_INTERVAL, SHARED_PREFERENCES_DEFAULT_INTERVAL));
        setDLCompleteDecisionTimeThreshold(intent.getIntExtra(SHARED_PREFERENCES_KEY_THRESHOLD_TIME, SHARED_PREFERENCES_DEFAULT_THRESHOLD_TIME));
        setDirection(intent.getIntExtra(SHARED_PREFERENCES_KEY_TEST_TYPE, SHARED_PREFERENCES_DL_DIRECTION) == SHARED_PREFERENCES_DL_DIRECTION ? DeviceStatsInfoStorageManager.TEST_TYPE.DL_TEST: DeviceStatsInfoStorageManager.TEST_TYPE.UL_TEST);

        SharedPreferences.Editor sEditor = sSharedPreferences.edit();
        sEditor.putString(SHARED_PREFERENCES_KEY_PACKAGE_NAME, mTargetPackageName);
        sEditor.putString(SHARED_PREFERENCES_KEY_CPU_CLOCK_FILE_PATH, mCPUClockFilePath);
        sEditor.putString(SHARED_PREFERENCES_KEY_THERMAL_FILE_PATH, mCPUTemperatureFilePath);
        sEditor.putInt(SHARED_PREFERENCES_KEY_INTERVAL, mLoggingInterval);
        sEditor.putInt(SHARED_PREFERENCES_KEY_THRESHOLD_TIME, mDLCompleteDecisionTimeThreshold);
        sEditor.putInt(SHARED_PREFERENCES_KEY_TEST_TYPE, (mDirection == DeviceStatsInfoStorageManager.TEST_TYPE.DL_TEST) ? 0 : 1);
        sEditor.commit();*/

        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d(TAG, "onRebind()");
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind()");
        this.mServiceLogicHandler.sendEmptyMessage(EVENT_TERMINATE_MONITORING_LOOP);
        return super.onUnbind(intent);
    }

    // monitoring controller
    private void startMonitoringDeviceStats() {
        Message sMsg = this.mServiceLogicHandler.obtainMessage();
        sMsg.what = EVENT_FIRE_UP_MONITORING_LOOP;
        this.mServiceLogicHandler.sendMessage(sMsg);
    }

    RemoteCallbackList<IDeviceMonitoringServiceCallback> mCallbacks = new RemoteCallbackList<>();

    private IDeviceMonitoringService.Stub mBinder = new IDeviceMonitoringService.Stub() {

        @Override
        public boolean registerCallback(IDeviceMonitoringServiceCallback callback) throws RemoteException {
            Log.d(TAG, "registering callback " + callback);
            boolean flag = false;
            Log.d(TAG, callback.toString());
            if(callback != null) {
                flag = mCallbacks.register(callback);
                Log.d(TAG, "registered callback count : " + mCallbacks.getRegisteredCallbackCount() + "");
            }
            return flag;
        }

        @Override
        public void unregisterCallback(IDeviceMonitoringServiceCallback callback) throws RemoteException {
            if (callback != null) mCallbacks.unregister(callback);
        }

        @Override
        public void fireUpMonitoringLoop(String packageName, int interval, String cpuFreqPath, String cpuThermalPath, int dlCompleteThresholdTime, int direction, int thermalType) {
            Log.d(TAG, "PackageName : " + packageName);
            Log.d(TAG, "SamplingInterval : " + interval);
            Log.d(TAG, "CPU Freq path : " + cpuFreqPath);
            Log.d(TAG, "CPU Thermal path : " + cpuThermalPath);
            Log.d(TAG, "dl complete threshold : " + dlCompleteThresholdTime);
            Log.d(TAG, "Direction : " + direction);
            Log.d(TAG, "thermalType : " + thermalType);

            setTargetPackageName(packageName);
            setTargetUid(DeviceMonitoringService.getUidByPackageName(getApplicationContext(), DeviceMonitoringService.this.mTargetPackageName));
            setCPUClockFilePath(cpuFreqPath);
            setCPUTemperatureFilePath(cpuThermalPath);
            setLoggingInterval(interval);
            setDLCompleteDecisionTimeThreshold(dlCompleteThresholdTime);
            DeviceStatsInfoStorageManager.getInstance(DeviceMonitoringService.this.getApplicationContext()).setDLCompleteDecisionTimeThreshold(mDLCompleteDecisionTimeThreshold);
            setDirection((direction == SHARED_PREFERENCES_DL_DIRECTION) ? DeviceStatsInfoStorageManager.TEST_TYPE.DL_TEST: DeviceStatsInfoStorageManager.TEST_TYPE.UL_TEST);
            setThermalType((thermalType == THERMAL_VTS) ? DeviceStatsInfoStorageManager.THERMAL_TYPE.THERMAL_VTS : DeviceStatsInfoStorageManager.THERMAL_TYPE.THERMAL_XO);

            mIsInMonitoring = true;
            startMonitoringDeviceStats();
        }

        @Override
        public void finishMonitoringLoop() {
            mIsInMonitoring = false;
            mServiceLogicHandler.sendEmptyMessage(EVENT_TERMINATE_MONITORING_LOOP);
        }

        @Override
        public boolean isInDeviceMonitoringState() {
            if (mIsInMonitoring) {
                return true;
            } else {
                return false;
            }
            /*if (mServiceLogicHandler.hasMessages(EVENT_ENTER_IDLE_MONITORING_STATE)) {
                Log.d(TAG, "EVENT_ENTER_IDLE_MONITORING_STATE");
                return true;
            }
            if (mServiceLogicHandler.hasMessages(EVENT_EXIT_IDLE_MONITORING_STATE)) {
                Log.d(TAG, "EVENT_EXIT_IDLE_MONITORING_STATE");
                return true;
            }
            if (mServiceLogicHandler.hasMessages(EVENT_EXIT_RECORDING_STATE)) {
                Log.d(TAG, "EVENT_EXIT_RECORDING_STATE");
                return true;
            }
            if (mServiceLogicHandler.hasMessages(EVENT_FIRE_UP_MONITORING_LOOP)) {
                Log.d(TAG, "EVENT_FIRE_UP_MONITORING_LOOP");
                return true;
            }
            if (mServiceLogicHandler.hasMessages(EVENT_READ_DEVICE_STATS_INFO)) {
                Log.d(TAG, "EVENT_READ_DEVICE_STATS_INFO");
                return true;
            }
            if (mServiceLogicHandler.hasMessages(EVENT_RECORD_CURRENT_STATS_INFO)) {
                Log.d(TAG, "EVENT_RECORD_CURRENT_STATS_INFO");
                return true;
            }

            Log.d(TAG, "not monitoring !!!!");
            return false;*/
        }
    };

    // static method
    private static int getUidByPackageName(Context context, String packageName) {
        try {
            return context.getPackageManager().getApplicationInfo(packageName, PackageManager.GET_META_DATA).uid;
        } catch (NameNotFoundException e) {
            return -1;
        }
    }
}
