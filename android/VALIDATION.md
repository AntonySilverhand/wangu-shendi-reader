# v0.0.15 display fixes and validation

## Implemented

- Shared overlay lifecycle for every sheet and confirmation dialog, including the data → download replacement path. Compact TOC and focused editors also suspend immersive reading. Closing editors releases focus; duplicate immersive requests are suppressed.
- A native `DisplayLayout` owns IME resizing. API 30+ uses typed IME insets and window coordinates; API 24–29 separates keyboard height from stable navigation insets with a visible-frame fallback. Only the portion still overlapping the host is removed, so `adjustResize` is not counted twice. Child WebView insets are consumed because the native host/CSS contract owns the geometry.
- Native callbacks marshal work onto the UI thread. Bridge reads access an immutable `volatile` JSON snapshot, not window APIs. Theme names and their canonical colors are allowlisted before persistence/application. The WebView is revealed after the first snapshot has executed; native/web first-run theme defaults follow the system preference.
- Four-edge TOC, search, sheet, toast and wide-layout safety. Scrims retain cutout protection in immersive mode and respond to actual visible insets.
- Invalid/unknown snapshots are rejected. Identical geometry does not trigger reader restoration; inset events are coalesced, and editing/obsolete chapters do not get repositioned.
- Fixed package/origin/storage keys and signing key retained. Release version is **15 / 0.0.15**. No chapter cache semantics, personal storage schema, or data migration changes.

## Checks executed in this workspace

- `npm run typecheck`: pass.
- `npm test`: 7 files / 66 tests pass.
- `bash tools/test-android-display.sh`: 11 native geometry assertions pass (JVM, not Android runtime).
- `bash tools/verify.sh`: focused regressions pass.
- `node test/native-display-regression.mjs <url>`: real web UI with a mock Bridge; nested sheet replacement, TOC/search/focus gating, asymmetric cutouts, small-inset paragraph/character anchoring, editor protection and compact/landscape/wide sheets pass.
- `node test/verify-delivery.mjs <url>` and `node test/check-layout.mjs <url>`: pass.
- Release and `DEBUG=1` builds with `bash android/build.sh 0.0.15`: pass; APK versionCode 15, versionName 0.0.15, minSdk 24, targetSdk 34. Only the debug APK enables WebView debugging.
- `apksigner verify --print-certs`: v0.0.13 and v0.0.15 certificates match (SHA-256 `bc7dc1d1bf929d108b0eaf705f7d9f9016f06f428e987d79687a2487be65b3b2`).

## Device validation — NOT executed here

The workspace has no runnable native `adb`: the SDK copy is an x86-64 binary and fails with `Exec format error` on this ARM host. No screenshots, actual keyboard/system-bar behavior, or successful device upgrade are claimed from browser/JVM tests.

Use an authorized **test device**, and export personal data first. Back up imported TXT originals separately; personal-data export is not a backup of chapter/locally imported text. Do not uninstall the old app or clear its data to update.

### Display / keyboard / lifecycle

```bash
DEBUG=1 bash android/build.sh 0.0.15
export ADB=/path/to/working/adb
"$ADB" devices -l
"$ADB" install -r artifacts/wangu-reader-v0.0.15-debug.apk
node tools/android-display-adb.mjs
```

The test adds a clearly named local test book (retained for inspection). It restores the previous reading settings and device rotation settings; it does not remove existing books or clear storage. Use `ANDROID_SERIAL` when multiple devices are attached.

Assertions read actual native window/inset state, not just JS attributes:

- Host/window and WebView dimensions, density/CSS conversion and exactly-once top padding.
- All five native theme backgrounds and system icon appearance flags, plus rejected invalid theme messages.
- Actual status/navigation visibility on immersive enter/exit and nested sheet replacement.
- Paragraph and character anchor preservation.
- IME visibility, actual WebView shrink, focused input geometry and no leftover bottom margin after keyboard dismissal.
- Rotation, landscape TOC cutout safety and background/resume route retention.

Artifacts: `artifacts/android-display/` contains **physical full-screen** `adb screencap` images, `dumpsys window` output and a report with explicitly outstanding manual checks. A successful automated run is not a blanket pass of the device matrix.

Manually inspect clock/gesture/button contrast in the screenshots; test transient edge-swipe reveal without the app fighting it, cold-start surfaces, editing in settings/download sheets and selection, split-screen/folding, enlarged display/font settings, and both navigation modes. Repeat on API 24/25, 28/29, 30–34 and 35/36 where available. Legacy visibility is inferred from insets, so corroborate it using screenshots/window dumps. If an emulator uses a hardware keyboard, enable its software keyboard before the IME test.

### Real in-place upgrade / data retention

Run this on a separate device/emulator still at or below the old version, **before** installing v0.0.15 on it. Both APKs must be debug builds with the original signing key. Existing `artifacts/wangu-reader-v0.0.13-debug.apk` can serve as the baseline; do not rebuild the current code with an old artifact filename and call that an old version.

```bash
export ADB=/path/to/working/adb
node tools/android-upgrade-adb.mjs \
  artifacts/wangu-reader-v0.0.13-debug.apk \
  artifacts/wangu-reader-v0.0.15-debug.apk \
  --confirm-test-device
```

The script checks package, increasing versionCode and matching certificate before installation. It refuses downgrades and never runs uninstall/`pm clear`. It installs the old APK, seeds a two-chapter local book, bookmark, progress and settings, captures personal/settings/local-book storage and every IndexedDB record, force-stops, then performs `adb install -r` of the new APK. It compares all pre-existing records (allowing newly added cache entries) and verifies the restored paragraph/character position. Use a quiet/offline test environment if background source updates would legitimately alter remote cache records during comparison.

Output: `artifacts/android-upgrade-report.json` only after assertions pass. The named test book is retained. Raw personal data and chapter text are not written into the report.

**Release acceptance remains pending these device runs and visual checks.** Matching signature/origin/storage keys make normal in-place data retention expected; they are not evidence of a completed upgrade test or a universal “100% data retention” guarantee.
