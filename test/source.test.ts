import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import {
  classifyTocItems,
  mergeTocEntries,
  mergeTocPages,
  normalizeTitle,
  parseChineseNumber,
  parseChapterLink,
  parseChapterPage,
  parseTocPageHtml,
  parseTocTotalPages,
  tocPath,
  chapterPath,
  buildChapterUrl,
  fetchChapter,
  extractAnchors,
  extractDeclaredPageIndex,
  type TocItemRaw,
} from '../src/shared/source.ts';

const B = '36780';

const here = dirname(fileURLToPath(import.meta.url));
const fixtures = (name: string) => join(here, 'fixtures', name);
const html = (name: string) => readFileSync(fixtures(name), 'utf8');

/** 真实 fixture 驱动抓取：routes 按去掉 host 的 pathname 匹配 */
function fixtureFetcher(routes: Record<string, string>, fail?: string[]) {
  return async (input: string): Promise<Response> => {
    const url = new URL(input);
    const path = url.pathname;
    if (fail?.includes(path)) throw new Error('network down');
    const body = routes[path];
    return new Response(body ?? '', {
      status: body !== undefined ? 200 : 404,
      headers: { 'content-type': 'text/html; charset=utf-8' },
    });
  };
}

const noRetry = { retries: 0, sleep: () => Promise.resolve() } as const;

describe('URL 构建受限', () => {
  it('目录页 URL', () => {
    expect(tocPath(B, 1)).toBe('/book/36780/');
    expect(tocPath(B, 2)).toBe('/book/36780/1.html');
    expect(tocPath(B, 44)).toBe('/book/36780/43.html');
    expect(() => tocPath(B, 0)).toThrow();
  });
  it('章节页 URL', () => {
    expect(chapterPath(B, '38621328')).toBe('/book/36780_38621328.html');
    expect(chapterPath(B, '38621328', 1)).toBe('/book/36780/38621328_1.html');
    expect(buildChapterUrl(B, '38621328', 2)).toBe(
      'https://wanshuge.org/book/36780/38621328_2.html',
    );
    expect(() => chapterPath(B, '../../etc/passwd')).toThrow();
    expect(() => chapterPath(B, '12', 40)).toThrow();
  });
  it('章节链接解析', () => {
    expect(parseChapterLink(B, '/book/36780_38621328.html')).toEqual({
      id: '38621328',
      pageIndex: 0,
    });
    expect(parseChapterLink(B, '/book/36780/38621328_1.html')).toEqual({
      id: '38621328',
      pageIndex: 1,
    });
    expect(parseChapterLink(B, 'https://evil.com/x')).toBeNull();
  });
  it('链接解析：绝对 URL / www / http / 协议相对 / query / hash', () => {
    expect(parseChapterLink(B, 'https://wanshuge.org/book/36780_38621328.html')).toEqual({
      id: '38621328',
      pageIndex: 0,
    });
    expect(parseChapterLink(B, 'https://www.wanshuge.org/book/36780_38621328.html?x=1#top')).toEqual({
      id: '38621328',
      pageIndex: 0,
    });
    expect(parseChapterLink(B, 'http://wanshuge.org/book/36780/38621328_1.html?from=pc')).toEqual({
      id: '38621328',
      pageIndex: 1,
    });
    expect(parseChapterLink(B, '//wanshuge.org/book/36780/38621328_2.html')).toEqual({
      id: '38621328',
      pageIndex: 2,
    });
    // 其它域名/非本书路径一律拒绝
    expect(parseChapterLink(B, 'https://wanshuge.org/book/99999_38621328.html')).toBeNull();
    expect(parseChapterLink(B, 'https://example.com/book/36780_38621328.html')).toBeNull();
    expect(parseChapterLink(B, 'javascript:void(0)')).toBeNull();
    expect(parseChapterLink(B, '')).toBeNull();
    // 目录分页页（/book/36780/1.html）不能被误认成章节
    expect(parseChapterLink(B, '/book/36780/1.html')).toBeNull();
    expect(parseChapterLink(B, '/book/36780/43.html')).toBeNull();
  });
  it('链接发现：单双引号、属性乱序、未加引号 href、script 干扰都能处理', () => {
    const raw = [
      `<a href="/book/36780_38621328.html">上一章</a>`,
      `<a class='x' href='/book/36780/38621328_1.html'>下一章</a>`,
      `<a  rel="nofollow"   href=/book/36780/38621328_2.html >下一页</a>`,
      `<a href="https://www.wanshuge.org/book/36780_38621328.html">绝对</a>`,
      `<script>var s="<a href='/book/36780/8924760.html'>干扰</a>";</script>`,
    ].join('');
    const hrefs = extractAnchors(raw).map((l) => l.href);
    expect(hrefs).toContain('/book/36780_38621328.html');
    expect(hrefs).toContain('/book/36780/38621328_1.html');
    expect(hrefs).toContain('/book/36780/38621328_2.html');
    expect(hrefs).toContain('https://www.wanshuge.org/book/36780_38621328.html');
    // 页面级解析会先剥离 script，JS 字符串里的“链接”不应被当成导航
    const page = parseChapterPage(B, raw, '38621328', 0);
    expect(page.sameChapterPages).toEqual([1, 2]);
    expect(page.nextChapterId).toBeNull();
  });
  it('页面自报分页序号（lastread/标题）', () => {
    expect(extractDeclaredPageIndex(html('chapter-38621571-p1.html'))).toBe(0);
    expect(extractDeclaredPageIndex(html('chapter-38621571-p2.html'))).toBe(1);
    expect(extractDeclaredPageIndex(html('chapter-38621571-p3.html'))).toBe(2);
    // 回环页：URL 是 _3，但页面自报第 1 页（lastread 序号 0）
    expect(extractDeclaredPageIndex(html('chapter-38621571-loopback.html'))).toBe(0);
  });
});

