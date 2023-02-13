package com.sammy.sbatterytweaks;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.CountDownTimer;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.topjohnwu.superuser.ShellUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;

public class BatteryWorker {
    public static String chargingState;
    public static String battTemp;
    public static String fastChargeStatus;
    private static boolean serviceEnabled;
    public static boolean isOngoing;
    private static boolean timerEnabled;
    private static boolean shouldCoolDown;
    public static boolean manualBypass = false;
    private static boolean fastChargeEnabled;
    private static boolean pauseMode;
    private static float temperature;
    private static float thresholdTemp;
    private static float tempDelta;
    private static int percentage;
    public static int battTestMode = 0;
    private static float cdSeconds;
    private static long cooldown;

    private static int startHour, startMinute;

    static String tempFile = "/sys/class/power_supply/battery/temp";
    static String percentageFile = "/sys/class/power_supply/battery/capacity";
    static String currentFile = "/sys/class/power_supply/battery/current_avg";
    static String currentNow = "";

    private static boolean isSchedEnabled;

    public static boolean idleEnabled;
    private static boolean disableSync;
    private static int idleLevel;
    static SimpleDateFormat sdf;
    static Date currTime;

    static Date start;

    private static int duration;

    public static boolean bypassSupported;
    private static com.topjohnwu.superuser.Shell Shell;

