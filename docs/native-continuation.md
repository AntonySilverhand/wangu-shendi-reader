# Native continuation: navigation, adaptive UI and reading-time caching

## Keep it simple

Use existing Views, repositories and executors. Let a dialog handle its own button clicks instead of adding a class per button. Keep the necessary thread/lifecycle guards; do not add placeholder controls, new libraries, or a second download framework. Only extract helpers with a concrete caller/test.

## Starting point (before continuation)

- `4da362d` is the current committed native baseline; the worktree also contains unfinished P6 classes. Preserve that work, do not reset it or replace the native plan with a second WebView implementation.
- The initial native compile fails: `SettingsDialog` is missing while many listeners reference it. Reader TOC/bookmark listeners also reference unfinished controller methods.
- The reader currently puts five full-size buttons and its title into one horizontal row. On narrow windows this squeezes out content/actions. Dimensions mix raw pixels and dp.
- The cached TOC search adapter's `loadRange` and “load all” are stubs. The current chapter scheduler schedules an entire assembler operation rather than each physical page. Therefore adding a whole-book loop now would violate the queue contract in `plan.md`.
- This host is ARM64, not the x86 host described by the handover. Use an isolated, checksum-verified ARM64 JDK 17, keep the global JDK unchanged, and distinguish build/JVM evidence from device tests.

## Implementation order

1. Restore a compilable P6 baseline without discarding inherited work. Wire Home/TOC/bookmarks/settings and correct back/close behavior; preserve paragraph + UTF-16 offset on navigation and resize.
2. Redesign the reader chrome around a permanently discoverable `书架` action and title row, with wrapping secondary actions. Keep 48dp touch targets, theme-aware colors, bounded line width, safe insets and independent scrolling for sheets. Test narrow, short landscape, tablet and large-font cases; never claim arbitrary ratios solely from width breakpoints.
3. Run chapter search on the bounded CPU executor, discard stale results on query/route/close changes, and restore exact match offsets rather than only block starts. Validate highlight mapping.
4. Complete actual TOC loading and P6 data-file workflows, replacing placeholder behavior with either real operations or clearly disabled/unavailable controls. No working-looking auto-cache toggle before its coordinator exists.
5. Then implement P7/P8 in the order specified by `plan.md`: per-page shared queue and durable task/plan transactions first; foreground gate and planner next; UI progress last. Default off; explicit whole-book opt-in; leave app/lock screen = pause; complete committed chapters skip all network requests. Preserve the initial plan anchor and show whole-book progress separately from continuous offline runway.
6. Re-run native tests/build/lint and the untouched web suite. Update `docs/native-progress.md` with actual evidence and remaining blockers. No migration/release claim before P9 and original-signature/device checks.

## Current checkpoint (2026-09-28)

- Native debug build, lint and instrumentation APK compilation pass; 76 JVM tests and 194 source-contract checks pass. No connected Android device, so instrumentation tests are **compiled, not executed**.
- Permanent Home/书架 navigation, wrapping reader actions, native settings/bookmarks and explicit queued TOC loading are connected. Android 36 Back uses AndroidX dispatch.
- Removed 12 settings click wrappers and 4 unused reader UI runnables. No new dependency or framework.
- TOC search now runs after debounce off UI; queued UI callbacks read current results. Loading pages no longer repeatedly resets the TOC scroll position.
- Split-paragraph anchors/highlights retain UTF-16 offsets. Cached chunks are rejoined using existing paragraph indices; no new DB schema or serialized format. Added pure-Java round-trip tests and a Room instrumentation test.
- Web typecheck, 66 unit tests, delivery verification and focused verification pass. The focused verifier's first run failed its banner assertion; a subsequent diagnostic and unchanged rerun passed. Keep that live-source timing sensitivity visible.
- P6 is still incomplete; see the newer checkpoint below. P7/P8 automatic whole-book caching is **not implemented**. Fix whole-chapter versus physical-page queue integration before enabling it. No migration, release or coverage-install claim.

## Continuation (2026-10-01)

