package com.sammy.chargerateautomator;

import static java.lang.Boolean.TRUE;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

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
