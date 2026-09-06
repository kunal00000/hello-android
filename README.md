# Hello Android

A minimal native Android starter using Kotlin and Jetpack Compose. The app displays
“Hello, world!”, follows the system light/dark theme, and handles system bar insets.

- App ID: `com.example.helloandroid`
- Minimum Android version: Android 8.0 (API 26)
- Toolchain: JDK 17, Gradle 8.13, Android Gradle Plugin 8.8.2, Kotlin 2.1.0
- SDK: Android Platform 35, Build Tools 35.0.0, Platform Tools

## Build on this Mac

From this directory:

```sh
source "$HOME/.config/android-development/env.sh"
./gradlew :app:assembleDebug
```

The APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
Building does not install or launch the app on a connected phone.

For a different machine, install the toolchain above, set `JAVA_HOME` to the JDK
directory, and set `ANDROID_HOME` to the SDK directory (or set `sdk.dir` in an
untracked `local.properties` file). The included wrapper downloads Gradle.

## Develop

Edit `app/src/main/java/com/example/helloandroid/MainActivity.kt` for the screen,
or `app/src/main/res/values/strings.xml` for its text.

Run Android lint with:

```sh
./gradlew :app:lintDebug
```

This starter has no backend, login, API keys, or runtime permissions.
