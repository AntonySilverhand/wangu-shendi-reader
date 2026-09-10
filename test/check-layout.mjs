import { chromium } from 'playwright';
const b = await chromium.launch(); let fail = 0;
const check = (n, ok, d) => { if (!ok) fail++; console.log(`${ok ? '  ✓' : '  ✗'} ${n}${d ? ' — ' + d : ''}`); };
for (const [w, h, expect] of [[1920, 1080, 'wide'], [1440, 900, 'normal'], [412, 915, 'mobile']]) {
  const p = await (await b.newContext({ viewport: { width: w, height: h }, isMobile: w < 700, hasTouch: w < 700 })).newPage();
  await p.goto('http://localhost:8787/#/read/38621433', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('#chapter-content p', { timeout: 120000 });
  await p.waitForTimeout(800);
  const r = await p.evaluate(() => {
    const vis = (s) => { const e = document.querySelector(s); return e ? getComputedStyle(e).display !== 'none' : false; };
    return { rail: vis('.side-rail'), toc: document.querySelector('#toc-panel')?.classList.contains('open'), overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth, content: Math.round(document.querySelector('#chapter-content').getBoundingClientRect().width) };
  });
  check(`${w}px 无横向溢出`, r.overflow <= 1, `overflow=${r.overflow} content=${r.content}`);
  if (expect === 'wide') { check('≥1500px 右侧进度栏显示', r.rail, String(r.rail)); check('≥1100px 目录侧栏默认展开', r.toc, String(r.toc)); }
  if (expect === 'normal') check('1100-1500px 无右侧栏', !r.rail);
  if (expect === 'mobile') { check('手机无右侧栏', !r.rail); check('手机目录默认收起', !r.toc); }
}
await b.close(); console.log(`布局验证：${fail === 0 ? '全部通过' : fail + ' 项失败'}`); process.exit(fail ? 1 : 0);
