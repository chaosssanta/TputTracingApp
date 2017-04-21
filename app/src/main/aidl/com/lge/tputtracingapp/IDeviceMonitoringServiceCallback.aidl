// IDeviceMonitoringServiceCallback.aidl
package com.lge.tputtracingapp;

// Declare any non-default types here with import statements

interface IDeviceMonitoringServiceCallback {
        void onMonitoringStarted();
        void onMonitoringStopped();
        void onRecordingStarted();
        void onRecordingStopped();
}
