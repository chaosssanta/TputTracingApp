package com.lge.tputtracingapp.service;

/**
 * Created by wonsik.lee on 2017-03-16.
 */

public interface DeviceLoggingStateChangedListener {
    public void onMonitoringStarted();
    public void onMonitoringStopped();
    public void onLoggingStarted();
    public void onLoggingStopped();
}
