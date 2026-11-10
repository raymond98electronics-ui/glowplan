# GlowPlan — Build your APK entirely online

You do not need Android Studio on your computer.

## GitHub Actions (recommended)

1. Open https://github.com/ and create a free account, if needed.
2. Create a new repository named `glowplan` (choose Private if preferred).
3. Unzip `GlowPlan_Online_Build.zip` on your computer.
4. In the new GitHub repository, select **Add file → Upload files**. Drag/upload the contents of the extracted folder so `settings.gradle.kts`, `app/`, and `.github/workflows/build-android-apk.yml` end up at the repository root. Commit the files. If uploading the hidden `.github` directory is inconvenient, create `.github/workflows/build-android-apk.yml` in GitHub's web editor by pasting its contents.
5. Open **Actions** in your GitHub repository and select **Build GlowPlan Android APK**. If it has not started automatically, choose **Run workflow**.
6. Once the build is successful, open that workflow run. Under **Artifacts**, download `GlowPlan-debug-apk`.
7. Extract the downloaded artifact ZIP to find `app-debug.apk`, transfer the APK to your Android phone, and open it to install. Android may require you to allow installations from the browser/files app.

The workflow uses Java 17, Android SDK API 36, Gradle 8.13, and builds an Android debug APK. No commercial signing key is needed for personal testing. For Play Store distribution, a signed release build is required.

## Limitations

The project has not been compiled or tested in this environment. GitHub's first build will download dependencies and may reveal a compile issue that requires fixing. The downloadable ZIP is source code plus automation, not already an APK. GitHub build availability and quota depend on your account/plan.
