package com.wifioptimizer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.List;

/**
 * AppAdapter — RecyclerView Adapter for displaying installed apps with a toggle checkbox.
 * Implements the Adapter pattern for efficient list rendering.
 * Notifies the Activity via OnAppToggleListener interface when user checks/unchecks an app.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

    // ─── Callback Interface ────────────────────────────────────────────────────

    /** Callback interface — Activity implements this to receive toggle events. */
    public interface OnAppToggleListener {
        void onToggle(String packageName, boolean isBlocked);
    }

    // ─── Fields ────────────────────────────────────────────────────────────────

    private final List<AppInfo>        apps;
    private final OnAppToggleListener  listener;
    private final ExecutorService      iconExecutor = Executors.newFixedThreadPool(4);

    // ─── Constructor ───────────────────────────────────────────────────────────

    public AppAdapter(List<AppInfo> apps, OnAppToggleListener listener) {
        this.apps     = apps;
        this.listener = listener;
    }

    // ─── ViewHolder ────────────────────────────────────────────────────────────

    /**
     * ViewHolder — holds references to the views for one app list row.
     * Avoids repeated findViewById() calls for better scroll performance.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView        ivAppIcon;
        final TextView         tvAppName;
        final TextView         tvPackageName;
        final MaterialCheckBox cbBlocked;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon     = itemView.findViewById(R.id.ivAppIcon);
            tvAppName     = itemView.findViewById(R.id.tvAppName);
            tvPackageName = itemView.findViewById(R.id.tvPackageName);
            cbBlocked     = itemView.findViewById(R.id.cbBlocked);
        }
    }

    // ─── Adapter Methods ───────────────────────────────────────────────────────

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo app = apps.get(position);

        holder.ivAppIcon.setImageDrawable(null); // Clear previous
        iconExecutor.execute(() -> {
            try {
                android.content.pm.PackageManager pm = holder.itemView.getContext().getPackageManager();
                android.graphics.drawable.Drawable icon = pm.getApplicationIcon(app.getPackageName());
                holder.ivAppIcon.post(() -> {
                    // Check if view is still bound to the same package
                    if (holder.tvPackageName.getText().toString().equals(app.getPackageName())) {
                        holder.ivAppIcon.setImageDrawable(icon);
                    }
                });
            } catch (Exception ignored) { }
        });

        holder.tvAppName.setText(app.getAppName());
        holder.tvPackageName.setText(app.getPackageName());

        // Set checkbox without triggering listener (to avoid recursive calls)
        holder.cbBlocked.setOnCheckedChangeListener(null);
        holder.cbBlocked.setChecked(app.isBlocked());

        // Restore listener — fires only on real user interaction
        holder.cbBlocked.setOnCheckedChangeListener((btn, isChecked) -> {
            app.setBlocked(isChecked);
            listener.onToggle(app.getPackageName(), isChecked);
        });

        // Tap anywhere on the row also toggles the checkbox
        holder.itemView.setOnClickListener(v -> holder.cbBlocked.toggle());
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    // ─── Filter Helper ─────────────────────────────────────────────────────────

    /** Update list with filtered results (for search). */
    public void updateList(List<AppInfo> filtered) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() { return apps.size(); }
            @Override
            public int getNewListSize() { return filtered.size(); }
            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return apps.get(oldItemPosition).getPackageName().equals(filtered.get(newItemPosition).getPackageName());
            }
            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                AppInfo oldApp = apps.get(oldItemPosition);
                AppInfo newApp = filtered.get(newItemPosition);
                return oldApp.isBlocked() == newApp.isBlocked() &&
                       oldApp.getAppName().equals(newApp.getAppName());
            }
        });
        apps.clear();
        apps.addAll(filtered);
        diffResult.dispatchUpdatesTo(this);
    }
    
    /** Prevent memory leaks by shutting down the executor when activity is destroyed */
    public void shutdown() {
        if (iconExecutor != null && !iconExecutor.isShutdown()) {
            iconExecutor.shutdownNow();
        }
    }
}
