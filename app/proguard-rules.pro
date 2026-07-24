# Add project specific ProGuard rules here.
# Targeted keep rules for background components
-keep class com.wifioptimizer.BlockVpnService { *; }
-keep class com.wifioptimizer.ScheduleReceiver { *; }
-keep class com.wifioptimizer.BootReceiver { *; }

# Keep Schedule model for JSON deserialization
-keep class com.wifioptimizer.Schedule { *; }
