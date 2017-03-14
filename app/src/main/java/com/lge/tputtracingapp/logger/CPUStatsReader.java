package com.lge.tputtracingapp.logger;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.regex.Pattern;

/**
 * Created by wonsik.lee on 2017-03-13.
 */

public class CPUStatsReader {
    private static final String TAG = CPUStatsReader.class.getSimpleName();

    public static int getThermalInfo(String filePath) {
        return Integer.valueOf(cmdCat(getHwmonDir().toString() + "/device/xo_therm").split(":")[1].split(" ")[0]);
    }

    public static HashMap<Integer, Integer> getCpuFreq(String filePath) {
        HashMap<Integer, Integer> ret = new HashMap<Integer, Integer>();

        return ret;
    }

    private static File getHwmonDir() {
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
    }

    private static String cmdCat(String f) {

        String[] command = { "cat", f };
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