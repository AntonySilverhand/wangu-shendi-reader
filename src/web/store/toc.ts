/**
 * 目录 Store：分页拉取 + 持久化 + 合并 + 搜索。
 * 源站目录有 44 页（约 4300 章），首屏只加载前几页，其余按需/后台加载。
 *
 * 状态语义（不再把“加载失败”与“未加载”混进一个 boolean）：
 *  - pageEntries      ：已成功加载的页
 *  - loadingPages     ：在途页
 *  - failedPages      ：加载失败的页（不自动重试，等用户操作）
 *  - isComplete       ：所有页都已成功加载
 *  - exhausted        ：没有更多可自动加载的页（isComplete === false 且无 loadable）
 *  - status           ：complete / exhausted / loading / partial 四态
 */
import type { TocEntry } from '../../shared/source.ts';
import { mergeTocEntries } from '../../shared/source.ts';
import type { TocRangeResult } from '../../shared/source.ts';
import { getToc, putToc } from './db.ts';
import { apiGet } from './remote.ts';
import { bump, syncTocCounters } from '../instrument.ts';
import { isAbortError } from './toc-search.ts';

export const TOC_CHUNK_PAGES = 4;
/** 源站目录每页约 100 章（用于数字章节号定位的初始估计） */
export const TOC_PAGE_SPAN_ESTIMATE = 100;

export interface TocSearchResult {
  index: number;
  entry: TocEntry;
}

export interface TocProgress {
  loadedPages: number;
  totalPages: number;
  loading: boolean;
}