describe('章节正文解析（真实 fixture）', () => {
  it('解析第 2771 章第 1 页', () => {
    const page = parseChapterPage(B, html('chapter-38621328-p1.html'), '38621328', 0);
    expect(page.title).toBe('第二千七百七十一章 无疆到来');
    expect(page.paragraphs.length).toBeGreaterThan(20);
    expect(page.paragraphs[0]).toContain('佛祖舍利');
    expect(page.nextPageIndex).toBe(1);
    expect(page.nextChapterId).toBeNull();
    expect(page.prevChapterId).toBe('38621326');
    for (const p of page.paragraphs) {
      expect(p).not.toMatch(/<\/?p>/);
      expect(p.trim()).toBe(p);
    }
  });
  it('第 2 页指向第 3 页，且不含上一章', () => {
    const page = parseChapterPage(B, html('chapter-38621328-p2.html'), '38621328', 1);
    expect(page.title).toBe('第二千七百七十一章 无疆到来');
    expect(page.nextPageIndex).toBe(2);
    expect(page.prevChapterId).toBeNull();
    expect(page.paragraphs.length).toBeGreaterThan(20);
  });
  it('最后一页指向下一章', () => {
    const page = parseChapterPage(B, html('chapter-38621328-p3.html'), '38621328', 2);
    expect(page.nextPageIndex).toBeNull();
    expect(page.nextChapterId).toBe('38621330');
    expect(page.prevChapterId).toBeNull();
  });
  it('用户原始链接对应的第 2772 章第 2 页', () => {
    const page = parseChapterPage(B, html('chapter-38621330-p2.html'), '38621330', 1);
    expect(page.title).toContain('第二千七百七十二章');
    expect(page.nextPageIndex).toBe(2);
    expect(page.prevChapterId).toBeNull();
  });
  it('“分节阅读”占位标题章节也能解析', () => {
    const page = parseChapterPage(B, html('chapter-13375259-p1.html'), '13375259', 0);
    expect(page.title).toBe('分节阅读第101节');
    expect(page.paragraphs.length).toBeGreaterThan(10);
    expect(page.nextPageIndex).toBe(1);
  });
  it('最早的第 1 章可以解析', () => {
    const page = parseChapterPage(B, html('chapter-8924760-p1.html'), '8924760', 0);
    expect(page.paragraphs.length).toBeGreaterThan(5);
  });
});

describe('第 2871 章真实 fixture（38621571：3 个物理页 + 回环页）', () => {
  it('第 1 物理页：同章下一页链接在“下一章”按钮上（文字不可信，URL 才是语义）', () => {
    const page = parseChapterPage(B, html('chapter-38621571-p1.html'), '38621571', 0);
    expect(page.title).toBe('第2871章 原来如此');
    expect(page.paragraphs.length).toBeGreaterThan(20);
    expect(page.paragraphs[0]).toContain('张若尘');
    expect(page.sameChapterPages).toEqual([1]);
    expect(page.nextPageIndex).toBe(1);
    expect(page.prevChapterId).toBe('38621566');
    expect(page.nextChapterId).toBeNull(); // “下一章”按钮指向同章 _1，不是下一章
    expect(page.declaredPageIndex).toBe(0);
  });
  it('第 2 物理页：上一/下一都指向同章分页', () => {
    const page = parseChapterPage(B, html('chapter-38621571-p2.html'), '38621571', 1);
    expect(page.sameChapterPages).toEqual([0, 2]);
    expect(page.nextPageIndex).toBe(2);
    expect(page.prevChapterId).toBeNull();
    expect(page.nextChapterId).toBeNull();
    expect(page.declaredPageIndex).toBe(1);
    expect(page.paragraphs.length).toBeGreaterThan(20);
  });
  it('第 3 物理页（末页）：真正下一章 38621574 在这里', () => {
    const page = parseChapterPage(B, html('chapter-38621571-p3.html'), '38621571', 2);
    expect(page.sameChapterPages).toEqual([1]);
    expect(page.nextPageIndex).toBeNull();
    expect(page.prevChapterId).toBeNull();
    expect(page.nextChapterId).toBe('38621574');
    expect(page.declaredPageIndex).toBe(2);
    expect(page.paragraphs.at(-1)).toBe('难怪荒天会亲自出手，夺走天尊宝纱。');
  });
  it('回环页：URL 是 _3，但内容/自报页码都是第 1 页', () => {
    const loop = parseChapterPage(B, html('chapter-38621571-loopback.html'), '38621571', 3);
    const first = parseChapterPage(B, html('chapter-38621571-p1.html'), '38621571', 0);
    expect(loop.declaredPageIndex).toBe(0); // 自报第 1 页，与请求的 3 不符
    expect(loop.paragraphs).toEqual(first.paragraphs);
  });
});

