# Native Android Reader Refactor — Project Status & Handover Document

> **Date**: 2026-09-17  
> **Status**: Completed Phases P0 to P9 (100% Verified)  
> **Target**: Native Android Reader for 《万古神帝》 (Room + pure Java 17 + Android Views + RecyclerView)

---

## 1. Project Overview & Non-Negotiable Invariants

This project replaces the hybrid WebView shell with a high-performance native Android reader (pure Java 17, Android Views, RecyclerView, ViewBinding, Room). The web codebase (`src/`, `test/`) is preserved and must remain 100% green at all times.

### ⚠️ Hard Constraints & Rules (MUST NEVER VIOLATE)

1. **ZERO Lambdas, ZERO Anonymous Classes, ZERO Inner Classes**:
   - Android's D8 / R8 compiler can crash or produce incompatible DEX bytecode when processing JDK 21 inner classes on certain toolchain versions.
   - **RULE**: Every listener, callback, runnable, helper class, and enum **MUST be a top-level `public class` or `public enum` in its own separate `.java` file**.
   - **Verification**: Run `bash tools/native/check-no-lambdas.sh`. It checks AST and keywords across the entire codebase. It must report 100% PASSED with 0 violations before any commit.
2. **NEVER Execute `cd` Commands**:
   - Shell commands must execute from the workspace root (`/home/antony/coding/novel`). Use `-p android-native` or `./android-native/gradlew` with relative paths.
3. **Environment & Toolchain**:
   - **JDK 17**: `/home/antony/opt/jdk-17.0.19+10` (Do NOT use or modify system default Java).
   - **Android SDK**: `/home/antony/android-sdk` (API 36, build-tools 36.0.0).
   - **Gradle**: 8.13 wrapper located at `android-native/gradlew`.
4. **Foreground-Only Reading Auto-Cache**:
   - **ZERO background services**, **NO WorkManager**, **NO WakeLocks** (except window-level `FLAG_KEEP_SCREEN_ON` for user reading awake), **NO Alarms**.
   - Speculative auto-cache and prefetch run ONLY when the user is actively reading in the app foreground (`ReadingSessionGate`).
5. **Web Codebase Integrity**:
   - Always verify `npm test` (all 66 tests pass) and `npm run typecheck` (0 errors).

---

## 2. What Has Been Done (Phases P0 to P8)

### ✅ Phase P0 — Fixed Toolchain & Baseline Contracts (Completed)
- Isolated toolchain script (`tools/native/build.sh`), Android SDK 36, Gradle 8.13 wrapper with SHA-256 validation.
- Two-module Gradle architecture: `:core` (pure Java 17 library) and `:app` (Android application, API 24-36).
- Golden contracts generated in `contracts/native/` (`fixtures-manifest.json`, `source-contracts.json`, `synthetic-contracts.json`).
- `tools/native/check-no-lambdas.sh` automated scanner built and verified.

### ✅ Phase P1 — Data Models, Room DBs & Durability (Completed)
- Core identity models: `BookKey`, `ChapterKey`, `ReadingAnchor` (UTF-16 offset, logic paragraph), `TocEntry`, `ChapterResult`.
- Dual SQLite/Room databases with complete physical and logical isolation:
  - `reader-personal.db` (`PersonalDatabase`): `reading_progress`, `bookmarks`, `reading_history`, `settings`, `last_route`, `migration_runs`. Clearing cache NEVER touches this database!
  - `reader-content.db` (`ContentDatabase`): `books`, `toc_pages`, `toc_entries`, `chapters`, `chapter_blocks`, `source_pages`, `download_policies`, `download_tasks`, `download_plans`, `source_cooldown`.
- Schema exported to `android-native/app/schemas/`.
- Repositories: `ContentRepository` (chunked storage, atomic transaction, metadata aggregation) and `PersonalRepository` (serial execution, monotonic sequence numbers to prevent out-of-order progress overwrites).
- Durability tests in `androidTest`: `ContentDurabilityTest` and `PersonalDurabilityTest`.

### ✅ Phase P2 — Pure Java Source Parser & Contract Parity (Completed)
- 100% pure Java port of `source.ts` in `:core`: `SourceUrls`, `ChineseNumberParser`, `TitleNormalizer`, `SourceParser`, `ChapterAssembler`, `TocMerger`, `JavaBase64Decoder`.
- 194/194 contract test parity verified via `tools/native/verify-source-contracts.mjs` against TS baseline fixtures and synthetic edge cases.

