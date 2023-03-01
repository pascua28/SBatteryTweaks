package com.sammy.sbatterytweaks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

public class BatteryReceiver extends BroadcastReceiver {
    private int mLevel;
    private float mTemp;
    private int mStatus;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        mTemp = (float) intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        mLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        mStatus = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
    }

    public int getLevel() {
        return mLevel;
    }

    public float getTemp() {
        return mTemp;
    }

    public boolean isCharging() { return mStatus > 0; }
}
