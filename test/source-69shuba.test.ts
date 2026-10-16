import { beforeEach, describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import {
  encodeGbkComponent,
  fetchShubaChapter,
  isShubaBookId,
  parseShubaBookInfo,
  parseShubaCatalog,
  parseShubaChapter,
  parseShubaChapterLink,
  parseShubaSearch,
  resetShubaSearchGate,
  searchShuba,
  shubaCatalogUrl,
  shubaChapterUrl,
  shubaCoverUrl,
  shubaInfoUrl,
  shubaNumber,
} from '../src/shared/source-69shuba.ts';
import { decodeHtml, isChallengeHtml, SourceError, type TocEntry } from '../src/shared/source.ts';
import {
  bookCoverUrl,
  isRemoteBookId,
  mergeBookToc,
  parseBookUrl,
  placeholderTitle,
  sourceOfBook,
} from '../src/shared/sources.ts';
import { handleApi, MemoryApiCache, type SearchResponse } from '../src/shared/api.ts';
import type { ChapterResult, TocRangeResult } from '../src/shared/source.ts';

const here = dirname(fileURLToPath(import.meta.url));
/** 69书吧 fixture 原始字节（GBK） */
const bytes = (name: string) => new Uint8Array(readFileSync(join(here, 'fixtures', '69shuba', name)));
/** 解码后的 HTML（走与线上相同的字符集嗅探） */
const page = (name: string) => decodeHtml(bytes(name), 'text/html');
const challenge = readFileSync(join(here, 'fixtures', 'cloudflare-challenge.html'), 'utf8');

const BOOK = '69shuba-51254';

/** 按 pathname 返回 GBK 字节；Content-Type 不带 charset（只能靠 meta 嗅探） */
function gbkFetcher(routes: Record<string, string>, calls: { url: string; init?: RequestInit }[] = []) {
  return async (input: string, init?: RequestInit): Promise<Response> => {
    calls.push({ url: input, init });
    const name = routes[new URL(input).pathname];
    if (name === undefined) return new Response('not found', { status: 404, headers: { 'content-type': 'text/html' } });
    return new Response(bytes(name), { status: 200, headers: { 'content-type': 'text/html' } });
  };
}

const ctx = () => ({ cache: new MemoryApiCache(), now: () => 1_700_000_000_000, retries: 0 });

describe('69书吧：书号与 URL（受限拼装）', () => {
  it('书号带前缀，与万书阁纯数字书号互不冲突', () => {
    expect(isShubaBookId(BOOK)).toBe(true);
    expect(isShubaBookId('51254')).toBe(false);
    expect(isShubaBookId('69shuba-0')).toBe(false);
    expect(isShubaBookId('69shuba-1234567890')).toBe(false);
    expect(isShubaBookId('69shuba-51254/../x')).toBe(false);
    expect(shubaNumber(BOOK)).toBe('51254');
    expect(sourceOfBook(BOOK)).toBe('69shuba');
    expect(sourceOfBook('36780')).toBe('wanshuge');
    expect(sourceOfBook('local-abc')).toBe(null);
    expect(isRemoteBookId(BOOK)).toBe(true);
    expect(placeholderTitle(BOOK)).toBe('69书吧 51254');
  });

  it('只由校验过的书号/章节号拼出 URL', () => {
    expect(shubaInfoUrl(BOOK)).toBe('https://www.69shuba.com/book/51254.htm');
    expect(shubaCatalogUrl(BOOK)).toBe('https://www.69shuba.com/book/51254/');
    expect(shubaChapterUrl(BOOK, '30001002')).toBe('https://www.69shuba.com/txt/51254/30001002');
    expect(shubaCoverUrl(BOOK)).toBe('https://static.69shuba.com/files/article/image/51/51254/51254s.jpg');
    expect(shubaCoverUrl('69shuba-999')).toBe('https://static.69shuba.com/files/article/image/0/999/999s.jpg');
    expect(bookCoverUrl(BOOK)).toBe(shubaCoverUrl(BOOK));
    expect(bookCoverUrl('36780')).toBe('https://wanshuge.org/img/36780.jpg');
    expect(bookCoverUrl('local-x')).toBe(null);
    expect(() => shubaChapterUrl(BOOK, '../x')).toThrow();
    expect(() => shubaCatalogUrl('36780')).toThrow();
  });

  it('章节链接只认本书 /txt/{n}/{id}（含相对/协议相对），其它书与外站返回 null', () => {
    expect(parseShubaChapterLink(BOOK, 'https://www.69shuba.com/txt/51254/30001002')).toBe('30001002');
    expect(parseShubaChapterLink(BOOK, '/txt/51254/30001002')).toBe('30001002');
    expect(parseShubaChapterLink(BOOK, '//69shuba.com/txt/51254/30001002#top')).toBe('30001002');
    expect(parseShubaChapterLink(BOOK, 'https://www.69shuba.com/txt/12345/30001002')).toBe(null);
    expect(parseShubaChapterLink(BOOK, 'https://evil.example/txt/51254/30001002')).toBe(null);
    expect(parseShubaChapterLink(BOOK, 'https://www.69shuba.com/book/51254/')).toBe(null);
    expect(parseShubaChapterLink(BOOK, 'javascript:void(0)')).toBe(null);
  });

  it('粘贴网址解析为书号（用户给出的链接与常见变体）', () => {
    expect(parseBookUrl('https://www.69shuba.com/book/51254/')).toEqual({ bookId: BOOK });
    expect(parseBookUrl('https://www.69shuba.com/book/51254.htm')).toEqual({ bookId: BOOK });
    expect(parseBookUrl('www.69shuba.com/book/51254/')).toEqual({ bookId: BOOK });
    expect(parseBookUrl('https://69shuba.cx/book/51254/')).toEqual({ bookId: BOOK });
    expect(parseBookUrl('https://www.69shuba.com/txt/51254/30001002')).toEqual({ bookId: BOOK, chapterId: '30001002' });
    expect(parseBookUrl('https://wanshuge.org/book/36780/')).toEqual({ bookId: '36780' });
    expect(parseBookUrl('https://wanshuge.org/book/36780_38621328.html')).toEqual({ bookId: '36780', chapterId: '38621328' });
    expect(parseBookUrl('https://wanshuge.org/book/36780/12.html')).toEqual({ bookId: '36780' });
    expect(parseBookUrl('https://evil.example/book/51254/')).toBe(null);
    expect(parseBookUrl('https://www.69shuba.com/login.php')).toBe(null);
    expect(parseBookUrl('万古神帝')).toBe(null);
    expect(parseBookUrl('飞天鱼 万古')).toBe(null);
  });
});

describe('字符集与人机验证', () => {
  it('GBK 页面按 meta charset 解码；Content-Type 的 charset 优先', () => {
    const html = page('catalog-51254.html');
    expect(html).toContain('测试书名最新章节列表');
    expect(decodeHtml(bytes('catalog-51254.html'), 'text/html; charset=GBK')).toBe(html);
    expect(decodeHtml(new TextEncoder().encode('<p>万古神帝</p>'), 'text/html; charset=utf-8')).toBe('<p>万古神帝</p>');
    // 未知字符集标签按 UTF-8，不抛错
    expect(decodeHtml(new TextEncoder().encode('<p>好</p>'), 'text/html; charset=x-unknown')).toBe('<p>好</p>');
  });

  it('识别 Cloudflare 挑战页（真实抓取），正常页面不误判', () => {
    expect(isChallengeHtml(challenge)).toBe(true);
    for (const name of ['book-51254.htm', 'catalog-51254.html', 'chapter-30001002.html', 'search.html']) {
      expect(isChallengeHtml(page(name))).toBe(false);
    }
    // 正常页面引用 Turnstile 组件不算挑战页
    expect(isChallengeHtml('<html><head><title>第1章</title><script src="https://challenges.cloudflare.com/turnstile/v0/api.js"></script>')).toBe(false);
  });

  it('上游返回挑战页时抛 blocked，且不重试、不降级 http（不尝试绕过）', async () => {
    const calls: string[] = [];
    const fetcher = async (url: string) => {
      calls.push(url);
      return new Response(challenge, { status: 403, headers: { 'content-type': 'text/html', 'cf-mitigated': 'challenge' } });
    };
    const err = await fetchShubaChapter(BOOK, '30001002', { fetcher, retries: 3, sleep: async () => undefined }).catch((e) => e);
    expect(err).toBeInstanceOf(SourceError);
    expect((err as SourceError).code).toBe('blocked');
    expect((err as SourceError).retryable).toBe(false);
    expect((err as SourceError).message).toContain('人机验证');
    expect(calls).toEqual(['https://www.69shuba.com/txt/51254/30001002']);
  });

  it('Android 壳的 cf-challenge 标记（403 文本）同样识别为 blocked', async () => {
    const fetcher = async () => new Response('cf-challenge', { status: 403, headers: { 'content-type': 'text/plain' } });
    const err = await fetchShubaChapter(BOOK, '30001002', { fetcher, retries: 0 }).catch((e) => e);
    expect((err as SourceError).code).toBe('blocked');
  });

  it('只允许 69书吧主机：重定向到外站即拒绝', async () => {
    const fetcher = async () => {
      const res = new Response(bytes('chapter-30001002.html'), { status: 200, headers: { 'content-type': 'text/html' } });
      Object.defineProperty(res, 'url', { value: 'https://evil.example/txt/51254/30001002' });
      return res;
    };
    const err = await fetchShubaChapter(BOOK, '30001002', { fetcher, retries: 0 }).catch((e) => e);
    expect((err as SourceError).code).toBe('redirected');
  });
});

describe('69书吧：目录', () => {
  it('只取 #catalog，按 data-num 升序（源站倒序），去掉“最新章节”块与其它书', () => {
    const entries = parseShubaCatalog(BOOK, page('catalog-51254.html'));
    expect(entries.map((e) => e.id)).toEqual(['30001001', '30001002', '30001003', '30001004', '30001005']);
    expect(entries.map((e) => e.title)).toEqual(['第1章 出山', '第2章 拜师', '第3章 下山', '番外 雪夜', '第4章 北上']);
    expect(entries[0]!.number).toBe(1);
    // 番外按源站目录原位保留，不移到末尾
    expect(entries.every((e) => e.extra === false)).toBe(true);
  });

  it('没有 data-num 时按文档顺序，倒序则翻转', () => {
    const html = page('catalog-51254.html').replace(/ data-num="\d+"/g, '');
    expect(parseShubaCatalog(BOOK, html).map((e) => e.id)).toEqual([
      '30001001', '30001002', '30001003', '30001004', '30001005',
    ]);
  });

  it('客户端合并：69书吧保持目录顺序只去重；万书阁仍走错号/重传启发式', () => {
    const entries = parseShubaCatalog(BOOK, page('catalog-51254.html'));
    const merged = mergeBookToc(BOOK, [entries, [entries[4]!]]);
    expect(merged.map((e) => e.id)).toEqual(entries.map((e) => e.id));
    // 同一组条目若当成万书阁书：番外被移到末尾（证明分派生效）
    const asWanshuge: TocEntry[] = entries.map((e) => ({ ...e, extra: e.title.startsWith('番外') }));
    expect(mergeBookToc('36780', [asWanshuge]).at(-1)!.title).toBe('番外 雪夜');
  });
});

describe('69书吧：书籍信息', () => {
  it('og meta：书名/作者/分类/状态/最新章/简介（去站点套话）', () => {
    const info = parseShubaBookInfo(BOOK, page('book-51254.htm'))!;
    expect(info).toMatchObject({
      id: BOOK,
      title: '测试书名',
      author: '测试作者',
      category: '玄幻魔法',
      status: '连载',
      latestChapter: '第4章 北上',
      updatedAt: '2025-05-01 08:00:00',
    });
    expect(info.intro).toBe('少年持剑出山，一路向北。 江湖很大，他只想找一个人。');
    expect(info.intro).not.toMatch(/69书吧|免费提供|最新章节由网友提供|</);
    // read_url 指向目录页，不是首章
    expect(info.firstChapterId).toBeUndefined();
  });

  it('没有 og meta 时读 div.booknav2 / div.navtxt', () => {
    const html = page('book-51254.htm').replace(/<meta property="og:[^>]*>/g, '');
    const info = parseShubaBookInfo(BOOK, html)!;
    expect(info.title).toBe('测试书名');
    expect(info.author).toBe('测试作者');
    expect(info.category).toBe('玄幻魔法');
    expect(info.status).toBe('连载');
    expect(info.intro).toContain('江湖很大');
  });

  it('目录页没有书籍信息（不会把页面 h1 误当书名）', () => {
    expect(parseShubaBookInfo(BOOK, page('catalog-51254.html'))).toBe(null);
  });
});

describe('69书吧：章节', () => {
  it('正文：去标题行/广告/本章完，保留原文重复句，导航来自 bookinfo', () => {
    const ch = parseShubaChapter(BOOK, '30001002', page('chapter-30001002.html'));
    expect(ch.title).toBe('第2章 拜师');
    expect(ch.paragraphs).toEqual([
      '山门前的石阶覆着薄雪。',
      '“你来做什么？”老人问。',
      '“拜师。”',
      '“拜师。”',
      '少年把剑放在地上，跪了下去。',
    ]);
    expect(ch.prevId).toBe('30001001');
    expect(ch.nextId).toBe('30001003');
    expect(ch.complete).toBe(true);
    expect(ch.pageCount).toBe(1);
    expect(ch.missingPages).toEqual([]);
    expect(ch.charCount).toBe(ch.paragraphs.join('').length);
  });

  it('首章没有上一章、末章没有下一章，仍是完整章节', () => {
    const first = parseShubaChapter(BOOK, '30001001', page('chapter-30001001.html'));
    expect(first.prevId).toBe(null);
    expect(first.nextId).toBe('30001002');
    expect(first.paragraphs).toEqual(['天还没亮，少年就背着剑下了山。', '山下的路很长。']);
    expect(first.complete).toBe(true);
    const last = parseShubaChapter(BOOK, '30001005', page('chapter-30001005.html'));
    expect(last.prevId).toBe('30001004');
    expect(last.nextId).toBe(null);
    expect(last.complete).toBe(true);
  });

  it('没有 bookinfo 脚本时按“上一章/下一章”按钮取导航（推荐区的其它书不算）', () => {
    const html = page('chapter-30001002.html').replace(/<script>\s*var bookinfo[\s\S]*?<\/script>/, '');
    const ch = parseShubaChapter(BOOK, '30001002', html);
    expect(ch.prevId).toBe('30001001');
    expect(ch.nextId).toBe('30001003');
    expect(ch.complete).toBe(true);
  });

  it('认不出正文（模板变化/错误页）时 complete=false，等待补全而不是缓存成完整', () => {
    const ch = parseShubaChapter(BOOK, '30001002', '<html><body><p>出错了</p></body></html>');
    expect(ch.paragraphs).toEqual([]);
    expect(ch.complete).toBe(false);
  });
});

describe('69书吧：搜索', () => {
  beforeEach(() => resetShubaSearchGate());

  it('GBK 百分号编码（与浏览器提交 GBK 表单一致）', () => {
    expect(encodeGbkComponent('万古神帝')).toBe('%CD%F2%B9%C5%C9%F1%B5%DB');
    expect(encodeGbkComponent('abc 1')).toBe('abc%201');
    expect(encodeGbkComponent('飞天鱼')).toBe('%B7%C9%CC%EC%D3%E3');
    // GBK 之外的字符按 &#码位; 提交
    expect(encodeGbkComponent('😀')).toBe('%26%23128512%3B');
  });

  it('解析结果列表（书名去高亮、作者/分类/最新章/简介）', () => {
    const r = parseShubaSearch(page('search.html'), 'https://www.69shuba.com/modules/article/search.php')!;
    expect(r).toHaveLength(2);
    expect(r[0]).toMatchObject({
      id: BOOK,
      title: '测试书名',
      author: '测试作者',
      category: '玄幻魔法',
      latestChapter: '第4章 北上',
    });
    expect(r[0]!.intro).toBe('少年持剑出山，一路向北。');
    expect(r[1]).toMatchObject({ id: '69shuba-8888', title: '测试续集', author: '另一位作者' });
  });

  it('唯一结果被重定向到详情页时解析详情页；无结果页返回空；认不出返回 null', () => {
    const one = parseShubaSearch(page('book-51254.htm'), 'https://www.69shuba.com/book/51254.htm')!;
    expect(one).toEqual([expect.objectContaining({ id: BOOK, title: '测试书名', author: '测试作者' })]);
    expect(parseShubaSearch('<div class="mybox">没有找到相关的小说</div>')).toEqual([]);
    expect(parseShubaSearch('<html><body>维护中</body></html>')).toBe(null);
  });

  it('searchShuba 以 GBK 表单 POST 到 search.php', async () => {
    const calls: { url: string; init?: RequestInit }[] = [];
    const fetcher = gbkFetcher({ '/modules/article/search.php': 'search.html' }, calls);
    const r = await searchShuba(' 测试 ', { fetcher, retries: 0, sleep: async () => undefined });
    expect(r.map((b) => b.id)).toEqual([BOOK, '69shuba-8888']);
    expect(calls[0]!.url).toBe('https://www.69shuba.com/modules/article/search.php');
    expect(calls[0]!.init?.method).toBe('POST');
    expect(calls[0]!.init?.body).toBe('searchkey=%B2%E2%CA%D4&searchtype=all');
  });
});

describe('API：按书号分派到 69书吧', () => {
  beforeEach(() => resetShubaSearchGate());

  it('目录：单页目录 + 书籍信息（目录页无 og 时补取详情页），第 2 页不发请求', async () => {
    const calls: { url: string }[] = [];
    const fetcher = gbkFetcher({ '/book/51254/': 'catalog-51254.html', '/book/51254.htm': 'book-51254.htm' }, calls);
    const res = await handleApi(new URL(`https://x/api/toc?book=${BOOK}&from=1&to=2`), { ...ctx(), fetcher });
    expect(res.status).toBe(200);
    const body = (await res.json()) as TocRangeResult;
    expect(body.totalPages).toBe(1);
    expect(body.failedPages).toEqual([]);
    expect(body.pages).toHaveLength(1);
    expect(body.pages[0]!.entries).toHaveLength(5);
    expect(body.book).toMatchObject({ id: BOOK, title: '测试书名', author: '测试作者', firstChapterId: '30001001' });
    expect(calls.map((c) => new URL(c.url).pathname).sort()).toEqual(['/book/51254.htm', '/book/51254/']);

    const page2 = await handleApi(new URL(`https://x/api/toc?book=${BOOK}&from=2&to=2`), { ...ctx(), fetcher });
    expect(((await page2.json()) as TocRangeResult).pages).toEqual([]);
    expect(calls).toHaveLength(2);
  });

  it('章节：GBK 正文解码后返回', async () => {
    const fetcher = gbkFetcher({ '/txt/51254/30001002': 'chapter-30001002.html' });
    const res = await handleApi(new URL(`https://x/api/chapter?book=${BOOK}&id=30001002`), { ...ctx(), fetcher });
    expect(res.status).toBe(200);
    const body = (await res.json()) as ChapterResult;
    expect(body.title).toBe('第2章 拜师');
    expect(body.paragraphs[0]).toBe('山门前的石阶覆着薄雪。');
    expect(body.complete).toBe(true);
  });

  it('搜索：source=69shuba 走 69书吧；非法书源/书号 400', async () => {
    const calls: { url: string; init?: RequestInit }[] = [];
    const fetcher = gbkFetcher({ '/modules/article/search.php': 'search.html' }, calls);
    const context = { ...ctx(), fetcher };
    const res = await handleApi(new URL(`https://x/api/search?q=${encodeURIComponent('测试')}&source=69shuba`), context);
    expect(res.status).toBe(200);
    const body = (await res.json()) as SearchResponse;
    expect(body.source).toBe('69shuba');
    expect(body.results[0]!.id).toBe(BOOK);
    // 第二次命中缓存（缓存键含书源，不与万书阁混用）
    const again = await handleApi(new URL(`https://x/api/search?q=${encodeURIComponent('测试')}&source=69shuba`), context);
    expect(again.headers.get('x-reader-cache')).toBe('hit');
    expect(calls).toHaveLength(1);

    expect((await handleApi(new URL('https://x/api/search?q=a&source=evil'), context)).status).toBe(400);
    expect((await handleApi(new URL('https://x/api/toc?book=69shuba-0&from=1&to=1'), context)).status).toBe(400);
    expect((await handleApi(new URL('https://x/api/chapter?book=69shuba-51254x&id=1'), context)).status).toBe(400);
  });

  it('人机验证：502 + code=blocked + 中文提示，不缓存失败结果', async () => {
    let n = 0;
    const fetcher = async () => {
      n++;
      return new Response(challenge, { status: 403, headers: { 'content-type': 'text/html', 'cf-mitigated': 'challenge' } });
    };
    const context = { ...ctx(), fetcher };
    const res = await handleApi(new URL(`https://x/api/chapter?book=${BOOK}&id=30001002`), context);
    expect(res.status).toBe(502);
    const body = (await res.json()) as { error: { code: string; message: string; retryable: boolean } };
    expect(body.error).toMatchObject({ code: 'blocked', retryable: false });
    expect(body.error.message).toContain('人机验证');
    const toc = await handleApi(new URL(`https://x/api/toc?book=${BOOK}&from=1&to=2`), context);
    expect(toc.status).toBe(502);
    await handleApi(new URL(`https://x/api/toc?book=${BOOK}&from=1&to=2`), context);
    expect(n).toBe(3);
  });
});
