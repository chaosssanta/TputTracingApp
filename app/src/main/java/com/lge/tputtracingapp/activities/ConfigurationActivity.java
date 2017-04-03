package com.lge.tputtracingapp.activities;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
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
import com.lge.tputtracingapp.service.DeviceLoggingService;

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
    private String mCpuInfoPath;

    private RadioButton mRdoBtnThermalXoThermal;
    private RadioButton mRdoBtnThermalVts;
    private RadioButton mRdoBtnThermalManual;
    private EditText mEditTxtCPUTemperaturePath;

    private ImageButton mInfoImage;
    private EditText mThresholdTimeEditText;

    private TextView mTxtViewProgressResult;

    private DeviceLoggingService mDeviceLoggingService;

    private Spinner mSpinnerCustum = null;
    ArrayList<String> mPackageNames = null;
    private String mSelectedPackageName = null;

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

            temp = mEditTxtCPUTemperaturePath.getText().toString();
            String cpuThermalFilePath = (TextUtils.isEmpty(temp)) ? mEditTxtCPUTemperaturePath.getHint().toString() : temp;

            temp = mThresholdTimeEditText.getText().toString();
            int sThresholdTime = (TextUtils.isEmpty(temp))? Integer.valueOf(mThresholdTimeEditText.getHint().toString()) : Integer.valueOf(temp);

            Intent startIntent = new Intent(ConfigurationActivity.this, DeviceLoggingService.class);
            startIntent.setAction("com.lge.data.START_LOGGING");
            startIntent.putExtra(DeviceLoggingService.SHARED_PREFERENCES_KEY_PACKAGE_NAME, packageName);
            startIntent.putExtra(DeviceLoggingService.SHARED_PREFERENCES_KEY_INTERVAL, interval);

            if (mRdoBtnChipsetVendorDefault.isChecked()) {
                mCpuInfoPath = mDefaultCpuInfoPath;
            }  else if (mRdoBtnChipsetVendorManual.isChecked()) {
                if (TextUtils.isEmpty(mEditTxtCPUClockPath.getText().toString()))
                    mCpuInfoPath = mEditTxtCPUClockPath.getHint().toString();
                else
                    mCpuInfoPath = mEditTxtCPUClockPath.getText().toString();
                Log.d("NHY", "mCpuInfoPath: " + mCpuInfoPath);
            }
            startIntent.putExtra(DeviceLoggingService.SHARED_PREFERENCES_KEY_CPU_CLOCK_FILE_PATH, mCpuInfoPath);

            startIntent.putExtra(DeviceLoggingService.SHARED_PREFERENCES_KEY_THERMAL_FILE_PATH, cpuThermalFilePath);
            startIntent.putExtra(DeviceLoggingService.SHARED_PREFERENCES_KEY_THRESHOLD_TIME, sThresholdTime);
            startIntent.putExtra(DeviceLoggingService.SHARED_PREFERENCES_KEY_SELECTED_PACKAGE_NAME, mSelectedPackageName);
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
        loadPackageNames();
        this.initUIControls();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        switch (compoundButton.getId()) {
            case R.id.radioButton_chipset_default:
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
        sAlert.setMessage("ThresHoldTIme이란? XXXXX").setCancelable(false);
        sAlert.setNeutralButton("OK", null);
        sAlert.show();
    }

    private void initUIControls() {
        this.mBtnLoggingController = (Button) findViewById(R.id.btn_start_service);

        this.mEditTxtPackageName = (EditText) findViewById(R.id.editTxt_package_name);
        this.mEditTxtInterval = (EditText) findViewById(R.id.editTxt_interval);
        this.mTxtViewProgressResult = (TextView) findViewById(R.id.textView_progress_result);

        this.mRdoBtnChipsetVendorDefault = (RadioButton) findViewById(R.id.radioButton_chipset_default);
        this.mRdoBtnChipsetVendorManual = (RadioButton) findViewById(R.id.radioButton_chipset_manual);
        this.mEditTxtCPUClockPath = (EditText) findViewById(R.id.editText_cpu_path);

        this.mRdoBtnThermalXoThermal = (RadioButton) findViewById(R.id.radioButton_thermal_xo_therm);
        this.mRdoBtnThermalVts = (RadioButton) findViewById(R.id.radioButton_thermal_vts);
        this.mRdoBtnThermalManual = (RadioButton) findViewById(R.id.radioButton_thermal_manual);
        this.mEditTxtCPUTemperaturePath = (EditText) findViewById(R.id.editText_thermal_path);
        this.mThresholdTimeEditText = (EditText) findViewById(R.id.thresholdTimeEditText);

        this.mInfoImage = (ImageButton) findViewById(R.id.infoImageView);

        this.mSpinnerCustum = (Spinner) findViewById(R.id.spinner_package_name);
        setPackageNamesToSpinner();

        // listener setup
        this.mRdoBtnChipsetVendorManual.setOnCheckedChangeListener(this);

        this.mRdoBtnChipsetVendorDefault.setChecked(true);

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

    //test start
    private void loadPackageNames() {
        Log.d(TAG, "loadPackageNames() Entry.");

        this.mPackageNames = new ArrayList<String>();
        final List<PackageInfo> packageInfos = this.getPackageManager().getInstalledPackages(PackageManager.GET_PERMISSIONS);
        PriorityQueue<PackageNameInstallTime> queue = new PriorityQueue<>(packageInfos.size(), new Comparator<PackageNameInstallTime>() {
            @Override
            public int compare(PackageNameInstallTime packageNameInstallTime, PackageNameInstallTime t1) {
                if (packageNameInstallTime.installTime > t1.installTime) {
                    return -1;
                } else if (packageNameInstallTime.installTime == t1.installTime) {
                    return 0;
                } else {
                    return 1;
                }
            }
        });

        for (PackageInfo pi: packageInfos) {
            if (pi.applicationInfo.uid < 10000) {
                continue;
            }

            String packageName = pi.applicationInfo.packageName;
            if (packageName.contains("com.lg") || packageName.contains("google.") || (packageName.contains("kt.") && !packageName.contains("giga")) || (packageName.contains("com.android") && !packageName.contains("chrome"))) {
                continue;
            }


            String[] requestedPermissions = pi.requestedPermissions;
            if (requestedPermissions != null) {
                for (String requestedPermission : requestedPermissions) {
                    if (requestedPermission.contains("android.permission.INTERNET")) {
                        queue.offer(new PackageNameInstallTime(pi.firstInstallTime, pi.packageName));
                        break;
                    }
                }
            }
        }

        while (queue.iterator().hasNext()) {
            PackageNameInstallTime pnit = queue.poll();
            Log.d(TAG, "adding " + pnit.packageName + ", install time : " + pnit.installTime);
            mPackageNames.add(pnit.packageName);
        }
    }
    private class PackageNameInstallTime {
        long installTime;
        String packageName;

        public PackageNameInstallTime(long installTime, String packageName) {
            this.installTime = installTime;
            this.packageName = packageName;
        }
    }

    private void setPackageNamesToSpinner() {
        Log.d(TAG, "setPackageNamesToSpinner() Entry.");
        CustomSpinnerAdapter customSpinnerAdapter = new CustomSpinnerAdapter(ConfigurationActivity.this, mPackageNames);
        mSpinnerCustum.setAdapter(customSpinnerAdapter);
        mSpinnerCustum.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String item = parent.getItemAtPosition(position).toString();
                Log.d(TAG, "Selected Item: " + item);
                mSelectedPackageName = item;
                mEditTxtPackageName.setText(item);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                for (int i=0; i<1000; i++) {
                    Toast.makeText(parent.getContext(), "Monitoring할 Package를 골라주세요.", Toast.LENGTH_LONG).show();
                }
            }
        });
    }
    //test end
}
