/** 章节获取：IndexedDB 优先，未命中再走受限后端，并回写缓存。 */
import type { ChapterResult } from '../../shared/source.ts';
import { getChapter, putChapter, type ChapterRecord } from './db.ts';
import { apiGet } from './remote.ts';

const inFlight = new Map<string, Promise<ChapterRecord>>();
export const CHAPTER_CACHE_VERSION = 2;

type UpdateListener = (record: ChapterRecord) => void;
const updateListeners = new Set<UpdateListener>();

/** 后台补全成功后通知界面（先显示缓存、再静默更新，不阻塞阅读） */
export function subscribeChapterUpdates(fn: UpdateListener): () => void {
  updateListeners.add(fn);
  return () => updateListeners.delete(fn);
}

function notifyUpdate(record: ChapterRecord): void {
  for (const fn of updateListeners) fn(record);
}

async function refreshInBackground(bookId: string, chapterId: string, stale: ChapterRecord): Promise<void> {
  const key = `${bookId}:${chapterId}`;
  if (inFlight.has(key)) return;
  try {
    const data = await apiGet<ChapterResult>(`/api/chapter?id=${encodeURIComponent(chapterId)}`, {
      retries: 0,
      timeoutMs: 30_000,
    });
    const record = toRecord(bookId, data);
    const better =
      record.paragraphs.length > stale.paragraphs.length ||
      (record.complete && !stale.complete);
    await putChapter(record).catch(() => undefined);
    if (better) notifyUpdate(record);
  } catch {
    /* 源站不可用：保留已缓存内容 */
  }
}

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
    missingPages: data.missingPages ?? [],
    complete: data.complete === true,
    v: CHAPTER_CACHE_VERSION,
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
    if (cached) {
      const healthy =
        cached.source === 'local' ||
        (cached.v === CHAPTER_CACHE_VERSION && cached.complete === true);
      if (healthy) return cached;
      // 关键：旧/不完整缓存先立即返回，后台静默补全（不再阻塞阅读）
      void refreshInBackground(bookId, chapterId, cached);
      return cached;
    }
  }
  const existing = inFlight.get(key);
  if (existing) return existing;

  const task = (async () => {
    opts.onNetworkStart?.();
    try {
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
    } catch (err) {
      throw err;
    }
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