describe('第 4208 章真实 fixture（38626103：5 个物理页）', () => {
  const routes4208: Record<string, string> = {};
  for (let n = 0; n < 5; n++) {
    routes4208[`/book/36780${n === 0 ? '_38626103' : `/38626103_${n}`}.html`] = html(
      `chapter-38626103-p${n + 1}.html`,
    );
  }

  it('第 1 页解析：同章下一页在“下一章”按钮上，prev 是真上一章', () => {
    const page = parseChapterPage(B, html('chapter-38626103-p1.html'), '38626103', 0);
    expect(page.title).toBe('第四千二百零八章 至高组会议');
    expect(page.sameChapterPages).toEqual([1]);
    expect(page.prevChapterId).toBe('38626097');
    expect(page.nextChapterId).toBeNull();
    expect(page.declaredPageIndex).toBe(0);
    expect(page.paragraphs.length).toBeGreaterThan(20);
  });
  it('第 5 页（末页）：真正下一章 38626110；上一章按钮虽指向 _3 也不影响语义', () => {
    const page = parseChapterPage(B, html('chapter-38626103-p5.html'), '38626103', 4);
    expect(page.sameChapterPages).toEqual([3]); // 源站末页“上一章”指向 _3，URL 身份仍正确
    expect(page.nextPageIndex).toBeNull();
    expect(page.nextChapterId).toBe('38626110');
    expect(page.declaredPageIndex).toBe(4);
    expect(page.paragraphs.at(-1)).toContain('答案，其实早就有了');
  });
  it('fetchChapter：5 页全部抓到、末页正文存在、complete 来自末页下一章', async () => {
    const requested: string[] = [];
    const fetcher = async (input: string): Promise<Response> => {
      const path = new URL(input).pathname;
      requested.push(path);
      const body = routes4208[path];
      return new Response(body ?? '', {
        status: body !== undefined ? 200 : 404,
        headers: { 'content-type': 'text/html; charset=utf-8' },
      });
    };
    const r = await fetchChapter(B, '38626103', { fetcher, ...noRetry });
    expect(r.pageCount).toBe(5);
    expect(r.complete).toBe(true);
    expect(r.missingPages).toEqual([]);
    expect(r.prevId).toBe('38626097');
    expect(r.nextId).toBe('38626110');
    expect(r.paragraphs.length).toBeGreaterThan(180);
    expect(r.paragraphs).toContain('“答案，其实早就有了！但，我必须亲自去见她，才能解开所有谜题。”张若尘目光变得幽邃。');
    // 需要抓的只有 0..4；末页已有真正下一章，不应探 _5
    expect(requested).toEqual([
      '/book/36780_38626103.html',
      '/book/36780/38626103_1.html',
      '/book/36780/38626103_2.html',
      '/book/36780/38626103_3.html',
      '/book/36780/38626103_4.html',
    ]);
  });
  it('第 4 页失败：保留 0-3 与第 5 页正文、complete=false、missingPages=[4]', async () => {
    const failing = async (input: string): Promise<Response> => {
      const path = new URL(input).pathname;
      if (path === '/book/36780/38626103_4.html') throw new Error('network down');
      const body = routes4208[path];
      return new Response(body ?? '', {
        status: body !== undefined ? 200 : 404,
        headers: { 'content-type': 'text/html; charset=utf-8' },
      });
    };
    const r = await fetchChapter(B, '38626103', { fetcher: failing, ...noRetry });
    expect(r.pageCount).toBe(4); // 0..3 成功；_4 失败，探针 _5 得到 404（不存在的积极证据）
    expect(r.complete).toBe(false);
    expect(r.missingPages).toEqual([4]);
    expect(r.paragraphs).toContain('“那在于什么？”虚天问道。');
  });
});

