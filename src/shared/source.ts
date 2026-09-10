/**
 * 书源适配层（万书阁 wanshuge.org / 《万古神帝》book 36780）。
 *
 * 本模块是纯函数 + 可注入 fetch 的同构代码，同时运行于：
 *   - Cloudflare Worker（生产）
 *   - Node dev server（本地开发/自托管）
 *   - 单元测试（传入假 fetch 与本地 HTML fixture）
 *
 * 只做三件事：构建受限的书源 URL、解析 HTML、合并分页。所有网络请求都限定在
 * BOOK.origin + BOOK.id 范围内，调用方无法传入任意 URL（见 buildChapterUrl/tocPath）。
 */

export const BOOK = {
  id: '36780',
  title: '万古神帝',
  author: '飞天鱼',
  origin: 'https://wanshuge.org',
  /** 源站目录页的“上一页/下一页”数量会变化，这里只作为兜底提示 */
  tocPageFallback: 44,
} as const;

export const ALLOWED_HOSTS = new Set(['wanshuge.org', 'www.wanshuge.org']);

/** 源站的分页正文上限保护，防止书源结构异常导致无限循环 */
export const MAX_CHAPTER_PAGES = 40;
/** 单个 HTML 响应体上限 */
export const MAX_HTML_BYTES = 4 * 1024 * 1024;
export const REQUEST_TIMEOUT_MS = 10_000;
export const MAX_RETRIES = 2;

export type FetchLike = (input: string, init?: RequestInit) => Promise<Response>;

export interface TocItemRaw {
  id: string;
  title: string;
}

export interface TocEntry {
  id: string;
  /** 源站标题原文 */
  title: string;
  /** 面向阅读的标题（把“分节阅读第N节”归一为“第N章”） */
  displayTitle: string;
  /** 章节序号（无法识别时为 null） */
  number: number | null;
  /** 番外等附加内容 */
  extra: boolean;
}

export interface TocPageResult {
  page: number;
  totalPages: number;
  entries: TocEntry[];
}

export interface ChapterResult {
  id: string;
  title: string;
  paragraphs: string[];
  prevId: string | null;
  nextId: string | null;
  pageCount: number;
  charCount: number;
}

export interface RawChapterPage {
  id: string;
  pageIndex: number;
  title: string;
  paragraphs: string[];
  prevChapterId: string | null;
  nextChapterId: string | null;
  nextPageIndex: number | null;
}

/* ------------------------------------------------------------------ */
/* URL building (受限：只允许 BOOK 范围内的路径)                        */
/* ------------------------------------------------------------------ */

export function tocPath(page: number): string {
  if (!Number.isInteger(page) || page < 1) throw new Error('bad toc page');
  if (page === 1) return `/book/${BOOK.id}/`;
  return `/book/${BOOK.id}/${page - 1}.html`;
}

export function chapterPath(id: string, pageIndex = 0): string {
  assertChapterId(id);
  if (!Number.isInteger(pageIndex) || pageIndex < 0 || pageIndex >= MAX_CHAPTER_PAGES) {
    throw new Error('bad chapter page index');
  }
  if (pageIndex === 0) return `/book/${BOOK.id}_${id}.html`;
  return `/book/${BOOK.id}/${id}_${pageIndex}.html`;
}

export function buildChapterUrl(id: string, pageIndex = 0): string {
  return BOOK.origin + chapterPath(id, pageIndex);
}

export function buildTocUrl(page: number): string {
  return BOOK.origin + tocPath(page);
}

export function assertChapterId(id: string): void {
  if (!/^\d{1,12}$/.test(id)) throw new Error('invalid chapter id');
}

/* ------------------------------------------------------------------ */
/* HTML helpers                                                        */
/* ------------------------------------------------------------------ */

const ENTITIES: Record<string, string> = {
  amp: '&',
  lt: '<',
  gt: '>',
  quot: '"',
  apos: "'",
  nbsp: '\u00a0',
  ldquo: '\u201c',
  rdquo: '\u201d',
  mdash: '\u2014',
  hellip: '\u2026',
};

