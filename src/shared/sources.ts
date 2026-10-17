/**
 * 书源注册表：按书号分派到具体书源适配器（同构，供 API 层与客户端共用）。
 *
 * 书号格式决定书源：
 *   - 纯数字（/^[1-9]\d{0,8}$/）      → 万书阁（兼容升级前的全部数据）
 *   - `69shuba-{数字}`               → 69书吧
 *   - `ixdzs-{数字}`                 → 爱下电子书（整本下载型：下载后作为离线书保存）
 *   - `local-xxx`                    → 本地导入书（不属于任何书源）
 *
 * 书源有两种模式：online（逐章在线获取，isRemoteBookId 为真）与 download
 * （只提供书籍信息/目录/搜索，正文来自站方整本 TXT，下载后与本地书同等对待）。
 */
import {
  coverUrl,
  fetchChapter,
  fetchTocRange,
  isValidBookId,
  mergeTocEntries,
  searchBooks,
  SITE,
  SourceError,
  type BookSummary,
  type ChapterResult,
  type FetchOptions,
  type TocEntry,
  type TocRangeResult,
} from './source.ts';
import {
  fetchShubaChapter,
  fetchShubaTocRange,
  isShubaBookId,
  searchShuba,
  SHUBA,
  shubaBookId,
  shubaCoverUrl,
  shubaNumber,
} from './source-69shuba.ts';
import {
  fetchIxdzsTocRange,
  isIxdzsBookId,
  IXDZS,
  ixdzsBookId,
  ixdzsNumber,
  searchIxdzs,
} from './source-ixdzs.ts';

export type SourceId = 'wanshuge' | '69shuba' | 'ixdzs';

export interface SourceMeta {
  id: SourceId;
  name: string;
  host: string;
  /** online = 逐章在线阅读；download = 整本下载后离线阅读 */
  mode: 'online' | 'download';
}

export const SOURCES: readonly SourceMeta[] = [
  { id: 'wanshuge', name: SITE.name, host: 'wanshuge.org', mode: 'online' },
  { id: '69shuba', name: SHUBA.name, host: 'www.69shuba.com', mode: 'online' },
  { id: 'ixdzs', name: IXDZS.name, host: 'ixdzs8.com', mode: 'download' },
];

export const DEFAULT_SOURCE: SourceId = 'wanshuge';

export function isSourceId(value: unknown): value is SourceId {
  return SOURCES.some((s) => s.id === value);
}

export function sourceMeta(id: SourceId): SourceMeta {
  return SOURCES.find((s) => s.id === id)!;
}

/** 书号所属书源；本地书或非法书号返回 null */
export function sourceOfBook(bookId: string | null | undefined): SourceId | null {
  if (isValidBookId(bookId)) return 'wanshuge';
  if (isShubaBookId(bookId)) return '69shuba';
  if (isIxdzsBookId(bookId)) return 'ixdzs';
  return null;
}

/** 属于某个书源的书号（任意模式）；API 的 book 参数据此校验 */
export function isSourceBookId(id: string | null | undefined): id is string {
  return sourceOfBook(id) !== null;
}

/** 可逐章在线阅读的远程书（书架元数据在 LibraryStore，正文按需从 API 获取） */
export function isRemoteBookId(id: string | null | undefined): id is string {
  const source = sourceOfBook(id);
  return source !== null && sourceMeta(source).mode === 'online';
}

/** 整本下载型书源的书号（下载后作为离线书保存，书号保持不变以保留进度/书签） */
export function isDownloadBookId(id: string | null | undefined): id is string {
  const source = sourceOfBook(id);
  return source !== null && sourceMeta(source).mode === 'download';
}

/** 书源名称（本地书为 null） */
export function sourceNameOf(bookId: string): string | null {
  const source = sourceOfBook(bookId);
  return source ? sourceMeta(source).name : null;
}

/** 书源内的原始书号（显示用）：36780 / 51254 / 137695 */
export function sourceBookNumber(bookId: string): string {
  if (isShubaBookId(bookId)) return shubaNumber(bookId);
  if (isIxdzsBookId(bookId)) return ixdzsNumber(bookId);
  return bookId;
}

/** 占位书名（目录加载前）：“书号 36780” / “69书吧 51254” / “爱下电子书 137695” */
export function placeholderTitle(bookId: string): string {
  const source = sourceOfBook(bookId);
  if (source && source !== 'wanshuge') return `${sourceMeta(source).name} ${sourceBookNumber(bookId)}`;
  return `书号 ${bookId}`;
}

export function bookCoverUrl(bookId: string): string | null {
  switch (sourceOfBook(bookId)) {
    case 'wanshuge':
      return coverUrl(bookId);
    case '69shuba':
      return shubaCoverUrl(bookId);
    default:
      return null;
  }
}

export function fetchBookTocRange(
  bookId: string,
  from: number,
  to: number,
  opts: FetchOptions = {},
): Promise<TocRangeResult> {
  if (isShubaBookId(bookId)) return fetchShubaTocRange(bookId, from, to, opts);
  if (isIxdzsBookId(bookId)) return fetchIxdzsTocRange(bookId, from, to, opts);
  return fetchTocRange(bookId, from, to, opts);
}

