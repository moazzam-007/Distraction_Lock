package com.wifioptimizer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;

/**
 * BootReceiver — Restores all scheduled alarms after device reboot.
 * Android clears all AlarmManager alarms on reboot, so we must reschedule.
 * Also restarts the VPN service if reboot happened during a block window.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        boolean isBootEvent = intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)
                || intent.getAction().equals("android.intent.action.QUICKBOOT_POWERON")
                || intent.getAction().equals("com.htc.intent.action.QUICKBOOT_POWERON");

        if (!isBootEvent) return;
        if (!PrefsManager.getInstance().isEnabled(context)) return;

        // Re-register all alarms (lost after reboot)
        ScheduleManager.scheduleAll(context);

        // If reboot happened during a block window AND VPN permission is already granted,
        // restart the VPN service immediately
        boolean inWindow   = ScheduleManager.isInBlockWindow(context);
        boolean vpnReady   = VpnService.prepare(context) == null;

        if (inWindow && vpnReady) {
            Intent vpnIntent = new Intent(context, BlockVpnService.class);
            vpnIntent.setAction(BlockVpnService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(vpnIntent);
            } else {
                context.startService(vpnIntent);
            }
        }
    }
}
