package com.sammy.chargerateautomator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.topjohnwu.superuser.Shell;

import java.io.File;
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

    static File chargingFile = new File("/sys/class/power_supply/battery/charge_now");
    static File tempFile = new File("/sys/class/power_supply/battery/batt_temp");
    static File percentageFile = new File("/sys/class/power_supply/battery/capacity");

    @Override
    public void onReceive(Context context, Intent intent) {

        temperature = Float.parseFloat(Utils.readFile(tempFile)) / 10F;
        percentage = Integer.parseInt(Utils.readFile(percentageFile));

        battTemp = String.valueOf(temperature) + " C";
        isCharging = Objects.equals(Utils.readFile(chargingFile), "1");
        fastChargeEnabled = Objects.equals(Settings.System.getString(context.getContentResolver(), "adaptive_fast_charging"), "1");
        protectEnabled = Objects.equals(Settings.Global.getString(context.getContentResolver(), "protect_battery"), "1");
        if (fastChargeEnabled) fastChargeStatus = "Enabled";
        else fastChargeStatus = "Disabled";

        if (MainActivity.isRootAvailable) {
            battFullCap = Integer.parseInt(Utils.runAndGetOutput("cat /sys/class/power_supply/battery/batt_full_capacity"));
        } else {
            if (protectEnabled) {
                battFullCap = 85;
            } else {
                battFullCap = 100;
            }
        }

        SharedPreferences sharedPref =
                PreferenceManager.getDefaultSharedPreferences(context);
        serviceEnabled = sharedPref.getBoolean(SettingsActivity.KEY_PREF_SERVICE, true);
        thresholdTemp = sharedPref.getFloat(SettingsActivity.KEY_PREF_THRESHOLD_UP, 36.5F);
        tempDelta = sharedPref.getFloat(SettingsActivity.KEY_PREF_TEMP_DELTA, 0.5F);
        pauseMode = sharedPref.getBoolean(SettingsActivity.KEY_PREF_BYPASS_MODE, false);
        timerEnabled = sharedPref.getBoolean(SettingsActivity.KEY_PREF_TIMER_SWITCH, false);
        cdSeconds = sharedPref.getFloat(SettingsActivity.KEY_PREF_CD_SECONDS, 30F);

        battWorker(context);

        if (MainActivity.isRunning)
            MainActivity.updateStatus();
    }

    public static boolean isBypassed() {
        if (percentage >= battFullCap) return true;
        else return false;
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

    private void startTimer() {
        cooldown = (long) cdSeconds * 1000 * 2;
        new CountDownTimer(cooldown, 1000) {
                public void onTick(long millisUntilFinished) {
                    isOngoing = true;

                    if (millisUntilFinished > (cooldown / 2))
                        shouldCoolDown = true;
                    else
                        shouldCoolDown = false;
                }

                public void onFinish () {
                    isOngoing = false;
                }
            }.start();
    }

    private void battWorker(Context context) {
        if (isCharging) {
            chargingState = "Charging: " + percentage + "%";

            if (isBypassed() && (protectEnabled || MainActivity.isRootAvailable)) {
                if (!fastChargeEnabled)
                    Settings.System.putString(context.getContentResolver(), "adaptive_fast_charging", "1");
                chargingState = "Idle";
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
                } else if (fastChargeEnabled) {
                    Settings.System.putString(context.getContentResolver(), "adaptive_fast_charging", "0");
                    Toast.makeText(context, "Fast charging mode is disabled", Toast.LENGTH_SHORT).show();
                }
            }
        } else chargingState = "Discharging: " + percentage + "%";
    }
}