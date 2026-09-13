# Android edge-to-edge display plan

## Goal and recommendation

Make the APK feel integrated with the whole phone screen rather than displaying the reader between two dark Android strips.

**Recommended default: edge-to-edge, not forced fullscreen.** Draw the app’s backgrounds behind the status bar (clock/notifications) and navigation bar (gesture handle/buttons), while keeping text and controls clear of those areas. System icons remain accessible and match the reader’s theme. This is the modern app treatment the requested comparison points toward; it does not require removing Android navigation.

**Optional second step: immersive reading.** Offer an explicit reading-only setting that hides both system bars, with swipe-to-reveal behavior. This provides additional usable reading height, whereas default edge-to-edge primarily improves visual integration. Cutouts still require protection.

Scope: Android APK first. Browser/PWA layouts retain safe-area support, but a web page cannot generally control Android system bars like a native Activity.

## Findings from this repository

| File | Current behavior / implication |
| --- | --- |
| `android/java/org/wanshu/reader/MainActivity.java` | A match-parent WebView, but no edge-to-edge window configuration. Both system bars are hard-coded to `#161719`. Match-parent only fills the area Android assigns to the app. |
| `android/AndroidManifest.xml` | `Theme.Material.NoActionBar` removes the app action bar, not the status/navigation bars. No explicit keyboard resize policy. Configuration changes are handled without recreating the Activity. |
| `android/build.sh` | Minimum API 24, compile/target API 34; direct SDK build without Gradle/AndroidX. Edge-to-edge must be explicitly enabled for the current target. |
| `src/web/index.html` | Already declares `viewport-fit=cover` and `interactive-widget=resizes-content`. These do not configure the native window. |
| `src/web/styles/app.css` | Already uses `env(safe-area-inset-*)` for several bars/panels. Home, chapter content, sheets, and some wide-layout offsets still need a complete four-edge audit. WebView inset propagation must be verified rather than assumed. |
| `src/web/store/settings.ts` | Updates CSS and browser `theme-color`, but never updates native system icon appearance or the WebView/window background. |
| `src/web/views/reader.ts` | Hiding reader toolbars only changes CSS transforms; it does not hide Android bars. Reader padding remains reserved. Resize restoration ignores height-only changes of 150px or less, so inset changes need explicit handling. |

These are source-code findings, not device measurements. Before implementation, record the affected phone’s Android version, WebView version, navigation mode, and screenshots. No on-device verification has been performed for this plan.

## Implementation sequence

### 1. Enable native edge-to-edge and establish one inset contract

Files: `MainActivity.java`; new top-level Java helper/listener classes as needed; `AssetClient.java` for page-ready delivery; new `src/web/native-display.ts`.

- Configure the window before the first page is displayed.
- On API 30+, use `Window.setDecorFitsSystemWindows(false)` and `WindowInsetsController`.
- On API 24–29, use the supported legacy layout flags (`LAYOUT_STABLE`, `LAYOUT_FULLSCREEN`, `LAYOUT_HIDE_NAVIGATION`). Do not set hide/fullscreen flags for the default mode.
- Use transparent system bars where supported. On API 28+, configure an appropriate display-cutout layout mode and protect cutouts through insets, including landscape side cutouts.
- Handle API 29+ navigation contrast enforcement deliberately: remove an unnecessary scrim only when the app supplies a reliable contrasting surface. Preserve a themed fallback on older devices/three-button navigation. API 24–25 cannot show dark navigation-button icons, so a darker navigation surface may be necessary.
- Do not pad the entire native WebView around system bars: that would recreate the current unused strips.

**Insets must have one owner:**

1. Read actual system-bar and display-cutout insets through a native `OnApplyWindowInsetsListener`; retain IME insets separately.
2. Publish a small, versioned snapshot to the trusted page on readiness and on changes. Cache the latest snapshot so initial events are not lost before JavaScript starts.
3. Convert native pixels to CSS pixels using the actual WebView viewport scale. With the current fixed-scale viewport, validate the expected `devicePixelRatio` conversion across display/font scaling settings.
4. Define shared CSS variables, for example `--safe-top: var(--native-safe-top, env(safe-area-inset-top, 0px))`, plus bottom/left/right equivalents.
5. In the APK, native values **replace**, never add to, browser `env()` values. On the web, retain `env()` fallback. Use the maximum overlapping bar/cutout inset on each edge, not their sum.
6. Validate WebView inset dispatch/consumption so it does not also resize or pad content for the same system bars. Do not consume keyboard insets indiscriminately.

Use event-driven updates only; no polling. Keep every new Android class top-level: this repository prohibits anonymous/inner/nested classes and lambdas.

### 2. Make the web layout safely fill that window

Files: `src/web/styles/app.css`, `src/web/main.ts`, `src/web/views/reader.ts`.

- Initialize the Android display adapter before the app’s first meaningful layout. Provide a readiness handshake and a safe startup surface rather than briefly exposing controls under system icons.
- Replace direct safe-area references with the shared variables.
- Let body, toolbar, drawer, and sheet backgrounds extend to physical edges; inset their interactive content instead.
- Audit all four edges for: home, chapter content, top/bottom toolbars, TOC, settings/bookmarks/download sheets, search controls, banners, toasts, and medium/wide side panels.
- Preserve the reader’s default 20px content margin and typography; add cutout protection without unnecessarily adding an entire second margin.
- Ensure chapter beginnings/endings and the last sheet/list action can scroll fully into the unobscured area.
- When reader toolbars slide away, keep a theme-matched system-bar backing surface so scrolling text does not reduce clock/gesture-icon contrast. This surface must not intercept touches.
- Audit reserved toolbar padding separately. Do not simply collapse it on every scroll: that can move the reading position and trigger hide/show oscillation. Keep the existing behavior for the first edge-to-edge change; reclaim unnecessary space later with anchor-preserving tests.
- Notify the reader explicitly when effective insets change, including changes smaller than the current resize threshold. Preserve the existing paragraph + character-offset anchor after layout settles, without resetting the chapter or adding continuous animation/Range sampling.

