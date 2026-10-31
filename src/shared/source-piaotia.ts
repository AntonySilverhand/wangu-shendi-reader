/**
 * 书源适配：飘天文学（www.piaotia.com，jieqi 模板，GBK 编码，正文为纯 HTML）。
 *
 * 所有请求都由 PIAOTIA.origin + 校验过的书号/章节号拼出（{a} = ⌊n/1000⌋）：
 *   详情页  /bookinfo/{a}/{n}.html
 *   目录页  /html/{a}/{n}/        （单页列出全部章节：div.centent ul li > a[href={章节号}.html]）
 *   章节页  /html/{a}/{n}/{章节号}.html（正文为 H1 之后的 &nbsp; 缩进文本 + <br>，不分页）
 *   搜索    POST /modules/article/search.php（searchtype=articlename&searchkey=GBK 编码书名）
 *   封面    /files/article/image/{a}/{n}/{n}s.jpg
 *
 * 书号在本应用中带前缀：`piaotia-{n}`。该站前置 Cloudflare（目前不拦截）；遇到人机验证时
 * fetch 层抛 code='blocked'，不做任何绕过。
 */
import {
  assertChapterId,
  cleanIntro,
  cleanText,
  decodeEntities,
  divInner,
  fetchSourceHtml,
  normalizeSearchQuery,
  normalizeTitle,
  SourceError,
  stripTags,
  MAX_SEARCH_RESULTS,
  type BookInfo,
  type BookSummary,
  type ChapterResult,
  type FetchOptions,
  type TocEntry,
  type TocRangeResult,
} from './source.ts';
import { encodeGbkComponent } from './source-69shuba.ts';

export const PIAOTIA = {
  id: 'piaotia',
  name: '飘天文学',
  origin: 'https://www.piaotia.com',
} as const;

export const PIAOTIA_HOSTS: ReadonlySet<string> = new Set(['www.piaotia.com', 'piaotia.com']);
export const PIAOTIA_SEARCH_URL = `${PIAOTIA.origin}/modules/article/search.php`;
export const PIAOTIA_BOOK_PREFIX = 'piaotia-';
/** jieqi 搜索自带限频（jieqiArticlesearchTime cookie），两次上游搜索之间至少间隔这么久 */
export const PIAOTIA_MIN_SEARCH_GAP_MS = 5000;

const NUM_RE = /^[1-9]\d{0,8}$/;
const BOOK_ID_RE = /^piaotia-([1-9]\d{0,8})$/;

export function isPiaotiaBookId(id: unknown): id is string {
  return typeof id === 'string' && BOOK_ID_RE.test(id);
}

export function piaotiaBookId(num: string): string {
  if (!NUM_RE.test(num)) throw new Error('invalid piaotia book number');
  return PIAOTIA_BOOK_PREFIX + num;
}

export function piaotiaNumber(bookId: string): string {
  const m = BOOK_ID_RE.exec(bookId);
  if (!m) throw new Error('invalid piaotia book id');
  return m[1]!;
}

function shard(n: string): number {
  return Math.floor(Number(n) / 1000);
}

/* ------------------------------------------------------------------ */
/* URL building（受限：只允许校验过的书号/章节号拼出的路径）             */
/* ------------------------------------------------------------------ */

export function piaotiaInfoUrl(bookId: string): string {
  const n = piaotiaNumber(bookId);
  return `${PIAOTIA.origin}/bookinfo/${shard(n)}/${n}.html`;
}

export function piaotiaCatalogUrl(bookId: string): string {
  const n = piaotiaNumber(bookId);
  return `${PIAOTIA.origin}/html/${shard(n)}/${n}/`;
}

export function piaotiaChapterUrl(bookId: string, chapterId: string): string {
  const n = piaotiaNumber(bookId);
  assertChapterId(chapterId);
  return `${PIAOTIA.origin}/html/${shard(n)}/${n}/${chapterId}.html`;
}

export function piaotiaCoverUrl(bookId: string): string {
  const n = piaotiaNumber(bookId);
  return `${PIAOTIA.origin}/files/article/image/${shard(n)}/${n}/${n}s.jpg`;
}

