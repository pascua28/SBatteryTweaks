package com.sammy.sbatterytweaks;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;

public class BatteryWorker {
    private static final String afcDisableFile = "/sys/class/sec/switch/afc_disable";
    public static String chargingState, battTemp, fastChargeStatus;
    public static boolean isOngoing, idleEnabled, disableSync,
            autoReset, bypassSupported, pausePdSupported, pausePdEnabled;
    public static float thresholdTemp, tempDelta;
    public static int battFullCap = 0, idleLevel;
    static SimpleDateFormat sdf;
    static Date currTime, start;
    private static boolean serviceEnabled, timerEnabled, shouldCoolDown, pauseMode,
            lvlSwitch, enableToast, isSchedEnabled;
    private static float cdSeconds;
    private static int fastChargeEnabled;
    private static int startHour;
    private static int startMinute;
    private static int lvlThreshold;
    private static int duration;
    private static long cooldown;

    public static void fetchUpdates(Context context) {
        try {
            fastChargeEnabled = Settings.System.getInt(context.getContentResolver(), "adaptive_fast_charging");
            if (fastChargeEnabled == 1) fastChargeStatus = context.getString(R.string.enabled);
            else fastChargeStatus = context.getString(R.string.disabled);
        } catch (Settings.SettingNotFoundException e) {
            if (Utils.isPrivileged() && Utils.runCmd("ls " + afcDisableFile).contains(afcDisableFile)) {
                int afcDisabled = Integer.parseInt(Utils.runCmd("cat " + afcDisableFile));
                if (afcDisabled == 0) {
                    fastChargeStatus = context.getString(R.string.enabled);
                    fastChargeEnabled = 1;
                } else if (afcDisabled == 1) {
                    fastChargeStatus = context.getString(R.string.disabled);
                    fastChargeEnabled = 0;
                }
            } else fastChargeStatus = context.getString(R.string.not_supported);
        }

        if (pausePdSupported)
            pausePdEnabled = Settings.System.getString(context.getContentResolver(), "pass_through").equals("1");

        SharedPreferences sharedPref =
                PreferenceManager.getDefaultSharedPreferences(context);
        serviceEnabled = sharedPref.getBoolean(SettingsActivity.KEY_PREF_SERVICE, true);
        thresholdTemp = sharedPref.getFloat(SettingsActivity.KEY_PREF_THRESHOLD_UP, 36.5F);
        tempDelta = sharedPref.getFloat(SettingsActivity.KEY_PREF_TEMP_DELTA, 0.5F);
        pauseMode = sharedPref.getBoolean(SettingsActivity.KEY_PREF_BYPASS_MODE, false);
        timerEnabled = sharedPref.getBoolean(SettingsActivity.KEY_PREF_TIMER_SWITCH, false);
        cdSeconds = sharedPref.getFloat(SettingsActivity.KEY_PREF_CD_SECONDS, 30F);
        isSchedEnabled = sharedPref.getBoolean(SettingsActivity.PREF_SCHED_ENABLED, false);
        idleEnabled = sharedPref.getBoolean(SettingsActivity.PREF_IDLE_SWITCH, false);
        idleLevel = sharedPref.getInt(SettingsActivity.PREF_IDLE_LEVEL, 75);
        lvlSwitch = sharedPref.getBoolean(SettingsActivity.PREF_BATT_LVL_SWITCH, false);
        lvlThreshold = sharedPref.getInt(SettingsActivity.PREF_BATT_LVL_THRESHOLD, 60);
        disableSync = sharedPref.getBoolean(SettingsActivity.PREF_DISABLE_SYNC, false);
        autoReset = sharedPref.getBoolean(SettingsActivity.PREF_RESET_STATS, false);
        enableToast = sharedPref.getBoolean(SettingsActivity.PREF_TOAST_NOTIF, false);

        SharedPreferences timePref = context.getSharedPreferences("timePref", Context.MODE_PRIVATE);
        startHour = timePref.getInt(TimePicker.PREF_START_HOUR, 22);
        startMinute = timePref.getInt(TimePicker.PREF_START_MINUTE, 0);
        duration = timePref.getInt(TimePicker.PREF_DURATION, 480);

        if (bypassSupported)
            battFullCap = Integer.parseInt(Utils.runCmd("cat " + BatteryService.fullCapFIle));
    }

    public static void batteryWorker(Context context, Boolean isCharging) {
        if (isCharging)
            battWorker(context.getApplicationContext());
    }

