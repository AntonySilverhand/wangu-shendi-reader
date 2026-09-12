import { chromium } from 'playwright';
import assert from 'node:assert/strict';
const base = process.argv[2] ?? 'http://localhost:8787';
const browser = await chromium.launch();
try {
  const ctx = await browser.newContext({ viewport: { width: 412, height: 915 }, serviceWorkers: 'block' });
  await ctx.addInitScript(() => Object.defineProperty(CSS, 'highlights', { value: undefined }));
  const ids = ['38621433', '38621434', '38621435'];
  const entries = ids.map((id, i) => ({ id, title: `第${i + 1}章 测试`, displayTitle: `第${i + 1}章 测试`, number: i + 1, extra: false }));
  await ctx.route(/\/api\/toc\?/, (r) => r.fulfill({ json: { from: 1, to: 1, totalPages: 1, pages: [{ page: 1, entries }], failedPages: [] } }));
  let slowStarted = false;
  await ctx.route(/\/api\/chapter\?/, async (r) => {
    const id = new URL(r.request().url()).searchParams.get('id');
    if (id === ids[1]) { slowStarted = true; await new Promise((resolve) => setTimeout(resolve, 1500)); }
    const paragraphs = Array.from({ length: 80 }, (_, n) => `段落${n}。` + '测试搜索关键词和阅读位置。'.repeat(15));
    await r.fulfill({ json: { id, title: `章节${id}`, paragraphs, charCount: paragraphs.join('').length, complete: true, missingPages: [], prevId: null, nextId: null } }).catch(() => {});
  });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto(`${base}/#/read/${ids[0]}`);
  await page.waitForSelector('#chapter-content p');
  await page.evaluate(() => window.scrollTo(0, 1800));
  await page.waitForTimeout(500);
  const anchor = await page.evaluate(() => window.__readerApp.reader.lastKnownPosition);
  assert.ok(anchor.paragraph > 0);
  await page.evaluate((id) => { void window.__readerApp.openChapter(id); }, ids[1]);
  for (let n = 0; !slowStarted && n < 50; n++) await page.waitForTimeout(20);
  assert.ok(slowStarted);
  await page.evaluate((id) => { void window.__readerApp.openChapter(id); }, ids[2]);
  await page.waitForFunction((id) => window.__readerApp.reader.currentChapterId === id, ids[2]);
  await page.waitForTimeout(1800);
  const state = await page.evaluate(() => ({ current: window.__readerApp.reader.currentChapterId, loading: window.__readerApp.loadingChapterId, personal: JSON.parse(localStorage.getItem('reader.personal.v1')) }));
  assert.equal(state.current, ids[2]);
  assert.equal(state.loading, null);
  assert.equal(state.personal.books['36780'].progress.chapterId, ids[2]);
  assert.equal(state.personal.books['36780'].progress.paragraph, 0);
  console.log('✓ 慢章节取消后不覆盖新章节，loading 清理，新章起点即时保存');
  for (const paragraph of [12, 24, 5]) {
    await page.evaluate(({ id, paragraph }) => window.__readerApp.openChapter(id, { anchor: { paragraph, offset: 8 } }), { id: ids[2], paragraph });
    await page.waitForTimeout(400);
    const pos = await page.evaluate(() => window.__readerApp.reader.lastKnownPosition);
    assert.equal(pos.paragraph, paragraph);
  }
  console.log('✓ 同一章多个书签锚点跳转不被 sameRendered 短路');
  const before = await page.locator('#chapter-content').innerText();
  await page.evaluate(() => window.__readerApp.search.open());
  await page.fill('#chapter-search input', '关键词');
  await page.waitForTimeout(450);
  assert.equal(await page.locator('#chapter-content mark').count(), 1);
  assert.equal(await page.locator('#chapter-content').innerText(), before);
  await page.evaluate(() => window.__readerApp.search.close());
  assert.equal(await page.locator('#chapter-content mark').count(), 0);
  assert.equal(await page.locator('#chapter-content').innerText(), before);
  assert.deepEqual(errors, []);
  console.log('✓ 无 CSS Highlight 的降级搜索只创建一个 mark，关闭恢复原文');
} finally {
  await browser.close();
}
