# Native Android Reader Refactor — Project Pause & Handover Document

> **Date**: 2026-09-17  
> **Status**: Paused at Phase P6 (In Progress)  
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

## 2. What Has Been Done (Phases P0 to P6)

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

### 🔄 Phase P6 — TOC, Search, Bookmarks, Settings (In Progress)
- **What is DONE in P6**:
  - `TocSearchController` implemented in `:core/src/main/java/org/wanshu/reader/core/toc/`:
    - 260ms debounce, generation protection, numeric probe (probe radius 3), 4 pages per batch, 200 result display limit with "more results" indicator, 60-second idle convergence on exhausted search.
    - All 6 unit tests passing in `core/src/test/java/org/wanshu/reader/core/toc/TocSearchControllerTest.java` (exact match with `test/toc-search.test.ts`).
  - `ChapterSearchEngine` implemented in `:core/src/main/java/org/wanshu/reader/core/search/`:
    - Case-insensitive search, 500-match cap, returns `ChapterSearchMatch` (paragraphIndex, startOffset, endOffset).
    - Unit tests passing in `ChapterSearchEngineTest.java`.
  - `ContentRepository.getTocEntries()` and `GetTocEntriesRunnable` implemented.
  - Search listeners and stub methods added to `ReaderController` (`toggleSearch()`, `nextSearchMatch()`, `prevSearchMatch()`, `closeSearch()`).
  - 239 Java files 100% compliant with zero lambdas/zero inner classes rule.
  - `tools/native/build.sh test` passing cleanly.

---

## 3. What Remains to Be Done (Next Tasks)

### 1. Complete Phase P6 (Immediate Next Focus)

1. **In-Chapter Search UI Wiring**:
   - In `ReaderView`:
     - Add search bar view above/below header (EditText, match count TextView, Prev/Next buttons, Close button).
     - Provide getter/methods: `showSearchBar()`, `hideSearchBar()`, `setSearchMatchCount(int current, int total)`, `getSearchEditText()`.
   - In `ReaderBlockAdapter`:
     - Support search query / active match highlighting using `BackgroundColorSpan` when binding `BlockViewHolder`.
   - In `ReaderController`:
     - Connect search input text watcher (120ms debounce) calling `ChapterSearchEngine.search(paragraphs, query)`.
     - Wire `nextSearchMatch()` and `prevSearchMatch()` to cycle through matches and scroll RecyclerView to matching block/paragraph.
     - Wire `closeSearch()` to clear highlights, hide search bar, and dismiss keyboard.

2. **TOC Dialog (`org.wanshu.reader.ui.toc`)**:
   - Create `TocDialog` (or full-screen dialog / bottom sheet):
     - `RecyclerView` with `TocAdapter` displaying chapter list.
     - Tabs/filter for "正文" (Main) and "番外" (Extra).
     - Highlights current chapter.
     - Displays cached status (complete vs partial vs uncached).
     - Search input connected to `TocSearchController`:
       - Shows search results as user types (260ms debounce).
       - Clicking a search result or chapter navigates via `AppNavigator.openReader(bookId, entry.chapterId)` and dismisses dialog.
     - Lazy-loading for online book `36780` ("加载全目录" button if not all 44 pages loaded).
     - **Constraint P6.6**: Local books (`local-*`) must NOT trigger network requests!

3. **Bookmarks Dialog (`org.wanshu.reader.ui.bookmarks`)**:
   - Create `BookmarksDialog`:
     - Lists bookmarks for `currentBookId` from `personalRepository.getBookmarks(bookId, callback)`.
     - Displays chapter title, paragraph snippet, creation timestamp.
     - Clicking a bookmark navigates via `AppNavigator.openReaderWithAnchor(bookId, bm.chapterId, bm.paragraphIndex, bm.offsetUtf16)`.
     - Delete individual bookmark via `personalRepository.deleteBookmark(id, callback)`.
   - Reader header bar: add "添加书签" button that samples current anchor and saves snippet.

