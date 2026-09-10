/**
 * 端到端验证脚本（Playwright + Chromium）。
 * 前置：npm run build && node --experimental-strip-types src/server/dev.ts
 * 运行：node test/e2e.mjs [baseUrl]
 */
import { chromium, devices } from 'playwright';
import { mkdirSync, writeFileSync } from 'node:fs';

const BASE = process.argv[2] ?? 'http://localhost:8787';
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
    () => location.hash.startsWith('#/read/') && document.querySelectorAll('#chapter-content p').length > 5,
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
  await page.waitForTimeout(300);
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
  await tp.waitForSelector('.quick-card:has-text("test-book")', { timeout: 60_000 });
  check('TXT 导入后出现在书架', true);
  await tp.click('.quick-card:has-text("test-book") button:has-text("打开")');
  await tp.waitForFunction(
    () => location.hash.startsWith('#/read/') && document.querySelectorAll('#chapter-content p').length > 0,
    null,
    { timeout: 60_000 },
  );
  const localTitle = await tp.locator('#chapter-title').textContent();
  check('本地书可阅读', Boolean(localTitle && localTitle.includes('起点')), localTitle ?? '');
  await txtCtx.close();

  console.log('\n[13] 控制台错误汇总（主上下文）');
  check('主流程无控制台错误', consoleErrors.length === 0, consoleErrors.slice(0, 3).join(' | '));

  await browser.close();

  const summary = {
    base: BASE,
    at: new Date().toISOString(),
    failures,
    results,
  };
  writeFileSync(`${SHOT_DIR}e2e-results.json`, JSON.stringify(summary, null, 2));
  console.log(`\n完成：${results.length - failures}/${results.length} 通过，截图在 artifacts/`);
  process.exit(failures > 0 ? 1 : 0);
}

main().catch(async (err) => {
  console.error('\nE2E 脚本异常：', err);
  process.exit(2);
});
