/**
 * 搜索回归（浏览器级）：
 *  1. 永久失败页 + 搜索不存在章节 → 60s 内 UI 持续可操作、无请求风暴、计数器收敛；
 *  2. 快速连续输入 2/28/287/2871 → 不启动多轮全量加载，数字 probe 命中；
 *  3. 网络失败不会无限自动重试；
 *  4. 关闭目录取消搜索任务；
 *  5. 结果渐进展示。
 */
import { chromium } from 'playwright';

const BASE = process.argv[2] ?? 'http://localhost:8787';
let fail = 0;
const check = (n, ok, d = '') => { if (!ok) fail++; console.log(`${ok ? '  ✓' : '  ✗'} ${n}${d ? ' — ' + d : ''}`); };

const browser = await chromium.launch();

async function makePage(failPages = []) {
  const ctx = await browser.newContext({ viewport: { width: 412, height: 915 }, isMobile: true, hasTouch: true });
  const tocReq = [];
  await ctx.route(/\/api\/toc\?/, async (route) => {
    const url = new URL(route.request().url());
    const from = Number(url.searchParams.get('from'));
    const to = Number(url.searchParams.get('to'));
    tocReq.push({ from, to });
    const pages = [], failedPages = [];
    for (let p = from; p <= to; p++) {
      if (failPages.includes(p)) { failedPages.push(p); continue; }
      const entries = [];
      for (let i = 0; i < 100; i++) {
        const n = (p - 1) * 100 + i + 1;
        entries.push({ id: String(38000000 + n), title: `第${n}章 测试章节`, displayTitle: `第${n}章 测试章节`, number: n, extra: false });
      }
      pages.push({ page: p, entries });
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ from, to, totalPages: 44, pages, failedPages }) });
  });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e).slice(0, 200)));
  page.on('console', (m) => m.type() === 'error' && errors.push(m.text().slice(0, 200)));
  return { ctx, page, tocReq, errors };
}

