# PDF Scanner (Android, Kotlin, Jetpack Compose) – v1.0.0 MVP

Scan or import pages with the Google ML Kit Document Scanner, reorder/delete/rename,
save as a PDF in app-private storage, then open or share it via the Android share sheet.

Stack: Kotlin 2.0, Jetpack Compose, Material 3 (light/dark + optional Dynamic Color),
MVVM with StateFlow, manual DI (`AppContainer`), no runtime permissions.

---------------------------------------------------------------------
## 0. Build the APK on GitHub (no Android Studio needed)
---------------------------------------------------------------------
This repo includes `.github/workflows/build.yml`. GitHub builds the app for you.

1. Create an empty repository on github.com (e.g. `pdfscan`).
2. Upload the CONTENTS of the unzipped `pdfscan/` folder to it, keeping the structure.
   Make sure the hidden `.github` folder is included. Easiest is from a terminal:

       cd pdfscan
       git init -b main
       git add .
       git commit -m "chore: initial Android project skeleton"
       git remote add origin https://github.com/<you>/pdfscan.git
       git push -u origin main

   (Web upload also works: drag the folders into "Add file > Upload files".
   The browser may skip hidden folders, so check that `.github/workflows/build.yml` exists.)
3. Open the repo > Actions tab. The "Build APK" workflow starts automatically
   (or press "Run workflow"). First run takes about 3-6 minutes.
4. When it turns green, open the run > Artifacts > download `pdfscan-debug-apk`,
   unzip it to get `app-debug.apk`.
5. Install on your phone: copy the APK over, open it, allow "Install unknown apps"
   for your file manager/browser. The phone needs Google Play services and Android 8.0+.

Make a downloadable release:

    git tag -a v1.0.0 -m "MVP"
    git push origin v1.0.0

The workflow then attaches `app-debug.apk` to a GitHub Release (Releases page).

If the build fails: open the failed run > click the red step > read the error.
Paste it to Claude and it can fix the code.

Note: this is a debug-signed APK, fine for personal use and testing. A Play Store
release needs your own signing key (keep it in GitHub Secrets, never in the repo).

---------------------------------------------------------------------
## 1. Requirements
---------------------------------------------------------------------
- Android Studio Ladybug (2024.2.1) or newer (Koala works; newer is better)
- JDK 17 (bundled with Android Studio: Settings > Build > Gradle > Gradle JDK = "jbr-17" or "Embedded JDK")
- Internet connection for the first Gradle sync (downloads Gradle 8.9 and dependencies)
- Android SDK Platform 35 (Android Studio offers to install it on first sync)
- A device or emulator WITH Google Play services:
  - Physical phone (Android 8.0 / API 26 or newer), or
  - Emulator image labelled "Google Play" (NOT "Google APIs" / AOSP)

---------------------------------------------------------------------
## 2. Open and run (5 steps)
---------------------------------------------------------------------
1. Unzip this archive. You get a folder named `pdfscan/`.
2. Android Studio > File > Open > select the `pdfscan` folder (the one containing `settings.gradle.kts`).
3. Wait for "Gradle sync" to finish. Accept any prompt to install SDK Platform 35 / build tools.
4. Pick a device in the toolbar (Play-services device/emulator) and press Run.
5. Tap "Scan", scan or import pages, then "Save PDF", then Share / Open.

