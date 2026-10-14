/**
 * 书架（远程书元数据）：localStorage `reader.library.v1`。
 * 正文/目录仍在 IndexedDB，进度/书签仍在 PersonalStore，三者都以 bookId 关联。
 * 预置书《万古神帝》只在首次运行时加入一次（seeded），用户移除后不会自动回来。
 */
import { DEFAULT_BOOK, isValidBookId, type BookInfo, type BookSummary } from '../../shared/source.ts';

export const LIBRARY_KEY = 'reader.library.v1';

export interface RemoteBook {
  id: string;
  title: string;
  author: string;
  category?: string;
  status?: string;
  intro?: string;
  latestChapter?: string;
  firstChapterId?: string;
  addedAt: number;
}

interface LibraryData {
  version: 1;
  seeded: boolean;
  activeBookId: string | null;
  books: RemoteBook[];
}

/** 远程书号（数字）；本地导入书是 local-xxx */
export function isRemoteBookId(id: string | null | undefined): id is string {
  return isValidBookId(id);
}

export function isLocalBookId(id: string | null | undefined): id is string {
  return typeof id === 'string' && /^local-[a-z0-9]{1,24}$/.test(id);
}

function sanitizeBook(raw: unknown): RemoteBook | null {
  if (!raw || typeof raw !== 'object') return null;
  const b = raw as Record<string, unknown>;
  if (!isRemoteBookId(b.id as string) || typeof b.title !== 'string' || !b.title) return null;
  const str = (v: unknown, max: number) => (typeof v === 'string' && v ? v.slice(0, max) : undefined);
  const book: RemoteBook = {
    id: b.id as string,
    title: b.title.slice(0, 100),
    author: str(b.author, 60) ?? '',
    addedAt: typeof b.addedAt === 'number' && Number.isFinite(b.addedAt) ? b.addedAt : Date.now(),
  };
  const opt: (keyof RemoteBook)[] = ['category', 'status', 'intro', 'latestChapter'];
  for (const k of opt) {
    const v = str(b[k], k === 'intro' ? 1000 : 100);
    if (v) (book as unknown as Record<string, unknown>)[k] = v;
  }
  if (typeof b.firstChapterId === 'string' && /^\d{1,12}$/.test(b.firstChapterId)) {
    book.firstChapterId = b.firstChapterId;
  }
  return book;
}

function normalize(raw: unknown): LibraryData | null {
  if (!raw || typeof raw !== 'object') return null;
  const r = raw as Record<string, unknown>;
  const books: RemoteBook[] = [];
  const seen = new Set<string>();
  for (const item of Array.isArray(r.books) ? r.books : []) {
    const b = sanitizeBook(item);
    if (b && !seen.has(b.id)) {
      seen.add(b.id);
      books.push(b);
    }
  }
  const active = typeof r.activeBookId === 'string' ? r.activeBookId : null;
  return { version: 1, seeded: r.seeded === true, activeBookId: active, books };
}

type Listener = () => void;

export class LibraryStore {
  private data: LibraryData;
  private listeners = new Set<Listener>();

  constructor() {
    let loaded: LibraryData | null = null;
    try {
      const raw = localStorage.getItem(LIBRARY_KEY);
      if (raw) loaded = normalize(JSON.parse(raw));
    } catch {
      loaded = null;
    }
    this.data = loaded ?? { version: 1, seeded: false, activeBookId: null, books: [] };
    if (!this.data.seeded) {
      if (!this.data.books.some((b) => b.id === DEFAULT_BOOK.id)) {
        this.data.books.unshift({
          id: DEFAULT_BOOK.id,
          title: DEFAULT_BOOK.title,
          author: DEFAULT_BOOK.author,
          addedAt: 0,
        });
      }
      this.data.seeded = true;
      this.persist();
    }
  }

  subscribe(fn: Listener): () => void {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }

  private persist(): void {
    try {
      localStorage.setItem(LIBRARY_KEY, JSON.stringify(this.data));
    } catch {
      /* 存储不可用时仅在内存中生效 */
    }
  }

  private changed(): void {
    this.persist();
    for (const fn of this.listeners) fn();
  }

  list(): RemoteBook[] {
    return [...this.data.books];
  }

  get(id: string): RemoteBook | null {
    return this.data.books.find((b) => b.id === id) ?? null;
  }

  has(id: string): boolean {
    return this.get(id) !== null;
  }

  /** 加入书架或用新信息更新（已有字段不会被空值覆盖） */
  upsert(info: BookInfo | BookSummary): RemoteBook | null {
    if (!isRemoteBookId(info.id) || !info.title) return null;
    const existing = this.get(info.id);
    const next = sanitizeBook({ ...(existing ?? { addedAt: Date.now() }), ...stripEmpty(info) });
    if (!next) return null;
    if (existing) {
      const same = JSON.stringify(existing) === JSON.stringify(next);
      if (same) return existing;
      this.data.books = this.data.books.map((b) => (b.id === next.id ? next : b));
    } else {
      this.data.books.push(next);
    }
    this.changed();
    return next;
  }

  /** 只更新已在书架上的书（目录加载顺带拿到的详情页信息） */
  refresh(info: BookInfo): void {
    if (this.has(info.id)) this.upsert(info);
  }

  remove(id: string): void {
    const before = this.data.books.length;
    this.data.books = this.data.books.filter((b) => b.id !== id);
    if (this.data.activeBookId === id) this.data.activeBookId = null;
    if (this.data.books.length !== before) this.changed();
  }

  get activeBookId(): string | null {
    return this.data.activeBookId;
  }

  setActive(id: string): void {
    if (this.data.activeBookId === id) return;
    this.data.activeBookId = id;
    this.persist();
  }

  /** 导出给个人数据备份 */
  exportData(): LibraryData {
    return JSON.parse(JSON.stringify(this.data)) as LibraryData;
  }

  /** 导入：合并（保留本机已有书，补入备份里的书） */
  importData(raw: unknown): number {
    const incoming = normalize(raw);
    if (!incoming) return 0;
    let n = 0;
    for (const b of incoming.books) {
      if (!this.has(b.id)) {
        this.data.books.push(b);
        n++;
      }
    }
    if (n > 0) this.changed();
    return n;
  }
}

function stripEmpty<T extends object>(obj: T): Partial<T> {
  const out: Partial<T> = {};
  for (const [k, v] of Object.entries(obj)) {
    if (v !== undefined && v !== null && v !== '') (out as Record<string, unknown>)[k] = v;
  }
  return out;
}