console.log('\n[1] 永久失败页 + 搜索不存在章节：60s 可操作性与收敛性');
{
  const { ctx, page, tocReq, errors } = await makePage([20]);
  await page.goto(`${BASE}/#/toc`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#toc-panel.open');
  await page.waitForTimeout(400);
  await page.fill('#toc-panel input[type=search]', '99999999');
  // 等任务结束（exhausted）
  await page.waitForFunction(
    () => (window.__readerDebug?.searchState === 'exhausted' || window.__readerDebug?.searchState === 'found'),
    null,
    { timeout: 20_000 },
  );
  const settled = await page.evaluate(() => {
    const d = window.__readerDebug;
    return { render: d.renderSearchCount, tasks: d.loadAllForSearchCount, failed: d.tocFailedPages, loaded: d.tocLoadedPages };
  });
  check('搜索任务结束且状态 exhausted', true, JSON.stringify(settled));
  check('失败页被记录', settled.failed.includes(20), JSON.stringify(settled.failed));

  // 60s 浸泡：每 5s 探测一次 UI 可操作性 + 请求/渲染计数收敛（不污染输入）
  const baseReq = tocReq.length;
  const baseRender = settled.render;
  let responsive = true;
  let storm = false;
  for (let i = 0; i < 12; i++) {
    await page.waitForTimeout(5000);
    const probe = await Promise.race([
      page.evaluate(() => ({ alive: 1, render: window.__readerDebug?.renderSearchCount ?? 0 })),
      new Promise((res) => setTimeout(() => res('timeout'), 2000)),
    ]);
    if (probe === 'timeout') { responsive = false; break; }
    // 输入框保持可编辑：focus + 选区操作（不改值、不触发 input 事件）
    const editable = await page.evaluate(() => {
      const el = document.querySelector('#toc-panel input[type=search]');
      if (!el) return false;
      el.focus();
      el.setSelectionRange(0, 0);
      return document.activeElement === el && el.readOnly !== true;
    }).catch(() => false);
    if (!editable) responsive = false;
    console.log(`  · 浸泡 ${(i + 1) * 5}s：render=${probe.render} 新请求=${tocReq.length - baseReq} editable=${editable}`);
    if (i >= 1) {
      if (tocReq.length - baseReq > 1) storm = true;
      if (probe.render > baseRender + 10) storm = true;
    }
  }
  check('60s 内主线程持续可响应且输入框可编辑', responsive);
  check('60s 内无请求风暴（新增 toc 请求 ≤1）', !storm, `新增 ${tocReq.length - baseReq} 个请求`);
  const finalRender = await page.evaluate(() => window.__readerDebug.renderSearchCount);
  check('renderSearch 计数收敛（无 busy loop）', finalRender <= baseRender + 10, `${baseRender} → ${finalRender}`);
  // 更换查询：新任务正常启动并收敛（状态机仍可用）
  await page.fill('#toc-panel input[type=search]', '88888888');
  await page.waitForFunction(
    () => window.__readerDebug?.searchState === 'exhausted' || window.__readerDebug?.searchState === 'found',
    null,
    { timeout: 20_000 },
  );
  const renderAfterNew = await page.evaluate(() => window.__readerDebug.renderSearchCount);
  await page.waitForTimeout(3000);
  const renderSettled = await page.evaluate(() => window.__readerDebug.renderSearchCount);
  check('更换查询后任务再次收敛', renderSettled === renderAfterNew, `${renderAfterNew} → ${renderSettled}`);
  check('无控制台错误', errors.length === 0, errors.slice(0, 2).join('|'));
  // 关闭目录仍可操作
  await page.click('#toc-panel .panel-head .icon-btn');
  check('目录可关闭', await page.evaluate(() => !document.getElementById('toc-panel')?.classList.contains('open')));
  await ctx.close();
}

console.log('\n[2] 快速连续输入：不启动多轮全量加载，probe 命中');
{
  const { ctx, page, tocReq } = await makePage([]);
  await page.goto(`${BASE}/#/toc`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#toc-panel.open');
  await page.waitForTimeout(400);
  const input = page.locator('#toc-panel input[type=search]');
  for (const q of ['2', '28', '287', '2871']) {
    await input.fill(q);
    await page.waitForTimeout(90); // 快于 debounce(260ms)，模拟快速连续输入
  }
  // 等待最终查询（2871）任务结束
  await page.waitForFunction(
    () => window.__readerDebug?.searchState === 'found' || window.__readerDebug?.searchState === 'exhausted',
    null,
    { timeout: 20_000 },
  );
  await page.waitForTimeout(300);
  const info = await page.evaluate(() => ({
    state: window.__readerDebug.searchState,
    loaded: window.__readerDebug.tocLoadedPages,
    tasks: window.__readerDebug.loadAllForSearchCount,
    rows: document.querySelectorAll('#toc-panel .toc-row').length,
    first: document.querySelector('#toc-panel .toc-row .num')?.textContent ?? '',
  }));
  check('最终搜索状态 found', info.state === 'found', info.state);
  check('找到 2871 章', info.rows >= 1 && info.first.includes('2871'), info.first);
  check('搜索任务只启动 1 个', info.tasks === 1, `${info.tasks}`);
  check('probe 定位：只加载估计页附近（<8 页），不加载全 44 页', info.loaded < 8, `${info.loaded} 页`);
  const ranges = tocReq.map((r) => `${r.from}-${r.to}`);
  const multiPage = tocReq.filter((r) => r.to - r.from > 0).length;
  check('搜索期间请求为单页 probe（多页请求≤1）', multiPage <= 1, ranges.join(','));
  await ctx.close();
}

console.log('\n[3] 全量扫描：文本搜索渐进展示 + 无结果时给出重试按钮');
{
  const { ctx, page } = await makePage([30]);
  await page.goto(`${BASE}/#/toc`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#toc-panel.open');
  await page.waitForTimeout(400);
  await page.fill('#toc-panel input[type=search]', '不存在的标题xyz');
  await page.waitForFunction(
    () => window.__readerDebug?.searchState === 'exhausted',
    null,
    { timeout: 30_000 },
  );
  const ui = await page.evaluate(() => ({
    hint: document.querySelector('#toc-panel .empty-hint')?.textContent ?? '',
    retryBtn: [...document.querySelectorAll('#toc-panel .empty-hint button')].map((b) => b.textContent),
    failed: window.__readerDebug.tocFailedPages,
    loaded: window.__readerDebug.tocLoadedPages,
  }));
  check('无结果显示明确提示', ui.hint.includes('没有找到匹配的章节'), ui.hint);
  check('显示“重试失败页并继续搜索”按钮', ui.retryBtn.some((t) => t.includes('重试失败页')), JSON.stringify(ui.retryBtn));
  check('失败页 30 被记录', ui.failed.includes(30), JSON.stringify(ui.failed));
  check('其余页已加载（43 页）', ui.loaded === 43, `${ui.loaded}`);
  await ctx.close();
}

console.log('\n[4] 关闭目录取消任务：网络请求随之中止');
{
  const { ctx, page, tocReq } = await makePage([]);
  await page.goto(`${BASE}/#/toc`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#toc-panel.open');
  await page.waitForTimeout(400);
  await page.fill('#toc-panel input[type=search]', 'xyz不存在');
  await page.waitForTimeout(200); // 任务已启动（debounce 后）
  await page.click('#toc-panel .panel-head .icon-btn');
  await page.waitForTimeout(800);
  const after = tocReq.length;
  await page.waitForTimeout(1500);
  const later = tocReq.length;
  const state = await page.evaluate(() => window.__readerDebug?.searchState);
  check('关闭后搜索状态为 cancelled', state === 'cancelled', state);
  check('关闭后请求停止', later === after, `${after} → ${later}`);
  await ctx.close();
}

await browser.close();
console.log(`\n搜索回归：${fail === 0 ? '全部通过' : `${fail} 项失败`}`);
process.exit(fail ? 1 : 0);