### Gradle wrapper note
This archive contains `gradle/wrapper/gradle-wrapper.properties` (Gradle 8.9) but not
`gradle-wrapper.jar` or the `gradlew` scripts. Android Studio does not need them.
For command-line builds, generate them once (needs Gradle installed, or use Studio's terminal):

    gradle wrapper --gradle-version 8.9

Then:

    ./gradlew assembleDebug        # APK at app/build/outputs/apk/debug/
    ./gradlew assembleRelease      # unsigned unless you add signing config
    ./gradlew bundleRelease        # AAB for Google Play

(Windows: `gradlew.bat`.)

---------------------------------------------------------------------
## 3. Optional: use the Inter font
---------------------------------------------------------------------
The project uses the system font by default so it builds with zero extra files.
To use Inter:
1. Download Inter from https://fonts.google.com/specimen/Inter
2. Copy and rename three weights into `app/src/main/res/font/`:
   `inter_regular.ttf`, `inter_medium.ttf`, `inter_semibold.ttf`
   (file names must be lowercase, only letters/digits/underscore)
3. In `ui/theme/Type.kt`, follow the comment above `val Inter` and replace it with the
   `FontFamily(Font(R.font...))` version; add the two imports named in that comment.

---------------------------------------------------------------------
## 4. Project layout
---------------------------------------------------------------------
    app/src/main/java/com/example/pdfscan/
      ScanApp.kt, AppContainer.kt, MainActivity.kt
      domain/    Models.kt, PdfEngine.kt, PdfRepository.kt      (contracts, no UI)
      data/      AndroidPdfEngine.kt, LocalPdfRepository.kt, SettingsRepository.kt
      scanner/   DocumentScanner.kt                              (ML Kit wrapper -> List<Uri>)
      ui/        AppNav.kt, IntentUtils.kt
        theme/   Color.kt, Type.kt, Theme.kt
        home/    HomeScreen.kt, HomeViewModel.kt
        preview/ PreviewScreen.kt, ScanSessionViewModel.kt
    app/src/main/res/  adaptive launcher icon (+ monochrome), themes, FileProvider paths

Dependency rule: ui -> domain <- data. `scanner` is only used by `ui`.
`AppContainer` is the only file that chooses concrete implementations.

---------------------------------------------------------------------
## 5. Common problems
---------------------------------------------------------------------
- "Scanner unavailable" snackbar: the device/emulator lacks Google Play services, or the
  scanner module could not download (check internet, update Play services, retry).
- Sync error "Unsupported Java": set Gradle JDK to 17 (Settings > Build > Gradle).
- Sync error about SDK 35: install "Android SDK Platform 35" in SDK Manager.
- Plain emulator crashes/fails on scan: use an emulator image with the Google Play logo.
- "No PDF viewer installed" toast on Open: install a PDF reader, or use Share instead.
- Release build problems: run `./gradlew assembleRelease`; if R8 reports missing classes
  add the suggested `-dontwarn` lines to `app/proguard-rules.pro`.
- Package name: `com.example.pdfscan` is a placeholder. Before publishing, rename the
  package/applicationId (Android Studio: right-click package > Refactor > Rename, then
  update `namespace` and `applicationId` in `app/build.gradle.kts`).

---------------------------------------------------------------------
## 6. Git / GitHub setup
---------------------------------------------------------------------
    cd pdfscan
    git init -b main
    git add .
    git commit -m "chore: initial Android project skeleton"
    gh repo create pdfscan --private --source=. --remote=origin --push
    # or: git remote add origin git@github.com:<you>/pdfscan.git && git push -u origin main

    git tag -a v1.0.0 -m "MVP: scan, preview, export, share"
    git push origin v1.0.0

Branches: `main` (always buildable), `feature/<name>`, `fix/<name>`, `chore/<name>`; squash-merge PRs.
Commits (Conventional Commits): `feat(scope): ...`, `fix(scope): ...`, `refactor`, `perf`, `test`,
`docs`, `build`, `chore`. Use `feat!:` or a `BREAKING CHANGE:` footer for breaking changes.
Never commit keystores or `keystore.properties` (already in `.gitignore`).

Roadmap (tags / milestones):
- v0.1.0  walking skeleton: scan -> PDF -> share
- v1.0.0  MVP (this project)
- v1.1.0  Save to Files (ACTION_CREATE_DOCUMENT), drag-reorder, undo delete,
          photo-picker/CameraX fallback, PDF size options
- v2.0.0  Room index + folders/tags, OCR (searchable PDF), compression engine, cloud backup

---------------------------------------------------------------------
## 7. Extension points (where future features plug in)
---------------------------------------------------------------------
| Feature                      | Seam                                   | Change                                           |
|------------------------------|----------------------------------------|--------------------------------------------------|
| Better PDF / compression/OCR | `domain/PdfEngine`                     | New implementation; swap in `AppContainer`       |
| CameraX / photo picker       | `ScanSessionViewModel.addPages(Uris)`  | New launcher that calls `addPages`               |
| Folders, tags, search        | `domain/PdfRepository`, `PdfEntry`     | Back with Room (files stay on disk)              |
| Cloud backup                 | `PdfRepository` + WorkManager          | Add a `BackupTarget` interface                   |
| New export steps             | `ExportState` sealed interface         | Add `Processing(step, progress)`; fix `when`s    |
| Filters / deskew / OCR prep  | (v1.1) `PageProcessor` before engine   | Fold processors over pages in `export()`         |
| Hilt                         | Constructor-injected ViewModels        | Replace `AppContainer` with Hilt modules         |

---------------------------------------------------------------------
## 8. Notes
---------------------------------------------------------------------
- minSdk 26, targetSdk/compileSdk 35. No CAMERA or storage permissions are needed.
- PDFs are stored in `filesDir/pdfs` (private; removed on uninstall) and shared via FileProvider
  (`${applicationId}.fileprovider`) with temporary read grants.
- `android.graphics.pdf.PdfDocument` is lossless, so PDFs can be larger than JPEG-embedded ones;
  pages are downsampled to 2000 px on the long edge (`PdfOptions.maxEdgePx`). Lower it for smaller files.
- Dependency versions in `gradle/libs.versions.toml` are pinned; bump them when you start.
