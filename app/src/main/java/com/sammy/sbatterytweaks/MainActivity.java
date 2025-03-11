package com.sammy.sbatterytweaks;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.scwang.wave.MultiWaveHeader;

import java.util.Locale;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {

    public static boolean isRunning;
    static MultiWaveHeader multiWaveHeader;
    private static SwitchCompat bypassToggle;
    private final Handler handler = new Handler();
    private final Shizuku.OnRequestPermissionResultListener REQUEST_PERMISSION_RESULT_LISTENER = this::onRequestPermissionsResult;
    private TextView chargingStatus;
    private TextView levelText;
    private TextView currentText;
    private TextView voltText;
    private TextView battTemperature;
    private TextView fastChgStatus;
    private TextView bypassText;
    private AppCompatImageButton settingsButton;
    private Animation rotateAnimation;
    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            BatteryManager manager = (BatteryManager) getApplicationContext().getSystemService(BATTERY_SERVICE);
            int currentNow = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            settingsButton.startAnimation(rotateAnimation);
            String lvlText, battPercent;
            battPercent = BatteryReceiver.mLevel + "%";
            lvlText = battPercent;
            chargingStatus.setText(BatteryWorker.chargingState);
            if (BatteryReceiver.isCharging())
                lvlText = "⚡" + battPercent;

            levelText.setText(lvlText);
            currentText.setText(String.format(Locale.getDefault(), "%d mA", currentNow));
            voltText.setText(String.format(Locale.getDefault(), "%d mV", BatteryReceiver.mVolt));
            battTemperature.setText(BatteryWorker.battTemp);
            if (BatteryReceiver.mTemp >= BatteryWorker.thresholdTemp)
                battTemperature.setTextColor(Color.parseColor("#A80505"));
            else if (BatteryReceiver.mTemp > (BatteryWorker.thresholdTemp - BatteryWorker.tempDelta))
                battTemperature.setTextColor(Color.parseColor("#CCBC2F"));
            else
                battTemperature.setTextColor(Color.parseColor("#187A4A"));

            fastChgStatus.setText(BatteryWorker.fastChargeStatus);
            bypassToggle.setChecked(BatteryService.isBypassed());
            handler.postDelayed(this, BatteryService.refreshInterval);
        }
    };

    public static void updateWaves(int percentage) {
        multiWaveHeader.setProgress(percentage / 100.0f);
    }

    public static void forceStop(Context context) {
        String packageName = context.getPackageName();
        Toast.makeText(context, context.getString(R.string.restart_granted), Toast.LENGTH_SHORT).show();
        Utils.runCmd("am force-stop " + packageName);
    }

    private void updateUI(Boolean shouldUpdate) {
        if (shouldUpdate) {
            handler.post(runnable);
        } else handler.removeCallbacksAndMessages(null);
    }

    private void onRequestPermissionsResult(int requestCode, int grantResult) {
        if (grantResult == PackageManager.PERMISSION_GRANTED)
            forceStop(this);
    }

    @SuppressLint("BatteryLife")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);

        if (!foregroundServiceRunning()) {
            Intent serviceIntent = new Intent(this,
                    BatteryService.class);
            startForegroundService(serviceIntent);
            finish();
            startActivity(getIntent());
        }

        PowerManager powerManager = (PowerManager) this.getSystemService(POWER_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            setTheme(R.style.Theme_ChargeRateAutomator_v31_NoActionBar);
        else
            setTheme(R.style.Theme_ChargeRateAutomator_NoActionBar);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        int actualCapacity = Utils.getActualCapacity(this);
        int fullcapnom = -1;
        float battHealth;

        CardView idleCard = findViewById(R.id.idleCardView);
        CardView capacityCard = findViewById(R.id.capacityView);
        idleCard.setVisibility(View.GONE);

        isRunning = true;

        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        settingsButton = findViewById(R.id.settingsBtn);
        rotateAnimation = AnimationUtils.loadAnimation(this, R.anim.spin);

        AppCompatImageButton donateButton = findViewById(R.id.supportBtn);

        bypassText = findViewById(R.id.bypassText);
        chargingStatus = findViewById(R.id.chargingText);
        levelText = findViewById(R.id.levelText);
        currentText = findViewById(R.id.currentText);
        voltText = findViewById(R.id.voltageText);
        TextView remainingCap = findViewById(R.id.remainingCap);

        battTemperature = findViewById(R.id.tempText);
        fastChgStatus = findViewById(R.id.fastCharge);
        TextView ratedCapacity = findViewById(R.id.capacityText);

        // Set initial text for some entries
        chargingStatus.setText(R.string.loading);
        fastChgStatus.setText(R.string.loading);
        currentText.setText(R.string.loading);
        voltText.setText(R.string.loading);
        levelText.setText(R.string.dots);
        battTemperature.setText(R.string.dots);

        updateUI(true);

        if (!Utils.isRooted()) {
            Utils.shizukuCheckPermission();
        }

        if (Utils.isPrivileged()) {
            fullcapnom = Integer.parseInt(Utils.runCmd("cat /sys/class/power_supply/battery/fg_fullcapnom"));
            battHealth = ((float) fullcapnom / actualCapacity) * 100;
            if (fullcapnom != 0 && battHealth != 0)
                remainingCap.setText(String.format(Locale.getDefault(), "%.2f%%", battHealth));
            else capacityCard.setVisibility(View.GONE);
        } else {
            capacityCard.setVisibility(View.GONE);
        }

        if (actualCapacity != 0)
            ratedCapacity.setText(String.format(Locale.getDefault(), "%d mAh", actualCapacity));

        bypassToggle = findViewById(R.id.bypassToggle);
        bypassText.setText(R.string.idle_charging_text);
        multiWaveHeader = findViewById(R.id.waveHeader);
        updateWaves(BatteryReceiver.mLevel);

        if (BatteryWorker.bypassSupported || BatteryWorker.pausePdSupported) {
            idleCard.setVisibility(View.VISIBLE);
        }
        bypassToggle.setOnClickListener(v -> BatteryWorker.setBypass(getApplicationContext(), bypassToggle.isChecked() ? 1 : 0));

        settingsButton.setOnClickListener(v -> {
            Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(settingsIntent);
        });

        donateButton.setOnClickListener(v -> {
            Intent openURL = new Intent(Intent.ACTION_VIEW);
            openURL.setData(Uri.parse("https://buymeacoffee.com/pascua14"));
            startActivity(openURL);
        });

        if (!Settings.System.canWrite(this)) {
            Intent permissionIntent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            Uri permissionUri = Uri.fromParts("package", getPackageName(), null);
            permissionIntent.setData(permissionUri);
            startActivity(permissionIntent);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_DENIED) {
                ActivityCompat.requestPermissions(this, new String[]
                        {
                                Manifest.permission.POST_NOTIFICATIONS
                        }, 1);
            }
        }

        if (!powerManager.isIgnoringBatteryOptimizations(getPackageName()))
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
    }

    @Override
    protected void onResume() {
        super.onResume();
        MultiWaveHeader multiWaveHeader = findViewById(R.id.waveHeader);
        multiWaveHeader.setProgress(BatteryReceiver.mLevel / 100.0f);
        isRunning = true;
        BatteryService.startBackgroundTask();
        updateUI(true);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        MultiWaveHeader multiWaveHeader = findViewById(R.id.waveHeader);
        multiWaveHeader.setProgress(BatteryReceiver.mLevel / 100.0f);
        isRunning = true;
        BatteryService.startBackgroundTask();
        updateUI(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isRunning = false;
        if (!BatteryReceiver.isCharging())
            BatteryService.stopBackgroundTask();
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
        updateUI(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        isRunning = false;
        if (!BatteryReceiver.isCharging())
            BatteryService.stopBackgroundTask();
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
        updateUI(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (!BatteryReceiver.isCharging())
            BatteryService.stopBackgroundTask();
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER);
        updateUI(false);
    }

    @SuppressWarnings("deprecation")
    private boolean foregroundServiceRunning() {
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        return activityManager.getRunningServices(Integer.MAX_VALUE).stream().anyMatch(service -> BatteryService.class.getName().equals(service.service.getClassName()));
    }
}