    private static int getDefaultBypass(Context context) {
        int protectEnabled;
        SharedPreferences sharedPreferences = context.getSharedPreferences("PROTECT_SETTING", Context.MODE_PRIVATE);
        protectEnabled = sharedPreferences.getInt("PROTECT_ENABLED", -1);

        if (protectEnabled == 1) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                return 85;
            } else return 80;
        } else return 100;
    }

    public static void setBypass(int level) {
        Utils.runCmd("echo " + level + " > " + BatteryService.fullCapFIle);
    }

    public static void setBypass(Context context, int bypass) {
        if (pausePdSupported) {
            if (bypass == 1) {
                enableFastCharge(context, 1);

                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            Utils.changeSetting(context, Utils.Namespace.SYSTEM, "pass_through", bypass);
            return;
        }

        if (bypass == 1) {
            enableFastCharge(context, 1);
            setBypass(BatteryReceiver.mLevel);
        } else {
            setBypass(getDefaultBypass(context));
        }
    }

    @SuppressLint("SimpleDateFormat")
    private static boolean isLazyTime() {
        String start_time = String.format(LocalDate.now().toString() + "-%02d:%02d", startHour, startMinute);
        sdf = new SimpleDateFormat("yyyy-MM-dd-hh:mm");
        currTime = Calendar.getInstance().getTime();
        start = currTime;

        try {
            start = sdf.parse(start_time);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        long currentTimeMillis = currTime.getTime();
        long startMillis = start.getTime();
        long endMillis = startMillis + ((long) duration * 60 * 1000);

        return (currentTimeMillis > startMillis) && (currentTimeMillis < endMillis);
    }

    private static void startTimer() {
        cooldown = (long) cdSeconds * 1000 * 2;
        new CountDownTimer(cooldown, 1000) {
            public void onTick(long millisUntilFinished) {
                isOngoing = true;

                shouldCoolDown = millisUntilFinished > (cooldown / 2);
            }

            public void onFinish() {
                isOngoing = false;
            }
        }.start();
    }

    private static void enableFastCharge(Context context, int enabled) {
        try {
            String adaptiveFast =
                    Settings.System.getString(context.getContentResolver(), "adaptive_fast_charging");

            if (adaptiveFast == null)
                throw new Settings.SettingNotFoundException("Not found");

            if (Utils.changeSetting(context, Utils.Namespace.SYSTEM, "adaptive_fast_charging", enabled) < 0) {
                fastChargeStatus = fastChargeStatus + " (" + context.getString(R.string.toggle_failed) + ")";
            }
        } catch (Settings.SettingNotFoundException ignored) {
            if (Utils.isPrivileged() && Utils.runCmd("ls " + afcDisableFile).contains(afcDisableFile)) {
                Utils.runCmd("echo " + (enabled == 1 ? 0 : 1) + " > " + afcDisableFile);
            }
        }
    }

    private static boolean shouldStopFastCharge() {
        if (pauseMode)
            return false;

        return (lvlSwitch && BatteryReceiver.mLevel >= lvlThreshold) ||
                (isSchedEnabled && isLazyTime());
    }

    private static boolean shouldStartFastCharge() {
        if (pauseMode)
            return false;

        return BatteryReceiver.mTemp <= (thresholdTemp - tempDelta) ||
                (isOngoing && !shouldCoolDown);
    }

    private static void stopFastCharge(Context context) {
        if (fastChargeEnabled == 1 && !BatteryService.isBypassed()) {
            enableFastCharge(context, 0);
        }
    }

    private static void resumeFastCharge(Context context) {
        if (pauseMode && BatteryService.isBypassed()) {
            setBypass(context, 0);
            toast(context, R.string.resumed);
        } else if (fastChargeEnabled == 0) {
            enableFastCharge(context, 1);
            toast(context, R.string.re_enabled);
        }
    }

    private static void handleOverheat(Context context) {
        if (timerEnabled && !isOngoing) startTimer();

        if (pauseMode && !BatteryService.isBypassed()) {
            setBypass(context, 1);
            toast(context, R.string.paused);
        } else if (!pauseMode && fastChargeEnabled == 1 && !BatteryService.isBypassed()) {
            enableFastCharge(context, 0);
            toast(context, R.string.fast_charging_disabled);
        }
    }

    private static void toast(Context context, int resId) {
        if (enableToast) {
            Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show();
        }
    }

    private static void battWorker(Context context) {
        if (!serviceEnabled) return;

        if (shouldStopFastCharge()) {
            stopFastCharge(context);
            return;
        }

        if (shouldStartFastCharge()) {
            resumeFastCharge(context);
            return;
        }

        if (BatteryReceiver.mTemp >= thresholdTemp) {
            handleOverheat(context);
        }
    }

    public static void updateStats(Context context, Boolean isCharging) {
        battTemp = BatteryReceiver.mTemp + "°C";

        if (BatteryService.isBypassed())
            chargingState = context.getString(R.string.idle);
        else if (isCharging)
            chargingState = context.getString(R.string.charging);
        else
            chargingState = context.getString(R.string.discharging);
    }

}
