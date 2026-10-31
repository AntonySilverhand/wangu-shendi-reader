/**
 * 换源补全：当前书源的章节是占位/空章时，自动从其它来源取同一章用于展示。
 *
 * 来源顺序：本机已有的同名书（整本下载/导入，离线、零请求）→ 在线书源（飘天文学 → 万书阁 → 69书吧，
 * 排除当前书源）。各站采集自不同上游，章节号常常对不上（同一章在不同站相差几十号，
 * 甚至同站内中文/阿拉伯数字混用），所以按“章名”匹配、同名时按全书相对位置取最近的一章，
 * 只有章名为空时才按章节号匹配。书名必须一致（不取搜索结果第一条），作者已知时优先同作者。
 *
 * 结果只用于展示，不写入 IndexedDB（不覆盖原书缓存与数据）；内存里按章缓存，
 * 同一本书在各书源的匹配结果与目录也缓存，连续几章占位时不重复搜索。
 */

import { apiGet } from './remote.ts';
import { getChapter, getToc } from './db.ts';
import { listLocalBooks } from './txt.ts';
import type { ChapterResult, TocEntry, TocRangeResult } from '../../shared/source.ts';
import type { SearchResponse } from '../../shared/api.ts';
import { sourceMeta, sourceOfBook, type SourceId } from '../../shared/sources.ts';
import { cleanParagraphs, countChars, EMPTY_CHAPTER_NOTE } from '../../shared/text-clean.ts';

export interface AltChapterResult {
  paragraphs: string[];
  charCount: number;
  title: string;
  sourceName: string;
  altBookId: string;
}

export interface AltRequest {
  bookId: string;
  bookTitle: string;
  author?: string;
  chapterId: string;
  chapterTitle: string;
  /** 本章在原书目录中的相对位置（0–1），用于同名章节消歧与估算万书阁目录页 */
  position: number | null;
}

/** 在线换源的尝试顺序：飘天文学正文完整、无验证，排第一；69书吧常有人机验证，排最后 */
const ONLINE_PRIORITY: readonly SourceId[] = ['piaotia', 'wanshuge', '69shuba'];
/** 同名章节的相对位置最多相差这么多仍可认定为同一章（各站章节总数不同、番外位置不同） */
const MAX_POSITION_DRIFT = 0.15;
const MIN_INDEX_DRIFT = 5;
const MAX_TOC_RANGE = 6;
const NEGATIVE_TTL_MS = 10 * 60_000;

export function altOnlineSources(bookId: string): SourceId[] {
  const current = sourceOfBook(bookId);
  return ONLINE_PRIORITY.filter((s) => s !== current && sourceMeta(s).mode === 'online');
}

/** 当前书有没有可尝试的换源来源 */
export function hasAltSources(bookId: string, bookTitle: string): boolean {
  return altOnlineSources(bookId).length > 0 || localCopies(bookId, bookTitle).length > 0;
}

