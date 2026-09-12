/**
 * 个人数据（阅读进度、书签、最近阅读）：localStorage。
 * 数据量很小、需要同步读取以最快恢复阅读位置；正文缓存走 IndexedDB。
 * 清理正文缓存不会触碰这里的数据。
 */
import type { Settings } from './settings.ts';
import { bump } from '../instrument.ts';

export interface ReadingPosition {
  chapterId: string;
  /** 章节目录中的序号（0 起，番外为 null） */
  chapterIndex: number | null;
  /** 段落序号 */
  paragraph: number;
  /** 段落内字符偏移 */
  offset: number;
  updatedAt: number;
}

export interface Bookmark {
  id: string;
  chapterId: string;
  chapterTitle: string;
  chapterIndex: number | null;
  paragraph: number;
  offset: number;
  excerpt: string;
  createdAt: number;
}

export interface HistoryItem {
  chapterId: string;
  chapterTitle: string;
  chapterIndex: number | null;
  at: number;
}

export interface BookPersonal {
  progress: ReadingPosition | null;
  bookmarks: Bookmark[];
  history: HistoryItem[];
}

export interface PersonalData {
  version: 1;
  books: Record<string, BookPersonal>;
}

export const PERSONAL_KEY = 'reader.personal.v1';
const HISTORY_LIMIT = 30;

function emptyBook(): BookPersonal {
  return { progress: null, bookmarks: [], history: [] };
}

function normalize(raw: unknown): PersonalData {
  const out: PersonalData = { version: 1, books: {} };
  if (!raw || typeof raw !== 'object') return out;
  const data = raw as Partial<PersonalData>;
  if (!data.books || typeof data.books !== 'object') return out;
  for (const [bookId, value] of Object.entries(data.books)) {
    const b = (value ?? {}) as Partial<BookPersonal>;
    const entry = emptyBook();
    if (b.progress && typeof b.progress === 'object') {
      const p = b.progress as Partial<ReadingPosition>;
      if (typeof p.chapterId === 'string') {
        entry.progress = {
          chapterId: p.chapterId,
          chapterIndex:
            typeof p.chapterIndex === 'number' || p.chapterIndex === null
              ? p.chapterIndex
              : null,
          paragraph: typeof p.paragraph === 'number' ? Math.max(0, p.paragraph) : 0,
          offset: typeof p.offset === 'number' ? Math.max(0, p.offset) : 0,
          updatedAt: typeof p.updatedAt === 'number' ? p.updatedAt : 0,
        };
      }
    }
    if (Array.isArray(b.bookmarks)) {
      entry.bookmarks = b.bookmarks
        .filter((x) => x && typeof x === 'object' && typeof (x as Bookmark).chapterId === 'string')
        .map((x) => {
          const bm = x as Bookmark;
          return {
            id: typeof bm.id === 'string' && bm.id ? bm.id : `${bm.chapterId}-${bm.paragraph}-${bm.offset}-${bm.createdAt ?? 0}`,
            chapterId: bm.chapterId,
            chapterTitle: typeof bm.chapterTitle === 'string' ? bm.chapterTitle : '',
            chapterIndex: typeof bm.chapterIndex === 'number' ? bm.chapterIndex : null,
            paragraph: typeof bm.paragraph === 'number' ? bm.paragraph : 0,
            offset: typeof bm.offset === 'number' ? bm.offset : 0,
            excerpt: typeof bm.excerpt === 'string' ? bm.excerpt : '',
            createdAt: typeof bm.createdAt === 'number' ? bm.createdAt : Date.now(),
          };
        });
    }
    if (Array.isArray(b.history)) {
      entry.history = b.history
        .filter((x) => x && typeof x === 'object' && typeof (x as HistoryItem).chapterId === 'string')
        .map((x) => {
          const h = x as HistoryItem;
          return {
            chapterId: h.chapterId,
            chapterTitle: typeof h.chapterTitle === 'string' ? h.chapterTitle : '',
            chapterIndex: typeof h.chapterIndex === 'number' ? h.chapterIndex : null,
            at: typeof h.at === 'number' ? h.at : 0,
          };
        })
        .slice(0, HISTORY_LIMIT);
    }
    out.books[bookId] = entry;
  }
  return out;
}

export class PersonalStore {
  private data: PersonalData;
  private listeners = new Set<() => void>();
  private persistFailed = false;
  private lastPersistError: string | null = null;

  constructor() {
    let raw: unknown = null;
    try {
      const text = localStorage.getItem(PERSONAL_KEY);
      if (text) raw = JSON.parse(text);
    } catch {
      raw = null;
    }
    this.data = normalize(raw);
  }

  /** 最近一次写盘是否失败（供 UI 给出可诊断反馈） */
  get storageHealthy(): boolean {
    return !this.persistFailed;
  }

  /** 最近一次写盘失败的原因（无则 null） */
  get storageError(): string | null {
    return this.lastPersistError;
  }

