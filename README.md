# Google Maps Android

A minimal Android application that opens Google Maps in an embedded WebView.

## Requirements
- Android Studio with Android SDK 36
- JDK 17
- Gradle 9.3.1

## Build
From the project root, run:

```bash
gradle assembleDebug
```

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

This project is a focused Google Maps WebView shell without Photos sync, OAuth token handling, account database, background workers, or Firebase.
