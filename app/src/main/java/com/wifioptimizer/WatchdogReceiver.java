package com.wifioptimizer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * WatchdogReceiver — A lightweight periodic alarm receiver.
 * Triggered every ~15 minutes by AlarmManager to check if the VPN was
 * aggressively killed by a "Phone Booster" or battery optimization.
 */
public class WatchdogReceiver extends BroadcastReceiver {

    public static final String ACTION_WATCHDOG = "com.wifioptimizer.WATCHDOG_CHECK";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_WATCHDOG.equals(intent.getAction())) return;

        // If app is completely disabled, stop the watchdog cycle
        if (!PrefsManager.getInstance().isEnabled(context)) {
            ScheduleManager.stopWatchdog(context);
            return;
        }

        // Sync state: checks if we are in block window and if VPN is dead, revives it
        ScheduleManager.syncVpnState(context);

        // Re-arm watchdog
        ScheduleManager.startWatchdog(context);
    }
}
