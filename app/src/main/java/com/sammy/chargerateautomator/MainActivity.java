package com.sammy.chargerateautomator;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.topjohnwu.superuser.Shell;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    BatteryWorker batteryWorker = new BatteryWorker();
    public static boolean isRunning;
    public static boolean isRootAvailable;
    private TextView chargingStatus;
    private TextView battTemperature;
    private TextView fastChgStatus;

    private ToggleButton bypassToggle;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        Button settingsButton = findViewById(R.id.settingsBtn);

        if (!foregroundServiceRunning()) {
            Intent serviceIntent = new Intent(this,
                    BatteryService.class);
            startForegroundService(serviceIntent);
        }

        TextView bypassText = findViewById(R.id.bypassText);
        chargingStatus = findViewById(R.id.chargingText);
        battTemperature = findViewById(R.id.tempText);
        fastChgStatus = findViewById(R.id.fastCharge);

        bypassToggle = (ToggleButton) findViewById(R.id.bypassToggle);
        bypassText.setText("Bypass charging:");

        if (!isRootAvailable) {
            bypassText.setAlpha(0.5f);
            bypassToggle.setEnabled(false);
        }

        bypassToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                BatteryWorker.setBypass(isChecked);
            }
        });

        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(settingsIntent);
            }
        });

        if (isRootAvailable && permGranted < 0) {
            Shell.cmd("pm grant com.sammy.chargerateautomator android.permission.WRITE_SECURE_SETTINGS").exec();
            Toast.makeText(this, "Permission granted! Restarting....", Toast.LENGTH_SHORT).show();
            new Timer().schedule(
                    new TimerTask() {
                        @Override
                        public void run() {
                            finish();
                            startActivity(getIntent());
                        }
                    }, 500);
        }
        permissionDialog(permGranted);

        mHandler = new Handler();
        mHandler.post(statusUpdate);
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

    private final Runnable statusUpdate = new Runnable() {
        @Override
        public void run() {
            chargingStatus.setText(BatteryWorker.chargingState);
            battTemperature.setText(BatteryWorker.battTemp);
            fastChgStatus.setText(BatteryWorker.fastChargeStatus);
            bypassToggle.setChecked(BatteryWorker.isBypassed());
            mHandler.postDelayed(this, 100);
        }
    };

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
                            "Okay",
                            new DialogInterface
                                    .OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog,
                                                    int which)
                                {
                                    Toast.makeText(getApplicationContext(), "App may not function correctly", Toast.LENGTH_LONG).show();
                                }
                            });
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        }
    }
}