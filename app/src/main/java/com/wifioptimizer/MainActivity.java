package com.wifioptimizer;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
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
    private TextView       tvSlot1, tvSlot2, tvAppsCount;
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
                        if (ScheduleManager.isInBlockWindow(this)) startVpnNow();
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
        tvSlot1      = findViewById(R.id.tvSlot1);
        tvSlot2      = findViewById(R.id.tvSlot2);
        tvAppsCount  = findViewById(R.id.tvAppsCount);
        switchEnable = findViewById(R.id.switchEnable);
        btnGrantVpn  = findViewById(R.id.btnGrantVpn);

        // Master ON/OFF toggle
        switchEnable.setOnCheckedChangeListener((btn, isChecked) -> {
            PrefsManager.getInstance().setEnabled(this, isChecked);
            if (isChecked) {
                checkVpnAndActivate();
            } else {
                ScheduleManager.cancelAll(this);
                stopVpnNow();
            }
            updateUI();
        });

        // Grant VPN permission button
        btnGrantVpn.setOnClickListener(v -> checkVpnAndActivate());

        // Manage apps button
        MaterialButton btnManageApps = findViewById(R.id.btnManageApps);
        btnManageApps.setOnClickListener(v ->
                startActivity(new Intent(this, AppSelectionActivity.class))
        );

        // Edit schedule button
        MaterialButton btnEditSchedule = findViewById(R.id.btnEditSchedule);
        btnEditSchedule.setOnClickListener(v ->
                startActivity(new Intent(this, ScheduleEditActivity.class))
        );
    }

    // ─── UI State ──────────────────────────────────────────────────────────────

    private void updateUI() {
        PrefsManager p       = PrefsManager.getInstance();
        boolean enabled      = p.isEnabled(this);
        boolean vpnGranted   = p.isVpnPermissionGranted(this);
        boolean running      = BlockVpnService.isRunning;
        int     blockedCount = p.getBlockedApps(this).size();

        // Prevent toggle listener from firing during programmatic update
        switchEnable.setOnCheckedChangeListener(null);
        switchEnable.setChecked(enabled);
        switchEnable.setOnCheckedChangeListener((btn, isChecked) -> {
            p.setEnabled(this, isChecked);
            if (isChecked) checkVpnAndActivate();
            else { ScheduleManager.cancelAll(this); stopVpnNow(); }
            updateUI();
        });

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

        // Schedule
        tvSlot1.setText("🌤  " + p.formatTime(p.getS1StartH(this), p.getS1StartM(this))
                + "  →  " + p.formatTime(p.getS1EndH(this),   p.getS1EndM(this)));
        tvSlot2.setText("🌙  " + p.formatTime(p.getS2StartH(this), p.getS2StartM(this))
                + "  →  " + p.formatTime(p.getS2EndH(this),   p.getS2EndM(this)));

        // Apps count
        tvAppsCount.setText(blockedCount + " app" + (blockedCount != 1 ? "s" : "") + " selected to block");

        // Permission button
        btnGrantVpn.setEnabled(!vpnGranted && enabled);
        btnGrantVpn.setAlpha(!vpnGranted && enabled ? 1f : 0.4f);
    }

    private void setStatus(String title, String subtitle, int bgColor, int dotColor) {
        tvStatus.setText(title);
        tvStatusSub.setText(subtitle);
        cardStatus.setCardBackgroundColor(bgColor);
        tvStatusDot.setTextColor(dotColor);
    }

    // ─── VPN Control ───────────────────────────────────────────────────────────

    private void checkVpnAndActivate() {
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent == null) {
            // Permission already granted
            PrefsManager.getInstance().setVpnPermissionGranted(this, true);
            ScheduleManager.scheduleAll(this);
            if (ScheduleManager.isInBlockWindow(this)) startVpnNow();
        } else {
            vpnPermLauncher.launch(vpnIntent);
        }
        updateUI();
    }

    private void startVpnNow() {
        Intent i = new Intent(this, BlockVpnService.class);
        i.setAction(BlockVpnService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
    }

    private void stopVpnNow() {
        Intent i = new Intent(this, BlockVpnService.class);
        i.setAction(BlockVpnService.ACTION_STOP);
        startService(i);
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
