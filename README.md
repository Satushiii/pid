# Quadcopter 3-Axis Cascade PID Controller (Android + USB OTG)

Native Kotlin Android application implementing a 3-Axis (Pitch, Roll, Yaw) Digital Cascade PID controller with real-time USB Serial output to Arduino and STM32 boards.

## Features
- **3-Axis Unified UI:** Configure Pitch, Roll, and Yaw on a single scrollable screen.
- **Dual-Stage Cascade PID per Axis:**
  - **Inner PID (Angle Control):** Computes desired angular rate from angle error with configurable $K_p, K_i, K_d$, sampling time $T_s$ (ms), and min/max saturation limits.
  - **Outer PID (Angular Rate -> Torque Control):** Computes motor torque from rate error with configurable $K_p, K_i, K_d$, sampling time $T_s$ (ms), and min/max torque saturation limits.
- **Live Telemetry Windows:** Displays real-time Angle ($^\circ$) and Angular Speed ($^\circ/\text{s}$) alongside live error and torque outputs.
- **USB OTG Serial Streaming:** Connects directly to Arduino (CH340, FTDI, CP210x, ATmega16U2) and STM32 (Native USB CDC Virtual COM Port) to transmit `<pitchTorque,rollTorque,yawTorque>\n` packets.

## Building the APK via GitHub Actions
1. Create a new GitHub repository.
2. Extract this `.zip` archive and push/upload all files (including the hidden `.github/workflows/build-apk.yml` folder) to the repository root.
3. Open the **Actions** tab in your GitHub repository.
4. Once the **Build Quadcopter PID APK** workflow completes, download **`Quadcopter-Cascade-PID-APK`** from the **Artifacts** section at the bottom of the run page.
