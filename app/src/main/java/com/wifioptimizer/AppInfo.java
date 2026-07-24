package com.wifioptimizer;

import android.graphics.drawable.Drawable;

/**
 * AppInfo — POJO / Model class representing an installed application.
 * Used by AppAdapter to populate the app selection RecyclerView.
 */
public class AppInfo {

    private final String      appName;
    private final String      packageName;
    private       boolean     isBlocked;

    public AppInfo(String appName, String packageName, boolean isBlocked) {
        this.appName     = appName;
        this.packageName = packageName;
        this.isBlocked   = isBlocked;
    }

    // ─── Getters ───────────────────────────────────────────────────────────────

    public String   getAppName()     { return appName; }
    public String   getPackageName() { return packageName; }
    public boolean  isBlocked()      { return isBlocked; }

    // ─── Setter ────────────────────────────────────────────────────────────────

    public void setBlocked(boolean blocked) { this.isBlocked = blocked; }
}
