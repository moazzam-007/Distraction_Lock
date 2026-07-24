package com.wifioptimizer;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

public class PrefsManager {

    private static final String PREFS_NAME   = "wifi_optimizer_prefs";
    private static PrefsManager instance;

    // Cache the SharedPreferences instance itself (NOT a Context) after first
    // access — SharedPreferencesImpl holds no reference back to the Activity
    // that requested it, so this is safe to keep for the process lifetime.
    private SharedPreferences cachedPrefs;

    private static final int DEF_S1_SH = 15, DEF_S1_SM = 0;
    private static final int DEF_S1_EH = 17, DEF_S1_EM = 0;
    private static final int DEF_S2_SH = 23, DEF_S2_SM = 0;
    private static final int DEF_S2_EH = 3,  DEF_S2_EM = 0;

    private static final Set<String> DEFAULT_BLOCKED = new HashSet<>();

    private PrefsManager() { }

    public static synchronized PrefsManager getInstance() {
        if (instance == null) {
            instance = new PrefsManager();
        }
        return instance;
    }

    private SharedPreferences prefs(Context c) {
        if (cachedPrefs == null) {
            // getApplicationContext() so we never end up holding whichever
            // Activity happened to call this first, even indirectly.
            cachedPrefs = c.getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
        return cachedPrefs;
    }

    // ─── App State ─────────────────────────────────────────────────────────────
    public boolean isEnabled(Context c) { return prefs(c).getBoolean("enabled", true); }
    public void setEnabled(Context c, boolean value) { prefs(c).edit().putBoolean("enabled", value).apply(); }

    // ─── First Run Onboarding ──────────────────────────────────────────────────
    public boolean isFirstRun(Context c) { return prefs(c).getBoolean("first_run", true); }
    public void setFirstRunDone(Context c) { prefs(c).edit().putBoolean("first_run", false).apply(); }

    // ─── VPN Permission ────────────────────────────────────────────────────────
    public boolean isVpnPermissionGranted(Context c) { return prefs(c).getBoolean("vpn_ok", false); }
    public void setVpnPermissionGranted(Context c, boolean value) { prefs(c).edit().putBoolean("vpn_ok", value).apply(); }

    // ─── Blocked Apps (Dynamic List) ───────────────────────────────────────────
    public Set<String> getBlockedApps(Context c) {
        Set<String> saved = prefs(c).getStringSet("blocked_apps", null);
        return saved != null ? new HashSet<>(saved) : new HashSet<>(DEFAULT_BLOCKED);
    }

    public void setBlockedApps(Context c, Set<String> packages) {
        prefs(c).edit().putStringSet("blocked_apps", new HashSet<>(packages)).apply();
    }

    /** NEW — atomic add; removes the read-modify-write race between callers. */
    public synchronized void addBlockedApp(Context c, String packageName) {
        Set<String> current = getBlockedApps(c);
        current.add(packageName);
        setBlockedApps(c, current);
    }

    /** NEW — atomic remove, same reasoning as addBlockedApp. */
    public synchronized void removeBlockedApp(Context c, String packageName) {
        Set<String> current = getBlockedApps(c);
        current.remove(packageName);
        setBlockedApps(c, current);
    }

    // ─── Dynamic Schedules ─────────────────────────────────────────────────────

    public List<Schedule> getSchedules(Context c) {
        String json = prefs(c).getString("schedules", null);
        List<Schedule> schedules = new ArrayList<>();
        if (json == null) {
            // Default 2 schedules for backward compatibility / first run
            schedules.add(new Schedule(DEF_S1_SH, DEF_S1_SM, DEF_S1_EH, DEF_S1_EM));
            schedules.add(new Schedule(DEF_S2_SH, DEF_S2_SM, DEF_S2_EH, DEF_S2_EM));
            return schedules;
        }

        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Schedule s = new Schedule();
                s.setId(obj.getString("id"));
                s.setStartHour(obj.getInt("startH"));
                s.setStartMinute(obj.getInt("startM"));
                s.setEndHour(obj.getInt("endH"));
                s.setEndMinute(obj.getInt("endM"));
                s.setEnabled(obj.optBoolean("enabled", true));
                schedules.add(s);
            }
        } catch (Exception e) {
            android.util.Log.e("PrefsManager", "Error parsing schedules", e);
        }
        return schedules;
    }

    public void saveSchedules(Context c, List<Schedule> schedules) {
        JSONArray array = new JSONArray();
        try {
            for (Schedule s : schedules) {
                JSONObject obj = new JSONObject();
                obj.put("id", s.getId());
                obj.put("startH", s.getStartHour());
                obj.put("startM", s.getStartMinute());
                obj.put("endH", s.getEndHour());
                obj.put("endM", s.getEndMinute());
                obj.put("enabled", s.isEnabled());
                array.put(obj);
            }
            prefs(c).edit().putString("schedules", array.toString()).apply();
        } catch (Exception e) {
            android.util.Log.e("PrefsManager", "Error saving schedules", e);
        }
    }

    public String formatTime(int hour, int minute) {
        int h12 = (hour % 12 == 0) ? 12 : hour % 12;
        String ampm = (hour < 12) ? "AM" : "PM";
        return String.format("%02d:%02d %s", h12, minute, ampm);
    }
}
