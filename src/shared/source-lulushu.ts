/**
 * 书源适配：露露书（www.lulushu55.com，UTF-8）。
 *
 * 所有请求都由 LULUSHU.origin + 校验过的书号/章节号拼出（{a} = ⌊n/1000⌋）：
 *   书页/目录  /{a}_{n}/                 （og meta + section.mulu 里的全部章节，单页）
 *   章节页     /{a}_{n}/{章节号}.html     （只有标题、上一章/下一章与本章的 hash，正文不在页面里）
 *   正文       POST /home/index/ajaxchapter（id={n}&eKey={hash}&cid={章节号}&basecid={章节号}，返回 JSON）
 *   搜索       POST /home/search（action=search&q=关键词）
 *   封面       /uploads/{a}/{n}.jpg
 *
 * 正文是页面脚本用页面里给出的 hash 再请求一次拿到的（浏览器打开网页时也是这样），不涉及登录/验证。
 * 书号在本应用中带前缀：`lulushu-{n}`。站点域名带序号（lulushu55），换域名时同步改 origin 与 SourceRules。
 */
import {
  assertChapterId,
  cleanIntro,
  cleanText,
  decodeEntities,
  fetchSourceHtml,
  metaContent,
  normalizeSearchQuery,
  normalizeTitle,
  SourceError,
  MAX_SEARCH_RESULTS,
  type BookInfo,
  type BookSummary,
  type ChapterResult,
  type FetchOptions,
  type TocEntry,
  type TocRangeResult,
} from './source.ts';

export const LULUSHU = {
  id: 'lulushu',
  name: '露露书',
  origin: 'https://www.lulushu55.com',
} as const;

export const LULUSHU_HOSTS: ReadonlySet<string> = new Set(['www.lulushu55.com', 'lulushu55.com']);
export const LULUSHU_AJAX_URL = `${LULUSHU.origin}/home/index/ajaxchapter`;
export const LULUSHU_SEARCH_URL = `${LULUSHU.origin}/home/search`;
export const LULUSHU_BOOK_PREFIX = 'lulushu-';
export const LULUSHU_MIN_SEARCH_GAP_MS = 3000;
/** 正文接口的分页上限（实测《万古神帝》不分页；防御源站返回循环的 nextpage） */
const MAX_AJAX_PAGES = 8;

const NUM_RE = /^[1-9]\d{0,8}$/;
const BOOK_ID_RE = /^lulushu-([1-9]\d{0,8})$/;
const HASH_RE = /^[0-9a-f]{8,64}$/;

export function isLulushuBookId(id: unknown): id is string {
  return typeof id === 'string' && BOOK_ID_RE.test(id);
}

export function lulushuBookId(num: string): string {
  if (!NUM_RE.test(num)) throw new Error('invalid lulushu book number');
  return LULUSHU_BOOK_PREFIX + num;
}

export function lulushuNumber(bookId: string): string {
  const m = BOOK_ID_RE.exec(bookId);
  if (!m) throw new Error('invalid lulushu book id');
  return m[1]!;
}

function shard(n: string): number {
  return Math.floor(Number(n) / 1000);
}

/* ------------------------------------------------------------------ */
/* URL building（受限：只允许校验过的书号/章节号拼出的路径）             */
/* ------------------------------------------------------------------ */

export function lulushuBookUrl(bookId: string): string {
  const n = lulushuNumber(bookId);
  return `${LULUSHU.origin}/${shard(n)}_${n}/`;
}

export function lulushuChapterUrl(bookId: string, chapterId: string): string {
  const n = lulushuNumber(bookId);
  assertChapterId(chapterId);
  return `${LULUSHU.origin}/${shard(n)}_${n}/${chapterId}.html`;
}

export function lulushuCoverUrl(bookId: string): string {
  const n = lulushuNumber(bookId);
  return `${LULUSHU.origin}/uploads/${shard(n)}/${n}.jpg`;
}

/** 正文接口的表单：书号/章节号已校验，hash 只能是十六进制 */
export function lulushuAjaxForm(bookId: string, chapterId: string, hash: string, page?: { nextpage: string; pagehash: string }): string {
  const n = lulushuNumber(bookId);
  assertChapterId(chapterId);
  if (!HASH_RE.test(hash)) throw new SourceError('书源页面缺少正文校验码', 'invalid', true);
  let form = `id=${n}&eKey=${hash}&cid=${chapterId}&basecid=${chapterId}`;
  if (page) {
    if (!/^\d{1,4}$/.test(page.nextpage) || !HASH_RE.test(page.pagehash)) throw new SourceError('正文分页参数异常', 'invalid', false);
    form += `&nextpage=${page.nextpage}&pagehash=${page.pagehash}`;
  }
  return form;
}

