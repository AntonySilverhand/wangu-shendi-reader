/**
 * 端到端验证脚本（Playwright + Chromium）。
 * 前置：npm run build && node --experimental-strip-types src/server/dev.ts
 * 运行：node test/e2e.mjs [baseUrl]
 */
import { chromium, devices } from 'playwright';
import { mkdirSync, writeFileSync } from 'node:fs';
import { deflateRawSync } from 'node:zlib';

const BASE = process.argv[2] ?? 'http://localhost:8787';
const PART = process.argv[3] ?? 'all';
const runPart = (n) => PART === 'all' || PART === String(n);
const SHOT_DIR = new URL('../artifacts/', import.meta.url).pathname;
mkdirSync(SHOT_DIR, { recursive: true });

const results = [];
let failures = 0;
function check(name, ok, detail = '') {
  results.push({ name, ok, detail });
  if (!ok) failures++;
  console.log(`${ok ? '  ✓' : '  ✗'} ${name}${detail ? ` — ${detail}` : ''}`);
}

const VIEWPORTS = [
  { name: 'phone-320', width: 320, height: 568, isMobile: true, hasTouch: true, deviceScaleFactor: 2 },
  { name: 'phone-412', width: 412, height: 915, isMobile: true, hasTouch: true, deviceScaleFactor: 2.6 },
  { name: 'fold-outer', width: 344, height: 882, isMobile: true, hasTouch: true, deviceScaleFactor: 2.5 },
  { name: 'fold-inner', width: 673, height: 841, isMobile: true, hasTouch: true, deviceScaleFactor: 2 },
  { name: 'tablet-820', width: 820, height: 1180, isMobile: true, hasTouch: true, deviceScaleFactor: 2 },
  { name: 'phone-landscape', width: 915, height: 412, isMobile: true, hasTouch: true, deviceScaleFactor: 2.5 },
  { name: 'desktop-1440', width: 1440, height: 900, isMobile: false, hasTouch: false, deviceScaleFactor: 1 },
  { name: 'desktop-1920', width: 1920, height: 1080, isMobile: false, hasTouch: false, deviceScaleFactor: 1 },
];

async function newContext(browser, vp, colorScheme = 'light') {
  const context = await browser.newContext({
    viewport: { width: vp.width, height: vp.height },
    isMobile: vp.isMobile,
    hasTouch: vp.hasTouch,
    deviceScaleFactor: vp.deviceScaleFactor,
    colorScheme,
    locale: 'zh-CN',
  });
  return context;
}

async function waitForChapter(page, timeout = 90_000) {
  try {
    await page.waitForSelector('#chapter-content p', { timeout });
    await page.waitForFunction(() => document.querySelectorAll('#chapter-content p').length > 5, null, {
      timeout,
    });
  } catch (err) {
    const state = await page
      .evaluate(() => ({
        url: location.href,
        view: document.getElementById('app')?.dataset.view,
        paragraphs: document.querySelectorAll('#chapter-content p').length,
        errorBox: document.querySelector('.error-box')?.textContent?.slice(0, 160) ?? null,
        banner: document.querySelector('.banner')?.textContent?.slice(0, 160) ?? null,
        loading: Boolean(document.querySelector('.skeleton-line')),
      }))
      .catch(() => null);
    console.log('  [diagnostic] 章节未渲染：', JSON.stringify(state));
    throw err;
  }
}

