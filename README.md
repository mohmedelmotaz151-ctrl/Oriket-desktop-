# Oriket Desktop — Windows 11-inspired Android app

Oriket Desktop is a React + TypeScript + Vite desktop environment with a Windows 11-inspired shell. The Android build wraps the same web application with **Capacitor**; it does not replace the interface with a separate native UI.

## Preserved application features
- Windows 11 desktop shell, lock screen, windows, taskbar, Start Menu and quick settings.
- Existing apps, icons, wallpapers, media player, file explorer and virtual C: drive.
- Games module, including the piano and its built-in melodies and recordings.
- Existing localStorage keys and data structures in the React app.

## Android identity and update compatibility
- Android application ID remains \`com.orkeit.desktop\`.
- The Android activity is kept in landscape orientation for the desktop-style interface.
- The direct-download APK uses the same original debug signing key as the previously published 1.0.135 APK. The workflow restores the existing GitHub Actions cache key \`orkeit-desktop-debug-signing-key-v1\`.
- The workflow fails rather than generating a new signing key if the original key is unavailable. If the cache expires, add \`ANDROID_DEBUG_KEYSTORE_BASE64\` in GitHub repository Actions secrets; it must contain the original keystore, not a newly generated one. The original key password and alias remain \`android\` and \`androiddebugkey\`.
- Each build uses a versionCode higher than the previous direct APK (baseline 135), allowing Android to recognize it as an update.
- Capacitor WebView data is kept in the same app sandbox across normal in-place APK updates. Do not uninstall the app to update it.

## Android Linux terminal (Command)
- On Android, Command uses the official Termux RUN_COMMAND interface to execute commands in the real Termux user environment and display returned output and exit codes.
- Install the current Termux app from F-Droid: https://f-droid.org/packages/com.termux/
- Open Termux once, then run: `mkdir -p ~/.termux && echo "allow-external-apps=true" >> ~/.termux/termux.properties`; restart Termux afterward.
- Grant Oriket Desktop the **Run commands in Termux environment** permission in Android app settings.
- Install Nmap from Termux with `pkg update && pkg install nmap`.
- This bridge runs non-interactive commands and returns their output; interactive full-screen TTY programs (for example editors such as nano, top, or htop) should be opened directly in Termux.
- Command includes quick actions for Linux identity/kernel/files, Android device and storage information, network interfaces/routes, Python installation and a Python runtime check, plus security-tool installation and availability checks.
- Optional Termux packages: install Python with `pkg install python`; install the security toolkit with `pkg install nmap dnsutils whois openssl curl netcat-openbsd`.
- Network diagnostics include `ip`, `ping`, `nslookup`, `traceroute`, `curl` and Nmap. Nmap scans require explicit authorization; the app asks for confirmation before running Nmap commands.
- Only scan networks and devices that you own or are explicitly authorized to test.

## Build locally

\`\`\`bash
npm install
npm run android:prepare
npm run build
npx cap add android
npx cap sync android
node scripts/prepare-capacitor-android.mjs
cd android
./gradlew assembleDebug
\`\`\`

The APK is created at \`android/app/build/outputs/apk/debug/app-debug.apk\`.

For subsequent builds, skip \`npx cap add android\` if the Android platform folder already exists, then run \`npm run android:build\`.

## In-app update flow
The Update app shortcut checks the GitHub release tagged \`latest\`, reads the APK asset and versionCode, compares it with the installed Android build, and opens the APK download in the Android browser after confirmation. After the download completes, open the downloaded APK to install it over the existing app. Android may ask the browser to allow installation of unknown apps.

## GitHub Actions
Every push to \`main\` and manual workflow run builds a Capacitor APK, uploads a 30-day Actions artifact, and updates the \`latest\` direct-download release. The build uses Node 22, Java 21 and Android API 36.

The web build remains available with \`npm run dev\` and \`npm run build\`.