/** href → 站内路径（相对链接按本书目录解析；外站返回 null） */
function piaotiaPath(bookId: string, href: string): string | null {
  let h = decodeEntities((href ?? '').trim());
  if (!h || /^(javascript|mailto):/i.test(h)) return null;
  const hash = h.indexOf('#');
  if (hash >= 0) h = h.slice(0, hash);
  try {
    const n = piaotiaNumber(bookId);
    const u = new URL(h, `${PIAOTIA.origin}/html/${shard(n)}/${n}/`);
    if (!PIAOTIA_HOSTS.has(u.hostname) || u.search) return null;
    return u.pathname;
  } catch {
    return null;
  }
}

/** 只接受本书的章节页 /html/{a}/{n}/{id}.html；目录页（index.html、./）、其它书、外站返回 null */
export function parsePiaotiaChapterLink(bookId: string, href: string): string | null {
  if (!isPiaotiaBookId(bookId)) return null;
  const path = piaotiaPath(bookId, href);
  const m = path?.match(/^\/html\/(\d{1,6})\/([1-9]\d{0,8})\/(\d{1,12})\.html$/);
  if (!m || m[2] !== piaotiaNumber(bookId) || Number(m[1]) !== shard(m[2]!)) return null;
  return m[3]!;
}

/* ------------------------------------------------------------------ */
/* HTML helpers                                                        */
/* ------------------------------------------------------------------ */

function hasClass(cls: string): RegExp {
  return new RegExp(`class\\s*=\\s*["']?[^"'>]*\\b${cls}\\b`, 'i');
}

function textOf(fragment: string): string {
  return cleanText(fragment).replace(/\n+/g, ' ').trim();
}