  private book(bookId: string): BookPersonal {
    let b = this.data.books[bookId];
    if (!b) {
      b = emptyBook();
      this.data.books[bookId] = b;
    }
    return b;
  }

  /**
   * 同步写盘。个人数据量极小（书签/进度/历史），偏向 durability：
   * 每次变更立即落盘，不依赖 400ms debounce 或页面生命周期。
   * 失败绝不静默：标记 + console.error，UI 可读取 storageHealthy/storageError。
   */
  private persistNow(): boolean {
    try {
      localStorage.setItem(PERSONAL_KEY, JSON.stringify(this.data));
      this.persistFailed = false;
      this.lastPersistError = null;
      bump('personalSaves');
      return true;
    } catch (err) {
      this.persistFailed = true;
      this.lastPersistError = err instanceof Error ? err.message : String(err);
      bump('personalSaveFails');
      console.error('[personal] localStorage 写入失败，个人数据未落盘：', this.lastPersistError);
      return false;
    }
  }

  flush(): void {
    this.persistNow();
  }

  private changed(): void {
    for (const fn of this.listeners) fn();
  }

  subscribe(fn: () => void): () => void {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }

  getProgress(bookId: string): ReadingPosition | null {
    return this.book(bookId).progress;
  }

  setProgress(bookId: string, pos: ReadingPosition, options?: { silent?: boolean }): void {
    const b = this.book(bookId);
    b.progress = pos;
    // 进度回调已被 Reader 限频（≈1 次/秒），直接同步落盘
    this.persistNow();
    if (!options?.silent) this.changed();
  }

  getBookmarks(bookId: string): Bookmark[] {
    return this.book(bookId).bookmarks;
  }

  addBookmark(bookId: string, bm: Omit<Bookmark, 'id' | 'createdAt'>): Bookmark {
    const entry: Bookmark = {
      ...bm,
      id: `${bm.chapterId}:${bm.paragraph}:${bm.offset}:${Date.now().toString(36)}`,
      createdAt: Date.now(),
    };
    const list = this.book(bookId).bookmarks;
    list.unshift(entry);
    this.persistNow(); // 书签操作必须立即完成持久化
    this.changed();
    return entry;
  }

  removeBookmark(bookId: string, id: string): void {
    const b = this.book(bookId);
    b.bookmarks = b.bookmarks.filter((x) => x.id !== id);
    this.persistNow();
    this.changed();
  }

  clearBookmarks(bookId: string): void {
    this.book(bookId).bookmarks = [];
    this.persistNow();
    this.changed();
  }

  getHistory(bookId: string): HistoryItem[] {
    return this.book(bookId).history;
  }

  pushHistory(bookId: string, item: Omit<HistoryItem, 'at'>): void {
    const b = this.book(bookId);
    b.history = [ { ...item, at: Date.now() }, ...b.history.filter((h) => h.chapterId !== item.chapterId) ].slice(
      0,
      HISTORY_LIMIT,
    );
    this.persistNow(); // 最近阅读立即持久化
    this.changed();
  }

  exportData(settings: Settings): string {
    const payload = {
      app: 'wangu-shendi-reader',
      version: 1,
      exportedAt: new Date().toISOString(),
      settings,
      personal: this.data,
    };
    return JSON.stringify(payload, null, 2);
  }

  /** 导入策略：merge 保留已有书签并合并；replace 完全替换 */
  importData(json: string, mode: 'merge' | 'replace'): { imported: number; settings?: Settings } {
    const parsed = JSON.parse(json) as {
      personal?: unknown;
      settings?: Settings;
      books?: unknown;
    };
    const incoming = normalize(parsed.personal ?? parsed);
    if (mode === 'replace') {
      this.data = incoming;
    } else {
      for (const [bookId, b] of Object.entries(incoming.books)) {
        const target = this.book(bookId);
        const known = new Set(target.bookmarks.map((x) => x.id));
        for (const bm of b.bookmarks) if (!known.has(bm.id)) target.bookmarks.push(bm);
        const byId = new Map(target.history.map((h) => [h.chapterId, h]));
        for (const h of b.history) {
          const prev = byId.get(h.chapterId);
          if (!prev || prev.at < h.at) byId.set(h.chapterId, h);
        }
        target.history = [...byId.values()].sort((a, b2) => b2.at - a.at).slice(0, HISTORY_LIMIT);
        if (b.progress && (!target.progress || target.progress.updatedAt < b.progress.updatedAt)) {
          target.progress = b.progress;
        }
      }
    }
    this.flush();
    this.changed();
    return { imported: Object.keys(incoming.books).length, settings: parsed.settings };
  }

  /** 供设置页显示统计 */
  stats(bookId: string): { bookmarks: number; history: number; hasProgress: boolean } {
    const b = this.book(bookId);
    return {
      bookmarks: b.bookmarks.length,
      history: b.history.length,
      hasProgress: b.progress !== null,
    };
  }
}
