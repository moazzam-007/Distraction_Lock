# 📶 WiFi Optimizer — Android App

> A smart, scheduled network management app for Android that silently controls which apps can access the internet during specific time windows — without root access.

[![Android](https://img.shields.io/badge/Platform-Android-green?logo=android)](https://android.com)
[![Java](https://img.shields.io/badge/Language-Java-orange?logo=openjdk)](https://openjdk.org)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-21%20(Lollipop)-blue)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)

---

## ✨ Features

- 🚫 **Selective App Blocking** — Block any installed app's internet access; choose from a searchable list
- ⏰ **Dual Schedule Windows** — Configure two daily time slots (e.g. 3–5 PM and 11 PM–3 AM)
- 🔕 **Silent & Invisible** — Blocked apps show "no internet" without any popup or overlay
- 🔄 **Boot Persistence** — Alarms and VPN state automatically restore after device restart
- 🧭 **Onboarding Wizard** — 3-step first-run setup (Permission → App Selection → Schedule)
- ✏️ **Editable Schedule** — Change block windows anytime with a built-in time picker
- 📋 **App Management Screen** — Add or remove apps from the blocklist at any time
- 🌙 **Dark Theme UI** — Clean, modern Material Design interface

---

## 🏗️ Architecture & Tech Stack

| Component | Technology |
|---|---|
| Language | **Java** (Android SDK) |
| Network Control | `android.net.VpnService` — local TUN interface |
| Scheduling | `AlarmManager` with `setExactAndAllowWhileIdle()` |
| Boot Persistence | `BroadcastReceiver` for `BOOT_COMPLETED` |
| UI | Material Design 3, `RecyclerView`, `TimePickerDialog` |
| Storage | `SharedPreferences` (Singleton pattern) |
| Background Work | `Thread` + `Handler` for non-blocking app list loading |

---

## 🔧 How It Works

```
User selects apps + sets schedule
        ↓
AlarmManager fires at scheduled time
        ↓
ScheduleReceiver starts BlockVpnService
        ↓
VpnService creates local TUN interface
        ↓
Only blocked apps route through tunnel
        ↓
Packets are read and discarded (black-hole)
        ↓
Blocked apps get no response → appear offline
        ↓
All other apps bypass VPN → unaffected
```

**No root access required.** Uses Android's official `VpnService` API with `addAllowedApplication()` to isolate only the selected apps into the tunnel — all other apps connect normally.

---

## 📁 Project Structure

```
app/src/main/java/com/wifioptimizer/
│
├── PrefsManager.java         ← Singleton: all SharedPreferences operations
├── AppInfo.java              ← POJO/Model: represents an installed app
│
├── MainActivity.java         ← Main screen: status, navigation
├── OnboardingActivity.java   ← First-run 3-step setup wizard
├── AppSelectionActivity.java ← Choose which apps to block
├── ScheduleEditActivity.java ← Edit blocking time windows
│
├── AppAdapter.java           ← RecyclerView Adapter (Adapter pattern)
│
├── BlockVpnService.java      ← Core VPN service (packet drop)
├── ScheduleManager.java      ← AlarmManager scheduling utility
├── ScheduleReceiver.java     ← Alarm trigger handler
└── BootReceiver.java         ← Boot persistence receiver
```

---

## 🎨 Java Design Patterns Used

- **Singleton** — `PrefsManager.getInstance()` for thread-safe global settings access
- **Adapter Pattern** — `AppAdapter extends RecyclerView.Adapter` with inner `ViewHolder`
- **Model/POJO** — `AppInfo.java` for clean data separation
- **Observer (Callback)** — `OnAppToggleListener` interface for RecyclerView events
- **Strategy** — `ScheduleManager` static utility class with interchangeable alarm strategies
- **Template Method** — Android lifecycle callbacks (`onStartCommand`, `onRevoked`)

---

## 🚀 Build & Install

### Option 1 — GitHub Actions (Recommended, no Android Studio needed)
1. Fork this repository
2. Push any change to `main` branch
3. Go to **Actions** tab → `Build WiFiOptimizer APK`
4. Download `WiFiOptimizer-debug-APK.zip` from the Artifacts section
5. Extract and install `app-debug.apk` on your Android device

### Option 2 — Android Studio
```bash
git clone https://github.com/yourusername/wifi-optimizer.git
cd wifi-optimizer
# Open in Android Studio → Run on device
```

---

## 📱 First-Time Setup

1. Install the APK and open the app
2. **Step 1**: Tap "Grant Network Permission" → Allow in system dialog
3. **Step 2**: Tap "Choose Apps" → Select apps to block (Instagram, YouTube, etc.)
4. **Step 3**: Review schedule → optionally customize → tap "Start Optimizing"
5. Done! The app now manages the network automatically on schedule.

> **Note**: On Xiaomi/MIUI devices, go to Settings → App Info → Battery → set to "No restrictions" for best reliability.

---

## ⚙️ Permissions Used

| Permission | Why |
|---|---|
| `BIND_VPN_SERVICE` | Core VPN functionality |
| `RECEIVE_BOOT_COMPLETED` | Restore alarms after reboot |
| `FOREGROUND_SERVICE` | Keep VPN service alive |
| `SCHEDULE_EXACT_ALARM` | Precise on/off timing |
| `POST_NOTIFICATIONS` | VPN status notification (Android 13+) |

---

## 📝 License

MIT License — feel free to use, modify, and distribute.

---

*Built with ❤️ using Java & Android SDK*
