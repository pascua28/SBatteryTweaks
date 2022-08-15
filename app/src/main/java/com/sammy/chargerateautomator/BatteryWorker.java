package com.sammy.chargerateautomator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.topjohnwu.superuser.Shell;

import java.io.File;

public class BatteryWorker extends BroadcastReceiver {
    public TextView chargingState;
    public TextView battTemp;
    public TextView fastChargeStatus;
    private static boolean serviceEnabled;
    public static boolean isCharging;
    private static boolean fastChargeEnabled;
    private static boolean protectEnabled;
    private static boolean chargeNow;
    private static float temperature;
    private static float thresholdTemp;
    private static float tempDelta;
    private static int percentage;
    private static int battFullCap = 0;

    static File statusFile = new File("/sys/class/power_supply/battery/status");
    static File chargeNowFile = new File("/sys/class/power_supply/battery/charge_now");
    static File tempFile = new File("/sys/class/power_supply/battery/batt_temp");
    static File percentageFile = new File("/sys/class/power_supply/battery/capacity");

    @Override
    public void onReceive(Context context, Intent intent) {

        temperature = Float.parseFloat(Utils.readFile(tempFile)) / 10F;
        percentage = Integer.parseInt(Utils.readFile(percentageFile));

        battTemp.setText(String.valueOf(temperature) + " C");
        isCharging = Utils.readFile(statusFile).equals("Charging") ? true : false;
        chargeNow = Utils.readFile(chargeNowFile).equals("1") ? true : false;
        fastChargeEnabled = Settings.System.getString(context.getContentResolver(), "adaptive_fast_charging").equals("1") ? true : false;
        protectEnabled = Settings.Global.getString(context.getContentResolver(), "protect_battery").equals("1") ? true : false;

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

        if (fastChargeEnabled)
            fastChargeStatus.setText("Enabled");
        else
            fastChargeStatus.setText("Disabled");

        if (isCharging) {
            chargingState.setText("Charging: " + percentage + "%");
            if ((temperature >= thresholdTemp) && (fastChargeEnabled) && serviceEnabled) {
                Settings.System.putString(context.getContentResolver(), "adaptive_fast_charging", "0");
                Toast.makeText(context, "Fast charging mode is disabled", Toast.LENGTH_SHORT).show();
            } else if ((temperature <= (thresholdTemp - tempDelta)) && (!fastChargeEnabled) && serviceEnabled) {
                Settings.System.putString(context.getContentResolver(), "adaptive_fast_charging", "1");
                Toast.makeText(context, "Fast charging mode is re-enabled", Toast.LENGTH_SHORT).show();
            }
        } else if (chargeNow &&  percentage >= battFullCap && (protectEnabled || MainActivity.isRootAvailable)) {
            if (!fastChargeEnabled)
                Settings.System.putString(context.getContentResolver(), "adaptive_fast_charging", "1");
            chargingState.setText("Idle");
        } else
            chargingState.setText("Discharging: " + percentage + "%");

        if (MainActivity.isRootAvailable) {
            if (percentage >= battFullCap) {
                MainActivity.bypassToggle.setTag("BYPASS");
                MainActivity.bypassToggle.setChecked(true);
            } else {
                MainActivity.bypassToggle.setTag("BYPASS");
                MainActivity.bypassToggle.setChecked(false);
            }
        }
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
}