export function titleKey(text: string): string {
  return text.replace(/[\s　:：,，.。、·・_\-—~～!！?？'"“”‘’()（）【】[\]《》<>]/g, '').toLowerCase();
}

const CHAPTER_PREFIX = /^(?:正文\s*)?(?:番外|外传)?\s*第\s*([零〇一二三四五六七八九十百千万两\d]+)\s*[章节回话掌卷]/;

/** 章名（去掉“第N章”前缀后的部分）；没有章名时为空串 */
export function chapterName(title: string): string {
  const t = title.trim().replace(/^\d{1,5}[\s.、，]+(?=第)/, '');
  return titleKey(t.replace(CHAPTER_PREFIX, ''));
}

function isEmptyChapter(paragraphs: string[]): boolean {
  if (paragraphs.length === 0) return true;
  const cleaned = cleanParagraphs(paragraphs);
  return cleaned.length === 1 && cleaned[0] === EMPTY_CHAPTER_NOTE;
}

/**
 * 在目录里找同一章：章名相同的候选里取相对位置最近的一个（超出漂移上限则不认）；
 * 章名为空时按章节号相同 + 位置最近。
 */
export function matchChapter(entries: readonly TocEntry[], chapterTitle: string, position: number | null): TocEntry | null {
  if (entries.length === 0) return null;
  const name = chapterName(chapterTitle);
  let candidates: number[] = [];
  if (name) {
    entries.forEach((e, i) => {
      if (chapterName(e.title) === name || chapterName(e.displayTitle) === name) candidates.push(i);
    });
  } else {
    const num = chapterTitle.match(CHAPTER_PREFIX)?.[1];
    const n = num ? (/^\d+$/.test(num) ? Number(num) : null) : null;
    if (n === null) return null;
    entries.forEach((e, i) => {
      if (e.number === n && !chapterName(e.title)) candidates.push(i);
    });
  }
  if (candidates.length === 0) return null;
  if (position === null) return candidates.length === 1 ? entries[candidates[0]!]! : null;
  const last = Math.max(1, entries.length - 1);
  candidates = candidates.sort((a, b) => Math.abs(a / last - position) - Math.abs(b / last - position));
  const best = candidates[0]!;
  // 允许的偏差：全书的 15%，短书至少几章（各站番外/序章位置不同）
  const tolerance = Math.max(MAX_POSITION_DRIFT * last, MIN_INDEX_DRIFT);
  return Math.abs(best - position * last) <= tolerance ? entries[best]! : null;
}

/** 别站正文首行常是它自己的章节标题（可能是原书的号码，如“第3602章 不周山下”），与页面标题重复，去掉 */
function stripLeadingTitle(paragraphs: string[], ...titles: string[]): string[] {
  const first = paragraphs[0];
  if (!first || paragraphs.length < 2 || first.length > 40 || !CHAPTER_PREFIX.test(first.trim())) return paragraphs;
  const name = chapterName(first);
  const same = titles.some((t) => titleKey(t) === titleKey(first) || (name !== '' && chapterName(t) === name));
  return same ? paragraphs.slice(1) : paragraphs;
}

/* ------------------------------------------------------------------ */
/* 本机副本                                                             */
/* ------------------------------------------------------------------ */

function localCopies(bookId: string, bookTitle: string): string[] {
  const key = titleKey(bookTitle);
  if (!key) return [];
  return listLocalBooks()
    .filter((b) => b.id !== bookId && titleKey(b.title) === key)
    .map((b) => b.id);
}

async function fromLocalCopy(copyId: string, req: AltRequest): Promise<AltChapterResult | null> {
  const toc = await getToc(copyId);
  if (!toc) return null;
  const entries = Object.keys(toc.pages)
    .map(Number)
    .sort((a, b) => a - b)
    .flatMap((p) => toc.pages[String(p)] ?? []);
  const match = matchChapter(entries, req.chapterTitle, req.position);
  if (!match) return null;
  const record = await getChapter(copyId, match.id);
  if (!record || isEmptyChapter(record.paragraphs)) return null;
  const paragraphs = stripLeadingTitle(record.paragraphs, req.chapterTitle, record.title);
  return {
    paragraphs,
    charCount: countChars(paragraphs),
    title: record.title,
    sourceName: '本机已下载的同名书',
    altBookId: copyId,
  };
}

/* ------------------------------------------------------------------ */
/* 在线书源                                                             */
/* ------------------------------------------------------------------ */

interface AltBook {
  bookId: string;
  totalPages: number;
  pages: Map<number, TocEntry[]>;
  /** 目录加载排队：同一本书的并发换源不重复取同一页 */
  queue: Promise<void>;
}

const bookCache = new Map<string, AltBook | { missingUntil: number }>();
const bookLookups = new Map<string, Promise<AltBook | null>>();

/** 同一本书在同一书源只搜一次（连续几章占位、预取换源同时进行时合并请求） */
function findBook(source: SourceId, req: AltRequest, signal?: AbortSignal): Promise<AltBook | null> {
  const cacheKey = `${source}|${titleKey(req.bookTitle)}|${titleKey(req.author ?? '')}`;
  const cached = bookCache.get(cacheKey);
  if (cached && 'bookId' in cached) return Promise.resolve(cached);
  if (cached && cached.missingUntil > Date.now()) return Promise.resolve(null);
  const pending = bookLookups.get(cacheKey);
  if (pending) return pending;
  const lookup = searchBook(source, req, cacheKey, signal);
  bookLookups.set(cacheKey, lookup);
  void lookup.then(
    () => bookLookups.delete(cacheKey),
    () => bookLookups.delete(cacheKey),
  );
  return lookup;
}

async function searchBook(source: SourceId, req: AltRequest, cacheKey: string, signal?: AbortSignal): Promise<AltBook | null> {
  const search = await apiGet<SearchResponse>(
    `/api/search?q=${encodeURIComponent(req.bookTitle)}&source=${source}`,
    { signal, retries: 0, timeoutMs: 20_000 },
  );
  const key = titleKey(req.bookTitle);
  const same = search.results.filter((r) => titleKey(r.title) === key);
  const author = titleKey(req.author ?? '');
  const sameAuthor = (r: { author?: string }) => !author || !r.author || titleKey(r.author) === author;
  const hit = (author ? same.find((r) => r.author && titleKey(r.author) === author) : undefined) ?? same.find(sameAuthor);
  if (!hit) {
    bookCache.set(cacheKey, { missingUntil: Date.now() + NEGATIVE_TTL_MS });
    return null;
  }
  const book: AltBook = { bookId: hit.id, totalPages: 0, pages: new Map(), queue: Promise.resolve() };
  bookCache.set(cacheKey, book);
  return book;
}

function loadTocPages(book: AltBook, from: number, to: number, signal?: AbortSignal): Promise<void> {
  const run = book.queue.then(() => loadMissingTocPages(book, from, to, signal));
  book.queue = run.catch(() => undefined);
  return run;
}

async function loadMissingTocPages(book: AltBook, from: number, to: number, signal?: AbortSignal): Promise<void> {
  let start = from;
  while (start <= to) {
    while (start <= to && book.pages.has(start)) start++;
    if (start > to) break;
    let end = start;
    while (end < to && end - start + 1 < MAX_TOC_RANGE && !book.pages.has(end + 1)) end++;
    const toc = await apiGet<TocRangeResult>(
      `/api/toc?book=${encodeURIComponent(book.bookId)}&from=${start}&to=${end}`,
      { signal, retries: 0, timeoutMs: 20_000 },
    );
    if (toc.totalPages > 0) book.totalPages = toc.totalPages;
    for (const p of toc.pages) book.pages.set(p.page, p.entries);
    for (let p = start; p <= end; p++) if (!book.pages.has(p)) book.pages.set(p, []);
    start = end + 1;
  }
}

function orderedEntries(book: AltBook): TocEntry[] {
  return [...book.pages.keys()].sort((a, b) => a - b).flatMap((p) => book.pages.get(p) ?? []);
}

/**
 * 在线书源里找同一章。单页目录（飘天文学/69书吧）一次拿全；万书阁目录分页（《万古神帝》44 页），
 * 先取前 2 页拿总页数，再按相对位置估算所在页、取前后各 2 页。
 * 只拿到部分目录时，相对位置按估算页换算成全书位置后再比较。
 */
async function findOnlineChapter(source: SourceId, req: AltRequest, signal?: AbortSignal): Promise<{ bookId: string; entry: TocEntry } | null> {
  const book = await findBook(source, req, signal);
  if (!book) return null;
  await loadTocPages(book, 1, 2, signal);
  const total = Math.max(1, book.totalPages);
  if (total <= 2 || req.position === null) {
    const entry = matchChapter(orderedEntries(book), req.chapterTitle, total <= 2 ? req.position : null);
    return entry ? { bookId: book.bookId, entry } : null;
  }
  const est = Math.min(total, Math.max(1, Math.round(req.position * total + 0.5)));
  const from = Math.max(1, est - 2);
  const to = Math.min(total, est + 2);
  await loadTocPages(book, from, to, signal);
  const windowEntries: TocEntry[] = [];
  for (let p = from; p <= to; p++) windowEntries.push(...(book.pages.get(p) ?? []));
  // 窗口内的相对位置：把全书位置映射到窗口坐标
  const windowPos = Math.min(1, Math.max(0, (req.position * total - (from - 1)) / (to - from + 1)));
  const entry = matchChapter(windowEntries, req.chapterTitle, windowPos);
  return entry ? { bookId: book.bookId, entry } : null;
}

async function fromOnline(source: SourceId, req: AltRequest, signal?: AbortSignal): Promise<AltChapterResult | null> {
  const found = await findOnlineChapter(source, req, signal);
  if (!found) return null;
  const chapter = await apiGet<ChapterResult>(
    `/api/chapter?book=${encodeURIComponent(found.bookId)}&id=${encodeURIComponent(found.entry.id)}`,
    { signal, retries: 0, timeoutMs: 25_000 },
  );
  if (isEmptyChapter(chapter.paragraphs)) return null;
  const paragraphs = stripLeadingTitle(chapter.paragraphs, req.chapterTitle, chapter.title);
  return {
    paragraphs,
    charCount: countChars(paragraphs),
    title: chapter.title,
    sourceName: sourceMeta(source).name,
    altBookId: found.bookId,
  };
}

/* ------------------------------------------------------------------ */
/* 入口                                                                 */
/* ------------------------------------------------------------------ */

const resultCache = new Map<string, AltChapterResult | null>();
const inflight = new Map<string, Promise<AltChapterResult | null>>();

const resultKey = (bookId: string, chapterId: string) => `${bookId}:${chapterId}`;

/** 本次会话里已取到的换源结果（已知“各处都没有”时返回 null，未尝试过返回 undefined） */
export function cachedAltChapter(bookId: string, chapterId: string): AltChapterResult | null | undefined {
  return resultCache.get(resultKey(bookId, chapterId));
}

/**
 * 依次尝试本机副本与各在线书源，返回第一个有正文的结果（原文，展示前由调用方按设置清洗）。
 * 单个来源失败（网络、人机验证、页面变化）不中断，继续下一个；同一章的并发请求合并。
 */
export function fetchAltChapterAuto(req: AltRequest, signal?: AbortSignal): Promise<AltChapterResult | null> {
  const key = resultKey(req.bookId, req.chapterId);
  if (!titleKey(req.bookTitle)) return Promise.resolve(null);
  if (resultCache.has(key)) return Promise.resolve(resultCache.get(key)!);
  const pending = inflight.get(key);
  if (pending) return pending;
  const task = (async () => {
    let anyFailed = false;
    for (const copy of localCopies(req.bookId, req.bookTitle)) {
      try {
        const r = await fromLocalCopy(copy, req);
        if (r) return r;
      } catch {
        anyFailed = true;
      }
    }
    for (const source of altOnlineSources(req.bookId)) {
      if (signal?.aborted) return null;
      try {
        const r = await fromOnline(source, req, signal);
        if (r) return r;
      } catch {
        if (signal?.aborted) return null;
        anyFailed = true;
      }
    }
    // 只有所有来源都明确“没有这一章”才记为无结果；有来源出错时下次还会再试
    if (!anyFailed && !signal?.aborted) resultCache.set(key, null);
    return null;
  })().then((r) => {
    if (r) resultCache.set(key, r);
    return r;
  });
  inflight.set(key, task);
  void task.finally(() => inflight.delete(key));
  return task;
}

/** 测试用：清空会话缓存 */
export function resetAltSourceCache(): void {
  bookCache.clear();
  bookLookups.clear();
  resultCache.clear();
  inflight.clear();
}