export type TocStatus = 'complete' | 'exhausted' | 'loading' | 'partial';

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
    syncTocCounters({
      failedPages: [...this.failedPages].sort((a, b) => a - b),
      loadedPages: this.pageEntries.size,
      totalPages: this.totalPages,
      loadingPages: this.loadingPages.size,
      nextMissingPage: this.nextLoadablePage(),
    });
    for (const fn of this.listeners) fn();
  }

  get loadedPageCount(): number {
    return this.pageEntries.size;
  }

  /** 所有页都已成功加载 */
  get isComplete(): boolean {
    return this.pageEntries.size >= this.totalPages;
  }

  /** 没有更多可自动加载的页（全部已加载或已失败） */
  get exhausted(): boolean {
    return this.nextLoadablePage() === null;
  }

  /** 四态：UI 依据此区分“全部成功 / 有失败页等待重试 / 加载中 / 部分未加载” */
  get status(): TocStatus {
    if (this.isComplete) return 'complete';
    if (this.loadingPages.size > 0) return 'loading';
    if (this.exhausted) return 'exhausted';
    return 'partial';
  }

  /** 尚未加载、未失败、未在途的页数 */
  countLoadable(): number {
    let n = 0;
    for (let p = 1; p <= this.totalPages; p++) {
      if (!this.pageEntries.has(p) && !this.failedPages.has(p) && !this.loadingPages.has(p)) {
        n++;
      }
    }
    return n;
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

  /**
   * 拉取目录页区间（去重、并发安全）。
   * signal 取消时抛 AbortError 且不记 failedPages（取消 ≠ 失败）。
   */
  async loadRange(
    from: number,
    to: number,
    opts: { background?: boolean; signal?: AbortSignal | null } = {},
  ): Promise<void> {
    bump('tocLoadRangeCalls');
    const start = Math.max(1, from);
    const end = Math.min(this.totalPages, to);
    if (start > end) return;
    const missing: number[] = [];
    for (let p = start; p <= end; p++) {
      if (!this.pageEntries.has(p) && !this.loadingPages.has(p) && !this.failedPages.has(p)) missing.push(p);
    }
    if (missing.length === 0) return;
    if (this.offline) {
      for (const p of missing) {
        this.failedPages.add(p);
        bump('tocPagesFailed');
      }
      this.emit();
      return;
    }

    const key = `${start}-${end}`;
    const existing = this.inFlight.get(key);
    if (existing) return existing;

    const signal = opts.signal ?? undefined;
    const task = (async () => {
      const generation = this.loadGeneration;
      // 服务端单次最多 6 页，客户端按 chunk 拆分
      for (let offset = 0; offset < missing.length;) {
        if (signal?.aborted) throw new DOMException('aborted', 'AbortError');
        const chunk = [missing[offset++]!];
        while (offset < missing.length && chunk.length < TOC_CHUNK_PAGES && missing[offset] === chunk[chunk.length - 1]! + 1) {
          chunk.push(missing[offset++]!);
        }
        const chunkFrom = chunk[0]!;
        const chunkTo = chunk[chunk.length - 1]!;
        bump('tocLoadRangeChunks');
        for (const p of chunk) this.loadingPages.add(p);
        this.emit();
        try {
          const result = await this.loader<TocRangeResult>(
            `/api/toc?from=${chunkFrom}&to=${chunkTo}`,
            { retries: 2, onRetry: () => undefined, signal },
          );
          if (generation !== this.loadGeneration) return;
          this.totalPages = result.totalPages || this.totalPages;
          for (const page of result.pages) {
            this.pageEntries.set(page.page, page.entries);
            this.failedPages.delete(page.page);
            bump('tocPagesLoaded');
          }
          for (const p of chunk.filter((p) => !this.pageEntries.has(p))) {
            this.failedPages.add(p);
            bump('tocPagesFailed');
          }
          this.rebuild();
          this.schedulePersist();
        } catch (err) {
          if (isAbortError(err)) {
            // 取消：不记失败，直接上抛
            throw err;
          }
          for (const p of chunk) {
            this.failedPages.add(p);
            bump('tocPagesFailed');
          }
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

  /**
   * 顺序加载剩余可加载页，直到 complete 或 exhausted。
   * 失败页跳过（等待用户重试）；每轮必须产生实际进展，否则立即返回，绝不空转。
   */
  async loadRemaining(onProgress?: (p: TocProgress) => void): Promise<'complete' | 'exhausted'> {
    const chunk = TOC_CHUNK_PAGES;
    for (let guard = 0; guard < 400; guard++) {
      const next = this.nextLoadablePage();
      if (next === null) break;
      const before = this.countLoadable();
      await this.loadRange(next, next + chunk - 1);
      onProgress?.(this.progress);
      if (this.countLoadable() >= before) {
        // 本轮无任何进展（如被并发加载抢先）：退出，绝不空转
        break;
      }
    }
    return this.isComplete ? 'complete' : 'exhausted';
  }

  /** 重试所有失败页（用户主动触发；一次调用每页最多一次） */
  async retryFailedPages(signal: AbortSignal | null = null): Promise<void> {
    if (this.failedPages.size === 0) return;
    const pages = [...this.failedPages].sort((a, b) => a - b);
    for (const page of pages) {
      if (signal?.aborted) throw new DOMException('aborted', 'AbortError');
      if (this.pageEntries.has(page)) {
        this.failedPages.delete(page);
        continue;
      }
      this.failedPages.delete(page);
      await this.loadRange(page, page, { signal });
    }
    this.emit();
  }

  /** 下一个可自动加载的页（跳过已加载/失败/在途；无则 null） */
  nextLoadablePage(): number | null {
    for (let p = 1; p <= this.totalPages; p++) {
      if (!this.pageEntries.has(p) && !this.failedPages.has(p) && !this.loadingPages.has(p)) {
        return p;
      }
    }
    return null;
  }

  /** @deprecated 别名：nextLoadablePage（兼容旧调用方命名） */
  nextMissingPage(): number | null {
    return this.nextLoadablePage();
  }

  /**
   * 数字章节号 → 估计所在目录页：
   *  1. 已加载页的章节号区间直接定位；
   *  2. 相邻页外推（仅当间距 ≤ 2 个页跨度时可信）；
   *  3. 兜底：每页约 100 章。
   * 仅用于搜索快速定位；估计不准时 probe 会向两侧扩展。
   */
  estimatePageForNumber(n: number): number | null {
    if (!Number.isFinite(n) || n < 1) return null;
    let nearestPage: number | null = null;
    let nearestGap = Number.POSITIVE_INFINITY;
    for (const [page, entries] of this.pageEntries) {
      const nums = entries
        .map((e) => e.number)
        .filter((x): x is number => x !== null && x > 0);
      if (nums.length === 0) continue;
      const min = Math.min(...nums);
      const max = Math.max(...nums);
      if (n >= min && n <= max) return page;
      const gap = n < min ? min - n : n - max;
      if (gap < nearestGap) {
        nearestGap = gap;
        nearestPage = page + (n < min ? -1 : 1);
      }
    }
    // 间距过大时外推不可信，改用全局估计
    if (nearestPage !== null && nearestGap <= TOC_PAGE_SPAN_ESTIMATE * 2) {
      return Math.max(1, Math.min(this.totalPages, nearestPage));
    }
    const est = Math.ceil(n / TOC_PAGE_SPAN_ESTIMATE);
    return Math.max(1, Math.min(this.totalPages, est));
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
