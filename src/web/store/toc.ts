/**
 * 目录 Store：分页拉取 + 持久化 + 合并 + 搜索。
 * 源站目录有 44 页（约 4300 章），首屏只加载前几页，其余按需/后台加载。
 */
import type { TocEntry } from '../../shared/source.ts';
import { mergeTocEntries } from '../../shared/source.ts';
import type { TocRangeResult } from '../../shared/source.ts';
import { getToc, putToc } from './db.ts';
import { apiGet } from './remote.ts';

export const TOC_CHUNK_PAGES = 4;

export interface TocSearchResult {
  index: number;
  entry: TocEntry;
}

export interface TocProgress {
  loadedPages: number;
  totalPages: number;
  loading: boolean;
}

type Listener = () => void;

export class TocStore {
  /** 按源站页码保存原始分类结果 */
  private pageEntries = new Map<number, TocEntry[]>();
  main: TocEntry[] = [];
  extras: TocEntry[] = [];
  all: TocEntry[] = [];
  private idToIndex = new Map<string, number>();
  totalPages = 44;
  private listeners = new Set<Listener>();
  private loadGeneration = 0;
  private persistTimer: ReturnType<typeof setTimeout> | null = null;
  private inFlight = new Map<string, Promise<void>>();
  loadingPages = new Set<number>();
  failedPages = new Set<number>();
  /** 本地导入书籍：目录只读 IndexedDB，不访问网络 */
  offline = false;
  private initialized = false;

  constructor(
    private bookId: string,
    private readonly loader: typeof apiGet = apiGet,
  ) {}

  subscribe(fn: Listener): () => void {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }

  private emit(): void {
    for (const fn of this.listeners) fn();
  }

  get loadedPageCount(): number {
    return this.pageEntries.size;
  }

  get isComplete(): boolean {
    return this.pageEntries.size >= this.totalPages;
  }

  get progress(): TocProgress {
    return {
      loadedPages: this.pageEntries.size,
      totalPages: this.totalPages,
      loading: this.loadingPages.size > 0,
    };
  }

  async init(): Promise<void> {
    if (this.initialized) return;
    this.initialized = true;
    try {
      const record = await getToc(this.bookId);
      if (record) {
        this.totalPages = record.totalPages || 44;
        for (const [page, entries] of Object.entries(record.pages)) {
          this.pageEntries.set(parseInt(page, 10), entries);
        }
        this.rebuild();
      }
    } catch {
      /* 忽略：目录可以重新拉取 */
    }
  }

  private schedulePersist(): void {
    if (this.persistTimer) return;
    this.persistTimer = setTimeout(() => {
      this.persistTimer = null;
      void this.persist();
    }, 600);
  }

  async persist(): Promise<void> {
    const pages: Record<string, TocEntry[]> = {};
    for (const [page, entries] of this.pageEntries) pages[String(page)] = entries;
    try {
      await putToc({
        bookId: this.bookId,
        pages,
        totalPages: this.totalPages,
        updatedAt: Date.now(),
      });
    } catch {
      /* 存储满时忽略，内存数据仍可用 */
    }
  }

  private rebuild(): void {
    const pages = [...this.pageEntries.keys()]
      .sort((a, b) => a - b)
      .map((p) => this.pageEntries.get(p)!);
    const merged = mergeTocEntries(pages);
    this.all = merged;
    this.main = merged.filter((e) => !e.extra);
    this.extras = merged.filter((e) => e.extra);
    this.idToIndex = new Map();
    this.all.forEach((entry, index) => {
      if (!this.idToIndex.has(entry.id)) this.idToIndex.set(entry.id, index);
    });
  }

