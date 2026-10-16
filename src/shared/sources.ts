/**
 * 书源注册表：按书号分派到具体书源适配器（同构，供 API 层与客户端共用）。
 *
 * 书号格式决定书源：
 *   - 纯数字（/^[1-9]\d{0,8}$/）      → 万书阁（兼容升级前的全部数据）
 *   - `69shuba-{数字}`               → 69书吧
 *   - `local-xxx`                    → 本地导入书（不属于任何书源）
 */
import {
  coverUrl,
  fetchChapter,
  fetchTocRange,
  isValidBookId,
  mergeTocEntries,
  searchBooks,
  SITE,
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

export type SourceId = 'wanshuge' | '69shuba';

export interface SourceMeta {
  id: SourceId;
  name: string;
  host: string;
}

export const SOURCES: readonly SourceMeta[] = [
  { id: 'wanshuge', name: SITE.name, host: 'wanshuge.org' },
  { id: '69shuba', name: SHUBA.name, host: 'www.69shuba.com' },
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
  return null;
}

/** 远程书（属于某个书源）的书号 */
export function isRemoteBookId(id: string | null | undefined): id is string {
  return sourceOfBook(id) !== null;
}

/** 书源名称（本地书为 null） */
export function sourceNameOf(bookId: string): string | null {
  const source = sourceOfBook(bookId);
  return source ? sourceMeta(source).name : null;
}

/** 书源内的原始书号（显示用）：36780 / 51254 */
export function sourceBookNumber(bookId: string): string {
  return isShubaBookId(bookId) ? shubaNumber(bookId) : bookId;
}

/** 占位书名（目录加载前）：“书号 36780” / “69书吧 51254” */
export function placeholderTitle(bookId: string): string {
  const source = sourceOfBook(bookId);
  if (source === '69shuba') return `${SHUBA.name} ${shubaNumber(bookId)}`;
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
  return fetchTocRange(bookId, from, to, opts);
}

export function fetchBookChapter(bookId: string, chapterId: string, opts: FetchOptions = {}): Promise<ChapterResult> {
  if (isShubaBookId(bookId)) return fetchShubaChapter(bookId, chapterId, opts);
  return fetchChapter(bookId, chapterId, opts);
}

export function searchSource(source: SourceId, query: string, opts: FetchOptions = {}): Promise<BookSummary[]> {
  if (source === '69shuba') return searchShuba(query, opts);
  return searchBooks(query, opts);
}

/**
 * 合并目录页：
 *  - 万书阁：多页 + 源站错号/重传启发式（mergeTocEntries）；
 *  - 69书吧：目录页即权威顺序，只按 id 去重。
 */
export function mergeBookToc(bookId: string, pages: TocEntry[][]): TocEntry[] {
  if (!isShubaBookId(bookId)) return mergeTocEntries(pages);
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

/**
 * 把用户粘贴的书籍/章节网址解析为书号（只做本地解析，不发请求）：
 *   https://www.69shuba.com/book/51254/      → 69shuba-51254
 *   https://www.69shuba.com/book/51254.htm   → 69shuba-51254
 *   https://www.69shuba.com/txt/51254/123    → 69shuba-51254 + 章节 123
 *   https://wanshuge.org/book/36780/         → 36780（章节页同理）
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
