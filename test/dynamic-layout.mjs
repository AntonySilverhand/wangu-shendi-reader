/**
 * 动态 viewport 回归：同一个 Page 实例、同一份 DOM，连续 resize。
 * 验证：layout mode 语义切换、目录 drawer↔侧栏转换、章节不重载、
 * 阅读位置保持、搜索 query 保持、书签状态保持、焦点不被抢、
 * 无横向溢出、无重复元素、无 console error。
 */
import { chromium } from 'playwright';

export async function runDynamicLayoutTests(base) {
  const browser = await chromium.launch();
  let fail = 0;
  const check = (n, ok, d = '') => { if (!ok) fail++; console.log(`${ok ? '  ✓' : '  ✗'} ${n}${d ? ' — ' + d : ''}`); };

  const ctx = await browser.newContext({ viewport: { width: 412, height: 915 }, isMobile: true, hasTouch: true });
  const page = await ctx.newPage();
  const errors = [];
  const chapterReqs = [];
  page.on('pageerror', (e) => errors.push(String(e).slice(0, 200)));
  page.on('console', (m) => m.type() === 'error' && errors.push(m.text().slice(0, 200)));
  page.on('request', (req) => {
    if (req.url().includes('/api/chapter?id=38621433')) chapterReqs.push(Date.now());
  });

  console.log('\n[1] 412×915 启动：compact + 读取章节 + 滚动到中部');
  await page.goto(`${base}/#/read/38621433`, { waitUntil: 'domcontentloaded' });
  await page.waitForSelector('#chapter-content p', { timeout: 120_000 });
  await page.waitForTimeout(800);
  const baselineReqs = chapterReqs.length; // 首次加载不算
  const initial = await page.evaluate(() => ({
    mode: document.documentElement.getAttribute('data-layout'),
    url: location.href,
  }));
  check('初始 layout=compact', initial.mode === 'compact', initial.mode);
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight * 0.45));
  await page.waitForTimeout(1200);
  const pos0 = await page.evaluate(() => window.__readerApp?.reader?.lastKnownPosition ?? null);
  check('滚动后已捕获位置', pos0 && pos0.paragraph > 3, `p${pos0?.paragraph}`);

  console.log('\n[2] 加书签 + 打开目录输入搜索（状态基线）');
  await page.evaluate(() => window.__readerApp?.reader?.showBars?.());
  await page.click('[data-testid=bookmark-btn]');
  await page.waitForTimeout(400);
  const bookmark0 = await page.evaluate(() => {
    const d = JSON.parse(localStorage.getItem('reader.personal.v1') ?? '{}');
    return Object.values(d.books ?? {}).reduce((n, b) => n + (b.bookmarks?.length ?? 0), 0);
  });
  check('书签已添加', bookmark0 >= 1, `${bookmark0}`);
  await page.click('[data-testid=toc-btn]');
  await page.waitForSelector('#toc-panel.open');
  await page.fill('#toc-panel input[type=search]', '2824');
  await page.waitForFunction(() => document.querySelectorAll('#toc-panel .toc-row').length >= 1, null, { timeout: 60_000 });
  const searchState0 = await page.evaluate(() => ({
    rows: document.querySelectorAll('#toc-panel .toc-row').length,
    query: document.querySelector('#toc-panel input[type=search]')?.value,
  }));
  check('搜索有结果且 query 已输入', searchState0.rows >= 1 && searchState0.query === '2824', JSON.stringify(searchState0));
  await page.click('#toc-panel .panel-head .icon-btn'); // 收起目录

  console.log('\n[3] 412 → 900×900（折叠展开）：medium、侧栏化、不重载、不丢位置');
  await page.evaluate(() => window.__readerApp?.reader?.showBars?.());
  await page.setViewportSize({ width: 900, height: 900 });
  await page.waitForTimeout(700);
  const mid = await page.evaluate(() => ({
    mode: document.documentElement.getAttribute('data-layout'),
    url: location.href,
    pos: window.__readerApp?.reader?.lastKnownPosition ?? null,
    overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
  }));
  check('900px → layout=medium', mid.mode === 'medium', mid.mode);
  check('URL 不变', mid.url === initial.url, mid.url);
  check('位置保持（±2 段）', mid.pos && pos0 && Math.abs(mid.pos.paragraph - pos0.paragraph) <= 2, `${pos0?.paragraph} → ${mid.pos?.paragraph}`);
  check('无横向溢出', mid.overflow <= 1, `overflow=${mid.overflow}`);
  // 打开目录：应变为侧栏（非抽屉覆盖）
  await page.click('[data-testid=toc-btn]');
  await page.waitForSelector('#toc-panel.open');
  await page.waitForFunction(() => document.querySelectorAll('#toc-panel .toc-row').length >= 1, null, { timeout: 60_000 });
  await page.evaluate(() => document.querySelector('#toc-panel input[type=search]')?.focus());
  await page.waitForTimeout(400);
  const panelInfo = await page.evaluate(() => {
    const panel = document.getElementById('toc-panel');
    const rect = panel.getBoundingClientRect();
    return {
      left: rect.left,
      width: Math.round(rect.width),
      // 侧栏形态：不覆盖全屏（宽度 < 90vw 且 transform none）
      transform: getComputedStyle(panel).transform,
      readerMargin: getComputedStyle(document.querySelector('.reader-root')).marginLeft,
      query: document.querySelector('#toc-panel input[type=search]')?.value,
      rows: document.querySelectorAll('#toc-panel .toc-row').length,
      focusIsInput: document.activeElement === document.querySelector('#toc-panel input[type=search]'),
    };
  });
  check('900px 目录为侧栏（left=0 且不覆盖全屏）', panelInfo.left === 0 && panelInfo.width < 450, JSON.stringify({ w: panelInfo.width, l: panelInfo.left }));
  check('正文为目录让出空间', panelInfo.readerMargin !== '0px', panelInfo.readerMargin);
  check('搜索 query 与结果在 resize 后保持', panelInfo.query === '2824' && panelInfo.rows >= 1, `${panelInfo.query}/${panelInfo.rows}`);
  check('输入框焦点在 resize 后保持', panelInfo.focusIsInput, String(panelInfo.focusIsInput));

  console.log('\n[4] 900 → 1600×1000：wide、右侧进度栏出现');
  await page.setViewportSize({ width: 1600, height: 1000 });
  await page.waitForTimeout(700);
  const wide = await page.evaluate(() => ({
    mode: document.documentElement.getAttribute('data-layout'),
    rail: getComputedStyle(document.querySelector('.side-rail')).display,
    pos: window.__readerApp?.reader?.lastKnownPosition ?? null,
    overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
    url: location.href,
  }));
  check('1600px → layout=wide', wide.mode === 'wide', wide.mode);
  check('右侧进度栏显示', wide.rail !== 'none', wide.rail);
  check('位置保持（±3 段）', wide.pos && pos0 && Math.abs(wide.pos.paragraph - pos0.paragraph) <= 3, `${pos0?.paragraph} → ${wide.pos?.paragraph}`);
  check('无横向溢出', wide.overflow <= 1, `overflow=${wide.overflow}`);
  check('URL 不变', wide.url === initial.url);

  console.log('\n[5] 1600 → 412×915：回 compact、目录收起、状态仍在');
  await page.setViewportSize({ width: 412, height: 915 });
  await page.waitForTimeout(700);
  const back = await page.evaluate(() => ({
    mode: document.documentElement.getAttribute('data-layout'),
    tocOpen: document.getElementById('toc-panel')?.classList.contains('open'),
    rail: getComputedStyle(document.querySelector('.side-rail')).display,
    pos: window.__readerApp?.reader?.lastKnownPosition ?? null,
    overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
    url: location.href,
  }));
  check('412px → layout=compact', back.mode === 'compact', back.mode);
  check('回 compact 后目录自动收起（不覆盖整屏）', back.tocOpen === false, String(back.tocOpen));
  check('右侧进度栏隐藏', back.rail === 'none', back.rail);
  check('位置保持（±3 段）', back.pos && pos0 && Math.abs(back.pos.paragraph - pos0.paragraph) <= 3, `${pos0?.paragraph} → ${back.pos?.paragraph}`);
  check('无横向溢出', back.overflow <= 1, `overflow=${back.overflow}`);
  check('URL 不变', back.url === initial.url);

  console.log('\n[6] 折叠/展开连续切换（900 ⇄ 390 ×2）+ 390 横屏 840×390');
  for (const [w, h] of [[900, 840], [390, 840], [900, 840], [390, 840], [840, 390], [390, 844]]) {
    await page.setViewportSize({ width: w, height: h });
    await page.waitForTimeout(500);
    const st = await page.evaluate(() => ({
      mode: document.documentElement.getAttribute('data-layout'),
      overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      pos: window.__readerApp?.reader?.lastKnownPosition ?? null,
      paras: document.querySelectorAll('#chapter-content p').length,
    }));
    const expect = w >= 1600 ? 'wide' : w >= 840 ? 'medium' : 'compact';
    check(`${w}×${h} → ${expect} 无溢出 内容未清空`, st.mode === expect && st.overflow <= 1 && st.paras > 50, JSON.stringify({ mode: st.mode, o: st.overflow, paras: st.paras }));
  }

  console.log('\n[7] 章节未重复加载 + 书签保持 + 无重复元素 + 无错误');
  check('整个过程中当前章节未重新请求', chapterReqs.length === baselineReqs, `${chapterReqs.length - baselineReqs} 次`);
  const final = await page.evaluate(() => {
    const d = JSON.parse(localStorage.getItem('reader.personal.v1') ?? '{}');
    return {
      bookmark: Object.values(d.books ?? {}).reduce((n, b) => n + (b.bookmarks?.length ?? 0), 0),
      panels: document.querySelectorAll('#toc-panel').length,
      rails: document.querySelectorAll('.side-rail').length,
      readers: document.querySelectorAll('#reader-root').length,
    };
  });
  check('书签跨全部 resize 保持', final.bookmark >= 1, `${final.bookmark}`);
  check('无重复元素（toc/rail/reader 各 1）', final.panels === 1 && final.rails === 1 && final.readers === 1, JSON.stringify(final));
  check('全程无 console/page 错误', errors.length === 0, errors.slice(0, 3).join(' | '));

  await browser.close();
  return fail;
}

const isMain = process.argv[1] && import.meta.url.endsWith(process.argv[1].split('/').pop() ?? '');
if (isMain) {
  const BASE = process.argv[2] ?? 'http://localhost:8787';
  const fail = await runDynamicLayoutTests(BASE);
  console.log(`\n动态布局回归：${fail === 0 ? '全部通过' : `${fail} 项失败`}`);
  process.exit(fail ? 1 : 0);
}
