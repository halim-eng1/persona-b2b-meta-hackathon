#!/usr/bin/env bash
set -e

# Ensure working directory is the project root
cd "$(dirname "$0")"

PACKAGE_NAME="com.example.persona"
MAIN_ACTIVITY=".MainActivity"

echo ""
echo "============================================================================"
echo " Persona App Pipeline: Build, Install, Run and Log"
echo "============================================================================"
echo ""

# ----------------------------------------------------------------------------
# Step 1: Check ADB connection
# ----------------------------------------------------------------------------
echo "[1/5] Checking connected ADB devices..."
if ! adb get-state >/dev/null 2>&1; then
    echo ""
    echo "============================================================================"
    echo "[WARNING] No active or authorized ADB device detected!"
    echo "============================================================================"
    adb devices
    echo ""
    echo "Please make sure:"
    echo " 1. Your Android phone is connected via USB or Wi-Fi pairing."
    echo " 2. USB debugging is enabled in Developer Options on the device."
    echo " 3. You have authorized this computer on your device authorization prompt."
    echo ""
    exit 1
fi

DEVICE_FOUND=$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')
if [ -z "$DEVICE_FOUND" ]; then
    echo ""
    echo "============================================================================"
    echo "[WARNING] Device found but not ready: status may be unauthorized or offline."
    echo "============================================================================"
    adb devices
    exit 1
fi

echo "[OK] Device connected and ready: $DEVICE_FOUND"
echo ""

# ----------------------------------------------------------------------------
# Step 2: Build and install Debug APK
# ----------------------------------------------------------------------------
echo "[2/5] Building and installing debug APK..."
if [ -f "./gradlew" ]; then
    ./gradlew installDebug
else
    ./gradlew.bat installDebug
fi
echo "[OK] Build and install completed successfully."
echo ""

# ----------------------------------------------------------------------------
# Step 3: Clean state - Force stop any running instance
# ----------------------------------------------------------------------------
echo "[3/5] Force-stopping existing instance of $PACKAGE_NAME..."
adb shell am force-stop "$PACKAGE_NAME"
echo "[OK] Stopped."
echo ""

# ----------------------------------------------------------------------------
# Step 4: Clear previous logcat buffer and Launch activity
# ----------------------------------------------------------------------------
echo "[4/5] Clearing logcat buffer and launching main activity..."
adb logcat -c
adb shell am start -n "$PACKAGE_NAME/$MAIN_ACTIVITY"
echo "[OK] App launched."
echo ""

# ----------------------------------------------------------------------------
# Step 5: Stream live logs: Dynamic PID filtering with broad fallback
# ----------------------------------------------------------------------------
echo "[5/5] Initializing live log streaming..."

# Allow up to 3 seconds for the Android OS to spawn the process and assign PID
APP_PID=""
for i in 1 2 3 4 5 6; do
    APP_PID=$(adb shell pidof -s "$PACKAGE_NAME" 2>/dev/null | tr -d '\r\n')
    if [ -n "$APP_PID" ]; then
        break
    fi
    sleep 0.5
done

if [ -n "$APP_PID" ]; then
    echo "[INFO] Streaming logs for PID: $APP_PID - Package: $PACKAGE_NAME"
    echo "[INFO] Press Ctrl+C to stop log streaming at any time."
    echo "----------------------------------------------------------------------------"
    adb logcat --pid="$APP_PID" || true
    echo ""
    echo "[INFO] Process $APP_PID has exited or logcat stream was interrupted."
    echo "[INFO] Switching to broad fallback filter for crashes and runtime errors..."
    echo "----------------------------------------------------------------------------"
else
    echo "[WARN] Could not resolve PID for $PACKAGE_NAME."
    echo "[INFO] Falling back to broad log filtering..."
    echo "----------------------------------------------------------------------------"
fi

adb logcat | grep -Ei "($PACKAGE_NAME|persona|crash|fatal|AndroidRuntime)"
