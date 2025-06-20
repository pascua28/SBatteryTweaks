package com.sammy.sbatterytweaks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Objects;

public class ScreenReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        switch (Objects.requireNonNull(intent.getAction())) {
            case Intent.ACTION_SCREEN_ON:
                DrainMonitor.handleBatteryChange(BatteryReceiver.divisor, BatteryReceiver.getCounter(context), BatteryReceiver.isCharging());
                DrainMonitor.handleScreenChange(true);
                break;
            case Intent.ACTION_SCREEN_OFF:
                DrainMonitor.handleBatteryChange(BatteryReceiver.divisor, BatteryReceiver.getCounter(context), BatteryReceiver.isCharging());
                DrainMonitor.handleScreenChange(false);
                break;
        }
    }
}