function lulushuPath(href: string): string | null {
  const h = decodeEntities((href ?? '').trim());
  if (!h || /^(javascript|mailto):/i.test(h)) return null;
  try {
    const u = new URL(h, LULUSHU.origin);
    if (!LULUSHU_HOSTS.has(u.hostname) || u.search) return null;
    return u.pathname;
  } catch {
    return null;
  }
}

/** 只接受本书的章节页 /{a}_{n}/{id}.html */
export function parseLulushuChapterLink(bookId: string, href: string): string | null {
  if (!isLulushuBookId(bookId)) return null;
  const m = lulushuPath(href)?.match(/^\/(\d{1,6})_([1-9]\d{0,8})\/(\d{1,12})\.html$/);
  if (!m || m[2] !== lulushuNumber(bookId) || Number(m[1]) !== shard(m[2]!)) return null;
  return m[3]!;
}

function bookNumberFromHref(href: string): string | null {
  const m = lulushuPath(href)?.match(/^\/(\d{1,6})_([1-9]\d{0,8})\/$/);
  return m && Number(m[1]) === shard(m[2]!) ? m[2]! : null;
}

/* ------------------------------------------------------------------ */
/* HTML helpers                                                        */
/* ------------------------------------------------------------------ */

function textOf(fragment: string): string {
  return cleanText(fragment).replace(/\n+/g, ' ').trim();
}

