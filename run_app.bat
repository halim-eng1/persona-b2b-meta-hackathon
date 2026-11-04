:; MSYS_NO_PATHCONV=1 cmd.exe /c "$0" "$@"; exit $?
@echo off
setlocal enabledelayedexpansion

:: ============================================================================
:: Automated Build, Install, and Run Pipeline for Persona Android Project
:: ============================================================================

:: Ensure working directory is the project root (where this script resides)
cd /d "%~dp0"

:: Check if PowerShell is available to leverage the modern logging engine
where powershell >nul 2>&1
if %errorlevel% equ 0 (
    echo [INFO] Redirecting to unified logging runner: scripts\run_persona_with_logs.ps1
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run_persona_with_logs.ps1"
    exit /b %errorlevel%
)

:: Fallback legacy execution if powershell is not found
set "PACKAGE_NAME=com.example.persona"
set "MAIN_ACTIVITY=.MainActivity"

echo.
echo ============================================================================
echo  Persona App Pipeline: Build, Install, Run and Log (Legacy Fallback)
echo ============================================================================
echo.

:: ----------------------------------------------------------------------------
:: Step 1: Check ADB connection
:: ----------------------------------------------------------------------------
echo [1/5] Checking connected ADB devices...
adb get-state >nul 2>&1
if errorlevel 1 (
    echo.
    echo ============================================================================
    echo [WARNING] No active or authorized ADB device detected!
    echo ============================================================================
    adb devices
    exit /b 1
)

:: ----------------------------------------------------------------------------
:: Step 2: Build and install Debug APK
:: ----------------------------------------------------------------------------
echo [2/5] Building and installing debug APK: gradlew.bat installDebug...
call .\gradlew.bat installDebug
if errorlevel 1 exit /b %errorlevel%

:: ----------------------------------------------------------------------------
:: Step 3: Clean state
:: ----------------------------------------------------------------------------
adb shell am force-stop %PACKAGE_NAME%
adb logcat -c
adb shell am start -n %PACKAGE_NAME%/%MAIN_ACTIVITY%
if errorlevel 1 exit /b %errorlevel%

:: ----------------------------------------------------------------------------
:: Step 4: Stream logcat
:: ----------------------------------------------------------------------------
adb logcat | findstr /i "%PACKAGE_NAME% persona crash fatal AndroidRuntime"

endlocal
