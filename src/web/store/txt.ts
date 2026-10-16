/**
 * 本地 TXT 导入 / 导出（书源不可用时的兜底路径）。
 * 分块解析，避免长任务阻塞主线程；不使用 Worker（收益不足）。
 */
import type { TocEntry } from '../../shared/source.ts';
import { normalizeTitle } from '../../shared/source.ts';
import { deleteChapterIds, listChapterIds, listChapters, putChapters, putToc, type ChapterRecord } from './db.ts';

export interface LocalBookMeta {
  /** local-xxx（TXT 导入）或书源整本下载的书号（如 ixdzs-137695，重新下载时保持不变） */
  id: string;
  title: string;
  chapters: number;
  bytes: number;
  importedAt: number;
  author?: string;
  /** 整本下载来源的书源 id */
  source?: string;
}

const LOCAL_BOOKS_KEY = 'reader.localbooks.v1';
const LOCAL_PAGES_PER_CHUNK = 100;

export function listLocalBooks(): LocalBookMeta[] {
  try {
    const raw = localStorage.getItem(LOCAL_BOOKS_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as LocalBookMeta[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function saveLocalBooks(books: LocalBookMeta[]): void {
  try {
    localStorage.setItem(LOCAL_BOOKS_KEY, JSON.stringify(books));
  } catch {
    /* 忽略 */
  }
}

function removeLocalBookMeta(id: string): void {
  saveLocalBooks(listLocalBooks().filter((b) => b.id !== id));
}

const HEADING_RE =
  /^[ \t　]*(?:(?:番外|外传)?第[零〇一二三四五六七八九十百千万两\d]+[章节回卷][^\n]{0,60}|(?:序章|序言|楔子|尾声|后记|完本感言|番外|致读者)[^。！？!?\n]{0,14}|Chapter\s+\d+[^\n]{0,60}|[0-9]{1,4}[、.．][^\n]{0,60})[ \t　]*$/;

interface RawChapter {
  title: string;
  lines: string[];
}

export function splitTxt(text: string): RawChapter[] {
  const lines = text.replace(/\r\n?/g, '\n').split('\n');
  const chapters: RawChapter[] = [];
  let current: RawChapter | null = null;
  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (HEADING_RE.test(line)) {
      current = { title: line, lines: [] };
      chapters.push(current);
      continue;
    }
    if (!current) {
      if (line) {
        current = { title: '正文', lines: [] };
        chapters.push(current);
      } else continue;
    }
    if (line) current.lines.push(line);
  }
  return chapters;
}

export interface ImportResult {
  bookId: string;
  title: string;
  chapters: number;
  bytes: number;
}

export interface LocalChapterInput {
  id: string;
  title: string;
  paragraphs: string[];
}

export interface LocalBookInput {
  bookId: string;
  title: string;
  author?: string;
  /** 来自书源整本下载时的书源 id（如 ixdzs），本地 TXT 导入为空 */
  source?: string;
  chapters: LocalChapterInput[];
}

/** 每批写入的章节数（一个 IndexedDB 事务） */
const SAVE_BATCH = 200;

/**
 * 把整本书写入 IndexedDB 并登记为本地书（TXT 导入与书源整本下载共用）。
 * 同一 bookId 再次保存（重新下载/更新）时：先覆盖写入新章节，再删除新版本里没有的旧章节，
 * 最后写目录与书籍记录——中途取消时旧书仍可读。
 */
export async function saveLocalBook(
  input: LocalBookInput,
  onProgress?: (done: number, total: number) => void,
): Promise<LocalBookMeta> {
  const { bookId, chapters } = input;
  const entries: TocEntry[] = [];
  const encoder = new TextEncoder();
  let bytesWritten = 0;
  const now = Date.now();
  for (let i = 0; i < chapters.length; i += SAVE_BATCH) {
    const batch: ChapterRecord[] = [];
    for (const chap of chapters.slice(i, i + SAVE_BATCH)) {
      const paragraphs = chap.paragraphs.filter((l) => l.length > 0);
      const info = normalizeTitle(chap.title);
      entries.push({
        id: chap.id,
        title: chap.title,
        displayTitle: info.displayTitle,
        number: info.number,
        extra: info.extra,
      });
      const joined = paragraphs.join('');
      const bytes = encoder.encode(joined).length;
      bytesWritten += bytes;
      batch.push({
        key: `${bookId}:${chap.id}`,
        bookId,
        chapterId: chap.id,
        title: chap.title,
        paragraphs,
        charCount: joined.length,
        source: 'local',
        fetchedAt: now,
        bytes,
      });
    }
    await putChapters(batch);
    onProgress?.(Math.min(i + SAVE_BATCH, chapters.length), chapters.length);
    await new Promise((r) => setTimeout(r, 0));
  }

  const keep = new Set(entries.map((e) => e.id));
  const stale = await listChapterIds(bookId).catch(() => new Set<string>());
  const remove = [...stale].filter((id) => !keep.has(id));
  if (remove.length > 0) await deleteChapterIds(bookId, remove);

  // 目录按固定页大小写入，与在线书结构保持一致
  const pages: Record<string, TocEntry[]> = {};
  for (let i = 0; i < entries.length; i += LOCAL_PAGES_PER_CHUNK) {
    const page = Math.floor(i / LOCAL_PAGES_PER_CHUNK) + 1;
    pages[String(page)] = entries.slice(i, i + LOCAL_PAGES_PER_CHUNK);
  }
  await putToc({
    bookId,
    pages,
    totalPages: Math.ceil(entries.length / LOCAL_PAGES_PER_CHUNK),
    updatedAt: now,
  });
  const meta: LocalBookMeta = {
    id: bookId,
    title: input.title.slice(0, 100),
    chapters: entries.length,
    bytes: bytesWritten,
    importedAt: now,
  };
  if (input.author) meta.author = input.author.slice(0, 60);
  if (input.source) meta.source = input.source;
  removeLocalBookMeta(bookId);
  saveLocalBooks([meta, ...listLocalBooks()]);
  return meta;
}

export async function importTxtFile(
  file: File,
  onProgress?: (done: number, total: number) => void,
): Promise<ImportResult> {
  const text = await file.text();
  const bytes = new TextEncoder().encode(text).length;
  const baseName = file.name.replace(/\.[^.]+$/, '').slice(0, 40) || '本地导入';
  const bookId = `local-${Date.now().toString(36)}`;
  const rawChapters = splitTxt(text);
  if (rawChapters.length === 0) throw new Error('没有识别到任何章节');
  const meta = await saveLocalBook(
    {
      bookId,
      title: baseName,
      chapters: rawChapters.map((chap, i) => ({ id: String(i + 1), title: chap.title, paragraphs: chap.lines })),
    },
    onProgress,
  );
  return { bookId, title: baseName, chapters: meta.chapters, bytes };
}

/** 把已缓存的章节导出为单个 TXT（按目录顺序） */
export async function exportCachedTxt(
  bookId: string,
  entries: TocEntry[],
): Promise<{ text: string; count: number }> {
  const records = await listChapters(bookId);
  const byId = new Map(records.map((r) => [r.chapterId, r]));
  const parts: string[] = [];
  let count = 0;
  for (const entry of entries) {
    const rec = byId.get(entry.id);
    if (!rec) continue;
    parts.push(`${entry.displayTitle}\n\n${rec.paragraphs.join('\n')}\n`);
    count++;
  }
  return { text: parts.join('\n'), count };
}
