/**
 * 聚焦回归验证：覆盖最近修复与新增功能，控制在 2 分钟内。
 * 运行：node test/verify-final.mjs [baseUrl]
 */
import { chromium } from 'playwright';
import { mkdirSync, writeFileSync } from 'node:fs';

const BASE = process.argv[2] ?? 'http://localhost:8787';
const SHOT = new URL('../artifacts/', import.meta.url).pathname;
mkdirSync(SHOT, { recursive: true });

let failures = 0;
const check = (name, ok, detail = '') => {
  if (!ok) failures++;
  console.log(`${ok ? '  ✓' : '  ✗'} ${name}${detail ? ` — ${detail}` : ''}`);
};

const browser = await chromium.launch();

async function openReader(ctx) {
  const page = await ctx.newPage();
  page.on('pageerror', (e) => console.log('  [pageerror]', String(e).slice(0, 200)));
  await page.goto(`${BASE}/#/`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#continue-reading', { timeout: 30_000 });
  await page.click('#continue-reading');
  await page.waitForSelector('#chapter-content p', { timeout: 120_000 });
  return page;
}

try {
  console.log('\n[1] 正文上方无空条 / 隐藏属性生效');
  {
    const ctx = await browser.newContext({ viewport: { width: 412, height: 915 }, isMobile: true, hasTouch: true });
    const page = await openReader(ctx);
    const banner = await page.evaluate(() => {
      const b = document.querySelector('.banner');
      return { hidden: b?.hidden ?? null, display: b ? getComputedStyle(b).display : null };
    });
    check('未使用时 banner 不占位', banner.display === 'none', JSON.stringify(banner));
    await ctx.close();
  }

  console.log('\n[2] 桌面/手机行宽');
  for (const vp of [
    { w: 320, h: 640, want: 280 },
    { w: 412, h: 915, want: 372 },
    { w: 1440, h: 900, want: 648 },
  ]) {
    const ctx = await browser.newContext({ viewport: { width: vp.w, height: vp.h }, isMobile: vp.w < 700, hasTouch: vp.w < 700 });
    const page = await openReader(ctx);
    const info = await page.evaluate(() => {
      const c = document.querySelector('#chapter-content').getBoundingClientRect();
      return {
        content: Math.round(c.width),
        overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      };
    });
    check(
      `${vp.w}px 行宽 ${info.content}px / 无溢出`,
      info.content === vp.want && info.overflow <= 1,
      JSON.stringify(info),
    );
    await ctx.close();
  }

  console.log('\n[3] 进度恢复与快捷键（含刷新）');
  {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    const page = await openReader(ctx);
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight * 0.35));
    await page.waitForTimeout(700);
    const before = await page.evaluate(() => window.__readerApp?.reader.lastKnownPosition ?? null);
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#chapter-content p', { timeout: 120_000 });
    await page.waitForTimeout(700);
    const after = await page.evaluate(() => window.__readerApp?.reader.lastKnownPosition ?? null);
    check('刷新后回到同一段', before && after && Math.abs(before.paragraph - after.paragraph) <= 2, `p${before?.paragraph} → p${after?.paragraph}`);
    await page.evaluate(() => window.__readerApp?.reader.showBars());
    const titleBefore = await page.locator('#chapter-title').textContent();
    await page.keyboard.press('ArrowRight');
    await page.waitForFunction(
      (prev) => document.querySelector('#chapter-title')?.textContent !== prev && document.querySelectorAll('#chapter-content p').length > 3,
      titleBefore,
      { timeout: 120_000 },
    );
    check('→ 键翻到下一章', true);
    await ctx.close();
  }

  console.log('\n[4] TXT 导入 → 本地书阅读 → 移除');
  {
    const ctx = await browser.newContext({ viewport: { width: 412, height: 915 }, isMobile: true, hasTouch: true });
    const page = await ctx.newPage();
    const txtPath = `${SHOT}regression-book.txt`;
    writeFileSync(
      txtPath,
      ['第一章 起点', '第一段内容。', '第二段内容。', '', '第二章 继续', '更多内容在这里。', '第三段。'].join('\n'),
    );
    await page.goto(`${BASE}/#/`, { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('#continue-reading', { timeout: 30_000 });
    await page.click('.quick-card:has-text("下载与缓存")');
    await page.waitForSelector('[data-testid=data-sheet]', { timeout: 10_000 });
    const stats = await page.locator('[data-testid=data-sheet] .stat').count();
    check('数据面板显示缓存统计', stats >= 3, `${stats} 项`);
    await page.setInputFiles('input[type=file][accept*=".txt"]', txtPath);
    await page.waitForSelector('.quick-card:has-text("regression-book")', { timeout: 60_000 });
    check('导入后出现在书架', true);
    await page.click('.quick-card:has-text("regression-book") button:has-text("打开")');
    await page.waitForFunction(
      () => location.hash.startsWith('#/read/') && document.querySelectorAll('#chapter-content p').length > 0,
      null,
      { timeout: 60_000 },
    );
    const localTitle = await page.locator('#chapter-title').textContent();
    const meta = await page.locator('.chapter-meta').textContent();
    check('本地书可阅读', Boolean(localTitle?.includes('起点')), localTitle ?? '');
    check('标记为本地导入', Boolean(meta?.includes('本地导入')), meta?.trim() ?? '');
    await page.evaluate(() => window.__readerApp?.reader.showBars());
    await page.keyboard.press('ArrowRight');
    await page.waitForFunction(
      () => document.querySelector('#chapter-title')?.textContent?.includes('继续'),
      null,
      { timeout: 30_000 },
    );
    check('本地书可翻章', true);
    await ctx.close();
  }

  console.log(`\n聚焦验证完成：${failures === 0 ? '全部通过' : `${failures} 项失败`}`);
  writeFileSync(`${SHOT}verify-final.json`, JSON.stringify({ at: new Date().toISOString(), failures }, null, 2));
  process.exit(failures === 0 ? 0 : 1);
} catch (err) {
  console.error('\n验证脚本异常：', err);
  process.exit(2);
} finally {
  await browser.close();
}