describe('fetchChapter 集成（真实 fixture + 注入 fetch）', () => {
  const routes2871: Record<string, string> = {
    '/book/36780_38621571.html': html('chapter-38621571-p1.html'),
    '/book/36780/38621571_1.html': html('chapter-38621571-p2.html'),
    '/book/36780/38621571_2.html': html('chapter-38621571-p3.html'),
    '/book/36780/38621571_3.html': html('chapter-38621571-loopback.html'),
  };

  it('2871：三个物理页全部抓到，末页正文存在，complete 来自终章证据', async () => {
    const requested: string[] = [];
    const fetcher = async (input: string): Promise<Response> => {
      const path = new URL(input).pathname;
      requested.push(path);
      const body = routes2871[path];
      return new Response(body ?? '', {
        status: body !== undefined ? 200 : 404,
        headers: { 'content-type': 'text/html; charset=utf-8' },
      });
    };
    const r = await fetchChapter(B, '38621571', { fetcher, ...noRetry });
    expect(r.pageCount).toBe(3);
    expect(r.complete).toBe(true);
    expect(r.missingPages).toEqual([]);
    expect(r.prevId).toBe('38621566');
    expect(r.nextId).toBe('38621574');
    expect(r.paragraphs.length).toBeGreaterThan(80);
    // 第三物理页的真实正文必须出现在最终结果里（bug 的核心断言）
    expect(r.paragraphs).toContain('难怪荒天会亲自出手，夺走天尊宝纱。');
    expect(r.paragraphs).toContain('“放肆！”');
    // 需要抓的只有 0/1/2；末页已有真正下一章，不应再探 _3
    expect(requested).toEqual([
      '/book/36780_38621571.html',
      '/book/36780/38621571_1.html',
      '/book/36780/38621571_2.html',
    ]);
  });

  it('合并后清洗：分页提示与网址水印不进正文（线上书源同样处理）', async () => {
    const encoded = (text: string) =>
      `<script>document.writeln(qsbs.bb('${Buffer.from(`<p>${text}</p>`).toString('base64')}'));</script>`;
    const dirty = html('chapter-38621571-p2.html').replace(
      '<p>请勿开启浏览器阅读模式',
      `${encoded('本小章还未完，请点击下一页继续阅读后面精彩内容！')}${encoded('最后一句。【△網www.AiQuxs.】')}<p>请勿开启浏览器阅读模式`,
    );
    const routes: Record<string, string> = { ...routes2871, '/book/36780/38621571_1.html': dirty };
    const fetcher = async (input: string): Promise<Response> => {
      const body = routes[new URL(input).pathname];
      return new Response(body ?? '', { status: body !== undefined ? 200 : 404 });
    };
    const r = await fetchChapter(B, '38621571', { fetcher, ...noRetry });
    expect(r.complete).toBe(true);
    expect(r.paragraphs).toContain('最后一句。');
    expect(r.paragraphs.join('\n')).not.toMatch(/下一页|www|AiQuxs/);
    expect(r.charCount).toBe(r.paragraphs.reduce((n, p) => n + p.length, 0));
  });

  it('_1 抓取失败：保留已取内容、complete=false、missingPages 记录已知缺口', async () => {
    const r = await fetchChapter(B, '38621571', {
      fetcher: fixtureFetcher(routes2871, ['/book/36780/38621571_1.html']),
      ...noRetry,
    });
    expect(r.complete).toBe(false);
    expect(r.missingPages).toEqual([1]);
    expect(r.pageCount).toBe(2);
    // 已成功取到的 0、2 页正文仍在，且末页正文没有丢
    expect(r.paragraphs).toContain('难怪荒天会亲自出手，夺走天尊宝纱。');
    expect(r.nextId).toBe('38621574');
  });

  it('_2 抓取失败：同样不标 complete，继续拿其它已发现分页', async () => {
    const r = await fetchChapter(B, '38621571', {
      fetcher: fixtureFetcher(routes2871, ['/book/36780/38621571_2.html']),
      ...noRetry,
    });
    expect(r.complete).toBe(false);
    expect(r.missingPages).toEqual([2]);
    expect(r.pageCount).toBe(2);
  });

  it('回环证据：只有第 0 页且 _1 回环到第 1 页内容 → 单页章节能确认完整', async () => {
    const r = await fetchChapter(B, '38621571', {
      fetcher: fixtureFetcher({
        '/book/36780_38621571.html': html('chapter-38621571-p1.html'),
        '/book/36780/38621571_1.html': html('chapter-38621571-loopback.html'),
      }),
      ...noRetry,
    });
    expect(r.pageCount).toBe(1);
    expect(r.complete).toBe(true);
    expect(r.missingPages).toEqual([]);
  });

  it('回环证据：_1 返回 404 → 同样视为“不存在更大分页”的积极证据', async () => {
    const r = await fetchChapter(B, '38621571', {
      fetcher: fixtureFetcher({ '/book/36780_38621571.html': html('chapter-38621571-p1.html') }),
      ...noRetry,
    });
    expect(r.pageCount).toBe(1);
    expect(r.complete).toBe(true);
    expect(r.missingPages).toEqual([]);
  });

  it('旧模板第一页：下一章直连下一章 id，但 _1/_2 实际存在 → 探针发现并补齐', async () => {
    // 源站 CDN 曾提供旧模板第一页（分页尚未拆分时），“下一章”按钮直连下一章 id；
    // 这曾经导致只抓第 1 页且 complete:true。探针机制必须识破它。
    const stale = html('chapter-38621571-p1.html').replace(
      '/book/36780/38621571_1.html',
      '/book/36780_38621574.html',
    );
    const r = await fetchChapter(B, '38621571', {
      fetcher: fixtureFetcher({ ...routes2871, '/book/36780_38621571.html': stale }),
      ...noRetry,
    });
    expect(r.pageCount).toBe(3);
    expect(r.complete).toBe(true);
    expect(r.missingPages).toEqual([]);
    expect(r.paragraphs).toContain('难怪荒天会亲自出手，夺走天尊宝纱。');
  });

  it('无法确认末页（无导航且探针失败）→ 不得标 complete', async () => {
    const bare = html('chapter-38621571-p1.html').replace(
      /<div class="read_btn">[\s\S]*?<\/div>/g,
      '<div class="read_btn"></div>',
    );
    const r = await fetchChapter(B, '38621571', {
      fetcher: fixtureFetcher(
        { '/book/36780_38621571.html': bare },
        ['/book/36780/38621571_1.html'],
      ),
      ...noRetry,
    });
    expect(r.paragraphs.length).toBeGreaterThan(20);
    expect(r.complete).toBe(false);
    expect(r.missingPages).toEqual([1]);
  });

  it('无法确认末页，但探针 404 → 可标 complete', async () => {
    const bare = html('chapter-38621571-p1.html').replace(
      /<div class="read_btn">[\s\S]*?<\/div>/g,
      '<div class="read_btn"></div>',
    );
    const r = await fetchChapter(B, '38621571', {
      fetcher: fixtureFetcher({ '/book/36780_38621571.html': bare }),
      ...noRetry,
    });
    expect(r.complete).toBe(true);
    expect(r.missingPages).toEqual([]);
  });
});

