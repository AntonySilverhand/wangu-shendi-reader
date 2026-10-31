import { beforeEach, describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import {
  fetchLulushuChapter,
  isLulushuBookId,
  lulushuAjaxForm,
  lulushuBookUrl,
  lulushuChapterUrl,
  lulushuCoverUrl,
  parseLulushuAjax,
  parseLulushuBookInfo,
  parseLulushuCatalog,
  parseLulushuChapterLink,
  parseLulushuChapterPage,
  parseLulushuSearch,
  resetLulushuSearchGate,
  searchLulushu,
} from '../src/shared/source-lulushu.ts';
import type { ChapterResult, TocRangeResult } from '../src/shared/source.ts';
import { isRemoteBookId, parseBookUrl, placeholderTitle, sourceOfBook } from '../src/shared/sources.ts';
import { handleApi, MemoryApiCache } from '../src/shared/api.ts';

const here = dirname(fileURLToPath(import.meta.url));
/** 露露书 fixture（真实页面裁剪，UTF-8） */
const page = (name: string) => readFileSync(join(here, 'fixtures', 'lulushu', name), 'utf8');
const BOOK = 'lulushu-41337';
const HASH = '4b09fca4c6e340c9';

type Call = { url: string; init?: RequestInit };
function fetcher(routes: Record<string, string>, calls: Call[] = []) {
  return async (input: string, init?: RequestInit): Promise<Response> => {
    calls.push({ url: input, init });
    const name = routes[new URL(input).pathname];
    if (name === undefined) return new Response('not found', { status: 404, headers: { 'content-type': 'text/html' } });
    return new Response(page(name), { status: 200, headers: { 'content-type': 'text/html; charset=UTF-8' } });
  };
}
const ctx = () => ({ cache: new MemoryApiCache(), now: () => 1_700_000_000_000, retries: 0 });

describe('露露书：书号与 URL', () => {
  it('书号带前缀；URL 按 {a}=⌊n/1000⌋ 拼出', () => {
    expect(isLulushuBookId(BOOK)).toBe(true);
    expect(isLulushuBookId('lulushu-0')).toBe(false);
    expect(sourceOfBook(BOOK)).toBe('lulushu');
    expect(isRemoteBookId(BOOK)).toBe(true);
    expect(placeholderTitle(BOOK)).toBe('露露书 41337');
    expect(lulushuBookUrl(BOOK)).toBe('https://www.lulushu55.com/41_41337/');
    expect(lulushuChapterUrl(BOOK, '0')).toBe('https://www.lulushu55.com/41_41337/0.html');
    expect(lulushuCoverUrl(BOOK)).toBe('https://www.lulushu55.com/uploads/41/41337.jpg');
  });

  it('正文接口表单只由书号、章节号与十六进制校验码组成', () => {
    expect(lulushuAjaxForm(BOOK, '3587', HASH)).toBe(`id=41337&eKey=${HASH}&cid=3587&basecid=3587`);
    expect(() => lulushuAjaxForm(BOOK, '3587', 'x&id=1')).toThrow();
    expect(() => lulushuAjaxForm(BOOK, '../1', HASH)).toThrow();
  });

  it('章节链接只认本书 /{a}_{n}/{id}.html；粘贴网址（含换过序号的旧域名）解析为书号', () => {
    expect(parseLulushuChapterLink(BOOK, '/41_41337/3587.html')).toBe('3587');
    expect(parseLulushuChapterLink(BOOK, '/41_41338/3587.html')).toBe(null);
    expect(parseLulushuChapterLink(BOOK, '/40_41337/3587.html')).toBe(null);
    expect(parseLulushuChapterLink(BOOK, 'https://evil.example/41_41337/1.html')).toBe(null);
    expect(parseBookUrl('https://www.lulushu55.com/41_41337/')).toEqual({ bookId: BOOK });
    expect(parseBookUrl('https://www.lulushu54.com/41_41337/3587.html')).toEqual({ bookId: BOOK, chapterId: '3587' });
    expect(parseBookUrl('https://www.lulushu55.com/41_41337/3587_1.html')).toEqual({ bookId: BOOK, chapterId: '3587' });
    expect(parseBookUrl('https://www.lulushu55.com/40_41337/')).toBe(null);
  });
});

describe('露露书：书页、章节页与正文接口', () => {
  it('目录只取“全部章节目录”（不取上方倒序的最新章节），去掉序号前缀', () => {
    const toc = parseLulushuCatalog(BOOK, page('book-41337.html'));
    expect(toc.map((e) => e.id)).toEqual(['0', '1', '2', '3585', '3586', '3587', '3588', '4231', '4232']);
    expect(toc[0]).toMatchObject({ title: '第1章 八百年后', number: 1 });
    expect(toc.find((e) => e.id === '3587')!.title).toBe('第三千五百五十八章 回城');
  });

  it('书籍信息来自 og meta，简介去掉站点套话', () => {
    const info = parseLulushuBookInfo(BOOK, page('book-41337.html'))!;
    expect(info).toMatchObject({ title: '万古神帝', author: '飞天鱼', category: '玄幻魔法', status: '已完结' });
    expect(info.intro ?? '').not.toMatch(/露露书/);
  });

  it('章节页：标题/校验码/上一章下一章用 prevcid/nextcid（next_page 指向本章网页第二页，不用）', () => {
    expect(parseLulushuChapterPage(BOOK, '3587', page('chapter-3587.html'))).toEqual({
      title: '第三千五百五十八章 回城', hash: HASH, prevId: '3586', nextId: '3588',
    });
    const first = parseLulushuChapterPage(BOOK, '0', page('chapter-0.html'));
    expect(first).toMatchObject({ title: '第1章 八百年后', prevId: null, nextId: '1' });
  });

  it('正文接口：<p> 段落，去掉首段标题（号码不同也认）与广告 div；失败状态抛错', () => {
    const r = parseLulushuAjax(page('ajax-3587.json'), '第三千五百五十八章 回城');
    expect(r.paragraphs[0]).toBe('五清宗的担忧，是有道理的。');
    expect(r.paragraphs).toHaveLength(5);
    expect(r.paragraphs.some((p) => /露露书|第3579章/.test(p))).toBe(false);
    expect(r.nextpage).toBe(null);
    expect(() => parseLulushuAjax('{"status":"unavailable hash"}', 'x')).toThrow(/unavailable hash/);
    expect(() => parseLulushuAjax('<html>', 'x')).toThrow();
  });

  it('取章节：先 GET 章节页拿校验码，再 POST 正文接口', async () => {
    const calls: Call[] = [];
    const f = fetcher({ '/41_41337/3587.html': 'chapter-3587.html', '/home/index/ajaxchapter': 'ajax-3587.json' }, calls);
    const c = await fetchLulushuChapter(BOOK, '3587', { fetcher: f, retries: 0 });
    expect(c).toMatchObject({ title: '第三千五百五十八章 回城', prevId: '3586', nextId: '3588', complete: true, pageCount: 1 });
    expect(calls.map((x) => [new URL(x.url).pathname, x.init?.method ?? 'GET'])).toEqual([
      ['/41_41337/3587.html', 'GET'],
      ['/home/index/ajaxchapter', 'POST'],
    ]);
    expect(calls[1]!.init?.body).toBe(`id=41337&eKey=${HASH}&cid=3587&basecid=3587`);
  });
});

describe('露露书：搜索与 API', () => {
  beforeEach(() => resetLulushuSearchGate());

  it('搜索结果 section.fengtui dl；POST action=search&q=', async () => {
    expect(parseLulushuSearch(page('search.html'))!.map((b) => [b.id, b.title, b.author])).toEqual([
      [BOOK, '万古神帝', '飞天鱼'],
      ['lulushu-17554', '万古神帝之张若尘，九郡主双修', '青檀'],
    ]);
    const calls: Call[] = [];
    await searchLulushu('万古神帝', { fetcher: fetcher({ '/home/search': 'search.html' }, calls), retries: 0, sleep: async () => undefined });
    expect(calls[0]!.init?.body).toBe(`action=search&q=${encodeURIComponent('万古神帝')}`);
    expect(parseLulushuSearch('<html>维护中</html>')).toBe(null);
  });

  it('API：目录单页 + 书籍信息；章节两步请求', async () => {
    const f = fetcher({ '/41_41337/': 'book-41337.html', '/41_41337/3587.html': 'chapter-3587.html', '/home/index/ajaxchapter': 'ajax-3587.json' });
    const toc = (await (await handleApi(new URL(`https://x/api/toc?book=${BOOK}&from=1&to=2`), { ...ctx(), fetcher: f })).json()) as TocRangeResult;
    expect(toc.pages[0]!.entries).toHaveLength(9);
    expect(toc.book).toMatchObject({ title: '万古神帝', author: '飞天鱼', firstChapterId: '0' });
    const ch = (await (await handleApi(new URL(`https://x/api/chapter?book=${BOOK}&id=3587`), { ...ctx(), fetcher: f })).json()) as ChapterResult;
    expect(ch).toMatchObject({ title: '第三千五百五十八章 回城', complete: true });
  });
});
