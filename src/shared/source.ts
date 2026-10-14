/**
 * 书源适配层（万书阁 wanshuge.org，任意书号；默认书《万古神帝》book 36780）。
 *
 * 本模块是纯函数 + 可注入 fetch 的同构代码，同时运行于：
 *   - Cloudflare Worker（生产）
 *   - Node dev server（本地开发/自托管）
 *   - 单元测试（传入假 fetch 与本地 HTML fixture）
 *
 * 只做几件事：构建受限的书源 URL、解析 HTML、合并分页、站内搜索。所有网络请求都由
 * SITE.origin + 校验过的书号/章节号拼出，调用方无法传入任意 URL（见 buildChapterUrl/tocPath）。
 */

export const SITE = {
  origin: 'https://wanshuge.org',
  name: '万书阁',
} as const;

/** 预置书（升级前唯一的书；书号不变以保留旧数据） */
export const DEFAULT_BOOK = {
  id: '36780',
  title: '万古神帝',
  author: '飞天鱼',
} as const;

export const ALLOWED_HOSTS = new Set(['wanshuge.org', 'www.wanshuge.org']);

/** 源站的分页正文上限保护，防止书源结构异常导致无限循环 */
export const MAX_CHAPTER_PAGES = 40;
/** 单个 HTML 响应体上限 */
export const MAX_HTML_BYTES = 4 * 1024 * 1024;
export const REQUEST_TIMEOUT_MS = 10_000;
export const MAX_RETRIES = 3;
/** 源站对突发并发很敏感（会直接重置连接），所有上游请求串行并保持最小间隔 */
export const MIN_REQUEST_GAP_MS = 160;
/** 源站搜索自带约 5 秒的限频（search_time cookie），上游搜索之间至少间隔这么久 */
export const MIN_SEARCH_GAP_MS = 5000;
export const MAX_SEARCH_RESULTS = 50;

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
  /** 全部分页是否都取到（false 时客户端显示“继续加载”） */
  complete: boolean;
  /** 未取到的分页序号（0 起） */
  missingPages: number[];
  /** 源站对本章设了会员门槛，只公开了部分内容（不绕过） */
  restricted?: boolean;
}

/** 书籍信息（目录第 1 页 = 详情页的 og meta） */
export interface BookInfo {
  id: string;
  title: string;
  author: string;
  category?: string;
  status?: string;
  intro?: string;
  latestChapter?: string;
  updatedAt?: string;
  /** 首章 id（og:novel:read_url） */
  firstChapterId?: string;
}

/** 搜索结果条目 */
export interface BookSummary {
  id: string;
  title: string;
  author?: string;
  category?: string;
  latestChapter?: string;
  updated?: string;
  intro?: string;
}

export interface RawChapterPage {
  id: string;
  pageIndex: number;
  title: string;
  paragraphs: string[];
  prevChapterId: string | null;
  nextChapterId: string | null;
  /** 本页已知的最小“下一分页”序号（兼容字段；发现逻辑见 sameChapterPages） */
  nextPageIndex: number | null;
  /** 本页链接中出现的同章分页序号（不含本页，升序去重） */
  sameChapterPages: number[];
  /** 页面自报的分页序号（lastread.set 参数 / 标题“第N页”，0 起）；无法识别为 null */
  declaredPageIndex: number | null;
  /** 页面含源站会员门槛提示 */
  restricted: boolean;
}

/* ------------------------------------------------------------------ */
/* URL building (受限：只允许校验过的书号/章节号拼出的路径)             */
/* ------------------------------------------------------------------ */

const BOOK_ID_RE = /^[1-9]\d{0,8}$/;

export function isValidBookId(id: unknown): id is string {
  return typeof id === 'string' && BOOK_ID_RE.test(id);
}

export function assertBookId(id: string): void {
  if (!isValidBookId(id)) throw new Error('invalid book id');
}

export function tocPath(bookId: string, page: number): string {
  assertBookId(bookId);
  if (!Number.isInteger(page) || page < 1) throw new Error('bad toc page');
  if (page === 1) return `/book/${bookId}/`;
  return `/book/${bookId}/${page - 1}.html`;
}

export function chapterPath(bookId: string, id: string, pageIndex = 0): string {
  assertBookId(bookId);
  assertChapterId(id);
  if (!Number.isInteger(pageIndex) || pageIndex < 0 || pageIndex >= MAX_CHAPTER_PAGES) {
    throw new Error('bad chapter page index');
  }
  if (pageIndex === 0) return `/book/${bookId}_${id}.html`;
  return `/book/${bookId}/${id}_${pageIndex}.html`;
}

export function buildChapterUrl(bookId: string, id: string, pageIndex = 0): string {
  return SITE.origin + chapterPath(bookId, id, pageIndex);
}

export function buildTocUrl(bookId: string, page: number): string {
  return SITE.origin + tocPath(bookId, page);
}

export const SEARCH_URL = `${SITE.origin}/search.html`;

/** 封面图（源站对无封面的书返回默认图） */
export function coverUrl(bookId: string): string {
  assertBookId(bookId);
  return `${SITE.origin}/img/${bookId}.jpg`;
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
  /^后续内容已被隐藏/,
  /^如果您已经是VIP会员/,
];