describe('目录页解析（真实 fixture）', () => {
  it('第 1 页：置顶块 + 正文 1-100 章', () => {
    const items = parseTocPageHtml(B, html('toc-page1.html'), 1);
    expect(items[0]!.title).toBe('新书元始法则发布了');
    expect(items.some((i) => i.title.startsWith('番外'))).toBe(true);
    expect(items.some((i) => i.title === '分节阅读第1节')).toBe(true);
    expect(items.some((i) => i.title === '分节阅读第100节')).toBe(true);
    // 详情页（第 1 页）没有页码选择器，总页数由后续页得知
    expect(parseTocTotalPages(B, html('toc-page1.html'))).toBeNull();
    expect(parseTocTotalPages(B, html('toc-page2.html'))).toBe(44);
    expect(parseTocTotalPages(B, html('toc-page2.html'))).toBe(44);
  });
  it('第 2 页分类：正文 101-200，置顶番外进 extras，尾部快捷链接被剔除', () => {
    const items = parseTocPageHtml(B, html('toc-page2.html'), 2);
    const { main, extras } = classifyTocItems(items, 2);
    expect(main[0]!.displayTitle).toBe('第101章');
    expect(main[main.length - 1]!.displayTitle).toBe('第200章');
    expect(main).toHaveLength(100);
    expect(extras.length).toBeGreaterThanOrEqual(7);
    expect(extras.every((e) => e.displayTitle.startsWith('番外'))).toBe(true);
    expect(main.some((m) => m.displayTitle === '第1章')).toBe(false);
  });
  it('两页合并后正文连续（第1章 … 第200章）', () => {
    const p1 = parseTocPageHtml(B, html('toc-page1.html'), 1);
    const p2 = parseTocPageHtml(B, html('toc-page2.html'), 2);
    const c1 = classifyTocItems(p1, 1);
    const c2 = classifyTocItems(p2, 2);
    const merged = mergeTocEntries([
      [...c1.main, ...c1.extras],
      [...c2.main, ...c2.extras],
    ]);
    const main = merged.filter((e) => !e.extra);
    expect(main[0]!.displayTitle).toBe('第1章');
    expect(main[99]!.displayTitle).toBe('第100章');
    expect(main[100]!.displayTitle).toBe('第101章');
    expect(main[199]!.displayTitle).toBe('第200章');
    const ids = main.map((e) => e.id);
    expect(new Set(ids).size).toBe(ids.length);
    // 番外在末尾
    const lastExtraIdx = merged.findIndex((e) => e.extra);
    expect(lastExtraIdx).toBe(main.length);
  });
});

