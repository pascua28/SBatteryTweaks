package com.sammy.chargerateautomator;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

public class MainActivity extends AppCompatActivity {

    BatteryWorker batteryWorker = new BatteryWorker();
    public static boolean isRunning;
    public static boolean isRootAvailable;
    private Button settingsButton;
    private TextView bypassText;

    public static ToggleButton bypassToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        settingsButton = findViewById(R.id.settingsBtn);

        isRunning = true;
        isRootAvailable = Utils.isRooted();

        String requiredPermission = "android.permission.WRITE_SECURE_SETTINGS";

        int permGranted = this.checkCallingOrSelfPermission(requiredPermission);

        try {
            this.registerReceiver(batteryWorker, new IntentFilter(Intent.ACTION_POWER_CONNECTED));
            this.registerReceiver(batteryWorker, new IntentFilter("com.sammy.chargerateautomator.notifier"));
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }

        if (!foregroundServiceRunning() && permGranted == 0) {
            Intent serviceIntent = new Intent(this,
                    BatteryService.class);
            startForegroundService(serviceIntent);
        }

        bypassText = findViewById(R.id.bypassText);
        batteryWorker.chargingState = findViewById(R.id.chargingText);
        batteryWorker.battTemp = findViewById(R.id.tempText);
        batteryWorker.fastChargeStatus = findViewById(R.id.fastCharge);

        bypassToggle = (ToggleButton) findViewById(R.id.bypassToggle);
        bypassToggle.setTag("BYPASS");
        if (isRootAvailable) {
            bypassText.setText("Bypass charging:");
        } else {
            bypassToggle.setVisibility(View.GONE);
        }

        bypassToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (bypassToggle.getTag() != null) {
                    bypassToggle.setTag(null);
                    return;
                }
                BatteryWorker.setBypass(isChecked);
            }
        });

        bypassToggle.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent even) {
                bypassToggle.setTag(null);
                return false;
            }
        });

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(settingsIntent);
            }
        });
        permissionDialog(permGranted);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isRunning = true;
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        isRunning = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        isRunning = false;
    }

    @Override
    protected void onStop() {
        super.onStop();
        isRunning = false;
    }

    private boolean foregroundServiceRunning(){
        ActivityManager activityManager = (ActivityManager) getSystemService(this.ACTIVITY_SERVICE);
        boolean b = activityManager.getRunningServices(Integer.MAX_VALUE).stream().anyMatch(service -> BatteryService.class.getName().equals(service.service.getClassName()));
        return b;
    }

    private void permissionDialog(int ret) {
        if (ret < 0) {
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
}