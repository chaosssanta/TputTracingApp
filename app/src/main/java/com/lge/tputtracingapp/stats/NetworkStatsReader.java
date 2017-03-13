package com.lge.tputtracingapp.stats;

import android.net.TrafficStats;

public class NetworkStatsReader {
    private static final String TAG = NetworkStatsReader.class.getSimpleName();
    
    public static long getTxBytesByUid(int targetUid) {
        return TrafficStats.getUidTxBytes(targetUid);
    }
    
    public static long getRxBytesByUid(int targetUid) {
        return TrafficStats.getUidRxBytes(targetUid);
    }
}