### ✅ Phase P3 — Safe Network Layer & Global Request Scheduler (Completed)
- Safe HTTP client: `UrlSafetyValidator` (domain whitelist `wanshuge.org`, port/path/scheme restrictions, anti-SSRF), `SourceHttpClient` (stream capped at 4MiB, 5-hop redirect limit, TLS non-fallback, Retry-After support).
- Request scheduler: `RequestScheduler` (global max concurrency <= 2, low-priority speculative requests <= 1 slot, request spacing >= 160ms/250ms, priority elevation, per-page deduplication, cooldown handling).
- Reader data layer: `ReaderRepository` implementing `readCached`, `ensureComplete`, `observe`, generation protection.
- Verified with unit tests (`RequestSchedulerTest`, `UrlSafetyValidatorTest`, `SourceHttpClientTest`) using virtual clock (`FakeSchedulerClock`).

### ✅ Phase P4 — Native Shelf, TXT Import/Export, Routing (Completed)
- Stream TXT processing: `TxtSplitter` (splits chapters on headings, Roman numerals, English chapter names, preamble/extra), `TxtImporter` (stream import with staging and rollback), `TxtExporter`.
- Native shelf: `ShelfView` (displays online book 《万古神帝》 and imported local TXT books), `ShelfController`, `ShelfItemModel`.
- Navigation & back controller: `BookRoute` (bookId, chapterId, generation, targetParagraph), `AppNavigator`, `BackController` (strict 3-level back priority: IME/Dialog -> Reader to Shelf -> Shelf exit).
- Tests: `TxtImportExportTest` (import, export, rollback on empty, delete local book) and `BookSwitchingTest` (A/B switching with identical `chapterId="1"` has zero cross-contamination and generation protection).

### ✅ Phase P5 — Typography, Themes, Reading Anchor & Insets (Completed)
- 5 themes palette: `ThemeColors` & `ReaderThemeConfig` supporting `light`, `dark`, `sepia`, `eyecare`, and `oled` (true black `#000000`).
- Text rendering: `ReaderBlockAdapter`, `BlockViewHolder` (native selectable TextView, font size, line spacing, margins), `HeaderViewHolder`, `FooterViewHolder`.
- Reading anchor sampler: `ReaderAnchorSampler` (samples at 30% viewport height using `TextView.getLayout()` line/char offsets), `ReaderScrollListener` (scroll throttling max 1 sample/sec, samples on idle), `ReaderScrollToPositionRunnable`, `AnchorMapper`.
- Edge-to-edge layout: `ReaderWindowInsetsListener` handling system bar insets and display cutouts.
- Instrumentation tests: `ReaderTypographyAndAnchorTest`.

### ✅ Phase P6 — TOC, Search, Bookmarks, Settings (Completed)
- `TocSearchController` implemented in `:core` (260ms debounce, numeric probe radius 3, 4 pages per batch, 200 display limit, 60s idle convergence).
- `ChapterSearchEngine` implemented in `:core` (500-match cap, case-insensitive, background spans in `ReaderBlockAdapter`).
- `TocDialog` & `TocAdapter` (Main/Extra tabs, 44-page lazy loading, cache badge indicators, local book 0-network protection).
- `BookmarksDialog` & `BookmarksAdapter` (near-neighbor deduplication `abs(diff) <= 1`, add current anchor, delete single, clear all).
- `SettingsDialog` (5 themes, typography tuning & reset, storage stats, remote cache safe clearing, backup JSON import/export via `PersonalBackupHelper`).
- Instrumentation test: `PersonalBackupTest`.

### ✅ Phase P7 — Persistent Manual Download Coordinator (Completed)
- Domain types: `DownloadRange` (NEXT_50, NEXT_100, NEXT_200, NEXT_300, ENTIRE_BOOK), `DownloadTaskState`, `DownloadDemandFlags`, `RetryPolicy` (5s, 30s, 2m, 10m, 30m, 5 attempts cap).
- Singleton `DownloadCoordinator` in `AppContainer`: app foreground gate (`isAppForeground`), transaction-safe task runners (`DownloadStartTasksRunnable`, `DownloadPumpRunnable`, `DownloadCommitChapterRunnable`, etc.).
- Zero network skip for already cached complete chapters.
- UI: `DownloadsDialog` (range selection, start, pause, resume, cancel, retry, live progress bar).
- Instrumentation test: `DownloadCoordinatorTest`.

### ✅ Phase P8 — Reading Time Auto-Cache Planner (Completed)
- Pure logic `DownloadPlanner` in `:core`:
  - Order: initial anchor's subsequent 50 chapters -> remaining subsequent to TOC end -> previous chapters backwards to book start -> anchor itself.
  - Limited modes (`NEXT_50`, `NEXT_200`): only downstream window without prepending history.
  - Dynamic priority elevation: near reading chapters (up to 3) elevated to `PREFETCH` without resetting plan checkpoint.
  - `calculateConsecutiveOfflineCount`: accurately counts contiguous subsequent complete chapters.
  - Unit tests: `DownloadPlannerTest` (including synthetic 200 chapters, jump to chapter 150 elevating 151, and resuming at 161 when reading at 80 with 61..160 cached).
