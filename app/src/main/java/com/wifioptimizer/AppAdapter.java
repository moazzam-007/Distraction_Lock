package com.wifioptimizer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

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

        try {
            android.content.pm.PackageManager pm = holder.itemView.getContext().getPackageManager();
            holder.ivAppIcon.setImageDrawable(pm.getApplicationIcon(app.getPackageName()));
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            holder.ivAppIcon.setImageDrawable(null);
        }

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
        apps.clear();
        apps.addAll(filtered);
        notifyDataSetChanged();
    }
}
