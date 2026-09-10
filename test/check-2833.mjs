import { findChapterEntry, fetchChapter, fetchSourceHtml } from '../src/shared/source.ts';
const toc = await fetchChapter; // noop
const entry = await findChapterEntry('36780', '第2833章').catch(() => null);
console.log('TOC entry:', JSON.stringify(entry));
if (!entry) process.exit(0);
const rec = await fetchChapter('36780', entry.id, {});
console.log('API:', JSON.stringify({ complete: rec.complete, pages: rec.paragraphs.length ? undefined : 0, paras: rec.paragraphs.length, missing: rec.missingPages, title: rec.title }));
// 逐页抓原始页面
for (let n = 0; n < 6; n++) {
  const url = n === 0 ? `https://www.wanshuge.org/book/36780_${entry.id}.html` : `https://www.wanshuge.org/book/36780/${entry.id}_${n}.html`;
  try {
    const { html } = await fetchSourceHtml(url, { highPriority: false });
    const ps = [...html.matchAll(/qsbs\.bb\('([^']+)'\)/g)].map((m) => Buffer.from(m[1], 'base64').toString('utf8')).join('');
    const count = (ps.match(/<p>/g) ?? []).length;
    const nextMatch = html.match(/href="([^"]+)"[^>]*>下一章/);
    const chars = ps.replace(/<[^>]+>/g, '').length;
    console.log(`page ${n}: ${count} paras, ${chars} chars, next=${nextMatch?.[1] ?? '?'}, first=${ps.replace(/<[^>]+>/g,'').slice(0, 20)}`);
    if (count === 0) break;
  } catch (e) { console.log(`page ${n}: ERROR ${String(e).slice(0, 60)}`); break; }
}
