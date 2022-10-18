package com.sammy.chargerateautomator;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

public class SettingsActivity extends AppCompatActivity {

    public static final String
            KEY_PREF_SERVICE = "enableservice";
    public static final String
            KEY_PREF_THRESHOLD_UP = "thresholdUp";
    public static final String
            KEY_PREF_TEMP_DELTA = "tempDelta";
    public static final String
            KEY_PREF_BYPASS_MODE = "pauseMode";
    public static final String
            KEY_PREF_TIMER_SWITCH = "timerSwitch";
    public static final String
            KEY_PREF_CD_SECONDS = "cdSeconds";
    public static final String
            PREF_SCHED_ENABLED = "schedSwitch";
    public static final String
            PREF_SCHED_IDLE = "schedIdle";
    public static final String
            PREF_SCHED_IDLE_LEVEL = "schedIdleLevel";
    public static final String
            PREF_DISABLE_SYNC = "disablesync";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
            setTheme(R.style.Theme_ChargeRateAutomator_v31);
        else
            setTheme(R.style.Theme_ChargeRateAutomator);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        SharedPreferences.OnSharedPreferenceChangeListener listener;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);
            SwitchPreferenceCompat pauseModeSwitch = findPreference("pauseMode");
            SwitchPreferenceCompat schedIdle = findPreference(PREF_SCHED_IDLE);

            if (!Utils.isRooted()) {
                if (pauseModeSwitch != null) {
                    pauseModeSwitch.setEnabled(false);
                }
                if (schedIdle != null) {
                    schedIdle.setEnabled(false);
                }
            }

            Preference timePreference = findPreference("timePickerBtn");
            timePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference p) {
                    TimePicker picker = new TimePicker(getActivity());
                    picker.show();
                    return true;
                }
            });
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            listener = (sharedPreferences, key) -> {
                if (key.equals(KEY_PREF_TIMER_SWITCH) && !sharedPreferences.getBoolean(KEY_PREF_TIMER_SWITCH, false)) {
                    BatteryWorker.isOngoing = false;
                }
            };
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(listener);
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(listener);
        }
    }
}