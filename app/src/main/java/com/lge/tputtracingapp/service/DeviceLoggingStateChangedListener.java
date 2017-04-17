package com.lge.tputtracingapp.service;

/**
 * Created by wonsik.lee on 2017-03-16.
 */

public interface DeviceLoggingStateChangedListener {
    void onMonitoringStarted();
    void onMonitoringStopped();
    void onRecordingStarted();
    void onRecordingStopped();
}
