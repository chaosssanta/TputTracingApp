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

    /* Network Stats */
    @Setter @Getter private long mTxBytes;
    @Setter @Getter private long mRxBytes;

    /* CPU Info */
    @Setter @Getter private ArrayList<Integer> mCpuFrequencyList;
    @Setter @Getter private float mCpuTemperature;

    /* CPU Usage */
    @Setter @Getter private float mCpuUsage;

    /* Package Name */
    @Setter @Getter private String mPackageName;

    /* Network Type */
    @Setter @Getter private String mNetworkType;

    /* Call Count */
    @Setter @Getter private int mCallCnt;

    /* Direction */
    @Setter @Getter private DeviceStatsInfoStorageManager.TEST_TYPE mDirection;

    public DeviceStatsInfo() {
        this.mCpuFrequencyList = new ArrayList<>();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CallCount : ").append(this.mCallCnt).append("\n");
        sb.append("Time : ").append(this.mTimeStamp).append("\n");
        sb.append("Tx : ").append(this.mTxBytes);
        sb.append(", Rx : ").append(this.mRxBytes).append("\n");
        if (this.mCpuFrequencyList != null) {
            for (int i = 0; i < this.mCpuFrequencyList.size(); ++i) {
                sb.append("CPU [").append(i).append("] ").append(this.mCpuFrequencyList.get(i)).append(" MHz\n");
            }
        }
        sb.append("Temperature : ").append(this.mCpuTemperature).append("\n");
        sb.append("Usage : ").append(this.mCpuUsage).append(" %\n");
        sb.append("Direction : ").append(this.mDirection).append("\n");
        sb.append("NetworkType : ").append(this.mNetworkType).append("\n");
        sb.append("PackageName : ").append(this.mPackageName).append("\n");

        return sb.toString();
    }

    @Override
    public DeviceStatsInfo clone() throws CloneNotSupportedException {
        DeviceStatsInfo cloned =  new DeviceStatsInfo();
        cloned.mTimeStamp = this.mTimeStamp;
        cloned.mCallCnt = this.mCallCnt;
        cloned.mTxBytes = this.mTxBytes;
        cloned.mRxBytes = this.mRxBytes;
        cloned.mCpuFrequencyList = (ArrayList<Integer>) this.mCpuFrequencyList.clone();
        cloned.mCpuTemperature = this.mCpuTemperature;
        cloned.mCpuUsage = this.mCpuUsage;
        cloned.mDirection = this.mDirection;
        cloned.mNetworkType = this.mNetworkType;
        cloned.mPackageName = this.mPackageName;
        return cloned;
    }
}
