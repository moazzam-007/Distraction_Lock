package com.wifioptimizer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/**
 * Helper class to navigate users to various permission and background execution settings.
 */
public class PermissionHelper {

    /**
     * Attempts to open the OEM-specific Autostart / Background execution settings page.
     * If the manufacturer is unknown or the specific intent fails, it falls back to the
     * standard Application Details settings page.
     */
    public static void requestAutoStart(Context context) {
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        Intent intent = new Intent();
        boolean found = false;

        try {
            if (manufacturer.contains("xiaomi") || manufacturer.contains("poco")) {
                intent.setComponent(new ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                ));
                found = true;
            } else if (manufacturer.contains("oppo") || manufacturer.contains("realme")) {
                intent.setComponent(new ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                ));
                found = true;
            } else if (manufacturer.contains("vivo")) {
                intent.setComponent(new ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                ));
                found = true;
            } else if (manufacturer.contains("letv")) {
                intent.setComponent(new ComponentName(
                        "com.letv.android.letvsafe",
                        "com.letv.android.letvsafe.AutobootManageActivity"
                ));
                found = true;
            } else if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
                intent.setComponent(new ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                ));
                found = true;
            }

            if (found) {
                context.startActivity(intent);
                return;
            }
        } catch (Exception e) {
            // OEM specific activity not found or changed, fallback
        }

        // Fallback to Application Details page
        openAppSettings(context);
    }

    /**
     * Opens the standard Android Battery Optimization settings.
     */
    public static void requestIgnoreBatteryOptimizations(Context context) {
        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            openAppSettings(context);
        }
    }

    /**
     * Opens the Accessibility Settings page so the user can enable the KeepAlive service.
     */
    public static void requestAccessibility(Context context) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            openAppSettings(context);
        }
    }

    private static void openAppSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        context.startActivity(intent);
    }
}
