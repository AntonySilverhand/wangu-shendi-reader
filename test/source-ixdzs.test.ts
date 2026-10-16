import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { deflateRawSync } from 'node:zlib';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import {
  isIxdzsBookId,
  ixdzsDownloadUrl,
  ixdzsSearchUrl,
  ixdzsBookUrl,
  parseIxdzsBookPage,
  parseIxdzsChapterLink,
  parseIxdzsSearch,
  splitIxdzsTxt,
} from '../src/shared/source-ixdzs.ts';
import { decodeTextBytes, extractFirstTxt, listZipEntries, ZipError } from '../src/shared/zip.ts';
import { isChallengeHtml, type TocEntry, type TocRangeResult } from '../src/shared/source.ts';
import {
  isDownloadBookId,
  isRemoteBookId,
  isSourceBookId,
  mergeBookToc,
  parseBookUrl,
  placeholderTitle,
  sourceOfBook,
} from '../src/shared/sources.ts';
import { handleApi, MemoryApiCache, type SearchResponse } from '../src/shared/api.ts';

const here = dirname(fileURLToPath(import.meta.url));
const fixture = (name: string) => readFileSync(join(here, 'fixtures', 'ixdzs', name), 'utf8');
const BOOK = 'ixdzs-593824';

/** 测试用最小 ZIP 写入器（单文件；method 8 = deflate，0 = stored） */
function makeZip(name: string, data: Uint8Array, method: 0 | 8 = 8, opts: { badSize?: boolean } = {}): Uint8Array {
  const nameBytes = new TextEncoder().encode(name);
  const body = method === 8 ? new Uint8Array(deflateRawSync(data)) : data;
  const size = opts.badSize ? data.length + 1 : data.length;
  const local = new Uint8Array(30 + nameBytes.length);
  const lv = new DataView(local.buffer);
  lv.setUint32(0, 0x04034b50, true);
  lv.setUint16(4, 20, true);
  lv.setUint16(6, 0x800, true);
  lv.setUint16(8, method, true);
  lv.setUint32(18, body.length, true);
  lv.setUint32(22, size, true);
  lv.setUint16(26, nameBytes.length, true);
  local.set(nameBytes, 30);
  const central = new Uint8Array(46 + nameBytes.length);
  const cv = new DataView(central.buffer);
  cv.setUint32(0, 0x02014b50, true);
  cv.setUint16(8, 0x800, true);
  cv.setUint16(10, method, true);
  cv.setUint32(20, body.length, true);
  cv.setUint32(24, size, true);
  cv.setUint16(28, nameBytes.length, true);
  cv.setUint32(42, 0, true);
  central.set(nameBytes, 46);
  const eocd = new Uint8Array(22);
  const ev = new DataView(eocd.buffer);
  ev.setUint32(0, 0x06054b50, true);
  ev.setUint16(8, 1, true);
  ev.setUint16(10, 1, true);
  ev.setUint32(12, central.length, true);
  ev.setUint32(16, local.length + body.length, true);
  const out = new Uint8Array(local.length + body.length + central.length + eocd.length);
  out.set(local, 0);
  out.set(body, local.length);
  out.set(central, local.length + body.length);
  out.set(eocd, local.length + body.length + central.length);
  return out;
}

const sampleBytes = () => new Uint8Array(readFileSync(join(here, 'fixtures', 'ixdzs', 'sample-gb18030.txt')));

