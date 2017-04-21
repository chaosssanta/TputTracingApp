// IDeviceMonitoringService.aidl
package com.lge.tputtracingapp;

// Declare any non-default types here with import statements

import com.lge.tputtracingapp.IDeviceMonitoringServiceCallback;

interface IDeviceMonitoringService {
    boolean registerCallback(IDeviceMonitoringServiceCallback callback);
    void unregisterCallback(IDeviceMonitoringServiceCallback callback);
    void fireupMonitoringLoop();
}