### 3. Synchronize Android chrome with reader themes

Files: `src/web/store/settings.ts`, `src/web/native-display.ts`, native display helper; optionally a small native launch theme resource and manifest reference.

- Add a narrow Android-only message for the selected theme; reuse the five existing theme IDs/colors.
- Dark status/navigation icons for light, paper, and eink themes where supported; light icons for dark and black themes. Use modern appearance APIs with version-guarded legacy flags.
- Match window/WebView startup backgrounds and any required system-bar backing surfaces to the theme. Mirror only the last theme in native preferences for launch continuity; web settings remain authoritative.
- Apply on initial page readiness, theme changes, and Activity resume. Preserve layout flags when changing icon appearance.
- Keep the communication surface strictly typed and allowlisted. If using `addJavascriptInterface`, expose only display/theme operations, marshal changes to the UI thread, and retain the exact `https://reader.local/` navigation/resource restrictions. Do not load untrusted documents/frames into a bridge-enabled WebView or expose arbitrary native commands.
- Do not change the stable origin, personal storage keys, chapter caches, or APK Service Worker policy.

### 4. Verify keyboard and lifecycle behavior before adding fullscreen

Files: `AndroidManifest.xml`, native inset handling, relevant web overlay styles.

- Explicitly select `adjustResize` as the initial keyboard policy and test it with edge-to-edge on both modern and legacy Android/WebView combinations.
- Distinguish IME height from the navigation safe area. If WebView already resizes for the keyboard, do not also add keyboard-height padding. Use a dedicated IME fallback only where measurement shows resize is insufficient.
- Test search and every editable sheet: focused inputs and their actions must remain visible; dismissal must restore the layout without blank bottom space.
- Recompute insets on rotation, display-size changes, split-screen/folding, and resume. Do not reload the WebView or lose reading position.
- Keep ordinary system back/navigation behavior intact.

### 5. Optional immersive reading setting — separate follow-up

Files: settings store/view, app/reader orchestration, Android display adapter.

- Add an Android-only “Immersive reading” setting, default off. Keep it distinct from hiding the reader’s own toolbars.
- On API 30+, hide system bars with `WindowInsetsController` and allow transient reveal by edge swipe; use immersive-sticky compatibility flags on older versions.
- Enter only in the reading view. Show system bars when leaving reading or opening editing/search UI; restore the chosen state after dismissal/resume without repeatedly fighting Android’s transient reveal.
- Recompute visible-bar insets while retaining cutout safety. Anchor reading position when geometry changes.
- Preserve system back/home gestures and provide an obvious way to turn the feature off. Do not request broad gesture-exclusion regions.

## Validation and acceptance criteria

### Device coverage

Test at least one physical phone, supplemented by emulators:

- API 24/25 legacy navigation and API 28/29 cutout/contrast behavior.
- API 30–34 gesture and three-button navigation.
- Android 15/16 behavior with the current target. Treat a future target-SDK upgrade as separate work and retest enforced edge-to-edge rules then; do not rely on deprecated bar-color setters as the long-term design.
- Portrait/landscape, notch/punch-hole, larger display/font settings, and split-screen or a foldable where available.
- All five themes; toolbars shown/hidden; home, reader, TOC, overlays, keyboard, cold start, background/resume, and process restart.

### Observable success

- In gesture mode, app backgrounds reach both screen edges with no unrelated dark strips in light/paper themes.
- Clock and navigation icons remain legible. No control is obscured by a cutout, gesture handle, navigation buttons, or keyboard.
- Native bounds confirm the WebView spans the app window in edge-to-edge mode; CSS measurements confirm each safe inset is applied once.
- Before/after screenshots demonstrate the improvement, with any older-device/three-button fallback documented.
- Rotation, keyboard dismissal, inset updates, and optional immersive transitions preserve the same paragraph/character anchor within normal layout rounding.
- No idle polling, data loss, external navigation expansion, or desktop/browser layout regression.

### Automated/build checks during implementation

- Add layout tests injecting zero/nonzero/asymmetric inset snapshots, checking single application, cutout safety, theme messages, and reading-anchor preservation.
- Add an adb/debug-WebView display regression script, following `tools/android-persistence-adb.mjs`; browser emulation alone cannot validate native system bars.
- Run `npm run typecheck`, `npm test`, and `bash tools/verify.sh`.
- Run `node test/verify-delivery.mjs` for reader geometry changes and `node test/check-layout.mjs` for wide-layout regressions.
- Build with `bash android/build.sh <next-version>` and verify `aapt dump badging` versionCode/versionName. The build argument currently names the artifact; manifest version fields must also be updated for a release.

## Delivery order

1. Capture baseline screenshots and native/WebView geometry.
2. Ship native edge-to-edge + inset handling + theme synchronization together; transparent bars alone are unsafe.
3. Complete device, keyboard, lifecycle, and reading-position regression checks.
4. Add optional immersive reading in a separate change after the default behavior is stable.

This document is a plan only; no application code has been changed.
