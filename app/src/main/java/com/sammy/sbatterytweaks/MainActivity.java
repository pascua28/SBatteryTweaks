package com.sammy.sbatterytweaks;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ShellUtils;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    public static boolean isRunning;
    private boolean isRootAvailable;
    private static TextView chargingStatus;
    private static TextView battTemperature;
    private static TextView fastChgStatus;
    private TextView headerText;
    private TextView ratedCapacity;


    private static ToggleButton bypassToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
            setTheme(R.style.Theme_ChargeRateAutomator_v31_NoActionBar);
        else
            setTheme(R.style.Theme_ChargeRateAutomator_NoActionBar);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        int actualCapacity = Utils.getActualCapacity(this);
        int fullcapnom;
        float battHealth;

        isRunning = true;
        isRootAvailable = Utils.isRooted();

        String requiredPermission = "android.permission.WRITE_SECURE_SETTINGS";

        int permGranted = this.checkCallingOrSelfPermission(requiredPermission);

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        Button settingsButton = findViewById(R.id.settingsBtn);

        Button donateButton = findViewById(R.id.supportBtn);

        if (!foregroundServiceRunning()) {
            Intent serviceIntent = new Intent(this,
                    BatteryService.class);
            startForegroundService(serviceIntent);
        }

        TextView bypassText = findViewById(R.id.bypassText);
        chargingStatus = findViewById(R.id.chargingText);
        battTemperature = findViewById(R.id.tempText);
        fastChgStatus = findViewById(R.id.fastCharge);
        headerText = findViewById(R.id.batteryHeader);
        ratedCapacity = findViewById(R.id.capacityText);

        if (Utils.isRooted()) {
            fullcapnom = Integer.parseInt(ShellUtils.fastCmd("cat /sys/class/power_supply/battery/fg_fullcapnom"));
            battHealth = ((float)fullcapnom / actualCapacity) * 100;
            if (fullcapnom != 0 && battHealth !=0)
                headerText.setText("Battery status (Health: " + String.format(Locale.ENGLISH, "%.2f", battHealth) + "%)");
            else headerText.setText("Battery status:");
        } else {
            headerText.setText("Battery status:");
        }

        if (actualCapacity != 0)
            ratedCapacity.setText("Rated capacity: " + actualCapacity + " mAh");

        bypassToggle = findViewById(R.id.bypassToggle);
        bypassText.setText("Bypass charging:");

        if (!BatteryWorker.bypassSupported) {
            bypassText.setAlpha(0.5f);
            bypassToggle.setEnabled(false);
        }

        bypassToggle.setOnCheckedChangeListener((compoundButton, isChecked) -> BatteryWorker.setBypass(isChecked));

        settingsButton.setOnClickListener(v -> {
            Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(settingsIntent);
        });

        donateButton.setOnClickListener(v -> {
            Intent openURL = new Intent(android.content.Intent.ACTION_VIEW);
            openURL.setData(Uri.parse("https://github.com/pascua28/SupportMe"));
            startActivity(openURL);
        });

        if (isRootAvailable && permGranted < 0) {
            Shell.cmd("pm grant com.sammy.sbatterytweaks android.permission.WRITE_SECURE_SETTINGS").exec();
            Toast.makeText(this, "Permission granted! Restarting…", Toast.LENGTH_SHORT).show();
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

    public static void updateStatus() {
            chargingStatus.setText(BatteryWorker.chargingState);
            battTemperature.setText(BatteryWorker.battTemp);
            fastChgStatus.setText(BatteryWorker.fastChargeStatus);
            bypassToggle.setChecked(BatteryWorker.isBypassed());
    }

    @SuppressWarnings("deprecation")
    private boolean foregroundServiceRunning(){
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        return activityManager.getRunningServices(Integer.MAX_VALUE).stream().anyMatch(service -> BatteryService.class.getName().equals(service.service.getClassName()));
    }

    private void permissionDialog(int ret) {
        if (ret < 0) {
            AlertDialog.Builder builder
                    = new AlertDialog
                    .Builder(MainActivity.this);
            builder.setMessage("WRITE_SECURE_SETTINGS not granted!\n\nTo grant access, run\n" +
                    "'adb shell pm grant com.sammy.sbatterytweaks android.permission.WRITE_SECURE_SETTINGS'\n" +
                    "on your computer");
            builder.setCancelable(false);

            builder
                    .setPositiveButton(
                            "Okay",
                            (dialog, which) -> Toast.makeText(getApplicationContext(), "App may not function correctly", Toast.LENGTH_LONG).show());
            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        }
    }
}