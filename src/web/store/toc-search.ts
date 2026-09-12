/**
 * 目录搜索控制器：纯逻辑、可注入、可单元测试。
 *
 * 与视图完全解耦：负责 debounce、generation token（取消旧任务）、
 * 章节号快速定位（数字 probe）、渐进式后台补全（full scan）、
 * 以及“穷尽”状态的判定。视图只渲染控制器给出的结果与状态。
 *
 * 关键不变量：
 *  - 同一时刻最多一个任务在跑；查询变化/取消 → generation 递增 →
 *    旧任务立刻失权（不再回调 UI），在途网络请求通过 AbortController 取消；
 *  - 网络失败只记录、不自动无限重试（只有用户点“重试”才继续）；
 *  - 没有可加载页（全部失败）时任务结束并标 exhausted，绝不形成
 *    无网络等待的循环；
 *  - 控制器自身从不调用自己（无 renderSearch → loadAll 回路）。
 */
import type { TocEntry } from '../../shared/source.ts';

export interface TocSearchResult {
  index: number;
  entry: TocEntry;
}

export type TocSearchPhase =
  | { kind: 'idle' }
  | { kind: 'debouncing' }
  | { kind: 'searching'; mode: 'probe' | 'full'; loadedPages: number; totalPages: number }
  | { kind: 'done'; complete: boolean; exhausted: boolean; failedPages: number }
  | { kind: 'cancelled' };

export interface TocSearchDeps {
  /** 在当前已加载数据上搜索（同步、纯函数） */
  searchLoaded(query: string, limit: number): TocSearchResult[];
  /** 尚未加载、未失败、未在途的页数 */
  countLoadable(): number;
  /** 下一个可自动加载的页（无则 null） */
  nextLoadablePage(): number | null;
  isComplete(): boolean;
  failedPageCount(): number;
  loadedPageCount(): number;
  totalPages(): number;
  /** 加载一段页（含失败记录）；可能抛 AbortError */
  loadRange(from: number, to: number, signal: AbortSignal | null): Promise<void>;
  /** 数字章节号 → 估计所在目录页（可为 null） */
  estimatePageForNumber(n: number): number | null;
  /** 重试失败页（用户主动触发，一次调用不无限重试） */
  retryFailedPages(signal: AbortSignal | null): Promise<void>;
}

export interface TocSearchEvents {
  /** 每次有新数据/新状态时回调（视图据此重绘） */
  update(state: TocSearchPhase, results: TocSearchResult[]): void;
}

export const SEARCH_DEBOUNCE_MS = 260;
const PROBE_RADIUS = 3;
const FULL_SCAN_CHUNK = 4;
export const TOC_SEARCH_LIMIT = 200;

export class TocSearchController {
  private generation = 0;
  private debounceTimer: ReturnType<typeof setTimeout> | null = null;
  private activeController: AbortController | null = null;
  private task: Promise<void> | null = null;
  private results: TocSearchResult[] = [];
  private cancelled = false;

  constructor(
    private deps: TocSearchDeps,
    private events: TocSearchEvents,
  ) {}

  /** 当前（最新一代的）结果快照，供视图直接渲染 */
  get currentResults(): TocSearchResult[] {
    return this.results;
  }

  /** 是否有本控制器发起的任务在跑 */
  get busy(): boolean {
    return this.task !== null;
  }

  /** 查询变化入口：debounce 后启动新一代任务 */
  setQuery(query: string): void {
    const q = query.trim();
    this.generation++;
    this.cancelled = false;
    this.abortInFlight();
    this.cancelDebounce();
    if (!q) {
      this.results = [];
      this.events.update({ kind: 'idle' }, []);
      return;
    }
    this.results = this.deps.searchLoaded(q, TOC_SEARCH_LIMIT);
    this.events.update({ kind: 'debouncing' }, this.results);
    this.debounceTimer = setTimeout(() => {
      this.debounceTimer = null;
      this.startTask(this.generation, q, async () => {});
    }, SEARCH_DEBOUNCE_MS);
  }

  /** 立即搜索（跳过 debounce，用于程序化调用） */
  searchNow(query: string): void {
    this.generation++;
    this.cancelled = false;
    this.abortInFlight();
    this.cancelDebounce();
    const q = query.trim();
    if (!q) {
      this.results = [];
      this.events.update({ kind: 'idle' }, []);
      return;
    }
    this.startTask(this.generation, q, async () => {});
  }

  /** 关闭目录/清空查询：取消一切 */
  cancel(): void {
    this.generation++;
    this.cancelled = true;
    this.abortInFlight();
    this.cancelDebounce();
    this.results = [];
    this.events.update({ kind: 'cancelled' }, []);
  }

  /** 搜索穷尽后用户点“重试失败页并继续”：先重试失败页，再继续搜索 */
  resumeAfterRetry(query: string): void {
    const q = query.trim();
    if (!q || this.task) return;
    this.generation++;
    this.cancelled = false;
    this.abortInFlight();
    this.cancelDebounce();
    this.startTask(this.generation, q, (signal) => this.deps.retryFailedPages(signal));
  }

  /** 目录数据因其它途径（滚动加载等）变化时，仅用新数据重算结果，不发起新加载 */
  refreshFromLoadedData(query: string): void {
    const q = query.trim();
    if (!q || this.task || this.debounceTimer || this.cancelled) return; // 任务在跑时由任务自己刷新；已取消则不再越权
    this.results = this.deps.searchLoaded(q, TOC_SEARCH_LIMIT);
    this.events.update(this.donePhase(), this.results);
  }

