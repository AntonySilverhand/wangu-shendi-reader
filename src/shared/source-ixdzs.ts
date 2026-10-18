/**
 * 书源适配：爱下电子书（ixdzs8.com，UTF-8）。“整本下载”型书源。
 *
 * 该站章节页前置 JS 安全验证（“正在验证浏览器 / ?challenge=token”），本应用不绕过，
 * 因此不逐章在线阅读，而是使用站方公开提供的“TXT下载”（down7.ixdzs8.com/{n}.zip，
 * 允许跨域）：书页提供书籍信息与完整目录，客户端下载压缩包、本机解压，按目录切分章节，
 * 作为离线书保存（见 web/store/book-download.ts）。
 *
 * 受限 URL（只由校验过的书号拼出）：
 *   书页（信息 + 完整目录）  https://ixdzs8.com/read/{n}/
 *   搜索                      GET https://ixdzs8.com/bsearch?q={关键词}
 *   整本 TXT                  https://down7.ixdzs8.com/{n}.zip
 * 书号在本应用中写作 `ixdzs-{n}`。
 */
import {
  cleanIntro,
  cleanText,
  decodeEntities,
  divInner,
  fetchSourceHtml,
  metaContent,
  normalizeSearchQuery,
  normalizeTitle,
  SourceError,
  MAX_SEARCH_RESULTS,
  type BookInfo,
  type BookSummary,
  type FetchOptions,
  type TocEntry,
  type TocRangeResult,
} from './source.ts';
import { isJunkLine } from './text-clean.ts';

export const IXDZS = {
  id: 'ixdzs',
  name: '爱下电子书',
  origin: 'https://ixdzs8.com',
} as const;

export const IXDZS_HOSTS: ReadonlySet<string> = new Set(['ixdzs8.com', 'www.ixdzs8.com']);
export const IXDZS_DOWNLOAD_ORIGIN = 'https://down7.ixdzs8.com';
export const IXDZS_BOOK_PREFIX = 'ixdzs-';
/** 整本压缩包上限（万古神帝约 15MB） */
export const IXDZS_MAX_ZIP_BYTES = 64 * 1024 * 1024;

const NUM_RE = /^[1-9]\d{0,8}$/;
const BOOK_ID_RE = /^ixdzs-([1-9]\d{0,8})$/;

export function isIxdzsBookId(id: unknown): id is string {
  return typeof id === 'string' && BOOK_ID_RE.test(id);
}

export function ixdzsBookId(num: string): string {
  if (!NUM_RE.test(num)) throw new Error('invalid ixdzs book number');
  return IXDZS_BOOK_PREFIX + num;
}

export function ixdzsNumber(bookId: string): string {
  const m = BOOK_ID_RE.exec(bookId);
  if (!m) throw new Error('invalid ixdzs book id');
  return m[1]!;
}

export function ixdzsBookUrl(bookId: string): string {
  return `${IXDZS.origin}/read/${ixdzsNumber(bookId)}/`;
}

export function ixdzsDownloadUrl(bookId: string): string {
  return `${IXDZS_DOWNLOAD_ORIGIN}/${ixdzsNumber(bookId)}.zip`;
}

export function ixdzsSearchUrl(query: string): string {
  return `${IXDZS.origin}/bsearch?q=${encodeURIComponent(query)}`;
}

