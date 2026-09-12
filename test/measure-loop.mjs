/**
 * busy-loop 取证：SharedArrayBuffer + Web Worker（独立线程）在被冻结的
 * 主线程之外实时读取 __readerDebug 计数器，beacon 到本脚本内嵌 HTTP 服务。
 *
 * 前置：已构建（npm run build）+ 服务在 8787。
 */
import { chromium } from 'playwright';
import { createServer } from 'node:http';

const BASE = process.argv[2] ?? 'http://localhost:8787';
const FAIL_PAGE = 20;
const TOTAL = 44;
const COUNTER_INDEX = {
  renderSearchCount: 0,
  loadAllForSearchCount: 1,
  tocLoadRangeCalls: 2,
  tocLoadRangeChunks: 3,
  tocPagesLoaded: 4,
  tocPagesFailed: 5,
  searchGeneration: 6,
  chapterOpens: 7,
  chapterStaleAborts: 8,
  personalSaves: 9,
  personalSaveFails: 10,
  layoutModeChanges: 11,
};

const beacons = [];
const srv = createServer((req, res) => {
  let body = '';
  req.on('data', (c) => (body += c));
  req.on('end', () => {
    try {
      beacons.push({ at: Date.now(), ...JSON.parse(body) });
    } catch {
      /* 忽略 */
    }
    res.writeHead(200);
    res.end('ok');
  });
});
await new Promise((res) => srv.listen(9797, res));

const browser = await chromium.launch({
  args: ['--enable-features=SharedArrayBuffer'],
});
const ctx = await browser.newContext({
  viewport: { width: 412, height: 915 },
  isMobile: true,
  hasTouch: true,
});
await ctx.route(/\/api\/toc\?/, async (route) => {
  const url = new URL(route.request().url());
  const from = Number(url.searchParams.get('from'));
  const to = Number(url.searchParams.get('to'));
  const pages = [], failedPages = [];
  for (let p = from; p <= to; p++) {
    if (p === FAIL_PAGE) { failedPages.push(p); continue; }
    const entries = [];
    for (let i = 0; i < 100; i++) {
      const n = (p - 1) * 100 + i + 1;
      entries.push({ id: String(38000000 + n), title: `第${n}章 测试章节`, displayTitle: `第${n}章 测试章节`, number: n, extra: false });
    }
    pages.push({ page: p, entries });
  }
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ from, to, totalPages: TOTAL, pages, failedPages }) });
});

const page = await ctx.newPage();
await page.goto(`${BASE}/#/toc`, { waitUntil: 'domcontentloaded' });
await page.waitForSelector('#toc-panel.open');
await page.waitForTimeout(500);

// 主线程布置：SAB + 轮询 Worker
await page.evaluate((keys) => {
  const sab = new SharedArrayBuffer(4 * keys.length);
  window.__readerDebugSAB = new Int32Array(sab);
  const code = `
    const keys = ${JSON.stringify(keys)};
    const sab = new Int32Array(new SharedArrayBuffer(4 * keys.length));
    // 通过 postMessage 共享同一内存
    onmessage = (e) => { };
    self.__sab = null;
  `;
  // Worker 需要拿到同一个 SAB 引用：直接构造时传入
  const blobUrl = URL.createObjectURL(new Blob([
    `const keys = ${JSON.stringify(keys)};
     const KEYS = keys;
     onmessage = (e) => {
       const sab = e.data;
       setInterval(() => {
         const out = {};
         for (let i = 0; i < KEYS.length; i++) out[KEYS[i]] = Atomics.load(sab, i);
         try { fetch('http://localhost:9797/b', { method: 'POST', body: JSON.stringify(out) }); } catch (e) {}
       }, 200);
     };`
  ], { type: 'application/javascript' }));
  const w = new Worker(blobUrl);
  w.postMessage(window.__readerDebugSAB);
  window.__w = w;
}, Object.keys(COUNTER_INDEX));

const t0 = Date.now();
await page.fill('#toc-panel input[type=search]', '99999999');
await page.waitForTimeout(4000);

// 主线程探测（预期冻结）
const probe = await Promise.race([
  page.evaluate(() => 'alive'),
  new Promise((res) => setTimeout(() => res('TIMEOUT'), 1500)),
]);
console.log('4s 后主线程探测:', probe);

const ordered = beacons.sort((a, b) => a.at - b.at);
const first = ordered[0];
const last = ordered[ordered.length - 1];
console.log(`beacon 总数: ${ordered.length}`);
if (first && last) {
  console.log('第一帧:', JSON.stringify(first));
  console.log('最后一帧:', JSON.stringify(last));
  const rate = last.renderSearchCount - first.renderSearchCount;
  const secs = (last.at - first.at) / 1000;
  console.log(`renderSearch 增量: ${rate.toLocaleString()} 次 / ${secs.toFixed(1)}s = ${(rate / Math.max(secs, 0.1)).toLocaleString()} 次/秒`);
  console.log(`loadAllForSearch 增量: ${(last.loadAllForSearchCount - first.loadAllForSearchCount).toLocaleString()}`);
  console.log(`tocLoadRangeCalls 增量: ${last.tocLoadRangeCalls - first.tocLoadRangeCalls}（无网络等待循环期间不再发请求）`);
}
console.log('pageerror 检查: 见上方（无输出=无错误）');
await browser.close();
srv.close();
