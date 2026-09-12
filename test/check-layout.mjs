/**
 * 宽屏/动态形态验证：
 *  - 三个典型尺寸的“首次启动”形态检查（1100/1500 断点语义）；
 *  - 同一 Page 实例 412→900→1600→412 的动态 resize 回归（详见 dynamic-layout.mjs）。
 */
import { chromium } from 'playwright';
import { runDynamicLayoutTests } from './dynamic-layout.mjs';

const BASE = process.argv[2] ?? 'http://localhost:8787';
let fail = 0;
const check = (n, ok, d) => { if (!ok) fail++; console.log(`${ok ? '  ✓' : '  ✗'} ${n}${d ? ' — ' + d : ''}`); };

const b = await chromium.launch();
for (const [w, h, expect] of [[1920, 1080, 'wide'], [1440, 900, 'medium'], [412, 915, 'compact']]) {
  const p = await (await b.newContext({ viewport: { width: w, height: h }, isMobile: w < 700, hasTouch: w < 700 })).newPage();
  await p.goto(`${BASE}/#/read/38621433`, { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('#chapter-content p', { timeout: 120000 });
  await p.waitForTimeout(800);
  const r = await p.evaluate(() => {
    const vis = (s) => { const e = document.querySelector(s); return e ? getComputedStyle(e).display !== 'none' : false; };
    return {
      mode: document.documentElement.getAttribute('data-layout'),
      rail: vis('.side-rail'),
      toc: document.querySelector('#toc-panel')?.classList.contains('open'),
      overflow: document.documentElement.scrollWidth - document.documentElement.clientWidth,
      content: Math.round(document.querySelector('#chapter-content').getBoundingClientRect().width),
    };
  });
  check(`${w}px 无横向溢出`, r.overflow <= 1, `overflow=${r.overflow} content=${r.content}`);
  check(`${w}px layout 语义=${expect}`, r.mode === expect, r.mode);
  if (expect === 'wide') { check('wide 右侧进度栏显示', r.rail, String(r.rail)); check('≥1100px 目录侧栏默认展开', r.toc, String(r.toc)); }
  if (expect === 'medium') check('medium 无右侧栏', !r.rail);
  if (expect === 'compact') { check('手机无右侧栏', !r.rail); check('手机目录默认收起', !r.toc); }
}
await b.close();
console.log(`\n首次启动形态验证：${fail === 0 ? '全部通过' : fail + ' 项失败'}`);

const dynFail = await runDynamicLayoutTests(BASE);
fail += dynFail;
console.log(`\n布局验证汇总：${fail === 0 ? '全部通过' : `${fail} 项失败`}`);
process.exit(fail ? 1 : 0);