export async function fetchBookChapter(bookId: string, chapterId: string, opts: FetchOptions = {}): Promise<ChapterResult> {
  if (isShubaBookId(bookId)) return fetchShubaChapter(bookId, chapterId, opts);
  if (isDownloadBookId(bookId)) {
    // 站点章节页有安全验证（不绕过）：只能整本下载后离线阅读，不发上游请求
    throw new SourceError(`${sourceNameOf(bookId)}的书需要先整本下载，下载后可离线阅读`, 'invalid', false);
  }
  return fetchChapter(bookId, chapterId, opts);
}

export function searchSource(source: SourceId, query: string, opts: FetchOptions = {}): Promise<BookSummary[]> {
  if (source === '69shuba') return searchShuba(query, opts);
  if (source === 'ixdzs') return searchIxdzs(query, opts);
  return searchBooks(query, opts);
}

/**
 * 合并目录页：
 *  - 万书阁：多页 + 源站错号/重传启发式（mergeTocEntries）；
 *  - 其它（69书吧 / 爱下电子书 / 本地 TXT）：目录本身即权威顺序，只按 id 去重。
 *    本地书若套用万书阁启发式，会把同号同名的章节（如番外重新从第一章编号）当重复丢掉。
 */
export function mergeBookToc(bookId: string, pages: TocEntry[][]): TocEntry[] {
  if (sourceOfBook(bookId) === 'wanshuge') return mergeTocEntries(pages);
  const seen = new Set<string>();
  const out: TocEntry[] = [];
  for (const page of pages) {
    for (const entry of page) {
      if (seen.has(entry.id)) continue;
      seen.add(entry.id);
      out.push(entry);
    }
  }
  return out;
}

export interface BookUrlRef {
  bookId: string;
  chapterId?: string;
}

/** 69书吧旧域名/镜像都会 301 到 www.69shuba.com，书号与路径一致 */
const SHUBA_LINK_HOSTS = new Set(['www.69shuba.com', '69shuba.com', 'www.69shuba.cx', '69shuba.cx', 'www.69shu.com', '69shu.com']);
const WANSHUGE_LINK_HOSTS = new Set(['wanshuge.org', 'www.wanshuge.org']);
/** 爱下电子书简体站与繁体镜像（书号一致） */
const IXDZS_LINK_HOSTS = new Set(['ixdzs8.com', 'www.ixdzs8.com', 'ixdzs8.tw', 'ixdzs.tw', 'ixdzs.hk']);

/**
 * 把用户粘贴的书籍/章节网址解析为书号（只做本地解析，不发请求）：
 *   https://www.69shuba.com/book/51254/      → 69shuba-51254
 *   https://www.69shuba.com/book/51254.htm   → 69shuba-51254
 *   https://www.69shuba.com/txt/51254/123    → 69shuba-51254 + 章节 123
 *   https://wanshuge.org/book/36780/         → 36780（章节页同理）
 *   https://ixdzs8.com/read/137695/          → ixdzs-137695（p{n}.html 为章节）
 */
export function parseBookUrl(input: string): BookUrlRef | null {
  let raw = (input ?? '').trim();
  if (!raw || raw.length > 300 || /\s/.test(raw)) return null;
  if (!/^https?:\/\//i.test(raw)) {
    if (!/^(?:www\.)?[a-z0-9.-]+\.[a-z]{2,}\//i.test(raw)) return null;
    raw = `https://${raw}`;
  }
  let url: URL;
  try {
    url = new URL(raw);
  } catch {
    return null;
  }
  const host = url.hostname.toLowerCase();
  const path = url.pathname;
  if (SHUBA_LINK_HOSTS.has(host)) {
    const book = path.match(/^\/book\/([1-9]\d{0,8})(?:\.htm|\/)?$/);
    if (book) return { bookId: shubaBookId(book[1]!) };
    const txt = path.match(/^\/txt\/([1-9]\d{0,8})\/(\d{1,12})(?:\.html?)?\/?$/);
    if (txt) return { bookId: shubaBookId(txt[1]!), chapterId: txt[2]! };
    return null;
  }
  if (IXDZS_LINK_HOSTS.has(host)) {
    const book = path.match(/^\/read\/([1-9]\d{0,8})\/?$/);
    if (book) return { bookId: ixdzsBookId(book[1]!) };
    const chapter = path.match(/^\/read\/([1-9]\d{0,8})\/p(\d{1,12})\.html$/);
    if (chapter) return { bookId: ixdzsBookId(chapter[1]!), chapterId: chapter[2]! };
    return null;
  }
  if (WANSHUGE_LINK_HOSTS.has(host)) {
    const book = path.match(/^\/book\/([1-9]\d{0,8})\/?$/);
    if (book) return { bookId: book[1]! };
    const first = path.match(/^\/book\/([1-9]\d{0,8})_(\d{1,12})\.html$/);
    if (first) return { bookId: first[1]!, chapterId: first[2]! };
    const later = path.match(/^\/book\/([1-9]\d{0,8})\/(\d{6,12})(?:_\d{1,2})?\.html$/);
    if (later) return { bookId: later[1]!, chapterId: later[2]! };
    const tocPage = path.match(/^\/book\/([1-9]\d{0,8})\/\d{1,5}\.html$/);
    if (tocPage) return { bookId: tocPage[1]! };
    return null;
  }
  return null;
}
