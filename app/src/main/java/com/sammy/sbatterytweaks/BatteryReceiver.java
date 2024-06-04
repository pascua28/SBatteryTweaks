package com.sammy.sbatterytweaks;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

import com.topjohnwu.superuser.ShellUtils;

import java.io.File;

public class BatteryReceiver extends BroadcastReceiver {
    private final File statsFile = new File("/data/system/batterystats.bin");
    private int mLevel, mStatus, mVolt;
    private float mTemp;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        mTemp = (float) intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        mLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        mStatus = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        mVolt = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);

        if (intent.getAction().equals(Intent.ACTION_POWER_CONNECTED)) {
            if (BatteryWorker.disableSync && !ContentResolver.getMasterSyncAutomatically())
                ContentResolver.setMasterSyncAutomatically(true);
        } else if (intent.getAction().equals(Intent.ACTION_POWER_DISCONNECTED)) {
            if (BatteryWorker.disableSync && ContentResolver.getMasterSyncAutomatically())
                ContentResolver.setMasterSyncAutomatically(false);

            if (BatteryWorker.autoReset) {
                if (statsFile.exists())
                    ShellUtils.fastCmd("rm " + statsFile);
            }

        }
    }

    public int getLevel() {
        return mLevel;
    }

    public float getTemp() {
        return mTemp;
    }

    public int getVolt() {
        return mVolt;
    }

    public boolean isCharging() {
        return mStatus > 0;
    }
}
