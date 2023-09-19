package com.sammy.sbatterytweaks;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.CountDownTimer;
import android.provider.Settings;
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
    public static boolean isOngoing;
    public static boolean manualBypass = false;
    public static float temperature;
    public static float thresholdTemp;
    public static int battFullCap = 0;
    public static String currentNow = "";
    public static String voltage = "";
    public static boolean idleEnabled;
    public static boolean disableSync;
    public static boolean autoReset;
    public static boolean bypassSupported;
    static SimpleDateFormat sdf;
    static Date currTime;
    static Date start;
    private static boolean serviceEnabled;
    private static boolean timerEnabled;
    private static boolean shouldCoolDown;
    private static int fastChargeEnabled;
    private static boolean pauseMode;
    private static float tempDelta;
    private static float cdSeconds;
    private static long cooldown;
    private static int startHour, startMinute;
    private static boolean isSchedEnabled;
    private static int idleLevel;

    private static boolean lvlSwitch;

    private static int lvlThreshold;

    private static boolean enableToast;
    private static int duration;
    private static com.topjohnwu.superuser.Shell Shell;

    public static void batteryWorker(Context context, Boolean isCharging) {
        try {
            fastChargeEnabled = Settings.System.getInt(context.getContentResolver(), "adaptive_fast_charging");
            if (fastChargeEnabled == 1) fastChargeStatus = "Enabled";
            else fastChargeStatus = "Disabled";
        } catch (Settings.SettingNotFoundException e) {
            fastChargeStatus = "Not supported";
        }

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
        lvlSwitch = sharedPref.getBoolean(SettingsActivity.PREF_BATT_LVL_SWITCH, false);
        lvlThreshold = sharedPref.getInt(SettingsActivity.PREF_BATT_LVL_THRESHOLD, 60);
        disableSync = sharedPref.getBoolean(SettingsActivity.PREF_DISABLE_SYNC, false);
        autoReset = sharedPref.getBoolean(SettingsActivity.PREF_RESET_STATS, false);
        enableToast = sharedPref.getBoolean(SettingsActivity.PREF_TOAST_NOTIF, false);

        SharedPreferences timePref = context.getSharedPreferences("timePref", Context.MODE_PRIVATE);
        startHour = timePref.getInt(TimePicker.PREF_START_HOUR, 22);
        startMinute = timePref.getInt(TimePicker.PREF_START_MINUTE, 0);
        duration = timePref.getInt(TimePicker.PREF_DURATION, 480);

        if (bypassSupported)
            battFullCap = Integer.parseInt(ShellUtils.fastCmd("cat /sys/class/power_supply/battery/batt_full_capacity"));

        if (!manualBypass && isCharging)
            battWorker(context.getApplicationContext());

        if (MainActivity.isRunning)
            MainActivity.updateStatus(manualBypass);
    }

    public static void setBypass(Boolean state, Boolean isManual) {
        manualBypass = isManual;
        if (state)
            com.topjohnwu.superuser.Shell.cmd("echo " + BatteryService.percentage + " > /sys/class/power_supply/battery/batt_full_capacity").exec();
        else {
            // Allow overriding the toggle when turning it off.
            manualBypass = false;

            com.topjohnwu.superuser.Shell.cmd("echo 100 > /sys/class/power_supply/battery/batt_full_capacity").exec();
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

            public void onFinish() {
                isOngoing = false;
            }
        }.start();
    }

    private static void enableFastCharge(Context context, int enabled)
    {
        Settings.System.putInt(context.getContentResolver(), "adaptive_fast_charging", enabled);
    }

    private static void battWorker(Context context) {
        if (!serviceEnabled)
            return;

        if (idleEnabled && !BatteryService.isBypassed() && battFullCap != idleLevel) {
            com.topjohnwu.superuser.Shell.cmd("echo " + idleLevel + " > /sys/class/power_supply/battery/batt_full_capacity").exec();
            manualBypass = false;
        } else if (idleEnabled && idleLevel == battFullCap && BatteryService.isBypassed()) {
            manualBypass = false;
        } else if ((lvlSwitch && (BatteryService.percentage >= lvlThreshold)) ||
                (isSchedEnabled && isLazyTime())) {
            if (fastChargeEnabled == 1)
                enableFastCharge(context,0);
        } else if (((temperature <= (thresholdTemp - tempDelta)) || (isOngoing && !shouldCoolDown))) {
            if (pauseMode && BatteryService.isBypassed()) {
                setBypass(false, false);

                if (enableToast)
                    Toast.makeText(context, "Charging is resumed!", Toast.LENGTH_SHORT).show();
            } else if (fastChargeEnabled == 0) {
                enableFastCharge(context,1);

                if (enableToast)
                    Toast.makeText(context, "Fast charging mode is re-enabled", Toast.LENGTH_SHORT).show();
            }
        } else if (temperature >= thresholdTemp) {
            if (timerEnabled && !isOngoing)
                startTimer();

            if (pauseMode && !BatteryService.isBypassed()) {
                setBypass(true, false);

                if (enableToast)
                    Toast.makeText(context, "Charging is paused!", Toast.LENGTH_SHORT).show();
            } else if (fastChargeEnabled == 1 && !BatteryService.isBypassed()) {
                enableFastCharge(context,0);

                if (enableToast)
                    Toast.makeText(context, "Fast charging mode is disabled", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public static void updateStats(Boolean charging) {
        temperature = temperature / 10F;
        battTemp = temperature + "°C";

        if (BatteryService.isBypassed())
            chargingState = "Idle";
        else if (charging)
            chargingState = "Charging";
        else
            chargingState = "Discharging";
    }

}
