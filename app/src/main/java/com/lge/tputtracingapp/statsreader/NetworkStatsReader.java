package com.lge.tputtracingapp.statsreader;

import android.content.Context;
import android.net.TrafficStats;
import android.telephony.TelephonyManager;
import android.util.Log;

public class NetworkStatsReader {

    private static final String TAG = NetworkStatsReader.class.getSimpleName();
    private static boolean mDebug = true;
    
    public static long getTxBytesByUid(int targetUid) {
        return TrafficStats.getUidTxBytes(targetUid);
    }
    
    public static long getRxBytesByUid(int targetUid) {
        return TrafficStats.getUidRxBytes(targetUid);
    }

    public static String getNetworkType(Context context) {
        int networkType = ((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE)).getNetworkType();
        String sNetworkType = null;
        switch (networkType) {
            case 7:
                sNetworkType = "1xRTT";
                break;
            case 4:
                sNetworkType = "CDMA";
                break;
            case 2:
                sNetworkType = "EDGE";
                break;
            case 14:
                sNetworkType = "eHRPD";
                break;
            case 5:
                sNetworkType = "EVDO rev. 0";
                break;
            case 6:
                sNetworkType = "EVDO rev. A";
                break;
            case 12:
                sNetworkType = "EVDO rev. B";
                break;
            case 1:
                sNetworkType = "GPRS";
                break;
            case 8:
                sNetworkType = "HSDPA";
                break;
            case 10:
                sNetworkType = "HSPA";
                break;
            case 15:
                sNetworkType = "HSPA+";
                break;
            case 9:
                sNetworkType = "HSUPA";
                break;
            case 11:
                sNetworkType = "iDen";
                break;
            case 13:
                sNetworkType = "LTE";
                break;
            case 3:
                sNetworkType = "UMTS";
                break;
            case 0:
                sNetworkType = "Unknown";
                break;
        }

        if (mDebug) {
            Log.d(TAG, "getNetworkType() returns " + sNetworkType);
        }
        return sNetworkType;
    }
}
