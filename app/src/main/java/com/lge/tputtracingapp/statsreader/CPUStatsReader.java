package com.lge.tputtracingapp.statsreader;

import android.util.Log;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Created by wonsik.lee on 2017-03-13.
 */

public class CPUStatsReader {
    private static final String TAG = CPUStatsReader.class.getSimpleName();

    private static int CPU_COUNT = -1;
    private static CpuFilter sCPU_FILE_Filter = new CpuFilter();
    private static File[] ff = null;

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


    static ProcessBuilder sGetTopProcessBuilder = new ProcessBuilder("/system/xbin/bash", "-c", "top -n 1 -m 1");

    public static int getCpuUsage() {
        InputStream is = null;
        Process p = null;

        StringBuilder sb = new StringBuilder();
        try {
            p = sGetTopProcessBuilder.start();
            is = p.getInputStream();

            int value = -1;
            while ((value = is.read()) != -1) {
                sb.append((char)value);
            }
            value = -1;
            while ((value = is.read()) != -1) {
                sb.append((char)value);
            }
        } catch (IOException exp) {
            exp.printStackTrace();
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (p != null) {
                p.destroy();
            }
        }

        String ret = sb.toString();
        int sum = 0;

        String[] parsingTargetArray = ret.split("\n");
        for (String s: parsingTargetArray[0].split(",")) {
            if (s.contains("User") || s.contains("System") || s.contains("IOW") || s.contains("IRQ")) {
                String[] tmp = s.split(" ");
                String target = tmp[tmp.length - 1].replace("%", "");

                try {
                    sum += Integer.valueOf(target);
                } catch (NumberFormatException e) {
                    sum += 0;
                }
            }
        }


        /*int topUsage = 0;
        for (int i = 1; i != parsingTargetArray.length; ++i) {
            String s = parsingTargetArray[i];
            if (s.contains("system")  && s.contains("top")) {
                String asdf = s.replaceAll("\\s{2,}", " ").trim();
               // Log.d(TAG, "top process : " + asdf);
                String[] tmp = asdf.split(" ");
                String topUsageString = tmp[4].replace("%", "");
                //Log.d(TAG, "top : " + topUsageString);
                topUsage = Integer.valueOf(topUsageString);
                break;
            }
        }*/
        return sum;
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