describe('标题归一化', () => {
  it('中文数字', () => {
    expect(parseChineseNumber('一')).toBe(1);
    expect(parseChineseNumber('十')).toBe(10);
    expect(parseChineseNumber('十一')).toBe(11);
    expect(parseChineseNumber('二十')).toBe(20);
    expect(parseChineseNumber('一百零一')).toBe(101);
    expect(parseChineseNumber('两千七百七十一')).toBe(2771);
    expect(parseChineseNumber('4325'.replace('4325', '四千三百二十五'))).toBe(4325);
  });
  it('标题', () => {
    expect(normalizeTitle('分节阅读第2771节')).toEqual({
      displayTitle: '第2771章',
      number: 2771,
      extra: false,
    });
    expect(normalizeTitle('第二千七百七十一章 无疆到来')).toMatchObject({
      number: 2771,
      extra: false,
    });
    expect(normalizeTitle('番外第十八章 诸神集结')).toMatchObject({
      number: 18,
      extra: true,
    });
    expect(normalizeTitle('完本感言')).toMatchObject({ number: null, extra: false });
  });
  it('重复番外保留最新 id', () => {
    const items: TocItemRaw[] = [
      { id: '58210332', title: '番外第十二章 巅峰相别' },
      { id: '59040612', title: '番外第十二章 巅峰相别' },
      { id: '58210314', title: '第四千二百五十一章 大结局五' },
    ];
    const merged = mergeTocPages([items]);
    const extra = merged.find((e) => e.extra)!;
    expect(extra.id).toBe('59040612');
    expect(merged.filter((e) => e.extra)).toHaveLength(1);
  });
  it('源站编号回退时按上传顺序排列，不交错两段剧情；结尾重传丢弃', () => {
    const items: TocItemRaw[] = [
      { id: '38623378', title: '第三千三百八十九章 又惹祸了' },
      { id: '38623308', title: '第3389章 再破极限' },
      { id: '38623375', title: '第3409章 天魔霸枪' },
      { id: '38623380', title: '第三千三百九十章 大师兄' },
      { id: '58210378', title: '第三千三百九十章 大师兄' },
    ];
    const titles = mergeTocPages([items]).map((e) => e.title);
    expect(titles).toEqual([
      '第3389章 再破极限',
      '第3409章 天魔霸枪',
      '第三千三百八十九章 又惹祸了',
      '第三千三百九十章 大师兄',
    ]);
  });
  it('错号批次内的同号章节不丢、不互相替换（第3402章后不能跳过第3403章）', () => {
    const items: TocItemRaw[] = [
      { id: '38623350', title: '第3403章 甲' },
      { id: '38623354', title: '分节阅读第3404节' },
      { id: '38623375', title: '第3409章 天魔霸枪' },
      { id: '38623378', title: '第三千三百八十九章 又惹祸了' },
      { id: '38623395', title: '第三千四百零二章 进始祖界修为大进' },
      { id: '38623396', title: '分节阅读第3403节' },
      { id: '38623397', title: '第三千四百零四章 时间缓流区' },
      // 结尾整批重传：占位与同标题条目都丢弃
      { id: '49080100', title: '分节阅读第3403节' },
      { id: '49080101', title: '第三千四百零四章 时间缓流区' },
    ];
    const ids = mergeTocPages([items]).map((e) => e.id);
    expect(ids).toEqual(['38623350', '38623354', '38623375', '38623378', '38623395', '38623396', '38623397']);
  });
  it('结尾重传的有标题版本替换早期同号占位', () => {
    const items: TocItemRaw[] = [
      { id: '38626100', title: '分节阅读第4207节' },
      { id: '38626103', title: '第四千二百零八章 至高组会议' },
      { id: '49080153', title: '第四千二百零七章 某章' },
    ];
    const titles = mergeTocPages([items]).map((e) => e.title);
    expect(titles).toEqual(['第四千二百零七章 某章', '第四千二百零八章 至高组会议']);
  });
  it('同一章号优先保留有标题的版本', () => {
    const items: TocItemRaw[] = [
      { id: '49080154', title: '分节阅读第4208节' },
      { id: '38626103', title: '第四千二百零八章 至高组会议' },
    ];
    const merged = mergeTocPages([items]);
    const main = merged.filter((e) => !e.extra);
    expect(main).toHaveLength(1);
    expect(main[0]!.title).toBe('第四千二百零八章 至高组会议');
  });
});

