package com.sammy.sbatterytweaks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.Objects;

public class ScreenReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences sharedPref =
                PreferenceManager.getDefaultSharedPreferences(context);
        boolean forceLowestHz = sharedPref.getBoolean(SettingsActivity.PREF_LOWEST_HZ, false);
        switch (Objects.requireNonNull(intent.getAction())) {
            case Intent.ACTION_SCREEN_ON:
                if (!BatteryReceiver.isCharging() && !BatteryService.isBypassed()) {
                    DrainMonitor.handleScreenChange(true);
                }
                if (forceLowestHz) {
                    RefreshRateController.onScreenOn(context);
                }
                break;
            case Intent.ACTION_SCREEN_OFF:
                if (!BatteryReceiver.isCharging() && !BatteryService.isBypassed()) {
                    DrainMonitor.handleScreenChange(false);
                }
                if (forceLowestHz) {
                    try {
                        Thread.sleep(3000L);
                    } catch (InterruptedException ignored) {
                    }
                    RefreshRateController.onScreenOff(context);
                }
                break;
        }
    }
}
