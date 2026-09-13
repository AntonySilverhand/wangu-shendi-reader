/**
 * Real in-place upgrade test. Use a spare/authorized test device, not your only data copy.
 * node tools/android-upgrade-adb.mjs OLD_DEBUG.apk NEW_DEBUG.apk --confirm-test-device
 * Keeps a named test book + progress/bookmark. Never uninstalls, clears data, or downgrades.
 */
import { execFileSync } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import { adb, PKG, connectReader, seedLocalBook, storageSnapshot, assertStoragePreserved, delay } from './android-device.mjs';

const [oldApk, newApk, confirmation] = process.argv.slice(2);
assert.ok(oldApk && newApk && confirmation === '--confirm-test-device', 'Usage: OLD_DEBUG.apk NEW_DEBUG.apk --confirm-test-device');
function metadata(path) {
  const badging = execFileSync(process.env.AAPT || 'aapt', ['dump', 'badging', path], { encoding: 'utf8' });
  const signature = execFileSync(process.env.APKSIGNER || 'apksigner', ['verify', '--print-certs', path], { encoding: 'utf8' });
  assert.equal(badging.match(/package: name='([^']+)'/)?.[1], PKG);
  assert.ok(badging.includes('application-debuggable'), `${path} is not a debug APK`);
  return { version: Number(badging.match(/versionCode='(\d+)'/)?.[1]), cert: signature.match(/certificate SHA-256 digest: (\w+)/)?.[1] };
}
const old = metadata(oldApk), next = metadata(newApk);
assert.ok(old.cert && next.cert);
assert.equal(next.cert, old.cert, 'Signing certificate changed; refuse installation');
assert.ok(next.version > old.version, 'versionCode must increase');
const installed = adb('shell', 'dumpsys', 'package', PKG).match(/versionCode=(\d+)/)?.[1];
assert.ok(!installed || Number(installed) <= old.version, 'Device already has a newer version; use another test device. No downgrade/uninstall will be performed.');

let connection;
try {
  adb('install', '-r', oldApk);
  connection = await connectReader();
  const title = await seedLocalBook(connection.page, 'upgrade-test');
  await connection.page.evaluate(() => window.scrollTo(0, 1800));
  await delay(500);
  await connection.page.evaluate(() => {
    window.__readerApp.reader.flushPosition();
    window.__readerApp.toggleBookmark();
    window.__readerApp.settings.update({ theme: 'paper', fontSize: 22, prefetch: false });
  });
  await delay(500);
  const before = await storageSnapshot(connection.page);
  const personal = JSON.parse(before.personal);
  assert.ok(Object.values(personal.books).some(b => b.progress?.paragraph > 0 && b.bookmarks.length > 0), 'Seeded progress/bookmark missing');
  assert.ok(before.stores.chapters.filter(r => r.value.source === 'local').length >= 2, 'Seeded TXT chapters missing');
  const anchor = await connection.page.evaluate(() => window.__readerApp.reader.lastKnownPosition);
  await connection.close(); connection = null;
  adb('shell', 'am', 'force-stop', PKG);
  adb('install', '-r', newApk); // Android preserves app data; no -d, uninstall, or pm clear.
  assert.equal(Number(adb('shell', 'dumpsys', 'package', PKG).match(/versionCode=(\d+)/)?.[1]), next.version);
  connection = await connectReader();
  const after = await storageSnapshot(connection.page);
  assertStoragePreserved(before, after);
  await connection.page.locator('.quick-card').filter({ hasText: title }).getByRole('button', { name: '打开', exact: true }).click();
  await connection.page.waitForFunction(() => window.__readerApp.reader.lastKnownPosition?.paragraph > 0);
  await delay(500);
  const restored = await connection.page.evaluate(() => window.__readerApp.reader.lastKnownPosition);
  assert.equal(restored.paragraph, anchor.paragraph);
  assert.ok(Math.abs(restored.offset - anchor.offset) <= 1, 'Upgrade changed character anchor');
  await mkdir('artifacts', { recursive: true });
  await writeFile('artifacts/android-upgrade-report.json', JSON.stringify({
    result: 'PASS', oldVersionCode: old.version, newVersionCode: next.version, cert: next.cert,
    device: adb('shell', 'getprop', 'ro.product.model'), origin: 'https://reader.local',
    recordsCompared: Object.fromEntries(Object.entries(before.stores).map(([name, rows]) => [name, rows.length])),
    title, paragraph: restored.paragraph, offset: restored.offset,
  }, null, 2));
  console.log(`In-place upgrade passed; all pre-existing IDB records and personal/settings/local-book data preserved. Test book retained: ${title}`);
} finally {
  await connection?.close();
}
