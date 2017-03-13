package com.lge.tputtracingapp.dto;

import java.util.ArrayList;

public class DeviceStatsInfo {

    private long mTimeStamp;

    /* Network statsreader */
    private long mTxBytes;
    private long mRxBytes;
    private ArrayList<Integer> mCpuFreqList;

    /* CPU Stats */
    public ArrayList<Integer> mCpuFrequencyList;
    public int mCpuTemperature;

    public DeviceStatsInfo() {
        this.mCpuFrequencyList = new ArrayList<>();
    }

    public DeviceStatsInfo(long mTxBytes, long mRxBytes, ArrayList<Integer> mCpuFreqList, int mCpuTemperature) {
        this.mTxBytes = mTxBytes;
        this.mRxBytes = mRxBytes;
        this.mCpuFreqList = mCpuFreqList;
        this.mCpuTemperature = mCpuTemperature;
    }

    public void setTxBytes(long txBytes) {
        this.mTxBytes = txBytes;
    }

    public void setRxBytes(long rxBytes) {
        this.mRxBytes = rxBytes;
    }
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("tx : ").append(this.mTxBytes);
        sb.append(", rx : ").append(this.mRxBytes).append("\n");
        if (this.mCpuFrequencyList != null) {
            for (int i = 0; i < this.mCpuFrequencyList.size(); ++i) {
                sb.append("CPU [").append(i).append("]").append(this.mCpuFrequencyList.get(i)).append(" MHz\n");
            }
        }
        sb.append("Temperature : ").append(this.mCpuTemperature);

        return sb.toString();
    }
}
