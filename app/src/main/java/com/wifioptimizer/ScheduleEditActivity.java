package com.wifioptimizer;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.List;

public class ScheduleEditActivity extends AppCompatActivity {

    private TextView tvStart, tvEnd;
    private MaterialButton btnSave, btnDelete;
    
    private int startH = 8, startM = 0;
    private int endH = 17, endM = 0;
    
    private String scheduleId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule_edit);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tvStart = findViewById(R.id.tvStart);
        tvEnd   = findViewById(R.id.tvEnd);
        btnSave = findViewById(R.id.btnSaveSchedule);
        btnDelete = findViewById(R.id.btnDeleteSchedule);

        scheduleId = getIntent().getStringExtra("schedule_id");
        if (scheduleId != null) {
            loadExistingSchedule();
            btnDelete.setVisibility(View.VISIBLE);
        } else {
            updateUI();
            btnDelete.setVisibility(View.GONE);
        }

        findViewById(R.id.cardStart).setOnClickListener(v -> pickTime(true));
        findViewById(R.id.cardEnd).setOnClickListener(v -> pickTime(false));
        
        btnSave.setOnClickListener(v -> saveSchedule());
        btnDelete.setOnClickListener(v -> deleteSchedule());
    }

    private void loadExistingSchedule() {
        List<Schedule> schedules = PrefsManager.getInstance().getSchedules(this);
        for (Schedule s : schedules) {
            if (s.getId().equals(scheduleId)) {
                startH = s.getStartHour();
                startM = s.getStartMinute();
                endH = s.getEndHour();
                endM = s.getEndMinute();
                break;
            }
        }
        updateUI();
    }

    private void pickTime(boolean isStart) {
        int h = isStart ? startH : endH;
        int m = isStart ? startM : endM;

        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            if (isStart) {
                startH = hourOfDay;
                startM = minute;
            } else {
                endH = hourOfDay;
                endM = minute;
            }
            updateUI();
        }, h, m, false).show();
    }

    private void updateUI() {
        PrefsManager p = PrefsManager.getInstance();
        tvStart.setText(p.formatTime(startH, startM));
        tvEnd.setText(p.formatTime(endH, endM));
    }

    private void saveSchedule() {
        if (startH == endH && startM == endM) {
            Toast.makeText(this, "Start and end times cannot be identical.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Schedule> schedules = PrefsManager.getInstance().getSchedules(this);
        
        if (scheduleId == null) {
            // New Schedule
            Schedule s = new Schedule(startH, startM, endH, endM);
            schedules.add(s);
        } else {
            // Update existing
            for (Schedule s : schedules) {
                if (s.getId().equals(scheduleId)) {
                    s.setStartHour(startH);
                    s.setStartMinute(startM);
                    s.setEndHour(endH);
                    s.setEndMinute(endM);
                    break;
                }
            }
        }

        PrefsManager.getInstance().saveSchedules(this, schedules);
        ScheduleManager.scheduleAll(this);
        ScheduleManager.syncVpnState(this);
        Toast.makeText(this, "Schedule saved", Toast.LENGTH_SHORT).show();
        finish();
    }
    
    private void deleteSchedule() {
        if (scheduleId == null) return;
        List<Schedule> schedules = PrefsManager.getInstance().getSchedules(this);
        for (Schedule s : schedules) {
            if (s.getId().equals(scheduleId)) {
                ScheduleManager.cancelSchedule(this, s);
                break;
            }
        }
        schedules.removeIf(s -> s.getId().equals(scheduleId));
        PrefsManager.getInstance().saveSchedules(this, schedules);
        ScheduleManager.scheduleAll(this);
        ScheduleManager.syncVpnState(this);
        Toast.makeText(this, "Schedule deleted", Toast.LENGTH_SHORT).show();
        finish();
    }
}
