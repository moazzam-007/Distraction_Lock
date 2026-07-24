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

    // ─── Service Lifecycle ─────────────────────────────────────────────────────

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopVpn();
            return START_NOT_STICKY;
        }
        startVpn();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }

    @Override
    public void onRevoked() {
        // Called when user revokes VPN permission from system settings
        stopVpn();
        super.onRevoked();
    }

    // ─── VPN Core Logic ────────────────────────────────────────────────────────

    private void startVpn() {
        if (isRunning) return;

        setupNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        // Read blocked apps dynamically from user preferences (not hardcoded)
        Set<String> blockedApps = PrefsManager.getInstance().getBlockedApps(this);

        Builder builder = new Builder();
        builder.setSession("WiFi Optimizer");
        builder.addAddress("10.215.173.1", 32);  // Fake TUN local IP
        builder.addRoute("0.0.0.0", 0);           // Capture all IPv4 traffic
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
            stopSelf();
            return;
        }

        isRunning = true;
        shouldRun = true;

        // Capture fd reference locally to avoid NPE if stopVpn() nulls it on another thread
        final ParcelFileDescriptor localFd = vpnFd;

        // Background thread: reads packets from TUN interface and discards them.
        // Blocked apps send packets → TUN receives → we discard → no response → "no internet".
        readerThread = new Thread(() -> {
            try (FileInputStream stream = new FileInputStream(localFd.fileDescriptor)) {
                byte[] buffer = new byte[32767];
                while (shouldRun) {
                    int bytesRead = stream.read(buffer);
                    if (bytesRead < 0) break; // EOF — pipe closed, exit cleanly
                    // Packet intentionally discarded — not forwarded anywhere
                }
            } catch (InterruptedException e) {
                // Normal shutdown — interrupted by stopVpn()
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                // IO error — exit thread cleanly
            } finally {
                isRunning = false; // Keep UI in sync if thread exits unexpectedly
            }
        }, "vpn-packet-drop-thread");

        readerThread.setDaemon(true);
        readerThread.start();
    }

    private void stopVpn() {
        shouldRun = false;
        isRunning  = false;

        if (readerThread != null) {
            readerThread.interrupt();
            readerThread = null;
        }

        if (vpnFd != null) {
            try { vpnFd.close(); } catch (IOException ignored) {}
            vpnFd = null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
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
        PendingIntent tapIntent = PendingIntent.getActivity(
                this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WiFi Optimizer")
                .setContentText("Network optimization active")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setContentIntent(tapIntent)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
