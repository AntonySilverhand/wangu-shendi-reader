/**
 * 书源适配：69书吧（www.69shuba.com，jieqi 模板，GBK 编码）。
 *
 * 与万书阁共用 source.ts 的上游队列、重试、字符集解码与人机验证识别，只在这里做
 * URL 拼装与页面解析。所有请求都由 SHUBA.origin + 校验过的书号/章节号拼出：
 *   详情页  /book/{n}.htm
 *   目录页  /book/{n}/          （单页列出全部章节：div#catalog li[data-num] > a）
 *   章节页  /txt/{n}/{章节号}    （正文在 div.txtnav，不分页）
 *   搜索    POST /modules/article/search.php（searchkey=GBK 编码关键词&searchtype=all）
 *   封面    https://static.69shuba.com/files/article/image/{n/1000}/{n}/{n}s.jpg
 *
 * 书号在本应用中带前缀：`69shuba-{n}`，与万书阁的纯数字书号互不冲突。
 * 该站点前置 Cloudflare：遇到人机验证时 fetch 层抛 code='blocked'，不做任何绕过。
 */
import {
  assertChapterId,
  balancedDivInner,
  divInner,
  cleanIntro,
  cleanText,
  decodeEntities,
  fetchSourceHtml,
  metaContent,
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
import { cleanParagraphs, countChars } from './text-clean.ts';

export const SHUBA = {
  id: '69shuba',
  name: '69书吧',
  origin: 'https://www.69shuba.com',
} as const;

export const SHUBA_HOSTS: ReadonlySet<string> = new Set(['www.69shuba.com', '69shuba.com']);
export const SHUBA_COVER_ORIGIN = 'https://static.69shuba.com';
export const SHUBA_SEARCH_URL = `${SHUBA.origin}/modules/article/search.php`;
export const SHUBA_BOOK_PREFIX = '69shuba-';
/** jieqi 搜索自带限频，两次上游搜索之间至少间隔这么久 */
export const SHUBA_MIN_SEARCH_GAP_MS = 5000;

const NUM_RE = /^[1-9]\d{0,8}$/;
const BOOK_ID_RE = /^69shuba-([1-9]\d{0,8})$/;

export function isShubaBookId(id: unknown): id is string {
  return typeof id === 'string' && BOOK_ID_RE.test(id);
}

/** 源站数字书号 → 应用内书号 */
export function shubaBookId(num: string): string {
  if (!NUM_RE.test(num)) throw new Error('invalid 69shuba book number');
  return SHUBA_BOOK_PREFIX + num;
}

/** 应用内书号 → 源站数字书号（非法时抛错） */
export function shubaNumber(bookId: string): string {
  const m = BOOK_ID_RE.exec(bookId);
  if (!m) throw new Error('invalid 69shuba book id');
  return m[1]!;
}

/* ------------------------------------------------------------------ */
/* URL building（受限：只允许校验过的书号/章节号拼出的路径）             */
/* ------------------------------------------------------------------ */

export function shubaInfoUrl(bookId: string): string {
  return `${SHUBA.origin}/book/${shubaNumber(bookId)}.htm`;
}

export function shubaCatalogUrl(bookId: string): string {
  return `${SHUBA.origin}/book/${shubaNumber(bookId)}/`;
}

export function shubaChapterUrl(bookId: string, chapterId: string): string {
  const n = shubaNumber(bookId);
  assertChapterId(chapterId);
  return `${SHUBA.origin}/txt/${n}/${chapterId}`;
}

export function shubaCoverUrl(bookId: string): string {
  const n = shubaNumber(bookId);
  return `${SHUBA_COVER_ORIGIN}/files/article/image/${Math.floor(Number(n) / 1000)}/${n}/${n}s.jpg`;
}

/**
 * 解析指向本书章节页的链接（相对/绝对/协议相对）；只接受 69书吧主机上本书的 /txt/{n}/{id}。
 * 其它书的推荐链接、目录页、外站链接一律返回 null。
 */
export function parseShubaChapterLink(bookId: string, href: string): string | null {
  if (!isShubaBookId(bookId)) return null;
  const path = shubaPath(href);
  if (path === null) return null;
  const m = path.match(/^\/txt\/([1-9]\d{0,8})\/(\d{1,12})(?:\.html?)?\/?$/);
  if (!m || m[1] !== shubaNumber(bookId)) return null;
  return m[2]!;
}

/** href → 站内路径（外站返回 null） */
function shubaPath(href: string): string | null {
  let h = decodeEntities((href ?? '').trim());
  if (!h) return null;
  const hash = h.indexOf('#');
  if (hash >= 0) h = h.slice(0, hash);
  if (/^https?:\/\//i.test(h) || h.startsWith('//')) {
    try {
      const u = new URL(h.startsWith('//') ? `https:${h}` : h);
      if (!SHUBA_HOSTS.has(u.hostname)) return null;
      h = u.pathname;
    } catch {
      return null;
    }
  }
  const q = h.indexOf('?');
  if (q >= 0) h = h.slice(0, q);
  return h.startsWith('/') ? h : null;
}

/* ------------------------------------------------------------------ */
/* HTML helpers                                                        */
/* ------------------------------------------------------------------ */

/** 删除开始标签匹配 attrRe 的所有 div（含嵌套内容） */
function removeDivs(html: string, attrRe: RegExp): string {
  let out = html;
  for (let guard = 0; guard < 200; guard++) {
    const re = /<div\b([^>]*)>/gi;
    let m: RegExpExecArray | null;
    let found = false;
    while ((m = re.exec(out))) {
      if (!attrRe.test(m[1]!)) continue;
      const openEnd = m.index + m[0].length;
      const inner = balancedDivInner(out, openEnd);
      const closeLen = out.slice(openEnd + inner.length).match(/^<\/div\s*>/i)?.[0].length ?? 0;
      out = out.slice(0, m.index) + ' ' + out.slice(openEnd + inner.length + closeLen);
      found = true;
      break;
    }
    if (!found) break;
  }
  return out;
}

function hasClass(cls: string): RegExp {
  return new RegExp(`class\\s*=\\s*["'][^"']*\\b${cls}\\b[^"']*["']`, 'i');
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

/** 69书吧正文里的站点广告/套话行 */
const SHUBA_NOISE = [
  /^[(（]?本章完[)）]?$/,
  /69\s*shu|69\s*书吧|六九书吧/i,
  /^(上一章|下一章|目录|书页|返回书页|加入书签|投推荐票)$/,
];

function isShubaNoise(line: string): boolean {
  if (SHUBA_NOISE[0]!.test(line) || SHUBA_NOISE[2]!.test(line)) return true;
  // 只剔除短的广告行，避免误伤恰好提到站名的正文长段
  return line.length <= 80 && SHUBA_NOISE[1]!.test(line);
}

/** 比较标题时忽略空白与标点差异 */
function titleKey(text: string): string {
  return text.replace(/[\s　:：,，.。、_\-—]/g, '');
}

/* ------------------------------------------------------------------ */
/* 目录                                                                */
/* ------------------------------------------------------------------ */

/**
 * 目录页 /book/{n}/：div#catalog 内的 li[data-num] > a。
 * 页面可能为倒序，也可能夹带“最新章节”区块，因此：
 *  - 只取 #catalog 内的条目（找不到时整页扫描兜底）；
 *  - 按 id 去重；全部带 data-num 时按 data-num 升序，否则按文档顺序（倒序时翻转）。
 * 顺序以源站目录为准，不套用万书阁的错号/重传启发式（见 sources.ts mergeBookToc）。
 */
export function parseShubaCatalog(bookId: string, html: string): TocEntry[] {
  shubaNumber(bookId);
  const block = stripScripts(divInner(html, /\bid\s*=\s*["']catalog["']/i) ?? html);
  const items: { id: string; title: string; num: number | null }[] = [];
  const seen = new Set<string>();
  const add = (href: string, inner: string, num: number | null) => {
    const id = parseShubaChapterLink(bookId, href);
    if (!id || seen.has(id)) return;
    const title = textOf(inner);
    if (!title) return;
    seen.add(id);
    items.push({ id, title, num });
  };

  const liRe = /<li\b([^>]*)>([\s\S]*?)<\/li\s*>/gi;
  let m: RegExpExecArray | null;
  while ((m = liRe.exec(block))) {
    const dn = m[1]!.match(/data-num\s*=\s*["']?(\d{1,6})/i);
    const a = m[2]!.match(/<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/i);
    if (a) add(a[1]!, a[2]!, dn ? parseInt(dn[1]!, 10) : null);
  }
  if (items.length === 0) {
    const aRe = /<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi;
    while ((m = aRe.exec(block))) add(m[1]!, m[2]!, null);
  }

  if (items.length > 1) {
    if (items.every((it) => it.num !== null)) {
      items.sort((a, b) => a.num! - b.num!);
    } else if (Number(items[0]!.id) > Number(items[items.length - 1]!.id)) {
      items.reverse();
    }
  }
  return items.map(({ id, title }) => {
    const info = normalizeTitle(title);
    // 番外等按源站目录原位保留（extra 只影响万书阁的番外归位）
    return { id, title, displayTitle: info.displayTitle, number: info.number, extra: false };
  });
}

/* ------------------------------------------------------------------ */
/* 书籍信息（详情页 og meta，缺失时读 div.booknav2）                     */
/* ------------------------------------------------------------------ */

function cleanShubaIntro(raw: string, title: string): string {
  const t = raw
    // og:description 里的 &lt;br /&gt; 解码后才成为标签，需再剥一次
    .replace(/<br\s*\/?>/gi, ' ')
    .replace(/<[^>]+>/g, '')
    .replace(/\s+/g, ' ')
    // 去掉“……69书吧免费提供……”一类站点套话（按句）
    .replace(/[^。！？\n]*(?:69\s*书吧|69shu)[^。！？\n]*[。！？]?/giu, '')
    .replace(/[^。！？\n]*(?:最新章节由网友提供|情节跌宕起伏、扣人心弦)[^。！？\n]*[。！？]?/gu, '')
    .trim();
  return cleanIntro(t, title);
}

export function parseShubaBookInfo(bookId: string, html: string): BookInfo | null {
  shubaNumber(bookId);
  const nav = divInner(html, hasClass('booknav2'));
  const title =
    metaContent(html, 'og:novel:book_name') ??
    (nav ? textOf(nav.match(/<h1\b[^>]*>([\s\S]*?)<\/h1>/i)?.[1] ?? '') || undefined : undefined);
  if (!title) return null;
  const info: BookInfo = { id: bookId, title, author: metaContent(html, 'og:novel:author') ?? '' };

  const navField = (label: string): string | undefined => {
    if (!nav) return undefined;
    const m = nav.match(new RegExp(`<p\\b[^>]*>\\s*${label}\\s*[：:]([\\s\\S]*?)<\\/p>`, 'i'));
    const v = m ? textOf(m[1]!) : '';
    return v || undefined;
  };
  if (!info.author && nav) {
    const a = nav.match(/<a\b[^>]*href\s*=\s*["'][^"']*authorarticle[^"']*["'][^>]*>([\s\S]*?)<\/a>/i);
    info.author = (a ? textOf(a[1]!) : '') || navField('作者') || '';
  }

  const category = metaContent(html, 'og:novel:category') ?? navField('分类');
  let status = metaContent(html, 'og:novel:status');
  if (!status && nav) status = textOf(nav).match(/(连载中|连载|完本|已完结|全本)/)?.[1];
  const latest = metaContent(html, 'og:novel:latest_chapter_name');
  const updated = metaContent(html, 'og:novel:update_time') ?? navField('更新');
  let desc = metaContent(html, 'og:description');
  if (!desc) {
    const navtxt = divInner(html, hasClass('navtxt'));
    const p = navtxt?.match(/<p\b[^>]*>([\s\S]*?)<\/p>/i)?.[1] ?? navtxt ?? '';
    desc = cleanText(p).replace(/\n+/g, ' ').trim() || undefined;
  }
  if (category) info.category = category;
  if (status) info.status = status;
  if (latest) info.latestChapter = latest;
  if (updated) info.updatedAt = updated;
  if (desc) {
    const intro = cleanShubaIntro(desc, title);
    if (intro) info.intro = intro;
  }
  // og:novel:read_url 通常指向目录页；只有指向章节时才是首章
  const readUrl = metaContent(html, 'og:novel:read_url');
  const first = readUrl ? parseShubaChapterLink(bookId, readUrl) : null;
  if (first) info.firstChapterId = first;
  return info;
}

/* ------------------------------------------------------------------ */
/* 章节                                                                */
/* ------------------------------------------------------------------ */

/** jieqi 模板的 `var bookinfo = { preview_page: "...", next_page: "..." }` */
function bookinfoField(html: string, key: string): string | null {
  const m = html.match(new RegExp(`\\b${key}\\s*:\\s*["']([^"']*)["']`));
  return m ? m[1]! : null;
}

/**
 * 解析章节页。正文是 div.txtnav 里的裸文本 + <br>（行首 &emsp;&emsp;），
 * 其中夹着标题 h1、div.txtinfo（时间/作者）、div#txtright 与 div.contentadv 广告。
 * 69书吧章节不分页：拿到正文且识别到章节导航（bookinfo 或上一章/下一章链接）即为完整。
 */
export function parseShubaChapter(bookId: string, chapterId: string, html: string): ChapterResult {
  shubaNumber(bookId);
  assertChapterId(chapterId);
  const txtnav = divInner(html, hasClass('txtnav'));

  let title = '';
  const h1 = (txtnav ?? html).match(/<h1\b[^>]*>([\s\S]*?)<\/h1>/i);
  if (h1) title = textOf(h1[1]!);
  if (!title) title = decodeEntities(bookinfoField(html, 'chaptername') ?? '').trim();
  if (!title) {
    const t = html.match(/<title>([\s\S]*?)<\/title>/i)?.[1];
    if (t) title = textOf(t).split(/[-_|]/)[0]!.trim();
  }

  const paragraphs: string[] = [];
  if (txtnav) {
    let body = stripScripts(txtnav).replace(/<h1\b[\s\S]*?<\/h1>/gi, ' ');
    body = removeDivs(body, /txtinfo|txtright|contentadv|bottom-?ad|\bads?\b|\badv\b|page1|tools|share/i);
    const lines = cleanText(body.replace(/<\/div\s*>|<div\b[^>]*>/gi, '\n')).split('\n');
    const key = titleKey(title);
    for (const raw of lines) {
      const line = raw.trim();
      if (!line || isShubaNoise(line)) continue;
      // 正文首行常重复标题
      if (paragraphs.length === 0 && key && titleKey(line) === key) continue;
      paragraphs.push(line);
    }
  }

  // 导航：bookinfo 优先，其次按钮文字（只认本书章节链接）
  let prevId: string | null = null;
  let nextId: string | null = null;
  let navFound = false;
  const prevPage = bookinfoField(html, 'preview_page') ?? bookinfoField(html, 'prev_page');
  const nextPage = bookinfoField(html, 'next_page');
  if (prevPage !== null || nextPage !== null) {
    navFound = true;
    if (prevPage) prevId = parseShubaChapterLink(bookId, prevPage);
    if (nextPage) nextId = parseShubaChapterLink(bookId, nextPage);
  }
  const anchors = /<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi;
  let m: RegExpExecArray | null;
  const cleaned = stripScripts(html);
  while ((m = anchors.exec(cleaned))) {
    const text = stripTags(m[2]!).replace(/\s+/g, '');
    if (!/^(上一章|下一章|上一页|下一页|目录)$/.test(text)) continue;
    navFound = true;
    const id = parseShubaChapterLink(bookId, m[1]!);
    if (!id || id === chapterId) continue;
    if (prevId === null && /^上一/.test(text)) prevId = id;
    if (nextId === null && /^下一/.test(text)) nextId = id;
  }
  if (prevId === chapterId) prevId = null;
  if (nextId === chapterId) nextId = null;

  const body = cleanParagraphs(paragraphs);
  return {
    id: chapterId,
    title: title || `章节 ${chapterId}`,
    paragraphs: body,
    prevId,
    nextId,
    pageCount: body.length > 0 ? 1 : 0,
    charCount: countChars(body),
    complete: body.length > 0 && navFound,
    missingPages: [],
  };
}

/* ------------------------------------------------------------------ */
/* 抓取                                                                */
/* ------------------------------------------------------------------ */

function shubaOpts(opts: FetchOptions, highPriority: boolean): FetchOptions {
  return { ...opts, highPriority, allowedHosts: SHUBA_HOSTS };
}

/**
 * 目录区间：69书吧目录只有 1 页（目录页一次列出全部章节）。
 * 范围含第 1 页时取目录页，并附带书籍信息（目录页没有 og meta 时再取详情页，失败不影响目录）。
 * 目录页失败或解析为空时抛错（不缓存失败结果，由客户端标记失败页）。
 */
export async function fetchShubaTocRange(
  bookId: string,
  from: number,
  to: number,
  opts: FetchOptions = {},
): Promise<TocRangeResult> {
  shubaNumber(bookId);
  if (from < 1 || to < from) throw new Error('bad toc range');
  if (from > 1) return { from, to, totalPages: 1, pages: [], failedPages: [] };

  const { html } = await fetchSourceHtml(shubaCatalogUrl(bookId), shubaOpts(opts, false));
  const entries = parseShubaCatalog(bookId, html);
  if (entries.length === 0) {
    throw new SourceError('目录为空或书源页面结构已变化', 'invalid', true);
  }
  let book = parseShubaBookInfo(bookId, html);
  if (!book || !book.author) {
    try {
      const info = await fetchSourceHtml(shubaInfoUrl(bookId), shubaOpts(opts, false));
      book = parseShubaBookInfo(bookId, info.html) ?? book;
    } catch (err) {
      if (opts.signal?.aborted) throw err;
      /* 书籍信息只是附带，失败不影响目录 */
    }
  }
  const result: TocRangeResult = { from, to, totalPages: 1, pages: [{ page: 1, entries }], failedPages: [] };
  if (book) {
    if (!book.firstChapterId) book.firstChapterId = entries[0]!.id;
    result.book = book;
  }
  return result;
}

export async function fetchShubaChapter(
  bookId: string,
  chapterId: string,
  opts: FetchOptions = {},
): Promise<ChapterResult> {
  const url = shubaChapterUrl(bookId, chapterId);
  const { html } = await fetchSourceHtml(url, shubaOpts(opts, opts.highPriority !== false));
  return parseShubaChapter(bookId, chapterId, html);
}

/* ------------------------------------------------------------------ */
/* 搜索（POST /modules/article/search.php，GBK 表单）                    */
/* ------------------------------------------------------------------ */

let gbkTable: Map<string, number> | null = null;

/** 用运行时自带的 GBK 解码器反推编码表（不打包码表；Node/Workers/WebView 均支持 gbk） */
function gbkEncodeTable(): Map<string, number> {
  if (gbkTable) return gbkTable;
  const map = new Map<string, number>();
  const decoder = new TextDecoder('gbk');
  const trails: number[] = [];
  for (let t = 0x40; t <= 0xfe; t++) if (t !== 0x7f) trails.push(t);
  const row = new Uint8Array(trails.length * 2);
  for (let lead = 0x81; lead <= 0xfe; lead++) {
    trails.forEach((t, i) => {
      row[i * 2] = lead;
      row[i * 2 + 1] = t;
    });
    const chars = [...decoder.decode(row)];
    if (chars.length === trails.length) {
      chars.forEach((ch, i) => {
        if (ch !== '�' && !map.has(ch)) map.set(ch, (lead << 8) | trails[i]!);
      });
    } else {
      for (const t of trails) {
        const ch = decoder.decode(new Uint8Array([lead, t]));
        if (ch.length === 1 && ch !== '�' && !map.has(ch)) map.set(ch, (lead << 8) | t);
      }
    }
  }
  gbkTable = map;
  return map;
}

const hex2 = (n: number) => n.toString(16).toUpperCase().padStart(2, '0');

/** 表单字段的 GBK 百分号编码；GBK 之外的字符按浏览器惯例提交为 &#码位; */
export function encodeGbkComponent(text: string): string {
  const table = gbkEncodeTable();
  let out = '';
  for (const ch of text) {
    const cp = ch.codePointAt(0)!;
    if (cp < 0x80) {
      out += encodeURIComponent(ch);
      continue;
    }
    const code = table.get(ch);
    out += code !== undefined ? `%${hex2(code >> 8)}%${hex2(code & 0xff)}` : encodeURIComponent(`&#${cp};`);
  }
  return out;
}

function bookNumberFromHref(href: string): string | null {
  const path = shubaPath(href);
  const m = path?.match(/^\/book\/([1-9]\d{0,8})(?:\.htm|\/)$/);
  return m ? m[1]! : null;
}

/**
 * 搜索结果页：div.newbox li（h3 a 书名 / label 作者·分类·状态 / .ellipsis_2 简介 / .zxzj 最新章）。
 * 只有一个结果时 jieqi 直接重定向到详情页（finalUrl 为 /book/{n}.htm）。
 * 认不出的页面返回 null，由调用方按错误处理。
 */
export function parseShubaSearch(html: string, finalUrl = ''): BookSummary[] | null {
  const direct = bookNumberFromHref(finalUrl);
  if (direct && /\.htm$/.test(new URL(finalUrl, SHUBA.origin).pathname)) {
    const info = parseShubaBookInfo(shubaBookId(direct), html);
    if (!info) return null;
    const { id, title, author, category, latestChapter, intro } = info;
    const one: BookSummary = { id, title };
    if (author) one.author = author;
    if (category) one.category = category;
    if (latestChapter) one.latestChapter = latestChapter;
    if (intro) one.intro = intro;
    return [one];
  }

  const region = stripScripts(divInner(html, hasClass('newbox')) ?? '');
  const out: BookSummary[] = [];
  const seen = new Set<string>();
  const liRe = /<li\b[^>]*>([\s\S]*?)<\/li\s*>/gi;
  let m: RegExpExecArray | null;
  while ((m = liRe.exec(region)) && out.length < MAX_SEARCH_RESULTS) {
    const li = m[1]!;
    const links = [...li.matchAll(/<a\b[^>]*href\s*=\s*["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi)];
    const num = links.map((a) => bookNumberFromHref(a[1]!)).find((n) => n !== null);
    if (!num || seen.has(num)) continue;
    const h3 = li.match(/<h3\b[^>]*>([\s\S]*?)<\/h3>/i)?.[1];
    const h3Link = h3?.match(/<a\b[^>]*>([\s\S]*?)<\/a>/i)?.[1];
    const title =
      textOf(h3Link ?? h3 ?? '') ||
      links.map((a) => textOf(a[2]!)).find(Boolean) ||
      decodeEntities(li.match(/<img\b[^>]*alt\s*=\s*["']([^"']*)["']/i)?.[1] ?? '').trim();
    if (!title) continue;
    seen.add(num);
    const id = shubaBookId(num);
    const b: BookSummary = { id, title };
    const labels = [...li.matchAll(/<label\b[^>]*>([\s\S]*?)<\/label>/gi)].map((l) => textOf(l[1]!));
    if (labels[0]) b.author = labels[0];
    if (labels[1]) b.category = labels[1];
    const intro = li.match(/<(\w+)\b[^>]*class\s*=\s*["'][^"']*\bellipsis_2\b[^"']*["'][^>]*>([\s\S]*?)<\/\1>/i)?.[2];
    if (intro) {
      const text = cleanShubaIntro(textOf(intro), title);
      if (text) b.intro = text;
    }
    const zxzj = li.match(/<div\b[^>]*class\s*=\s*["'][^"']*\bzxzj\b[^"']*["'][^>]*>([\s\S]*?)<\/div>/i)?.[1];
    if (zxzj) {
      const p = zxzj.match(/<p\b[^>]*>([\s\S]*?)<\/p>/i)?.[1] ?? zxzj;
      const latest = textOf(p).replace(/^最[近新]章节[：:]?\s*/, '');
      if (latest) b.latestChapter = latest;
    }
    out.push(b);
  }
  if (out.length > 0 || /\bnewbox\b/.test(html)) return out;
  if (/没有找到|没有搜索到|未找到|搜索结果为空|无结果/.test(html)) return [];
  return null;
}

let lastShubaSearchAt = 0;
let shubaSearchChain: Promise<void> = Promise.resolve();

/** 测试用：重置搜索闸门 */
export function resetShubaSearchGate(): void {
  lastShubaSearchAt = 0;
  shubaSearchChain = Promise.resolve();
}

export async function searchShuba(query: string, opts: FetchOptions = {}): Promise<BookSummary[]> {
  const q = normalizeSearchQuery(query);
  if (q === null) throw new SourceError('invalid search query', 'invalid', false);
  const sleep = opts.sleep ?? ((ms: number) => new Promise<void>((r) => setTimeout(r, ms)));
  const gate = shubaSearchChain.then(async () => {
    const wait = lastShubaSearchAt + SHUBA_MIN_SEARCH_GAP_MS - Date.now();
    if (wait > 0) await sleep(wait);
    lastShubaSearchAt = Date.now();
  });
  shubaSearchChain = gate.catch(() => undefined);
  await gate;
  const { html, finalUrl } = await fetchSourceHtml(SHUBA_SEARCH_URL, {
    ...shubaOpts(opts, true),
    form: `searchkey=${encodeGbkComponent(q)}&searchtype=all`,
  });
  const results = parseShubaSearch(html, finalUrl);
  if (results === null) {
    if (/间隔|频繁|稍后再/.test(html)) {
      throw new SourceError('书源搜索太频繁，请稍后再试', 'http', true, 429);
    }
    throw new SourceError('unexpected search page', 'invalid', true);
  }
  return results;
}
