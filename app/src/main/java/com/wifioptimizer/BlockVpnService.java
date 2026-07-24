package com.wifioptimizer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import androidx.core.app.NotificationCompat;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Set;

/**
 * BlockVpnService — Core VPN service that silently drops network traffic
 * for user-selected apps during scheduled time windows.
 *
 * Mechanism:
 *  1. Creates a local TUN interface using Android's VpnService API.
 *  2. Uses addAllowedApplication() so ONLY blocked apps route through the tunnel.
 *  3. A background thread reads packets from the TUN interface and discards them.
 *  4. Blocked apps get no response → appear as "no internet connection".
 *  5. All other apps bypass the VPN completely → unaffected.
 */
public class BlockVpnService extends VpnService {

    public static final String ACTION_START = "com.wifioptimizer.START_VPN";
    public static final String ACTION_STOP  = "com.wifioptimizer.STOP_VPN";

    private static final int    NOTIF_ID   = 1001;
    private static final String CHANNEL_ID = "wifiopt_service";

    // Visible to MainActivity for UI status updates
    public static volatile boolean isRunning = false;

    private ParcelFileDescriptor vpnFd       = null;
    private Thread               readerThread = null;
    private volatile boolean     shouldRun    = false;
    private Set<String>          activeBlockedPackages = null;

    // ─── Service Lifecycle ─────────────────────────────────────────────────────

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
        }

        Set<String> newBlockedPackages = PrefsManager.getInstance().getBlockedApps(this);
        
        if (isRunning) {
            // If the VPN is already running and the list of blocked apps hasn't changed, ignore the start command
            if (activeBlockedPackages != null && activeBlockedPackages.equals(newBlockedPackages)) {
                return START_STICKY;
            }
            // Restart tunnel to apply new blocked apps list
            tearDownTunnel();
        }
        startVpn(newBlockedPackages);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }

    @Override
    public void onRevoke() {
        stopVpn();
        PrefsManager.getInstance().setVpnPermissionGranted(this, false);
        super.onRevoke();
    }

    // ─── VPN Core Logic ────────────────────────────────────────────────────────

    private void startVpn(Set<String> blockedApps) {

        setupNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        activeBlockedPackages = new java.util.HashSet<>(blockedApps);

        Builder builder = new Builder();
        builder.setSession("WiFi Optimizer");
        builder.addAddress("10.215.173.1", 32);  // Fake TUN local IP
        builder.addRoute("0.0.0.0", 0);           // Capture all IPv4 traffic
        builder.addRoute("::", 0);                // Capture all IPv6 traffic
        builder.addDnsServer("8.8.8.8");
        builder.setMtu(1500);

        // addAllowedApplication: ONLY these apps route through VPN tunnel.
        // All other apps bypass VPN → normal internet unaffected.
        int appsAdded = 0;
        for (String pkg : blockedApps) {
            try {
                builder.addAllowedApplication(pkg);
                appsAdded++;
            } catch (PackageManager.NameNotFoundException e) {
                // App not installed on this device — skip silently
            }
        }

        if (appsAdded == 0) {
            // No selected apps are installed — nothing to block
            stopForeground(true);
            stopSelf();
            return;
        }

        try {
            vpnFd = builder.establish();
        } catch (Exception e) {
            stopSelf();
            return;
        }

        if (vpnFd == null) {
            stopForeground(true);
            stopSelf();
            return;
        }

        isRunning = true;
        shouldRun = true;

        // Capture fd reference locally to avoid NPE if stopVpn() nulls it on another thread
        ParcelFileDescriptor localFd = vpnFd;
        if (localFd == null) return;

        // Background thread: reads packets from TUN interface and discards them.
        // Blocked apps send packets → TUN receives → we discard → no response → "no internet".
        readerThread = new Thread(() -> {
            try (FileInputStream in = new FileInputStream(localFd.getFileDescriptor())) {
                byte[] buffer = new byte[32767];
                while (shouldRun) {
                    // Blocking read — thread waits here until a packet arrives
                    int length = in.read(buffer);
                    if (length < 0) break;
                    // Packet is immediately dropped (not forwarded anywhere)
                }
            } catch (IOException e) {
                // IO error or interrupted by stopVpn() closing the stream
            } finally {
                isRunning = false; // Keep UI in sync if thread exits unexpectedly
            }
        }, "vpn-packet-drop-thread");

        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void tearDownTunnel() {
        if (!isRunning) return;
        isRunning = false;
        shouldRun = false;

        // The thread blocks on vpnFd.read(). 
        // Closing the fd below will unblock it and cause an IOException.
        if (readerThread != null) {
            readerThread = null;
        }

        try {
            if (vpnFd != null) {
                vpnFd.close();
                vpnFd = null;
            }
        } catch (IOException ignored) {}
    }

    private void stopVpn() {
        tearDownTunnel();
        stopForeground(true);
        stopSelf();
    }

    // ─── Notification ──────────────────────────────────────────────────────────

    private void setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "WiFi Optimizer",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Network optimization service");
            channel.setShowBadge(false);
            channel.enableVibration(false);
            channel.enableLights(false);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                
        Intent stopIntent = new Intent(this, BlockVpnService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Network Optimization Active")
                .setContentText("Selected apps are currently blocked.")
                .setSmallIcon(android.R.drawable.ic_secure)
                .setContentIntent(pi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