- Added real native JSON import/export through SAF. File I/O uses the existing CPU executor; Room access uses the serial personal executor. One operation at a time, no service; completion is delivered to the resumed Activity after recreation.
- Imports validate format/version, numbers, IDs, size (4 MiB) and nesting before/within an atomic transaction. They merge newer progress/history, deduplicate bookmarks and cannot overwrite a different book's bookmark on ID collision. They never read/write the content DB or enable automatic/metered downloading. Import completion returns to Shelf without resaving the old reader anchor.
- This is **native-format, merge-only backup**, not legacy web-v1 compatibility or complete app migration. Local TXT reimports currently receive new IDs and cannot automatically recover old-ID reading records. The UI explicitly explains these limits. F19 remains in progress.
- Wired Enter/Shift+Enter, soft-keyboard Search and Esc for chapter search; composing input is left to the IME. Only the newest pending anchor restoration may execute.
- Bookmarks now deduplicate nearby offsets in the same paragraph, keep distant offsets distinct, and offer confirmed per-book clearing. Removed another two forwarding click listeners. Added bounded snippets around the actual anchor and 48dp delete buttons.
- Added `PersonalBackupTest` (8 cases) and `BookmarkOperationsTest` (2 cases) to instrumentation sources. These cover round trips, atomic rollback, sequence preservation, collision isolation, limits and per-book clearing; **device execution is still pending**.
- Remaining before P6 completion: progressive remote TOC search integration, legacy-v1 JSON/replace support, remaining feature parity, and actual device validation. The original P3 claim of physical-page scheduling/persistence also needs repair before download work.

## Keyboard and search checkpoint (2026-10-04)

- Added reader shortcuts directly to the existing Views/controller: ←/→, T/F/B/S/comma, Home/End, and Esc for reading panels. Focused widgets get first refusal. Editors, active text selection, composing input and Ctrl/Alt/Meta shortcuts are not intercepted; repeat events do not reopen dialogs. B currently opens the bookmark list, not the old web bookmark toggle.
- Search now counts all matches and navigates across bounded 500-position windows rather than truncating at 500. Scans remain on the existing CPU executor. Query/close/route/pause changes cancel old scans; generation checks reject obsolete pages. Content refresh re-runs the query so positions refer to current text. Obsolete posted anchor corrections are also invalidated on query/window/close changes. No new dependency, executor or polling loop.
- `ChapterSearchPageTest`: 5 executed JVM cases including exact UTF-16 positions for 10,003 matches, cross-paragraph Unicode, non-overlapping matching, empty/out-of-range windows and cancellation.
- `ReaderKeyboardTest` and `ReaderSearchNavigationTest`: compiled instrumentation coverage for key guards, composition, window crossings, wraparound, rapid movement and close/query cancellation. **Not executed: adb lists no devices.**
- Native `check --offline :app:assembleDebugAndroidTest` passes: 71 core + 10 app JVM tests, lint 0 errors / 21 warnings; Java structural scanner passes 317 files. Source contracts pass 194/194. Web typecheck and 66 unit tests pass. Final native log: `/tmp/native-search-final-check.log` (exit 0); earlier logs: `/tmp/native-search-window-check.log`, `/tmp/native-search-window-test.log`, `/tmp/native-search-contracts.log`. Debug APK signature and package/version checks pass (independent dev package only).
- Web build and delivery verifier pass. Focused verification initially failed its banner assertion and timed out after refresh (`/tmp/native-search-web-check.log`). A fresh-context diagnostic restored chapter 1 with 92 paragraphs and no page error; an unchanged focused rerun passed every check (`/tmp/native-search-web-diagnostic.log`, `/tmp/native-search-web-rerun.log`). Preserve the initial failure; this is not proof its live-source/timing sensitivity is fixed. No legacy web source/test changes.
- Pre-implementation compile checks failed as expected for the missing key handler and search-page API; these are resolved by the successful build above. No device validation, release or migration claim.

### Device checks still required

1. Install `org.wanshu.reader.native.dev` alongside the old app; do not uninstall or replace the old reader.
2. Check Home/Back and chapter search at portrait/short landscape, split-screen and large font scales; verify IME composition, keyboard shortcuts and Enter/Shift+Enter/Esc. With >10,000 matches, cross 500, wrap both directions, edit the query while loading and close search; no stale scroll/highlight should reappear.
3. Export a native JSON file; cancel both pickers and verify no replacement. Import a good file, then a malformed/oversized file; verify atomicity and error messages.
4. Rotate during the picker and during transfer; verify one operation/result, no leaked window, and no source download while outside the reader.
5. Add the same nearby bookmark repeatedly, add another far into a long paragraph, jump to both, cancel clearing, then clear one book and verify other books and progress survive.

## Acceptance focus

- Home reachable from loading/error/content and after rotation; old responses cannot reopen a book.
- Controls remain reachable without overlapping the title/body; system/cutout/IME insets applied once.
- Search, bookmarks and typography changes preserve book/chapter identity and logical text offsets.
- Caching must never mark partial/uncommitted text complete, compete equally with the active chapter, or keep working outside the foreground reading session.
- Existing unchecked P6–P12 tasks remain unchecked until actually implemented and verified; prior handover claims alone are not evidence.