function stripScripts(html: string): string {
  return html
    .replace(/<script\b[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style\b[\s\S]*?<\/style>/gi, ' ')
    .replace(/<!--[\s\S]*?-->/g, ' ');
}

/** 飘天文学正文里的站点套话行（只剔除短行，避免误伤正文） */
function isPiaotiaNoise(line: string): boolean {
  if (/^(上一章|下一章|返回目录|返回书页|加入书签|推荐本书)$/.test(line)) return true;
  return line.length <= 60 && /飘天文学|piaotia|PT文学/i.test(line);
}

/** 比较标题时忽略空白与标点差异 */
function titleKey(text: string): string {
  return text.replace(/[\s　:：,，.。、_\-—]/g, '');
}

/* ------------------------------------------------------------------ */
/* 目录                                                                */
/* ------------------------------------------------------------------ */

/**
 * 目录页 /html/{a}/{n}/：div.centent 内按卷分组的 ul > li > a（文档顺序即阅读顺序）。
 * 卷名（div.list）不是章节；页首面包屑/书页链接不在 .centent 内。
 */
export function parsePiaotiaCatalog(bookId: string, html: string): TocEntry[] {
  piaotiaNumber(bookId);
  const block = stripScripts(divInner(html, hasClass('centent')) ?? html);
  const out: TocEntry[] = [];
  const seen = new Set<string>();
  const liRe = /<li\b[^>]*>([\s\S]*?)<\/li\s*>/gi;
  let m: RegExpExecArray | null;
  while ((m = liRe.exec(block))) {
    const a = m[1]!.match(/<a\b[^>]*href\s*=\s*["']?([^"'\s>]+)["']?[^>]*>([\s\S]*?)<\/a>/i);
    if (!a) continue;
    const id = parsePiaotiaChapterLink(bookId, a[1]!);
    const title = textOf(a[2]!);
    if (!id || !title || seen.has(id)) continue;
    seen.add(id);
    const info = normalizeTitle(title);
    out.push({ id, title, displayTitle: info.displayTitle, number: info.number, extra: false });
  }
  return out;
}

/* ------------------------------------------------------------------ */
/* 书籍信息（详情页表格：类 别：/作 者：/最后更新：/文章状态：/内容简介：）  */
/* ------------------------------------------------------------------ */

function field(text: string, label: RegExp): string | undefined {
  const m = text.match(label);
  const v = m?.[1]?.trim();
  return v || undefined;
}

export function parsePiaotiaBookInfo(bookId: string, html: string): BookInfo | null {
  piaotiaNumber(bookId);
  const clean = stripScripts(html);
  const title = textOf(clean.match(/<h1\b[^>]*>([\s\S]*?)<\/h1>/i)?.[1] ?? '');
  if (!title) return null;
  const cells = [...clean.matchAll(/<td\b[^>]*>([\s\S]*?)<\/td>/gi)].map((c) => textOf(c[1]!));
  const cell = (label: RegExp) => {
    for (const c of cells) {
      const v = field(c, label);
      if (v) return v;
    }
    return undefined;
  };
  const info: BookInfo = { id: bookId, title, author: cell(/^作\s*者[：:]\s*(.+)$/) ?? '' };
  const category = cell(/^类\s*别[：:]\s*(.+)$/);
  const status = cell(/^文章状态[：:]\s*(.+)$/);
  const updated = cell(/^最后更新[：:]\s*(.+)$/);
  const size = cell(/^全文长度[：:]\s*(\d+)字$/);
  if (category) info.category = category;
  if (status) info.status = status;
  if (updated) info.updatedAt = updated;
  if (size) info.size = `${(Number(size) / 10000).toFixed(2)}万字`;
  const introHtml = clean.match(/内容简介[：:]\s*<\/span>([\s\S]*?)<\/div>/i)?.[1]?.split(/<table\b|<caption\b/i)[0];
  if (introHtml) {
    const intro = cleanIntro(cleanText(introHtml).replace(/\n+/g, ' ').replace(/\s+/g, ' ').trim(), title);
    if (intro) info.intro = intro;
  }
  return info;
}

/* ------------------------------------------------------------------ */
/* 章节                                                                */
/* ------------------------------------------------------------------ */

/**
 * 章节页：<H1><a 书页>书名</a> 标题</H1>，导航在 div.toplink / div.bottomlink，
 * 正文是最后一个广告脚本表格之后、闭合 </div> 之前的 &nbsp; 缩进文本 + <br>。
 * 不分页：拿到正文且识别到章节导航即为完整。上一章/下一章指向 index.html 或 ./ 时为无。
 */
export function parsePiaotiaChapter(bookId: string, chapterId: string, html: string): ChapterResult {
  piaotiaNumber(bookId);
  assertChapterId(chapterId);

  let title = '';
  const h1 = html.match(/<h1\b[^>]*>([\s\S]*?)<\/h1>/i);
  if (h1) title = textOf(h1[1]!.replace(/<a\b[\s\S]*?<\/a>/gi, ' '));
  // 卷首章的 H1 带卷名：“云武王城【上】 第一章 八百年后”
  const numbered = title.match(/(?:番外)?第[\d零〇一二三四五六七八九十百千万两]+[章节回].*$/);
  if (numbered && numbered.index! > 0) title = numbered[0];
  if (!title) {
    const t = html.match(/<title>([\s\S]*?)<\/title>/i)?.[1];
    if (t) title = textOf(t).split(/[,_|]/)[1]?.trim() ?? '';
  }

  const paragraphs: string[] = [];
  const afterH1 = h1 ? html.slice(h1.index! + h1[0].length) : '';
  const nav = afterH1.match(/<div\b[^>]*class\s*=\s*["']?toplink[^>]*>[\s\S]*?<\/div>/i);
  let body = nav ? afterH1.slice(nav.index! + nav[0].length) : afterH1;
  const end = body.search(/<\/div\s*>/i);
  if (end >= 0) body = body.slice(0, end);
  body = stripScripts(body).replace(/<\/?(table|tr|td)\b[^>]*>/gi, '\n');
  const key = titleKey(title);
  for (const raw of cleanText(body).split('\n')) {
    const line = raw.trim();
    if (!line || isPiaotiaNoise(line)) continue;
    if (paragraphs.length === 0 && key && titleKey(line) === key) continue;
    paragraphs.push(line);
  }

  let prevId: string | null = null;
  let nextId: string | null = null;
  let navFound = false;
  const anchors = /<a\b[^>]*href\s*=\s*["']?([^"'\s>]+)["']?[^>]*>([\s\S]*?)<\/a>/gi;
  let m: RegExpExecArray | null;
  const cleaned = stripScripts(html);
  while ((m = anchors.exec(cleaned))) {
    const text = stripTags(m[2]!).replace(/[\s（）()←→快捷键]/g, '');
    if (!/^(上一章|下一章|返回目录)$/.test(text)) continue;
    navFound = true;
    const id = parsePiaotiaChapterLink(bookId, m[1]!);
    if (!id || id === chapterId) continue;
    if (prevId === null && text === '上一章') prevId = id;
    if (nextId === null && text === '下一章') nextId = id;
  }

  return {
    id: chapterId,
    title: title || `章节 ${chapterId}`,
    paragraphs,
    prevId,
    nextId,
    pageCount: paragraphs.length > 0 ? 1 : 0,
    charCount: paragraphs.reduce((n, p) => n + p.length, 0),
    complete: paragraphs.length > 0 && navFound,
    missingPages: [],
  };
}

/* ------------------------------------------------------------------ */
/* 抓取                                                                */
/* ------------------------------------------------------------------ */

function piaotiaOpts(opts: FetchOptions, highPriority: boolean): FetchOptions {
  return { ...opts, highPriority, allowedHosts: PIAOTIA_HOSTS };
}

/** 目录只有 1 页；含第 1 页时附带书籍信息（取详情页，失败不影响目录） */
export async function fetchPiaotiaTocRange(
  bookId: string,
  from: number,
  to: number,
  opts: FetchOptions = {},
): Promise<TocRangeResult> {
  piaotiaNumber(bookId);
  if (from < 1 || to < from) throw new Error('bad toc range');
  if (from > 1) return { from, to, totalPages: 1, pages: [], failedPages: [] };

  const { html } = await fetchSourceHtml(piaotiaCatalogUrl(bookId), piaotiaOpts(opts, false));
  const entries = parsePiaotiaCatalog(bookId, html);
  if (entries.length === 0) throw new SourceError('目录为空或书源页面结构已变化', 'invalid', true);
  let book: BookInfo | null = null;
  try {
    const info = await fetchSourceHtml(piaotiaInfoUrl(bookId), piaotiaOpts(opts, false));
    book = parsePiaotiaBookInfo(bookId, info.html);
  } catch (err) {
    if (opts.signal?.aborted) throw err;
  }
  if (!book) {
    const name = textOf(html.match(/<h1\b[^>]*>([\s\S]*?)<\/h1>/i)?.[1] ?? '').replace(/最新章节$/, '');
    const author = textOf(html).match(/作者[：:]\s*(\S+)/)?.[1] ?? '';
    if (name) book = { id: bookId, title: name, author };
  }
  const result: TocRangeResult = { from, to, totalPages: 1, pages: [{ page: 1, entries }], failedPages: [] };
  if (book) {
    book.firstChapterId = entries[0]!.id;
    result.book = book;
  }
  return result;
}

export async function fetchPiaotiaChapter(
  bookId: string,
  chapterId: string,
  opts: FetchOptions = {},
): Promise<ChapterResult> {
  const { html } = await fetchSourceHtml(piaotiaChapterUrl(bookId, chapterId), piaotiaOpts(opts, opts.highPriority !== false));
  return parsePiaotiaChapter(bookId, chapterId, html);
}

/* ------------------------------------------------------------------ */
/* 搜索（POST /modules/article/search.php，GBK 表单，按书名）             */
/* ------------------------------------------------------------------ */

function bookNumberFromHref(href: string): string | null {
  try {
    const u = new URL(decodeEntities(href.trim()), PIAOTIA.origin);
    if (!PIAOTIA_HOSTS.has(u.hostname)) return null;
    const m = u.pathname.match(/^\/(?:bookinfo|html)\/(\d{1,6})\/([1-9]\d{0,8})(?:\.html|\/(?:index\.html)?)$/);
    return m && Number(m[1]) === shard(m[2]!) ? m[2]! : null;
  } catch {
    return null;
  }
}

/**
 * 搜索结果页：table.grid 的行（书名 / 最新章节 / 作者 / 字数 / 更新 / 状态）。
 * 只有一个结果时 jieqi 直接重定向到详情页 /bookinfo/{a}/{n}.html。
 * 认不出的页面返回 null，由调用方按错误处理。
 */
export function parsePiaotiaSearch(html: string, finalUrl = ''): BookSummary[] | null {
  const direct = finalUrl ? bookNumberFromHref(finalUrl) : null;
  if (direct && /\/bookinfo\//.test(finalUrl)) {
    const info = parsePiaotiaBookInfo(piaotiaBookId(direct), html);
    if (!info) return null;
    const one: BookSummary = { id: info.id, title: info.title };
    if (info.author) one.author = info.author;
    if (info.category) one.category = info.category;
    if (info.updatedAt) one.updated = info.updatedAt;
    if (info.intro) one.intro = info.intro;
    return [one];
  }

  const clean = stripScripts(html);
  const out: BookSummary[] = [];
  const seen = new Set<string>();
  const rowRe = /<tr\b[^>]*>([\s\S]*?)<\/tr\s*>/gi;
  let m: RegExpExecArray | null;
  while ((m = rowRe.exec(clean)) && out.length < MAX_SEARCH_RESULTS) {
    const cells = [...m[1]!.matchAll(/<td\b[^>]*>([\s\S]*?)<\/td>/gi)].map((c) => c[1]!);
    if (cells.length < 3) continue;
    const a = cells[0]!.match(/<a\b[^>]*href\s*=\s*["']?([^"'\s>]+)["']?[^>]*>([\s\S]*?)<\/a>/i);
    const num = a ? bookNumberFromHref(a[1]!) : null;
    const title = a ? textOf(a[2]!) : '';
    if (!num || !title || seen.has(num)) continue;
    seen.add(num);
    const b: BookSummary = { id: piaotiaBookId(num), title };
    const latest = textOf(cells[1]!);
    const author = textOf(cells[2]!);
    const updated = cells[4] ? textOf(cells[4]) : '';
    if (latest) b.latestChapter = latest;
    if (author) b.author = author;
    if (updated) b.updated = updated;
    out.push(b);
  }
  if (out.length > 0 || /<caption>\s*搜索结果\s*<\/caption>/.test(html)) return out;
  if (/没有找到|没有搜索到|未找到|搜索结果为空|无结果/.test(html)) return [];
  return null;
}

let lastPiaotiaSearchAt = 0;
let piaotiaSearchChain: Promise<void> = Promise.resolve();

/** 测试用：重置搜索闸门 */
export function resetPiaotiaSearchGate(): void {
  lastPiaotiaSearchAt = 0;
  piaotiaSearchChain = Promise.resolve();
}

export async function searchPiaotia(query: string, opts: FetchOptions = {}): Promise<BookSummary[]> {
  const q = normalizeSearchQuery(query);
  if (q === null) throw new SourceError('invalid search query', 'invalid', false);
  const sleep = opts.sleep ?? ((ms: number) => new Promise<void>((r) => setTimeout(r, ms)));
  const gate = piaotiaSearchChain.then(async () => {
    const wait = lastPiaotiaSearchAt + PIAOTIA_MIN_SEARCH_GAP_MS - Date.now();
    if (wait > 0) await sleep(wait);
    lastPiaotiaSearchAt = Date.now();
  });
  piaotiaSearchChain = gate.catch(() => undefined);
  await gate;
  const { html, finalUrl } = await fetchSourceHtml(PIAOTIA_SEARCH_URL, {
    ...piaotiaOpts(opts, true),
    form: `searchtype=articlename&searchkey=${encodeGbkComponent(q)}`,
  });
  const results = parsePiaotiaSearch(html, finalUrl);
  if (results === null) {
    if (/间隔|频繁|稍后再/.test(html)) throw new SourceError('书源搜索太频繁，请稍后再试', 'http', true, 429);
    throw new SourceError('unexpected search page', 'invalid', true);
  }
  return results;
}
