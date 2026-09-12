/**
 * Chromium 持久化回归（不是 Android 实机验证）：
 *
 * APK 的 WebView 页面 origin 固定为 https://reader.local/（由 AssetClient
 * 拦截请求并提供 APK 内资源）。本脚本用 Playwright 在 Chromium 中复刻同一
 * 语义：同一 http://reader.local origin 上提供 dist-android 构建产物，
 * 同磁盘 profile 正常关闭并重新启动浏览器，验证：
 *  localStorage（进度/书签/历史/设置/本地书列表）与 IndexedDB（章节/目录缓存、
 *  本地导入书）全部跨启动存在。
 *
 * 负对照：换成另一个端口（模拟旧版随机端口架构），证明数据按 origin 隔离消失。
 *
 * 设备端自动化：tools/android-persistence-adb.sh（需要 adb + 设备）。
 */
import { chromium } from 'playwright';
import { readFile, writeFile, mkdtemp } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, normalize, extname } from 'node:path';

const DIST = new URL('../dist-android/', import.meta.url).pathname;
let fail = 0;
const check = (n, ok, d = '') => { if (!ok) fail++; console.log(`${ok ? '  ✓' : '  ✗'} ${n}${d ? ' — ' + d : ''}`); };

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.txt': 'text/plain; charset=utf-8',
};

async function serveAsset(route) {
  const url = new URL(route.request().url());
  let path = decodeURIComponent(url.pathname);
  if (path.endsWith('/')) path += 'index.html';
  if (path === '/native/source') {
    // 模拟书源不可用：持久化验证全程离线（TXT 本地书不依赖网络）
    await route.fulfill({ status: 502, contentType: 'text/plain', body: 'offline' });
    return;
  }
  const file = normalize(join(DIST, path));
  if (!file.startsWith(normalize(DIST))) {
    await route.fulfill({ status: 403, contentType: 'text/plain', body: 'forbidden' });
    return;
  }
  try {
    const body = await readFile(file);
    await route.fulfill({
      status: 200,
      contentType: MIME[extname(file).toLowerCase()] ?? 'application/octet-stream',
      body,
    });
  } catch {
    // SPA 回退
    try {
      const body = await readFile(join(DIST, 'index.html'));
      await route.fulfill({ status: 200, contentType: 'text/html; charset=utf-8', body });
    } catch {
      await route.fulfill({ status: 404, contentType: 'text/plain', body: 'not found' });
    }
  }
}

async function makeContext(origin, userDataDir) {
  const ctx = await chromium.launchPersistentContext(userDataDir, {
    viewport: { width: 412, height: 915 },
    isMobile: true,
    hasTouch: true,
    args: ['--disable-dev-shm-usage'],
  });
  await ctx.route(`${origin}/**`, serveAsset);
  const page = ctx.pages()[0] ?? (await ctx.newPage());
  const errors = [];
  page.on('pageerror', (e) => errors.push(String(e).slice(0, 160)));
  return { ctx, page, errors };
}