  /** 拉取目录页区间（去重、并发安全） */
  async loadRange(from: number, to: number, opts: { background?: boolean } = {}): Promise<void> {
    const start = Math.max(1, from);
    const end = Math.min(this.totalPages, to);
    if (start > end) return;
    const missing: number[] = [];
    for (let p = start; p <= end; p++) {
      if (!this.pageEntries.has(p) && !this.loadingPages.has(p)) missing.push(p);
    }
    if (missing.length === 0) return;
    if (this.offline) {
      for (const p of missing) this.failedPages.add(p);
      this.emit();
      return;
    }

    const key = `${start}-${end}`;
    const existing = this.inFlight.get(key);
    if (existing) return existing;

    const task = (async () => {
      const generation = this.loadGeneration;
      // 服务端单次最多 6 页，客户端按 chunk 拆分
      for (let offset = 0; offset < missing.length; offset += TOC_CHUNK_PAGES) {
        const chunk = missing.slice(offset, offset + TOC_CHUNK_PAGES);
        const chunkFrom = chunk[0]!;
        const chunkTo = chunk[chunk.length - 1]!;
        for (const p of chunk) this.loadingPages.add(p);
        this.emit();
        try {
          const result = await this.loader<TocRangeResult>(
            `/api/toc?from=${chunkFrom}&to=${chunkTo}`,
            { retries: 2, onRetry: () => undefined },
          );
          if (generation !== this.loadGeneration) return;
          this.totalPages = result.totalPages || this.totalPages;
          for (const page of result.pages) {
            this.pageEntries.set(page.page, page.entries);
            this.failedPages.delete(page.page);
          }
          for (const p of result.failedPages) this.failedPages.add(p);
          this.rebuild();
          this.schedulePersist();
        } catch {
          for (const p of chunk) this.failedPages.add(p);
        } finally {
          for (const p of chunk) this.loadingPages.delete(p);
          this.emit();
        }
      }
      if (opts.background) await this.persist();
    })();

    this.inFlight.set(key, task);
    try {
      await task;
    } finally {
      this.inFlight.delete(key);
    }
  }

  /** 顺序加载直到完成；失败页跳过（用户可手动重试） */
  async loadAll(onProgress?: (p: TocProgress) => void): Promise<void> {
    const chunk = TOC_CHUNK_PAGES;
    for (let guard = 0; guard < 200; guard++) {
      const next = this.nextMissingPage();
      if (next === null) break;
      await this.loadRange(next, next + chunk - 1);
      onProgress?.(this.progress);
      if (this.loadingPages.size === 0 && !this.pageEntries.has(next)) {
        this.failedPages.add(next);
      }
    }
    onProgress?.(this.progress);
  }

  nextMissingPage(): number | null {
    for (let p = 1; p <= this.totalPages; p++) {
      if (!this.pageEntries.has(p) && !this.failedPages.has(p)) return p;
    }
    return null;
  }

  indexOfChapter(chapterId: string): number {
    return this.idToIndex.get(chapterId) ?? -1;
  }

  entryAt(index: number): TocEntry | null {
    return this.all[index] ?? null;
  }

  get mainCount(): number {
    return this.main.length;
  }

  /** 章内/目录搜索：支持标题、章节号、章节 id */
  search(query: string, limit = 60): TocSearchResult[] {
    const q = query.trim();
    if (!q) return [];
    const numeric = /^\d+$/.test(q) ? parseInt(q, 10) : null;
    const lower = q.toLowerCase();
    const out: TocSearchResult[] = [];
    for (let i = 0; i < this.all.length && out.length < limit; i++) {
      const entry = this.all[i]!;
      if (
        entry.title.toLowerCase().includes(lower) ||
        entry.displayTitle.toLowerCase().includes(lower) ||
        (numeric !== null && (entry.number === numeric || entry.id === q))
      ) {
        out.push({ index: i, entry });
      }
    }
    return out;
  }

  /** 找到最接近指定章节号的目录项（用于“跳转章节号”） */
  nearestToNumber(number: number): number {
    let best = -1;
    let bestDelta = Number.POSITIVE_INFINITY;
    for (let i = 0; i < this.all.length; i++) {
      const n = this.all[i]!.number;
      if (n === null) continue;
      const delta = Math.abs(n - number);
      if (delta < bestDelta) {
        bestDelta = delta;
        best = i;
      }
    }
    return best;
  }

  resetForBook(bookId: string): void {
    this.loadGeneration++;
    this.bookId = bookId;
    this.pageEntries.clear();
    this.loadingPages.clear();
    this.failedPages.clear();
    this.inFlight.clear();
    this.main = [];
    this.extras = [];
    this.all = [];
    this.idToIndex.clear();
    this.initialized = false;
    this.emit();
  }
}
