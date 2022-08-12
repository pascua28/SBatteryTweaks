package com.sammy.chargerateautomator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class BatteryReceiver extends BroadcastReceiver {
    public TextView chargingState;
    public TextView battTemp;
    public TextView fastChargeStatus;
    private static boolean serviceEnabled;
    public static boolean isCharging;
    private static boolean fastChargeEnabled;
    private static String statusText;
    private static float temperature;
    private static float thresholdTemp;
    private static float thresholdDown;

    @Override
    public void onReceive(Context context, Intent intent) {
        temperature = Float.parseFloat(getBatteryProps("/sys/class/power_supply/battery/batt_temp")) / 10F;

        battTemp.setText(String.valueOf(temperature) + " C");

        isCharging = getBatteryProps("/sys/class/power_supply/battery/status").equals("Charging") ? true : false;

        statusText = getBatteryProps("/sys/class/power_supply/battery/status");

        fastChargeEnabled = Settings.System.getString(context.getContentResolver(), "adaptive_fast_charging").equals("1") ? true : false;

        SharedPreferences sharedPref =
                PreferenceManager.getDefaultSharedPreferences(context);
        serviceEnabled = sharedPref.getBoolean(SettingsActivity.KEY_PREF_SERVICE, true);
        thresholdTemp = sharedPref.getFloat(SettingsActivity.KEY_PREF_THRESHOLD_UP, 36.5F);

        //Give it time to cool down
        thresholdDown = thresholdTemp - 0.4F;

        if (fastChargeEnabled) {
            fastChargeStatus.setText("Enabled");
        } else {
            fastChargeStatus.setText("Disabled");
        }

        if (isCharging) {
            chargingState.setText("Charging");
            if ((temperature > thresholdTemp) && (fastChargeEnabled) && serviceEnabled) {
                Settings.System.putString(context.getContentResolver(), "adaptive_fast_charging", "0");
                Toast.makeText(context, "Fast charging mode is disabled", Toast.LENGTH_SHORT).show();
            } else if ((temperature < thresholdDown) && (!fastChargeEnabled) && serviceEnabled) {
                Settings.System.putString(context.getContentResolver(), "adaptive_fast_charging", "1");
                Toast.makeText(context, "Fast charging mode is re-enabled", Toast.LENGTH_SHORT).show();
            }
        } else {
            chargingState.setText("Discharging");
        }
    }

    public static String getBatteryProps(String filePath) {
        File file = new File(filePath);
        BufferedReader buf = null;
        try {
            buf = new BufferedReader(new FileReader(file));

            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = buf.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }

            return stringBuilder.toString().trim();
        } catch (IOException ignored) {
        } finally {
            try {
                if (buf != null) buf.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
