package com.wifioptimizer;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AppSelectionActivity — Lets the user choose which installed apps to block.
 * Loads all user-installed apps in a background thread (keeps UI smooth).
 * User can search, toggle apps, and save the selection.
 */
public class AppSelectionActivity extends AppCompatActivity
        implements AppAdapter.OnAppToggleListener {

    private AppAdapter         adapter;
    private List<AppInfo>      allApps;       // Full list (for search reset)
    private List<AppInfo>      displayedApps; // Currently shown in RecyclerView
    private Set<String>        selectedPkgs;  // Packages currently toggled ON

    private TextView           tvCount;
    private MaterialButton     btnSave;

    // Background thread pool for loading app list without blocking UI
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler         uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_selection);

        setupToolbar();
        initViews();
        loadAppsInBackground();
    }

    // ─── Setup ─────────────────────────────────────────────────────────────────

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Select Apps to Block");
        }
    }

    private void initViews() {
        tvCount        = findViewById(R.id.tvSelectedCount);
        btnSave        = findViewById(R.id.btnSaveApps);
        RecyclerView rv = findViewById(R.id.rvApps);
        EditText etSearch = findViewById(R.id.etSearch);

        allApps       = new ArrayList<>();
        displayedApps = new ArrayList<>();
        selectedPkgs  = PrefsManager.getInstance().getBlockedApps(this);

        adapter = new AppAdapter(displayedApps, this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        updateCountLabel();

        // Real-time search filter
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterApps(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Save button — persists selection and reschedules
        btnSave.setOnClickListener(v -> saveAndFinish());
    }

    // ─── App Loading (Background Thread) ──────────────────────────────────────

    private void loadAppsInBackground() {
        executor.execute(() -> {
            List<AppInfo> loaded = fetchInstalledApps();

            // Update UI on main thread
            uiHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                allApps.addAll(loaded);
                displayedApps.addAll(loaded);
                adapter.notifyDataSetChanged();
                updateCountLabel();
            });
        });
    }

    private List<AppInfo> fetchInstalledApps() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        
        List<android.content.pm.ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA);
        List<AppInfo> result = new ArrayList<>();
        java.util.Set<String> addedPackages = new java.util.HashSet<>();

        for (android.content.pm.ResolveInfo resolveInfo : resolveInfos) {
            String packageName = resolveInfo.activityInfo.packageName;
            
            // Skip duplicates (some apps have multiple launcher activities)
            if (addedPackages.contains(packageName)) continue;
            // Skip our own app
            if (packageName.equals(getPackageName())) continue;

            addedPackages.add(packageName);
            String name = resolveInfo.loadLabel(pm).toString();
            boolean isBlocked = selectedPkgs.contains(packageName);
            result.add(new AppInfo(name, packageName, isBlocked));
        }

        // Sort: blocked apps first, then alphabetically
        java.util.Collections.sort(result, (a, b) -> {
            if (a.isBlocked() != b.isBlocked()) return a.isBlocked() ? -1 : 1;
            return a.getAppName().compareToIgnoreCase(b.getAppName());
        });

        return result;
    }

    // ─── Search Filter ─────────────────────────────────────────────────────────

    private void filterApps(String query) {
        List<AppInfo> filtered = new ArrayList<>();
        String lowerQuery = query.toLowerCase().trim();

        for (AppInfo app : allApps) {
            if (app.getAppName().toLowerCase().contains(lowerQuery)
                    || app.getPackageName().toLowerCase().contains(lowerQuery)) {
                filtered.add(app);
            }
        }
        adapter.updateList(filtered);
    }

    // ─── Toggle Callback ───────────────────────────────────────────────────────

    /** Called by AppAdapter when user checks/unchecks an app row. */
    @Override
    public void onToggle(String packageName, boolean isBlocked) {
        if (isBlocked) {
            selectedPkgs.add(packageName);
        } else {
            selectedPkgs.remove(packageName);
        }
        updateCountLabel();
    }

    private void updateCountLabel() {
        int count = selectedPkgs.size();
        tvCount.setText(count + " app" + (count != 1 ? "s" : "") + " selected to block");
    }

    // ─── Save ──────────────────────────────────────────────────────────────────

    private void saveAndFinish() {
        PrefsManager.getInstance().setBlockedApps(this, selectedPkgs);

        // Reschedule alarms with the updated app list applied
        if (PrefsManager.getInstance().isEnabled(this)) {
            ScheduleManager.scheduleAll(this);
            ScheduleManager.syncVpnState(this);
        }

        Toast.makeText(this, "Saved! " + selectedPkgs.size() + " apps will be blocked.", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        if (adapter != null) {
            adapter.shutdown();
        }
        super.onDestroy();
    }
}
