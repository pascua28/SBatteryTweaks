package com.sammy.chargerateautomator;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    BatteryReceiver battInfo = new BatteryReceiver();
    public static boolean isRunning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        isRunning = true;

        String requiredPermission = "android.permission.WRITE_SECURE_SETTINGS";

        int permGranted = this.checkCallingOrSelfPermission(requiredPermission);

        if (!foregroundServiceRunning() && permGranted == 0) {
            Intent serviceIntent = new Intent(this,
                    BatteryService.class);
            startForegroundService(serviceIntent);
        }

        battInfo.chargingState = findViewById(R.id.chargingText);
        battInfo.battTemp = findViewById(R.id.tempText);
        battInfo.fastChargeStatus = findViewById(R.id.fastCharge);

        if (permGranted < 0) {
            AlertDialog.Builder builder
                    = new AlertDialog
                    .Builder(MainActivity.this);
            builder.setMessage("WRITE_SECURE_SETTINGS not granted!\n\nTo grant access, run\n" +
                    "'adb shell pm grant com.sammy.chargerateautomator android.permission.WRITE_SECURE_SETTINGS'\n" +
                    "on your computer");
            builder.setCancelable(false);

            builder
                    .setPositiveButton(
                            "Exit",
                            new DialogInterface
                                    .OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which)
                                {
                                    isRunning = false;
                                    finish();
                                }
                            });
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isRunning = true;
        registerReceiver(battInfo,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        registerReceiver(battInfo,new IntentFilter("com.sammy.chargerateautomator.notifier"));
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        isRunning = true;
        registerReceiver(battInfo,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        registerReceiver(battInfo,new IntentFilter("com.sammy.chargerateautomator.notifier"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        isRunning = false;
        registerReceiver(battInfo,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        registerReceiver(battInfo,new IntentFilter("com.sammy.chargerateautomator.notifier"));
    }

    @Override
    protected void onStop() {
        super.onStop();
        isRunning = false;
    }

    public boolean foregroundServiceRunning(){
        ActivityManager activityManager = (ActivityManager) getSystemService(this.ACTIVITY_SERVICE);
        boolean b = activityManager.getRunningServices(Integer.MAX_VALUE).stream().anyMatch(service -> BatteryService.class.getName().equals(service.service.getClassName()));
        return b;
    }
}