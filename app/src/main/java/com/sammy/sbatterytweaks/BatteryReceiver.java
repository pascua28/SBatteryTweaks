package com.sammy.sbatterytweaks;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.BatteryManager;

import androidx.preference.PreferenceManager;

import com.topjohnwu.superuser.ShellUtils;

import java.io.File;
import java.util.Objects;

public class BatteryReceiver extends BroadcastReceiver {
    public static int mLevel, mVolt = -1;
    public static float mTemp;
    public static boolean drainMonitorEnabled = false;
    public static boolean isUsbCharging, isWirelessCharging;
    private static int mPlugged, mStatus;
    private static int lastReportedLevel = -1;
    private final File statsFile = new File("/data/system/batterystats.bin");

    public static boolean isCharging() {
        return mPlugged > 0;
    }

    public static boolean notCharging() {
        return mPlugged > 0 && mStatus == BatteryManager.BATTERY_STATUS_NOT_CHARGING;
    }

    public static int getCounter(Context context) {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
    }

    public static float getTemp() {
        return mTemp;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        mTemp = ((float) intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10);
        mLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        mPlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        mStatus = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        mVolt = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        isUsbCharging = mPlugged == BatteryManager.BATTERY_PLUGGED_USB;
        isWirelessCharging = mPlugged == BatteryManager.BATTERY_PLUGGED_WIRELESS;

        if (drainMonitorEnabled && mLevel != lastReportedLevel) {
            lastReportedLevel = mLevel;
            int counter = getCounter(context);
            if (counter > 0) {
                DrainMonitor.recordRegressionSample(context, mLevel, counter, isCharging());
            }
        }

        updateStatusPref(context, mLevel, isCharging(), BatteryService.isBypassed());

        BatteryStatusWidgetProvider.Companion.updateAllWidgets(context);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        drainMonitorEnabled = sharedPreferences.getBoolean(SettingsActivity.KEY_PREF_DRAIN_MONITOR, false);

        BatteryWorker.fetchUpdates(context);
        BatteryWorker.updateStats(context, isCharging());

        if (Objects.equals(intent.getAction(), Intent.ACTION_POWER_CONNECTED)) {
            if (BatteryWorker.disableSync && !ContentResolver.getMasterSyncAutomatically()) {
                ContentResolver.setMasterSyncAutomatically(true);
            }

            BatteryService.setBypassMode(BatteryService.BypassMode.AUTO);

            BatteryService.startBackgroundTask(context);

            if (drainMonitorEnabled) {
                DrainMonitor.resetStats(context);
            }
        } else if (Objects.equals(intent.getAction(), Intent.ACTION_POWER_DISCONNECTED)) {
            if (drainMonitorEnabled) {
                DrainMonitor.resetStats(context);
            }

            if (BatteryWorker.disableSync && ContentResolver.getMasterSyncAutomatically()) {
                ContentResolver.setMasterSyncAutomatically(false);
            }

            if (BatteryWorker.autoReset) {
                if (statsFile.exists()) {
                    ShellUtils.fastCmd("rm " + statsFile);
                }
            }

            if (BatteryService.isBypassed == 1)
                BatteryWorker.setBypass(context, 0);

            BatteryService.setBypassMode(BatteryService.BypassMode.AUTO);

            if (!MainActivity.isRunning && !drainMonitorEnabled) {
                BatteryService.stopBackgroundTask();
            }
        }

        if (MainActivity.isRunning) {
            MainActivity.updateWaves(mLevel);
        }

        BatteryService.updateNotif(context);
    }

    private void updateStatusPref(Context context, int level, Boolean charging, Boolean idle) {
        SharedPreferences prefs = context.getSharedPreferences("battery_widget", Context.MODE_PRIVATE);
        prefs.edit()
                .putInt("battery_level", level)
                .putBoolean("charging", charging)
                .putBoolean("idle", idle)
                .commit();
    }
}