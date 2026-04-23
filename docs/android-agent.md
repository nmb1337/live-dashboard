# Android Agent (Consent First)

This Android app is a non-root client for Live Dashboard.
It reports selected device activity only after explicit user consent.

## What it can report

- Current foreground app package name and app name
- Battery percentage and charging state (optional)
- Network type as contextual metadata

## What it does not do

- No root required
- No key logging
- No message/content extraction from other apps
- No hidden startup without user-configured opt-in

## Required permissions

- Usage access (`PACKAGE_USAGE_STATS`) for foreground app detection
- Foreground service for continuous heartbeat upload
- Internet/network state for API upload
- Notification permission on Android 13+

## Backend endpoints used

- `POST /api/consent`
- `POST /api/report`

The app is compatible with consent enforcement mode:

- If server sets `REQUIRE_EXPLICIT_CONSENT=true`, consent is uploaded before reporting.

## Local build

Open this folder in Android Studio:

- `packages/android-agent`

Then run:

1. Sync Gradle project.
2. Build release APK from `app` module.
3. Output is generated under:
   - `app/build/outputs/apk/release/app-release.apk`

## Runtime setup

1. Fill server URL and token.
2. Grant usage access.
3. Confirm consent checkbox.
4. Save settings and start tracking.
