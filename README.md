# Archived as BentoBar is a more mature project with the same goal



# Centerpiece

**Centerpiece** is an extensible top-bar utility engineered specifically for **Googlebook OS** (Android-based desktop platform). It occupies the central "dead space" of the top system bar—positioned horizontally between the system launcher/clock elements on the far left and the system status tray (quick settings, battery, Wi-Fi, notifications) on the far right.

Centerpiece provides a native Googlebook OS-styled status item and widget layer on top of the desktop environment, complete with an IPC/AIDL extension interface, zero-ADB REST micro-daemon, and dynamic popover action sheets.

---

## 🌟 Key Features

1. **Pixel-Perfect Desktop Status Bar Docking**:
   - Anchors directly within the `statusBars()` insets region at `y = 0..statusBarHeight` (59px on 260 dpi).
   - Styled to match native Googlebook OS status capsules: `#1E1F22` obsidian slate with `1dp #303135` border and `#D3E3FD` soft lavender-blue active state.
   - Built with `FLAG_NOT_FOCUSABLE` and `WRAP_CONTENT` scoped touch bounds to ensure full mouse and touch pass-through for underlying application windows.

2. **Built-in Modular Widgets**:
   - **Centerpiece Icon (`builtin.logo`)**: Minimalist status bar capsule providing one-tap access directly to the Centerpiece Dashboard and Settings.
   - **System Monitor (`builtin.sysmon`)**: Real-time kernel CPU utilization derived from hardware C-state residency (`/sys/devices/system/cpu/cpu*/cpuidle`) and RAM usage via `ActivityManager`. Features OpenType tabular numerals (`tnum`) and fixed invariant capsule widths to prevent layout jitter, configurable metric visibility (`Both`, `CPU Only`, `RAM Only`), and one-tap launch to the native Task Manager.
   - **Keep Screen Awake (`builtin.awake`)**: Caffeine utility preventing display sleep via `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` and `PowerManager.WakeLock`. Features hollow coffee icon when inactive, solid coffee icon with infinity symbol (`☕ ∞`) when active indefinitely, and live timer countdowns (`☕ 30m`).

3. **Layout Customization**:
   - Customizable alignment: **Center**, **Left-bound**, or **Right-bound**.
   - Fine-tunable horizontal offset, vertical offset, and bar height adjustments.

4. **App Visibility & True Fullscreen Detection**:
   - **Maximized vs. True Fullscreen**: Distinguishes between maximized desktop windows (`top == 59`, where Centerpiece remains visible) and true immersive fullscreen apps/games (`top <= 10`, `height >= displayHeight - 35`, where Centerpiece hides cleanly).
   - **App Filtering**: Supports `DISABLED`, `BLACKLIST`, and `WHITELIST` modes with Desktop Launcher safety exemptions.

5. **Anchored Popover Dialog Coordinator**:
   - Clicking any status chip expands a floating popover card positioned directly beneath the item.
   - Features outside-touch tracking and toggle tap behavior: tapping an open chip dismisses its popover cleanly.

6. **Extensible Plugin & IPC Architecture (`CenterpiecePluginContract`)**:
   - **AIDL Service Interface (`ICenterpieceService.aidl`)**: Third-party Android apps can register status items, push live updates, and receive click callbacks.
   - **Zero-ADB REST Daemon (Port 7420)**: Built-in HTTP server on `0.0.0.0:7420` for shell scripts, CLI tools, and automation.
   - **ADB / Terminal Broadcast Interface**: Fast shell scripting support for developer workflows.

---

## 🚀 Centerpiece CLI

The included `./centerpiece` command line tool provides complete status bar management directly from the Linux terminal or shell scripts:

```bash
# Toggle Centerpiece top bar overlay on / off
./centerpiece toggle
./centerpiece show
./centerpiece hide

# System Monitor metrics display
./centerpiece sysmon both
./centerpiece sysmon cpu
./centerpiece sysmon ram
./centerpiece sysmon status

# Keep Screen Awake controls
./centerpiece awake toggle
./centerpiece awake 30m
./centerpiece awake off
./centerpiece awake status

# Layout & positioning controls
./centerpiece layout center
./centerpiece layout left --offset 24 --height 59
./centerpiece layout right --offset -16

# Visibility & fullscreen rules
./centerpiece visibility status
./centerpiece visibility fullscreen on|off
./centerpiece visibility mode blacklist
./centerpiece visibility add com.android.chrome
./centerpiece visibility remove com.android.chrome
./centerpiece visibility clear

# Register third-party status chips
./centerpiece register build "Build" "Passing" 850
./centerpiece update build "Build" "Failed" 850
./centerpiece unregister build
```

---

## 🌐 Desktop REST API (Port 7420)

Centerpiece embeds a zero-dependency HTTP micro-daemon listening on `0.0.0.0:7420`.

```bash
# Query active chips
curl http://localhost:7420/v1/chips

# Manage system monitor metric mode
curl -X POST http://localhost:7420/v1/sysmon -d '{"metricMode":"BOTH"}'

# Control screen awake utility
curl -X POST http://localhost:7420/v1/awake -d '{"durationMinutes":30}'
curl -X POST http://localhost:7420/v1/awake -d '{"active":false}'

# Adjust layout alignment and offsets
curl -X POST http://localhost:7420/v1/layout -d '{"alignment":"CENTER","xOffsetDp":0,"yOffsetDp":0,"heightDp":59}'

# App visibility rules
curl http://localhost:7420/v1/visibility
curl -X POST http://localhost:7420/v1/visibility -d '{"hideInFullscreen":true,"mode":"BLACKLIST","add":"com.android.chrome"}'
```

---

## 🔌 AIDL Plugin Protocol

Third-party apps bind to the service action:
```xml
<intent-filter>
    <action android:name="com.exaclast.centerpiece.SERVICE" />
</intent-filter>
```

### AIDL Interface Definition (`ICenterpieceService.aidl`)
```java
package com.exaclast.centerpiece;

import com.exaclast.centerpiece.CenterpieceItem;
import com.exaclast.centerpiece.ICenterpieceCallback;

interface ICenterpieceService {
    boolean registerItem(in CenterpieceItem item, in ICenterpieceCallback callback);
    boolean updateItem(in CenterpieceItem item);
    boolean unregisterItem(String itemId);
    List<CenterpieceItem> getItems();
}
```

---

## 🛠️ Build and Test

Built with Kotlin 2.3, Jetpack Compose, and Android SDK 36 on Googlebook OS.

### Run Unit Tests
```bash
./gradlew testDebugUnitTest --project-cache-dir="$HOME/.cache/projects/$(basename "$PWD")-$(pwd -P | sha1sum | cut -c1-8)/gradle"
```

### Build Debug APK
```bash
./gradlew assembleDebug --project-cache-dir="$HOME/.cache/projects/$(basename "$PWD")-$(pwd -P | sha1sum | cut -c1-8)/gradle"
```

### Deploy to Host Device via ADB
```bash
adb install -r app/build/outputs/apk/debug/Centerpiece-debug.apk
adb shell appops set com.exaclast.centerpiece SYSTEM_ALERT_WINDOW allow
adb shell pm grant com.exaclast.centerpiece android.permission.POST_NOTIFICATIONS
adb shell am start-foreground-service com.exaclast.centerpiece/.service.CenterpieceHostService
```

---

## 📄 License
See [LICENSE](LICENSE) for details.
