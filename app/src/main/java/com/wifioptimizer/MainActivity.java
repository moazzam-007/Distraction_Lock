package com.wifioptimizer;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

/**
 * MainActivity — Main screen of the WiFi Optimizer app.
 * Shows optimization status, schedule summary, and navigation to sub-screens.
 * Routes to OnboardingActivity on first launch.
 */
public class MainActivity extends AppCompatActivity {

    private TextView       tvStatus, tvStatusSub, tvStatusDot;
    private TextView       tvAppsCount;
    private SwitchMaterial switchEnable;
    private MaterialButton btnGrantVpn;
    private MaterialCardView cardStatus;

    // VPN permission launcher (for the edge case where permission expired)
    private ActivityResultLauncher<Intent> vpnPermLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // First run → redirect to Onboarding
        if (PrefsManager.getInstance().isFirstRun(this)) {
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        registerVpnLauncher();
        initViews();
        requestNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ensure VPN is running if it should be (revival if killed by system booster)
        if (PrefsManager.getInstance().isEnabled(this)) {
            ScheduleManager.syncVpnState(this);
        }
        // Refresh UI every time we return to this screen (e.g. after editing apps/schedule)
        updateUI();
    }

    // ─── Setup ─────────────────────────────────────────────────────────────────

    private void registerVpnLauncher() {
        vpnPermLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        PrefsManager.getInstance().setVpnPermissionGranted(this, true);
                        ScheduleManager.scheduleAll(this);
                        ScheduleManager.syncVpnState(this);
                        updateUI();
                    }
                }
        );
    }

    private void initViews() {
        cardStatus   = findViewById(R.id.cardStatus);
        tvStatus     = findViewById(R.id.tvStatus);
        tvStatusSub  = findViewById(R.id.tvStatusSub);
        tvStatusDot  = findViewById(R.id.tvStatusDot);
        tvAppsCount  = findViewById(R.id.tvAppsCount);
        switchEnable = findViewById(R.id.switchEnable);
        btnGrantVpn  = findViewById(R.id.btnGrantVpn);

        // Master ON/OFF toggle
        switchEnable.setOnCheckedChangeListener(this::onSwitchEnableChanged);

        // Grant VPN permission button
        btnGrantVpn.setOnClickListener(v -> checkVpnAndActivate());

        // Manage apps button
        MaterialButton btnManageApps = findViewById(R.id.btnManageApps);
        btnManageApps.setOnClickListener(v ->
                startActivity(new Intent(this, AppSelectionActivity.class))
        );

        // Add schedule button
        MaterialButton btnAddSchedule = findViewById(R.id.btnAddSchedule);
        btnAddSchedule.setOnClickListener(v ->
                startActivity(new Intent(this, ScheduleEditActivity.class))
        );
    }

    // ─── UI State ──────────────────────────────────────────────────────────────

    private void onSwitchEnableChanged(android.widget.CompoundButton btn, boolean isChecked) {
        PrefsManager.getInstance().setEnabled(this, isChecked);
        if (isChecked) {
            checkVpnAndActivate();
        } else {
            ScheduleManager.cancelAll(this);
            Intent i = new Intent(this, BlockVpnService.class);
            i.setAction(BlockVpnService.ACTION_STOP);
            startService(i);
        }
        updateUI();
    }

    private void updateUI() {
        PrefsManager p       = PrefsManager.getInstance();
        boolean enabled      = p.isEnabled(this);
        boolean vpnGranted   = p.isVpnPermissionGranted(this);
        boolean running      = BlockVpnService.isRunning;
        int     blockedCount = p.getBlockedApps(this).size();

        // Prevent toggle listener from firing during programmatic update
        switchEnable.setOnCheckedChangeListener(null);
        switchEnable.setChecked(enabled);
        switchEnable.setOnCheckedChangeListener(this::onSwitchEnableChanged);

        // Status card
        if (!enabled) {
            setStatus("Optimization Disabled", "Enable to activate network optimization",
                    getColor(R.color.card_inactive), getColor(R.color.dot_inactive));
        } else if (!vpnGranted) {
            setStatus("Setup Required", "Tap 'Grant Permission' to complete setup",
                    getColor(R.color.card_warning), getColor(R.color.dot_warning));
        } else if (running) {
            setStatus("Optimization Active ✓", "Apps are being managed now",
                    getColor(R.color.card_active), getColor(R.color.dot_active));
        } else {
            setStatus("Optimization Scheduled", "Will activate at scheduled time",
                    getColor(R.color.card_scheduled), getColor(R.color.dot_scheduled));
        }

        // Dynamic Schedules
        android.widget.LinearLayout scheduleContainer = findViewById(R.id.scheduleContainer);
        scheduleContainer.removeAllViews();
        java.util.List<Schedule> schedules = p.getSchedules(this);
        
        if (schedules.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No schedules set. Optimization runs manually.");
            empty.setTextColor(getColor(R.color.text_secondary));
            empty.setTextSize(14f);
            scheduleContainer.addView(empty);
        } else {
            for (Schedule s : schedules) {
                TextView tv = new TextView(this);
                tv.setText("⏰ " + p.formatTime(s.getStartHour(), s.getStartMinute()) +
                           "  →  " + p.formatTime(s.getEndHour(), s.getEndMinute()));
                tv.setTextColor(getColor(R.color.text_primary));
                tv.setTextSize(14f);
                tv.setTypeface(android.graphics.Typeface.MONOSPACE);
                tv.setPadding(0, 8, 0, 8);
                
                // Click to edit
                tv.setOnClickListener(v -> {
                    Intent intent = new Intent(this, ScheduleEditActivity.class);
                    intent.putExtra("schedule_id", s.getId());
                    startActivity(intent);
                });
                
                scheduleContainer.addView(tv);
                
                View divider = new View(this);
                divider.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(getColor(R.color.divider));
                scheduleContainer.addView(divider);
            }
        }
        
        // Permission warnings
        if (!PermissionUtils.hasExactAlarmPermission(this)) {
            btnGrantVpn.setText("Grant Exact Alarm Permission");
            btnGrantVpn.setEnabled(true);
            btnGrantVpn.setAlpha(1f);
            btnGrantVpn.setOnClickListener(v -> PermissionUtils.requestExactAlarmPermission(this));
        } else if (!PermissionUtils.hasBatteryOptimizationPermission(this)) {
            btnGrantVpn.setText("Grant Battery Permission (Required)");
            btnGrantVpn.setEnabled(true);
            btnGrantVpn.setAlpha(1f);
            btnGrantVpn.setOnClickListener(v -> PermissionUtils.requestBatteryOptimizationPermission(this));
        } else {
            btnGrantVpn.setText("Grant Network Permission");
            btnGrantVpn.setOnClickListener(v -> checkVpnAndActivate());
            // Permission button state
            btnGrantVpn.setEnabled(!vpnGranted && enabled);
            btnGrantVpn.setAlpha(!vpnGranted && enabled ? 1f : 0.4f);
        }

        // Apps count
        tvAppsCount.setText(blockedCount + " app" + (blockedCount != 1 ? "s" : "") + " selected to block");

    }

    private void setStatus(String title, String subtitle, int bgColor, int dotColor) {
        tvStatus.setText(title);
        tvStatusSub.setText(subtitle);
        cardStatus.setCardBackgroundColor(bgColor);
        tvStatusDot.setTextColor(dotColor);
    }

    // ─── VPN Control ───────────────────────────────────────────────────────────

    private void checkVpnAndActivate() {
        Intent vpnIntent = android.net.VpnService.prepare(this);
        if (vpnIntent == null) {
            // Permission already granted
            PrefsManager.getInstance().setVpnPermissionGranted(this, true);
            ScheduleManager.scheduleAll(this);
            ScheduleManager.syncVpnState(this);
        } else {
            vpnPermLauncher.launch(vpnIntent);
        }
        updateUI();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 99);
            }
        }
    }
}