describe('爱下电子书：书号与 URL', () => {
  it('整本下载型书源：书号 ixdzs-{n}，不是在线书，也不是万书阁书号', () => {
    expect(isIxdzsBookId(BOOK)).toBe(true);
    expect(isIxdzsBookId('ixdzs-0')).toBe(false);
    expect(sourceOfBook(BOOK)).toBe('ixdzs');
    expect(isSourceBookId(BOOK)).toBe(true);
    expect(isDownloadBookId(BOOK)).toBe(true);
    expect(isRemoteBookId(BOOK)).toBe(false);
    expect(isDownloadBookId('69shuba-51254')).toBe(false);
    expect(placeholderTitle(BOOK)).toBe('爱下电子书 593824');
  });

  it('URL 只由书号拼出', () => {
    expect(ixdzsBookUrl(BOOK)).toBe('https://ixdzs8.com/read/593824/');
    expect(ixdzsDownloadUrl('ixdzs-137695')).toBe('https://down7.ixdzs8.com/137695.zip');
    expect(ixdzsSearchUrl('万古 神帝')).toBe('https://ixdzs8.com/bsearch?q=%E4%B8%87%E5%8F%A4%20%E7%A5%9E%E5%B8%9D');
    expect(() => ixdzsDownloadUrl('137695')).toThrow();
    expect(parseIxdzsChapterLink(BOOK, '/read/593824/p12.html')).toBe('12');
    expect(parseIxdzsChapterLink(BOOK, 'https://ixdzs8.com/read/593824/p12.html')).toBe('12');
    expect(parseIxdzsChapterLink(BOOK, '/read/1/p12.html')).toBe(null);
    expect(parseIxdzsChapterLink(BOOK, 'https://evil.example/read/593824/p12.html')).toBe(null);
  });

  it('粘贴网址：用户给出的书页链接、章节链接与繁体镜像', () => {
    expect(parseBookUrl('https://ixdzs8.com/read/137695/')).toEqual({ bookId: 'ixdzs-137695' });
    expect(parseBookUrl('ixdzs8.com/read/137695/')).toEqual({ bookId: 'ixdzs-137695' });
    expect(parseBookUrl('https://ixdzs8.com/read/137695/p5.html')).toEqual({ bookId: 'ixdzs-137695', chapterId: '5' });
    expect(parseBookUrl('https://ixdzs.tw/read/137695/')).toEqual({ bookId: 'ixdzs-137695' });
    expect(parseBookUrl('https://ixdzs8.com/bsearch?q=a')).toBe(null);
  });
});

describe('爱下电子书：书页与搜索（真实页面）', () => {
  it('书页：og meta 书籍信息 + #a-list 完整目录（正序、原位）', () => {
    const { book, entries } = parseIxdzsBookPage(BOOK, fixture('book-593824.html'));
    expect(book).toMatchObject({
      id: BOOK,
      title: '万古神帝之张劫外传',
      author: '小姚爱运动',
      category: '玄幻奇幻',
      status: '已完结',
      latestChapter: '第139章 无法分开',
      updatedAt: '2025-03-18 18:21:00',
      size: '70.37万字',
      firstChapterId: '1',
    });
    expect(book!.intro).toMatch(/^在万古神帝里面，劫老是一个非常可爱的存在/);
    expect(entries).toHaveLength(139);
    expect(entries.map((e) => e.id)).toEqual(Array.from({ length: 139 }, (_, i) => String(i + 1)));
    expect(entries[0]).toMatchObject({ title: '第1章 落魄后裔', number: 1, extra: false });
    // 顺序以目录为准（与 69书吧相同，只去重）
    expect(mergeBookToc(BOOK, [entries]).map((e) => e.id)).toEqual(entries.map((e) => e.id));
  });

  it('搜索：u-list 结果（书名/作者/状态·字数/简介/最新章）', () => {
    const results = parseIxdzsSearch(fixture('search-wangu.html'))!;
    expect(results.length).toBeGreaterThanOrEqual(5);
    expect(results.every((r) => isIxdzsBookId(r.id))).toBe(true);
    const wangu = results.find((r) => r.id === 'ixdzs-137695');
    expect(wangu).toMatchObject({ title: '万古神帝', author: '飞天鱼' });
    expect(wangu!.category).toContain('已完结');
    const jie = results.find((r) => r.id === 'ixdzs-593824')!;
    expect(jie).toMatchObject({ title: '万古神帝之张劫外传', author: '小姚爱运动' });
    expect(jie.intro).toContain('劫老');
    expect(parseIxdzsSearch('<html><body>维护中</body></html>')).toBe(null);
  });

  it('章节页的 JS 安全验证页识别为人机验证（不求解）', () => {
    expect(isChallengeHtml(fixture('verify-page.html'))).toBe(true);
    expect(isChallengeHtml(fixture('book-593824.html'))).toBe(false);
    expect(isChallengeHtml(fixture('search-wangu.html'))).toBe(false);
  });
});

