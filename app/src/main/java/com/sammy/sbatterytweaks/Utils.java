package com.sammy.sbatterytweaks;

import static java.lang.Boolean.TRUE;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

public class Utils {
    public static boolean isRooted() {
        Shell.getShell();
        return TRUE.equals(Shell.isAppGrantedRoot());
    }

    public static boolean isPrivileged() {
        if (isRooted()) {
            return true;
        } else if (shizukuCheckPermission()) return Shizuku.getUid() == 1000;

        return false;
    }

    public static boolean isShizuku() {
        return shizukuCheckPermission();
    }

    public static String runCmd(String commands) {
        if (isRooted()) {
            return ShellUtils.fastCmd(commands);
        } else if (shizukuCheckPermission()) {
            try {
                return runProcess(exec(commandsToShCommand(commands)));
            } catch (Exception ignored) {
                return "0";
            }
        }
        return "0";
    }

    private static String[] commandsToShCommand(String... commands) {
        String joinedCommands = String.join("\n", commands);
        return new String[]{"sh", "-c", joinedCommands};
    }

    public static ShizukuRemoteProcess exec(String[] command) throws Exception {
        Method method = Shizuku.class.getDeclaredMethod(
                "newProcess",
                String[].class,
                String[].class,
                String.class
        );

        method.setAccessible(true);

        return (ShizukuRemoteProcess) method.invoke(null, command, null, "/");
    }

    private static String runProcess(Process process) throws Exception {
        BufferedReader stdOutStream = new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedReader stdErrStream = new BufferedReader(new InputStreamReader(process.getErrorStream()));

        String stdOut = stdOutStream.lines().reduce("", (acc, line) -> acc + line + "\n").trim();
        String stdErr = stdErrStream.lines().reduce("", (acc, line) -> acc + line + "\n").trim();

        stdOutStream.close();
        stdErrStream.close();

        process.waitFor();

        if (process.exitValue() != 0) {
            throw new Exception(stdErr);
        }

        return stdOut;
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

    public static boolean shizukuCheckPermission() {
        if (Shizuku.isPreV11()) {
            return false;
        }
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                return true;
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                return false;
            } else {
                Shizuku.requestPermission(1);
                return false;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return false;
    }

    public static int changeSetting(Context context, Namespace namespace, String setting, int value) {
        try {
            if (namespace == Namespace.SYSTEM) {
                Settings.System.putInt(context.getContentResolver(), setting, value);
            } else if (namespace == Namespace.GLOBAL) {
                Settings.Global.putInt(context.getContentResolver(), setting, value);
            }
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            try {
                ContentResolver cr = context.getContentResolver();

                ContentValues cv = new ContentValues(2);
                cv.put("name", setting);
                cv.put("value", value);
                cr.insert(Uri.parse("content://com.netvor.provider.SettingsDatabaseProvider/" + namespace.getNs()), cv);
                return 0;
            } catch (Exception f) {
                f.printStackTrace();
                if (isPrivileged() || isShizuku()) {
                    runCmd("settings put " + namespace.getNs() + " " + setting + " " + value);
                    return 0;
                } else return -1;
            }
        }
    }

    public enum Namespace {
        GLOBAL("global"),
        SYSTEM("system");

        private final String ns;

        Namespace(String ns) {
            this.ns = ns;
        }

        String getNs() {
            return ns;
        }
    }
}
