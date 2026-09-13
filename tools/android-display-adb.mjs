/**
 * Device-only checks, not browser emulation. Requires DEBUG=1 v0.0.15+ APK.
 * Run on a test device: adds a named local book; restores settings/orientation, never clears data.
 * ADB=/path/to/native/adb node tools/android-display-adb.mjs
 */
import { execFileSync } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import { adb, PKG, connectReader, seedLocalBook, delay } from './android-device.mjs';

let connection, originalSettings, rawSettings, rotation, autoRotation;
const dir = 'artifacts/android-display';
const snapshot = page => page.evaluate(() => JSON.parse(window._nativeDisplayBridge.getDisplaySnapshot()));
async function capture(name) {
  await writeFile(`${dir}/${name}.png`, execFileSync(process.env.ADB || 'adb', ['exec-out', 'screencap', '-p'], { timeout: 30000 }));
  await writeFile(`${dir}/${name}-windows.txt`, adb('shell', 'dumpsys', 'window', 'windows'));
}
async function bars(page, visible) {
  await page.waitForFunction(v => {
    const s = JSON.parse(window._nativeDisplayBridge.getDisplaySnapshot());
    return s.statusVisible === v && s.navigationVisible === v;
  }, visible, { timeout: 10000 });
}
try {
  await mkdir(dir, { recursive: true });
  adb('shell', 'am', 'force-stop', PKG);
  connection = await connectReader();
  const { page } = connection;
  originalSettings = await page.evaluate(() => window.__readerApp.settings.get());
  rawSettings = await page.evaluate(() => localStorage.getItem('reader.settings.v1'));
  const sdk = Number(adb('shell', 'getprop', 'ro.build.version.sdk'));
  rotation = adb('shell', 'settings', 'get', 'system', 'user_rotation');
  autoRotation = adb('shell', 'settings', 'get', 'system', 'accelerometer_rotation');
  adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0');
  adb('shell', 'settings', 'put', 'system', 'user_rotation', '0');
  await page.evaluate(() => window.__readerApp.settings.update({ immersiveReading: false }));
  const title = await seedLocalBook(page, 'display-test');
  await bars(page, true);
  await delay(500);
  const baseline = await snapshot(page);
  assert.ok(baseline.hostHeight > 0 && baseline.windowHeight > 0);
  assert.equal(baseline.hostHeight, baseline.windowHeight, 'WebView host does not fill native window');
  assert.equal(baseline.webHeight, baseline.hostHeight, 'Unexpected whole-WebView padding/shrink');
  const css = await page.evaluate(() => ({
    density: devicePixelRatio,
    height: innerHeight,
    top: parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--safe-top')),
    bottom: parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--safe-bottom')),
    toolbarHeight: document.querySelector('.topbar').getBoundingClientRect().height,
    toolbarBase: parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--topbar-h')),
  }));
  assert.ok(Math.abs(css.density - baseline.density) < 0.01, 'Native px/CSS px scale differs');
  assert.ok(Math.abs(css.height * css.density - baseline.webHeight) <= 2, 'WebView viewport was inset twice');
  assert.equal(css.top, baseline.top);
  assert.equal(css.bottom, baseline.bottom);
  assert.ok(Math.abs(css.toolbarHeight - css.toolbarBase - baseline.top) <= 1, 'Top inset applied more than once');

  const colors = { light: '#f6f5f2', paper: '#f5edda', eink: '#ededed', dark: '#161719', black: '#000000' };
  for (const [theme, color] of Object.entries(colors)) {
    await page.evaluate(t => window.__readerApp.settings.update({ theme: t }), theme);
    const expectedAppearance = theme === 'dark' || theme === 'black' ? 0 : sdk >= 26 ? 3 : 1;
    await page.waitForFunction(({ color, appearance }) => {
      const s = JSON.parse(window._nativeDisplayBridge.getDisplaySnapshot());
      return s.backgroundColor === color && s.appearance === appearance;
    }, { color, appearance: expectedAppearance });
    await capture(theme); // physical screenshot includes real status/navigation bars
  }
  await page.evaluate(() => window._nativeDisplayBridge.setTheme('arbitrary-theme', '#ffffff'));
  await delay(200);
  assert.equal((await snapshot(page)).backgroundColor, '#000000', 'Native whitelist accepted unknown theme');
  await page.evaluate(() => window._nativeDisplayBridge.setTheme('light', '#000000'));
  await delay(200);
  assert.equal((await snapshot(page)).backgroundColor, '#000000', 'Native whitelist accepted mismatched color');

  await page.evaluate(() => window.scrollTo(0, 1800));
  await delay(400);
  const before = await page.evaluate(() => window.__readerApp.reader.lastKnownPosition);
  await page.evaluate(() => window.__readerApp.settings.update({ immersiveReading: true }));
  await bars(page, false);
  await delay(400);
  const after = await page.evaluate(() => window.__readerApp.reader.lastKnownPosition);
  assert.equal(after.paragraph, before.paragraph);
  assert.ok(Math.abs(after.offset - before.offset) <= 1, 'Character anchor changed');
  await capture('immersive');

  await page.evaluate(() => window.__readerApp.openData());
  await bars(page, true);
  await page.getByRole('button', { name: '下载章节…', exact: true }).click();
  await delay(400);
  await bars(page, true);
  await page.locator('[data-testid="download-sheet"] [data-testid="sheet-close"]').click();
  await bars(page, false);

  await page.evaluate(() => window.__readerApp.search.open());
  await page.locator('.search-bar input').tap();
  await bars(page, true);
  await page.waitForFunction(() => JSON.parse(window._nativeDisplayBridge.getDisplaySnapshot()).ime > 0, null, { timeout: 10000 });
  await delay(400);
  const keyboard = await snapshot(page);
  assert.equal(keyboard.bottom, 0, 'Keyboard mistaken for navigation safe area');
  assert.equal(keyboard.webHeight, keyboard.hostHeight - keyboard.keyboardOverlap, 'IME deducted twice');
  const inputVisible = await page.locator('.search-bar input').evaluate(e => {
    const r = e.getBoundingClientRect();
    return r.top >= 0 && r.bottom <= innerHeight;
  });
  assert.ok(inputVisible, 'Focused input hidden behind keyboard');
  await capture('keyboard');
  adb('shell', 'input', 'keyevent', '4'); // system back dismisses IME first
  await page.waitForFunction(() => JSON.parse(window._nativeDisplayBridge.getDisplaySnapshot()).ime === 0);
  await page.evaluate(() => window.__readerApp.search.close());
  await bars(page, false);
  await page.evaluate(() => window.__readerApp.settings.update({ immersiveReading: false }));
  await bars(page, true);
  await delay(400);
  const restored = await snapshot(page);
  assert.equal(restored.keyboardOverlap, 0);
  assert.equal(restored.webHeight, restored.hostHeight, 'Blank bottom space after IME dismissal');

  const url = page.url();
  const anchor = await page.evaluate(() => window.__readerApp.reader.lastKnownPosition);
  adb('shell', 'settings', 'put', 'system', 'user_rotation', '1');
  await page.waitForFunction(() => innerWidth > innerHeight);
  await delay(500);
  assert.equal(page.url(), url, 'Rotation changed chapter');
  const rotated = await page.evaluate(() => window.__readerApp.reader.lastKnownPosition);
  assert.equal(rotated.paragraph, anchor.paragraph);
  await page.evaluate(() => window.__readerApp.tocView.open());
  const safe = await snapshot(page);
  const inputBounds = await page.locator('#toc-panel input').boundingBox();
  assert.ok(inputBounds.x >= safe.left, 'TOC input enters landscape cutout');
  await capture('landscape');
  adb('shell', 'input', 'keyevent', '3');
  adb('shell', 'am', 'start', '-W', '-n', `${PKG}/.MainActivity`);
  await bars(page, true);
  assert.equal(page.url(), url, 'Resume reloaded chapter');

  await writeFile(`${dir}/report.json`, JSON.stringify({
    automated: 'PASS', device: adb('shell', 'getprop', 'ro.product.model'), sdk,
    baseline, keyboard, title,
    manualPending: ['Review full-screen screenshots for icon contrast/cutout appearance', 'Transient edge-swipe reveal', 'Repeat in gesture and three-button modes; remaining OS/device matrix', 'Cold-start launch surface'],
  }, null, 2));
  console.log(`Device assertions passed. Full-screen screenshots: ${dir}. Test book retained: ${title}`);
} finally {
  if (connection && originalSettings) {
    await connection.page.evaluate(({ settings, raw }) => {
      window.__readerApp.settings.replace(settings);
      if (raw === null) localStorage.removeItem('reader.settings.v1');
      else localStorage.setItem('reader.settings.v1', raw);
    }, { settings: originalSettings, raw: rawSettings }).catch(() => {});
  }
  for (const [key, value] of [['user_rotation', rotation], ['accelerometer_rotation', autoRotation]]) {
    if (value !== undefined) {
      if (value === 'null') adb('shell', 'settings', 'delete', 'system', key);
      else adb('shell', 'settings', 'put', 'system', key, value);
    }
  }
  await connection?.close();
}
