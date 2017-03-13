package com.lge.tputtracingapp.dto;

import android.bluetooth.BluetoothClass;

import java.util.ArrayList;

public class DeviceStatsInfoForSegment {
    
    /* Network statsreader */
    public long txBytes;
    public long rxBytes;
    private ArrayList<Integer> mCpuFreqList;

    /* CPU Stats */
    public ArrayList<Integer> cpuFrequencyList;
    public int cpuTemperature;

    public DeviceStatsInfoForSegment() {
        this.cpuFrequencyList = new ArrayList<>();
    }

    public DeviceStatsInfoForSegment(long txBytes, long rxBytes, ArrayList<Integer> mCpuFreqList, int cpuTemperature) {
        this.txBytes = txBytes;
        this.rxBytes = rxBytes;
        this.mCpuFreqList = mCpuFreqList;
        this.cpuTemperature = cpuTemperature;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("tx : ").append(this.txBytes);
        sb.append(", rx : ").append(this.rxBytes).append("\n");
        if (this.cpuFrequencyList != null) {
            for (int i = 0; i < this.cpuFrequencyList.size(); ++i) {
                sb.append("CPU [").append(i).append("]").append(this.cpuFrequencyList.get(i)).append(" MHz\n");
            }
        }
        sb.append("Temperature : ").append(this.cpuTemperature);

        return sb.toString();
    }
}
