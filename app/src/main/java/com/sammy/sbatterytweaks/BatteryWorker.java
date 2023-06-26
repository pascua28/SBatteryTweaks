package com.sammy.sbatterytweaks;

import android.annotation.SuppressLint;
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
    public static float temperature;
    private static float thresholdTemp;
    private static float tempDelta;
    public static int percentage;
    public static int battFullCap = 0;
    private static float cdSeconds;
    private static long cooldown;

    private static int startHour, startMinute;

    public static String currentNow = "";

    private static boolean isSchedEnabled;

    public static boolean idleEnabled;
    public static boolean disableSync;
    private static int idleLevel;

    public static boolean autoReset;
    static SimpleDateFormat sdf;
    static Date currTime;

    static Date start;

    private static int duration;

    public static boolean bypassSupported;
    private static com.topjohnwu.superuser.Shell Shell;

    public static void batteryWorker(Context context) {
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
        autoReset = sharedPref.getBoolean(SettingsActivity.PREF_RESET_STATS, false);

        SharedPreferences timePref = context.getSharedPreferences("timePref", Context.MODE_PRIVATE);
        startHour = timePref.getInt(TimePicker.PREF_START_HOUR, 22);
        startMinute = timePref.getInt(TimePicker.PREF_START_MINUTE, 0);
        duration = timePref.getInt(TimePicker.PREF_DURATION, 480);

        if (Utils.isRooted() && bypassSupported)
            battFullCap = Integer.parseInt(ShellUtils.fastCmd("cat /sys/class/power_supply/battery/batt_full_capacity"));

        if (!manualBypass)
            battWorker(context.getApplicationContext());

        if (MainActivity.isRunning)
            MainActivity.updateStatus(manualBypass);
    }
    public static void setBypass(Boolean state, Boolean isManual) {
        manualBypass = isManual;
        if (state)
            Shell.cmd("echo " + percentage + " > /sys/class/power_supply/battery/batt_full_capacity").exec();
        else {
            // Allow overriding the toggle when turning it off.
            manualBypass = false;

            Shell.cmd("echo 100 > /sys/class/power_supply/battery/batt_full_capacity").exec();
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

    private static void battWorker(Context context) {
        if (idleEnabled && !BatteryService.isBypassed() && battFullCap != idleLevel) {
            Shell.cmd("echo " + idleLevel + " > /sys/class/power_supply/battery/batt_full_capacity").exec();
            manualBypass = false;
        } else if (idleEnabled && idleLevel == battFullCap && BatteryService.isBypassed()) {
            manualBypass = false;
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
    }

    public static void updateStats(Boolean charging) {
        temperature = temperature / 10F;
        battTemp = temperature + " °C";

        if (BatteryService.isBypassed())
            chargingState = "Idle";
        else if (charging)
            chargingState = "Charging: " + currentNow;
        else
            chargingState = "Discharging: " + currentNow;
    }

}
