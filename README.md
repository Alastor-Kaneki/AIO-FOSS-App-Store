# GitDroid

GitDroid is a native Android FOSS app store that combines **F-Droid**, optional **IzzyOnDroid**, and **GitHub Releases** in one Material You interface.

Repository name: **AIO FOSS App Store**  
Android package: `dev.alastorkaneki.gitdroid`

## Current alpha features

- Native Kotlin + Jetpack Compose application; no WebView wrapper.
- Material You dynamic colors with a true-black AMOLED option.
- Optional immersive mode with transient system bars.
- F-Droid and IzzyOnDroid `index-v1.jar` streaming parser with a 12-hour local cache.
- GitHub Android-repository discovery and latest-release APK resolution.
- Search, source filters, app categories, source controls, and app details.
- ABI-aware GitHub APK selection.
- Download Manager integration and handoff to Android's system package installer.
- Optional GitHub token stored only in the app's private preferences.
- Custom Alastor Kaneki-inspired launcher icon generated for this project.

## Sources

| Source | Default | Endpoint |
|---|---:|---|
| F-Droid | Enabled | `https://f-droid.org/repo` |
| IzzyOnDroid | Disabled | `https://apt.izzysoft.de/fdroid/repo` |
| GitHub | Enabled | `https://api.github.com` |

## Build

The project uses JDK 17, Gradle 9.5, Android Gradle Plugin 9.3, compile SDK 37, target SDK 36, and Kotlin/Compose 2.3.21.

```bash
gradle :app:assembleDebug
```

GitHub Actions publishes `GitDroid-debug.apk` for branch and pull-request builds. A persistent release signing identity can be supplied with these repository secrets:

- `GITDROID_KEYSTORE_BASE64`
- `GITDROID_KEYSTORE_PASSWORD`
- `GITDROID_KEY_ALIAS`
- `GITDROID_KEY_PASSWORD`

Private signing material must never be committed to this public repository.

## Security notes

GitDroid downloads only over HTTPS. Android's package installer remains the final authority for installation and displays package/signing conflicts. This alpha parses F-Droid-compatible indexes but does not yet perform independent repository-JAR signature verification. GitHub release APKs are selected from upstream releases and are not automatically reproducible-build verified. Review source, permissions, and signing identity before installing.

## Planned work

- Repository-signature and APK-certificate verification.
- Installed-app tracking and update notifications.
- User-added F-Droid-compatible repositories.
- Download queue/history and resumable retry controls.
- Reproducible-build metadata where available.

Licensed under the MIT License.
