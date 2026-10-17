# Reminder 2.0 (Kotlin + Jetpack Compose)

## Build the APK (pick one)
A. Android Studio (easiest): File > Open this folder, wait for Gradle sync, then Build > Build APK(s).
B. Command line: JDK 17 + Android SDK (platform 35); run `gradle wrapper --gradle-version 8.9` once, then `./gradlew assembleRelease`.
   Output: app/build/outputs/apk/release/app-release.apk
C. No local tools: upload this folder (including the hidden .github folder) to a GitHub repo, then Actions > Build APK > Run workflow. Download the APK from Artifacts.

## Install
Copy the APK to the phone, open it, allow "Install unknown apps" when asked.

## Permissions
- Notifications (Android 13+): required, requested on first launch.
- Full-screen alerts (Android 14+): optional, for the alarm screen over the lock screen.
- Exact alarms: not needed (AlarmManager.setAlarmClock is exact and Doze-exempt).
- Samsung/Xiaomi etc.: if reminders arrive late, set the app's battery usage to "Unrestricted".

## Features
Daily, weekday and one-time repeats; snooze; "keep alerting until dismissed"; per-reminder colors;
quick-add chips; undo delete; duplicate; light/dark/AMOLED; accent colors or Material You; English and Persian (RTL);
12/24-hour override; custom alert sound; share/import reminders as text; test notification.
