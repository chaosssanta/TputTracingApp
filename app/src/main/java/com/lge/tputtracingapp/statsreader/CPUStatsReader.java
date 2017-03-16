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

// /sys/devices/system/cpu/
    public static int getThermalInfo(String filePath) {
        try {
            return Integer.valueOf(cmdCat(filePath).split(":")[1].split(" ")[0]);
        } catch (IndexOutOfBoundsException e) {
            return -1;
        }
    }

    public static ArrayList<Integer> getCpuFreq(String filePath) {
        if (CPU_COUNT == -1) {
            Log.d(TAG, "CPU_COUNT init");
            CPU_COUNT = getCPUs(filePath);
        }
        ArrayList<Integer> ret = new ArrayList<>();
        for (int i = 0; i < CPU_COUNT; i++) {
            ret.add(Integer.valueOf(cmdCat(filePath + "cpu" + i + "/cpufreq/scaling_cur_freq").replace("\n", "")));
        }
        return ret;
    }

    private static int getCPUs(String filePath) {
        class CpuFilter implements FileFilter {
            @Override
            public boolean accept(File path) {
                if (Pattern.matches("cpu[0-9]+", path.getName())) {
                    return true;
                }
                return false;
            }
        }

        return new File(filePath).listFiles(new CpuFilter()).length;
    }

/*    private static File getHwmonDir() {
        class HwmonFilter implements FileFilter {
            @Override
            public boolean accept(File pathname) {
                if ((Pattern.matches("hwmon[0-9]+", pathname.getName())) || Pattern.matches("hwmon[10-19]+", pathname.getName())) {
                    return true;
                }
                return false;
            }
        }

        File dir = new File("/sys/class/hwmon/");
        File[] files = dir.listFiles(new HwmonFilter());
        String cmd = null;
        String ret = null;
        for (File file : files) {
            cmd = file + "/device/xo_therm";
            ret = cmdCat(cmd);
            if (ret != null && ret.contains("Result:")) {
                return file;
            }
        }
        return null;
    }*/

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