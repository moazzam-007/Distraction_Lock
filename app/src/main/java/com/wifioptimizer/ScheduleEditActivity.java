package com.wifioptimizer;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

/**
 * ScheduleEditActivity — Allows the user to configure both block time windows.
 * Uses TimePickerDialog (Android's built-in time picker) for each time field.
 * Changes are saved to PrefsManager and alarms are rescheduled on Save.
 */
public class ScheduleEditActivity extends AppCompatActivity {

    // Slot 1 current values (shown + editable)
    private int s1StartH, s1StartM, s1EndH, s1EndM;
    // Slot 2 current values
    private int s2StartH, s2StartM, s2EndH, s2EndM;

    // TextViews that display the current time values
    private TextView tvS1Start, tvS1End, tvS2Start, tvS2End;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule_edit);

        setupToolbar();
        loadCurrentValues();
        initViews();
    }

    // ─── Setup ─────────────────────────────────────────────────────────────────

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Edit Schedule");
        }
    }

    /** Load saved times from PrefsManager as starting values. */
    private void loadCurrentValues() {
        PrefsManager p = PrefsManager.getInstance();
        s1StartH = p.getS1StartH(this); s1StartM = p.getS1StartM(this);
        s1EndH   = p.getS1EndH(this);   s1EndM   = p.getS1EndM(this);
        s2StartH = p.getS2StartH(this); s2StartM = p.getS2StartM(this);
        s2EndH   = p.getS2EndH(this);   s2EndM   = p.getS2EndM(this);
    }

    private void initViews() {
        tvS1Start = findViewById(R.id.tvS1Start);
        tvS1End   = findViewById(R.id.tvS1End);
        tvS2Start = findViewById(R.id.tvS2Start);
        tvS2End   = findViewById(R.id.tvS2End);

        refreshTimeLabels();

        // Slot 1 — tap start time
        MaterialCardView cardS1Start = findViewById(R.id.cardS1Start);
        cardS1Start.setOnClickListener(v ->
                showTimePicker(s1StartH, s1StartM, (hour, minute) -> {
                    s1StartH = hour; s1StartM = minute;
                    refreshTimeLabels();
                })
        );

        // Slot 1 — tap end time
        MaterialCardView cardS1End = findViewById(R.id.cardS1End);
        cardS1End.setOnClickListener(v ->
                showTimePicker(s1EndH, s1EndM, (hour, minute) -> {
                    s1EndH = hour; s1EndM = minute;
                    refreshTimeLabels();
                })
        );

        // Slot 2 — tap start time
        MaterialCardView cardS2Start = findViewById(R.id.cardS2Start);
        cardS2Start.setOnClickListener(v ->
                showTimePicker(s2StartH, s2StartM, (hour, minute) -> {
                    s2StartH = hour; s2StartM = minute;
                    refreshTimeLabels();
                })
        );

        // Slot 2 — tap end time
        MaterialCardView cardS2End = findViewById(R.id.cardS2End);
        cardS2End.setOnClickListener(v ->
                showTimePicker(s2EndH, s2EndM, (hour, minute) -> {
                    s2EndH = hour; s2EndM = minute;
                    refreshTimeLabels();
                })
        );

        // Save button
        MaterialButton btnSave = findViewById(R.id.btnSaveSchedule);
        btnSave.setOnClickListener(v -> saveAndFinish());
    }

    // ─── Time Picker ───────────────────────────────────────────────────────────

    /** Functional interface for time picker callback (Java 8+). */
    private interface TimePickedCallback {
        void onTimePicked(int hour, int minute);
    }

    /** Shows Android's built-in TimePickerDialog (24h format). */
    private void showTimePicker(int currentHour, int currentMinute, TimePickedCallback callback) {
        new TimePickerDialog(this,
                (view, hourOfDay, minute) -> callback.onTimePicked(hourOfDay, minute),
                currentHour, currentMinute,
                true  // 24-hour format
        ).show();
    }

    /** Refresh all 4 time labels to show current selected values. */
    private void refreshTimeLabels() {
        PrefsManager p = PrefsManager.getInstance();
        tvS1Start.setText(p.formatTime(s1StartH, s1StartM));
        tvS1End  .setText(p.formatTime(s1EndH,   s1EndM));
        tvS2Start.setText(p.formatTime(s2StartH, s2StartM));
        tvS2End  .setText(p.formatTime(s2EndH,   s2EndM));
    }

    // ─── Save ──────────────────────────────────────────────────────────────────

    private void saveAndFinish() {
        int s1StartMins = s1StartH * 60 + s1StartM;
        int s1EndMins   = s1EndH * 60 + s1EndM;
        int s2StartMins = s2StartH * 60 + s2StartM;
        int s2EndMins   = s2EndH * 60 + s2EndM;

        if (s1StartMins >= s1EndMins) {
            Toast.makeText(this, "Slot 1 start time must be before end time.", Toast.LENGTH_LONG).show();
            return;
        }
        if (s2StartMins == s2EndMins) {
            Toast.makeText(this, "Slot 2 start and end times cannot be the same.", Toast.LENGTH_LONG).show();
            return;
        }

        PrefsManager p = PrefsManager.getInstance();
        p.setSlot1(this, s1StartH, s1StartM, s1EndH, s1EndM);
        p.setSlot2(this, s2StartH, s2StartM, s2EndH, s2EndM);

        // Reschedule all alarms with the new times
        if (p.isEnabled(this)) {
            ScheduleManager.scheduleAll(this);
        }

        Toast.makeText(this, "Schedule saved!", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
