# GlowPlan — Android Room Lighting Calculator

Native Android starter app in **Kotlin + Jetpack Compose**, designed with colorful room cards.

## Features

- Six default editable presets (lumens per square foot):
  - Living Room — 15
  - Kitchen - General — 75
  - Dining Room — 35
  - Bedroom — 15
  - Hallway — 10
  - Bathroom — 75
- Room dimensions: feet, metres, centimetres, inches.
- Switch units at any time without changing the underlying room dimensions.
- Enter the lumens per LED lamp (from its packaging).
- Show the calculated room area, total lumen requirement, and number of lamps rounded up.
- Edit preset values; create and delete custom room types.
- Save presets locally so they remain after closing the app.
- No account or internet connection required to run the app after installation.

## Calculation

```
area_ft2 = (length * feet_per_unit) * (width * feet_per_unit)
total_required_lumens = area_ft2 * required_lumens_per_ft2
number_of_lights = ceil(total_required_lumens / lumens_per_light)
```

**Important:** This is a simplified planning estimate. It assumes all lamp lumen output is available for room illumination. Actual results vary with fixture optics, layout, surface reflectance, room shape, ceiling height, utilization, and light-loss factors. Do not use the estimate as a substitute for a detailed lighting design.

## Open and run

1. Install a current **Android Studio** with JDK 17+ and the Android SDK API 36 installed.
2. In Android Studio, choose **Open**, and select the `GlowPlan_Android` folder.
3. Allow Android Studio to sync the Gradle project. A `gradle/wrapper/gradle-wrapper.properties` is included; a wrapper JAR is not bundled. Android Studio can use a locally configured Gradle distribution or generate the wrapper from an installed Gradle.
4. Select a phone or emulator running **Android 7.0 / API 24 or later**.
5. Select **Run > Run 'app'**, or choose **Build > Build APK(s)**.

## Dependencies

- Android Gradle Plugin: 8.13.2
- Kotlin + Compose compiler: 2.3.20
- Compose BOM: 2026.04.01
- AndroidX Activity Compose: 1.13.0
- `compileSdk=36`, `targetSdk=36`, `minSdk=24`

Source code is in `app/src/main/java/com/glowplan/app/MainActivity.kt`.

## Build status

This environment has no Android SDK or Gradle installation, so **the project is source-only and has not been compiled into an APK here**. Sync/build it in Android Studio before installing on a phone.