/** href → 站内路径（外站返回 null） */
function ixdzsPath(href: string): string | null {
  let h = decodeEntities((href ?? '').trim());
  if (!h) return null;
  if (/^https?:\/\//i.test(h) || h.startsWith('//')) {
    try {
      const u = new URL(h.startsWith('//') ? `https:${h}` : h);
      if (!IXDZS_HOSTS.has(u.hostname)) return null;
      h = u.pathname;
    } catch {
      return null;
    }
  }
  h = h.replace(/[?#].*$/, '');
  return h.startsWith('/') ? h : null;
}

/** 本书章节链接 /read/{n}/p{id}.html → 章节号；其它返回 null */
export function parseIxdzsChapterLink(bookId: string, href: string): string | null {
  if (!isIxdzsBookId(bookId)) return null;
  const m = ixdzsPath(href)?.match(/^\/read\/([1-9]\d{0,8})\/p(\d{1,12})\.html$/);
  if (!m || m[1] !== ixdzsNumber(bookId)) return null;
  return m[2]!;
}

function textOf(fragment: string): string {
  return cleanText(fragment).replace(/\n+/g, ' ').trim();
}

/* ------------------------------------------------------------------ */
/* 书页：书籍信息（og meta）+ 完整目录（div#a-list ul.u-chapter）         */
/* ------------------------------------------------------------------ */

export interface IxdzsBookPage {
  book: BookInfo | null;
  entries: TocEntry[];
}

export function parseIxdzsBookPage(bookId: string, html: string): IxdzsBookPage {
  ixdzsNumber(bookId);
  // 完整目录在 #a-list（正序）；页面顶部 .cfirst 只是倒序的最新几章，不取
  const block = divInner(html, /\bid\s*=\s*["']a-list["']/i);
  const entries: TocEntry[] = [];
  const seen = new Set<string>();
  const scan = (fragment: string) => {
    const re = /<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi;
    let m: RegExpExecArray | null;
    while ((m = re.exec(fragment))) {
      const id = parseIxdzsChapterLink(bookId, m[1]!);
      const title = textOf(m[2]!);
      if (!id || !title || seen.has(id)) continue;
      seen.add(id);
      const info = normalizeTitle(title);
      // 目录顺序即阅读顺序；“彩蛋章/完本感言”等原位保留
      entries.push({ id, title, displayTitle: info.displayTitle, number: info.number, extra: false });
    }
  };
  if (block) scan(block);

  const title = metaContent(html, 'og:novel:book_name') ?? metaContent(html, 'og:title');
  if (!title) return { book: null, entries };
  const book: BookInfo = { id: bookId, title, author: metaContent(html, 'og:novel:author') ?? '' };
  const category = metaContent(html, 'og:novel:category');
  const status = metaContent(html, 'og:novel:status');
  const latest = metaContent(html, 'og:novel:latest_chapter_name');
  const updated = metaContent(html, 'og:novel:update_time');
  if (category) book.category = category;
  if (status) book.status = status;
  if (latest) book.latestChapter = latest;
  if (updated) book.updatedAt = updated.replace('T', ' ').replace(/[+-]\d{2}:\d{2}$/, '');
  // 简介：页面 #intro 比 og:description（被截断、套话）完整
  const introHtml = html.match(/<p\b[^>]*\bid\s*=\s*["']intro["'][^>]*>([\s\S]*?)<\/p>/i)?.[1];
  const intro = cleanIntro(
    (introHtml ? cleanText(introHtml.replace(/<span\b[\s\S]*?<\/span>/gi, '')) : metaContent(html, 'og:description') ?? '')
      .replace(/\n+/g, ' ')
      .replace(/…{2,}\s*$/u, '')
      .trim(),
    title,
  );
  if (intro) book.intro = intro;
  const size = html.match(/<span\b[^>]*class\s*=\s*["']nsize["'][^>]*>([^<]{1,20})<\/span>/i)?.[1]?.trim();
  if (size) book.size = size;
  if (entries[0]) book.firstChapterId = entries[0].id;
  return { book, entries };
}

/**
 * 目录区间：书页一次列出全部章节，只有第 1 页（附带书籍信息）。
 * 目录为空时抛错（不缓存失败结果）。
 */
export async function fetchIxdzsTocRange(
  bookId: string,
  from: number,
  to: number,
  opts: FetchOptions = {},
): Promise<TocRangeResult> {
  ixdzsNumber(bookId);
  if (from < 1 || to < from) throw new Error('bad toc range');
  if (from > 1) return { from, to, totalPages: 1, pages: [], failedPages: [] };
  const { html } = await fetchSourceHtml(ixdzsBookUrl(bookId), { ...opts, highPriority: false, allowedHosts: IXDZS_HOSTS });
  const { book, entries } = parseIxdzsBookPage(bookId, html);
  if (entries.length === 0) throw new SourceError('目录为空或书源页面结构已变化', 'invalid', true);
  const result: TocRangeResult = { from, to, totalPages: 1, pages: [{ page: 1, entries }], failedPages: [] };
  if (book) result.book = book;
  return result;
}

/* ------------------------------------------------------------------ */
/* 搜索（GET /bsearch?q=）                                              */
/* ------------------------------------------------------------------ */

function bookNumberFromHref(href: string): string | null {
  return ixdzsPath(href)?.match(/^\/read\/([1-9]\d{0,8})\/$/)?.[1] ?? null;
}

/** 结果在 ul.u-list > li（h3.bname 书名 / .bauthor 作者 / .size 字数 / .lz|.end 状态 / .l-p2 简介 / .l-chapter 最新章） */
export function parseIxdzsSearch(html: string): BookSummary[] | null {
  const list = html.match(/<ul\b[^>]*class\s*=\s*["'][^"']*\bu-list\b[^"']*["'][^>]*>([\s\S]*?)<\/ul>/i);
  if (!list) {
    if (/搜索结果|bsearch/.test(html) && /没有|未找到|暂无/.test(html)) return [];
    return null;
  }
  const out: BookSummary[] = [];
  const seen = new Set<string>();
  const liRe = /<li\b([^>]*)>([\s\S]*?)<\/li>/gi;
  let m: RegExpExecArray | null;
  while ((m = liRe.exec(list[1]!)) && out.length < MAX_SEARCH_RESULTS) {
    const li = m[2]!;
    const h3 = li.match(/<h3\b[^>]*class\s*=\s*["'][^"']*\bbname\b[^"']*["'][^>]*>([\s\S]*?)<\/h3>/i)?.[1] ?? '';
    const a = h3.match(/<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/i);
    const num = a ? bookNumberFromHref(a[1]!) : bookNumberFromHref(m[1]!.match(/data-url\s*=\s*["']([^"']+)["']/i)?.[1] ?? '');
    const title = a ? textOf(a[2]!) : '';
    if (!num || !title || seen.has(num)) continue;
    seen.add(num);
    const b: BookSummary = { id: ixdzsBookId(num), title };
    const span = (cls: string) =>
      li.match(new RegExp(`<span\\b[^>]*class\\s*=\\s*["'][^"']*\\b${cls}\\b[^"']*["'][^>]*>([\\s\\S]*?)<\\/span>`, 'i'))?.[1];
    const author = span('bauthor');
    if (author && textOf(author)) b.author = textOf(author);
    const status = span('lz') ?? span('end');
    const size = span('size');
    const meta = [status && textOf(status), size && textOf(size)].filter(Boolean).join(' · ');
    if (meta) b.category = meta;
    const intro = li.match(/<p\b[^>]*class\s*=\s*["'][^"']*\bl-p2\b[^"']*["'][^>]*>([\s\S]*?)<\/p>/i)?.[1];
    if (intro) {
      const text = cleanIntro(textOf(intro), title);
      if (text) b.intro = text.length > 160 ? `${text.slice(0, 160)}…` : text;
    }
    const latest = span('l-chapter');
    if (latest && textOf(latest)) b.latestChapter = textOf(latest);
    out.push(b);
  }
  return out;
}

export async function searchIxdzs(query: string, opts: FetchOptions = {}): Promise<BookSummary[]> {
  const q = normalizeSearchQuery(query);
  if (q === null) throw new SourceError('invalid search query', 'invalid', false);
  const { html } = await fetchSourceHtml(ixdzsSearchUrl(q), { ...opts, highPriority: true, allowedHosts: IXDZS_HOSTS });
  const results = parseIxdzsSearch(html);
  if (results === null) throw new SourceError('unexpected search page', 'invalid', true);
  return results;
}

/* ------------------------------------------------------------------ */
/* 整本 TXT 切分（按书页目录对齐）                                       */
/* ------------------------------------------------------------------ */

export interface TxtChapter {
  id: string;
  title: string;
  paragraphs: string[];
}

export interface TxtSplitResult {
  chapters: TxtChapter[];
  /** 目录里有、TXT 里没找到的章节 */
  missing: TocEntry[];
  /** true = 按目录对齐（章节号与站点一致）；false = 目录缺失/对不上，按顶格标题顺序编号 */
  aligned: boolean;
}

/** 站方 TXT：『书名/作者』『状态』『内容简介:…』+ 广告行 + “------章节内容开始-------” + 顶格标题 / 全角缩进正文 */
const MARKER_RE = /^-{3,}\s*章节内容开始\s*-{3,}$/;
const TRAILER_RE = /^(?:=+\s*已完结\s*=+|『[^』]*』?)$/;
const AD_RE = /爱下电子书|ixdzs\d*\.(?:com|tw|hk)/i;
const NUMBERED_HEADING_RE = /^第[零〇一二三四五六七八九十百千万两\d]+[章节回卷]/;
/** 目录对齐时允许向后跳过（TXT 缺章）的最大章数 */
const ALIGN_WINDOW = 200;

const titleKey = (s: string) => s.replace(/[\s　]+/g, '');

/**
 * 把整本 TXT 切成章节。目录（书页）可用时按目录标题逐条对齐：TXT 的顶格行与目录标题
 * （去空白后）相等即为章节开头，章节号用站点的 p 编号，标题用目录里的规范写法；
 * 对不上的顶格行当正文保留（宁多勿少）。目录缺失或对齐率过低时退回按顶格标题切分。
 */
export function splitIxdzsTxt(text: string, catalog: TocEntry[]): TxtSplitResult {
  const lines = text.replace(/\r\n?/g, '\n').split('\n');
  let start = lines.findIndex((l) => MARKER_RE.test(l.trim()));
  start = start >= 0 ? start + 1 : 0;

  const positions = new Map<string, number[]>();
  catalog.forEach((entry, i) => {
    const k = titleKey(entry.title);
    const list = positions.get(k);
    if (list) list.push(i);
    else positions.set(k, [i]);
  });

  const chapters: TxtChapter[] = [];
  const missing: TocEntry[] = [];
  let current: TxtChapter | null = null;
  let next = 0;
  let matched = 0;
  let headings = 0;
  for (let i = start; i < lines.length; i++) {
    const raw = lines[i]!;
    const line = raw.trim();
    if (!line) continue;
    const indented = /^[　 \t]/.test(raw);
    if (!indented) {
      if (TRAILER_RE.test(line) || (AD_RE.test(line) && line.length <= 120)) continue;
      headings++;
      const j = positions.get(titleKey(line))?.find((x) => x >= next && x - next <= ALIGN_WINDOW);
      if (j !== undefined) {
        for (let k = next; k < j; k++) missing.push(catalog[k]!);
        current = { id: catalog[j]!.id, title: catalog[j]!.title, paragraphs: [] };
        chapters.push(current);
        next = j + 1;
        matched++;
        continue;
      }
    } else if (AD_RE.test(line) && line.length <= 120) {
      continue;
    }
    current?.paragraphs.push(line);
  }

  if (catalog.length > 0 && matched > 0 && matched >= headings * 0.5) {
    for (let k = next; k < catalog.length; k++) missing.push(catalog[k]!);
    return { chapters, missing, aligned: true };
  }

  // 退路：顶格的短行即标题，章节号顺排。该格式标题一律顶格，缩进行即使以“第N章”开头也是正文
  // （实测《万古神帝》18 处引用章节名）。作者的话里偶有未缩进的长段落（实测 8 处），不当标题：
  // 标题 ≤ 40 字，且除“第N章…”外不以句号结尾（目录里确有“第2796章 明王坐禅玉失珠。”）。
  const fallback: TxtChapter[] = [];
  let cur: TxtChapter | null = null;
  const headingLike = (line: string) =>
    [...line].length <= 40 && (!/[。…]$/.test(line) || NUMBERED_HEADING_RE.test(line)) && !isJunkLine(line);
  for (let i = start; i < lines.length; i++) {
    const raw = lines[i]!;
    const line = raw.trim();
    if (!line) continue;
    if (TRAILER_RE.test(line) || (AD_RE.test(line) && line.length <= 120)) continue;
    if (!/^[　 \t]/.test(raw) && headingLike(line)) {
      cur = { id: String(fallback.length + 1), title: line, paragraphs: [] };
      fallback.push(cur);
      continue;
    }
    if (!cur) {
      cur = { id: '1', title: '正文', paragraphs: [] };
      fallback.push(cur);
    }
    cur.paragraphs.push(line);
  }
  return { chapters: fallback, missing: [], aligned: false };
}
