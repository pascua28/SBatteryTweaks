package com.sammy.chargerateautomator;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class BatteryReceiver extends BroadcastReceiver {
    private ContentResolver contentResolver;
    public TextView chargingState;
    public TextView battTemp;
    public TextView fastChargeStatus;
    static double temperature;
    public static boolean isCharging;
    static String statusText;
    static boolean fastChargeEnabled;
    static double thresholdTemp = 36.5;

    @Override
    public void onReceive(Context context, Intent intent) {
        temperature = Double.parseDouble(getBatteryProps("/sys/class/power_supply/battery/batt_temp")) / 10;

        battTemp.setText(String.valueOf(temperature) + " C");

        isCharging = getBatteryProps("/sys/class/power_supply/battery/status").equals("Charging") ? true : false;

        statusText = getBatteryProps("/sys/class/power_supply/battery/status");

        fastChargeEnabled = Settings.System.getString(context.getContentResolver(), "adaptive_fast_charging").equals("1") ? true : false;

        if (fastChargeEnabled) {
            fastChargeStatus.setText("Enabled");
        } else {
            fastChargeStatus.setText("Disabled");
        }

        if (isCharging) {
            chargingState.setText("Charging");
            if ((temperature > thresholdTemp) && (fastChargeEnabled)) {
                Settings.System.putString(context.getContentResolver(), "adaptive_fast_charging", "0");
                Toast.makeText(context, "Fast charging mode is disabled", Toast.LENGTH_SHORT).show();
            } else if ((temperature < thresholdTemp) && (!fastChargeEnabled)) {
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
