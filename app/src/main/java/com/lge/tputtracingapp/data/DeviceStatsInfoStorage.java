package com.lge.tputtracingapp.data;

import java.util.ArrayList;

/**
 * Created by wonsik.lee on 2017-03-14.
 */

public class DeviceStatsInfoStorage {
    private static final String TAG = DeviceStatsInfoStorage.class.getSimpleName();

    private ArrayList<DeviceStatsInfo> mDeviceStatsList;

    private DeviceStatsInfoStorage mInstance;

    private DeviceStatsInfoStorage() {
        this.mDeviceStatsList = new ArrayList<DeviceStatsInfo>();
    }


}