async function waitApp(page, origin) {
  await page.goto(`${origin}/#/`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(
    () => document.getElementById('app')?.dataset.view === 'home',
    null,
    { timeout: 30_000 },
  );
  await page.waitForSelector('#home-root', { timeout: 30_000 });
  await page.waitForTimeout(600);
}

const TXT_PATH = new URL('../artifacts/persist-book.txt', import.meta.url).pathname;
await writeFile(TXT_PATH, ['第一章 起点',
  ...Array.from({ length: 40 }, (_, i) => `段落${i}：` + '长段落用于验证真实滚动与阅读锚点恢复。'.repeat(8)),
  '第二章 继续',
  ...Array.from({ length: 40 }, (_, i) => `后续${i}：` + '本地内容离线保存。'.repeat(16)),
].join('\n'));

const ORIGIN = 'http://reader.local';
const OTHER_ORIGIN = 'http://reader.local:8081';
// persistent profile 目录 = 模拟 WebView 磁盘存储：两次 launch 之间浏览器进程完全退出
const PROFILE = await mkdtemp(join(tmpdir(), 'reader-persist-'));

console.log('\n== 浏览器启动 1 ==');
{
  const { ctx, page, errors } = await makeContext(ORIGIN, PROFILE);
  await waitApp(page, ORIGIN);
  check('启动 1：应用就绪', true);

  // 哨兵：localStorage
  await page.evaluate(() => {
    localStorage.setItem('reader.sentinel', 'sentinel-ok');
  });
  // 真实流程：导入 TXT 本地书（全程离线）
  await page.click('.quick-card:has-text("下载与缓存")');
  await page.waitForSelector('[data-testid=data-sheet]', { timeout: 15_000 });
  await page.setInputFiles('input[type=file][accept*=".txt"]', TXT_PATH);
  await page.waitForSelector('.quick-card:has-text("persist-book")', { timeout: 30_000 });
  check('启动 1：TXT 本地书导入成功', true);

  // 打开本地书 → 滚动 → 加书签
  await page.click('.quick-card:has-text("persist-book") button:has-text("打开")');
  await page.waitForFunction(
    () => location.hash.startsWith('#/read/') && document.querySelectorAll('#chapter-content p').length > 3,
    null,
    { timeout: 30_000 },
  );
  await page.evaluate(() => window.scrollTo(0, 400));
  await page.waitForTimeout(1200); // 等待滚动停止取样 + progress 落盘
  await page.evaluate(() => window.__readerApp?.reader?.showBars?.());
  await page.click('[data-testid=bookmark-btn]');
  await page.waitForTimeout(300);
  // 改主题设置
  await page.evaluate(() => {
    const raw = JSON.parse(localStorage.getItem('reader.settings.v1') ?? '{}');
    raw.theme = 'black';
    localStorage.setItem('reader.settings.v1', JSON.stringify(raw));
  });
  // 哨兵：IndexedDB
  await page.evaluate(() => new Promise((res, rej) => {
    const r = indexedDB.open('reader-db', 1);
    r.onsuccess = () => {
      const tx = r.result.transaction('toc', 'readwrite');
      tx.objectStore('toc').put({ bookId: '__sentinel__', pages: {}, totalPages: 1, updatedAt: Date.now() });
      tx.oncomplete = res;
      tx.onerror = () => rej(tx.error);
    };
    r.onerror = () => rej(r.error);
  }));

  const state1 = await page.evaluate(() => {
    const personal = JSON.parse(localStorage.getItem('reader.personal.v1') ?? '{}');
    const books = Object.values(personal.books ?? {});
    return {
      bookmarkCount: books.reduce((n, b) => n + (b.bookmarks?.length ?? 0), 0),
      historyCount: books.reduce((n, b) => n + (b.history?.length ?? 0), 0),
      hasProgress: books.some((b) => b.progress?.chapterId),
      localBooks: JSON.parse(localStorage.getItem('reader.localbooks.v1') ?? '[]').length,
      sentinel: localStorage.getItem('reader.sentinel'),
      theme: (JSON.parse(localStorage.getItem('reader.settings.v1') ?? '{}')).theme,
    };
  });
  check('启动 1：书签已写入', state1.bookmarkCount === 1, `${state1.bookmarkCount}`);
  check('启动 1：历史已写入', state1.historyCount >= 1, `${state1.historyCount}`);
  check('启动 1：进度已写入', state1.hasProgress, JSON.stringify(state1));
  check('启动 1：本地书列表已写入', state1.localBooks === 1);
  check('启动 1：localStorage 哨兵已写入', state1.sentinel === 'sentinel-ok');
  check('启动 1：主题设置为 black', state1.theme === 'black');
  check('启动 1：无页面错误', errors.length === 0, errors.slice(0, 2).join('|'));
  await ctx.close(); // 正常关闭，不声称模拟 force-stop。
}

console.log('\n== 同 profile、同 origin 浏览器启动 2 ==');
{
  const { ctx, page, errors } = await makeContext(ORIGIN, PROFILE);
  await waitApp(page, ORIGIN);
  const state2 = await page.evaluate(async () => {
    const personal = JSON.parse(localStorage.getItem('reader.personal.v1') ?? '{}');
    const books = Object.values(personal.books ?? {});
    const ids = await new Promise((res) => {
      const r = indexedDB.open('reader-db', 1);
      r.onsuccess = () => {
        const tx = r.result.transaction(['chapters', 'toc'], 'readonly');
        const chapters = tx.objectStore('chapters').getAll();
        const toc = tx.objectStore('toc').getAll();
        tx.oncomplete = () => res({ chapters: chapters.result?.length ?? 0, toc: toc.result?.length ?? 0 });
        tx.onerror = () => res({ chapters: 0, toc: 0 });
      };
      r.onerror = () => res({ chapters: 0, toc: 0 });
    });
    return {
      bookmarkCount: books.reduce((n, b) => n + (b.bookmarks?.length ?? 0), 0),
      historyCount: books.reduce((n, b) => n + (b.history?.length ?? 0), 0),
      hasProgress: books.some((b) => b.progress?.chapterId),
      localBooks: JSON.parse(localStorage.getItem('reader.localbooks.v1') ?? '[]').length,
      sentinel: localStorage.getItem('reader.sentinel'),
      theme: (JSON.parse(localStorage.getItem('reader.settings.v1') ?? '{}')).theme,
      ...ids,
    };
  });
  check('启动 2：书签跨启动存在', state2.bookmarkCount === 1, `${state2.bookmarkCount}`);
  check('启动 2：最近阅读跨启动存在', state2.historyCount >= 1, `${state2.historyCount}`);
  check('启动 2：阅读进度跨启动存在', state2.hasProgress);
  check('启动 2：本地导入书跨启动存在', state2.localBooks === 1);
  check('启动 2：IndexedDB 章节缓存存在', state2.chapters >= 2, `${state2.chapters} 条`);
  check('启动 2：IndexedDB 目录缓存存在（含哨兵）', state2.toc >= 2, `${state2.toc} 条`);
  check('启动 2：localStorage 哨兵存在', state2.sentinel === 'sentinel-ok');
  check('启动 2：主题设置存在', state2.theme === 'black');

  // 主页显示“继续阅读”且能恢复到正确章节（TXT 本地书）
  const continueBtn = await page.locator('.quick-card:has-text("persist-book") button:has-text("打开")').count();
  check('启动 2：本地书可重新打开', continueBtn === 1);
  await page.click('.quick-card:has-text("persist-book") button:has-text("打开")');
  await page.waitForFunction(
    () => location.hash.startsWith('#/read/') && document.querySelectorAll('#chapter-content p').length > 3,
    null,
    { timeout: 30_000 },
  );
  await page.waitForTimeout(1200);
  const restored = await page.evaluate(() => ({
    scrollY: window.scrollY,
    pos: window.__readerApp?.reader?.lastKnownPosition ?? null,
    title: document.querySelector('#chapter-title')?.textContent ?? '',
  }));
  check('启动 2：恢复到本地书章节', restored.title.includes('起点'), restored.title);
  check('启动 2：阅读位置恢复（非章首）', restored.pos !== null && restored.pos.paragraph > 0 && restored.scrollY > 100, JSON.stringify(restored));
  check('启动 2：无页面错误', errors.length === 0, errors.slice(0, 2).join('|'));
  await ctx.close();
}

console.log('\n== 负对照：不同端口 origin（旧随机端口架构的行为）==');
{
  const { ctx, page } = await makeContext(OTHER_ORIGIN, PROFILE);
  await waitApp(page, OTHER_ORIGIN);
  const state3 = await page.evaluate(() => ({
    sentinel: localStorage.getItem('reader.sentinel'),
    personal: localStorage.getItem('reader.personal.v1'),
    localBooks: localStorage.getItem('reader.localbooks.v1'),
  }));
  check('负对照：换端口后数据隔离消失（证明 origin 语义）', state3.sentinel === null && state3.personal === null && state3.localBooks === null, JSON.stringify(state3));
  await ctx.close();
}

console.log(`\nChromium 同源持久化回归（非 Android 实机）：${fail === 0 ? '全部通过' : `${fail} 项失败`}`);
process.exit(fail ? 1 : 0);
