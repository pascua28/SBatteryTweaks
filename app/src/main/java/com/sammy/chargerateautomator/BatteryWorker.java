package com.sammy.chargerateautomator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;
import java.util.Objects;

public class BatteryWorker extends BroadcastReceiver {
    public static String chargingState;
    public static String battTemp;
    public static String fastChargeStatus;
    private static boolean serviceEnabled;
    public static boolean isCharging;
    public static boolean isOngoing;
    private boolean timerEnabled;
    private boolean shouldCoolDown;
    private static boolean fastChargeEnabled;
    private static boolean protectEnabled;
    private static boolean pauseMode;
    private static float temperature;
    private static float thresholdTemp;
    private static float tempDelta;
    private static int percentage;
    private static int battFullCap = 0;
    private float cdSeconds;
    private long cooldown;

    private int startHour, startMinute;

    static String tempFile = "/sys/class/power_supply/battery/batt_temp";
    static String percentageFile = "/sys/class/power_supply/battery/capacity";
    static String currentFile = "/sys/class/power_supply/battery/current_avg";
    static String currentNow;

    private boolean isSchedEnabled;
    private boolean schedIdleEnabled;
    private int schedIdleLevel;
    private String start_time;
    LocalDate currDate;
    SimpleDateFormat sdf;
    Date currTime, start;

    private long currentTimeMillis;
    private long startMillis;
    private long endMillis;
    private int duration;

    @Override
    public void onReceive(Context context, Intent intent) {
        fastChargeEnabled = Objects.equals(Settings.System.getString(context.getContentResolver(), "adaptive_fast_charging"), "1");
        protectEnabled = Objects.equals(Settings.Global.getString(context.getContentResolver(), "protect_battery"), "1");
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
        schedIdleEnabled = sharedPref.getBoolean(SettingsActivity.PREF_SCHED_IDLE, false);
        schedIdleLevel = sharedPref.getInt(SettingsActivity.PREF_SCHED_IDLE_LEVEL, 85);

        SharedPreferences timePref = context.getSharedPreferences("timePref", Context.MODE_PRIVATE);
        startHour = timePref.getInt(TimePicker.PREF_START_HOUR, 22);
        startMinute = timePref.getInt(TimePicker.PREF_START_MINUTE, 0);
        duration = timePref.getInt(TimePicker.PREF_DURATION, 480);

        if (Utils.isRooted()) {
            battFullCap = Integer.parseInt(ShellUtils.fastCmd("cat /sys/class/power_supply/battery/batt_full_capacity"));
        } else {
            if (protectEnabled) {
                battFullCap = 85;
            } else {
                battFullCap = 100;
            }
        }

        if (MainActivity.isRunning)
            MainActivity.updateStatus();

        battWorker(context);

        if (isCharging && isBypassed()) {
            if (!fastChargeEnabled)
                Settings.System.putString(context.getContentResolver(), "adaptive_fast_charging", "1");
            chargingState = "Idle";
        }
    }

    public static boolean isBypassed() {
        return percentage >= battFullCap && battFullCap < 100;
    }

    public static void setBypass(Boolean state) {
        if (state) {
            Shell.cmd("echo " + percentage + "> /sys/class/power_supply/battery/batt_full_capacity").exec();
        } else {
            if (protectEnabled) {
                Shell.cmd("echo 85 > /sys/class/power_supply/battery/batt_full_capacity").exec();
            } else {
                Shell.cmd("echo 100 > /sys/class/power_supply/battery/batt_full_capacity").exec();
            }
        }
    }

    private boolean isLazyTime() {
        start_time = String.format(LocalDate.now().toString() + "-%02d:%02d", startHour, startMinute);
        currDate = LocalDate.now();
        sdf = new SimpleDateFormat("yyyy-MM-dd-hh:mm");
        currTime = Calendar.getInstance().getTime();
        start = currTime;

        try {
            start = sdf.parse(start_time);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        currentTimeMillis = currTime.getTime();
        startMillis = start.getTime();
        endMillis = startMillis + (duration * 60 * 1000);

        if ((currentTimeMillis > startMillis) && (currentTimeMillis < endMillis)) return true;
        else return false;
    }

    private void startTimer() {
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

    private void battWorker(Context context) {
        if (isCharging) {
            chargingState = "Charging: " + currentNow;

            if (isSchedEnabled && isLazyTime()) {
                if (schedIdleEnabled && percentage >= schedIdleLevel && !isBypassed() && Utils.isRooted()) {
                    setBypass(true);
                } else if (fastChargeEnabled) {
                    Settings.System.putString(context.getContentResolver(), "adaptive_fast_charging", "0");
                }
            } else if (((temperature <= (thresholdTemp - tempDelta)) || (isOngoing && !shouldCoolDown)) && serviceEnabled) {
                if (pauseMode && isBypassed()) {
                    setBypass(false);
                    Toast.makeText(context, "Charging is resumed!", Toast.LENGTH_SHORT).show();
                } else if (!fastChargeEnabled) {
                    Settings.System.putString(context.getContentResolver(), "adaptive_fast_charging", "1");
                    Toast.makeText(context, "Fast charging mode is re-enabled", Toast.LENGTH_SHORT).show();
                }
            } else if ((temperature >= thresholdTemp) && serviceEnabled) {
                if (timerEnabled && !isOngoing)
                    startTimer();

                if (pauseMode && !isBypassed()) {
                    setBypass(true);
                    Toast.makeText(context, "Charging is paused!", Toast.LENGTH_SHORT).show();
                } else if (fastChargeEnabled && !isBypassed()) {
                    Settings.System.putString(context.getContentResolver(), "adaptive_fast_charging", "0");
                    Toast.makeText(context, "Fast charging mode is disabled", Toast.LENGTH_SHORT).show();
                }
            }
        } else chargingState = "Discharging: " + currentNow;
    }

    public static void updateStats(Boolean readMode) {
        if (readMode) {
            temperature = Float.parseFloat(Utils.readFile(tempFile));
            percentage = Integer.parseInt(Utils.readFile(percentageFile));
            currentNow = Utils.readFile(currentFile) + " mA";
        } else {
            temperature = Float.parseFloat(ShellUtils.fastCmd("cat " + tempFile));
            percentage = Integer.parseInt(ShellUtils.fastCmd("cat " + percentageFile));
            currentNow = ShellUtils.fastCmd("cat " + currentFile) + " mA";
        }

        temperature = temperature / 10F;
        battTemp = temperature + "° C";
    }

}