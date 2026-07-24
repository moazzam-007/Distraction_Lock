package com.wifioptimizer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;

public class ScheduleReceiver extends BroadcastReceiver {

    public static final String ACTION_START  = "com.wifioptimizer.BLOCK_START";
    public static final String ACTION_STOP   = "com.wifioptimizer.BLOCK_STOP";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!PrefsManager.getInstance().isEnabled(context)) return;
        if (intent.getAction() == null) return;

        String scheduleId = intent.getStringExtra("schedule_id");
        int hour = intent.getIntExtra("hour", -1);
        int minute = intent.getIntExtra("minute", -1);
        int rc = intent.getIntExtra("rc", -1);

        if (scheduleId == null || hour == -1 || minute == -1 || rc == -1) {
            return; // Invalid intent
        }

        switch (intent.getAction()) {
            case ACTION_START:
                if (VpnService.prepare(context) == null) {
                    PrefsManager.getInstance().setVpnPermissionGranted(context, true);
                    startVpnService(context);
                } else {
                    PrefsManager.getInstance().setVpnPermissionGranted(context, false);
                }
                ScheduleManager.rescheduleNextDay(context, ACTION_START, rc, scheduleId, hour, minute);
                break;

            case ACTION_STOP:
                stopVpnService(context);
                ScheduleManager.rescheduleNextDay(context, ACTION_STOP, rc, scheduleId, hour, minute);
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
