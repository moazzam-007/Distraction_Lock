package com.wifioptimizer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;
import java.util.List;

public class ScheduleManager {

    public static void scheduleAll(Context context) {
        cancelAll(context);
        if (!PrefsManager.getInstance().isEnabled(context)) {
            stopWatchdog(context);
            return;
        }
        startWatchdog(context);

        List<Schedule> schedules = PrefsManager.getInstance().getSchedules(context);
        for (Schedule s : schedules) {
            if (s.isEnabled()) {
                int baseRc = s.getId().hashCode() & 0x7FFFFFFF;
                scheduleAlarm(context, s.getId(), s.getStartHour(), s.getStartMinute(), ScheduleReceiver.ACTION_START, baseRc);
                scheduleAlarm(context, s.getId(), s.getEndHour(), s.getEndMinute(), ScheduleReceiver.ACTION_STOP, baseRc + 1);
            }
        }
    }

    public static void cancelAll(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        List<Schedule> schedules = PrefsManager.getInstance().getSchedules(context);
        for (Schedule s : schedules) {
            int baseRc = s.getId().hashCode() & 0x7FFFFFFF;
            am.cancel(buildPendingIntent(context, ScheduleReceiver.ACTION_START, baseRc, s.getId(), 0, 0));
            am.cancel(buildPendingIntent(context, ScheduleReceiver.ACTION_STOP, baseRc + 1, s.getId(), 0, 0));
        }
    }

    private static void scheduleAlarm(Context context, String scheduleId, int hour, int minute, String action, int requestCode) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        PendingIntent pi = buildPendingIntent(context, action, requestCode, scheduleId, hour, minute);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        }
    }

    public static void rescheduleNextDay(Context context, String action, int requestCode, String scheduleId, int hour, int minute) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_YEAR, 1);

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            PendingIntent pi = buildPendingIntent(context, action, requestCode, scheduleId, hour, minute);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            }
        }
    }

    private static PendingIntent buildPendingIntent(Context context, String action, int requestCode, String scheduleId, int hour, int minute) {
        Intent intent = new Intent(context, ScheduleReceiver.class);
        intent.setAction(action);
        intent.putExtra("schedule_id", scheduleId);
        intent.putExtra("hour", hour);
        intent.putExtra("minute", minute);
        intent.putExtra("rc", requestCode);
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static boolean isInBlockWindow(Context context) {
        if (!PrefsManager.getInstance().isEnabled(context)) return false;

        Calendar now = Calendar.getInstance();
        int currentMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        List<Schedule> schedules = PrefsManager.getInstance().getSchedules(context);
        for (Schedule s : schedules) {
            if (!s.isEnabled()) continue;

            int startMins = s.getStartHour() * 60 + s.getStartMinute();
            int endMins = s.getEndHour() * 60 + s.getEndMinute();

            if (startMins < endMins) {
                if (currentMins >= startMins && currentMins < endMins) return true;
            } else {
                // Midnight crossing
                if (currentMins >= startMins || currentMins < endMins) return true;
            }
        }
        return false;
    }

    public static void syncVpnState(Context context) {
        if (!PrefsManager.getInstance().isVpnPermissionGranted(context)) return;
        
        Intent i = new Intent(context, BlockVpnService.class);
        if (isInBlockWindow(context)) {
            i.setAction(BlockVpnService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i);
            } else {
                context.startService(i);
            }
        } else {
            i.setAction(BlockVpnService.ACTION_STOP);
            context.startService(i);
        }
    }

    public static void startWatchdog(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        
        Intent intent = new Intent(context, WatchdogReceiver.class);
        intent.setAction(WatchdogReceiver.ACTION_WATCHDOG);
        PendingIntent pi = PendingIntent.getBroadcast(context, 9999, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // Run every 15 minutes roughly
        long interval = AlarmManager.INTERVAL_FIFTEEN_MINUTES;
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + interval, interval, pi);
    }

    public static void stopWatchdog(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        
        Intent intent = new Intent(context, WatchdogReceiver.class);
        intent.setAction(WatchdogReceiver.ACTION_WATCHDOG);
        PendingIntent pi = PendingIntent.getBroadcast(context, 9999, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
    }
}
