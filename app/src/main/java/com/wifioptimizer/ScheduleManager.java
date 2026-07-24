package com.wifioptimizer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * ScheduleManager — Static utility class for managing 4 daily exact alarms.
 * Slot 1: 3 PM (start) and 5 PM (stop)
 * Slot 2: 11 PM (start) and 3 AM (stop)
 *
 * Uses setExactAndAllowWhileIdle() for reliability even in Doze mode.
 * Alarms self-reschedule every 24 hours via ScheduleReceiver.
 */
public class ScheduleManager {

    // Request codes — uniquely identify each PendingIntent with AlarmManager
    public static final int RC_S1_START = 101;
    public static final int RC_S1_STOP  = 102;
    public static final int RC_S2_START = 103;
    public static final int RC_S2_STOP  = 104;

    /**
     * Maps each request code to its correct action string.
     * CRITICAL: cancelAll() uses this map to build matching PendingIntents.
     * PendingIntent matching requires action + requestCode to match exactly.
     */
    private static final Map<Integer, String> RC_ACTION_MAP = new HashMap<>();
    static {
        RC_ACTION_MAP.put(RC_S1_START, ScheduleReceiver.ACTION_START);
        RC_ACTION_MAP.put(RC_S1_STOP,  ScheduleReceiver.ACTION_STOP);
        RC_ACTION_MAP.put(RC_S2_START, ScheduleReceiver.ACTION_START);
        RC_ACTION_MAP.put(RC_S2_STOP,  ScheduleReceiver.ACTION_STOP);
    }

    /** Schedule all 4 daily alarms based on saved preferences. */
    public static void scheduleAll(Context context) {
        cancelAll(context);
        PrefsManager p = PrefsManager.getInstance();

        scheduleAlarm(context, 1, p.getS1StartH(context), p.getS1StartM(context), ScheduleReceiver.ACTION_START, RC_S1_START);
        scheduleAlarm(context, 1, p.getS1EndH(context),   p.getS1EndM(context),   ScheduleReceiver.ACTION_STOP,  RC_S1_STOP);
        scheduleAlarm(context, 2, p.getS2StartH(context), p.getS2StartM(context), ScheduleReceiver.ACTION_START, RC_S2_START);
        scheduleAlarm(context, 2, p.getS2EndH(context),   p.getS2EndM(context),   ScheduleReceiver.ACTION_STOP,  RC_S2_STOP);
    }

    /**
     * Cancel all 4 alarms.
     * Uses RC_ACTION_MAP so each cancel PendingIntent has the CORRECT action —
     * AlarmManager.cancel() requires the action to match exactly.
     */
    public static void cancelAll(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        for (Map.Entry<Integer, String> entry : RC_ACTION_MAP.entrySet()) {
            // Extras don't affect PendingIntent matching, only action + rc + component do
            PendingIntent pi = buildPendingIntent(context, entry.getValue(), entry.getKey(), 0, 0, 0);
            am.cancel(pi);
        }
    }

    /**
     * Schedule a single exact alarm.
     * If the target time has already passed today, schedules for tomorrow.
     */
    private static void scheduleAlarm(Context context, int slot, int hour, int minute,
                                       String action, int requestCode) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE,      minute);
        cal.set(Calendar.SECOND,      0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1); // Schedule for tomorrow if time passed
        }

        PendingIntent pi = buildPendingIntent(context, action, requestCode, slot, hour, minute);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        }
    }

    /**
     * Reschedule a single alarm for the same time tomorrow.
     * Called from ScheduleReceiver after each trigger to maintain daily repetition.
     */
    public static void rescheduleNextDay(Context context, String action, int requestCode,
                                          int slot, int hour, int minute) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE,      minute);
        cal.set(Calendar.SECOND,      0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_YEAR, 1); // Always next day

        PendingIntent pi = buildPendingIntent(context, action, requestCode, slot, hour, minute);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        }
    }

    /** Returns true if current time falls within either configured blocking window. */
    public static boolean isInBlockWindow(Context context) {
        PrefsManager p = PrefsManager.getInstance();
        Calendar now = Calendar.getInstance();
        int nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        // Slot 1 (e.g. 3 PM – 5 PM, within same day)
        int s1Start = p.getS1StartH(context) * 60 + p.getS1StartM(context);
        int s1End   = p.getS1EndH(context)   * 60 + p.getS1EndM(context);
        if (s1Start < s1End && nowMinutes >= s1Start && nowMinutes < s1End) {
            return true;
        }

        // Slot 2 (e.g. 11 PM – 3 AM, can cross midnight)
        int s2Start = p.getS2StartH(context) * 60 + p.getS2StartM(context);
        int s2End   = p.getS2EndH(context)   * 60 + p.getS2EndM(context);
        if (s2Start > s2End) {
            // Crosses midnight: active if time >= start OR time < end
            return nowMinutes >= s2Start || nowMinutes < s2End;
        }
        return s2Start < s2End && nowMinutes >= s2Start && nowMinutes < s2End;
    }

    /** Maps slot number + action string → correct request code. */
    public static int rcForAction(String action, int slot) {
        String key = slot + action;
        if (key.equals(1 + ScheduleReceiver.ACTION_START)) return RC_S1_START;
        if (key.equals(1 + ScheduleReceiver.ACTION_STOP))  return RC_S1_STOP;
        if (key.equals(2 + ScheduleReceiver.ACTION_START)) return RC_S2_START;
        return RC_S2_STOP;
    }

    /**
     * Build a PendingIntent for ScheduleReceiver with slot/hour/minute embedded as extras.
     * These extras let the receiver know what time to reschedule for the next day.
     */
    private static PendingIntent buildPendingIntent(Context context, String action,
                                                     int requestCode, int slot,
                                                     int hour, int minute) {
        Intent intent = new Intent(context, ScheduleReceiver.class);
        intent.setAction(action);
        intent.putExtra(ScheduleReceiver.EXTRA_SLOT,   slot);
        intent.putExtra(ScheduleReceiver.EXTRA_HOUR,   hour);
        intent.putExtra(ScheduleReceiver.EXTRA_MINUTE, minute);

        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
    }
}
