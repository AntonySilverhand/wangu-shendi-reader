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
  parseTotalPagesFromTocHtml,
  tocPath,
  chapterPath,
  buildChapterUrl,
  type TocItemRaw,
} from '../src/shared/source.ts';

const here = dirname(fileURLToPath(import.meta.url));
const fixtures = (name: string) => join(here, 'fixtures', name);
const html = (name: string) => readFileSync(fixtures(name), 'utf8');

describe('URL 构建受限', () => {
  it('目录页 URL', () => {
    expect(tocPath(1)).toBe('/book/36780/');
    expect(tocPath(2)).toBe('/book/36780/1.html');
    expect(tocPath(44)).toBe('/book/36780/43.html');
    expect(() => tocPath(0)).toThrow();
  });
  it('章节页 URL', () => {
    expect(chapterPath('38621328')).toBe('/book/36780_38621328.html');
    expect(chapterPath('38621328', 1)).toBe('/book/36780/38621328_1.html');
    expect(buildChapterUrl('38621328', 2)).toBe(
      'https://wanshuge.org/book/36780/38621328_2.html',
    );
    expect(() => chapterPath('../../etc/passwd')).toThrow();
    expect(() => chapterPath('12', 40)).toThrow();
  });
  it('章节链接解析', () => {
    expect(parseChapterLink('/book/36780_38621328.html')).toEqual({
      id: '38621328',
      pageIndex: 0,
    });
    expect(parseChapterLink('/book/36780/38621328_1.html')).toEqual({
      id: '38621328',
      pageIndex: 1,
    });
    expect(parseChapterLink('https://evil.com/x')).toBeNull();
  });
});

describe('章节正文解析（真实 fixture）', () => {
  it('解析第 2771 章第 1 页', () => {
    const page = parseChapterPage(html('chapter-38621328-p1.html'), '38621328', 0);
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
    const page = parseChapterPage(html('chapter-38621328-p2.html'), '38621328', 1);
    expect(page.title).toBe('第二千七百七十一章 无疆到来');
    expect(page.nextPageIndex).toBe(2);
    expect(page.prevChapterId).toBeNull();
    expect(page.paragraphs.length).toBeGreaterThan(20);
  });
  it('最后一页指向下一章', () => {
    const page = parseChapterPage(html('chapter-38621328-p3.html'), '38621328', 2);
    expect(page.nextPageIndex).toBeNull();
    expect(page.nextChapterId).toBe('38621330');
    expect(page.prevChapterId).toBeNull();
  });
  it('用户原始链接对应的第 2772 章第 2 页', () => {
    const page = parseChapterPage(html('chapter-38621330-p2.html'), '38621330', 1);
    expect(page.title).toContain('第二千七百七十二章');
    expect(page.nextPageIndex).toBe(2);
    expect(page.prevChapterId).toBeNull();
  });
  it('“分节阅读”占位标题章节也能解析', () => {
    const page = parseChapterPage(html('chapter-13375259-p1.html'), '13375259', 0);
    expect(page.title).toBe('分节阅读第101节');
    expect(page.paragraphs.length).toBeGreaterThan(10);
    expect(page.nextPageIndex).toBe(1);
  });
  it('最早的第 1 章可以解析', () => {
    const page = parseChapterPage(html('chapter-8924760-p1.html'), '8924760', 0);
    expect(page.paragraphs.length).toBeGreaterThan(5);
  });
});

describe('目录页解析（真实 fixture）', () => {
  it('第 1 页：置顶块 + 正文 1-100 章', () => {
    const items = parseTocPageHtml(html('toc-page1.html'), 1);
    expect(items[0]!.title).toBe('新书元始法则发布了');
    expect(items.some((i) => i.title.startsWith('番外'))).toBe(true);
    expect(items.some((i) => i.title === '分节阅读第1节')).toBe(true);
    expect(items.some((i) => i.title === '分节阅读第100节')).toBe(true);
    // 详情页（第 1 页）没有页码选择器，总页数由后续页得知
    expect(parseTocTotalPages(html('toc-page1.html'))).toBeNull();
    expect(parseTocTotalPages(html('toc-page2.html'))).toBe(44);
    expect(parseTotalPagesFromTocHtml(html('toc-page2.html'))).toBe(44);
  });
  it('第 2 页分类：正文 101-200，置顶番外进 extras，尾部快捷链接被剔除', () => {
    const items = parseTocPageHtml(html('toc-page2.html'), 2);
    const { main, extras } = classifyTocItems(items, 2);
    expect(main[0]!.displayTitle).toBe('第101章');
    expect(main[main.length - 1]!.displayTitle).toBe('第200章');
    expect(main).toHaveLength(100);
    expect(extras.length).toBeGreaterThanOrEqual(7);
    expect(extras.every((e) => e.displayTitle.startsWith('番外'))).toBe(true);
    expect(main.some((m) => m.displayTitle === '第1章')).toBe(false);
  });
  it('两页合并后正文连续（第1章 … 第200章）', () => {
    const p1 = parseTocPageHtml(html('toc-page1.html'), 1);
    const p2 = parseTocPageHtml(html('toc-page2.html'), 2);
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
