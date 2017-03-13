package com.lge.tputtracingapp.dto;

import java.util.ArrayList;

public class DeviceStatsInfoForSegment {
    
    /* Network stats */    
    public int txBytes;
    public int rxBytes;
    
    /* CPU Stats */
    public ArrayList<Integer> cpuFrequencyList;
    public int cpuTemperature;
}
