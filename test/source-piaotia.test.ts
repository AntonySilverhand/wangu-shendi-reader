import { beforeEach, describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import {
  isPiaotiaBookId,
  parsePiaotiaBookInfo,
  parsePiaotiaCatalog,
  parsePiaotiaChapter,
  parsePiaotiaChapterLink,
  parsePiaotiaSearch,
  piaotiaCatalogUrl,
  piaotiaChapterUrl,
  piaotiaCoverUrl,
  piaotiaInfoUrl,
  resetPiaotiaSearchGate,
  searchPiaotia,
} from '../src/shared/source-piaotia.ts';
import { decodeHtml, type ChapterResult, type TocRangeResult } from '../src/shared/source.ts';
import { bookCoverUrl, isRemoteBookId, parseBookUrl, placeholderTitle, sourceOfBook } from '../src/shared/sources.ts';
import { handleApi, MemoryApiCache, type SearchResponse } from '../src/shared/api.ts';

const here = dirname(fileURLToPath(import.meta.url));
/** 飘天文学 fixture（真实页面裁剪，GBK 原始字节） */
const bytes = (name: string) => new Uint8Array(readFileSync(join(here, 'fixtures', 'piaotia', name)));
const page = (name: string) => decodeHtml(bytes(name), 'text/html');

const BOOK = 'piaotia-7906';

function gbkFetcher(routes: Record<string, string>, calls: { url: string; init?: RequestInit }[] = []) {
  return async (input: string, init?: RequestInit): Promise<Response> => {
    calls.push({ url: input, init });
    const name = routes[new URL(input).pathname];
    if (name === undefined) return new Response('not found', { status: 404, headers: { 'content-type': 'text/html' } });
    return new Response(bytes(name), { status: 200, headers: { 'content-type': 'text/html' } });
  };
}

const ctx = () => ({ cache: new MemoryApiCache(), now: () => 1_700_000_000_000, retries: 0 });

describe('飘天文学：书号与 URL（受限拼装）', () => {
  it('书号带前缀，与其它书源互不冲突', () => {
    expect(isPiaotiaBookId(BOOK)).toBe(true);
    expect(isPiaotiaBookId('7906')).toBe(false);
    expect(isPiaotiaBookId('piaotia-0')).toBe(false);
    expect(sourceOfBook(BOOK)).toBe('piaotia');
    expect(isRemoteBookId(BOOK)).toBe(true);
    expect(placeholderTitle(BOOK)).toBe('飘天文学 7906');
  });

  it('URL 按 {a}=⌊n/1000⌋ 分片拼出', () => {
    expect(piaotiaInfoUrl(BOOK)).toBe('https://www.piaotia.com/bookinfo/7/7906.html');
    expect(piaotiaCatalogUrl(BOOK)).toBe('https://www.piaotia.com/html/7/7906/');
    expect(piaotiaChapterUrl(BOOK, '9852372')).toBe('https://www.piaotia.com/html/7/7906/9852372.html');
    expect(piaotiaCoverUrl(BOOK)).toBe('https://www.piaotia.com/files/article/image/7/7906/7906s.jpg');
    expect(bookCoverUrl('piaotia-246')).toBe('https://www.piaotia.com/files/article/image/0/246/246s.jpg');
    expect(() => piaotiaChapterUrl(BOOK, '../x')).toThrow();
  });

  it('章节链接只认本书 /html/{a}/{n}/{id}.html；目录页、其它书、错分片、外站返回 null', () => {
    expect(parsePiaotiaChapterLink(BOOK, '9852372.html')).toBe('9852372');
    expect(parsePiaotiaChapterLink(BOOK, 'https://www.piaotia.com/html/7/7906/9852372.html')).toBe('9852372');
    expect(parsePiaotiaChapterLink(BOOK, 'index.html')).toBe(null);
    expect(parsePiaotiaChapterLink(BOOK, './')).toBe(null);
    expect(parsePiaotiaChapterLink(BOOK, '/html/7/7907/1.html')).toBe(null);
    expect(parsePiaotiaChapterLink(BOOK, '/html/8/7906/1.html')).toBe(null);
    expect(parsePiaotiaChapterLink(BOOK, 'https://evil.example/html/7/7906/1.html')).toBe(null);
  });

  it('粘贴网址解析为书号', () => {
    expect(parseBookUrl('https://www.piaotia.com/bookinfo/7/7906.html')).toEqual({ bookId: BOOK });
    expect(parseBookUrl('https://www.piaotia.com/html/7/7906/')).toEqual({ bookId: BOOK });
    expect(parseBookUrl('www.piaotia.com/html/7/7906/index.html')).toEqual({ bookId: BOOK });
    expect(parseBookUrl('https://www.piaotia.com/html/7/7906/9852372.html')).toEqual({ bookId: BOOK, chapterId: '9852372' });
    expect(parseBookUrl('https://www.piaotia.com/html/6/7906/')).toBe(null);
    expect(parseBookUrl('https://www.piaotia.com/bookinfo/7/7906/')).toBe(null);
  });
});

describe('飘天文学：目录与书籍信息', () => {
  it('div.centent 的 ul > li > a，按文档顺序；卷名与空 li 不算章节', () => {
    const toc = parsePiaotiaCatalog(BOOK, page('toc.html'));
    expect(toc).toHaveLength(27);
    expect(toc[0]).toEqual({ id: '4751825', title: '第一章 八百年后', displayTitle: '第一章 八百年后', number: 1, extra: false });
    expect(toc.find((e) => e.id === '9852372')).toMatchObject({ title: '第3579章 回城', number: 3579 });
    expect(toc.at(-1)!.title).toBe('番外第十八章 诸神集结');
  });

  it('详情页：书名/作者/分类/状态/更新/字数/简介（不带“最新章节预览”）', () => {
    const info = parsePiaotiaBookInfo(BOOK, page('info.html'))!;
    expect(info).toMatchObject({ id: BOOK, title: '万古神帝', author: '飞天鱼', category: '玄幻魔法', status: '连载中', updatedAt: '2024-11-30', size: '1789.10万字' });
    expect(info.intro).toMatch(/^八百年前，明帝之子张若尘/);
    expect(info.intro).not.toMatch(/最新章节|查看全部/);
  });
});

describe('飘天文学：章节', () => {
  it('正文为 &nbsp; 缩进 + <br>：去掉广告脚本表格，导航来自上一章/下一章按钮', () => {
    const c = parsePiaotiaChapter(BOOK, '9852372', page('chapter-9852372.html'));
    expect(c.title).toBe('第3579章 回城');
    expect(c.paragraphs[0]).toBe('五清宗的担忧，是有道理的。');
    expect(c.paragraphs).toHaveLength(6);
    expect(c.paragraphs.some((p) => /script|上一章|style\d/.test(p))).toBe(false);
    expect(c).toMatchObject({ prevId: '9849094', nextId: '9855178', complete: true, pageCount: 1 });
  });

  it('卷首章：H1 去掉卷名；上一章指向目录（index.html）时为无，仍是完整章节', () => {
    const c = parsePiaotiaChapter(BOOK, '4751825', page('chapter-4751825.html'));
    expect(c.title).toBe('第一章 八百年后');
    expect(c.paragraphs[0]).toBe('“池瑶，我待你如挚爱，你为何要杀我？”');
    expect(c).toMatchObject({ prevId: null, nextId: '4751826', complete: true });
  });

  it('认不出正文（错误页/模板变化）时 complete=false', () => {
    const c = parsePiaotiaChapter(BOOK, '1', '<html><body>维护中</body></html>');
    expect(c.paragraphs).toEqual([]);
    expect(c.complete).toBe(false);
  });
});

describe('飘天文学：搜索', () => {
  beforeEach(() => resetPiaotiaSearchGate());

  it('结果表格：书名/最新章/作者/更新', () => {
    const r = parsePiaotiaSearch(page('search.html'))!;
    expect(r.map((b) => b.id)).toEqual(['piaotia-16066', BOOK, 'piaotia-15614']);
    expect(r[1]).toEqual({ id: BOOK, title: '万古神帝', latestChapter: '番外第十八章 诸神集结', author: '飞天鱼', updated: '24-11-30' });
  });

  it('唯一结果被重定向到详情页（http）时解析详情页；无结果返回空；认不出返回 null', () => {
    expect(parsePiaotiaSearch(page('info.html'), 'http://www.piaotia.com/bookinfo/7/7906.html')).toEqual([
      expect.objectContaining({ id: BOOK, title: '万古神帝', author: '飞天鱼' }),
    ]);
    expect(parsePiaotiaSearch('<table class="grid"><caption>搜索结果</caption><tr><th>文章名称</th></tr></table>')).toEqual([]);
    expect(parsePiaotiaSearch('<html><body>维护中</body></html>')).toBe(null);
  });

  it('按书名 POST GBK 表单（searchtype=articlename）', async () => {
    const calls: { url: string; init?: RequestInit }[] = [];
    const fetcher = gbkFetcher({ '/modules/article/search.php': 'search.html' }, calls);
    const r = await searchPiaotia(' 万古神帝 ', { fetcher, retries: 0, sleep: async () => undefined });
    expect(r[1]!.id).toBe(BOOK);
    expect(calls[0]!.url).toBe('https://www.piaotia.com/modules/article/search.php');
    expect(calls[0]!.init?.method).toBe('POST');
    expect(calls[0]!.init?.body).toBe('searchtype=articlename&searchkey=%CD%F2%B9%C5%C9%F1%B5%DB');
  });
});

describe('API：按书号分派到飘天文学', () => {
  beforeEach(() => resetPiaotiaSearchGate());

  it('目录：单页目录 + 详情页书籍信息；第 2 页不发请求', async () => {
    const calls: { url: string }[] = [];
    const fetcher = gbkFetcher({ '/html/7/7906/': 'toc.html', '/bookinfo/7/7906.html': 'info.html' }, calls);
    const res = await handleApi(new URL(`https://x/api/toc?book=${BOOK}&from=1&to=2`), { ...ctx(), fetcher });
    expect(res.status).toBe(200);
    const body = (await res.json()) as TocRangeResult;
    expect(body.totalPages).toBe(1);
    expect(body.pages[0]!.entries).toHaveLength(27);
    expect(body.book).toMatchObject({ id: BOOK, title: '万古神帝', author: '飞天鱼', firstChapterId: '4751825' });
    const page2 = await handleApi(new URL(`https://x/api/toc?book=${BOOK}&from=2&to=2`), { ...ctx(), fetcher });
    expect(((await page2.json()) as TocRangeResult).pages).toEqual([]);
    expect(calls).toHaveLength(2);
  });

  it('章节：GBK 正文解码后返回；搜索走 source=piaotia', async () => {
    const fetcher = gbkFetcher({ '/html/7/7906/9852372.html': 'chapter-9852372.html', '/modules/article/search.php': 'search.html' });
    const res = await handleApi(new URL(`https://x/api/chapter?book=${BOOK}&id=9852372`), { ...ctx(), fetcher });
    const body = (await res.json()) as ChapterResult;
    expect(body).toMatchObject({ title: '第3579章 回城', complete: true });
    const s = await handleApi(new URL(`https://x/api/search?q=${encodeURIComponent('万古神帝')}&source=piaotia`), { ...ctx(), fetcher });
    expect(((await s.json()) as SearchResponse).results.map((b) => b.id)).toContain(BOOK);
    expect((await handleApi(new URL('https://x/api/chapter?book=piaotia-7906x&id=1'), { ...ctx(), fetcher })).status).toBe(400);
  });

  it('只允许飘天文学主机：重定向到外站即拒绝', async () => {
    const fetcher = async () => {
      const r = new Response('<html></html>', { status: 200, headers: { 'content-type': 'text/html' } });
      Object.defineProperty(r, 'url', { value: 'https://evil.example/x' });
      return r;
    };
    const res = await handleApi(new URL(`https://x/api/chapter?book=${BOOK}&id=9852372`), { ...ctx(), fetcher });
    expect(res.status).toBeGreaterThanOrEqual(400);
  });
});