describe('多书：任意书号（真实 fixture：104429 / 230580 / 14773）', () => {
  it('URL 只由校验过的书号拼出', async () => {
    const { tocPath: tp, chapterPath: cp, coverUrl, assertBookId } = await import('../src/shared/source.ts');
    expect(tp('104429', 1)).toBe('/book/104429/');
    expect(tp('104429', 17)).toBe('/book/104429/16.html');
    expect(cp('230580', '72836267')).toBe('/book/230580_72836267.html');
    expect(cp('230580', '72836267', 2)).toBe('/book/230580/72836267_2.html');
    expect(coverUrl('104429')).toBe('https://wanshuge.org/img/104429.jpg');
    for (const bad of ['', '0', '01', 'abc', '1/../2', '1234567890', '36780?x']) {
      expect(() => assertBookId(bad)).toThrow();
      expect(() => tp(bad, 1)).toThrow();
    }
  });

  it('parseChapterLink 只认本书链接，站内其他书的推荐链接一律忽略', () => {
    expect(parseChapterLink('104429', '/book/104429_21930803.html')).toEqual({ id: '21930803', pageIndex: 0 });
    expect(parseChapterLink('104429', '/book/104429/21930803_1.html')).toEqual({ id: '21930803', pageIndex: 1 });
    expect(parseChapterLink('104429', '/book/36780_21930803.html')).toBeNull();
    expect(parseChapterLink('36780', '/book/104429_21930803.html')).toBeNull();
    expect(parseChapterLink('104429', '/book/104429/16.html')).toBeNull();
    expect(parseChapterLink('bad', '/book/bad_1.html')).toBeNull();
  });

  it('总页数：17 页的书读 select；≤100 章的书 select 只有一项 → 1 页；详情页未知', () => {
    expect(parseTocTotalPages('104429', html('book-104429-p2.html'))).toBe(17);
    expect(parseTocTotalPages('14773', html('toc-14773-p2.html'))).toBe(2);
    expect(parseTocTotalPages('230580', html('book-230580-p2.html'))).toBe(1);
    expect(parseTocTotalPages('104429', html('book-104429-p1.html'))).toBeNull();
  });

  it('单页目录：第 1 页 + 回环的第 2 页合并后按上传顺序、无重复', () => {
    const merged = mergeTocPages([
      parseTocPageHtml('230580', html('book-230580-p1.html'), 1),
      parseTocPageHtml('230580', html('book-230580-p2.html'), 2),
    ]);
    expect(merged.map((e) => e.displayTitle)).toEqual([
      '人物介绍',
      ...Array.from({ length: 16 }, (_, i) => `第${i + 1}章`),
    ]);
  });

  it('第 1 页（详情页）的 og meta → 书籍信息，简介去掉站点套话', async () => {
    const { parseBookInfo } = await import('../src/shared/source.ts');
    const a = parseBookInfo('104429', html('book-104429-p1.html'))!;
    expect(a).toMatchObject({
      id: '104429',
      title: '斗破苍穹',
      author: '九支书竹',
      status: '连载中',
      firstChapterId: '21930717',
    });
    expect(a.intro).toMatch(/^三十三诸天世界/);
    const b = parseBookInfo('230580', html('book-230580-p1.html'))!;
    expect(b.title).toBe('万古神帝之万古青鹿传');
    expect(b.intro).toBeUndefined(); // 只有套话
    expect(b.firstChapterId).toBe('72836267');
    expect(parseBookInfo('36780', html('toc-page1.html'))!.firstChapterId).toBe('8924760');
  });

  it('正文为裸 <p>（无 qsbs、无 #content）时也能解析，且不混入推荐书链接', () => {
    const page = parseChapterPage('230580', html('chapter-230580-72836267-p1.html'), '72836267', 0);
    expect(page.title).toBe('人物介绍');
    expect(page.paragraphs.length).toBeGreaterThanOrEqual(5);
    expect(page.paragraphs[0]).toMatch(/^青鹿神王/);
    expect(page.paragraphs.join('')).not.toMatch(/请勿开启|万书阁/);
    expect(page.nextChapterId).toBe('72836268');
    expect(page.restricted).toBe(false);
  });

  it('源站会员门槛：标记 restricted，门槛提示不进正文（不绕过）', () => {
    const page = parseChapterPage('230580', html('chapter-230580-89420896-vip.html'), '89420896', 0);
    expect(page.restricted).toBe(true);
    expect(page.paragraphs).toHaveLength(5);
    expect(page.paragraphs.join('')).not.toMatch(/VIP|后续内容已被隐藏/);
    expect(page.nextChapterId).toBe('89420897');
  });

  it('fetchChapter 透传 restricted；单页章节靠探针 404 确认完整', async () => {
    const fetcher = fixtureFetcher({
      '/book/230580_89420896.html': html('chapter-230580-89420896-vip.html'),
    });
    const r = await fetchChapter('230580', '89420896', { fetcher, ...noRetry });
    expect(r.restricted).toBe(true);
    expect(r.complete).toBe(true);
    expect(r.paragraphs).toHaveLength(5);
  });

  it('其他书的编码正文与分页发现照常工作', () => {
    const page = parseChapterPage('104429', html('chapter-104429-21930803-p1.html'), '21930803', 0);
    expect(page.title).toBe('第八章吐血的莫小狼');
    expect(page.paragraphs.length).toBeGreaterThan(20);
    expect(page.sameChapterPages).toEqual([1]);
    expect(page.prevChapterId).toBe('21930792');
  });
});

