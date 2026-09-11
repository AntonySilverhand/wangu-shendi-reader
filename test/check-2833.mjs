// 第 2833 章（38621457）上游分页诊断：API 结果 + 逐页原始抓取对照。
// 用法：node test/check-2833.mjs
import { fetchChapter, fetchSourceHtml } from '../src/shared/source.ts';

const ID = '38621457';
const rec = await fetchChapter(ID, { highPriority: false }).catch((e) => {
  console.log('API ERROR:', String(e).slice(0, 120));
  return null;
});
if (rec) {
  console.log('API:', JSON.stringify({
    complete: rec.complete,
    paras: rec.paragraphs.length,
    missing: rec.missingPages,
    title: rec.title,
  }));
}
// 逐页抓原始页面
for (let n = 0; n < 6; n++) {
  const url = n === 0 ? `https://www.wanshuge.org/book/36780_${ID}.html` : `https://www.wanshuge.org/book/36780/${ID}_${n}.html`;
  try {
    const { html } = await fetchSourceHtml(url, { highPriority: false });
    const ps = [...html.matchAll(/qsbs\.bb\('([^']+)'\)/g)].map((m) => Buffer.from(m[1], 'base64').toString('utf8')).join('');
    const count = (ps.match(/<p>/g) ?? []).length;
    const nextMatch = html.match(/href="([^"]+)"[^>]*>下一章/);
    const chars = ps.replace(/<[^>]+>/g, '').length;
    console.log(`page ${n}: ${count} paras, ${chars} chars, next=${nextMatch?.[1] ?? '?'}, first=${ps.replace(/<[^>]+>/g, '').slice(0, 20)}`);
    if (count === 0) break;
  } catch (e) { console.log(`page ${n}: ERROR ${String(e).slice(0, 60)}`); break; }
}
