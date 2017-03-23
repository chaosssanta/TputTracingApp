package com.lge.tputtracingapp.data;

import java.util.ArrayList;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Accessors(prefix = "m")
@AllArgsConstructor
public class DeviceStatsInfo {

    @Setter @Getter private long mTimeStamp;

    /* Network statsreader */
    @Setter @Getter private long mTxBytes;
    @Setter @Getter private long mRxBytes;

    /* CPU Stats */
    @Setter @Getter private ArrayList<Integer> mCpuFrequencyList;
    @Setter @Getter private int mCpuTemperature;
    @Setter @Getter private int mCpuUsage;

    public DeviceStatsInfo() {
        this.mCpuFrequencyList = new ArrayList<>();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Time : ").append(this.mTimeStamp).append("\n");
        sb.append("tx : ").append(this.mTxBytes);
        sb.append(", rx : ").append(this.mRxBytes).append("\n");
        if (this.mCpuFrequencyList != null) {
            for (int i = 0; i < this.mCpuFrequencyList.size(); ++i) {
                sb.append("CPU [").append(i).append("] ").append(this.mCpuFrequencyList.get(i)).append(" MHz\n");
            }
        }
        sb.append("Temperature : ").append(this.mCpuTemperature).append("\n");
        sb.append("Usage : ").append(this.mCpuUsage).append(" %\n");

        return sb.toString();
    }

    @Override
    public DeviceStatsInfo clone() throws CloneNotSupportedException {
        DeviceStatsInfo cloned =  new DeviceStatsInfo();
        cloned.mTimeStamp = this.mTimeStamp;
        cloned.mTxBytes = this.mTxBytes;
        cloned.mRxBytes = this.mRxBytes;
        cloned.mCpuFrequencyList = (ArrayList<Integer>) this.mCpuFrequencyList.clone();
        cloned.mCpuTemperature = this.mCpuTemperature;
        cloned.mCpuUsage = this.mCpuUsage;
        return cloned;
    }
}
