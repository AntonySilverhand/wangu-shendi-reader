import { chromium } from 'playwright';
const browser = await chromium.launch();
const page = await (await browser.newContext({ viewport: { width: 412, height: 915 } })).newPage();
await page.goto('http://localhost:8787/#/read/38621420', { waitUntil: 'domcontentloaded' });
await page.waitForSelector('#chapter-content p', { timeout: 150000 });
await page.waitForTimeout(1500);
const r = await page.evaluate(() => ({
  title: document.querySelector('#chapter-title')?.textContent,
  paras: document.querySelectorAll('#chapter-content p').length,
  banner: document.querySelector('.banner')?.hidden === false ? document.querySelector('.banner')?.textContent : null,
  last: [...document.querySelectorAll('#chapter-content p')].pop()?.textContent?.slice(0, 40),
}));
console.log(JSON.stringify(r));
await browser.close();
