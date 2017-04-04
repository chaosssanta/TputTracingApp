package com.lge.tputtracingapp.statsreader;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Created by wonsik.lee on 2017-03-13.
 */

public class CPUStatsReader {
    private static final String TAG = CPUStatsReader.class.getSimpleName();

    private static final String mCpuUsageFilePath = "/proc/stat";

    private static int CPU_COUNT = -1;
    private static CpuFilter sCPU_FILE_Filter = new CpuFilter();
    private static File[] ff = null;

    //test start
    private long mWork, mWorkT, mWorkBefore;
    private long mTotal, mTotalT, mTotalBefore;
    private float mCpuTotal;

    private static CPUStatsReader mCPUStatsReader = null;
    private BufferedReader mReader;
    private String[] mSa;

    public static CPUStatsReader getInstance() {
        if (mCPUStatsReader == null)
            mCPUStatsReader = new CPUStatsReader();
        return mCPUStatsReader;
    }

    private CPUStatsReader() {

    }
    //test end

    private static class CpuFilter implements FileFilter {
        @Override
        public boolean accept(File path) {
            if (Pattern.matches("cpu[0-9]+", path.getName())) {
                return true;
            }
            return false;
        }
    }

    public static int getThermalInfo(String filePath) {
        try {
            return Integer.valueOf(cmdCat(filePath).split(":")[1].split(" ")[0]);
        } catch (IndexOutOfBoundsException e) {
            return -1;
        }
    }

    public static ArrayList<Integer> getCpuFreq(String filePath) throws NumberFormatException {
        if (CPU_COUNT == -1) {
            Log.d(TAG, "CPU_COUNT init : " + filePath);
            CPU_COUNT = getNumOfCPUs(filePath);
            Log.d(TAG, "CPU_COUNT : " + CPU_COUNT);
        }
        ArrayList<Integer> ret = new ArrayList<>();
        for (int i = 0; i < CPU_COUNT; i++) {
            ret.add(Integer.valueOf(cmdCat(filePath + "cpu" + i + "/cpufreq/scaling_cur_freq").replace("\n", "")));
        }
        return ret;
    }

    private float restrictPercentage(float percentage) {
        if (percentage > 100)
            return 100;
        else if (percentage < 0)
            return 0;
        else return percentage;
    }

    private static int getNumOfCPUs(String filePath) {
        if (ff == null) {
            ff = new File(filePath).listFiles(sCPU_FILE_Filter);
        }

        try {
            return ff.length;
        } catch (NullPointerException e) {
            return -1;
        }
    }
    private static final int INVALID_CPU_PATH = -1;

    public static boolean isFreqPathVaild(String filePath) {
        if (getNumOfCPUs(filePath) == INVALID_CPU_PATH) {
            return false;
        } else {
            return true;
        }
    }

    //    CPU usage percents calculation. It is possible negative values or values higher than 100% may appear.
    //    http://stackoverflow.com/questions/1420426
    //    http://kernel.org/doc/Documentation/filesystems/proc.txt
    public float getCpuUsage() {
        try {
            mReader = new BufferedReader(new FileReader(mCpuUsageFilePath));
            mSa = mReader.readLine().split("[ ]+", 9);

            mWork = Long.parseLong(mSa[1]) + Long.parseLong(mSa[2]) + Long.parseLong(mSa[3]);
            mTotal = mWork + Long.parseLong(mSa[4]) + Long.parseLong(mSa[5]) + Long.parseLong(mSa[6]) + Long.parseLong(mSa[7]);

            if (mTotalBefore != 0) {
                mTotalT = mTotal - mTotalBefore;
                mWorkT = mWork - mWorkBefore;
                mCpuTotal = restrictPercentage(mWorkT * 100 / (float) mTotalT);
                Log.d(TAG, "CPU Usage: " + restrictPercentage(mWorkT * 100 / (float) mTotalT) + "%");
            }
            mTotalBefore = mTotal;
            mWorkBefore = mWork;

            mReader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return mCpuTotal;
    }

    private static String cmdCat(String f) {

        String[] command = {"cat", f};
        StringBuilder cmdReturn = new StringBuilder();

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();

            InputStream inputStream = process.getInputStream();
            int c;

            while ((c = inputStream.read()) != -1) {
                cmdReturn.append((char) c);
            }
            process.destroy();
            inputStream.close();
            return cmdReturn.toString();

        } catch (IOException e) {
            e.printStackTrace();
            return "Something Wrong";
        }

    }
}