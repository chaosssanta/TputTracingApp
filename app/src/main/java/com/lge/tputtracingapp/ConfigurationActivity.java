package com.lge.tputtracingapp;

import com.android.LGSetupWizard.R;
import com.lge.tputtracingapp.service.LoggingStateChangedListener;
import com.lge.tputtracingapp.service.MonitoringService;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class ConfigurationActivity extends Activity {

    private static String TAG = "DeviceStatsMonitor";

    private Button mBtnMonitoringController;
    private EditText mEditTextPackageName;
    private EditText mEditTextInterval;
    private EditText mEditTextCPUClockPath;
    private EditText mEditTextCPUCoreCount;
    private EditText mEditTextCPUTemperaturePath;
    private TextView mTxtViewProgressResult;


    private MonitoringService mMonitoringService;
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
            if (ConfigurationActivity.this.mMonitoringService.isMonitoringInProgress()) {
                Toast.makeText(ConfigurationActivity.this, "Already monitoring...", Toast.LENGTH_SHORT).show();
            } else {
                String packageName = mEditTextPackageName.getText().toString();
                int interval = Integer.valueOf(mEditTextInterval.getText().toString());
                String cpuClockFilePath = mEditTextCPUClockPath.getText().toString();
                String cpuThermalFilePath = mEditTextCPUTemperaturePath.getText().toString();
                int cpuCount = Integer.valueOf(mEditTextCPUCoreCount.getText().toString());

                if (packageName != null) {
                    ConfigurationActivity.this.mMonitoringService.startLogging(packageName, interval, cpuClockFilePath, cpuThermalFilePath, cpuCount);
                } else {
                    Toast.makeText(ConfigurationActivity.this, "Package Name should be specified.", Toast.LENGTH_SHORT);
                }
            }
        }
    };

    private OnClickListener mStopMonitoringOnClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            if (!ConfigurationActivity.this.mMonitoringService.isMonitoringInProgress()) {
                Toast.makeText(ConfigurationActivity.this, "No monitoring is in progress...", Toast.LENGTH_SHORT).show();
            } else {
                Log.d(TAG, "stop monitoring");
                ConfigurationActivity.this.mMonitoringService.stopLogging();
            }
        }
    };

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(TAG, "onServiceConnected() : " + className.toString());

            ConfigurationActivity.this.mMonitoringService = ((MonitoringService.ServiceBinder) service).getService();
            ConfigurationActivity.this.mIsServiceBound = true;

            ConfigurationActivity.this.refreshMonitoringBtn();

            ConfigurationActivity.this.mMonitoringService.setLoggingStateChangedListener(mLoggingStateChangedListener);

            Log.d(TAG, "Service binding completed.");
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            ConfigurationActivity.this.mIsServiceBound = false;
        }
    };

    private void refreshMonitoringBtn() {
        if (this.mMonitoringService.isMonitoringInProgress()) { // if monitoring is in running state
            // need to set the btn property to stop monitoring set.
            this.mBtnMonitoringController.setText("Stop Monitoring"); // set the text
            this.mBtnMonitoringController.setOnClickListener(this.mStopMonitoringOnClickListener);
        } else {
            // otherwise,
            this.mBtnMonitoringController.setText("Start Monitoring");
            this.mBtnMonitoringController.setOnClickListener(this.mStartLoggingOnClickListener);
        }
        this.mBtnMonitoringController.setEnabled(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // start the logging service at application start time.
        /*Intent startIntent = new Intent(this, MonitoringService.class);
        startIntent.putExtra("package_name", "com.google.android.youtube");*/
        this.bindService(new Intent(this, MonitoringService.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        this.mBtnMonitoringController = (Button) findViewById(R.id.btn_start_service);
        this.mEditTextPackageName = (EditText) findViewById(R.id.editTxt_interval);
        this.mEditTextInterval = (EditText) findViewById(R.id.editTxt_interval);
        this.mEditTextCPUCoreCount = (EditText) findViewById(R.id.editText_cpu_count);
        this.mEditTextCPUClockPath = (EditText) findViewById(R.id.editText_cpu_path);
        this.mEditTextCPUTemperaturePath = (EditText) findViewById(R.id.editText_thermal_path);
        this.mTxtViewProgressResult = (TextView) findViewById(R.id.textView_progress_result);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.unbindService(mConnection);
        Log.d(TAG, "Unbinding Service.");
    }
}