    public static void batteryWorker(Context context, boolean charging) {
        fastChargeEnabled = ShellUtils.fastCmd("settings get system adaptive_fast_charging").equals("1");
        if (fastChargeEnabled) fastChargeStatus = "Enabled";
        else fastChargeStatus = "Disabled";

        SharedPreferences sharedPref =
                PreferenceManager.getDefaultSharedPreferences(context);
        serviceEnabled = sharedPref.getBoolean(SettingsActivity.KEY_PREF_SERVICE, true);
        thresholdTemp = sharedPref.getFloat(SettingsActivity.KEY_PREF_THRESHOLD_UP, 36.5F);
        tempDelta = sharedPref.getFloat(SettingsActivity.KEY_PREF_TEMP_DELTA, 0.5F);
        pauseMode = sharedPref.getBoolean(SettingsActivity.KEY_PREF_BYPASS_MODE, false);
        timerEnabled = sharedPref.getBoolean(SettingsActivity.KEY_PREF_TIMER_SWITCH, false);
        cdSeconds = sharedPref.getFloat(SettingsActivity.KEY_PREF_CD_SECONDS, 30F);
        isSchedEnabled = sharedPref.getBoolean(SettingsActivity.PREF_SCHED_ENABLED, false);
        idleEnabled = sharedPref.getBoolean(SettingsActivity.PREF_IDLE_SWITCH, false);
        idleLevel = sharedPref.getInt(SettingsActivity.PREF_IDLE_LEVEL, 75);
        disableSync = sharedPref.getBoolean(SettingsActivity.PREF_DISABLE_SYNC, false);

        SharedPreferences timePref = context.getSharedPreferences("timePref", Context.MODE_PRIVATE);
        startHour = timePref.getInt(TimePicker.PREF_START_HOUR, 22);
        startMinute = timePref.getInt(TimePicker.PREF_START_MINUTE, 0);
        duration = timePref.getInt(TimePicker.PREF_DURATION, 480);

        if (Utils.isRooted() && bypassSupported)
            battTestMode = Integer.parseInt(ShellUtils.fastCmd("cat /sys/class/power_supply/battery/test_mode"));

        if (MainActivity.isRunning)
            MainActivity.updateStatus();

        if (!manualBypass)
            battWorker(context.getApplicationContext(), charging);

        if (BatteryService.isBypassed())
            chargingState = "Idle";
    }
    public static void setBypass(Boolean state, Boolean isManual) {
        manualBypass = isManual;
        if (state)
            Shell.cmd("echo 1 > /sys/class/power_supply/battery/test_mode").exec();
        else {
            // Allow overriding the toggle when turning it off.
            manualBypass = false;

            // From the kernel source, writing "2" to test_mode should be enough but it doesn't cover all charging cases.
            Shell.cmd("echo 0 > /sys/class/power_supply/battery/test_mode").exec();

            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            Shell.cmd("echo 1 > /sys/class/power_supply/battery/batt_slate_mode").exec();

            try {
                Thread.sleep(100);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            Shell.cmd("echo 0 > /sys/class/power_supply/battery/batt_slate_mode").exec();
        }
    }

    @SuppressLint("SimpleDateFormat")
    private static boolean isLazyTime() {
        String start_time = String.format(LocalDate.now().toString() + "-%02d:%02d", startHour, startMinute);
        sdf = new SimpleDateFormat("yyyy-MM-dd-hh:mm");
        currTime = Calendar.getInstance().getTime();
        start = currTime;

        try {
            start = sdf.parse(start_time);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        long currentTimeMillis = currTime.getTime();
        long startMillis = start.getTime();
        long endMillis = startMillis + ((long) duration * 60 * 1000);

        return (currentTimeMillis > startMillis) && (currentTimeMillis < endMillis);
    }

    private static void startTimer() {
        cooldown = (long) cdSeconds * 1000 * 2;
        new CountDownTimer(cooldown, 1000) {
                public void onTick(long millisUntilFinished) {
                    isOngoing = true;

                    shouldCoolDown = millisUntilFinished > (cooldown / 2);
                }

                public void onFinish () {
                    isOngoing = false;
                }
            }.start();
    }

    private static void battWorker(Context context, boolean charging) {
        if (charging) {
            chargingState = "Charging: " + currentNow;

            if (disableSync && !ContentResolver.getMasterSyncAutomatically())
                ContentResolver.setMasterSyncAutomatically(true);

            if (idleEnabled && (percentage >= idleLevel) && !BatteryService.isBypassed()) {
                setBypass(true, false);
            } else if (isSchedEnabled && isLazyTime()) {
                if (fastChargeEnabled)
                    ShellUtils.fastCmd("settings put system adaptive_fast_charging 0");
            } else if (((temperature <= (thresholdTemp - tempDelta)) || (isOngoing && !shouldCoolDown)) && serviceEnabled) {
                if (pauseMode && BatteryService.isBypassed()) {
                    setBypass(false, false);
                    Toast.makeText(context, "Charging is resumed!", Toast.LENGTH_SHORT).show();
                } else if (!fastChargeEnabled) {
                    ShellUtils.fastCmd(" settings put system adaptive_fast_charging 1");
                    Toast.makeText(context, "Fast charging mode is re-enabled", Toast.LENGTH_SHORT).show();
                }
            } else if ((temperature >= thresholdTemp) && serviceEnabled) {
                if (timerEnabled && !isOngoing)
                    startTimer();

                if (pauseMode && !BatteryService.isBypassed()) {
                    setBypass(true, false);
                    Toast.makeText(context, "Charging is paused!", Toast.LENGTH_SHORT).show();
                } else if (fastChargeEnabled && !BatteryService.isBypassed()) {
                    ShellUtils.fastCmd(" settings put system adaptive_fast_charging 0");
                    Toast.makeText(context, "Fast charging mode is disabled", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            if (disableSync && ContentResolver.getMasterSyncAutomatically())
                ContentResolver.setMasterSyncAutomatically(false);
            chargingState = "Discharging: " + currentNow;
        }
    }

    public static void updateStats() {
        temperature = Float.parseFloat(ShellUtils.fastCmd("cat " + tempFile));
        percentage = Integer.parseInt(ShellUtils.fastCmd("cat " + percentageFile));
        currentNow = ShellUtils.fastCmd("cat " + currentFile) + " mA";

        temperature = temperature / 10F;
        battTemp = temperature + " °C";
    }

}
