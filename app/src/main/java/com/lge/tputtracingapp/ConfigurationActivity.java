package com.lge.tputtracingapp;

import com.android.LGSetupWizard.R;
import com.lge.tputtracingapp.service.LoggingStateChangedListener;
import com.lge.tputtracingapp.service.DeviceLoggingService;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

public class ConfigurationActivity extends Activity implements CompoundButton.OnCheckedChangeListener {

    private static String TAG = "DeviceStatsMonitor";

    private Button mBtnMonitoringController;
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

    private TextView mTxtViewProgressResult;

    private DeviceLoggingService mDeviceLoggingService;
    private boolean mIsServiceBound;

    private LoggingStateChangedListener mLoggingStateChangedListener = new LoggingStateChangedListener() {

        @Override
        public void onLoggingStateChanged(boolean isLogging) {
            refreshMonitoringBtn();
        }
    };

    private OnClickListener mStartLoggingOnClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            if (ConfigurationActivity.this.mDeviceLoggingService.isMonitoringInProgress()) {
                Toast.makeText(ConfigurationActivity.this, "Already monitoring...", Toast.LENGTH_SHORT).show();
            } else {
                String temp = mEditTxtPackageName.getText().toString();
                String packageName = (TextUtils.isEmpty(temp)) ? mEditTxtPackageName.getHint().toString() : temp;

                temp = mEditTxtInterval.getText().toString();
                int interval = (TextUtils.isEmpty(temp)) ? Integer.valueOf(mEditTxtInterval.getHint().toString()) : Integer.valueOf(temp);

                temp = mEditTxtCPUClockPath.getText().toString();
                String cpuClockFilePath = (TextUtils.isEmpty(temp)) ? mEditTxtCPUClockPath.getHint().toString() : temp;

                temp = mEditTxtCPUTemperaturePath.getText().toString();
                String cpuThermalFilePath = (TextUtils.isEmpty(temp)) ? mEditTxtCPUTemperaturePath.getHint().toString() : temp;

                ConfigurationActivity.this.mDeviceLoggingService.startLogging(packageName, interval, cpuClockFilePath, cpuThermalFilePath);
            }
        }
    };

    private OnClickListener mStopMonitoringOnClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            if (!ConfigurationActivity.this.mDeviceLoggingService.isMonitoringInProgress()) {
                Toast.makeText(ConfigurationActivity.this, "No monitoring is in progress...", Toast.LENGTH_SHORT).show();
            } else {
                Log.d(TAG, "stop monitoring");
                ConfigurationActivity.this.mDeviceLoggingService.stopLogging();
            }
        }
    };

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "onServiceConnected() : " + className.toString());

            ConfigurationActivity.this.mDeviceLoggingService = ((DeviceLoggingService.ServiceBinder) service).getService();
            ConfigurationActivity.this.mIsServiceBound = true;

            ConfigurationActivity.this.refreshMonitoringBtn();

            ConfigurationActivity.this.mDeviceLoggingService.setLoggingStateChangedListener(mLoggingStateChangedListener);

            Log.d(TAG, "Service binding completed.");
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            ConfigurationActivity.this.mIsServiceBound = false;
        }
    };

    private void refreshMonitoringBtn() {
        if (this.mDeviceLoggingService.isMonitoringInProgress()) { // if monitoring is in running state
            // need to set the btn property to stop monitoring set.
            this.mBtnMonitoringController.setText("Stop   Logging"); // set the text
            this.mBtnMonitoringController.setOnClickListener(this.mStopMonitoringOnClickListener);
        } else {
            // otherwise,
            this.mBtnMonitoringController.setText("Start Logging");
            this.mBtnMonitoringController.setOnClickListener(this.mStartLoggingOnClickListener);
        }
        this.mBtnMonitoringController.setEnabled(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.bindService(new Intent(this, DeviceLoggingService.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        this.initUIControls();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.unbindService(mConnection);
        Log.d(TAG, "Unbinding Service.");
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        switch (compoundButton.getId()) {
            case R.id.radioButton_chipset_manual:
                if (isChecked) {
                    this.mEditTxtCPUClockPath.setVisibility(View.VISIBLE);
                } else {
                    this.mEditTxtCPUClockPath.setVisibility(View.GONE);
                }
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

    private void initUIControls() {
        this.mBtnMonitoringController = (Button) findViewById(R.id.btn_start_service);
        this.mEditTxtPackageName = (EditText) findViewById(R.id.editTxt_package_name);
        this.mEditTxtInterval = (EditText) findViewById(R.id.editTxt_interval);
        this.mTxtViewProgressResult = (TextView) findViewById(R.id.textView_progress_result);

        this.mRdoBtnChipsetVendorQCT = (RadioButton) findViewById(R.id.radioButton_chipset_qct);
        this.mRdoBtnChipsetVendorMTK = (RadioButton) findViewById(R.id.radioButton_chipset_mtk);
        this.mRdoBtnChipsetVendorManual = (RadioButton) findViewById(R.id.radioButton_chipset_manual);
        this.mEditTxtCPUClockPath = (EditText) findViewById(R.id.editText_cpu_path);

        this.mRdoBtnThermalXoThermal = (RadioButton) findViewById(R.id.radioButton_xo_therm);
        this.mRdoBtnThermalVts = (RadioButton) findViewById(R.id.radioButton_vts);
        this.mRdoBtnThermalManual = (RadioButton) findViewById(R.id.radioButton_thermal_manual);
        this.mEditTxtCPUTemperaturePath = (EditText) findViewById(R.id.editText_thermal_path);

        // listener setup
        this.mRdoBtnChipsetVendorManual.setOnCheckedChangeListener(this);
        this.mRdoBtnChipsetVendorManual.setChecked(true);
        this.mRdoBtnChipsetVendorQCT.setChecked(true);

        this.mRdoBtnThermalManual.setOnCheckedChangeListener(this);
        this.mRdoBtnThermalManual.setChecked(true);
        this.mRdoBtnThermalXoThermal.setChecked(true);
    }
}
