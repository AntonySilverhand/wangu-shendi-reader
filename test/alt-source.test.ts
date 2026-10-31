import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  altOnlineSources,
  cachedAltChapter,
  chapterName,
  fetchAltChapterAuto,
  matchChapter,
  resetAltSourceCache,
  type AltRequest,
} from '../src/web/store/alt-source.ts';
import { normalizeTitle, type TocEntry } from '../src/shared/source.ts';

const toc = (titles: string[], idBase = 1000): TocEntry[] =>
  titles.map((title, i) => {
    const info = normalizeTitle(title);
    return { id: String(idBase + i), title, displayTitle: info.displayTitle, number: info.number, extra: false };
  });

describe('换源：章名匹配（各站章节号对不上）', () => {
  it('章名去掉“第N章”前缀（阿拉伯/中文数字、番外、无空格写法）', () => {
    expect(chapterName('第3579章 回城')).toBe('回城');
    expect(chapterName('第3579章回城')).toBe('回城');
    expect(chapterName('第三千五百五十八章 回城')).toBe('回城');
    expect(chapterName('番外第十八章 诸神集结')).toBe('诸神集结');
    expect(chapterName('第12章')).toBe('');
  });

  it('同一章在别站号码不同（实测相差 21 / 69 号、中文数字）也能按章名找到', () => {
    const other = toc(['第三千五百五十七章 头七', '第三千五百五十八章 回城', '第三千六百六十四章 谋未来']);
    expect(matchChapter(other, '第3579章 回城', 0.5)?.id).toBe('1001');
    expect(matchChapter(other, '第3595章谋未来', 0.9)?.id).toBe('1002');
  });

  it('同名章节按全书相对位置取最近的一个；离得太远不认', () => {
    const titles = Array.from({ length: 100 }, (_, i) => `第${i + 1}章 第${i + 1}节`);
    titles[10] = '第11章 交锋';
    titles[80] = '第81章 交锋';
    const entries = toc(titles);
    expect(matchChapter(entries, '第11章 交锋', 0.1)?.id).toBe('1010');
    expect(matchChapter(entries, '第83章 交锋', 0.8)?.id).toBe('1080');
    expect(matchChapter(entries, '第50章 交锋', 0.5)).toBe(null);
    // 不知道位置时，有歧义就放弃（宁可换下一个来源，也不显示错的章节）
    expect(matchChapter(entries, '第11章 交锋', null)).toBe(null);
    expect(matchChapter(toc(['第1章 开端', '第2章 交锋']), '第9章 交锋', null)?.id).toBe('1001');
  });

  it('没有章名时按章节号；章名不同不会误配', () => {
    const entries = toc(['第1章', '第2章', '第3章 新的开始']);
    expect(matchChapter(entries, '第2章', 0.5)?.id).toBe('1001');
    expect(matchChapter(entries, '第3章', 1)).toBe(null);
    expect(matchChapter(entries, '第2章 完全不同', 0.5)).toBe(null);
  });
});

