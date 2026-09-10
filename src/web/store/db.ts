/**
 * IndexedDB 层：可再获取的正文缓存与目录缓存。
 * 与个人数据（localStorage）分离，清缓存不会删除书签/进度。
 */
import type { TocEntry } from '../../shared/source.ts';

export const DB_NAME = 'reader-db';
export const DB_VERSION = 1;
export const STORE_CHAPTERS = 'chapters';
export const STORE_TOC = 'toc';

export interface ChapterRecord {
  key: string;
  bookId: string;
  chapterId: string;
  title: string;
  paragraphs: string[];
  charCount: number;
  source: 'remote' | 'local';
  fetchedAt: number;
  bytes: number;
  /** 源站给出的相邻章节（目录未加载时的兜底） */
  prevId?: string | null;
  nextId?: string | null;
  /** 源站分页未全部取到 */
  missingPages?: number[];
}

export interface TocRecord {
  bookId: string;
  /** 目录按源站页码分组的原始数据，便于缺页重试与乱序到达后重建 */
  pages: Record<string, TocEntry[]>;
  totalPages: number;
  updatedAt: number;
}

export const chapterKey = (bookId: string, chapterId: string): string => `${bookId}:${chapterId}`;

let dbPromise: Promise<IDBDatabase> | null = null;

export function openDb(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains(STORE_CHAPTERS)) {
        const store = db.createObjectStore(STORE_CHAPTERS, { keyPath: 'key' });
        store.createIndex('bookId', 'bookId', { unique: false });
      }
      if (!db.objectStoreNames.contains(STORE_TOC)) {
        db.createObjectStore(STORE_TOC, { keyPath: 'bookId' });
      }
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error('indexedDB open failed'));
    req.onblocked = () => reject(new Error('indexedDB blocked'));
  });
  return dbPromise;
}

function wrapRequest<T>(req: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error('indexedDB request failed'));
  });
}

function txDone(tx: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error ?? new Error('indexedDB tx failed'));
    tx.onabort = () => reject(tx.error ?? new Error('indexedDB tx aborted'));
  });
}

export async function getChapter(
  bookId: string,
  chapterId: string,
): Promise<ChapterRecord | null> {
  const db = await openDb();
  const tx = db.transaction(STORE_CHAPTERS, 'readonly');
  const store = tx.objectStore(STORE_CHAPTERS);
  const value = await wrapRequest<ChapterRecord | undefined>(store.get(chapterKey(bookId, chapterId)));
  return value ?? null;
}

export async function putChapter(record: ChapterRecord): Promise<void> {
  const db = await openDb();
  const tx = db.transaction(STORE_CHAPTERS, 'readwrite');
  tx.objectStore(STORE_CHAPTERS).put(record);
  await txDone(tx);
}

export async function deleteChapter(bookId: string, chapterId: string): Promise<void> {
  const db = await openDb();
  const tx = db.transaction(STORE_CHAPTERS, 'readwrite');
  tx.objectStore(STORE_CHAPTERS).delete(chapterKey(bookId, chapterId));
  await txDone(tx);
}

export async function listChapters(bookId: string): Promise<ChapterRecord[]> {
  const db = await openDb();
  const tx = db.transaction(STORE_CHAPTERS, 'readonly');
  const index = tx.objectStore(STORE_CHAPTERS).index('bookId');
  const values = await wrapRequest<ChapterRecord[]>(index.getAll(bookId));
  return values;
}

export async function listChapterIds(bookId: string): Promise<Set<string>> {
  const db = await openDb();
  const tx = db.transaction(STORE_CHAPTERS, 'readonly');
  const index = tx.objectStore(STORE_CHAPTERS).index('bookId');
  const keys = await wrapRequest<IDBValidKey[]>(index.getAllKeys(bookId));
  return new Set(keys.map((k) => String(k).split(':').slice(1).join(':')));
}

export async function clearBookContent(bookId: string): Promise<number> {
  const db = await openDb();
  const tx = db.transaction(STORE_CHAPTERS, 'readwrite');
  const index = tx.objectStore(STORE_CHAPTERS).index('bookId');
  const keys = await wrapRequest<IDBValidKey[]>(index.getAllKeys(bookId));
  const store = tx.objectStore(STORE_CHAPTERS);
  for (const key of keys) store.delete(key);
  await txDone(tx);
  return keys.length;
}

export async function deleteChapterIds(bookId: string, ids: string[]): Promise<number> {
  const db = await openDb();
  const tx = db.transaction(STORE_CHAPTERS, 'readwrite');
  const store = tx.objectStore(STORE_CHAPTERS);
  let n = 0;
  for (const id of ids) {
    const key = chapterKey(bookId, id);
    const exists = await wrapRequest<IDBValidKey | undefined>(store.getKey(key));
    if (exists !== undefined) {
      store.delete(key);
      n++;
    }
  }
  await txDone(tx);
  return n;
}

export async function getToc(bookId: string): Promise<TocRecord | null> {
  const db = await openDb();
  const tx = db.transaction(STORE_TOC, 'readonly');
  const value = await wrapRequest<TocRecord | undefined>(tx.objectStore(STORE_TOC).get(bookId));
  return value ?? null;
}

export async function putToc(record: TocRecord): Promise<void> {
  const db = await openDb();
  const tx = db.transaction(STORE_TOC, 'readwrite');
  tx.objectStore(STORE_TOC).put(record);
  await txDone(tx);
}

export interface StorageStats {
  chapters: number;
  bytes: number;
  usage: number | null;
  quota: number | null;
}

export async function storageStats(bookId: string): Promise<StorageStats> {
  const all = await listChapters(bookId);
  let usage: number | null = null;
  let quota: number | null = null;
  try {
    if (navigator.storage?.estimate) {
      const est = await navigator.storage.estimate();
      usage = est.usage ?? null;
      quota = est.quota ?? null;
    }
  } catch {
    /* 忽略 */
  }
  return {
    chapters: all.length,
    bytes: all.reduce((n, c) => n + (c.bytes || 0), 0),
    usage,
    quota,
  };
}

export function formatBytes(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined) return '—';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}
