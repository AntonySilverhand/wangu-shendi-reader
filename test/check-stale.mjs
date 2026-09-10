import { chromium } from 'playwright';
const browser = await chromium.launch();
const page = await (await browser.newContext({ viewport: { width: 412, height: 915 } })).newPage();
await page.goto('http://localhost:8787/#/', { waitUntil: 'domcontentloaded' });
await page.waitForSelector('#continue-reading', { timeout: 30000 });
// 注入一条“旧版半章”缓存（无 complete/v 字段，仅 1 段）
await page.evaluate(() => new Promise((resolve, reject) => {
  const req = indexedDB.open('reader-db', 1);
  req.onsuccess = () => {
    const db = req.result;
    const tx = db.transaction('chapters', 'readwrite');
    tx.objectStore('chapters').put({
      key: '36780:38621433', bookId: '36780', chapterId: '38621433', title: '旧缓存章节',
      paragraphs: ['这是旧的半章内容，只有一段。'], charCount: 15, source: 'remote',
      fetchedAt: Date.now(), bytes: 40,
    });
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  };
  req.onerror = () => reject(req.error);
}));
// 打开该章：应立即显示旧缓存（不等待网络），随后后台静默补全
const t0 = Date.now();
await page.goto('http://localhost:8787/#/read/38621433', { waitUntil: 'domcontentloaded' });
await page.waitForSelector('#chapter-content p', { timeout: 60000 });
const immediate = await page.evaluate(() => document.querySelectorAll('#chapter-content p').length);
const elapsed = Date.now() - t0;
let after = immediate;
for (let i = 0; i < 20 && after < 50; i++) {
  await page.waitForTimeout(1000);
  after = await page.evaluate(() => document.querySelectorAll('#chapter-content p').length);
}
console.log(JSON.stringify({ immediateParas: immediate, immediateMs: elapsed, afterBackfill: after }));
await browser.close();
