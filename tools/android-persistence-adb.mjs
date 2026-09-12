/** 真机 WebView/CDP force-stop 验证。需先安装 DEBUG=1 APK；不会 pm clear。 */
import { execFileSync } from 'node:child_process';
import { writeFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';
const PKG = 'org.wanshu.reader';
const adb = (...args) => execFileSync('adb', args, { encoding: 'utf8', timeout: 30000 }).trim();
const delay = (ms) => new Promise((r) => setTimeout(r, ms));
let port;
async function launch() {
  adb('shell', 'am', 'start', '-W', '-n', `${PKG}/.MainActivity`);
  let socket;
  for (let i = 0; i < 40; i++) {
    const pid = adb('shell', 'pidof', PKG).split(' ')[0];
    const sockets = adb('shell', 'cat', '/proc/net/unix');
    socket = sockets.match(new RegExp(`@?(webview_devtools_remote_${pid})\\b`))?.[1];
    if (socket) break;
    await delay(250);
  }
  assert.ok(socket, '找不到 WebView DevTools：确认安装的是 debug APK');
  port = adb('forward', 'tcp:0', `localabstract:${socket}`);
  const browser = await chromium.connectOverCDP(`http://127.0.0.1:${port}`);
  const page = browser.contexts()[0].pages().find((p) => p.url().startsWith('https://reader.local/'));
  assert.ok(page, 'synthetic HTTPS 主文档没有渲染');
  await page.waitForFunction(() => window.__readerApp && document.querySelector('.quick-card'), null, { timeout: 30000 });
  assert.equal(await page.evaluate(() => location.origin), 'https://reader.local');
  return page;
}
async function snapshot(page) {
  return page.evaluate(async () => {
    const stores = await new Promise((resolve, reject) => {
      const r = indexedDB.open('reader-db');
      r.onerror = () => reject(r.error);
      r.onsuccess = () => {
        const db = r.result;
        const tx = db.transaction(['chapters', 'toc']);
        const chapters = tx.objectStore('chapters').getAll();
        const toc = tx.objectStore('toc').getAll();
        tx.oncomplete = () => { resolve({ chapters: chapters.result, toc: toc.result }); db.close(); };
        tx.onerror = () => reject(tx.error);
      };
    });
    return {
      personal: localStorage.getItem('reader.personal.v1'),
      settings: localStorage.getItem('reader.settings.v1'),
      localBooks: localStorage.getItem('reader.localbooks.v1'),
      ...stores,
    };
  });
}
try {
  adb('shell', 'am', 'force-stop', PKG);
  let page = await launch();
  const title = `adb-persistence-${Date.now()}`;
  await page.click('.quick-card:has-text("下载与缓存")');
  await page.waitForSelector('input[type=file][accept*=".txt"]', { state: 'attached' });
  await page.evaluate((title) => {
    const text = ['第一章 起点', ...Array.from({ length: 60 }, (_, i) => `段落${i}：` + '测试本地阅读进度持久化。'.repeat(12)), '第二章 后续', '测试第二章。'].join('\n');
    const dt = new DataTransfer();
    dt.items.add(new File([text], `${title}.txt`, { type: 'text/plain' }));
    const input = document.querySelector('input[type=file][accept*=".txt"]');
    input.files = dt.files;
    input.dispatchEvent(new Event('change', { bubbles: true }));
  }, title);
  await page.locator('.quick-card').filter({ hasText: title }).getByRole('button', { name: '打开', exact: true }).click();
  await page.waitForSelector('#chapter-content p');
  await page.evaluate(() => window.scrollTo(0, 1800));
  await page.waitForTimeout(500); // 仅等待 scroll-end 取样；不是写入延迟 workaround。
  await page.evaluate(() => window.__readerApp.reader.showBars());
  await page.click('[data-testid=bookmark-btn]');
  await page.evaluate(() => window.__readerApp.settings.update({ theme: 'black', fontSize: 22 }));
  const before = await snapshot(page);
  assert.ok(before.chapters.length >= 2);
  assert.ok(Object.values(JSON.parse(before.personal).books).some((b) => b.progress?.paragraph > 0 && b.bookmarks.length > 0));
  // 不触发 pagehide/正常 close，直接 force-stop。
  adb('shell', 'am', 'force-stop', PKG);
  adb('forward', '--remove', `tcp:${port}`);
  page = await launch();
  const after = await snapshot(page);
  assert.deepEqual(after, before, 'force-stop 后个人数据、设置、TXT、IDB 必须完全相同');
  await page.locator('.quick-card').filter({ hasText: title }).getByRole('button', { name: '打开', exact: true }).click();
  await page.waitForFunction(() => window.__readerApp.reader.lastKnownPosition?.paragraph > 0 && window.scrollY > 100);
  await writeFile('artifacts/android-device-persistence.json', JSON.stringify({ result: 'PASS', origin: 'https://reader.local', device: adb('shell', 'getprop', 'ro.product.model'), chapterRecords: after.chapters.length }, null, 2));
  console.log('真机 force-stop 数据比对与阅读恢复通过。测试 TXT 保留在设备，可手动移除。');
} finally {
  if (port) adb('forward', '--remove', `tcp:${port}`);
}