describe('ZIP 与文本解码', () => {
  it('deflate 与 stored 两种条目；文件名 UTF-8；GB18030 正文自动识别', async () => {
    for (const method of [8, 0] as const) {
      const zip = makeZip('万古神帝之张劫外传.txt', sampleBytes(), method);
      expect(listZipEntries(zip).map((e) => e.name)).toEqual(['万古神帝之张劫外传.txt']);
      const { name, data } = await extractFirstTxt(zip);
      expect(name).toBe('万古神帝之张劫外传.txt');
      const text = decodeTextBytes(data);
      expect(text.startsWith('『万古神帝之张劫外传/作者:小姚爱运动』')).toBe(true);
      expect(text).toContain('清平镇的早晨');
    }
  });

  it('UTF-8（含 BOM）与 UTF-16 文本', () => {
    const utf8 = new TextEncoder().encode('第1章 开端');
    expect(decodeTextBytes(utf8)).toBe('第1章 开端');
    expect(decodeTextBytes(new Uint8Array([0xef, 0xbb, 0xbf, ...utf8]))).toBe('第1章 开端');
    const u16 = new Uint8Array([0xff, 0xfe, 0x2c, 0x7b]); // “第” UTF-16LE
    expect(decodeTextBytes(u16)).toBe('第');
  });

  it('损坏/伪造的压缩包报 ZipError（不会崩溃或越界）', async () => {
    await expect(extractFirstTxt(new TextEncoder().encode('not a zip'))).rejects.toBeInstanceOf(ZipError);
    await expect(extractFirstTxt(makeZip('a.txt', sampleBytes(), 8, { badSize: true }))).rejects.toBeInstanceOf(ZipError);
    await expect(extractFirstTxt(makeZip('a.jpg', sampleBytes(), 8))).rejects.toThrow('没有 TXT');
    const truncated = makeZip('a.txt', sampleBytes(), 8).slice(0, 60);
    await expect(extractFirstTxt(truncated)).rejects.toBeInstanceOf(ZipError);
  });
});

describe('整本 TXT 按目录切章', () => {
  const catalog = (): TocEntry[] => parseIxdzsBookPage(BOOK, fixture('book-593824.html')).entries.slice(0, 6);
  const text = () => decodeTextBytes(sampleBytes());

  it('顶格标题与目录对齐：章节号/标题用站点目录，缺章计入 missing，广告与尾注剔除', () => {
    const r = splitIxdzsTxt(text(), catalog());
    expect(r.aligned).toBe(true);
    expect(r.chapters.map((c) => [c.id, c.title])).toEqual([
      ['1', '第1章 落魄后裔'],
      ['2', '第2章 深渊奇遇'],
      ['3', '第3章 击杀仇人'],
      ['5', '第5章 美人计'],
    ]);
    expect(r.missing.map((e) => e.id)).toEqual(['4', '6']);
    expect(r.chapters[0]!.paragraphs).toEqual(['清平镇的早晨，雾还没有散。', '“劫哥，又去山里？”']);
    // 正文里提到“第三章”不会被当成标题；原文重复句保留
    expect(r.chapters[1]!.paragraphs).toHaveLength(2);
    expect(r.chapters[2]!.paragraphs).toEqual(['他握紧了刀。', '他握紧了刀。']);
    // 对不上目录的顶格行当正文保留（宁多勿少）；广告行与 =已完结= 去掉
    expect(r.chapters[3]!.paragraphs).toEqual([
      '（第4章在 TXT 中缺失，应计入 missing。）',
      '这一行没有缩进，但不是目录里的标题，应作为正文保留。',
      '她笑了笑。',
    ]);
    const all = r.chapters.flatMap((c) => c.paragraphs).join('\n');
    expect(all).not.toMatch(/ixdzs|爱下电子书|已完结|『/);
  });

  it('没有目录时退回按顶格标题切分（章节号顺排）', () => {
    const r = splitIxdzsTxt(text(), []);
    expect(r.aligned).toBe(false);
    expect(r.chapters.map((c) => c.title)).toEqual([
      '第1章落魄后裔',
      '第2章深渊奇遇',
      '第3章击杀仇人',
      '第5章美人计',
      '这一行没有缩进，但不是目录里的标题，应作为正文保留。',
    ]);
    expect(r.chapters.map((c) => c.id)).toEqual(['1', '2', '3', '4', '5']);
  });
});