/** 源站会员门槛提示（检测后标记 restricted，不绕过） */
const RESTRICTED_RE = /后续内容已被隐藏|请升级VIP会员后继续阅读/;

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

/**
 * 部分书的正文直接是 h2.chapter-title 之后的裸 <p>（既无 qsbs 编码也无 #content）。
 * 范围：标题后第一个 read_btn 结束，到下一个 read_btn / 站点推荐段（<p style=…>）之前。
 */
export function extractInlineParagraphs(html: string): string[] {
  const h2 = html.search(/<h2[^>]*class="[^"]*chapter-title[^"]*"[^>]*>/i);
  if (h2 < 0) return [];
  let rest = html.slice(h2);
  const firstBtn = rest.search(/<div[^>]*class="[^"]*read_btn[^"]*"/i);
  if (firstBtn >= 0) {
    const close = rest.indexOf('</div>', firstBtn);
    if (close >= 0) rest = rest.slice(close + 6);
  }
  const ends = [
    rest.search(/<div[^>]*class="[^"]*read_btn[^"]*"/i),
    rest.search(/<p\s+style=/i),
    rest.search(/<div[^>]*class="[^"]*phb_fm/i),
  ].filter((n) => n >= 0);
  if (ends.length > 0) rest = rest.slice(0, Math.min(...ends));
  rest = rest.replace(/<script\b[\s\S]*?<\/script>/gi, ' ');
  const out: string[] = [];
  const re = /<p\b[^>]*>([\s\S]*?)<\/p\s*>/gi;
  let m: RegExpExecArray | null;
  while ((m = re.exec(rest))) {
    if (/<a\b/i.test(m[1]!)) continue;
    const text = cleanText(m[1]!);
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
        // 源站标题形如“章节名(作者)”或缺左括号的“章节名作者)”，作者部分无法可靠切分时只去掉括号段
        .replace(/[(（][^()（）]{1,20}[)）]\s*$/u, '')
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

export interface NavLink {
  href: string;
  text: string;
}

/**
 * 稳健的链接发现：扫描所有 <a> 标签，不做任何“导航文字”白名单过滤。
 * 支持单/双引号、属性任意顺序、未加引号的 href、实体编码。
 * 语义（分页/上一章/下一章）由调用方按 URL 中的 chapter id 判定，
 * 绝不依赖按钮文字——源站“下一章”按钮经常指向同章下一页。
 */
export function extractAnchors(html: string): NavLink[] {
  const links: NavLink[] = [];
  const re = /<a\b([^>]*)>([\s\S]*?)<\/a>/gi;
  let m: RegExpExecArray | null;
  while ((m = re.exec(html))) {
    const attrs = m[1]!;
    const hm = attrs.match(/href\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/i);
    if (!hm) continue;
    const href = decodeEntities(hm[1] ?? hm[2] ?? hm[3] ?? '');
    if (!href) continue;
    const text = stripTags(m[2]!).replace(/\s+/g, '');
    links.push({ href, text });
  }
  return links;
}

/** 去掉 script/style/注释后再扫描链接，避免把 JS 字符串误认成 <a> */
function extractContentAnchors(html: string): NavLink[] {
  const cleaned = html
    .replace(/<script\b[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style\b[\s\S]*?<\/style>/gi, ' ')
    .replace(/<!--[\s\S]*?-->/g, ' ');
  return extractAnchors(cleaned);
}

export interface ParsedChapterLink {
  id: string;
  pageIndex: number;
}

/**
 * 解析任意指向本书章节页面的链接：相对/绝对/协议相对、单双引号、query/hash。
 * 只接受 wanshuge.org / www.wanshuge.org 上的本书（bookId）路径，
 * 其它（含站内其他书的推荐链接）一律返回 null（安全边界不变，后端仍不是任意 URL 代理）。
 */
export function parseChapterLink(bookId: string, href: string): ParsedChapterLink | null {
  if (!isValidBookId(bookId)) return null;
  let h = (href ?? '').trim();
  if (!h) return null;
  const hash = h.indexOf('#');
  if (hash >= 0) h = h.slice(0, hash);
  if (/^https?:\/\//i.test(h) || h.startsWith('//')) {
    try {
      const u = new URL(/^https?:\/\//i.test(h) ? h : `https:${h}`);
      if (!ALLOWED_HOSTS.has(u.hostname)) return null;
      h = u.pathname;
    } catch {
      return null;
    }
  }
  const q = h.indexOf('?');
  if (q >= 0) h = h.slice(0, q);

  let m = h.match(new RegExp(`^/book/${bookId}_(\\d+)\\.html$`));
  if (m) return { id: m[1]!, pageIndex: 0 };
  m = h.match(new RegExp(`^/book/${bookId}/(\\d+)(?:_(\\d+))?\\.html$`));
  if (m) {
    const digits = m[1]!;
    // /book/{bookId}/{n}.html 同时是“目录第 n+1 页”（页码最多几百）与旧版章节首页形式。
    // 章节 id 在本源站均为 7 位以上；按位数区分，避免把目录页误认成章节。
    if (!m[2] && digits.length < 6) return null;
    return { id: digits, pageIndex: m[2] ? parseInt(m[2], 10) : 0 };
  }
  return null;
}

/**
 * 页面自报的分页序号：优先 lastread.set('36780','<id>',...,'<pageIndex>',...)，
 * 其次标题“（第N页）”。源站对超出末尾的分页会回环到第一页，
 * 该字段可与请求的 pageIndex 比对识别回环。
 */
export function extractDeclaredPageIndex(html: string): number | null {
  const lr = html.match(/lastread\.set\([^)]*\)/i);
  if (lr) {
    const args = lr[0].match(/'([^']*)'/g);
    if (args && args.length >= 5) {
      const n = parseInt(args[4]!.slice(1, -1), 10);
      if (Number.isInteger(n)) return n;
    }
  }
  const h2 = html.match(/chapter-title[^>]*>([\s\S]*?)<\/h2>/i);
  const mark = h2?.[1] ?? html.match(/<title>([\s\S]*?)<\/title>/i)?.[1] ?? '';
  const pm = mark.match(/第(\d+)页/);
  if (pm) return parseInt(pm[1]!, 10) - 1;
  return null;
}

/* ------------------------------------------------------------------ */
/* Chapter parsing                                                     */
/* ------------------------------------------------------------------ */

/**
 * 解析单个物理页。链接语义完全由 URL 中的 chapter id 判定：
 *  - 同 id = 同章分页（无论按钮文字是“上一章/下一章/上一页/下一页”）
 *  - 不同 id 且 pageIndex 0 = 真正相邻章节（文字仅用于区分方向，不用于认定语义）
 */
export function parseChapterPage(
  bookId: string,
  html: string,
  expectedId: string,
  pageIndex: number,
): RawChapterPage {
  const title = extractTitle(html);
  let paragraphs = extractEncodedParagraphs(html);
  if (paragraphs.length === 0) paragraphs = extractPlainParagraphs(html);
  if (paragraphs.length === 0) paragraphs = extractInlineParagraphs(html);
  const restricted = paragraphs.some((p) => RESTRICTED_RE.test(p));

  const cleaned: string[] = [];
  for (const p of paragraphs) {
    const t = p.trim();
    if (!t || isNoise(t)) continue;
    if (t === title) continue;
    // 源站同一页可能真的重复同一句话（如连声惊呼），必须原样保留，不得去重
    cleaned.push(t);
  }

  const sameChapterPages = new Set<number>();
  const prevCandidates: string[] = [];
  const nextCandidates: string[] = [];
  let otherFirstId: string | null = null;

  for (const link of extractContentAnchors(html)) {
    const parsed = parseChapterLink(bookId, link.href);
    if (!parsed) continue;
    if (parsed.id === expectedId) {
      // 同 chapter id = 同章分页，文字说什么都不改变这一点
      if (parsed.pageIndex !== pageIndex) sameChapterPages.add(parsed.pageIndex);
      continue;
    }
    // 不同 chapter id：只收 chapter 首页（pageIndex 0）作为相邻章节候选
    if (parsed.pageIndex !== 0) continue;
    if (/^(上一章|上一页)$/.test(link.text)) {
      prevCandidates.push(parsed.id);
    } else if (/^(下一章|下一页)$/.test(link.text)) {
      nextCandidates.push(parsed.id);
    } else if (otherFirstId === null) {
      otherFirstId = parsed.id;
    }
  }

  const sortedSame = [...sameChapterPages].sort((a, b) => a - b);
  const nextPageIndex = sortedSame.find((n) => n > pageIndex) ?? null;
  const prevChapterId = prevCandidates[0] ?? null;
  const nextChapterId =
    nextCandidates[0] ?? (prevCandidates.length === 0 && nextCandidates.length === 0 ? otherFirstId : null);

  return {
    id: expectedId,
    pageIndex,
    title: title || `章节 ${expectedId}`,
    paragraphs: cleaned,
    prevChapterId,
    nextChapterId,
    nextPageIndex,
    sameChapterPages: sortedSame,
    declaredPageIndex: extractDeclaredPageIndex(html),
    restricted,
  };
}

/* ------------------------------------------------------------------ */
/* TOC parsing                                                         */
/* ------------------------------------------------------------------ */

function extractListItems(bookId: string, block: string): TocItemRaw[] {
  const items: TocItemRaw[] = [];
  const re =
    /<a\b[^>]*href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/gi;
  let m: RegExpExecArray | null;
  while ((m = re.exec(block))) {
    const href = m[1]!;
    const parsed = parseChapterLink(bookId, href);
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
export function parseTocPageHtml(bookId: string, html: string, _page: number): TocItemRaw[] {
  const items: TocItemRaw[] = [];
  const re =
    /<ul[^>]+class="[^"]*\b(?:ph_list|section-list)\b[^"]*"[^>]*>([\s\S]*?)<\/ul>/gi;
  let m: RegExpExecArray | null;
  while ((m = re.exec(html))) {
    items.push(...extractListItems(bookId, m[1]!));
  }
  if (items.length > 0) return items;

  // 兜底：整页扫描
  return extractListItems(bookId, html);
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

/** 重传判定：同标题条目的 id 相差超过此值视为源站后期重新上传（正文批次内 id 连续，跨度约 1.4 万） */
const REUPLOAD_ID_GAP = 1_000_000;

/**
 * 合并多页目录（去重、排序、番外归位）：
 *  - 正文按上传顺序（id 递增）排列，与源站“下一章”链接一致。源站章节号多处回退/错号
 *    （例如第3409章之后从“第三千三百八十九章”重新编号），按章节号排序会把两段剧情交错。
 *  - 同号同标题只留最早的一份；同号的“分节阅读第N节”占位让位给有标题的版本（保持占位的位置）。
 *  - 结尾的整批重传（同标题、id 远大于原版、占位标题）丢弃。
 *  - 番外按番外序号排序，附在最后
 */
export function mergeTocEntries(pages: TocEntry[][]): TocEntry[] {
  const unique = new Map<string, TocEntry>();
  for (const page of pages) {
    for (const entry of page) if (!entry.extra && !unique.has(entry.id)) unique.set(entry.id, entry);
  }
  const ordered = [...unique.values()].sort((a, b) => a.id.length - b.id.length || (a.id < b.id ? -1 : 1));

  const main: TocEntry[] = [];
  const byNumber = new Map<number, number[]>(); // 章节号 → main 下标
  const firstIdByTitle = new Map<string, number>();
  let maxTitledNumber = 0;
  for (const entry of ordered) {
    const number = entry.number ?? 0;
    const rest = restTitle(entry);
    const same = byNumber.get(number) ?? [];
    if (rest === '') {
      // 占位标题：已有同号章节，或更大章号早已发布（结尾重传批次）
      if (same.length > 0 || maxTitledNumber > number) continue;
    } else {
      const firstId = firstIdByTitle.get(rest);
      if (same.some((i) => restTitle(main[i]!) === rest)) continue;
      if (firstId !== undefined && Number(entry.id) - firstId > REUPLOAD_ID_GAP) continue;
      if (firstId === undefined) firstIdByTitle.set(rest, Number(entry.id));
      maxTitledNumber = Math.max(maxTitledNumber, number);
      const placeholder = same.find((i) => restTitle(main[i]!) === '');
      if (placeholder !== undefined) {
        main[placeholder] = entry;
        continue;
      }
    }
    same.push(main.length);
    byNumber.set(number, same);
    main.push(entry);
  }

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

export function parseTocTotalPages(bookId: string, html: string): number | null {
  assertBookId(bookId);
  const m = html.match(new RegExp(`/book/${bookId}/(\\d+)\\.html"[^>]*>第[\\d\\-]+章`));
  const select = html.match(/<select[\s\S]*?<\/select>/i);
  if (select) {
    const options = select[0].match(new RegExp(`/book/${bookId}/(\\d+)\\.html`, 'g'));
    if (options && options.length > 0) {
      const max = Math.max(
        ...options.map((o) => parseInt(o.match(/\/(\d+)\.html/)![1]!, 10)),
      );
      return max + 1;
    }
    // 分页下拉只有“第1-100章”一项（≤100 章的书）：只有 1 页
    if (new RegExp(`/book/${bookId}/["']`).test(select[0])) return 1;
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
  /** 0 = 本次未能确定总页数（客户端保留原值） */
  totalPages: number;
  pages: TocRangePage[];
  failedPages: number[];
  /** 范围包含第 1 页（详情页）时附带书籍信息 */
  book?: BookInfo;
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
  bookId: string,
  from: number,
  to: number,
  opts: FetchOptions = {},
): Promise<TocRangeResult> {
  assertBookId(bookId);
  if (from < 1 || to < from) throw new Error('bad toc range');
  const pages: number[] = [];
  for (let p = from; p <= to; p++) pages.push(p);

  const fetched = await mapWithConcurrency(pages, 2, async (page) => {
    if (opts.signal?.aborted) throw new DOMException('aborted', 'AbortError');
    try {
      const { html } = await fetchSourceHtml(buildTocUrl(bookId, page), { ...opts, highPriority: false });
      const classified = classifyTocItems(parseTocPageHtml(bookId, html, page), page);
      return {
        page,
        entries: [...classified.main, ...classified.extras],
        totalPages: parseTocTotalPages(bookId, html),
        book: page === 1 ? parseBookInfo(bookId, html) : null,
      };
    } catch {
      return { page, entries: null, totalPages: null, book: null };
    }
  });

  const ok = fetched.filter((f) => f.entries !== null) as {
    page: number;
    entries: TocEntry[];
    totalPages: number | null;
  }[];
  const failedPages = fetched.filter((f) => f.entries === null).map((f) => f.page);
  const totalPages = fetched.find((f) => f.totalPages !== null)?.totalPages ?? 0;
  const book = fetched.find((f) => f.book)?.book ?? undefined;

  const result: TocRangeResult = {
    from,
    to,
    totalPages,
    pages: ok.map((f) => ({ page: f.page, entries: f.entries })),
    failedPages,
  };
  if (book) result.book = book;
  return result;
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

interface UpstreamJob {
  run: () => void;
  high: boolean;
}

const upstreamJobs: UpstreamJob[] = [];
let upstreamActive = 0;
let upstreamLastStart = 0;
let upstreamTimer: ReturnType<typeof setTimeout> | null = null;
/** 并发上限（避免队头阻塞）与请求最小间隔（避免触发源站重置）同时生效 */
const UPSTREAM_CONCURRENCY = 2;

function pumpUpstream(): void {
  if (upstreamTimer) {
    clearTimeout(upstreamTimer);
    upstreamTimer = null;
  }
  while (upstreamActive < UPSTREAM_CONCURRENCY && upstreamJobs.length > 0) {
    const now = Date.now();
    const wait = upstreamLastStart + MIN_REQUEST_GAP_MS - now;
    if (wait > 0) {
      upstreamTimer = setTimeout(pumpUpstream, wait);
      return;
    }
    // 章节请求优先；目录/预取让路，避免阅读被目录加载阻塞
    let index = upstreamJobs.findIndex((j) => j.high);
    if (index < 0) index = 0;
    const job = upstreamJobs.splice(index, 1)[0]!;
    upstreamActive++;
    upstreamLastStart = Date.now();
    job.run();
  }
}

/** 排队 + 限速 + 优先级；prefetch/toc 等后台请求应传 high=false */
export function enqueueUpstream<T>(task: () => Promise<T>, high = true): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    upstreamJobs.push({
      high,
      run: () => {
        task()
          .then(resolve, reject)
          .finally(() => {
            upstreamActive--;
            pumpUpstream();
          });
      },
    });
    pumpUpstream();
  });
}

export interface FetchTextResult {
  html: string;
  finalUrl: string;
  bytes: number;
}

export interface FetchOptions {
  /** 后台请求（目录、预取）传 false，让章节请求优先 */
  highPriority?: boolean;
  fetcher?: FetchLike;
  timeoutMs?: number;
  retries?: number;
  signal?: AbortSignal;
  sleep?: (ms: number) => Promise<void>;
  /** 非空时以 POST application/x-www-form-urlencoded 发送（仅用于站内搜索） */
  form?: string;
}

const defaultSleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

/** 三端（Worker/Node/Android）统一用桌面 UA，拿到同一套页面模板 */
export const SOURCE_USER_AGENT =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36';

export function fetchSourceHtml(url: string, opts: FetchOptions = {}): Promise<FetchTextResult> {
  return enqueueUpstream(() => fetchSourceHtmlDirect(url, opts), opts.highPriority !== false);
}

async function fetchSourceHtmlDirect(
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
        const headers: Record<string, string> = {
          'User-Agent': SOURCE_USER_AGENT,
          Accept: 'text/html,application/xhtml+xml',
          'Accept-Language': 'zh-CN,zh;q=0.9',
          'Cache-Control': 'no-cache',
        };
        const init: RequestInit = { signal: controller.signal, redirect: 'follow', headers };
        if (opts.form !== undefined) {
          init.method = 'POST';
          init.body = opts.form;
          headers['Content-Type'] = 'application/x-www-form-urlencoded';
        }
        const res = await fetcher(candidate, init);

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
        if (opts.signal?.aborted) throw new DOMException('aborted', 'AbortError');
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

export async function fetchTocPage(
  bookId: string,
  page: number,
  opts: FetchOptions = {},
): Promise<TocPageResult> {
  const { html } = await fetchSourceHtml(buildTocUrl(bookId, page), opts);
  const raw = parseTocPageHtml(bookId, html, page);
  const totalPages = parseTocTotalPages(bookId, html) ?? 0;
  return { page, totalPages, entries: mergeTocPages([raw]) };
}

/* ------------------------------------------------------------------ */
/* Chapter fetching（frontier + 终章证据）                              */
/* ------------------------------------------------------------------ */

/** 单页抓取结果分类：404 与回环都是“该分页不存在”的积极证据 */
type PageFetchOutcome =
  | { kind: 'ok'; page: RawChapterPage }
  | { kind: 'not_found' }
  | { kind: 'failed'; error: SourceError };

/**
 * 抓取章节全部分页：
 *  - 从第 0 页出发，把每页发现的同 chapter id 分页链接加入 frontier，
 *    全部抓完后按 pageIndex 排序合并；发现顺序不再依赖单条“下一页”链，
 *    也不依赖按钮文字（源站“下一章”经常指向同章下一页）。
 *  - 源站对超出末尾的分页不回 404，而是回环到第一页（内容重复、
 *    lastread/标题自报页码回退）；这类响应不当作真实分页，并作为终章证据。
 *  - complete 只可能来自积极证据：末页存在指向不同 chapter id 的真正下一章，
 *    或探针确认不存在更大的同章分页（回环/404）。绝不因
 *    “没发现分页”或“missingPages 为空”而置 true。
 */
export async function fetchChapter(
  bookId: string,
  id: string,
  opts: FetchOptions = {},
): Promise<ChapterResult> {
  assertBookId(bookId);
  assertChapterId(id);
  const fetched = new Map<number, RawChapterPage>();
  const failed = new Set<number>();
  const absent = new Set<number>(); // 确认不存在/回环的分页序号
  const seen = new Set<number>();
  const discovered = new Set<number>();
  const signatures = new Set<string>();
  const queue: number[] = [0];
  let probePending = false;
  let terminal = false;
  let firstError: SourceError | null = null;

  const fetchOne = async (pageIndex: number): Promise<PageFetchOutcome> => {
    try {
      const res = await fetchSourceHtml(buildChapterUrl(bookId, id, pageIndex), opts);
      return { kind: 'ok', page: parseChapterPage(bookId, res.html, id, pageIndex) };
    } catch (err) {
      const sourceErr =
        err instanceof SourceError
          ? err
          : new SourceError(err instanceof Error ? err.message : String(err), 'network', true);
      if (sourceErr.code === 'http' && sourceErr.status === 404) {
        return { kind: 'not_found' };
      }
      return { kind: 'failed', error: sourceErr };
    }
  };

  const isLoopback = (page: RawChapterPage): boolean => {
    if (page.paragraphs.length === 0) return false;
    const sig = page.paragraphs.join('\u0001');
    return (
      signatures.has(sig) ||
      (page.declaredPageIndex !== null && page.declaredPageIndex < page.pageIndex)
    );
  };

  const absorb = (page: RawChapterPage): void => {
    if (page.paragraphs.length === 0) {
      // 有页面但提取不到正文：按失败处理，稍后可重试
      failed.add(page.pageIndex);
      return;
    }
    signatures.add(page.paragraphs.join('\u0001'));
    fetched.set(page.pageIndex, page);
    for (const pi of page.sameChapterPages) {
      discovered.add(pi);
      if (pi >= 0 && pi < MAX_CHAPTER_PAGES && !seen.has(pi) && !queue.includes(pi)) {
        queue.push(pi);
      }
    }
  };

  while (fetched.size < MAX_CHAPTER_PAGES) {
    if (opts.signal?.aborted) throw new DOMException('aborted', 'AbortError');
    if (queue.length === 0) {
      const maxIdx = fetched.size > 0 ? Math.max(...fetched.keys()) : -1;
      if (maxIdx < 0) break; // 第 0 页都没拿到：由 firstError 抛出或按失败结果返回
      const last = fetched.get(maxIdx)!;

      // 终章证据 A：末页存在指向不同 chapter id 的真正下一章。
      // 例外：maxIdx === 0 时仍要探针——源站 CDN 曾以旧模板提供第一页
      //（“下一章”直连下一章 id，而 _1 分页实际已存在），只有探针能区分。
      if (last.nextChapterId !== null && maxIdx > 0) {
        terminal = true;
        break;
      }

      // 终章证据 B：已知不存在 maxIdx+1（回环或 404）也是积极证据
      if (absent.has(maxIdx + 1)) {
        terminal = true;
        break;
      }

      // 终章证据 C：有限探针——每章最多探一次“下一页”。
      // 跳过已尝试失败的序号（fetchOne 内部已重试）；失败保持 unknown。
      if (probePending) {
        terminal = false;
        break;
      }
      let probeIdx = maxIdx + 1;
      while (
        probeIdx < MAX_CHAPTER_PAGES &&
        (seen.has(probeIdx) || failed.has(probeIdx) || absent.has(probeIdx))
      ) {
        probeIdx++;
      }
      if (probeIdx >= MAX_CHAPTER_PAGES) {
        terminal = false;
        break;
      }
      probePending = true;
      queue.push(probeIdx);
      continue;
    }

    queue.sort((a, b) => a - b);
    const pageIndex = queue.shift()!;
    if (seen.has(pageIndex) || pageIndex < 0 || pageIndex >= MAX_CHAPTER_PAGES) continue;
    seen.add(pageIndex);
    const outcome = await fetchOne(pageIndex);
    if (outcome.kind === 'ok') {
      if (isLoopback(outcome.page)) {
        // 回环：源站把不存在的分页重发成更早的页面。该分页不存在（积极证据），
        // 但页面里的链接仍可能指向未知分页，继续吸收链接。
        absent.add(pageIndex);
        for (const pi of outcome.page.sameChapterPages) {
          discovered.add(pi);
          if (pi >= 0 && pi < MAX_CHAPTER_PAGES && !seen.has(pi) && !queue.includes(pi)) {
            queue.push(pi);
          }
        }
        continue;
      }
      absorb(outcome.page);
    } else if (outcome.kind === 'not_found') {
      absent.add(pageIndex);
    } else {
      failed.add(pageIndex);
      if (firstError === null) firstError = outcome.error;
    }
  }

  // 所有已知分页都取到了但没有终章证据 → 不得标 complete
  if (fetched.size >= MAX_CHAPTER_PAGES) terminal = false;
  const missing = new Set<number>(failed);
  for (const pi of discovered) {
    if (!fetched.has(pi) && !absent.has(pi) && pi < MAX_CHAPTER_PAGES) missing.add(pi);
  }

  if (fetched.size === 0) {
    if (firstError) throw firstError;
    // 首页有响应但没有任何正文/分页信息：按不完整返回，等待后续重验
    return {
      id,
      title: `章节 ${id}`,
      paragraphs: [],
      prevId: null,
      nextId: null,
      pageCount: 0,
      charCount: 0,
      complete: false,
      missingPages: [...missing].sort((a, b) => a - b),
    };
  }

  // 只消除两种真实重复：整页重发、以及分页处“上页末段 = 下页首段”的重叠。
  // 其余重复句属于原文，必须保留。回环页在吸收阶段已排除，不会进入合并。
  const sorted = [...fetched.values()].sort((a, b) => a.pageIndex - b.pageIndex);
  const paragraphs: string[] = [];
  const pageSignatures = new Set<string>();
  for (const p of sorted) {
    const sig = p.paragraphs.join('\u0001');
    if (p.paragraphs.length > 0 && pageSignatures.has(sig)) continue;
    pageSignatures.add(sig);
    for (const para of p.paragraphs) {
      if (paragraphs.length > 0 && paragraphs[paragraphs.length - 1] === para) continue;
      paragraphs.push(para);
    }
  }

  const first = sorted[0]!;
  const last = sorted[sorted.length - 1]!;
  const title = sorted.map((p) => p.title).find((t) => !/第\d+页/.test(t)) ?? first.title;

  const result: ChapterResult = {
    id,
    title,
    paragraphs,
    prevId: first.prevChapterId,
    nextId: last.nextChapterId ?? first.nextChapterId,
    pageCount: sorted.length,
    charCount: paragraphs.reduce((n, p) => n + p.length, 0),
    complete: terminal && missing.size === 0,
    missingPages: [...missing].sort((a, b) => a - b),
  };
  if (sorted.some((p) => p.restricted)) result.restricted = true;
  return result;
}

/* ------------------------------------------------------------------ */
/* 书籍信息（详情页 og meta）                                           */
/* ------------------------------------------------------------------ */

function metaContent(html: string, property: string): string | undefined {
  const esc = property.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const m =
    html.match(new RegExp(`<meta[^>]+property=["']${esc}["'][^>]*content=["']([^"']*)["']`, 'i')) ??
    html.match(new RegExp(`<meta[^>]+content=["']([^"']*)["'][^>]*property=["']${esc}["']`, 'i'));
  if (!m) return undefined;
  const v = cleanText(m[1]!).replace(/\n+/g, ' ').trim();
  return v || undefined;
}

/** 去掉简介里的站点套话（“三秒记住本站…”“是由作者：X所著，万书阁免费提供…”） */
export function cleanIntro(raw: string, title = ''): string {
  let t = raw
    .replace(/三秒记住本站[\s\S]*$/u, '')
    .replace(/[^。！？\n]*万书阁免费提供[^。！？\n]*[。！？]?/gu, '')
    .replace(/如果您喜欢[^，,]*[，,]别忘记分享给朋友[.。…]*/gu, '')
    .replace(/^关于[^：:]{1,40}[：:]/u, '')
    .trim();
  if (title && t === title) t = '';
  return t;
}

export function parseBookInfo(bookId: string, html: string): BookInfo | null {
  assertBookId(bookId);
  const title = metaContent(html, 'og:novel:book_name') ?? metaContent(html, 'og:title');
  if (!title) return null;
  const info: BookInfo = {
    id: bookId,
    title,
    author: metaContent(html, 'og:novel:author') ?? '',
  };
  const category = metaContent(html, 'og:novel:category');
  const status = metaContent(html, 'og:novel:status');
  const latest = metaContent(html, 'og:novel:latest_chapter_name');
  const updated = metaContent(html, 'og:novel:update_time');
  const desc = metaContent(html, 'og:description');
  const readUrl = metaContent(html, 'og:novel:read_url');
  if (category) info.category = category;
  if (status) info.status = status;
  if (latest) info.latestChapter = latest;
  if (updated) info.updatedAt = updated;
  if (desc) {
    const intro = cleanIntro(desc, title);
    if (intro) info.intro = intro;
  }
  if (readUrl) {
    const link = parseChapterLink(bookId, readUrl);
    if (link && link.pageIndex === 0) info.firstChapterId = link.id;
  }
  return info;
}

/* ------------------------------------------------------------------ */
/* 站内搜索（POST /search.html，字段 s）                                 */
/* ------------------------------------------------------------------ */

/** 规范化搜索词；不合法时返回 null */
export function normalizeSearchQuery(raw: unknown): string | null {
  if (typeof raw !== 'string') return null;
  // eslint-disable-next-line no-control-regex
  if (/[\u0000-\u001f\u007f]/.test(raw)) return null;
  const q = raw.replace(/\s+/g, ' ').trim();
  if (q.length < 1 || [...q].length > 30) return null;
  return q;
}

function bookIdFromHref(href: string): string | null {
  const m = href.match(/^(?:https?:\/\/(?:www\.)?wanshuge\.org)?\/book\/([1-9]\d{0,8})\/?$/i);
  return m ? m[1]! : null;
}

function textOf(fragment: string): string {
  return cleanText(fragment).replace(/\n+/g, ' ').trim();
}

/**
 * 解析搜索结果页。支持两套模板：
 *  - 桌面：ul#novel-search > li（s1 分类 / s2 书名 / s3 作者 / s4 最新章 / s5 日期）
 *  - 移动：a.list[href=/book/{id}/]（h4 书名 / .info 简介）
 * 页面里找不到结果容器（模板变化/被拦截）时返回 null，由调用方按错误处理。
 */
export function parseSearchResults(html: string): BookSummary[] | null {
  const out: BookSummary[] = [];
  const seen = new Set<string>();
  const push = (b: BookSummary) => {
    if (seen.has(b.id) || out.length >= MAX_SEARCH_RESULTS) return;
    seen.add(b.id);
    out.push(b);
  };

  const desktop = html.match(/<ul[^>]*id=["']novel-search["'][^>]*>([\s\S]*?)<\/ul>/i);
  if (desktop) {
    const liRe = /<li\b[^>]*>([\s\S]*?)<\/li>/gi;
    let m: RegExpExecArray | null;
    while ((m = liRe.exec(desktop[1]!))) {
      const li = m[1]!;
      const span = (cls: string) =>
        li.match(new RegExp(`<span[^>]*class=["']${cls}["'][^>]*>([\\s\\S]*?)<\\/span>`, 'i'))?.[1];
      const s2 = span('s2');
      if (!s2) continue;
      const a = s2.match(/<a[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/i);
      if (!a) continue;
      const id = bookIdFromHref(decodeEntities(a[1]!));
      const title = textOf(a[2]!);
      if (!id || !title) continue;
      const b: BookSummary = { id, title };
      const author = span('s3') && textOf(span('s3')!);
      const category = span('s1') && textOf(span('s1')!).replace(/^\[|\]$/g, '');
      const latest = span('s4') && textOf(span('s4')!);
      const updated = span('s5') && textOf(span('s5')!);
      if (author) b.author = author;
      if (category) b.category = category;
      if (latest) b.latestChapter = latest;
      if (updated) b.updated = updated;
      push(b);
    }
    return out;
  }

  // 移动模板：从“搜索结果”标题到下一个版块（“完结小说推荐”等）之间
  const head = html.search(/<h2[^>]*>[^<]*搜索结果/);
  if (head < 0) return null;
  let region = html.slice(head);
  const next = region.slice(1).search(/<div[^>]*class=["'][^"']*\bindex_qb\b/i);
  if (next >= 0) region = region.slice(0, next + 1);
  const aRe = /<a\b([^>]*class=["'][^"']*\blist\b[^"']*["'][^>]*)>([\s\S]*?)<\/a>/gi;
  let m: RegExpExecArray | null;
  while ((m = aRe.exec(region))) {
    const attrs = m[1]!;
    const href = attrs.match(/href=["']([^"']+)["']/i)?.[1];
    const id = href ? bookIdFromHref(decodeEntities(href)) : null;
    if (!id) continue;
    const body = m[2]!;
    const title =
      textOf(body.match(/<h4[^>]*>([\s\S]*?)<\/h4>/i)?.[1] ?? '') ||
      decodeEntities(attrs.match(/title=["']([^"']*)["']/i)?.[1] ?? '').trim();
    if (!title) continue;
    const b: BookSummary = { id, title };
    const info = body.match(/<div[^>]*class=["']info["'][^>]*>([\s\S]*?)<\/div>/i)?.[1];
    if (info) {
      const intro = cleanIntro(textOf(info), title);
      if (intro) b.intro = intro;
    }
    push(b);
  }
  return out;
}

let lastSearchAt = 0;
let searchChain: Promise<void> = Promise.resolve();

/** 测试用：重置搜索闸门 */
export function resetSearchGate(): void {
  lastSearchAt = 0;
  searchChain = Promise.resolve();
}

/**
 * 站内搜索：经统一上游队列（高优先级）发送 POST；
 * 另有串行闸门保证两次上游搜索至少间隔 MIN_SEARCH_GAP_MS（源站自带限频）。
 */
export async function searchBooks(query: string, opts: FetchOptions = {}): Promise<BookSummary[]> {
  const q = normalizeSearchQuery(query);
  if (q === null) throw new SourceError('invalid search query', 'invalid', false);
  const sleep = opts.sleep ?? defaultSleep;
  const gate = searchChain.then(async () => {
    const wait = lastSearchAt + MIN_SEARCH_GAP_MS - Date.now();
    if (wait > 0) await sleep(wait);
    lastSearchAt = Date.now();
  });
  searchChain = gate.catch(() => undefined);
  await gate;
  const { html } = await fetchSourceHtml(SEARCH_URL, {
    ...opts,
    highPriority: true,
    form: `s=${encodeURIComponent(q)}`,
  });
  const results = parseSearchResults(html);
  if (results === null) throw new SourceError('unexpected search page', 'invalid', true);
  return results;
}
