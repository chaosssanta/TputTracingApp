package com.lge.tputtracingapp.service;

/**
 * Created by wonsik.lee on 2017-03-16.
 */

public interface DeviceMonitoringStateChangedListener {
    void onDeviceMonitoringLoopStarted();
    void onDeviceMonitoringLoopStopped();
    void onDeviceRecordingStarted();
    void onDeviceRecordingStopped();
}