describe('API：爱下电子书', () => {
  const ctx = () => ({ cache: new MemoryApiCache(), now: () => 1_700_000_000_000, retries: 0 });
  const fetcherFor = (routes: Record<string, string>, calls: string[] = []) =>
    async (input: string): Promise<Response> => {
      calls.push(input);
      const u = new URL(input);
      const body = routes[u.pathname];
      if (body === undefined) return new Response('not found', { status: 404, headers: { 'content-type': 'text/html' } });
      return new Response(body, { status: 200, headers: { 'content-type': 'text/html; charset=UTF-8' } });
    };

  it('目录：书页即全部目录 + 书籍信息', async () => {
    const calls: string[] = [];
    const fetcher = fetcherFor({ '/read/593824/': fixture('book-593824.html') }, calls);
    const res = await handleApi(new URL(`https://x/api/toc?book=${BOOK}&from=1&to=2`), { ...ctx(), fetcher });
    expect(res.status).toBe(200);
    const body = (await res.json()) as TocRangeResult;
    expect(body.totalPages).toBe(1);
    expect(body.pages[0]!.entries).toHaveLength(139);
    expect(body.book).toMatchObject({ title: '万古神帝之张劫外传', size: '70.37万字' });
    expect(calls).toEqual(['https://ixdzs8.com/read/593824/']);
  });

  it('搜索：source=ixdzs 走 GET /bsearch', async () => {
    const calls: string[] = [];
    const fetcher = fetcherFor({ '/bsearch': fixture('search-wangu.html') }, calls);
    const res = await handleApi(new URL(`https://x/api/search?q=${encodeURIComponent('万古神帝')}&source=ixdzs`), { ...ctx(), fetcher });
    expect(res.status).toBe(200);
    const body = (await res.json()) as SearchResponse;
    expect(body.source).toBe('ixdzs');
    expect(body.results.some((r) => r.id === 'ixdzs-137695')).toBe(true);
    expect(calls).toEqual(['https://ixdzs8.com/bsearch?q=%E4%B8%87%E5%8F%A4%E7%A5%9E%E5%B8%9D']);
  });

  it('章节接口直接拒绝（需整本下载），不向源站发请求', async () => {
    const calls: string[] = [];
    const fetcher = fetcherFor({}, calls);
    const res = await handleApi(new URL(`https://x/api/chapter?book=${BOOK}&id=1`), { ...ctx(), fetcher });
    expect(res.status).toBe(400);
    const body = (await res.json()) as { error: { message: string; retryable: boolean } };
    expect(body.error.message).toContain('整本下载');
    expect(body.error.retryable).toBe(false);
    expect(calls).toEqual([]);
  });

  it('书页被安全验证拦截时报 blocked', async () => {
    const fetcher = fetcherFor({ '/read/593824/': fixture('verify-page.html') });
    const res = await handleApi(new URL(`https://x/api/toc?book=${BOOK}&from=1&to=1`), { ...ctx(), fetcher });
    expect(res.status).toBe(502);
    expect(((await res.json()) as { error: { code: string } }).error.code).toBe('blocked');
  });
});
