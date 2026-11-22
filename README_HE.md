# מפות Google לאנדרואיד

אפליקציית Android מינימלית המציגה את Google Maps בתוך WebView.

## דרישות
- Android Studio עם Android SDK 36
- JDK 17
- Gradle 9.3.1

## בנייה
מתיקיית הפרויקט מריצים:

```bash
gradle assembleDebug
```

קובץ ה-APK ייווצר בנתיב `app/build/outputs/apk/debug/app-debug.apk`.

## מה כלול
הפרויקט כולל רק מעטפת WebView ל-Google Maps. הוסרו קוד Google Photos, מנגנוני OAuth וניהול אסימונים, סנכרון ענן, מסד נתונים לחשבונות, משימות רקע, Firebase והגדרות שאינן קשורות למפות.