function stripScripts(html: string): string {
  return html
    .replace(/<script\b[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style\b[\s\S]*?<\/style>/gi, ' ')
    .replace(/<!--[\s\S]*?-->/g, ' ');
}

/** 带该 class 的 section（有多个时取最后一个：书页上先是“最新章节列表”，后是“全部章节目录”） */
function sectionInner(html: string, cls: string): string | null {
  const all = [...html.matchAll(new RegExp(`<section\\b[^>]*class\\s*=\\s*["'][^"']*\\b${cls}\\b[^"']*["'][^>]*>([\\s\\S]*?)<\\/section>`, 'gi'))];
  return all.length ? all[all.length - 1]![1]! : null;
}

function pageVar(html: string, name: string): string | null {
  const m = html.match(new RegExp(`\\bvar\\s+${name}\\s*=\\s*["']([^"']*)["']`));
  return m ? decodeEntities(m[1]!) : null;
}

const titleKey = (text: string) => text.replace(/[\s　:：,，.。、_\-—]/g, '');

/* ------------------------------------------------------------------ */
/* 书页（目录 + 书籍信息）                                              */
/* ------------------------------------------------------------------ */

/**
 * 目录：section.mulu 内的 ul > li > a（文档顺序即阅读顺序）；标题带序号前缀“1.第1章 …”，去掉。
 * 页面上方 ul#newchapter 是倒序的“最新章节”，不在 section.mulu 里。
 */
export function parseLulushuCatalog(bookId: string, html: string): TocEntry[] {
  lulushuNumber(bookId);
  const block = stripScripts(sectionInner(html, 'mulu') ?? '');
  const out: TocEntry[] = [];
  const seen = new Set<string>();
  for (const m of block.matchAll(/<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi)) {
    const id = parseLulushuChapterLink(bookId, m[1]!);
    const title = textOf(m[2]!).replace(/^\d{1,5}\.\s*/, '');
    if (!id || !title || seen.has(id)) continue;
    seen.add(id);
    const info = normalizeTitle(title);
    out.push({ id, title, displayTitle: info.displayTitle, number: info.number, extra: false });
  }
  return out;
}

export function parseLulushuBookInfo(bookId: string, html: string): BookInfo | null {
  lulushuNumber(bookId);
  const title = metaContent(html, 'og:novel:book_name') ?? textOf(html.match(/<h1\b[^>]*>([\s\S]*?)<\/h1>/i)?.[1] ?? '');
  if (!title) return null;
  const info: BookInfo = { id: bookId, title, author: metaContent(html, 'og:novel:author') ?? '' };
  const category = metaContent(html, 'og:novel:category');
  const status = metaContent(html, 'og:novel:status');
  const updated = metaContent(html, 'og:novel:update_time');
  const latest = metaContent(html, 'og:novel:latest_chapter_name');
  if (category) info.category = category;
  if (status) info.status = status;
  if (updated) info.updatedAt = updated;
  if (latest) info.latestChapter = latest;
  const introHtml = html.match(/<div\b[^>]*class\s*=\s*["'][^"']*\bintro\b[^"']*["'][^>]*>([\s\S]*?)<\/div>/i)?.[1];
  const raw = introHtml ? textOf(introHtml) : metaContent(html, 'og:description') ?? '';
  const intro = cleanIntro(raw.replace(/[^。！？]*露露书[^。！？]*[。！？]?/g, '').trim(), title);
  if (intro) info.intro = intro;
  return info;
}

/* ------------------------------------------------------------------ */
/* 章节                                                                */
/* ------------------------------------------------------------------ */

export interface LulushuChapterPage {
  title: string;
  hash: string | null;
  prevId: string | null;
  nextId: string | null;
}

/**
 * 章节页：var chaptername / hash / prevcid / nextcid。
 * 不用 preview_page/next_page：next_page 常指向本章网页版的第二页（3587_1.html），不是下一章；
 * 正文接口一次返回整章。prevcid/nextcid 为 -1 或与本章相同时为无。
 */
export function parseLulushuChapterPage(bookId: string, chapterId: string, html: string): LulushuChapterPage {
  lulushuNumber(bookId);
  const hash = pageVar(html, 'hash');
  const neighbour = (name: string) => {
    const v = pageVar(html, name);
    return v && /^\d{1,12}$/.test(v) && v !== chapterId ? v : null;
  };
  let title = pageVar(html, 'chaptername') ?? '';
  if (!title) title = textOf(html.match(/<h1\b[^>]*>([\s\S]*?)<\/h1>/i)?.[1] ?? '');
  return {
    title: title.replace(/^\d{1,5}\.\s*/, ''),
    hash: hash && HASH_RE.test(hash) ? hash : null,
    prevId: neighbour('prevcid'),
    nextId: neighbour('nextcid'),
  };
}

export interface LulushuAjaxPage {
  paragraphs: string[];
  nextpage: { nextpage: string; pagehash: string } | null;
}

/** 正文接口：{status:'success', info:{content:'<p>…</p>'}, nextpage?, hash?}；status 不是 success 时抛错 */
export function parseLulushuAjax(body: string, title: string): LulushuAjaxPage {
  let data: { status?: string; info?: { content?: string }; nextpage?: unknown; hash?: unknown };
  try {
    data = JSON.parse(body);
  } catch {
    throw new SourceError('书源正文接口返回异常', 'invalid', true);
  }
  if (data.status !== 'success' || typeof data.info?.content !== 'string') {
    throw new SourceError(`书源正文接口失败（${String(data.status ?? '无状态')}）`, 'invalid', true);
  }
  const html = stripScripts(data.info.content).replace(/<div\b[\s\S]*?<\/div>/gi, ' ');
  const key = titleKey(title).replace(/^第[\d零〇一二三四五六七八九十百千万两]+章/, '');
  const paragraphs: string[] = [];
  for (const m of html.matchAll(/<p\b[^>]*>([\s\S]*?)<\/p>/gi)) {
    const line = textOf(m[1]!);
    if (!line) continue;
    // 首段常是章节标题（号码可能与目录不同：“第3579章回城” vs “第三千五百五十八章 回城”）
    if (paragraphs.length === 0 && /^第[\d零〇一二三四五六七八九十百千万两]+章/.test(line) && line.length <= 40
      && (!key || titleKey(line).endsWith(key))) continue;
    paragraphs.push(line);
  }
  const np = data.nextpage;
  const nextpage = (typeof np === 'string' || typeof np === 'number') && String(np) !== '' && typeof data.hash === 'string'
    ? { nextpage: String(np), pagehash: data.hash }
    : null;
  return { paragraphs, nextpage };
}

/* ------------------------------------------------------------------ */
/* 抓取                                                                */
/* ------------------------------------------------------------------ */

function lulushuOpts(opts: FetchOptions, highPriority: boolean): FetchOptions {
  return { ...opts, highPriority, allowedHosts: LULUSHU_HOSTS };
}

export async function fetchLulushuTocRange(
  bookId: string,
  from: number,
  to: number,
  opts: FetchOptions = {},
): Promise<TocRangeResult> {
  lulushuNumber(bookId);
  if (from < 1 || to < from) throw new Error('bad toc range');
  if (from > 1) return { from, to, totalPages: 1, pages: [], failedPages: [] };
  const { html } = await fetchSourceHtml(lulushuBookUrl(bookId), lulushuOpts(opts, false));
  const entries = parseLulushuCatalog(bookId, html);
  if (entries.length === 0) throw new SourceError('目录为空或书源页面结构已变化', 'invalid', true);
  const result: TocRangeResult = { from, to, totalPages: 1, pages: [{ page: 1, entries }], failedPages: [] };
  const book = parseLulushuBookInfo(bookId, html);
  if (book) {
    book.firstChapterId = entries[0]!.id;
    result.book = book;
  }
  return result;
}

/** 章节：先取章节页拿标题/导航/hash，再按 hash 请求正文接口（分页时继续取下一页） */
export async function fetchLulushuChapter(
  bookId: string,
  chapterId: string,
  opts: FetchOptions = {},
): Promise<ChapterResult> {
  const high = opts.highPriority !== false;
  const page = await fetchSourceHtml(lulushuChapterUrl(bookId, chapterId), lulushuOpts(opts, high));
  const meta = parseLulushuChapterPage(bookId, chapterId, page.html);
  if (!meta.hash) throw new SourceError('书源页面缺少正文校验码（页面结构可能已变化）', 'invalid', true);
  const paragraphs: string[] = [];
  let next: { nextpage: string; pagehash: string } | undefined;
  let pages = 0;
  let complete = true;
  do {
    const res = await fetchSourceHtml(LULUSHU_AJAX_URL, {
      ...lulushuOpts(opts, high),
      form: lulushuAjaxForm(bookId, chapterId, meta.hash, next),
    });
    const part = parseLulushuAjax(res.html, meta.title);
    paragraphs.push(...part.paragraphs);
    next = part.nextpage ?? undefined;
    pages++;
    if (next && pages >= MAX_AJAX_PAGES) {
      complete = false;
      break;
    }
  } while (next);
  return {
    id: chapterId,
    title: meta.title || `章节 ${chapterId}`,
    paragraphs,
    prevId: meta.prevId,
    nextId: meta.nextId,
    pageCount: pages,
    charCount: paragraphs.reduce((n, p) => n + p.length, 0),
    complete: complete && paragraphs.length > 0,
    missingPages: [],
  };
}

/* ------------------------------------------------------------------ */
/* 搜索（POST /home/search，UTF-8 表单）                                */
/* ------------------------------------------------------------------ */

/** 结果页：section.fengtui 的 dl（h3 a 书名 / span[itemprop=author] / p 简介） */
export function parseLulushuSearch(html: string): BookSummary[] | null {
  const region = stripScripts(sectionInner(html, 'fengtui') ?? '');
  const out: BookSummary[] = [];
  const seen = new Set<string>();
  for (const m of region.matchAll(/<dl\b[^>]*>([\s\S]*?)<\/dl>/gi)) {
    if (out.length >= MAX_SEARCH_RESULTS) break;
    const dl = m[1]!;
    const a = dl.match(/<h3\b[^>]*>[\s\S]*?<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/i);
    const num = a ? bookNumberFromHref(a[1]!) : null;
    const title = a ? textOf(a[2]!) : '';
    if (!num || !title || seen.has(num)) continue;
    seen.add(num);
    const b: BookSummary = { id: lulushuBookId(num), title };
    const author = textOf(dl.match(/<span\b[^>]*itemprop\s*=\s*["']author["'][^>]*>([\s\S]*?)<\/span>/i)?.[1] ?? '');
    const intro = textOf(dl.match(/<p\b[^>]*>([\s\S]*?)<\/p>/i)?.[1] ?? '').replace(/\.{3}$/, '');
    if (author) b.author = author;
    if (intro) b.intro = intro;
    out.push(b);
  }
  if (out.length > 0 || /fengtui_title/.test(html)) return out;
  if (/没有找到|未找到|无结果/.test(html)) return [];
  return null;
}

let lastLulushuSearchAt = 0;
let lulushuSearchChain: Promise<void> = Promise.resolve();

/** 测试用：重置搜索闸门 */
export function resetLulushuSearchGate(): void {
  lastLulushuSearchAt = 0;
  lulushuSearchChain = Promise.resolve();
}

export async function searchLulushu(query: string, opts: FetchOptions = {}): Promise<BookSummary[]> {
  const q = normalizeSearchQuery(query);
  if (q === null) throw new SourceError('invalid search query', 'invalid', false);
  const sleep = opts.sleep ?? ((ms: number) => new Promise<void>((r) => setTimeout(r, ms)));
  const gate = lulushuSearchChain.then(async () => {
    const wait = lastLulushuSearchAt + LULUSHU_MIN_SEARCH_GAP_MS - Date.now();
    if (wait > 0) await sleep(wait);
    lastLulushuSearchAt = Date.now();
  });
  lulushuSearchChain = gate.catch(() => undefined);
  await gate;
  const { html } = await fetchSourceHtml(LULUSHU_SEARCH_URL, {
    ...lulushuOpts(opts, true),
    form: `action=search&q=${encodeURIComponent(q)}`,
  });
  const results = parseLulushuSearch(html);
  if (results === null) throw new SourceError('unexpected search page', 'invalid', true);
  return results;
}