export function decodeEntities(input: string): string {
  return input.replace(/&(#x?[0-9a-fA-F]+|[a-zA-Z]+);/g, (m, code: string) => {
    if (code.startsWith('#x') || code.startsWith('#X')) {
      const cp = parseInt(code.slice(2), 16);
      return Number.isFinite(cp) ? String.fromCodePoint(cp) : m;
    }
    if (code.startsWith('#')) {
      const cp = parseInt(code.slice(1), 10);
      return Number.isFinite(cp) ? String.fromCodePoint(cp) : m;
    }
    return ENTITIES[code] ?? m;
  });
}

export function stripTags(html: string): string {
  return html
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<\/p\s*>/gi, '\n')
    .replace(/<[^>]+>/g, '');
}

function cleanText(raw: string): string {
  let t = decodeEntities(stripTags(raw));
  t = t.replace(/\u00a0/g, ' ').replace(/\r\n?/g, '\n');
  t = t
    .split('\n')
    .map((line) => line.replace(/[ \t\u3000]+/g, ' ').trim())
    .filter(Boolean)
    .join('\n');
  return t;
}

/** 明显的模板噪声行，避免写入正文缓存 */
const NOISE = [
  /^请勿开启浏览器阅读模式/,
  /^手机浏览器扫描二维码/,
  /^万书阁/,
  /^加入书架$/,
  /^保存书签$/,
  /^上一章$/,
  /^下一章$/,
  /^章节(目录|列表)$/,
  /^热门小说推荐/,
  /^阅读记录$/,
];

function isNoise(text: string): boolean {
  return NOISE.some((re) => re.test(text));
}

export function base64ToUtf8(b64: string): string {
  const bin = atob(b64.replace(/\s+/g, ''));
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return new TextDecoder('utf-8').decode(bytes);
}

/** 源站把正文放在 document.writeln(qsbs.bb('base64')) 里 */
export function extractEncodedParagraphs(html: string): string[] {
  const out: string[] = [];
  const re = /qsbs\.bb\('([^']*)'\)/g;
  let m: RegExpExecArray | null;
  while ((m = re.exec(html))) {
    let decoded: string;
    try {
      decoded = base64ToUtf8(m[1]!);
    } catch {
      continue;
    }
    const text = cleanText(decoded);
    if (text) out.push(text);
  }
  return out;
}

/** 兼容未编码的老页面/降级页面 */
export function extractPlainParagraphs(html: string): string[] {
  const candidates = [
    /<div[^>]+id="content"[^>]*>([\s\S]*?)<\/div>/i,
    /<div[^>]+class="[^"]*\bcontent\b[^"]*"[^>]*>([\s\S]*?)<\/div>/i,
    /<div[^>]+class="[^"]*\bread[-_]?content\b[^"]*"[^>]*>([\s\S]*?)<\/div>/i,
  ];
  for (const re of candidates) {
    const m = html.match(re);
    if (!m) continue;
    const block = m[1]!;
    const parts = /<p[\s>]/i.test(block)
      ? block.split(/<\/p\s*>/i)
      : block.split(/<br\s*\/?>/i);
    const paras = parts.map(cleanText).filter((t) => t && !isNoise(t));
    if (paras.length > 0) return paras;
  }
  return [];
}

export function extractTitle(html: string): string {
  const h2 = html.match(
    /<h2[^>]*class="[^"]*chapter-title[^"]*"[^>]*>([\s\S]*?)<\/h2>/i,
  );
  if (h2) return cleanTitle(stripTags(h2[1]!));
  const t = html.match(/<title>([\s\S]*?)<\/title>/i);
  if (t) {
    return cleanTitle(
      decodeEntities(stripTags(t[1]!))
        .replace(/[_\-|]\s*万书阁\s*$/u, '')
        .replace(/\s*第\d+页\s*$/u, '')
        .replace(/\(飞天鱼\)/g, '')
        .trim(),
    );
  }
  return '';
}

function cleanTitle(raw: string): string {
  return raw
    .replace(/（第\d+页）/g, '')
    .replace(/\(第\d+页\)/g, '')
    .replace(/\s+/g, ' ')
    .trim();
}

interface NavLink {
  href: string;
  text: string;
}

