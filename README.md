# AnyPods Pro 🎧⚡

**AnyPods Pro** brings the entire Apple AirPods Pro iOS experience and audio engineering suite to **any generic or third-party Bluetooth earbuds** on Android. 

Zero root required. Works with any standard Bluetooth earbuds, neckbands, and TWS (tested on Android 15 with generic earbuds like TRIGOR, Boat, Noise, Realme, OnePlus, etc.).

---

## ✨ Features Unlocked

### 1. 🛡️ Apple Noise Control Suite
- **Active Noise Cancellation (ANC):** Real-time acoustic noise suppression and echo cancellation software engine.
- **Transparency Mode:** Low-latency ambient audio loopback with speech-enhancement DSP.
- **Adaptive Audio:** Continuously samples environmental noise in real-time (dB decibel meter). Automatically transitions from Transparency in quiet environments (< 65 dB) to Active Noise Cancellation in noisy environments (> 65 dB).
- **Off Mode:** Raw passthrough with battery conservation.

### 2. 🧠 Audio Intelligence
- **Conversation Awareness:** Automatically detects when you start speaking. Instantly ducks background media playback volume by 65% and activates ambient transparency. Smoothly restores your music 2.5 seconds after you finish talking.
- **Personalized Volume:** Continuously adapts media volume up or down based on ambient noise conditions (office, commute, outdoors).
- **Loud Sound Reduction:** Built-in dynamic DSP compressor & peak limiter in Transparency mode that caps sudden harsh noises (car honks, construction clatter, dropped objects) to protect your hearing while keeping speech crystal clear.

### 3. 🌐 Spatial Audio (3D Surround Sound)
- **Fixed & Wide Stage:** Multi-channel 3D audio virtualizer engine that expands stereo sound into an immersive theatre-like soundstage right from your Android device.

### 4. 🎚️ Headphone Accommodations (iOS Audio/Visual Tuning)
- **Tuning Profiles:**
  - `Balanced Tone`: Flat reference curve with subtle clarity lift.
  - `Vocal Range`: Boosts 800 Hz – 3500 Hz for punchy, intelligible podcasts and vocals.
  - `Brightness`: Crisp high-frequency boost (> 3.5 kHz) for acoustic instruments and cymbals.
- **Boost Intensity:** Slight (+3 dB), Moderate (+6 dB), Strong (+9 dB).

### 5. ⚖️ Left / Right Audio Balance
- Smooth channel pan slider (-100% Left to +100% Right) to fine-tune audio balance for users with asymmetric hearing.

### 6. 🔋 Independent Battery Telemetry & Case Calibration
- **Triple Battery Status:** Real-time gauges for **Left Earbud**, **Charging Case**, and **Right Earbud**.
- **Live Charging Indicators (⚡):** Displays active charging lightning badge when earbuds or case are charging.
- **Case Telemetry Fine-Tuning:** Generic TWS earbuds do not broadcast proprietary case levels over open Bluetooth. AnyPods provides one-tap presets (25%, 45%, 70%, 100%), slider calibration, and charging toggles that persist across app launches.

### 7. 🔊 Ultra-Loud "Find My Earbuds" Beacon (+25dB Hardware Boost)
- Overrides system alarm & media volume streams to 100%.
- Generates a dual-frequency square-clipped acoustic siren (3.2 kHz / 4.0 kHz) tuned for human ear sensitivity.
- Attaches Android `LoudnessEnhancer` with **+2500 mB (+25 dB gain boost)** directly to the audio hardware session—making lost earbuds loud enough to hear outdoors or across entire rooms.
- Independent Left, Right, or Dual-bud chirp triggers.

### 8. 📱 Floating iOS AirPods Pop-up Card
- Floating dark/light bottom modal card showing animated AirPods, connection badge, device codec, and live Left/Right/Case battery dials.

---

## 🚀 Installation & Pre-built APK

A pre-built debug APK is available inside the repository:
- `release/anypods-v1.0.apk`

Or install directly via ADB:
```bash
adb install -r release/anypods-v1.0.apk
```

---

## 🛠️ Building from Source

```bash
git clone https://github.com/SHAURYASANYAL3/anypods.git
cd anypods
./gradlew assembleDebug
```

Built with **Jetpack Compose Material 3**, targeting modern Android (SDK 26 - 35+).

---

## 📜 Permissions Used
- `BLUETOOTH_CONNECT` & `BLUETOOTH_SCAN`: Detect connected Bluetooth headsets and read HFP/A2DP battery telemetry.
- `RECORD_AUDIO`: Required for Transparency pass-through, Adaptive Audio dB meter, and Conversation Awareness speech detection.
- `SYSTEM_ALERT_WINDOW`: Displays the iOS-style floating pop-up card over other applications.
- `FOREGROUND_SERVICE_CONNECTED_DEVICE`: Persistent background earbud tracking service.

---

## 📄 License
MIT License. Built for universal Bluetooth earbuds freedom.
