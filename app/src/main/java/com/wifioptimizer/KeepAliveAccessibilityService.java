package com.wifioptimizer;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * A minimal Accessibility Service designed solely to act as a resurrection hook.
 * It does NOT monitor any UI events or read screen content (configured in XML).
 * If the app is killed by a memory booster, the Android OS will automatically 
 * restart this service, allowing us to restore our background alarms and VPN.
 */
public class KeepAliveAccessibilityService extends AccessibilityService {

    private static final String TAG = "KeepAliveService";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service Created - Resurrection successful");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "Service Connected - Restoring alarms and VPN state");
        
        // When the service is restarted by the OS after a kill, 
        // we restore our schedules and VPN state.
        if (PrefsManager.getInstance().isEnabled(this)) {
            ScheduleManager.scheduleAll(this);
            ScheduleManager.syncVpnState(this);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // We do nothing. This is a stub.
    }

    @Override
    public void onInterrupt() {
        // Do nothing.
    }
    
    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "Service Unbound");
        return super.onUnbind(intent);
    }
}
