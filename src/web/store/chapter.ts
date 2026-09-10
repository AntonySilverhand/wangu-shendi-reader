/** 章节获取：IndexedDB 优先，未命中再走受限后端，并回写缓存。 */
import type { ChapterResult } from '../../shared/source.ts';
import { getChapter, putChapter, type ChapterRecord } from './db.ts';
import { apiGet } from './remote.ts';

const inFlight = new Map<string, Promise<ChapterRecord>>();

function toRecord(bookId: string, data: ChapterResult): ChapterRecord {
  const text = data.paragraphs.join('');
  const bytes = new TextEncoder().encode(text).length;
  return {
    key: `${bookId}:${data.id}`,
    bookId,
    chapterId: data.id,
    title: data.title,
    paragraphs: data.paragraphs,
    charCount: data.charCount,
    source: 'remote',
    fetchedAt: Date.now(),
    bytes,
    prevId: data.prevId,
    nextId: data.nextId,
  };
}

export interface LoadChapterOptions {
  force?: boolean;
  signal?: AbortSignal;
  onNetworkStart?: () => void;
}

export async function loadChapterRecord(
  bookId: string,
  chapterId: string,
  opts: LoadChapterOptions = {},
): Promise<ChapterRecord> {
  const key = `${bookId}:${chapterId}`;
  if (!opts.force) {
    const cached = await getChapter(bookId, chapterId);
    if (cached) return cached;
  }
  const existing = inFlight.get(key);
  if (existing) return existing;

  const task = (async () => {
    opts.onNetworkStart?.();
    const data = await apiGet<ChapterResult>(`/api/chapter?id=${encodeURIComponent(chapterId)}`, {
      signal: opts.signal,
      retries: 1,
      timeoutMs: 45_000,
    });
    const record = toRecord(bookId, data);
    try {
      await putChapter(record);
    } catch {
      /* 存储满时仍返回内容 */
    }
    return record;
  })();

  inFlight.set(key, task);
  try {
    return await task;
  } finally {
    inFlight.delete(key);
  }
}

export function isChapterCached(bookId: string, chapterId: string): Promise<boolean> {
  return getChapter(bookId, chapterId).then((r) => r !== null);
}
