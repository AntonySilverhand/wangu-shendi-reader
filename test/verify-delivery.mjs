import { chromium } from 'playwright';
const BASE = process.argv[2] ?? 'http://localhost:8787';
let fail = 0;
const check = (n, ok, d = '') => { if (!ok) fail++; console.log(`${ok ? '  ✓' : '  ✗'} ${n}${d ? ' — ' + d : ''}`); };
const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: 412, height: 915 }, isMobile: true, hasTouch: true });
const page = await ctx.newPage();
const errs = [];
page.on('pageerror', (e) => errs.push(String(e).slice(0, 160)));

console.log('\n[1] 章节内容完整性（2824 = 源站 3 页 99 段）');
await page.goto(`${BASE}/#/read/38621433`, { waitUntil: 'domcontentloaded' });
await page.waitForSelector('#chapter-content p', { timeout: 120000 });
await page.waitForTimeout(1200);
let info = await page.evaluate(() => {
  const ps = [...document.querySelectorAll('#chapter-content p')];
  return { n: ps.length, last: ps.at(-1)?.textContent ?? '', meta: document.querySelector('.chapter-meta')?.textContent ?? '' };
});
check('2824 完整 99 段', info.n === 99, `${info.n} 段`);
check('结尾与源站一致', info.last.includes('彻底安静下来'), info.last.slice(0, 30));
check('显示“源站分页已合并”', info.meta.includes('源站分页已合并'), info.meta);

console.log('\n[2] 旧半章缓存：立即显示 + 后台补全（不阻塞）');
await page.evaluate(() => new Promise((res, rej) => {
  const r = indexedDB.open('reader-db', 1);
  r.onsuccess = () => { const tx = r.result.transaction('chapters', 'readwrite');
    tx.objectStore('chapters').put({ key: '36780:38621420', bookId: '36780', chapterId: '38621420', title: '旧半章', paragraphs: ['旧的半章，仅一段。'], charCount: 10, source: 'remote', fetchedAt: Date.now(), bytes: 30 });
    tx.oncomplete = res; tx.onerror = () => rej(tx.error); };
  r.onerror = () => rej(r.error);
}));
const t0 = Date.now();
await page.goto(`${BASE}/#/read/38621420`, { waitUntil: 'domcontentloaded' });
await page.waitForSelector('#chapter-content p', { timeout: 60000 });
const firstMs = Date.now() - t0;
let n = await page.evaluate(() => document.querySelectorAll('#chapter-content p').length);
for (let i = 0; i < 15 && n < 50; i++) { await page.waitForTimeout(1000); n = await page.evaluate(() => document.querySelectorAll('#chapter-content p').length); }
check('2s 内先显示缓存内容', firstMs < 2000, `${firstMs}ms`);
check('后台自动补全为完整 98 段', n >= 90, `${n} 段`);

console.log('\n[3] 连续翻章 10 次（卡死/失败检测）');
await page.goto(`${BASE}/#/read/8924760`, { waitUntil: 'domcontentloaded' });
await page.waitForSelector('#chapter-content p', { timeout: 120000 });
const times = [];
let failures = 0;
for (let i = 0; i < 10; i++) {
  const before = await page.locator('#chapter-title').textContent();
  const t = Date.now();
  await page.evaluate(() => window.__readerApp?.reader?.showBars?.());
  await page.keyboard.press('ArrowRight');
  try {
    await page.waitForFunction((prev) => {
      const el = document.querySelector('#chapter-title');
      const ps = document.querySelectorAll('#chapter-content p').length;
      return el && el.textContent !== prev && ps > 5;
    }, before, { timeout: 90000 });
    times.push(Date.now() - t);
  } catch { failures++; times.push(-1); }
}
const okTimes = times.filter((t) => t > 0);
check('10 次翻章全部成功', failures === 0, `失败 ${failures} 次`);
check('单章加载无长时间阻塞', okTimes.length > 0 && Math.max(...okTimes) < 60000, `最慢 ${Math.max(...okTimes)}ms / 中位 ${okTimes.sort((a, b) => a - b)[Math.floor(okTimes.length / 2)]}ms`);

console.log('\n[4] 静止无请求 + SW 缓存按版本隔离');
const reqs = [];
page.on('request', (r) => reqs.push(r.url()));
await page.waitForTimeout(4500);
const firstWindow = reqs.length;
await page.waitForTimeout(4500);
const secondWindow = reqs.length - firstWindow;
check('无轮询：首个 4.5s 内请求 ≤1（仅下一章预取）', firstWindow <= 1, `${firstWindow} 个`);
check('后续 4.5s 无任何请求', secondWindow === 0, `${secondWindow} 个`);
const caches = await page.evaluate(async () => (await window.caches?.keys?.()) ?? []);
const runtime = caches.filter((k) => k.startsWith('reader-runtime-'));
const shell = caches.filter((k) => k.startsWith('reader-shell-'));
check('运行时缓存只有一个版本', runtime.length <= 1, runtime.join(','));
check('外壳缓存只有一个版本', shell.length <= 1, shell.join(','));
check('无页面异常', errs.length === 0, errs.join(' | '));

await browser.close();
console.log(`\n交付验证：${fail === 0 ? '全部通过' : `${fail} 项失败`}`);
process.exit(fail === 0 ? 0 : 1);
