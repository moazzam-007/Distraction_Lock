package com.wifioptimizer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;

/**
 * ScheduleReceiver — BroadcastReceiver triggered by AlarmManager at scheduled times.
 * Starts or stops the VPN service, then reschedules itself for the next day.
 */
public class ScheduleReceiver extends BroadcastReceiver {

    public static final String ACTION_START  = "com.wifioptimizer.BLOCK_START";
    public static final String ACTION_STOP   = "com.wifioptimizer.BLOCK_STOP";

    // Extras embedded in the PendingIntent — used for next-day rescheduling
    public static final String EXTRA_SLOT    = "slot";
    public static final String EXTRA_HOUR    = "hour";
    public static final String EXTRA_MINUTE  = "minute";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!PrefsManager.getInstance().isEnabled(context)) return;
        if (intent.getAction() == null) return;

        // Read extras — these were embedded by ScheduleManager.buildPendingIntent()
        int slot   = intent.getIntExtra(EXTRA_SLOT,   1);
        int hour   = intent.getIntExtra(EXTRA_HOUR,   -1);
        int minute = intent.getIntExtra(EXTRA_MINUTE, -1);

        // Fallback if extras are missing
        if (hour == -1 || minute == -1) {
            PrefsManager p = PrefsManager.getInstance();
            if (ACTION_START.equals(intent.getAction())) {
                hour   = slot == 1 ? p.getS1StartH(context) : p.getS2StartH(context);
                minute = slot == 1 ? p.getS1StartM(context) : p.getS2StartM(context);
            } else {
                hour   = slot == 1 ? p.getS1EndH(context) : p.getS2EndH(context);
                minute = slot == 1 ? p.getS1EndM(context) : p.getS2EndM(context);
            }
        }

        switch (intent.getAction()) {
            case ACTION_START:
                // Check if VPN permission is actually still granted
                if (VpnService.prepare(context) == null) {
                    PrefsManager.getInstance().setVpnPermissionGranted(context, true);
                    startVpnService(context);
                } else {
                    PrefsManager.getInstance().setVpnPermissionGranted(context, false);
                }
                // Reschedule start alarm for same time tomorrow
                ScheduleManager.rescheduleNextDay(
                        context, ACTION_START,
                        ScheduleManager.rcForAction(ACTION_START, slot),
                        slot, hour, minute
                );
                break;

            case ACTION_STOP:
                stopVpnService(context);
                // Reschedule stop alarm for same time tomorrow
                ScheduleManager.rescheduleNextDay(
                        context, ACTION_STOP,
                        ScheduleManager.rcForAction(ACTION_STOP, slot),
                        slot, hour, minute
                );
                break;
        }
    }

    private void startVpnService(Context context) {
        Intent i = new Intent(context, BlockVpnService.class);
        i.setAction(BlockVpnService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(i);
        } else {
            context.startService(i);
        }
    }

    private void stopVpnService(Context context) {
        Intent i = new Intent(context, BlockVpnService.class);
        i.setAction(BlockVpnService.ACTION_STOP);
        context.startService(i);
    }
}
