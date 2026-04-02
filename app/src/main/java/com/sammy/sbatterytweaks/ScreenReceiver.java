package com.sammy.sbatterytweaks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.Objects;

public class ScreenReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!BatteryReceiver.isCharging() && !BatteryService.isBypassed()) {
            switch (Objects.requireNonNull(intent.getAction())) {
                case Intent.ACTION_SCREEN_ON:
                    DrainMonitor.handleScreenChange(true);
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    DrainMonitor.handleScreenChange(false);
                    break;
            }
        }
    }
}
