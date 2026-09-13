/** Shared device-test helpers. No uninstall, pm clear, downgrade, or data deletion. */
import { execFileSync } from 'node:child_process';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';

export const PKG = 'org.wanshu.reader';
export const adb = (...args) => execFileSync(process.env.ADB || 'adb', args, { encoding: 'utf8', timeout: 120000 }).trim();
export const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

export async function connectReader() {
  adb('shell', 'am', 'start', '-W', '-n', `${PKG}/.MainActivity`);
  let socket;
  for (let i = 0; i < 60; i++) {
    const pid = adb('shell', 'pidof', PKG).split(' ')[0];
    socket = adb('shell', 'cat', '/proc/net/unix').match(new RegExp(`@?(webview_devtools_remote_${pid})\\b`))?.[1];
    if (socket) break;
    await delay(250);
  }
  assert.ok(socket, 'No debug WebView: install a DEBUG=1 APK on an authorized test device');
  const port = adb('forward', 'tcp:0', `localabstract:${socket}`);
  let browser;
  try {
    browser = await chromium.connectOverCDP(`http://127.0.0.1:${port}`);
    let page;
    for (let i = 0; i < 40; i++) {
      page = browser.contexts()[0].pages().find(p => p.url().startsWith('https://reader.local/'));
      if (page) break;
      await delay(250);
    }
    assert.ok(page, 'Trusted reader.local document not found');
    await page.waitForFunction(() => window.__readerApp && document.querySelector('#app[aria-busy="false"]'));
    return {
      page,
      async close() {
        await browser.close();
        adb('forward', '--remove', `tcp:${port}`);
      },
    };
  } catch (err) {
    await browser?.close();
    adb('forward', '--remove', `tcp:${port}`);
    throw err;
  }
}

/** Leaves a clearly named local test book on the device; never edits/deletes existing books. */
export async function seedLocalBook(page, prefix) {
  const title = `${prefix}-${Date.now()}`;
  await page.evaluate(() => { location.hash = '#/'; });
  await page.locator('.quick-card').filter({ hasText: '下载与缓存' }).click();
  const file = page.locator('input[type=file][accept*=".txt"]');
  await file.waitFor({ state: 'attached' });
  await file.setInputFiles({
    name: `${title}.txt`, mimeType: 'text/plain',
    buffer: Buffer.from(['第一章 显示测试', ...Array.from({ length: 80 }, (_, i) => `段落${i}：${'用于安全区与更新后的阅读位置验证。'.repeat(12)}`), '第二章 后续', '本地书第二章必须完整保留。'].join('\n\n')),
  });
  await page.locator('.quick-card').filter({ hasText: title }).getByRole('button', { name: '打开', exact: true }).click();
  await page.waitForSelector('#chapter-content p');
  return title;
}

export async function storageSnapshot(page) {
  return page.evaluate(async () => {
    const stores = await new Promise((resolve, reject) => {
      const r = indexedDB.open('reader-db');
      r.onerror = () => reject(r.error);
      r.onsuccess = () => {
        const db = r.result;
        const names = Array.from(db.objectStoreNames);
        const tx = db.transaction(names, 'readonly');
        const requests = names.map(name => ({ name, keys: tx.objectStore(name).getAllKeys(), values: tx.objectStore(name).getAll() }));
        tx.oncomplete = () => {
          resolve(Object.fromEntries(requests.map(r => [r.name, r.keys.result.map((key, i) => ({ key, value: r.values.result[i] }))])));
          db.close();
        };
        tx.onerror = () => { reject(tx.error); db.close(); };
      };
    });
    return {
      personal: localStorage.getItem('reader.personal.v1'),
      settings: localStorage.getItem('reader.settings.v1'),
      localBooks: localStorage.getItem('reader.localbooks.v1'),
      stores,
    };
  });
}

export function assertStoragePreserved(before, after) {
  for (const key of ['personal', 'settings', 'localBooks']) assert.equal(after[key], before[key], `${key} changed on upgrade`);
  // New remote cache entries are allowed; every pre-existing record must remain byte-for-byte equivalent.
  for (const [name, records] of Object.entries(before.stores)) {
    const next = new Map(after.stores[name]?.map(r => [JSON.stringify(r.key), r.value]));
    for (const record of records) assert.deepEqual(next.get(JSON.stringify(record.key)), record.value, `${name}/${JSON.stringify(record.key)} changed or disappeared`);
  }
}
