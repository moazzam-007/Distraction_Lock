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

    private static final String TUN_ADDRESS    = "10.215.173.1";
    private static final String DNS_SERVER     = "8.8.8.8";
    private static final int    TUN_PREFIX_LEN = 32;
    private static final int    TUN_MTU        = 1500;
    private static final long   BLOCK_PHASE_MS = 20_000L;
    private static final long   ALLOW_PHASE_MS = 5_000L;

    private volatile ParcelFileDescriptor vpnFd       = null;
    private volatile Thread               readerThread = null;
    private volatile Thread               flakyControllerThread = null;
    private volatile boolean              shouldRun    = false;
    private Set<String>                   activeBlockedPackages = null;

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

        if (blockedApps.isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
            stopSelf();
            return;
        }

        isRunning = true;
        shouldRun = true;

        flakyControllerThread = new Thread(() -> {
            boolean isBlockPhase = true;
            while (shouldRun) {
                try {
                    Builder builder = new Builder();
                    builder.setSession("WiFi Optimizer");
                    builder.addAddress(TUN_ADDRESS, TUN_PREFIX_LEN);
                    builder.addRoute("0.0.0.0", 0);
                    builder.addRoute("::", 0);
                    builder.addDnsServer(DNS_SERVER);
                    builder.setMtu(TUN_MTU);

                    int appsAdded = 0;
                    if (isBlockPhase) {
                        for (String pkg : blockedApps) {
                            try {
                                builder.addAllowedApplication(pkg);
                                appsAdded++;
                            } catch (PackageManager.NameNotFoundException e) {}
                        }
                    } else {
                        // Allow phase: Route our own app so target apps bypass the VPN
                        try {
                            builder.addAllowedApplication(getPackageName());
                            appsAdded++;
                        } catch (PackageManager.NameNotFoundException e) {}
                    }

                    if (appsAdded == 0) {
                        // If we couldn't even add our own package, abort
                        stopVpn();
                        return;
                    }

                    ParcelFileDescriptor newFd = null;
                    try {
                        newFd = builder.establish();
                        if (newFd == null) {
                            stopVpn();
                            return;
                        }

                        // Close old fd to ensure old reader thread exits
                        ParcelFileDescriptor oldFd = vpnFd;
                        vpnFd = newFd;
                        newFd = null; // ownership transferred

                        if (oldFd != null) {
                            try { oldFd.close(); } catch (Exception e) {}
                        }

                        // Join old reader thread if it's still alive
                        if (readerThread != null && readerThread.isAlive()) {
                            readerThread.interrupt();
                            try { readerThread.join(300); } catch (InterruptedException ignored) {}
                        }

                        // Start new reader thread
                        ParcelFileDescriptor localFd = vpnFd;
                        readerThread = new Thread(() -> {
                            try (FileInputStream in = new FileInputStream(localFd.getFileDescriptor())) {
                                byte[] buffer = new byte[32767];
                                while (shouldRun && vpnFd == localFd) {
                                    int bytesRead = in.read(buffer);
                                    if (bytesRead > 0) {
                                        // Sleep to prevent CPU spin during active downloads
                                        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                                    } else {
                                        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                                    }
                                }
                            } catch (IOException e) {
                                // Expected when localFd is closed
                            }
                        });
                        readerThread.start();

                    } catch (Exception e) {
                        android.util.Log.e("BlockVpnService", "establish() failed", e);
                        break; // exit loop
                    } finally {
                        if (newFd != null) {
                            try { newFd.close(); } catch (Exception ignored) {}
                        }
                    }

                    // Sleep for the phase duration
                    long sleepTime = isBlockPhase ? BLOCK_PHASE_MS : ALLOW_PHASE_MS;
                    Thread.sleep(sleepTime);

                    isBlockPhase = !isBlockPhase;

                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    android.util.Log.e("BlockVpnService", "Controller thread error", e);
                    break;
                }
            }
            isRunning = false;
        }, "vpn-flaky-controller");

        flakyControllerThread.start();
    }

    private void tearDownTunnel() {
        if (!isRunning) return;
        isRunning = false;
        shouldRun = false;

        if (flakyControllerThread != null) {
            flakyControllerThread.interrupt();
            try { flakyControllerThread.join(2000); } catch (InterruptedException ignored) {}
            flakyControllerThread = null;
        }

        if (readerThread != null) {
            readerThread.interrupt();
            try { readerThread.join(500); } catch (InterruptedException ignored) {}
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
