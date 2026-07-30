# Add project specific ProGuard rules here.
# Targeted keep rules for background components
-keep class com.wifioptimizer.BlockVpnService { *; }
-keep class com.wifioptimizer.ScheduleReceiver { *; }
-keep class com.wifioptimizer.BootReceiver { *; }
-keep class com.wifioptimizer.WatchdogReceiver { *; }
-keep class com.wifioptimizer.KeepAliveAccessibilityService { *; }

# Keep Schedule model for JSON deserialization
-keep class com.wifioptimizer.Schedule { *; }