  private donePhase(): TocSearchPhase {
    return {
      kind: 'done',
      complete: this.deps.isComplete(),
      exhausted: this.deps.nextLoadablePage() === null,
      failedPages: this.deps.failedPageCount(),
    };
  }

  private cancelDebounce(): void {
    if (this.debounceTimer) {
      clearTimeout(this.debounceTimer);
      this.debounceTimer = null;
    }
  }

  private abortInFlight(): void {
    this.activeController?.abort();
    this.activeController = null;
    this.task = null;
  }

  private startTask(
    gen: number,
    query: string,
    pre: (signal: AbortSignal) => Promise<void>,
  ): void {
    if (this.task) return; // 理论不可达（调用方已 abort），双保险
    const controller = new AbortController();
    this.activeController = controller;
    const task = (async () => {
      try {
        await pre(controller.signal);
        if (gen !== this.generation || controller.signal.aborted) return;
        await this.run(gen, query, controller.signal);
      } catch (err) {
        if (isAbortError(err)) return;
        // 其它异常：按当前状态收尾（不自动重试）
        this.finish(gen, this.donePhase());
      } finally {
        if (this.activeController === controller) this.activeController = null;
        // 只有最新一代任务结束时才清空（旧任务失权后不得干扰新任务）
        if (gen === this.generation) this.task = null;
      }
    })();
    this.task = task;
  }

  private async run(gen: number, query: string, signal: AbortSignal): Promise<void> {
    // 1) 已加载数据直接命中：立即出结果，不发任何网络请求
    const immediate = this.deps.searchLoaded(query, TOC_SEARCH_LIMIT);
    if (gen !== this.generation) return;
    this.results = immediate;
    if (immediate.length > 0 && (/^\d+$/.test(query) || this.deps.isComplete())) {
      this.events.update(this.donePhase(), immediate);
      return;
    }

    // 2) 纯章节号：先做定位 probe（只拉估计页 ± radius，命中即停）
    const numeric = /^\d+$/.test(query) ? parseInt(query, 10) : null;
    if (numeric !== null && !this.deps.isComplete()) {
      const found = await this.numericProbe(gen, query, numeric, signal);
      if (gen !== this.generation || signal.aborted) return;
      if (found) return;
    }

    // 3) 渐进式后台补全：逐段推进，每轮用新数据重算并通知 UI
    await this.fullScan(gen, query, signal);
  }

  private async numericProbe(
    gen: number,
    query: string,
    n: number,
    signal: AbortSignal,
  ): Promise<boolean> {
    const est = this.deps.estimatePageForNumber(n);
    const total = this.deps.totalPages();
    const pages: number[] = [];
    if (est !== null) pages.push(est);
    for (let radius = 1; radius <= PROBE_RADIUS; radius++) {
      if (est === null) break;
      if (est + radius <= total) pages.push(est + radius);
      if (est - radius >= 1) pages.push(est - radius);
    }
    for (const page of pages) {
      if (gen !== this.generation || signal.aborted) return false;
      this.events.update(
        {
          kind: 'searching',
          mode: 'probe',
          loadedPages: this.deps.loadedPageCount(),
          totalPages: total,
        },
        [],
      );
      await this.deps.loadRange(page, page, signal);
      if (gen !== this.generation || signal.aborted) return false;
      const results = this.deps.searchLoaded(query, TOC_SEARCH_LIMIT);
      if (gen !== this.generation) return false;
      if (results.length > 0) {
        this.results = results;
        this.events.update(this.donePhase(), results);
        return true;
      }
    }
    return false;
  }

  private async fullScan(gen: number, query: string, signal: AbortSignal): Promise<void> {
    for (let guard = 0; guard < 400; guard++) {
      if (gen !== this.generation || signal.aborted) return;
      const next = this.deps.nextLoadablePage();
      if (next === null) {
        this.finish(gen, this.donePhase());
        return;
      }
      const before = this.deps.countLoadable();
      this.events.update(
        {
          kind: 'searching',
          mode: 'full',
          loadedPages: this.deps.loadedPageCount(),
          totalPages: this.deps.totalPages(),
        },
        this.results,
      );
      await this.deps.loadRange(next, next + FULL_SCAN_CHUNK - 1, signal);
      if (gen !== this.generation || signal.aborted) return;
      const results = this.deps.searchLoaded(query, TOC_SEARCH_LIMIT);
      if (gen !== this.generation) return;
      this.results = results;
      if (results.length > 0 && /^\d+$/.test(query)) {
        this.events.update(this.donePhase(), results);
        return;
      }
      this.events.update(
        {
          kind: 'searching',
          mode: 'full',
          loadedPages: this.deps.loadedPageCount(),
          totalPages: this.deps.totalPages(),
        },
        results,
      );
      if (this.deps.isComplete()) {
        this.events.update(
          {
            kind: 'done',
            complete: true,
            exhausted: false,
            failedPages: 0,
          },
          results,
        );
        return;
      }
      // 防自旋兜底：本轮加载没有产生任何进展 → 立即收尾（绝不空转）
      if (this.deps.countLoadable() >= before) {
        this.finish(gen, this.donePhase());
        return;
      }
    }
    this.finish(gen, this.donePhase());
  }

  private finish(gen: number, state: TocSearchPhase): void {
    if (gen !== this.generation) return;
    this.events.update(state, this.results);
  }
}

export function isAbortError(err: unknown): boolean {
  return err instanceof DOMException && err.name === 'AbortError';
}
