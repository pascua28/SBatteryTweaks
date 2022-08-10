package com.sammy.chargerateautomator;

import android.app.ActivityManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings;


import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    BattInfo battInfo = new BattInfo();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!foregroundServiceRunning()) {
            Intent serviceIntent = new Intent(this,
                    BatteryService.class);
            startForegroundService(serviceIntent);
        }

        boolean writePerm = Settings.System.canWrite(this);

        battInfo.chargingState = findViewById(R.id.chargingText);
        battInfo.battTemp = findViewById(R.id.tempText);
        battInfo.fastChargeStatus = findViewById(R.id.fastCharge);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(battInfo,new IntentFilter("com.sammy.chargerateautomator.notifier"));
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        registerReceiver(battInfo,new IntentFilter("com.sammy.chargerateautomator.notifier"));
    }

    @Override
    protected void onPause() {
        super.onPause();
        registerReceiver(battInfo,new IntentFilter("com.sammy.chargerateautomator.notifier"));
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    public boolean foregroundServiceRunning(){
        ActivityManager activityManager = (ActivityManager) getSystemService(this.ACTIVITY_SERVICE);
        boolean b = activityManager.getRunningServices(Integer.MAX_VALUE).stream().anyMatch(service -> BatteryService.class.getName().equals(service.service.getClassName()));
        return b;
    }
}