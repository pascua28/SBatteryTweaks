package com.sammy.sbatterytweaks;

import static com.sammy.sbatterytweaks.R.id.picker;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;

import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import nl.joery.timerangepicker.TimeRangePicker;

public class TimePicker extends Dialog implements View.OnClickListener {
    public static final String
        PREF_START_HOUR = "startHour";
    public static final String
            PREF_START_MINUTE = "startMinute";
    public final String
            PREF_END_HOUR = "endHour";
    public final String
            PREF_END_MINUTE = "endMinute";
    public static final String
            PREF_DURATION = "duration";

    public Activity c;
    public Dialog d;
    public Button saveBtn;
    private int startHour, startMinute, endHour, endMinute, duration;
    private TimeRangePicker timeRangePicker;
    private SharedPreferences pref;
    private SharedPreferences.Editor editor;

    public TimePicker(Activity a) {
        super(a);
        this.c = a;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.fragment_time_picker);
        saveBtn = (Button) findViewById(R.id.saveBtn);
        saveBtn.setOnClickListener(this);

        TextView start_Time = findViewById(R.id.start_time);
        TextView end_Time = findViewById(R.id.end_time);

        pref = getContext().getSharedPreferences("timePref", Context.MODE_PRIVATE);
        editor = pref.edit();

        startHour = pref.getInt(PREF_START_HOUR, 22);
        startMinute = pref.getInt(PREF_START_MINUTE, 0);
        endHour = pref.getInt(PREF_END_HOUR, 6);
        endMinute = pref.getInt(PREF_END_MINUTE, 0);
        duration = pref.getInt(PREF_DURATION, 480);

        timeRangePicker = findViewById(picker);

        timeRangePicker.setStartTime(new TimeRangePicker.Time(startHour, startMinute));
        timeRangePicker.setEndTimeMinutes((endHour * 60) + endMinute);

        start_Time.setText("Start: " + timeRangePicker.getStartTime().toString());
        end_Time.setText("End: " + timeRangePicker.getEndTime().toString());

        timeRangePicker.setOnTimeChangeListener(new TimeRangePicker.OnTimeChangeListener() {
            @Override
            public void onStartTimeChange(@NonNull TimeRangePicker.Time time) {
                startHour = time.getHour();
                startMinute = time.getMinute();
                start_Time.setText("Start: " + time.toString());

            }

            @Override
            public void onEndTimeChange(@NonNull TimeRangePicker.Time time) {
                endHour = time.getHour();
                endMinute = time.getMinute();
                end_Time.setText("End: " + time.toString());
            }

            @Override
            public void onDurationChange(@NonNull TimeRangePicker.TimeDuration timeDuration) {
                duration = timeDuration.getDurationMinutes();
            }
        });

        timeRangePicker.setOnDragChangeListener(new TimeRangePicker.OnDragChangeListener() {
            @Override
            public boolean onDragStart(@NonNull TimeRangePicker.Thumb thumb) {
                if (thumb != TimeRangePicker.Thumb.BOTH) {
                    //animate
                }
                return true;
            }

            @Override
            public void onDragStop(@NonNull TimeRangePicker.Thumb thumb) {

            }
        });


    }

    private void updateTimes() {
        editor.putInt(PREF_START_HOUR, startHour);
        editor.putInt(PREF_START_MINUTE, startMinute);
        editor.putInt(PREF_END_HOUR, endHour);
        editor.putInt(PREF_END_MINUTE, endMinute);
        editor.putInt(PREF_DURATION, duration);
        editor.commit();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.saveBtn:
                updateTimes();
                dismiss();
                break;
            default:
                break;
        }
        dismiss();
    }
}