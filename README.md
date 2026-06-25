# Mural Magic — Execution Instructions

An Android app that recognises a 2D mural through the camera and plays a
pre-rendered animation for it ("trigger and play"). Built with ARCore
Augmented Images.

---

## 1. Requirements

- **Android Studio** (recent stable version) with the Android SDK installed.
- **A physical Android device** that supports ARCore:
  - Android 7.0 (API 24) or higher.
  - "Google Play Services for AR" installed (the device prompts to install/update
    it on first launch if needed — this needs internet **once**).
  - ARCore does **not** run on the standard Android emulator, so a real device is
    required. (Developed and tested on a Samsung Galaxy S10 Lite.)
- A USB cable, or the prebuilt APK (see Section 4) for offline install.
- Internet connection **the first time only**, so Gradle can download the build
  tools and dependencies. After that the app runs fully offline.

---

## 2. Open the project

**From the repository:**
```
git clone <repository-url>
```
Then in Android Studio: **File → Open** and select the cloned folder.

**From the zip:** extract it, then **File → Open** and select the extracted
folder.

On first open, Android Studio runs a **Gradle sync** automatically (it shows a
progress bar at the bottom). Let it finish — this downloads the Gradle wrapper
and dependencies and needs internet. `local.properties` (your local SDK path) is
generated automatically; you don't need to create it.

---

## 3. Run on a connected device

1. On the phone, enable **Developer options** and **USB debugging**
   (Settings → About phone → tap "Build number" 7 times, then
   Settings → Developer options → USB debugging).
2. Connect the phone by USB and accept the "Allow USB debugging" prompt.
3. In Android Studio, pick the device in the device dropdown (top toolbar).
4. Press **Run** (the green ▶) or `Shift + F10`.
5. On first launch, **grant the camera permission** when asked.

---

## 4. Build a standalone APK (for the demo, no computer needed)

To run the app independently of Android Studio:

1. **Build → Build Bundle(s) / APK(s) → Build APK(s).**
2. When it finishes, click **locate** in the notification to find
   `app-debug.apk` (under `app/build/outputs/apk/debug/`).
3. Copy that APK to the phone and open it to install (the device may need
   "Install unknown apps" enabled for the file manager).
4. Make sure "Google Play Services for AR" is already installed on the demo
   device beforehand (while you have internet).

Once installed, the app needs no computer and no internet — the image database
and the videos are bundled inside the app.

---

## 5. Using the app

1. The app opens on the **home screen**. Tap **Start scanning** to open the
   camera.
2. Tap the **Start scanning** button on the camera screen, then point the phone
   at a mural.
3. When a mural is recognised, its animation plays full screen.

**Testing without the physical murals:** the nine reference images are bundled at
`app/src/main/assets/ar/1.jpg` … `9.jpg`. Display them on a monitor or print them
out, then point the phone at them to trigger the matching videos.

---

## 6. Project structure (quick map)

- `HomeActivity` — landing screen; launches the scanner.
- `MainActivity` — camera + ARCore image recognition (renders the camera feed
  via `GLSurfaceView`, loads the augmented-image database from assets, and
  launches the video on detection).
- `VideoActivity` — full-screen playback of the matched video.
- `app/src/main/assets/ar/` — the nine target mural images.
- Videos — bundled locally (played by `VideoActivity`).

---

## 7. Troubleshooting

- **"ARCore not supported" / app closes on the scan screen** — the device isn't
  ARCore-capable, or "Google Play Services for AR" isn't installed. Install it
  from the Play Store.
- **Camera is black or permission denied** — grant the camera permission in
  Settings → Apps → Mural Magic → Permissions, then reopen.
- **Device not listed in Android Studio** — check the USB cable, that USB
  debugging is on, and that you accepted the debugging prompt on the phone.
- **Gradle sync fails** — confirm internet access for the first build; then use
  **File → Sync Project with Gradle Files**.
- **A mural won't trigger** — low-detail artwork (large flat colour areas) has
  weak visual features and may not be recognised reliably; try better lighting
  and a straight-on angle.
