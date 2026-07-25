# 📶 WiFi Optimizer (Distraction Lock) — Android App

> A smart, scheduled network management app for Android that silently controls which apps can access the internet during specific time windows — without root access.

[![Android](https://img.shields.io/badge/Platform-Android-green?logo=android)](https://android.com)
[![Java](https://img.shields.io/badge/Language-Java-orange?logo=openjdk)](https://openjdk.org)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-21%20(Lollipop)-blue)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-lightgrey)](LICENSE)

---

## 📖 The Problem & The Solution

**The Problem:** Today, screen addiction isn't just a problem for kids—adults and parents are often even more addicted to scrolling through Reels, Shorts, and useless news feeds. When younger people suggest they reduce their screen time, the advice is often dismissed with, *"We are adults, we do what we want."* 

**The Solution:** WiFi Optimizer acts as your silent digital guardian. You can install it on your parents' phone (or anyone's phone) and set up specific scheduled time slots. During those slots, highly addictive apps (like Instagram, YouTube, etc.) will have their internet access blocked. 

**The best part?** The block happens silently in the background without any popups or warnings. The apps simply appear to be "offline" or loading forever. Meanwhile, the rest of the phone and all other important apps work perfectly fine!

*You can even use it to overcome your own addiction!* Set a schedule for your most distracting apps and get your focus back.

---

## ✨ Key Features

- 🚫 **Selective Stealth Blocking** — Block internet access for specific addictive apps while leaving the rest of the phone online. No annoying popups or overlays!
- ⏰ **Dynamic Schedule Windows** — Configure multiple daily time slots (e.g., 3–5 PM and 11 PM–3 AM) for blocking to automatically activate.
- 🔕 **Invisible & Silent** — Blocked apps simply show "no internet". The user won't easily know they are being intentionally blocked.
- 🔄 **Bulletproof Auto-Revival** — Uses a background Watchdog. Even if a system booster force-kills the app, it automatically revives itself to ensure the schedule is enforced.
- 🧭 **Clean Slate Setup** — No hardcoded apps or schedules. You choose exactly what to block and when.
- 🌙 **Dark Theme UI** — Clean, modern Material Design interface.

---

## 🗺️ Roadmap / Future Features

- **Anti-Uninstall Protection**: Once a scheduled block starts, the app cannot be uninstalled or force-stopped until the schedule ends (Perfect for enforcing self-discipline!).
- **Localization**: Multi-language support.
- **Enhanced Icon Caching**: Smoother scrolling on the app selection screen.

---

## 🤝 Open Source & Contributing

**You are completely free to use this code, fork it, and add features!** 
Whether you want to build upon the core VPN engine, add the anti-uninstall feature, or redesign the UI, you are welcome to contribute. Feel free to submit Pull Requests or open Issues!

---

## 🏗️ Architecture & Tech Stack

| Component | Technology |
|---|---|
| Language | **Java** (Android SDK) |
| Network Control | `android.net.VpnService` — local TUN interface |
| Scheduling | `AlarmManager` with `setInexactRepeating()` |
| Auto-Revival | Background `WatchdogReceiver` |
| Boot Persistence | `BroadcastReceiver` for `BOOT_COMPLETED` |
| UI | Material Design 3, `RecyclerView`, `TimePickerDialog` |
| Storage | `SharedPreferences` (Singleton pattern) |
| Background Work | `Thread` + `Handler` for non-blocking app list loading |

---

## 🔧 How It Works (Technical)

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
├── WatchdogReceiver.java     ← Auto-revival watchdog
└── BootReceiver.java         ← Boot persistence receiver
```

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
4. **Step 3**: Tap the Add icon to create a schedule → customize time → tap Save
5. Done! The app now manages the network automatically on schedule.

> **Note**: On Xiaomi/MIUI devices, go to Settings → App Info → Battery → set to "No restrictions" for best reliability.

---

## ⚙️ Permissions Used

| Permission | Why |
|---|---|
| `BIND_VPN_SERVICE` | Core VPN functionality |
| `RECEIVE_BOOT_COMPLETED` | Restore alarms after reboot |
| `FOREGROUND_SERVICE` | Keep VPN service alive |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Required for Android 14+ VPN services |
| `SCHEDULE_EXACT_ALARM` | Precise on/off timing |
| `POST_NOTIFICATIONS` | VPN status notification (Android 13+) |

---

## 📝 License

MIT License — feel free to use, modify, and distribute.

---

*Built with ❤️ using Java & Android SDK*
