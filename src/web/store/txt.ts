/**
 * 本地 TXT 导入 / 导出（书源不可用时的兜底路径）。
 * 分块解析，避免长任务阻塞主线程；不使用 Worker（收益不足）。
 */
import type { TocEntry } from '../../shared/source.ts';
import { normalizeTitle } from '../../shared/source.ts';
import { putChapter, putToc, listChapters, type ChapterRecord } from './db.ts';

export interface LocalBookMeta {
  id: string;
  title: string;
  chapters: number;
  bytes: number;
  importedAt: number;
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

  const entries: TocEntry[] = [];
  let bytesWritten = 0;
  for (let i = 0; i < rawChapters.length; i++) {
    const chap = rawChapters[i]!;
    const chapterId = String(i + 1);
    const paragraphs = chap.lines.filter((l) => l.length > 0);
    const info = normalizeTitle(chap.title);
    entries.push({
      id: chapterId,
      title: chap.title,
      displayTitle: info.displayTitle,
      number: info.number,
      extra: info.extra,
    });
    const joined = paragraphs.join('');
    bytesWritten += new TextEncoder().encode(joined).length;
    const record: ChapterRecord = {
      key: `${bookId}:${chapterId}`,
      bookId,
      chapterId,
      title: chap.title,
      paragraphs,
      charCount: joined.length,
      source: 'local',
      fetchedAt: Date.now(),
      bytes: new TextEncoder().encode(joined).length,
    };
    await putChapter(record);
    onProgress?.(i + 1, rawChapters.length);
    if ((i + 1) % 40 === 0) await new Promise((r) => setTimeout(r, 0));
  }

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
    updatedAt: Date.now(),
  });
  removeLocalBookMeta(bookId);
  saveLocalBooks([
    { id: bookId, title: baseName, chapters: entries.length, bytes: bytesWritten, importedAt: Date.now() },
    ...listLocalBooks(),
  ]);
  return { bookId, title: baseName, chapters: entries.length, bytes };
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