4. **Settings & Cache Dialog (`org.wanshu.reader.ui.settings`)**:
   - Create `SettingsDialog`:
     - Theme selector (5 themes: Light, Dark, Sepia, Eyecare, OLED).
     - Font size (+/- controls), line spacing, page margins.
     - Switches: reading constant awake (`keepScreenAwake`), immersive mode (`immersiveMode`), prefetch next chapter (`prefetchNextChapter`), and auto-cache setting ("阅读时自动缓存").
     - Storage stats: display chapter count and bytes from `ContentRepository.getStorageStats(bookId, callback)`.
     - "清除缓存" button calling `ContentRepository.clearRemoteCache(bookId, callback)`. (Safety check: MUST NOT delete local books or touch `PersonalDatabase`!).
     - Personal data export/import JSON (`reader.personal.v1` / `reader.settings.v1`).
     - Help & keyboard shortcuts modal/info.

5. **Instrumentation Tests & P6 Checklist**:
   - Write instrumentation tests covering TOC navigation, chapter search, bookmarks, and settings.
   - Update `docs/native-progress.md` and check off P6 in `plan.md`.

---

### 2. Upcoming Phases (P7 to P12)

- **Phase P7 — Persistent Manual Download Coordinator**:
  - `download_tasks` table + singleton `DownloadCoordinator`.
  - App foreground gate (pauses when leaving app, resumes when returning, zero background services).
  - Range selection: next 50 / 100 / 300 / entire book.
  - Complete vs partial chapter verification.
- **Phase P8 — Reading Time Auto-Cache Planner (New User Feature)**:
  - `DownloadPlanner` pure logic: order = next 50 chapters -> rest of book to end -> previous chapters to start.
  - `ReadingSessionGate`: only downloads when actively reading in foreground on unmetered network with battery > 20%.
  - Pacing: 250ms initial interval, can reduce to 160ms after 10 successful pages.
  - Spec 6.5 download progress displays (detailed stats in download panel, lightweight indicator in reader).
- **Phase P9 — Legacy APK Migration**:
  - Isolated migration activity with minimal `https://reader.local/` WebView bridge.
  - Streaming IDB cursor migration in 64KiB chunks.
  - Zero WebView creation on normal startup.
- **Phase P10 — Comprehensive Regression Matrix**:
  - F01–F22 verification across multiple Android API levels (API 24, 28, 30, 34, 36).
- **Phase P11 — Performance Profiling & Optimization**:
  - Memory, CPU, power consumption verification.
- **Phase P12 — Production Release & Handover**:
  - Release APK build with R8, proper signing, GitHub release assets.

---

## 4. How the Incoming Agent Should Take Over

### Step 1: Set Up the Environment
Run this in your bash session before running Gradle or build scripts:
```bash
export JAVA_HOME=/home/antony/opt/jdk-17.0.19+10
export ANDROID_SDK_ROOT=/home/antony/android-sdk
export PATH=$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH
```

### Step 2: Run Verification Checks to Confirm Baseline
Verify that everything is currently green:
```bash
# 1. Verify zero lambdas rule (239 Java files must pass):
bash tools/native/check-no-lambdas.sh

# 2. Verify all native unit tests in :core and :app:
bash tools/native/build.sh test

# 3. Verify Android debug build:
bash tools/native/build.sh debug

# 4. Verify web tests and TypeScript types:
npm test
npm run typecheck
```

### Step 3: Key Architecture References
- **`plan.md`**: Master blueprint for the native migration.
- **`AGENTS.md`**: Project instructions, constraints, and operational guidelines.
- **`docs/native-progress.md`**: Continuous progress log.
- **`docs/native-feature-matrix.md`**: Matrix of features F01–F22.
- **`android-native/core/src/main/java/org/wanshu/reader/core/`**: Pure Java business logic, models, parsers, schedulers, search engines.
- **`android-native/app/src/main/java/org/wanshu/reader/`**: Android app code (Room entities/DAOs, repositories, UI controllers, views).

### Step 4: Practical Coding Tips for Hand-Written Java
- Every time you create a callback, listener, runnable, or helper:
  - Create a new file: e.g. `TocItemClickListener.java`, `BookmarksLoadedCallback.java`.
  - Declare it as `public class TocItemClickListener implements View.OnClickListener { ... }`.
  - Pass references (like `controller` or `dialog`) through constructor arguments.
  - DO NOT use anonymous classes like `new View.OnClickListener() { ... }`.
  - DO NOT use lambdas like `v -> { ... }`.
  - DO NOT declare static nested classes or inner classes.
- Run `bash tools/native/check-no-lambdas.sh` immediately after creating new files.