function extractNavLinks(html: string): NavLink[] {
  const links: NavLink[] = [];
  const re = /<a\b[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/gi;
  let m: RegExpExecArray | null;
  while ((m = re.exec(html))) {
    const text = stripTags(m[2]!).replace(/\s+/g, '');
    if (/^(上一章|下一章|上一页|下一页|章节目录)$/.test(text)) {
      links.push({ href: m[1]!, text });
    }
  }
  return links;
}

interface ParsedChapterLink {
  id: string;
  pageIndex: number;
}

export function parseChapterLink(href: string): ParsedChapterLink | null {
  let m = href.match(new RegExp(`^/book/${BOOK.id}_(\\d+)\\.html$`));
  if (m) return { id: m[1]!, pageIndex: 0 };
  m = href.match(new RegExp(`^/book/${BOOK.id}/(\\d+)(?:_(\\d+))?\\.html$`));
  if (m) return { id: m[1]!, pageIndex: m[2] ? parseInt(m[2], 10) : 0 };
  return null;
}

/* ------------------------------------------------------------------ */
/* Chapter parsing                                                     */
/* ------------------------------------------------------------------ */

export function parseChapterPage(
  html: string,
  expectedId: string,
  pageIndex: number,
): RawChapterPage {
  const title = extractTitle(html);
  let paragraphs = extractEncodedParagraphs(html);
  if (paragraphs.length === 0) paragraphs = extractPlainParagraphs(html);

  const cleaned: string[] = [];
  const seen = new Set<string>();
  for (const p of paragraphs) {
    const t = p.trim();
    if (!t || isNoise(t)) continue;
    if (t === title) continue;
    // 同一页偶尔重复输出，去重但保留顺序
    if (seen.has(t)) continue;
    seen.add(t);
    cleaned.push(t);
  }

  let prevChapterId: string | null = null;
  let nextChapterId: string | null = null;
  let nextPageIndex: number | null = null;

  for (const link of extractNavLinks(html)) {
    const parsed = parseChapterLink(link.href);
    if (!parsed) continue;
    if (parsed.id === expectedId) {
      if (parsed.pageIndex === pageIndex + 1) nextPageIndex = parsed.pageIndex;
      continue;
    }
    if (link.text === '上一章' && prevChapterId === null) prevChapterId = parsed.id;
    if (link.text === '下一章' && nextChapterId === null) nextChapterId = parsed.id;
  }

  return {
    id: expectedId,
    pageIndex,
    title: title || `章节 ${expectedId}`,
    paragraphs: cleaned,
    prevChapterId,
    nextChapterId,
    nextPageIndex,
  };
}

/* ------------------------------------------------------------------ */
/* TOC parsing                                                         */
/* ------------------------------------------------------------------ */

function extractListItems(block: string): TocItemRaw[] {
  const items: TocItemRaw[] = [];
  const re =
    /<a\b[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/gi;
  let m: RegExpExecArray | null;
  while ((m = re.exec(block))) {
    const href = m[1]!;
    const parsed = parseChapterLink(href);
    if (!parsed || parsed.pageIndex !== 0) continue;
    const title = decodeEntities(stripTags(m[2]!)).replace(/\s+/g, ' ').trim();
    if (!title) continue;
    // 排除目录分页链接（/book/36780/2.html 这类会被误读成章节 2）
    if (/^(上一页|下一页|首页|尾页)$/.test(title)) continue;
    items.push({ id: parsed.id, title });
  }
  return items;
}

/**
 * 目录页有两种模板：
 *  - 第 1 页（书籍详情页）有多个 ul：置顶“新书 + 番外”区块 + 正文 1-100 章
 *  - 第 2+ 页主列表是 ul.section-list（头部夹带置顶区块，尾部夹带“从第一章开始”的快捷链接）
 * 解析阶段不做过滤，分类与去重见 classifyTocItems/mergeTocPages。
 */
export function parseTocPageHtml(html: string, _page: number): TocItemRaw[] {
  const items: TocItemRaw[] = [];
  const re =
    /<ul[^>]+class="[^"]*\b(?:ph_list|section-list)\b[^"]*"[^>]*>([\s\S]*?)<\/ul>/gi;
  let m: RegExpExecArray | null;
  while ((m = re.exec(html))) {
    items.push(...extractListItems(m[1]!));
  }
  if (items.length > 0) return items;

  // 兜底：整页扫描
  return extractListItems(html);
}

interface ClassifiedToc {
  main: TocEntry[];
  extras: TocEntry[];
}

function toEntry(item: TocItemRaw): TocEntry {
  const info = normalizeTitle(item.title);
  return {
    id: item.id,
    title: item.title,
    displayTitle: info.displayTitle,
    number: info.number,
    extra: info.extra,
  };
}

/** 标题去掉“第X章”后的部分；“分节阅读第N节”视为空占位 */
export function restTitle(entry: TocEntry): string {
  if (/^分节阅读第\d+节$/.test(entry.title.trim())) return '';
  const m = entry.displayTitle.match(/^第[\d零〇一二三四五六七八九十百千万两]+章[\s　]*(.*)$/);
  if (m) return m[1] ?? '';
  return entry.displayTitle;
}

function median(values: number[]): number | null {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.floor(sorted.length / 2)] ?? null;
}

/**
 * 单页目录分类：
 *  - 番外/新书放入 extras（新书推广丢弃）
 *  - 正文剔除两类噪声：
 *    1. 每页末尾重复出现的“从第一章开始”快捷链接（“分节阅读第1节…”）
 *    2. 与页面主体章节号差距过大的条目（源站多次上传遗留的错位区块）
 * 源站同一章可能被上传多次，去重与排序在 mergeTocEntries 中完成。
 */
export function classifyTocItems(items: TocItemRaw[], page = 1): ClassifiedToc {
  const main: TocEntry[] = [];
  const extras: TocEntry[] = [];

  // 先去掉促销/番外，得到正文候选中位数
  const candidates: TocEntry[] = [];
  for (const item of items) {
    const title = item.title.trim();
    if (!title || isPromo(title)) continue;
    const entry = toEntry({ id: item.id, title });
    if (entry.extra) extras.push(entry);
    else candidates.push(entry);
  }
  const numbers = candidates
    .map((e) => e.number)
    .filter((n): n is number => n !== null && n > 0);
  const mid = median(numbers);

  for (const entry of candidates) {
    if (
      page >= 2 &&
      mid !== null &&
      entry.number !== null &&
      entry.number > 0 &&
      (entry.number < mid / 2 || entry.number > mid * 2)
    ) {
      continue; // 与主体章节号明显错位的区块（含尾部快捷链接）
    }
    main.push(entry);
  }
  return { main, extras };
}

/**
 * 合并多页目录（含去重、排序、番外归位）：
 *  - 正文按章节号排序，同一章优先保留“有标题”的版本
 *  - 同一章号的不同标题保留（源站少量错号章节）
 *  - 无章号的收尾内容（完本感言/彩蛋章）排在正文后面
 *  - 番外按番外序号排序，附在最后
 */
export function mergeTocEntries(pages: TocEntry[][]): TocEntry[] {
  const groups = new Map<number, { entries: TocEntry[]; order: number }>();
  const noNumber: TocEntry[] = [];
  const noNumberSeen = new Set<string>();
  const seenIds = new Set<string>();
  let order = 0;

  for (const page of pages) {
    for (const entry of page) {
      if (entry.extra) continue;
      if (seenIds.has(entry.id)) continue;
      seenIds.add(entry.id);
      if (entry.number === null || entry.number <= 0) {
        if (!noNumberSeen.has(entry.displayTitle)) {
          noNumberSeen.add(entry.displayTitle);
          noNumber.push(entry);
        }
        continue;
      }
      let group = groups.get(entry.number);
      if (!group) {
        group = { entries: [], order: order++ };
        groups.set(entry.number, group);
      }
      group.entries.push(entry);
    }
  }

  const main: TocEntry[] = [];
  const sortedNumbers = [...groups.keys()].sort((a, b) => a - b);
  for (const number of sortedNumbers) {
    const group = groups.get(number)!;
    const titled = group.entries.filter((e) => restTitle(e) !== '');
    const pool = titled.length > 0 ? titled : group.entries;
    const seenRest = new Set<string>();
    for (const entry of pool) {
      const rest = restTitle(entry);
      if (seenRest.has(rest)) continue;
      seenRest.add(rest);
      main.push(entry);
    }
  }
  main.push(...noNumber);

  // 番外：同标题保留 id 最大（最新）的一份，按番外序号排序
  const extrasByTitle = new Map<string, TocEntry>();
  for (const page of pages) {
    for (const entry of page) {
      if (!entry.extra) continue;
      const prev = extrasByTitle.get(entry.displayTitle);
      if (!prev || BigInt(entry.id) > BigInt(prev.id)) extrasByTitle.set(entry.displayTitle, entry);
    }
  }
  const extras = [...extrasByTitle.values()].sort((a, b) => {
    const an = a.number ?? Number.MAX_SAFE_INTEGER;
    const bn = b.number ?? Number.MAX_SAFE_INTEGER;
    if (an !== bn) return an - bn;
    return a.title.localeCompare(b.title, 'zh');
  });
  return [...main, ...extras];
}

/** 兼容旧调用 */
export function mergeTocPages(pages: TocItemRaw[][]): TocEntry[] {
  return mergeTocEntries(pages.map((items, index) => {
    const { main, extras } = classifyTocItems(items, index + 1);
    return [...main, ...extras];
  }));
}

export function parseTocTotalPages(html: string): number | null {
  const m = html.match(new RegExp(`/book/${BOOK.id}/(\\d+)\\.html"[^>]*>第[\\d\\-]+章`));
  const select = html.match(/<select[\s\S]*?<\/select>/i);
  if (select) {
    const options = select[0].match(new RegExp(`/book/${BOOK.id}/(\\d+)\\.html`, 'g'));
    if (options && options.length > 0) {
      const max = Math.max(
        ...options.map((o) => parseInt(o.match(/\/(\d+)\.html/)![1]!, 10)),
      );
      return max + 1;
    }
  }
  if (m) return parseInt(m[1]!, 10) + 1;
  return null;
}

/* ------------------------------------------------------------------ */
/* Title / number normalization                                        */
/* ------------------------------------------------------------------ */

const CN_DIGITS: Record<string, number> = {
  零: 0,
  〇: 0,
  一: 1,
  二: 2,
  两: 2,
  三: 3,
  四: 4,
  五: 5,
  六: 6,
  七: 7,
  八: 8,
  九: 9,
};

const CN_UNITS: Record<string, number> = { 十: 10, 百: 100, 千: 1000, 万: 10000 };

export function parseChineseNumber(input: string): number | null {
  if (!input) return null;
  if (/^\d+$/.test(input)) return parseInt(input, 10);
  let total = 0;
  let section = 0;
  let number = 0;
  for (const ch of input) {
    if (ch in CN_DIGITS) {
      number = CN_DIGITS[ch]!;
    } else if (ch in CN_UNITS) {
      const unit = CN_UNITS[ch]!;
      if (unit === 10000) {
        section = (section + number) * unit;
        total += section;
        section = 0;
      } else {
        section += (number || 1) * unit;
      }
      number = 0;
    } else {
      return null;
    }
  }
  return total + section + number;
}

export interface TitleInfo {
  displayTitle: string;
  number: number | null;
  extra: boolean;
}

export function normalizeTitle(title: string): TitleInfo {
  let t = title.replace(/\s+/g, ' ').trim();
  // 源站有些条目带序号前缀：“803 第803章 真的阴间”
  t = t.replace(/^\d{1,4}[\s.、，]+(?=第)/, '');
  // “分节阅读第123节” → “第123章”
  let m = t.match(/^分节阅读第(\d+)节$/);
  if (m) {
    const n = parseInt(m[1]!, 10);
    return { displayTitle: `第${n}章`, number: n, extra: false };
  }
  // “第二千七百七十一章 无疆到来”，兼容源站把“章”误写成“掌”、或漏写的条目
  m = t.match(/^第([零〇一二三四五六七八九十百千万两]+|\d+)[章回掌][\s　]*(.*)$/);
  if (m) {
    const n = parseChineseNumber(m[1]!);
    return { displayTitle: t, number: n, extra: false };
  }
  m = t.match(/^第([零〇一二三四五六七八九十百千万两]+)[\s　]+(\S.*)$/);
  if (m) {
    const n = parseChineseNumber(m[1]!);
    if (n !== null && n > 0) return { displayTitle: t, number: n, extra: false };
  }
  if (/^番外/.test(t)) {
    const fm = t.match(/^番外第([零〇一二三四五六七八九十百千万两\d]+)章/);
    const n = fm ? parseChineseNumber(fm[1]!) : null;
    return { displayTitle: t, number: n, extra: true };
  }
  return { displayTitle: t, number: null, extra: false };
}

function isPromo(title: string): boolean {
  return /^新书/.test(title) || title.includes('新书') && title.length <= 12;
}

/* ------------------------------------------------------------------ */
/* TOC range fetching                                                  */
/* ------------------------------------------------------------------ */

export interface TocRangePage {
  page: number;
  entries: TocEntry[];
}

export interface TocRangeResult {
  from: number;
  to: number;
  totalPages: number;
  pages: TocRangePage[];
  failedPages: number[];
}

async function mapWithConcurrency<T, R>(
  items: T[],
  limit: number,
  fn: (item: T, index: number) => Promise<R>,
): Promise<R[]> {
  const results = new Array<R>(items.length);
  let next = 0;
  const workers = new Array(Math.min(limit, items.length)).fill(0).map(async () => {
    for (;;) {
      const i = next++;
      if (i >= items.length) return;
      results[i] = await fn(items[i]!, i);
    }
  });
  await Promise.all(workers);
  return results;
}

export async function fetchTocRange(
  from: number,
  to: number,
  opts: FetchOptions = {},
): Promise<TocRangeResult> {
  if (from < 1 || to < from) throw new Error('bad toc range');
  const pages: number[] = [];
  for (let p = from; p <= to; p++) pages.push(p);

  const fetched = await mapWithConcurrency(pages, 3, async (page) => {
    try {
      const { html } = await fetchSourceHtml(buildTocUrl(page), opts);
      const classified = classifyTocItems(parseTocPageHtml(html, page), page);
      return {
        page,
        entries: [...classified.main, ...classified.extras],
        totalPages: parseTocTotalPages(html),
      };
    } catch {
      return { page, entries: null, totalPages: null };
    }
  });

  const ok = fetched.filter((f) => f.entries !== null) as {
    page: number;
    entries: TocEntry[];
    totalPages: number | null;
  }[];
  const failedPages = fetched.filter((f) => f.entries === null).map((f) => f.page);
  const totalPages =
    fetched.find((f) => f.totalPages !== null)?.totalPages ?? BOOK.tocPageFallback;

  return {
    from,
    to,
    totalPages,
    pages: ok.map((f) => ({ page: f.page, entries: f.entries })),
    failedPages,
  };
}

/* ------------------------------------------------------------------ */
/* Networking (受限 + 重试 + 退避)                                      */
/* ------------------------------------------------------------------ */

export type SourceErrorCode =
  | 'timeout'
  | 'network'
  | 'http'
  | 'invalid'
  | 'too_large'
  | 'redirected';

export class SourceError extends Error {
  readonly code: SourceErrorCode;
  readonly retryable: boolean;
  readonly status?: number;

  constructor(message: string, code: SourceErrorCode, retryable: boolean, status?: number) {
    super(message);
    this.name = 'SourceError';
    this.code = code;
    this.retryable = retryable;
    this.status = status;
  }
}

export interface FetchTextResult {
  html: string;
  finalUrl: string;
  bytes: number;
}

export interface FetchOptions {
  fetcher?: FetchLike;
  timeoutMs?: number;
  retries?: number;
  signal?: AbortSignal;
  sleep?: (ms: number) => Promise<void>;
}

const defaultSleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

export async function fetchSourceHtml(
  url: string,
  opts: FetchOptions = {},
): Promise<FetchTextResult> {
  const fetcher = opts.fetcher ?? fetch;
  const timeoutMs = opts.timeoutMs ?? REQUEST_TIMEOUT_MS;
  const maxRetries = opts.retries ?? MAX_RETRIES;
  const sleep = opts.sleep ?? defaultSleep;

  const parsed = new URL(url);
  if (!ALLOWED_HOSTS.has(parsed.hostname)) {
    throw new SourceError('host not allowed', 'invalid', false);
  }

  let lastError: SourceError | null = null;

  // 先 https，失败后降级 http（源站两种协议都提供）
  const candidates = [url];
  if (parsed.protocol === 'https:') {
    const httpUrl = new URL(url);
    httpUrl.protocol = 'http:';
    candidates.push(httpUrl.toString());
  }

  for (const candidate of candidates) {
    for (let attempt = 0; attempt <= maxRetries; attempt++) {
      if (opts.signal?.aborted) throw new SourceError('aborted', 'network', false);
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), timeoutMs);
      const onAbort = () => controller.abort();
      opts.signal?.addEventListener('abort', onAbort, { once: true });
      try {
        const res = await fetcher(candidate, {
          signal: controller.signal,
          redirect: 'follow',
          headers: {
            'User-Agent':
              'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
            Accept: 'text/html,application/xhtml+xml',
            'Accept-Language': 'zh-CN,zh;q=0.9',
            'Cache-Control': 'no-cache',
          },
        });

        if (res.status !== 200) {
          throw new SourceError(`upstream HTTP ${res.status}`, 'http', res.status >= 500, res.status);
        }
        const finalUrl = res.url || candidate;
        const finalHost = new URL(finalUrl).hostname;
        if (!ALLOWED_HOSTS.has(finalHost)) {
          throw new SourceError('redirected off allowed host', 'redirected', false);
        }
        const contentType = res.headers.get('content-type') ?? '';
        if (contentType && !/text\/html|text\/plain/i.test(contentType)) {
          throw new SourceError(`unexpected content-type ${contentType}`, 'invalid', false);
        }
        const buf = await res.arrayBuffer();
        if (buf.byteLength > MAX_HTML_BYTES) {
          throw new SourceError('response too large', 'too_large', false);
        }
        const html = new TextDecoder('utf-8').decode(buf);
        return { html, finalUrl, bytes: buf.byteLength };
      } catch (err) {
        const aborted = err instanceof Error && err.name === 'AbortError';
        const sourceErr =
          err instanceof SourceError
            ? err
            : aborted
              ? new SourceError('request timeout', 'timeout', true)
              : new SourceError(
                  err instanceof Error ? err.message : String(err),
                  'network',
                  true,
                );
        lastError = sourceErr;
        if (!sourceErr.retryable || attempt === maxRetries) break;
        const backoff = Math.min(4000, 400 * 2 ** attempt) + Math.random() * 250;
        await sleep(backoff);
      } finally {
        clearTimeout(timer);
        opts.signal?.removeEventListener('abort', onAbort);
      }
    }
  }

  throw lastError ?? new SourceError('fetch failed', 'network', true);
}

