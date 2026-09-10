import { chromium } from 'playwright';
const browser = await chromium.launch();
const page = await (await browser.newContext({ viewport: { width: 412, height: 915 }, isMobile: true, hasTouch: true })).newPage();
await page.goto('http://localhost:8787/#/read/38621433', { waitUntil: 'domcontentloaded' });
await page.waitForSelector('#chapter-content p', { timeout: 180000 });
await page.waitForTimeout(2000);
const r = await page.evaluate(() => {
  const ps = [...document.querySelectorAll('#chapter-content p')];
  return {
    title: document.querySelector('#chapter-title')?.textContent,
    paras: ps.length,
    first: ps[0]?.textContent?.slice(0, 30),
    last: ps[ps.length - 1]?.textContent?.slice(0, 50),
    meta: document.querySelector('.chapter-meta')?.textContent,
    banner: document.querySelector('.banner')?.hidden === false ? document.querySelector('.banner')?.textContent : null,
  };
});
console.log(JSON.stringify(r, null, 1));
await browser.close();