async function openFirstChapter(page) {
  await page.goto(`${BASE}/#/`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#continue-reading', { timeout: 30_000 });
  await page.click('#continue-reading');
  await waitForChapter(page);
}

/** 工具栏可能因滚动自动隐藏，点击顶栏按钮前先唤出 */
async function showBars(page) {
  await page.evaluate(() => {
    const app = window.__readerApp;
    if (app && app.reader && typeof app.reader.showBars === 'function') app.reader.showBars();
  });
  await page.waitForTimeout(120);
}

async function main() {
  const browser = await chromium.launch();
  const context = await newContext(browser, VIEWPORTS[1]);
  const page = await context.newPage();
  const consoleErrors = [];
  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      consoleErrors.push(msg.text());
      console.log(`  [console.error] ${msg.text().slice(0, 200)}`);
    }
  });
  page.on('pageerror', (err) => {
    consoleErrors.push(String(err));
    console.log(`  [pageerror] ${String(err).slice(0, 300)}`);
  });

  if (runPart(1)) {
  console.log('\n[1] 首屏与基本阅读');
  await openFirstChapter(page);
  const paraCount = await page.locator('#chapter-content p').count();
  check('章节能加载并渲染段落', paraCount > 5, `${paraCount} 段`);
  const title = await page.locator('#chapter-title').textContent();
  check('章节标题可见', Boolean(title && title.length > 1), title ?? '');
  writeFileSync(`${SHOT_DIR}e2e-src.txt`, `chapter: ${title}\n`);

  console.log('\n[2] 四种主题与纯黑');
  const themes = ['light', 'dark', 'black', 'eink', 'paper'];
  for (const theme of themes) {
    await page.evaluate((t) => {
      const raw = JSON.parse(localStorage.getItem('reader.settings.v1') ?? '{}');
      raw.theme = t;
      localStorage.setItem('reader.settings.v1', JSON.stringify(raw));
    }, theme);
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#chapter-content p', { timeout: 90_000 });
    const applied = await page.evaluate(() => ({
      theme: document.documentElement.getAttribute('data-theme'),
      bg: getComputedStyle(document.body).backgroundColor,
      color: getComputedStyle(document.body).color,
      overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
    }));
    check(`主题 ${theme} 生效`, applied.theme === theme, JSON.stringify(applied));
    check(`主题 ${theme} 无横向溢出`, applied.overflow <= 1, `overflow=${applied.overflow}`);
    check(`主题 ${theme} 背景色已应用`, applied.bg !== 'rgba(0, 0, 0, 0)', applied.bg);
  }
  // 恢复暗色
  await page.evaluate(() => {
    const raw = JSON.parse(localStorage.getItem('reader.settings.v1') ?? '{}');
    raw.theme = 'dark';
    localStorage.setItem('reader.settings.v1', JSON.stringify(raw));
  });

  console.log('\n[3] 排版设置与阅读位置锚定');
  await page.goto(`${BASE}/#/`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#continue-reading');
  await page.click('#continue-reading');
  await waitForChapter(page);
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight * 0.4));
  await page.waitForTimeout(600);
  const before = await page.evaluate(() => {
    const app = window.__readerApp;
    return app ? app.reader.lastKnownPosition : null;
  });
  await page.evaluate(() => {
    const raw = JSON.parse(localStorage.getItem('reader.settings.v1') ?? '{}');
    raw.fontSize = 24;
    raw.lineHeight = 2.1;
    localStorage.setItem('reader.settings.v1', JSON.stringify(raw));
  });
  await page.reload({ waitUntil: 'domcontentloaded' });
  await waitForChapter(page);
  await page.waitForTimeout(800);
  const after = await page.evaluate(() => {
    const app = window.__readerApp;
    return app ? app.reader.lastKnownPosition : null;
  });
  const anchorOk =
    before && after && Math.abs(before.paragraph - after.paragraph) <= 2;
  check(
    '改字号后保持文本位置',
    Boolean(anchorOk),
    `before p${before?.paragraph} after p${after?.paragraph}`,
  );
  const fontSize = await page.evaluate(() =>
    getComputedStyle(document.querySelector('#chapter-content')).fontSize,
  );
  check('字号已应用', fontSize === '24px', fontSize);
  await page.evaluate(() => {
    const raw = JSON.parse(localStorage.getItem('reader.settings.v1') ?? '{}');
    raw.fontSize = 18;
    raw.lineHeight = 1.8;
    localStorage.setItem('reader.settings.v1', JSON.stringify(raw));
  });

  console.log('\n[4] 目录、搜索与跳章');
  await page.goto(`${BASE}/#/`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#continue-reading');
  await page.click('#continue-reading');
  await waitForChapter(page);
  await page.click('[data-testid=toc-btn]');
  await page.waitForSelector('#toc-panel.open', { timeout: 10_000 });
  await page.waitForSelector('#toc-panel .toc-row', { timeout: 30_000 });
  await page.waitForTimeout(800);
  const rows = await page.locator('#toc-panel .toc-row').count();
  check('目录行已渲染', rows > 3, `${rows} 行`);
  await page.fill('#toc-panel input[type=search]', '2771');
  await page.waitForSelector('#toc-panel .toc-row', { timeout: 300_000 });
  const searchRows = await page.locator('#toc-panel .toc-row').count();
  check('目录搜索有结果', searchRows >= 1, `${searchRows} 条`);
  await page.locator('#toc-panel .toc-row').first().click();
  await page.waitForFunction(
    () => /^#\/book\/[^/]+\/read\//.test(location.hash) && document.querySelectorAll('#chapter-content p').length > 5,
    null,
    { timeout: 90_000 },
  );
  const jumpedTitle = await page.locator('#chapter-title').textContent();
  check('搜索结果可跳转', Boolean(jumpedTitle), jumpedTitle ?? '');

  console.log('\n[5] 章内搜索与快捷键');
  await showBars(page);
  await page.click('[data-testid=search-btn]');
  await page.fill('#chapter-search input', '张若尘');
  await page.waitForTimeout(500);
  const countText = await page.locator('#chapter-search .search-count').textContent();
  check('章内搜索命中', countText !== '0/0', countText ?? '');
  await page.keyboard.press('Escape');
  const searchClosed = await page.evaluate(
    () => !document.querySelector('#chapter-search')?.classList.contains('open'),
  );
  check('Esc 关闭搜索', searchClosed);
  const chapterBeforeKey = await page.locator('#chapter-title').textContent();
  await page.keyboard.press('ArrowRight');
  await page.waitForFunction(
    (prev) => {
      const el = document.querySelector('#chapter-title');
      return el && el.textContent !== prev && document.querySelectorAll('#chapter-content p').length > 5;
    },
    chapterBeforeKey,
    { timeout: 90_000 },
  );
  check('→ 键切换下一章', true);

  console.log('\n[6] 书签与进度持久化');
  await showBars(page);
  await page.click('[data-testid=bookmark-btn]');
  await page.waitForTimeout(900);
  const bookmarksAfterAdd = await page.evaluate(() => {
    const data = JSON.parse(localStorage.getItem('reader.personal.v1') ?? '{}');
    const books = Object.values(data.books ?? {});
    return books.reduce((n, b) => n + (b.bookmarks?.length ?? 0), 0);
  });
  check('书签已写入本地', bookmarksAfterAdd >= 1, `${bookmarksAfterAdd} 条`);
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1200);
  const bookmarksAfterReload = await page.evaluate(() => {
    const data = JSON.parse(localStorage.getItem('reader.personal.v1') ?? '{}');
    const books = Object.values(data.books ?? {});
    return books.reduce((n, b) => n + (b.bookmarks?.length ?? 0), 0);
  });
  check('刷新后书签保留', bookmarksAfterReload === bookmarksAfterAdd, `${bookmarksAfterReload} 条`);

  console.log('\n[7] 离线阅读已缓存章节');
  await page.goto(`${BASE}/#/`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#continue-reading', { timeout: 30_000 });
  await page.click('#continue-reading');
  await waitForChapter(page);
  const currentHash = await page.evaluate(() => location.hash);
  await context.setOffline(true);
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(800);
  const offlineState = await page.evaluate(() => ({
    hash: location.hash,
    net: document.querySelector('#net-status')?.classList.contains('show'),
  }));
  check('离线指示显示', offlineState.net === true, JSON.stringify(offlineState));
  await context.setOffline(false);

  }
  if (runPart(2)) {
  console.log('\n[8] 多视口布局与横向溢出');
  for (const vp of VIEWPORTS) {
    const ctx = await newContext(browser, vp);
    const p = await ctx.newPage();
    await p.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' });
    await p.waitForSelector('#continue-reading', { timeout: 30_000 });
    await p.click('#continue-reading');
    await waitForChapter(p);
    const info = await p.evaluate(() => {
      const doc = document.documentElement;
      const content = document.querySelector('#chapter-content');
      const rect = content.getBoundingClientRect();
      return {
        overflowX: doc.scrollWidth - doc.clientWidth,
        contentWidth: Math.round(rect.width),
        viewport: window.innerWidth,
        topbarVisible: getComputedStyle(document.querySelector('.topbar')).display !== 'none',
        bottombarVisible: getComputedStyle(document.querySelector('.bottombar')).display !== 'none',
      };
    });
    check(
      `${vp.name} 无横向溢出`,
      info.overflowX <= 1,
      `overflow=${info.overflowX} content=${info.contentWidth} vp=${info.viewport}`,
    );
    const isWide = vp.width >= 960;
    check(
      `${vp.name} 正文限宽合理`,
      isWide ? info.contentWidth <= 900 : info.contentWidth <= info.viewport,
      `content=${info.contentWidth}`,
    );
    await p.screenshot({ path: `${SHOT_DIR}layout-${vp.name}.png`, fullPage: false });
    await ctx.close();
  }

  console.log('\n[9] 连续窗口缩放（模拟桌面窗口拖动）');
  const resizeCtx = await newContext(browser, VIEWPORTS[6]);
  const rp = await resizeCtx.newPage();
  await rp.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' });
  await rp.waitForSelector('#continue-reading');
  await rp.click('#continue-reading');
  await waitForChapter(rp);
  await rp.evaluate(() => window.scrollTo(0, 1200));
  await rp.waitForTimeout(500);
  const anchorBefore = await rp.evaluate(() => window.__readerApp?.reader.lastKnownPosition ?? null);
  for (const w of [1200, 900, 700, 1100, 1440]) {
    await rp.setViewportSize({ width: w, height: 900 });
    await rp.waitForTimeout(220);
  }
  const anchorAfter = await rp.evaluate(() => window.__readerApp?.reader.lastKnownPosition ?? null);
  check(
    '连续缩放后位置不跳回章首',
    anchorBefore && anchorAfter && Math.abs(anchorAfter.paragraph - anchorBefore.paragraph) <= 3,
    `before p${anchorBefore?.paragraph} after p${anchorAfter?.paragraph}`,
  );
  await resizeCtx.close();

  console.log('\n[10] 输入方式与可访问性基础');
  const deskCtx = await newContext(browser, VIEWPORTS[6]);
  const dp = await deskCtx.newPage();
  await dp.goto(`${BASE}/`, { waitUntil: 'domcontentloaded' });
  await dp.waitForSelector('#continue-reading');
  await dp.click('#continue-reading');
  await waitForChapter(dp);
  await dp.click('[data-testid=settings-btn]');
  await dp.waitForSelector('[data-testid=settings-sheet]');
  const focusInSheet = await dp.evaluate(() =>
    Boolean(document.activeElement && document.activeElement.closest('[data-testid=settings-sheet]')),
  );
  check('设置面板打开后有初始焦点', focusInSheet);
  await dp.keyboard.press('Tab');
  const focusAfterTab = await dp.evaluate(() => document.activeElement?.tagName);
  check('Tab 可移动焦点', Boolean(focusAfterTab), focusAfterTab ?? '');
  await dp.keyboard.press('Escape');
  await dp
    .waitForSelector('[data-testid=settings-sheet]', { state: 'detached', timeout: 5000 })
    .catch(() => undefined);
  const sheetClosed = await dp.evaluate(
    () => !document.querySelector('[data-testid=settings-sheet]'),
  );
  check('Esc 关闭设置面板', sheetClosed);
  const wheelBefore = await dp.evaluate(() => window.scrollY);
  await dp.mouse.wheel(0, 600);
  await dp.waitForTimeout(300);
  const wheelAfter = await dp.evaluate(() => window.scrollY);
  check('鼠标滚轮可滚动阅读', wheelAfter > wheelBefore, `${wheelBefore} → ${wheelAfter}`);
  await deskCtx.close();

  console.log('\n[11] 性能与静止状态');
  const perfCtx = await newContext(browser, VIEWPORTS[1]);
  const perfPage = await perfCtx.newPage();
  const requests = [];
  perfPage.on('request', (req) => requests.push({ url: req.url(), at: Date.now() }));
  const perfErrors = [];
  perfPage.on('console', (m) => m.type() === 'error' && perfErrors.push(m.text()));
  perfPage.on('pageerror', (e) => perfErrors.push(String(e)));
  const t0 = Date.now();
  await perfPage.goto(`${BASE}/`, { waitUntil: 'load' });
  const loadMs = Date.now() - t0;
  await perfPage.waitForSelector('#continue-reading', { timeout: 30_000 });
  await perfPage.click('#continue-reading');
  await waitForChapter(perfPage);
  const interactiveMs = Date.now() - t0;
  check('启动到可交互耗时 < 5s', loadMs < 5000, `${loadMs}ms`);
  check('进入章节耗时（含网络）', interactiveMs < 60_000, `${interactiveMs}ms`);
  const idleStart = Date.now();
  const countAtIdle = requests.length;
  await perfPage.waitForTimeout(4000);
  const idleRequests = requests.filter((r) => r.at > idleStart).length;
  check(
    '静止阅读 4s 内无持续网络请求',
    idleRequests <= 1,
    `${idleRequests} 个请求（首屏共 ${countAtIdle}）`,
  );
  const heap = await perfPage.evaluate(() => {
    const mem = performance.memory;
    return mem ? Math.round(mem.usedJSHeapSize / 1048576) : null;
  });
  check('JS 堆内存可控', heap === null || heap < 120, heap === null ? '不可用' : `${heap} MB`);
  check('无控制台错误', perfErrors.length === 0, perfErrors.slice(0, 3).join(' | '));
  await perfPage.screenshot({ path: `${SHOT_DIR}reader-mobile-first.png` });
  await perfCtx.close();

  console.log('\n[12] TXT 导入与本地书籍');
  const txtCtx = await newContext(browser, VIEWPORTS[1]);
  const tp = await txtCtx.newPage();
  const txtPath = `${SHOT_DIR}test-book.txt`;
  writeFileSync(
    txtPath,
    ['第一章 起点', '第一段内容。', '第二段内容。', '', '第二章 继续', '这里的文字也不少。', '足够渲染成段落了。'].join('\n'),
  );
  await tp.goto(`${BASE}/#/`, { waitUntil: 'domcontentloaded' });
  await tp.waitForSelector('#continue-reading', { timeout: 30_000 });
  await tp.click('.quick-card:has-text("下载与缓存")');
  await tp.waitForSelector('[data-testid=data-sheet]', { timeout: 10_000 });
  await tp.setInputFiles('input[type=file][accept*=".txt"]', txtPath);
  await tp.waitForSelector('.shelf-card:has-text("test-book")', { timeout: 60_000 });
  check('TXT 导入后出现在书架', true);
  await tp.click('.shelf-card:has-text("test-book") .shelf-open');
  await tp.waitForFunction(
    () => /^#\/book\/[^/]+\/read\//.test(location.hash) && document.querySelectorAll('#chapter-content p').length > 0,
    null,
    { timeout: 60_000 },
  );
  const localTitle = await tp.locator('#chapter-title').textContent();
  check('本地书可阅读', Boolean(localTitle && localTitle.includes('起点')), localTitle ?? '');
  await txtCtx.close();

  }

  if (runPart(3)) {
  console.log('\n[14] 多书：搜索书源 → 详情 → 阅读 → 重载 → 书架切换（API 由 fixture 模拟，不依赖源站）');
  const mctx = await newContext(browser, VIEWPORTS[1]);
  const mp = await mctx.newPage();
  const mErrors = [];
  mp.on('pageerror', (err) => mErrors.push(String(err)));
  const searchCalls = [];
  const fakeBook = {
    id: '230580',
    title: '测试多书',
    author: '测试作者',
    category: '玄幻修真',
    status: '已完结',
    intro: '这是一本用于端到端测试的书。',
    latestChapter: '第2章',
  };
  const fakeEntries = [
    { id: '72836268', title: '第1章 开端', displayTitle: '第1章 开端', number: 1, extra: false },
    { id: '72836269', title: '第2章 继续', displayTitle: '第2章 继续', number: 2, extra: false },
  ];
  const fakeChapter = (id) => ({
    id,
    title: fakeEntries.find((e) => e.id === id)?.title ?? '章节',
    paragraphs: Array.from({ length: 30 }, (_, i) => `测试多书 ${id} 的第 ${i + 1} 段正文。`),
    prevId: id === '72836269' ? '72836268' : null,
    nextId: id === '72836268' ? '72836269' : null,
    pageCount: 1,
    charCount: 600,
    complete: true,
    missingPages: [],
  });
  await mp.route('**/api/search*', (route) => {
    searchCalls.push(route.request().url());
    return route.fulfill({ json: { query: '测试', results: [fakeBook] } });
  });
  await mp.route('**/api/toc?book=230580*', (route) =>
    route.fulfill({
      json: { from: 1, to: 2, totalPages: 1, pages: [{ page: 1, entries: fakeEntries }], failedPages: [], book: { ...fakeBook } },
    }),
  );
  await mp.route('**/api/chapter?book=230580*', (route) => {
    const id = new URL(route.request().url()).searchParams.get('id');
    return route.fulfill({ json: fakeChapter(id) });
  });
  await mp.route('**/img/**', (route) => route.fulfill({ status: 404, body: '' }));

  await mp.goto(`${BASE}/#/`, { waitUntil: 'domcontentloaded' });
  await mp.waitForSelector('#continue-reading', { timeout: 30_000 });
  check('书架首页显示预置书', (await mp.locator('.shelf-card[data-book-id="36780"]').count()) === 1);
  await mp.click('#open-discover');
  await mp.waitForSelector('#discover-input');
  await mp.fill('#discover-input', '测试');
  await mp.click('#discover-submit');
  await mp.waitForSelector('.discover-item[data-book-id="230580"]', { timeout: 15_000 });
  check('搜索结果出现', true);
  check('搜索词写入地址栏', (await mp.evaluate(() => location.hash)).startsWith('#/search?q='));
  await mp.screenshot({ path: `${SHOT_DIR}multi-search.png` });
  await mp.click('.discover-item[data-book-id="230580"]');
  await mp.waitForSelector('[data-testid=book-sheet] .book-intro:has-text("端到端")', { timeout: 15_000 });
  check('详情面板显示简介', true);
  await mp.click('#book-read');
  await mp.waitForFunction(
    () => location.hash === '#/book/230580/read/72836268' && document.querySelectorAll('#chapter-content p').length >= 30,
    null,
    { timeout: 30_000 },
  );
  check('开始阅读：地址含书号与章节号', true);
  check('顶栏显示新书书名', (await mp.locator('.topbar-title strong').textContent()) === '测试多书');
  await mp.reload({ waitUntil: 'domcontentloaded' });
  await mp.waitForFunction(() => document.querySelectorAll('#chapter-content p').length >= 30, null, { timeout: 30_000 });
  check('重载后仍在该书该章', (await mp.evaluate(() => location.hash)) === '#/book/230580/read/72836268');
  await mp.click('[data-testid=home-btn]', { force: true });
  await mp.waitForSelector('.shelf-card[data-book-id="230580"]');
  check('书架出现两本', (await mp.locator('.shelf-card').count()) === 2);
  check('当前书大卡片为新书', (await mp.locator('.book-card h2').textContent()) === '测试多书');
  const progress = await mp.evaluate(() => JSON.parse(localStorage.getItem('reader.personal.v1') ?? '{}').books?.['36780']?.progress ?? null);
  check('切书未污染预置书进度', progress === null || progress.chapterId !== '72836268');
  await mp.screenshot({ path: `${SHOT_DIR}multi-shelf.png`, fullPage: true });
  await mp.goto(`${BASE}/#/book/230580`, { waitUntil: 'domcontentloaded' });
  await mp.waitForSelector('.book-card');
  await mp.click('.shelf-card[data-book-id="230580"] .shelf-remove');
  await mp.click('.sheet .row-actions button:has-text("确定")');
  await mp.waitForFunction(() => !document.querySelector('.shelf-card[data-book-id="230580"]'), null, { timeout: 10_000 });
  check('移出书架后当前书回到预置书', (await mp.locator('.book-card h2').textContent()) === '万古神帝');
  check('多书流程无页面错误', mErrors.length === 0, mErrors.slice(0, 2).join(' | '));
  await mctx.close();
  }
  if (runPart(4)) {
  console.log('\n[15] 69书吧：切换书源 → 搜索 → 详情 → 阅读 → 书架；粘贴网址；人机验证提示（API 由 fixture 模拟）');
  const sctx = await newContext(browser, VIEWPORTS[1]);
  const sp = await sctx.newPage();
  const sErrors = [];
  sp.on('pageerror', (err) => sErrors.push(String(err)));
  const BOOK69 = '69shuba-51254';
  const book69 = { id: BOOK69, title: '测试书名', author: '测试作者', category: '玄幻魔法', status: '连载', intro: '少年持剑出山，一路向北。', latestChapter: '第2章 拜师' };
  const entries69 = [
    { id: '30001001', title: '第1章 出山', displayTitle: '第1章 出山', number: 1, extra: false },
    { id: '30001002', title: '第2章 拜师', displayTitle: '第2章 拜师', number: 2, extra: false },
    { id: '30001003', title: '第3章 封锁', displayTitle: '第3章 封锁', number: 3, extra: false },
  ];
  const chapter69 = (id) => ({
    id,
    title: entries69.find((e) => e.id === id)?.title ?? '章节',
    paragraphs: Array.from({ length: 30 }, (_, i) => `69书吧测试 ${id} 第 ${i + 1} 段。`),
    prevId: null,
    nextId: null,
    pageCount: 1,
    charCount: 400,
    complete: true,
    missingPages: [],
  });
  const searchUrls = [];
  await sp.route('**/api/search*', (route) => {
    searchUrls.push(route.request().url());
    return route.fulfill({ json: { query: '测试', source: '69shuba', results: [book69] } });
  });
  await sp.route(`**/api/toc?book=${BOOK69}*`, (route) =>
    route.fulfill({ json: { from: 1, to: 2, totalPages: 1, pages: [{ page: 1, entries: entries69 }], failedPages: [], book: { ...book69 } } }),
  );
  await sp.route(`**/api/chapter?book=${BOOK69}*`, (route) => {
    const id = new URL(route.request().url()).searchParams.get('id');
    if (id === '30001003') {
      return route.fulfill({
        status: 502,
        json: { error: { code: 'blocked', message: '书源开启了人机验证（Cloudflare），当前网络无法直接获取，请稍后再试或换个网络', retryable: false } },
      });
    }
    return route.fulfill({ json: chapter69(id) });
  });
  await sp.route('**/files/article/image/**', (route) => route.fulfill({ status: 404, body: '' }));
  await sp.route('**/img/**', (route) => route.fulfill({ status: 404, body: '' }));

  await sp.goto(`${BASE}/#/search`, { waitUntil: 'domcontentloaded' });
  await sp.waitForSelector('#discover-input');
  await sp.click('.discover-sources button[data-source="69shuba"]');
  check('书源切换为 69书吧', (await sp.getAttribute('.discover-sources button[data-source="69shuba"]', 'aria-pressed')) === 'true');
  await sp.fill('#discover-input', '测试');
  await sp.click('#discover-submit');
  await sp.waitForSelector(`.discover-item[data-book-id="${BOOK69}"]`, { timeout: 15_000 });
  check('搜索请求带 source=69shuba', searchUrls.some((u) => new URL(u).searchParams.get('source') === '69shuba'), searchUrls.join(' '));
  check('地址栏记录书源', (await sp.evaluate(() => location.hash)).includes('&source=69shuba'));
  await sp.reload({ waitUntil: 'domcontentloaded' });
  await sp.waitForSelector(`.discover-item[data-book-id="${BOOK69}"]`, { timeout: 15_000 });
  check('重载后仍在 69书吧 搜索', (await sp.getAttribute('.discover-sources button[data-source="69shuba"]', 'aria-pressed')) === 'true');
  await sp.screenshot({ path: `${SHOT_DIR}shuba-search.png` });
  await sp.click(`.discover-item[data-book-id="${BOOK69}"]`);
  await sp.waitForSelector('[data-testid=book-sheet] .book-intro:has-text("持剑出山")', { timeout: 15_000 });
  check('详情面板标注书源', ((await sp.locator('[data-testid=book-sheet] .author').textContent()) ?? '').includes('69书吧'));
  await sp.click('#book-read');
  await sp.waitForFunction(
    (id) => location.hash === `#/book/${id}/read/30001001` && document.querySelectorAll('#chapter-content p').length >= 30,
    BOOK69,
    { timeout: 30_000 },
  );
  check('69书吧的书可阅读', true);
  // 详情面板关闭动画期间遮罩仍在，会吞掉点击
  await sp.waitForSelector('[data-testid=book-sheet]', { state: 'detached' });
  await sp.click('[data-testid=home-btn]', { force: true });
  await sp.waitForSelector(`.shelf-card[data-book-id="${BOOK69}"]`);
  check('书架卡片标注 69书吧', ((await sp.locator(`.shelf-card[data-book-id="${BOOK69}"] .shelf-info`).textContent()) ?? '').includes('69书吧'));

  await sp.goto(`${BASE}/#/search`, { waitUntil: 'domcontentloaded' });
  await sp.waitForSelector('#discover-input');
  await sp.fill('#discover-input', 'https://www.69shuba.com/book/51254/');
  await sp.click('#discover-submit');
  await sp.waitForSelector('[data-testid=book-sheet] h2:has-text("测试书名")', { timeout: 15_000 });
  check('粘贴书籍网址直接打开详情', true);
  await sp.keyboard.press('Escape');
  await sp.waitForSelector('[data-testid=book-sheet]', { state: 'detached' });
  await sp.goto(`${BASE}/#/search`, { waitUntil: 'domcontentloaded' });
  await sp.waitForSelector('#discover-input');
  await sp.fill('#discover-input', 'https://www.69shuba.com/txt/51254/30001003');
  await sp.click('#discover-submit');
  await sp.waitForFunction(() => location.hash.endsWith('/read/30001003'), null, { timeout: 15_000 });
  await sp.waitForFunction(() => document.body.textContent?.includes('人机验证'), null, { timeout: 15_000 });
  check('粘贴章节网址直达该章；人机验证给出明确提示', true);
  await sp.screenshot({ path: `${SHOT_DIR}shuba-blocked.png` });
  check('69书吧流程无页面错误', sErrors.length === 0, sErrors.slice(0, 2).join(' | '));
  await sctx.close();
  }
  if (runPart(5)) {
  console.log('\n[16] 爱下电子书：搜索 → 下载整本 → 离线阅读 → 书架（API 与压缩包由 fixture 模拟）');
  const xctx = await newContext(browser, VIEWPORTS[1]);
  const xp = await xctx.newPage();
  const xErrors = [];
  xp.on('pageerror', (err) => xErrors.push(String(err)));
  const BOOKX = 'ixdzs-593824';
  const bookX = { id: BOOKX, title: '测试整本书', author: '测试作者', category: '已完结 · 1.2万字', intro: '用于端到端测试的整本下载书。' };
  const titlesX = ['第1章 开端', '第2章 转折', '第3章 结局'];
  const entriesX = titlesX.map((t, i) => ({ id: String(i + 1), title: t, displayTitle: t, number: i + 1, extra: false }));
  const txt = [
    '『测试整本书/作者:测试作者』',
    '『状态:已完结』',
    '爱下电子书Txt版阅读,下载和分享更多电子书请访问，简体:https://ixdzs8.com',
    '------章节内容开始-------',
    ...titlesX.flatMap((t, i) => [t.replace(' ', ''), ...Array.from({ length: 30 }, (_, j) => `\u3000\u3000整本书第 ${i + 1} 章第 ${j + 1} 段。`)]),
    '=已完结=',
  ].join('\r\n');
  const zip = (() => {
    const name = Buffer.from('测试整本书.txt');
    const data = Buffer.from(txt, 'utf8');
    const body = deflateRawSync(data);
    const local = Buffer.alloc(30 + name.length);
    local.writeUInt32LE(0x04034b50, 0); local.writeUInt16LE(0x800, 6); local.writeUInt16LE(8, 8);
    local.writeUInt32LE(body.length, 18); local.writeUInt32LE(data.length, 22); local.writeUInt16LE(name.length, 26); name.copy(local, 30);
    const central = Buffer.alloc(46 + name.length);
    central.writeUInt32LE(0x02014b50, 0); central.writeUInt16LE(0x800, 8); central.writeUInt16LE(8, 10);
    central.writeUInt32LE(body.length, 20); central.writeUInt32LE(data.length, 24); central.writeUInt16LE(name.length, 28); name.copy(central, 46);
    const eocd = Buffer.alloc(22);
    eocd.writeUInt32LE(0x06054b50, 0); eocd.writeUInt16LE(1, 8); eocd.writeUInt16LE(1, 10);
    eocd.writeUInt32LE(central.length, 12); eocd.writeUInt32LE(local.length + body.length, 16);
    return Buffer.concat([local, body, central, eocd]);
  })();
  const chapterCalls = [];
  await xp.route('**/api/search*', (route) => route.fulfill({ json: { query: '测试', source: 'ixdzs', results: [bookX] } }));
  await xp.route(`**/api/toc?book=${BOOKX}*`, (route) =>
    route.fulfill({ json: { from: 1, to: 2, totalPages: 1, pages: [{ page: 1, entries: entriesX }], failedPages: [], book: { ...bookX, status: '已完结', size: '1.2万字' } } }),
  );
  await xp.route(`**/api/chapter?book=${BOOKX}*`, (route) => {
    chapterCalls.push(route.request().url());
    return route.fulfill({ status: 400, json: { error: { code: 'invalid', message: '需要整本下载', retryable: false } } });
  });
  await xp.route('https://down7.ixdzs8.com/593824.zip', (route) =>
    route.fulfill({ status: 200, body: zip, headers: { 'content-type': 'application/zip', 'access-control-allow-origin': '*' } }),
  );

  await xp.goto(`${BASE}/#/search`, { waitUntil: 'domcontentloaded' });
  await xp.waitForSelector('#discover-input');
  await xp.click('.discover-sources button[data-source="ixdzs"]');
  await xp.fill('#discover-input', '测试');
  await xp.click('#discover-submit');
  await xp.waitForSelector(`.discover-item[data-book-id="${BOOKX}"]`, { timeout: 15_000 });
  check('爱下电子书搜索结果出现', true);
  await xp.click(`.discover-item[data-book-id="${BOOKX}"]`);
  await xp.waitForSelector('[data-testid=book-sheet] .download-status:has-text("整本下载")', { timeout: 15_000 });
  check('详情面板提示需整本下载、没有“加入书架”', (await xp.locator('#book-add').count()) === 0);
  await xp.click('#book-download');
  await xp.waitForSelector('[data-testid=book-sheet] .download-status:has-text("已下载 3 章")', { timeout: 30_000 });
  check('下载整本：解压并按目录切出 3 章', true);
  await xp.screenshot({ path: `${SHOT_DIR}ixdzs-downloaded.png` });
  await xp.click('#book-read');
  await xp.waitForFunction(
    (id) => location.hash === `#/book/${id}/read/1` && document.querySelectorAll('#chapter-content p').length >= 30,
    BOOKX,
    { timeout: 30_000 },
  );
  check('打开已下载的书阅读第 1 章', (await xp.locator('#chapter-title').textContent()) === '第1章 开端');
  check('不显示“本地导入内容”提示', !((await xp.textContent('body')) ?? '').includes('本地导入内容'));
  await xp.waitForSelector('[data-testid=book-sheet]', { state: 'detached' });
  await xp.click('[data-testid=home-btn]', { force: true });
  await xp.waitForSelector(`.shelf-card[data-book-id="${BOOKX}"]`);
  check('书架卡片显示作者与书源', ((await xp.locator(`.shelf-card[data-book-id="${BOOKX}"] .shelf-info`).textContent()) ?? '').includes('测试作者 · 爱下电子书'));
  await xp.reload({ waitUntil: 'domcontentloaded' });
  await xp.goto(`${BASE}/#/book/${BOOKX}/read/3`, { waitUntil: 'domcontentloaded' });
  await xp.waitForFunction(() => document.querySelectorAll('#chapter-content p').length >= 30, null, { timeout: 30_000 });
  check('重载后从本机读取第 3 章，不请求章节接口', chapterCalls.length === 0, chapterCalls.join(' '));
  await xp.goto(`${BASE}/#/search`, { waitUntil: 'domcontentloaded' });
  await xp.waitForSelector('#discover-input');
  await xp.fill('#discover-input', 'https://ixdzs8.com/read/593824/p2.html');
  await xp.click('#discover-submit');
  await xp.waitForFunction(() => location.hash.endsWith('/read/2') && document.querySelectorAll('#chapter-content p').length >= 30, null, { timeout: 15_000 });
  check('粘贴已下载书的章节网址直达该章', true);
  check('爱下电子书流程无页面错误', xErrors.length === 0, xErrors.slice(0, 2).join(' | '));
  await xctx.close();
  }
  console.log('\n[13] 控制台错误汇总（主上下文）');
  check('主流程无控制台错误', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '));

  await browser.close();

  const summary = {
    base: BASE,
    at: new Date().toISOString(),
    failures,
    results,
  };
  writeFileSync(`${SHOT_DIR}e2e-results-${PART}.json`, JSON.stringify(summary, null, 2));
  console.log(`\n完成：${results.length - failures}/${results.length} 通过，截图在 artifacts/`);
  process.exit(failures > 0 ? 1 : 0);
}

main().catch(async (err) => {
  console.error('\nE2E 脚本异常：', err);
  process.exit(2);
});
