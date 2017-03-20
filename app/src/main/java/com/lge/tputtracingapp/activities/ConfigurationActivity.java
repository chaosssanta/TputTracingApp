package com.lge.tputtracingapp.activities;

import com.android.LGSetupWizard.R;
import com.lge.tputtracingapp.service.DeviceLoggingService;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.TextView;

public class ConfigurationActivity extends Activity implements CompoundButton.OnCheckedChangeListener, OnClickListener {

    private static String TAG = "DeviceStatsMonitor";

    private Button mBtnLoggingController;
    private EditText mEditTxtPackageName;
    private EditText mEditTxtInterval;

    private RadioButton mRdoBtnChipsetVendorQCT;
    private RadioButton mRdoBtnChipsetVendorMTK;
    private RadioButton mRdoBtnChipsetVendorManual;
    private EditText mEditTxtCPUClockPath;

    private RadioButton mRdoBtnThermalXoThermal;
    private RadioButton mRdoBtnThermalVts;
    private RadioButton mRdoBtnThermalManual;
    private EditText mEditTxtCPUTemperaturePath;

    private ImageButton mInfoImage;
    private EditText mThresholdTimeEditText;

    private TextView mTxtViewProgressResult;

    private DeviceLoggingService mDeviceLoggingService;

    private OnClickListener mStopMonitoringOnClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            stopService(new Intent(ConfigurationActivity.this, DeviceLoggingService.class));
            refreshMonitoringBtn();
        }
    };

    private OnClickListener mStartMonitoringOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            String temp = mEditTxtPackageName.getText().toString();
            String packageName = (TextUtils.isEmpty(temp)) ? mEditTxtPackageName.getHint().toString() : temp;

            temp = mEditTxtInterval.getText().toString();
            int interval = (TextUtils.isEmpty(temp)) ? Integer.valueOf(mEditTxtInterval.getHint().toString()) : Integer.valueOf(temp);

            temp = mEditTxtCPUClockPath.getText().toString();
            String cpuClockFilePath = (TextUtils.isEmpty(temp)) ? mEditTxtCPUClockPath.getHint().toString() : temp;

            temp = mEditTxtCPUTemperaturePath.getText().toString();
            String cpuThermalFilePath = (TextUtils.isEmpty(temp)) ? mEditTxtCPUTemperaturePath.getHint().toString() : temp;

            temp = mThresholdTimeEditText.getText().toString();
            int sThresholdTime = (TextUtils.isEmpty(temp))? Integer.valueOf(mThresholdTimeEditText.getHint().toString()) : Integer.valueOf(temp);

            Intent startIntent = new Intent(ConfigurationActivity.this, DeviceLoggingService.class);
            startIntent.setAction("com.lge.data.START_LOGGING");
            startIntent.putExtra(DeviceLoggingService.SHARED_PREFERENCES_KEY_PACKAGE_NAME, packageName);
            startIntent.putExtra(DeviceLoggingService.SHARED_PREFERENCES_KEY_INTERVAL, interval);
            startIntent.putExtra(DeviceLoggingService.SHARED_PREFERENCES_KEY_CPU_CLOCK_FILE_PATH, cpuClockFilePath);
            startIntent.putExtra(DeviceLoggingService.SHARED_PREFERENCES_KEY_THERMAL_FILE_PATH, cpuThermalFilePath);
            startIntent.putExtra(DeviceLoggingService.SHARED_PREFERENCES_KEY_THRESHOLD_TIME, sThresholdTime);
            startService(startIntent);

            refreshMonitoringBtn();
        }
    };

    private void refreshMonitoringBtn() {
        if (this.isMyServiceRunning(DeviceLoggingService.class)) {
            // need to set the btn property to stop monitoring set.
            this.mBtnLoggingController.setText("Stop   Logging"); // set the text
            this.mBtnLoggingController.setOnClickListener(this.mStopMonitoringOnClickListener);
        } else {
            // otherwise,
            this.mBtnLoggingController.setText("Start Logging");
            this.mBtnLoggingController.setOnClickListener(this.mStartMonitoringOnClickListener);
        }
        this.mBtnLoggingController.setEnabled(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreated()");
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
        this.initUIControls();
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        switch (compoundButton.getId()) {
            case R.id.radioButton_chipset_qct:
                break;
            case R.id.radioButton_chipset_mtk:
                break;
            case R.id.radioButton_chipset_manual:
                if (isChecked) {
                    this.mEditTxtCPUClockPath.setVisibility(View.VISIBLE);
                } else {
                    this.mEditTxtCPUClockPath.setVisibility(View.GONE);
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
        sAlert.setMessage("mDLCompleteDecisionTimeThreshold, 이름 이상해").setCancelable(false);
        sAlert.setNeutralButton("OK", null);
        sAlert.show();
    }

    private void initUIControls() {
        this.mBtnLoggingController = (Button) findViewById(R.id.btn_start_service);

        this.mEditTxtPackageName = (EditText) findViewById(R.id.editTxt_package_name);
        this.mEditTxtInterval = (EditText) findViewById(R.id.editTxt_interval);
        this.mTxtViewProgressResult = (TextView) findViewById(R.id.textView_progress_result);

        this.mRdoBtnChipsetVendorQCT = (RadioButton) findViewById(R.id.radioButton_chipset_qct);
        this.mRdoBtnChipsetVendorMTK = (RadioButton) findViewById(R.id.radioButton_chipset_mtk);
        this.mRdoBtnChipsetVendorManual = (RadioButton) findViewById(R.id.radioButton_chipset_manual);
        this.mEditTxtCPUClockPath = (EditText) findViewById(R.id.editText_cpu_path);

        this.mRdoBtnThermalXoThermal = (RadioButton) findViewById(R.id.radioButton_thermal_xo_therm);
        this.mRdoBtnThermalVts = (RadioButton) findViewById(R.id.radioButton_thermal_vts);
        this.mRdoBtnThermalManual = (RadioButton) findViewById(R.id.radioButton_thermal_manual);
        this.mEditTxtCPUTemperaturePath = (EditText) findViewById(R.id.editText_thermal_path);
        this.mThresholdTimeEditText = (EditText) findViewById(R.id.thresholdTimeEditText);

        this.mInfoImage = (ImageButton) findViewById(R.id.infoImageView);

        // listener setup
        this.mRdoBtnChipsetVendorManual.setOnCheckedChangeListener(this);
        this.mRdoBtnChipsetVendorQCT.setChecked(true);
        this.mRdoBtnThermalManual.setOnCheckedChangeListener(this);
        this.mRdoBtnThermalXoThermal.setChecked(true);
        this.mInfoImage.setOnClickListener(this);

        this.refreshMonitoringBtn();
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
