package com.sammy.sbatterytweaks;

import static java.lang.Boolean.TRUE;

import android.content.Context;

import com.topjohnwu.superuser.Shell;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class Utils {
    static {
        System.loadLibrary("Utils");
    }

    public static native String readFile(String filePath);

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
