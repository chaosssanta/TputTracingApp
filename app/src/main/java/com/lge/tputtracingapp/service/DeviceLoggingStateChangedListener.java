package com.lge.tputtracingapp.service;

/**
 * Created by wonsik.lee on 2017-03-16.
 */

public interface DeviceLoggingStateChangedListener {
    void onMonitoringLoopStarted();
    void onMonitoringLoopStopped();
    void onRecordingStarted();
    void onRecordingStopped();
}