describe('站内搜索', () => {
  it('桌面模板：书号/书名/作者/分类/最新章', async () => {
    const { parseSearchResults } = await import('../src/shared/source.ts');
    const r = parseSearchResults(html('search-desktop.html'))!;
    expect(r.length).toBeGreaterThan(20);
    expect(r[0]).toEqual({
      id: '17542',
      title: '斗破苍穹之重生萧玄',
      author: '爱蹦迪的奶牛',
      category: '言情小说',
      latestChapter: '第六章 富贵险中求',
      updated: '08-07',
    });
    expect(r.find((b) => b.id === '104429')?.title).toBe('斗破苍穹');
    expect(new Set(r.map((b) => b.id)).size).toBe(r.length);
  });

  it('移动模板：只取结果区，不混入“完结小说推荐”', async () => {
    const { parseSearchResults, MAX_SEARCH_RESULTS } = await import('../src/shared/source.ts');
    const r = parseSearchResults(html('search-mobile.html'))!;
    expect(r.length).toBeGreaterThan(10);
    expect(r.length).toBeLessThanOrEqual(MAX_SEARCH_RESULTS);
    expect(r[0]!.id).toBe('14455');
    expect(r[0]!.intro).toMatch(/^穿越斗破/);
  });

  it('无结果容器 → null；空结果 → []', async () => {
    const { parseSearchResults } = await import('../src/shared/source.ts');
    expect(parseSearchResults('<html><body>blocked</body></html>')).toBeNull();
    expect(parseSearchResults('<ul class="txt-list" id="novel-search">请输入搜索词</ul>')).toEqual([]);
  });

  it('搜索词规范化：裁剪空白、限长、拒绝控制字符', async () => {
    const { normalizeSearchQuery } = await import('../src/shared/source.ts');
    expect(normalizeSearchQuery('  斗破   苍穹 ')).toBe('斗破 苍穹');
    expect(normalizeSearchQuery('')).toBeNull();
    expect(normalizeSearchQuery('   ')).toBeNull();
    expect(normalizeSearchQuery('a\u0000b')).toBeNull();
    expect(normalizeSearchQuery('字'.repeat(31))).toBeNull();
    expect(normalizeSearchQuery('字'.repeat(30))).toBe('字'.repeat(30));
  });

  it('searchBooks 以 POST 表单发送，并保持两次上游搜索的最小间隔', async () => {
    const { searchBooks, resetSearchGate, MIN_SEARCH_GAP_MS } = await import('../src/shared/source.ts');
    resetSearchGate();
    const calls: { url: string; method?: string; body?: unknown; ct?: string }[] = [];
    const fetcher = async (url: string, init?: RequestInit) => {
      const headers = (init?.headers ?? {}) as Record<string, string>;
      calls.push({ url, method: init?.method, body: init?.body, ct: headers['Content-Type'] });
      return new Response(html('search-desktop.html'), { status: 200, headers: { 'content-type': 'text/html' } });
    };
    const waits: number[] = [];
    const sleep = (ms: number) => {
      waits.push(ms);
      return Promise.resolve();
    };
    const r = await searchBooks(' 斗破苍穹 ', { fetcher, retries: 0, sleep });
    expect(r.length).toBeGreaterThan(20);
    expect(calls[0]).toEqual({
      url: 'https://wanshuge.org/search.html',
      method: 'POST',
      body: `s=${encodeURIComponent('斗破苍穹')}`,
      ct: 'application/x-www-form-urlencoded',
    });
    await searchBooks('万古神帝', { fetcher, retries: 0, sleep });
    expect(waits.some((w) => w > MIN_SEARCH_GAP_MS - 1000)).toBe(true);
    await expect(searchBooks('', { fetcher, retries: 0, sleep })).rejects.toThrow();
    resetSearchGate();
  });
});
