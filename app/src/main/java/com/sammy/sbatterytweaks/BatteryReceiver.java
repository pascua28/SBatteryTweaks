package com.sammy.sbatterytweaks;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

import com.topjohnwu.superuser.ShellUtils;

import java.io.File;

public class BatteryReceiver extends BroadcastReceiver {
    public static int mLevel, mVolt;
    public static float mTemp;
    private static int mPlugged, mStatus;
    private final File statsFile = new File("/data/system/batterystats.bin");

    public static boolean isCharging() {
        return mPlugged > 0;
    }

    public static boolean notCharging() {
        return mPlugged > 0 && mStatus == BatteryManager.BATTERY_STATUS_NOT_CHARGING;
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

        if (intent.getAction().equals(Intent.ACTION_POWER_CONNECTED)) {
            if (BatteryWorker.disableSync && !ContentResolver.getMasterSyncAutomatically())
                ContentResolver.setMasterSyncAutomatically(true);

            BatteryService.startBackgroundTask();
        } else if (intent.getAction().equals(Intent.ACTION_POWER_DISCONNECTED)) {
            if (BatteryWorker.disableSync && ContentResolver.getMasterSyncAutomatically())
                ContentResolver.setMasterSyncAutomatically(false);

            if (BatteryWorker.autoReset) {
                if (statsFile.exists())
                    ShellUtils.fastCmd("rm " + statsFile);
            }

            if (!MainActivity.isRunning) {
                BatteryService.stopBackgroundTask();
            }
        }

        if (MainActivity.isRunning) {
            MainActivity.updateWaves(mLevel);
        }
        BatteryService.updateNotif(context.getString(R.string.temperature_title) + getTemp() + " °C");
    }

    private float getTemp() {
        return mTemp;
    }
}