describe('换源：来源顺序与流程', () => {
  const calls: string[] = [];
  const routes = new Map<string, unknown>();
  beforeEach(() => {
    resetAltSourceCache();
    calls.length = 0;
    routes.clear();
    vi.stubGlobal('fetch', async (path: string) => {
      calls.push(path);
      const key = [...routes.keys()].find((k) => path.startsWith(k));
      if (!key) return new Response(JSON.stringify({ error: { code: 'http', message: 'down', retryable: false } }), { status: 502 });
      return new Response(JSON.stringify(routes.get(key)), { status: 200, headers: { 'content-type': 'application/json' } });
    });
  });
  afterEach(() => vi.unstubAllGlobals());

  const req = (over: Partial<AltRequest> = {}): AltRequest => ({
    bookId: '36780',
    bookTitle: '万古神帝',
    author: '飞天鱼',
    chapterId: '9',
    chapterTitle: '第3579章 回城',
    position: 0.84,
    ...over,
  });
  const search = (source: string, results: object[]) => ({ query: '万古神帝', source, results });
  const singleToc = (titles: string[], base: number) => ({ from: 1, to: 2, totalPages: 1, pages: [{ page: 1, entries: toc(titles, base) }], failedPages: [] });
  const chapter = (title: string, paragraphs: string[]) => ({ id: 'x', title, paragraphs, prevId: null, nextId: null, pageCount: 1, charCount: 10, complete: true, missingPages: [] });

  it('万书阁的书先试飘天文学，再 69书吧；本身的书源不参与', () => {
    expect(altOnlineSources('36780')).toEqual(['piaotia', '69shuba']);
    expect(altOnlineSources('piaotia-7906')).toEqual(['wanshuge', '69shuba']);
    expect(altOnlineSources('ixdzs-137695')).toEqual(['piaotia', 'wanshuge', '69shuba']);
  });

  it('搜索 → 同名同作者的书 → 按章名找章 → 取正文；结果缓存，同章不再请求', async () => {
    routes.set('/api/search?q=%E4%B8%87%E5%8F%A4%E7%A5%9E%E5%B8%9D&source=piaotia', search('piaotia', [
      { id: 'piaotia-1', title: '万古神帝', author: '别人' },
      { id: 'piaotia-7906', title: '万古神帝', author: '飞天鱼' },
    ]));
    routes.set('/api/toc?book=piaotia-7906', singleToc(['第一章 八百年后', ...Array.from({ length: 8 }, (_, i) => `第${i + 2}章 过场${i}`), '第3579章 回城'], 500));
    routes.set('/api/chapter?book=piaotia-7906&id=509', chapter('第3579章 回城', ['五清宗的担忧，是有道理的。']));
    const r = await fetchAltChapterAuto(req({ position: 0.95 }));
    expect(r).toMatchObject({ sourceName: '飘天文学', altBookId: 'piaotia-7906', paragraphs: ['五清宗的担忧，是有道理的。'] });
    expect(cachedAltChapter('36780', '9')).toBe(r);
    const n = calls.length;
    await fetchAltChapterAuto(req({ position: 0.95 }));
    expect(calls.length).toBe(n);
  });

  it('连续几章同时换源：同一书源只搜一次', async () => {
    routes.set('/api/search?q=%E4%B8%87%E5%8F%A4%E7%A5%9E%E5%B8%9D&source=piaotia', search('piaotia', [{ id: 'piaotia-7906', title: '万古神帝', author: '飞天鱼' }]));
    routes.set('/api/toc?book=piaotia-7906', singleToc(['第3582章 我来替你守护万年', '第3583章 战讯'], 300));
    routes.set('/api/chapter?book=piaotia-7906&id=300', chapter('第3582章 我来替你守护万年', ['甲']));
    routes.set('/api/chapter?book=piaotia-7906&id=301', chapter('第3583章 战讯', ['乙']));
    const [a, b] = await Promise.all([
      fetchAltChapterAuto(req({ chapterId: '1', chapterTitle: '第3582章 我来替你守护万年', position: 0 })),
      fetchAltChapterAuto(req({ chapterId: '2', chapterTitle: '第3583章 战讯', position: 1 })),
    ]);
    expect([a?.paragraphs, b?.paragraphs]).toEqual([['甲'], ['乙']]);
    expect(calls.filter((c) => c.startsWith('/api/search')).length).toBe(1);
    expect(calls.filter((c) => c.startsWith('/api/toc')).length).toBe(1);
  });

  it('别站正文首行重复章节标题（原书号码）时去掉；正文里普通的“第N章”句子保留', async () => {
    routes.set('/api/search?q=%E4%B8%87%E5%8F%A4%E7%A5%9E%E5%B8%9D&source=piaotia', search('piaotia', [{ id: 'piaotia-7906', title: '万古神帝', author: '飞天鱼' }]));
    routes.set('/api/toc?book=piaotia-7906', singleToc(['第三千六百七十一章 不周山下', '第三千六百七十二章 别的'], 400));
    routes.set('/api/chapter?book=piaotia-7906&id=400', chapter('第三千六百七十一章 不周山下', ['第3602章 不周山下', '宇宙浩大。']));
    routes.set('/api/chapter?book=piaotia-7906&id=401', chapter('第三千六百七十二章 别的', ['第一章里埋下的伏笔，终于揭开。', '正文。']));
    expect((await fetchAltChapterAuto(req({ chapterId: '1', chapterTitle: '第3602章 不周山下', position: 0 })))?.paragraphs).toEqual(['宇宙浩大。']);
    expect((await fetchAltChapterAuto(req({ chapterId: '2', chapterTitle: '第3603章 别的', position: 1 })))?.paragraphs).toEqual(['第一章里埋下的伏笔，终于揭开。', '正文。']);
  });

  it('同名不同作者的书不用；第一个书源失败/也是占位时继续下一个', async () => {
    routes.set('/api/search?q=%E4%B8%87%E5%8F%A4%E7%A5%9E%E5%B8%9D&source=piaotia', search('piaotia', [{ id: 'piaotia-1', title: '万古神帝', author: '别人' }]));
    routes.set('/api/search?q=%E4%B8%87%E5%8F%A4%E7%A5%9E%E5%B8%9D&source=69shuba', search('69shuba', [{ id: '69shuba-5', title: '万古神帝' }]));
    routes.set('/api/toc?book=69shuba-5', singleToc(['第一章 八百年后', '第三千五百五十八章 回城'], 700));
    routes.set('/api/chapter?book=69shuba-5&id=701', chapter('第三千五百五十八章 回城', ['本章节内容正在手打中，请稍后刷新']));
    expect(await fetchAltChapterAuto(req({ position: 1 }))).toBe(null);
    expect(calls.some((c) => c.startsWith('/api/toc?book=piaotia-1'))).toBe(false);
    // 所有来源都明确“没有”：记为无结果，不再重复请求
    expect(cachedAltChapter('36780', '9')).toBe(null);
  });

  it('有来源出错时不记为无结果（下次还会再试）；同书的搜索结果复用', async () => {
    routes.set('/api/search?q=%E4%B8%87%E5%8F%A4%E7%A5%9E%E5%B8%9D&source=piaotia', search('piaotia', [{ id: 'piaotia-7906', title: '万古神帝', author: '飞天鱼' }]));
    routes.set('/api/toc?book=piaotia-7906', singleToc(['第3578章 头七', '第3579章 回城'], 900));
    routes.set('/api/chapter?book=piaotia-7906&id=900', chapter('第3578章 头七', ['头七的正文。']));
    expect(await fetchAltChapterAuto(req({ chapterId: '9', chapterTitle: '第3579章 回城', position: 1 }))).toBe(null);
    expect(cachedAltChapter('36780', '9')).toBe(undefined);
    const searches = calls.filter((c) => c.startsWith('/api/search')).length;
    expect(await fetchAltChapterAuto(req({ chapterId: '8', chapterTitle: '第3578章 头七', position: 0 }))).toMatchObject({ paragraphs: ['头七的正文。'] });
    expect(calls.filter((c) => c.startsWith('/api/search?q=%E4%B8%87%E5%8F%A4%E7%A5%9E%E5%B8%9D&source=piaotia')).length).toBe(1);
    expect(calls.filter((c) => c.startsWith('/api/search')).length).toBe(searches);
  });

  it('万书阁目录分页：先取前 2 页拿总页数，再按相对位置只取附近几页', async () => {
    const pageOf = (p: number) => ({ page: p, entries: toc(Array.from({ length: 100 }, (_, i) => `第${(p - 1) * 100 + i + 1}章 章${(p - 1) * 100 + i + 1}`), p * 1000) });
    const big = (from: number, to: number) => ({ from, to, totalPages: 44, pages: Array.from({ length: to - from + 1 }, (_, k) => pageOf(from + k)), failedPages: [] });
    routes.set('/api/search?q=%E4%B8%87%E5%8F%A4%E7%A5%9E%E5%B8%9D&source=wanshuge', search('wanshuge', [{ id: '36780', title: '万古神帝', author: '飞天鱼' }]));
    routes.set('/api/toc?book=36780&from=1&to=2', big(1, 2));
    routes.set('/api/toc?book=36780&from=35&to=39', big(35, 39));
    routes.set('/api/chapter?book=36780&id=37010', chapter('第3611章 章3611', ['正文']));
    const r = await fetchAltChapterAuto(req({ bookId: 'piaotia-7906', chapterTitle: '第3640章 章3611', position: 3610 / 4399 }));
    expect(r?.altBookId).toBe('36780');
    expect(calls.filter((c) => c.startsWith('/api/toc')).sort()).toEqual(['/api/toc?book=36780&from=1&to=2', '/api/toc?book=36780&from=35&to=39']);
  });
});
