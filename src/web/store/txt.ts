/**
 * 本地 TXT 导入 / 导出（书源不可用时的兜底路径）。
 * 分块解析，避免长任务阻塞主线程；不使用 Worker（收益不足）。
 */
import type { TocEntry } from '../../shared/source.ts';
import { normalizeTitle } from '../../shared/source.ts';
import { splitIxdzsTxt } from '../../shared/source-ixdzs.ts';
import { cleanParagraphs } from '../../shared/text-clean.ts';
import { decodeTextBytes, extractFirstTxt } from '../../shared/zip.ts';
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
/** 文件选择器的 accept：TXT 与含 TXT 的 ZIP（Android 壳的原生选择器不按此过滤，导入时按内容识别） */
export const TXT_ACCEPT = '.txt,.zip,text/plain,application/zip';
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
      // 存原文：采集站的分页提示/广告在读取时按“清理站点广告”设置清洗（chapter.ts），关掉即可看到原文
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

/** 导入文件上限：整本 TXT（万古神帝约 32MB）或其压缩包 */
export const MAX_IMPORT_BYTES = 96 * 1024 * 1024;

export interface PreparedImport {
  title: string;
  author?: string;
  chapters: LocalChapterInput[];
  /** 解码后的文本字节数（UTF-8 计） */
  bytes: number;
}

/** 爱下电子书等站方 TXT 的头：『书名/作者:xxx』 */
const HEADER_RE = /^『([^/『』]{1,60})\/作者[:：]([^『』]{1,40})』$/;

/**
 * 把用户选的文件（.txt 或含 .txt 的 .zip）解析成章节（纯函数，便于测试）：
 *  - 编码：BOM → 严格 UTF-8 → GB18030（国内 TXT 多为 GBK，旧版按 UTF-8 读会乱码）
 *  - 爱下电子书格式（“章节内容开始”分隔线 + 顶格标题/缩进正文）按顶格行切章，书名/作者取自文件头
 *  - 其它 TXT 用通用标题规则
 */
export async function prepareTxtImport(fileName: string, data: Uint8Array): Promise<PreparedImport> {
  if (data.length > MAX_IMPORT_BYTES) throw new Error('文件过大（上限 96MB）');
  let name = fileName;
  let bytes = data;
  const isZip = /\.zip$/i.test(fileName) || (data[0] === 0x50 && data[1] === 0x4b && data[2] === 0x03 && data[3] === 0x04);
  if (isZip) {
    try {
      const entry = await extractFirstTxt(data);
      name = entry.name.split('/').pop() ?? entry.name;
      bytes = entry.data;
    } catch (err) {
      throw new Error(`压缩包里没有可导入的 TXT（${err instanceof Error ? err.message : String(err)}）`);
    }
  }
  const text = decodeTextBytes(bytes);
  let title = name.replace(/\.[^.]+$/, '').slice(0, 40) || '本地导入';
  let author: string | undefined;
  const head = text.slice(0, 400).replace(/\r/g, '').split('\n')[0]?.trim() ?? '';
  const header = head.match(HEADER_RE);
  if (header) {
    title = header[1]!.trim();
    author = header[2]!.trim();
  }

  let chapters: LocalChapterInput[];
  if (/^-{3,}\s*章节内容开始\s*-{3,}\s*$/m.test(text.slice(0, 20_000))) {
    chapters = splitIxdzsTxt(text, []).chapters;
  } else {
    chapters = splitTxt(text).map((chap, i) => ({ id: String(i + 1), title: chap.title, paragraphs: chap.lines }));
  }
  chapters = chapters.filter((c) => c.paragraphs.length > 0 || chapters.length === 1);
  if (chapters.length === 0) throw new Error('没有识别到任何章节');
  return { title, author, chapters, bytes: bytes.length };
}

export async function importTxtFile(
  file: File,
  onProgress?: (done: number, total: number) => void,
): Promise<ImportResult> {
  if (file.size > MAX_IMPORT_BYTES) throw new Error('文件过大（上限 96MB）');
  const prepared = await prepareTxtImport(file.name, new Uint8Array(await file.arrayBuffer()));
  const bookId = `local-${Date.now().toString(36)}`;
  const meta = await saveLocalBook(
    { bookId, title: prepared.title, author: prepared.author, chapters: prepared.chapters },
    onProgress,
  );
  return { bookId, title: prepared.title, chapters: meta.chapters, bytes: prepared.bytes };
}

/** 把已缓存的章节导出为单个 TXT（按目录顺序） */
export async function exportCachedTxt(
  bookId: string,
  entries: TocEntry[],
  clean = true,
): Promise<{ text: string; count: number }> {
  const records = await listChapters(bookId);
  const byId = new Map(records.map((r) => [r.chapterId, r]));
  const parts: string[] = [];
  let count = 0;
  for (const entry of entries) {
    const rec = byId.get(entry.id);
    if (!rec) continue;
    const paragraphs = clean ? cleanParagraphs(rec.paragraphs) : rec.paragraphs;
    parts.push(`${entry.displayTitle}\n\n${paragraphs.join('\n')}\n`);
    count++;
  }
  return { text: parts.join('\n'), count };
}
