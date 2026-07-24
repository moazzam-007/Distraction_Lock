package com.wifioptimizer;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/**
 * OnboardingActivity — 3-step first-run setup wizard.
 *
 * Step 1: Welcome + Grant VPN Permission
 * Step 2: Select apps to block (launches AppSelectionActivity)
 * Step 3: Confirm schedule + Finish setup → goes to MainActivity
 *
 * Uses show/hide of step views instead of ViewPager for simplicity.
 */
public class OnboardingActivity extends AppCompatActivity {

    private static final int TOTAL_STEPS = 3;
    private int currentStep = 1;

    // Step container views
    private View stepView1, stepView2, stepView3;

    // Step indicator dots
    private View dot1, dot2, dot3;

    // VPN permission request launcher
    private ActivityResultLauncher<Intent> vpnPermLauncher;

    // App selection request launcher
    private ActivityResultLauncher<Intent> appSelectLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        registerLaunchers();
        initViews();
        showStep(1);
    }

    // ─── Launcher Registration ─────────────────────────────────────────────────

    private void registerLaunchers() {
        // VPN permission result
        vpnPermLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        PrefsManager.getInstance().setVpnPermissionGranted(this, true);
                        goToStep(2); // Permission granted → move to app selection
                    }
                    // If denied, user can retry by tapping button again
                }
        );

        // App selection result
        appSelectLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // Check if they actually selected apps, but allow them to proceed anyway
                    // Just move to step 3
                    goToStep(3);
                }
        );
        
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (currentStep > 1) {
                    goToStep(currentStep - 1);
                } else {
                    finish();
                }
            }
        });
    }

    // ─── View Setup ────────────────────────────────────────────────────────────

    private void initViews() {
        stepView1 = findViewById(R.id.stepView1);
        stepView2 = findViewById(R.id.stepView2);
        stepView3 = findViewById(R.id.stepView3);
        dot1      = findViewById(R.id.dot1);
        dot2      = findViewById(R.id.dot2);
        dot3      = findViewById(R.id.dot3);

        // Step 1 — Grant VPN permission
        MaterialButton btnGrantVpn = findViewById(R.id.btnGrantVpn);
        btnGrantVpn.setOnClickListener(v -> requestVpnPermission());

        // Step 2 — Select apps
        MaterialButton btnSelectApps = findViewById(R.id.btnSelectApps);
        btnSelectApps.setOnClickListener(v -> {
            Intent i = new Intent(this, AppSelectionActivity.class);
            appSelectLauncher.launch(i);
        });

        // Step 3 — Finish setup
        MaterialButton btnFinish = findViewById(R.id.btnFinish);
        btnFinish.setOnClickListener(v -> finishOnboarding());

        // Step 3 — Edit schedule before finishing
        MaterialButton btnEditSchedule = findViewById(R.id.btnEditScheduleOnboard);
        btnEditSchedule.setOnClickListener(v ->
                startActivity(new Intent(this, ScheduleEditActivity.class))
        );
    }

    // ─── Step Navigation ───────────────────────────────────────────────────────

    private void showStep(int step) {
        stepView1.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        stepView2.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
        stepView3.setVisibility(step == 3 ? View.VISIBLE : View.GONE);

        dot1.setAlpha(step >= 1 ? 1f : 0.3f);
        dot2.setAlpha(step >= 2 ? 1f : 0.3f);
        dot3.setAlpha(step == 3 ? 1f : 0.3f);

        currentStep = step;

        // Update step 3 schedule preview
        if (step == 3) updateSchedulePreview();
    }

    private void goToStep(int step) {
        showStep(step);
    }

    // ─── VPN Permission ────────────────────────────────────────────────────────

    private void requestVpnPermission() {
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent == null) {
            // Already granted
            PrefsManager.getInstance().setVpnPermissionGranted(this, true);
            goToStep(2);
        } else {
            vpnPermLauncher.launch(vpnIntent);
        }
    }

    // ─── Schedule Preview ──────────────────────────────────────────────────────

    private void updateSchedulePreview() {
        PrefsManager p = PrefsManager.getInstance();
        TextView tvScheduleSummary = findViewById(R.id.tvScheduleSummary);
        java.util.List<Schedule> schedules = p.getSchedules(this);
        
        if (schedules.isEmpty()) {
            tvScheduleSummary.setText("No schedules set");
        } else {
            StringBuilder sb = new StringBuilder();
            for (Schedule s : schedules) {
                sb.append("⏰ ").append(p.formatTime(s.getStartHour(), s.getStartMinute()))
                  .append(" – ").append(p.formatTime(s.getEndHour(), s.getEndMinute())).append("\n");
            }
            tvScheduleSummary.setText(sb.toString().trim());
        }
    }

    // ─── Finish ────────────────────────────────────────────────────────────────

    private void finishOnboarding() {
        if (VpnService.prepare(this) != null) {
            Toast.makeText(this, "VPN permission is required to continue.", Toast.LENGTH_LONG).show();
            goToStep(1);
            return;
        }

        PrefsManager p = PrefsManager.getInstance();
        p.setFirstRunDone(this);
        p.setEnabled(this, true);

        // Schedule all alarms and sync VPN state immediately
        ScheduleManager.scheduleAll(this);
        ScheduleManager.syncVpnState(this);

        // Go to main screen
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

}
