# JobAlertzNg Android App

Native Android wrapper for https://www.jobalertz.com.ng/.

## Included
- JobAlertzNg-branded Android shell.
- Native bottom navigation: Home, Jobs, Career, Stories and More.
- Jobs menu for Lagos, Abuja, Other States, Closing Soon and Expired Jobs.
- Blogger-hosted pages and vacancy articles load inside the app.
- Search dialog using Blogger search.
- On-device background Blogger feed checks with Android WorkManager.
- Notifications deep-link to the new article.

## Notification behaviour
The app reads the public Blogger JSON feed. The first successful check stores the latest post as a baseline and does not send old-post notifications. Later checks notify about new posts. WorkManager periodic work uses Android's minimum 15-minute interval; the operating system may defer actual execution because of battery/network conditions. Users must grant notification permission on Android 13 and later. This is not an instant push service.

## Build
The GitHub Actions workflow `.github/workflows/build-android.yml` builds `app/build/outputs/apk/debug/app-debug.apk` on every push to `main` and on manual workflow dispatch. It uploads a simple `JobAlertzNg.apk` workflow artifact and publishes that APK to the `latest-apk` GitHub Release.

The debug APK is installable by sideloading. It is not Play Store-signed. For a public Play Store release, create and securely store a release signing key and build a signed release APK/AAB separately.

## Zero-cost approach
The source and build use GitHub Actions standard runners and Android/Gradle dependencies. GitHub Actions free usage depends on the account's included minutes and public-repository policy. No paid Firebase or notification service is required for this local periodic-check model.
