package com.sammy.sbatterytweaks;

import static java.lang.Boolean.TRUE;

import android.content.Context;

import com.topjohnwu.superuser.Shell;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class Utils {
    private static StringBuilder stringBuilder = new StringBuilder();
    private static BufferedReader buf = null;

    public static String readFile(String filePath) {
        File file = new File(filePath);
        buf = null;
        try {
            buf = new BufferedReader(new FileReader(file));
            stringBuilder.setLength(0);

            String line;
            while ((line = buf.readLine()) != null) {
                stringBuilder.append(line);
            }

            return stringBuilder.toString();
        } catch (IOException ignored) {
        } finally {
            try {
                if (buf != null) buf.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return "0";
    }

    public static boolean isRooted() {
        Shell.getShell();
        if (TRUE.equals(Shell.isAppGrantedRoot())) {
            return true;
        } else {
            return false;
        }
    }

    public static int getActualCapacity(Context context) {
        int mCapacity;
        try {
            Class<?> powerProfile = Class.forName("com.android.internal.os.PowerProfile");
            Constructor constructor = powerProfile.getDeclaredConstructor(Context.class);
            Object powerProInstance = constructor.newInstance(context);
            Method batteryCap = powerProfile.getMethod("getBatteryCapacity");
            mCapacity = Math.round((long) (double) batteryCap.invoke(powerProInstance));
        } catch (Exception e) {
            e.printStackTrace();
            mCapacity = 0;
        }

        return mCapacity;
    }
}
