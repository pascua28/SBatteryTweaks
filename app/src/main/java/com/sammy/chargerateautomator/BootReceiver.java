package com.sammy.chargerateautomator;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class BootReceiver extends BroadcastReceiver {

    BatteryWorker batteryWorker = new BatteryWorker();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            Intent bootIntent = new Intent(context, BatteryService.class);

            context.getApplicationContext().registerReceiver(batteryWorker, new IntentFilter(Intent.ACTION_POWER_CONNECTED));
            context.getApplicationContext().registerReceiver(batteryWorker, new IntentFilter("com.sammy.chargerateautomator.notifier"));
            context.startForegroundService(bootIntent);
        }
    }
}
