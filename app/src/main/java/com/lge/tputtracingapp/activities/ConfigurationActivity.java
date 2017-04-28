package com.lge.tputtracingapp.activities;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.android.LGSetupWizard.R;
import com.lge.tputtracingapp.IDeviceMonitoringService;
import com.lge.tputtracingapp.IDeviceMonitoringServiceCallback;
import com.lge.tputtracingapp.service.DeviceMonitoringService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class ConfigurationActivity extends Activity implements CompoundButton.OnCheckedChangeListener, OnClickListener {
    private static String TAG = ConfigurationActivity.class.getSimpleName();
    private static final String mDefaultCpuInfoPath = "/sys/devices/system/cpu/";

    private Button mBtnLoggingController;
    private EditText mEditTxtPackageName;
    private EditText mEditTxtInterval;

    private RadioButton mRdoBtnChipsetVendorDefault;
    private RadioButton mRdoBtnChipsetVendorManual;
    private EditText mEditTxtCPUClockPath;
    private RadioButton mRdoBtnThermalXoThermal;
    private RadioButton mRdoBtnThermalVts;
    private RadioButton mRdoBtnThermalManual;
    private EditText mEditTxtCPUTemperaturePath;

    private RadioButton mRdoBtnDL;
    private RadioButton mRdoBtnUL;
    private int mDirection;

    private ImageButton mInfoImage;
    private EditText mEditTxtThresholdTime;

    private TextView mTxtViewResult;

    private IDeviceMonitoringService mDeviceLoggingService;

    private Spinner mSpinnerCustom = null;
    ArrayList<String> mPackageNames = null;

    static private class UIValidationResult {
        enum UIException {
            NoError(0x00000000), PackageNameInvalid(0x00000001), IntervalValueInvalid(0x00000002), ThresholdTimeInvalid(0x00000004), CPUFreqPathInvalid(0x00000008), CPUThermalPathInvalid(0x00000010);

            private int value;
            UIException(int value) {
                this.value = value;
            }

            public int getValue() {
                return value;
            }
        }

        public int mExceptinoCode;

        public UIValidationResult() {
            this.mExceptinoCode = UIException.NoError.getValue();
        }

        public void addException(UIException exception) {
            this.mExceptinoCode = this.mExceptinoCode | exception.getValue();
        }

        public void removceException(UIException exception) {
            this.mExceptinoCode =this.mExceptinoCode & ~(exception.getValue());
        }

        public boolean isExceptionIncluded(UIException e) {
            return (this.mExceptinoCode & e.getValue()) != 0;
        }

        @Override
        public String toString() {
            return Integer.toBinaryString(this.mExceptinoCode);
        }
    }

    private UIValidationResult getUIValidationResult() {

        UIValidationResult e = new UIValidationResult();
        // 1. packageName check
        PackageManager sPm = getPackageManager();
        try {
            sPm.getPackageInfo(this.mEditTxtPackageName.getText().toString(), 0);
        } catch (PackageManager.NameNotFoundException e1) {
            Log.d(TAG, "adding exception invalid package name ");
            e.addException(UIValidationResult.UIException.PackageNameInvalid);
        }

        // 2. interval time check
        try {
            int sInterval = ("".equals(this.mEditTxtInterval.getText().toString())) ? Integer.valueOf(this.mEditTxtInterval.getHint().toString()) : Integer.valueOf(this.mEditTxtInterval.getText().toString());
            Log.d(TAG, "interval : " + sInterval);
            if (sInterval < 100 || sInterval > 5000) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            Log.d(TAG, "adding exception invalid interval time ");
            e.addException(UIValidationResult.UIException.IntervalValueInvalid);
        }

        // 3. threshold time check
        try {
            int sThresholdTime = ("".equals(this.mEditTxtThresholdTime.getText().toString())) ? Integer.valueOf(this.mEditTxtThresholdTime.getHint().toString()) : Integer.valueOf(this.mEditTxtThresholdTime.getText().toString());
            if (sThresholdTime > 10 || sThresholdTime < 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            Log.d(TAG, "adding exception invalid threshold time ");
            e.addException(UIValidationResult.UIException.ThresholdTimeInvalid);
        }

        // 4. cpu freq path check
//        if (!isFreqPathVaild((!"".equals(this.mEditTxtCPUClockPath.getText().toString())) ? this.mEditTxtCPUClockPath.getText().toString(): (this.mEditTxtCPUClockPath.getHint().toString()))) {
//            Log.d(TAG, "adding exception CPU freq path ");
//            e.addException(UIValidationResult.UIException.CPUFreqPathInvalid);
//        }

        // 5. cpu thermal path check
        // TODO: cpu thermal path check should be implemented later

        return e;
    }

    private OnClickListener mStopMonitoringOnClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            if (mDeviceLoggingService != null) {
                unbindService(mConnection);
            }
            refreshMonitoringBtn();
        }
    };

    private OnClickListener mStartMonitoringOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            UIValidationResult e  = getUIValidationResult();

            boolean sSum = false;
            StringBuilder sb = new StringBuilder("아래의 입력 값을 확인하세요\n");
            if (e.isExceptionIncluded(UIValidationResult.UIException.PackageNameInvalid)) {
                sb.append("Package 명이 올바르지 않습니다.\n");
                sSum = true;
            }
            if (e.isExceptionIncluded(UIValidationResult.UIException.IntervalValueInvalid)) {
                sb.append("Interval 값이 올바르지 않습니다.\n");
                sSum = true;
            }
            if (e.isExceptionIncluded(UIValidationResult.UIException.ThresholdTimeInvalid)) {
                sb.append("Threshold 값이 올바르지 않습니다.\n");
                sSum = true;
            }
            if (e.isExceptionIncluded(UIValidationResult.UIException.CPUFreqPathInvalid)) {
                sb.append("CPU Frequency path 가 올바르지 않습니다.\n");
                sSum = true;
            }
            if (e.isExceptionIncluded(UIValidationResult.UIException.CPUThermalPathInvalid)) {
                sb.append("CPU Thermal path가 올바르지 않습니다.");
                sSum = true;
            }

            if (sSum) {
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(ConfigurationActivity.this);
                alertDialogBuilder.setTitle("Invalid UI settings!!").setMessage(sb.toString()).setCancelable(false).setPositiveButton("확인",new DialogInterface.OnClickListener() {public void onClick(DialogInterface dialog,int id) {}});
                AlertDialog alertDialog = alertDialogBuilder.create();
                alertDialog.show();
                return;
            }

            String sTemp = "";
            String sPackageName = mEditTxtPackageName.getText().toString();

            sTemp = mEditTxtInterval.getText().toString();
            int sInterval = (TextUtils.isEmpty(sTemp)) ? Integer.valueOf(mEditTxtInterval.getHint().toString()) : Integer.valueOf(sTemp);

            sTemp = mEditTxtCPUClockPath.getText().toString();
            String sCpuClockFilePath = (TextUtils.isEmpty(sTemp)) ? mEditTxtCPUClockPath.getHint().toString() : sTemp;

            sTemp = mEditTxtCPUTemperaturePath.getText().toString();
            String sCpuThermalFilePath = (TextUtils.isEmpty(sTemp)) ? mEditTxtCPUTemperaturePath.getHint().toString() : sTemp;

            sTemp = mEditTxtThresholdTime.getText().toString();
            int sThresholdTime = (TextUtils.isEmpty(sTemp))? Integer.valueOf(mEditTxtThresholdTime.getHint().toString()) : Integer.valueOf(sTemp);

            mDirection = mRdoBtnDL.isChecked() ? DeviceMonitoringService.SHARED_PREFERENCES_DL_DIRECTION : DeviceMonitoringService.SHARED_PREFERENCES_UL_DIRECTION;
            try {
                ConfigurationActivity.this.mDeviceLoggingService.fireupMonitoringLoop(sPackageName, sInterval, sCpuClockFilePath, sCpuThermalFilePath, sThresholdTime, mDirection);
            } catch (RemoteException e1) {
                e1.printStackTrace();
            }

            refreshMonitoringBtn();
        }
    };

    IDeviceMonitoringServiceCallback.Stub mCallback = new IDeviceMonitoringServiceCallback.Stub() {

        @Override
        public void onMonitoringStarted() throws RemoteException {
            Log.d(TAG, "onMonitoringStarted()");
            ConfigurationActivity.this.mTxtViewResult.append("onMonitoringStarted\n" + "" );
        }

        @Override
        public void onMonitoringStopped() throws RemoteException {
            Log.d(TAG, "onMonitoringStopped()");
            ConfigurationActivity.this.mTxtViewResult.append("onMonitoringStopped\n");
        }

        @Override
        public void onRecordingStarted() throws RemoteException {
            Log.d(TAG, "onRecordingStarted()");
            ConfigurationActivity.this.mTxtViewResult.append("Test Started!!!\n");
        }

        @Override
        public void onRecordingStopped(float overallTput, long duration, long totalTxBytes, long totalRxBytes, int callCount) throws RemoteException {
            Log.d(TAG, "onRecordingStopped()");
            ConfigurationActivity.this.mTxtViewResult.append("Test Completed \n" + "CallCount : " + callCount + "     TPut : " + overallTput + " Mbps\n\n");
        }
    };

    ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.d(TAG, "onServiceConnected() : " + componentName);
            try {
                if (service != null) {
                    mDeviceLoggingService = IDeviceMonitoringService.Stub.asInterface(service);
                    mDeviceLoggingService.registerCallback(mCallback);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "onServiceDisconnected()");
        }
    };

    private void refreshMonitoringBtn() {
        if (this.isMyServiceRunning(DeviceMonitoringService.class)) {
            Log.d(TAG, "DeviceMonitoringService is running");
            // need to set the btn property to stop monitoring set.
            this.mBtnLoggingController.setText("Stop   Logging"); //set the text
            this.mBtnLoggingController.setOnClickListener(this.mStopMonitoringOnClickListener);
        } else {
            // otherwise,
            Log.d(TAG, "DeviceMonitoringService is NOT running");
            this.mBtnLoggingController.setText("Start Logging");
            this.mBtnLoggingController.setOnClickListener(this.mStartMonitoringOnClickListener);
        }
        this.mBtnLoggingController.setEnabled(true);
    }

    /*  activity method override STARTS */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreated()");
        setContentView(R.layout.activity_main);
        loadPackageNames();
        this.initUIControls();

        Intent startIntent = new Intent(ConfigurationActivity.this, DeviceMonitoringService.class);
        startIntent.setAction("com.lge.data.START_LOGGING");
        bindService(startIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume()");
        super.onResume();
    }

    @Override
    protected void onPostResume() {
        Log.d(TAG, "onPostResume()");
        super.onPostResume();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause()");

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
       /* try {
            if (mDeviceLoggingService != null && this.mIsBoundToService) {
                mDeviceLoggingService.unregisterCallback(mCallback);
                this.unbindService(this.mConnection);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }*/
        super.onDestroy();
    }
    /*  activity method override ENDS */

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        switch (compoundButton.getId()) {
            case R.id.radioButton_chipset_default:
                if (isChecked) {
                    this.mEditTxtCPUClockPath.setText("");
                    this.mEditTxtCPUClockPath.setVisibility(View.GONE);
                }
                break;
            case R.id.radioButton_chipset_manual:
                if (isChecked) {
                    this.mEditTxtCPUClockPath.setVisibility(View.VISIBLE);
                }
                break;

            case R.id.radioButton_thermal_xo_therm:
                break;
            case R.id.radioButton_thermal_vts:
                break;
            case R.id.radioButton_thermal_manual:
                if (isChecked) {
                    this.mEditTxtCPUTemperaturePath.setVisibility(View.VISIBLE);
                } else {
                    this.mEditTxtCPUTemperaturePath.setVisibility(View.GONE);
                }
                break;
            case R.id.radioButton_dl_direction:
                mDirection = DeviceMonitoringService.SHARED_PREFERENCES_DL_DIRECTION;
                break;
            case R.id.radioButton_ul_direction:
                mDirection = DeviceMonitoringService.SHARED_PREFERENCES_UL_DIRECTION;
                break;

            default:
                break;
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.infoImageView: {
                showInfoDialog();
                break;
            }
        }
    }

    private void showInfoDialog() {
        AlertDialog.Builder sAlert = new AlertDialog.Builder(this);
        sAlert.setTitle("ThresHoldTime Info.");
        sAlert.setMessage("ThresHoldTIme이란? XXXXX").setCancelable(false);
        sAlert.setNeutralButton("OK", null);
        sAlert.show();
    }

    private void initUIControls() {
        this.mBtnLoggingController = (Button) findViewById(R.id.btn_start_service);

        this.mEditTxtPackageName = (EditText) findViewById(R.id.editTxt_package_name);
        this.mEditTxtInterval = (EditText) findViewById(R.id.editTxt_interval);
//        this.mTxtViewProgressResult = (TextView) findViewById(R.id.textView_progress_result);

        this.mRdoBtnChipsetVendorDefault = (RadioButton) findViewById(R.id.radioButton_chipset_default);
        this.mRdoBtnChipsetVendorManual = (RadioButton) findViewById(R.id.radioButton_chipset_manual);
        this.mEditTxtCPUClockPath = (EditText) findViewById(R.id.editText_cpu_path);

        this.mRdoBtnThermalXoThermal = (RadioButton) findViewById(R.id.radioButton_thermal_xo_therm);
        this.mRdoBtnThermalVts = (RadioButton) findViewById(R.id.radioButton_thermal_vts);
        this.mRdoBtnThermalManual = (RadioButton) findViewById(R.id.radioButton_thermal_manual);
        this.mEditTxtCPUTemperaturePath = (EditText) findViewById(R.id.editText_thermal_path);
        this.mEditTxtThresholdTime = (EditText) findViewById(R.id.thresholdTimeEditText);

        this.mInfoImage = (ImageButton) findViewById(R.id.infoImageView);

        this.mSpinnerCustom = (Spinner) findViewById(R.id.spinner_package_name);
        this.mEditTxtPackageName.setVisibility(View.GONE);

        this.mRdoBtnDL = (RadioButton) findViewById(R.id.radioButton_dl_direction);
        this.mRdoBtnUL = (RadioButton) findViewById(R.id.radioButton_ul_direction);

        setPackageNamesToSpinner();

        // listener setup
        this.mRdoBtnChipsetVendorDefault.setOnCheckedChangeListener(this);
        this.mRdoBtnChipsetVendorManual.setOnCheckedChangeListener(this);
        this.mRdoBtnChipsetVendorDefault.setChecked(true);

        this.mRdoBtnDL.setOnCheckedChangeListener(this);
        this.mRdoBtnUL.setOnCheckedChangeListener(this);
        this.mRdoBtnDL.setChecked(true);

        this.mRdoBtnThermalManual.setOnCheckedChangeListener(this);
        this.mRdoBtnThermalXoThermal.setChecked(true);
        this.mInfoImage.setOnClickListener(this);

        this.mTxtViewResult = (TextView) findViewById(R.id.txtView_resultSummary);
        this.mTxtViewResult.setMovementMethod(ScrollingMovementMethod.getInstance());
        this.mTxtViewResult.setText("Result Summary\n");
        //DeviceStatsInfoStorageManager.getInstance(this).registerResultView(this.mTxtViewResult);

        this.refreshMonitoringBtn();
    }


    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager sManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        for (ActivityManager.RunningServiceInfo service : sManager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    //test start
    private void loadPackageNames() {
        Log.d(TAG, "loadPackageNames() Entry.");

        this.mPackageNames = new ArrayList<>();
        final List<PackageInfo> sPackageInfos = this.getPackageManager().getInstalledPackages(PackageManager.GET_PERMISSIONS);
        PriorityQueue<PackageNameInstallTime> queue = new PriorityQueue<>(sPackageInfos.size(), new Comparator<PackageNameInstallTime>() {
            @Override
            public int compare(PackageNameInstallTime packageNameInstallTime, PackageNameInstallTime t1) {
                if (packageNameInstallTime.sInstallTime > t1.sInstallTime) {
                    return -1;
                } else if (packageNameInstallTime.sInstallTime == t1.sInstallTime) {
                    return 0;
                } else {
                    return 1;
                }
            }
        });

        for (PackageInfo pi: sPackageInfos) {
            if (pi.applicationInfo.uid < 10000) {
                continue;
            }

            String sPackageName = pi.applicationInfo.packageName;
            if (sPackageName.contains("com.lg")
                    || sPackageName.contains("google.")
                    || (sPackageName.contains("kt.") && !sPackageName.contains("giga"))
                    || (sPackageName.contains("com.android") && !sPackageName.contains("chrome"))) {
                continue;
            }

            String[] sRequestedPermissions = pi.requestedPermissions;
            if (sRequestedPermissions != null) {
                for (String requestedPermission : sRequestedPermissions) {
                    if (requestedPermission.contains("android.permission.INTERNET")) {
                        queue.offer(new PackageNameInstallTime(pi.firstInstallTime, pi.packageName));
                        break;
                    }
                }
            }
        }

        while (queue.iterator().hasNext()) {
            PackageNameInstallTime sPnit = queue.poll();
           // Log.d(TAG, "adding " + sPnit.sPackageName + ", install time : " + sPnit.sInstallTime);
            mPackageNames.add(sPnit.sPackageName);
        }
        mPackageNames.add("직접입력");
    }

    private class PackageNameInstallTime {
        long sInstallTime;
        String sPackageName;

        public PackageNameInstallTime(long installTime, String packageName) {
            this.sInstallTime = installTime;
            this.sPackageName = packageName;
        }
    }

    private void setPackageNamesToSpinner() {
        Log.d(TAG, "setPackageNamesToSpinner() Entry.");
        CustomSpinnerAdapter sCustomSpinnerAdapter = new CustomSpinnerAdapter(ConfigurationActivity.this, mPackageNames);
        mSpinnerCustom.setAdapter(sCustomSpinnerAdapter);
        mSpinnerCustom.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String sItem = parent.getItemAtPosition(position).toString();
                Log.d(TAG, "Selected Item: " + sItem);

                if ("직접입력".equals(sItem)) {
                    mEditTxtPackageName.setText("");
                    mEditTxtPackageName.setVisibility(View.VISIBLE);
                    return;
                }

                mEditTxtPackageName.setText(sItem);
                mEditTxtPackageName.setVisibility(View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                for (int i=0; i<1000; i++) {
                    Toast.makeText(parent.getContext(), "Monitoring할 Package를 골라주세요.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}