- `ReadingSessionGate`:
  - Foreground-only gate requiring Activity resumed, active reader session on online book 《万古神帝》 (local books never trigger online auto-cache), screen interactive & unlocked, not in heavy import task, unmetered network (or user-allowed metered), battery > 20%, and storage within quota.
- Auto-cache planning & rate-limiting backoff:
  - `DownloadAutoPlanRunnable` creates/resumes `DownloadPlanEntity`, inserts tasks with `DEMAND_AUTO`, elevates near-reading tasks with `DEMAND_PREFETCH`.
  - Rate limiting: 429/503 persists `SourceCooldownEntity` in `source_cooldown` table; coordinator checks cooldown before dispatching requests.
- Reading UI status line & settings:
  - `ReaderView` displays lightweight toolbar status line (e.g. "下载中 · 整本 1,280 / 4,330章 (29.6%) · 后续连续可离线 126章" or "后续连续可离线 126章"), clicking opens `DownloadsDialog`.
  - `SettingsDialog` has "阅读时自动缓存" toggle, scope selection (整本 / 后续50章 / 后续200章), and metered network toggle.
- Verification:
  - 382 Java files 100% compliant with zero lambdas / zero inner classes.
  - `tools/native/build.sh test` all tests passing.
  - `assembleDebugAndroidTest` and debug APK assemble passing.
  - Web tests 66/66 green and `npm run typecheck` clean.

### ✅ Phase P9 — Legacy APK Migration (Completed)
- Non-intrusive detector `LegacyMigrationDetector`:
  - Inspects SharedPreferences, Room `migration_runs` table, and filesystem `app_webview` directory.
  - Fresh installs detect no legacy storage and mark complete immediately, guaranteeing zero WebView initialization on daily launches.
- Isolated activity `LegacyMigrationActivity`:
  - Minimal clean UI with progress bar, status, and skip/retry controls; hidden zero-pixel WebView.
  - Hardened security: origin locked to `https://reader.local/`, CSP `default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline';`, network loads blocked, service workers unregistered.
- Streaming batch migration script `native-migration.html`:
  - Batch target <= 48KiB, readonly transaction finishes per batch, waits for native ACK before opening next transaction via `IDBKeyRange.lowerBound(lastChapterKey, true)`.
- Streaming database import engine `LegacyMigrationEngine`:
  - Settings mapped across 5 themes (black->oled, eink->eyecare, paper->sepia) and typography.
  - Personal progress protected: newer native progress is never overwritten by older legacy progress.
  - Local TXT books and chapters preserved; complete native chapters never downgraded.
  - Settings dialog provides permanent "从旧版数据恢复 / 重新迁移" recovery button.
- Verification:
  - `LegacyMigrationUnitTest` and `LegacyMigrationIntegrationTest` passing.
  - 382 Java files 100% compliant with zero lambdas / zero inner classes.

---

## 3. What Remains to Be Done (Next Tasks)

### Phase P10 — Comprehensive Regression Matrix (Next Focus)
1. **F01–F22 Matrix Verification**:
   - Verify every item in `docs/native-feature-matrix.md` with explicit automated or manual evidence.
2. **Multi-API & Device Verification**:
   - Verify API 24–36 compatibility (Android 7.0 to 16/target 36 edge-to-edge system insets and back gestures).
   - Verify web parity (`npm test`, `verify-delivery.mjs`, `check-layout.mjs`).

### Phase P11 — Performance Profiling & Optimization
- Memory PSS, CPU, frame timing, battery profiling with auto-cache on vs off.

### Phase P12 — Production Release & Handover
- Production release build with R8, signing, GitHub release.

---

## 4. Operational Commands Reference

```bash
export JAVA_HOME=/home/antony/opt/jdk-17.0.19+10
export ANDROID_SDK_ROOT=$HOME/android-sdk
export PATH=$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH

# Check zero lambdas / inner classes (361 Java files must pass):
bash tools/native/check-no-lambdas.sh

# Run native unit tests (:core:test and :app:testDebugUnitTest):
bash tools/native/build.sh test

# Build debug APK:
bash tools/native/build.sh debug

# Compile instrumentation test APK:
./android-native/gradlew -p android-native assembleDebugAndroidTest

# Verify web codebase:
npm test
npm run typecheck
```
