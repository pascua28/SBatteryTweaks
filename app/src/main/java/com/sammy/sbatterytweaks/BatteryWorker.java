package com.sammy.sbatterytweaks;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.text.TextUtils;
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
    public static int currentNow;
    public static String voltage = "";
    public static boolean idleEnabled;
    public static boolean disableSync;
    public static boolean autoReset;
    public static boolean bypassSupported;

    public static boolean pausePdSupported;
    static SimpleDateFormat sdf;
    static Date currTime;
    static Date start;
    private static boolean serviceEnabled;
    private static boolean timerEnabled;
    private static boolean shouldCoolDown;

    public static boolean pausePdEnabled;
    private static int fastChargeEnabled;
    private static boolean pauseMode;
    public static float tempDelta;
    private static float cdSeconds;
    private static long cooldown;
    private static int startHour, startMinute;
    private static boolean isSchedEnabled;
    private static int idleLevel;

    private static boolean lvlSwitch;

    private static int lvlThreshold;

    private static boolean enableToast;
    private static int duration;
    private static boolean protectEnabled;

    public void batteryWorker(Context context, Boolean isCharging) {
        try {
            fastChargeEnabled = Settings.System.getInt(context.getContentResolver(), "adaptive_fast_charging");
            if (fastChargeEnabled == 1) fastChargeStatus = "Enabled";
            else fastChargeStatus = "Disabled";
        } catch (Settings.SettingNotFoundException e) {
            fastChargeStatus = "Not supported";
        }

        pausePdSupported = !TextUtils.isEmpty(Settings.System.getString(context.getContentResolver(), "pass_through"));
        if (pausePdSupported)
            pausePdEnabled = Settings.System.getString(context.getContentResolver(), "pass_through").equals("1");

        protectEnabled = Settings.Global.getString(context.getContentResolver(), "protect_battery").equals("1");

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

    public static void setBypass(Context context, int enabled, Boolean isManual) {
        manualBypass = isManual;

        if (enabled == 0) {
            // Allow overriding the toggle when turning it off.
            manualBypass = false;
        }

        if (pausePdSupported) {
            try {
                Settings.System.putInt(context.getContentResolver(), "pass_through", enabled);
            } catch (Exception e) {
                try {
                    ContentResolver cr = context.getContentResolver();

                    ContentValues cv = new ContentValues(2);
                    cv.put("name", "pass_through");
                    cv.put("value", enabled);
                    cr.insert(Uri.parse("content://com.netvor.provider.SettingsDatabaseProvider/system"), cv);
                } catch (Exception f) {
                    if (Utils.isRooted())
                        com.topjohnwu.superuser.Shell.cmd("settings put system pass_through" + enabled).exec();
                }
            }
            return;
        }

        if (enabled == 1)
            com.topjohnwu.superuser.Shell.cmd("echo " + BatteryService.percentage + " > /sys/class/power_supply/battery/batt_full_capacity").exec();
        else {
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

    private void enableFastCharge(Context context, int enabled)
    {
        try {
            Settings.System.putInt(context.getContentResolver(), "adaptive_fast_charging", enabled);
        } catch (Exception e) {
            try {
                ContentResolver cr = context.getContentResolver();

                ContentValues cv = new ContentValues(2);
                cv.put("name", "adaptive_fast_charging");
                cv.put("value", enabled);
                cr.insert(Uri.parse("content://com.netvor.provider.SettingsDatabaseProvider/system"), cv);
            } catch (Exception f) {
                if (Utils.isRooted())
                    com.topjohnwu.superuser.Shell.cmd("settings put system adaptive_fast_charging " + enabled).exec();
                else {
                    fastChargeStatus = fastChargeStatus + " (failed to toggle)";
                    e.printStackTrace();
                    f.printStackTrace();
                }
            }
        }
    }

    private void battWorker(Context context) {
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
                enableFastCharge(context, 0);
        } else if (((temperature <= (thresholdTemp - tempDelta)) || (isOngoing && !shouldCoolDown))) {
            if (pauseMode && BatteryService.isBypassed()) {
                setBypass(context, 0, false);

                if (enableToast)
                    Toast.makeText(context, "Charging is resumed!", Toast.LENGTH_SHORT).show();
            } else if (fastChargeEnabled == 0) {
                enableFastCharge(context,1);

                if (enableToast)
                    Toast.makeText(context, "Fast charging mode is re-enabled", Toast.LENGTH_SHORT).show();
            }
        } else if (currentNow < 50 && currentNow > -50) {
            if (fastChargeEnabled == 0)
                enableFastCharge(context,1);
        } else if (temperature >= thresholdTemp) {
            if (timerEnabled && !isOngoing)
                startTimer();

            if (pauseMode && !BatteryService.isBypassed()) {
                setBypass(context, 1,false);

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

        if (BatteryService.isBypassed() || (!bypassSupported && protectEnabled && BatteryService.percentage >= 85))
            chargingState = "Idle";
        else if (charging)
            chargingState = "Charging";
        else
            chargingState = "Discharging";
    }

}
