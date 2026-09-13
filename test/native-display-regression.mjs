/** 浏览器 + 原生 Bridge mock；覆盖真实 UI/布局，不伪称 Android 系统栏验证。 */
import { chromium } from 'playwright';
import assert from 'node:assert/strict';
const base = process.argv[2] ?? 'http://localhost:8787';
const browser = await chromium.launch();
try {
  const page = await browser.newPage({ viewport: { width: 412, height: 915 }, isMobile: true, hasTouch: true });
  await page.addInitScript(() => {
    window.displayCalls = [];
    window.testInsets = { version: 1, top: 24, bottom: 24, left: 0, right: 0, ime: 0 };
    window._nativeDisplayBridge = {
      getDisplaySnapshot: () => JSON.stringify(window.testInsets),
      onPageReady() {}, setTheme() {},
      setImmersive(value) { window.displayCalls.push(value); },
    };
  });
  await page.goto(`${base}/#/read/38621433`);
  await page.waitForSelector('#chapter-content p');
  await page.evaluate(() => window.__readerApp.settings.update({ immersiveReading: true }));
  const immersive = async (expected) => {
    await page.waitForFunction((v) => window.displayCalls.at(-1) === v, expected);
  };
  await immersive(true);
  await page.evaluate(() => window.__readerApp.openData());
  await immersive(false);
  const callsBefore = await page.evaluate(() => window.displayCalls.length);
  await page.getByRole('button', { name: '下载章节…', exact: true }).click();
  await page.waitForTimeout(350);
  await immersive(false);
  assert.equal(await page.evaluate(() => window.displayCalls.length), callsBefore, '替换弹窗不应短暂 hide/show');
  await page.locator('[data-testid="download-sheet"] [data-testid="sheet-close"]').click();
  await immersive(true);
  console.log('✓ 数据 → 下载弹窗替换及关闭：沉浸状态正确，无中途闪动');

  await page.evaluate(() => window.__readerApp.tocView.open());
  await immersive(false);
  await page.evaluate(() => window.__readerApp.tocView.close());
  await immersive(true);
  console.log('✓ compact 目录抽屉抑制沉浸');

  await page.evaluate(() => window.__readerApp.search.open());
  await immersive(false);
  await page.evaluate(() => window.__readerApp.search.close());
  await immersive(true);
  console.log('✓ 章内搜索关闭释放输入焦点与沉浸抑制');

  await page.setViewportSize({ width: 840, height: 390 });
  await page.evaluate(() => {
    window.__onNativeDisplayChange({ ...window.testInsets, top: 0, left: 48 });
    window.__readerApp.tocView.open();
  });
  await page.waitForTimeout(350);
  const input = page.locator('#toc-panel input');
  const bounds = await input.boundingBox();
  assert.ok(bounds.x >= 48, `目录输入框进入左侧 cutout: ${bounds.x}`);
  await input.focus();
  await immersive(false);
  await input.evaluate(e => e.blur());
  await immersive(true);
  console.log('✓ 横屏左侧 48px cutout 避让，侧栏输入时暂停沉浸');

  await page.evaluate(() => {
    window.__readerApp.tocView.close();
    window.scrollTo(0, 1800);
  });
  await page.waitForTimeout(400);
  const anchor = await page.evaluate(() => window.__readerApp.reader.lastKnownPosition);
  await page.evaluate(() => window.__onNativeDisplayChange({ ...window.testInsets, top: 32, left: 48 }));
  await page.waitForTimeout(400);
  const after = await page.evaluate(() => window.__readerApp.reader.lastKnownPosition);
  assert.equal(after.paragraph, anchor.paragraph);
  assert.ok(Math.abs(after.offset - anchor.offset) <= 1, 'inset 改变后字符锚点漂移');
  console.log('✓ 小幅 inset 改变保持段落/字符位置');

  // 编辑期间即使原生 IME 快照改变，也不能调用正文 restorePosition 抢焦点滚动。
  await page.evaluate(() => window.__readerApp.tocView.open());
  await input.focus();
  await page.evaluate(() => {
    const reader = window.__readerApp.reader;
    const restore = reader.restorePosition.bind(reader);
    window.restoreCalls = 0;
    reader.restorePosition = (...args) => { window.restoreCalls++; restore(...args); };
    window.__onNativeDisplayChange({ ...window.testInsets, top: 32, left: 48, bottom: 0, ime: 300 });
  });
  await page.waitForTimeout(100);
  assert.equal(await page.evaluate(() => window.restoreCalls), 0);
  console.log('✓ 编辑期间 inset 变化不重锚定正文');

  for (const size of [{ width: 412, height: 915 }, { width: 840, height: 390 }, { width: 1920, height: 1080 }]) {
    await input.evaluate(e => e.blur());
    await page.setViewportSize(size);
    await page.evaluate(() => {
      window.__onNativeDisplayChange({ version: 1, top: 32, bottom: 24, left: 48, right: 20, ime: 0 });
      window.__readerApp.openSettings();
    });
    await page.waitForTimeout(350);
    const geometry = await page.locator('[data-testid="settings-sheet"]').evaluate(e => {
      const r = e.getBoundingClientRect();
      const s = getComputedStyle(e);
      return { left: r.left + parseFloat(s.paddingLeft), right: r.right - parseFloat(s.paddingRight), top: r.top, width: innerWidth };
    });
    assert.ok(geometry.left >= 48 && geometry.right <= geometry.width - 20 && geometry.top >= 32, JSON.stringify(geometry));
    await page.locator('[data-testid="settings-sheet"] [data-testid="sheet-close"]').click();
    await page.waitForTimeout(300);
  }
  console.log('✓ compact/横屏/wide 设置弹窗四边安全区');
} finally {
  await browser.close();
}
