import { chromium } from 'playwright';
const b = await chromium.launch();
const p = await (await b.newContext({ viewport: { width: 412, height: 915 } })).newPage();
await p.goto('http://localhost:8787/#/', { waitUntil: 'domcontentloaded' });
await p.waitForSelector('#continue-reading', { timeout: 60000 });
await p.evaluate(() => new Promise((res, rej) => {
  const r = indexedDB.open('reader-db', 1);
  r.onsuccess = () => { const tx = r.result.transaction('chapters', 'readwrite');
    tx.objectStore('chapters').put({ key: '36780:38621481', bookId: '36780', chapterId: '38621481', title: '旧版缺段缓存', paragraphs: ['旧版只存了一段'], charCount: 8, source: 'remote', fetchedAt: Date.now(), bytes: 20, v: 2 });
    tx.oncomplete = res; tx.onerror = () => rej(tx.error); };
  r.onerror = () => rej(r.error);
}));
await p.goto('http://localhost:8787/#/read/38621481', { waitUntil: 'domcontentloaded' });
await p.waitForSelector('#chapter-content p', { timeout: 120000 });
let n = 0;
for (let i = 0; i < 25 && n !== 69; i++) { await p.waitForTimeout(1000); n = await p.evaluate(() => document.querySelectorAll('#chapter-content p').length); }
console.log('2842 段数:', n, n === 69 ? '✓' : '✗');
const v = await p.evaluate(() => new Promise((res) => {
  const r = indexedDB.open('reader-db', 1);
  r.onsuccess = () => { const q = r.result.transaction('chapters', 'readonly').objectStore('chapters').get('36780:38621481');
    q.onsuccess = () => res(q.result?.v ?? 'deleted'); };
}));
console.log('旧记录版本:', v, v === 3 ? '✓ 已重取' : '');
await p.goto('http://localhost:8787/#/read/38621457', { waitUntil: 'domcontentloaded' });
await p.waitForSelector('#chapter-content p', { timeout: 60000 });
const n2 = await p.evaluate(() => document.querySelectorAll('#chapter-content p').length);
console.log('2833 段数:', n2, n2 === 99 ? '✓' : '✗');
await b.close();