export function parseTotalPagesFromTocHtml(html: string): number | null {
  return parseTocTotalPages(html);
}

export async function fetchTocPage(
  page: number,
  opts: FetchOptions = {},
): Promise<TocPageResult> {
  const { html } = await fetchSourceHtml(buildTocUrl(page), opts);
  const raw = parseTocPageHtml(html, page);
  const totalPages = parseTocTotalPages(html) ?? BOOK.tocPageFallback;
  return { page, totalPages, entries: mergeTocPages([raw]) };
}

export async function fetchChapter(
  id: string,
  opts: FetchOptions = {},
): Promise<ChapterResult> {
  assertChapterId(id);
  const pages: RawChapterPage[] = [];

  // 乐观预取第 2 页：多页章节可省一个往返；单页章节浪费一次快速失败请求
  const optimistic = fetchSourceHtml(buildChapterUrl(id, 1), {
    ...opts,
    retries: 0,
  })
    .then((r) => r)
    .catch(() => null);

  const firstRes = await fetchSourceHtml(buildChapterUrl(id, 0), opts);
  let current = parseChapterPage(firstRes.html, id, 0);
  pages.push(current);

  let guard = 0;
  while (current.nextPageIndex !== null && current.nextPageIndex > current.pageIndex) {
    if (++guard > MAX_CHAPTER_PAGES) break;
    const pageIndex = current.nextPageIndex;
    let html: string | null = null;
    if (pageIndex === 1) {
      const pre = await optimistic;
      html = pre?.html ?? null;
    }
    if (html === null) {
      const res = await fetchSourceHtml(buildChapterUrl(id, pageIndex), opts);
      html = res.html;
    }
    current = parseChapterPage(html, id, pageIndex);
    pages.push(current);
  }

  const paragraphs: string[] = [];
  const seen = new Set<string>();
  for (const p of pages) {
    for (const para of p.paragraphs) {
      if (seen.has(para)) continue;
      seen.add(para);
      paragraphs.push(para);
    }
  }

  const first = pages[0]!;
  const last = pages[pages.length - 1]!;
  const title = pages.map((p) => p.title).find((t) => !/第\d+页/.test(t)) ?? first.title;

  return {
    id,
    title,
    paragraphs,
    prevId: first.prevChapterId,
    nextId: last.nextChapterId,
    pageCount: pages.length,
    charCount: paragraphs.reduce((n, p) => n + p.length, 0),
  };
}
