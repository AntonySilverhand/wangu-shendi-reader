/**
 * 目录搜索状态机单元测试（纯逻辑，不依赖 DOM）：
 *  - 穷尽（失败页 + 无结果）必须收敛，绝无 renderSearch→loadAll 回路；
 *  - debounce + generation：快速连续输入只启动一个任务；
 *  - 数字章节号走 probe 定位，不强制全量加载；
 *  - 取消后旧任务不得回调 UI；
 *  - 失败不自动无限重试。
 */
import { describe, expect, it } from 'vitest';
import type { TocEntry } from '../src/shared/source.ts';
import {
  TocSearchController,
  isAbortError,
  type TocSearchPhase,
  type TocSearchResult,
} from '../src/web/store/toc-search.ts';

function entry(n: number): TocEntry {
  return {
    id: String(38000000 + n),
    title: `第${n}章 测试`,
    displayTitle: `第${n}章 测试`,
    number: n,
    extra: false,
  };
}

interface FakeStore {
  loaded: Map<number, TocEntry[]>; // page -> entries
  failed: Set<number>;
  loading: Set<number>;
  totalPages: number;
  perPage: number;
  loadLog: string[];
  searchLog: string[];
}

function makeStore(totalPages = 5, perPage = 10): FakeStore {
  return { loaded: new Map(), failed: new Set(), loading: new Set(), totalPages, perPage, loadLog: [], searchLog: [] };
}

function makeDeps(store: FakeStore) {
  const entriesOf = (p: number): TocEntry[] => {
    const list: TocEntry[] = [];
    for (let i = 0; i < store.perPage; i++) list.push(entry((p - 1) * store.perPage + i + 1));
    return list;
  };
  const allEntries = (): TocEntry[] =>
    [...store.loaded.keys()].sort((a, b) => a - b).flatMap((p) => store.loaded.get(p)!);

  return {
    store,
    searchLoaded(query: string, limit: number): TocSearchResult[] {
      store.searchLog.push(query);
      const q = query.trim();
      const numeric = /^\d+$/.test(q) ? parseInt(q, 10) : null;
      const lower = q.toLowerCase();
      const out: TocSearchResult[] = [];
      const all = allEntries();
      for (let i = 0; i < all.length && out.length < limit; i++) {
        const e = all[i]!;
        if (
          e.title.toLowerCase().includes(lower) ||
          (numeric !== null && (e.number === numeric || e.id === q))
        ) {
          out.push({ index: i, entry: e });
        }
      }
      return out;
    },
    countLoadable(): number {
      let n = 0;
      for (let p = 1; p <= store.totalPages; p++) {
        if (!store.loaded.has(p) && !store.failed.has(p) && !store.loading.has(p)) n++;
      }
      return n;
    },
    nextLoadablePage(): number | null {
      for (let p = 1; p <= store.totalPages; p++) {
        if (!store.loaded.has(p) && !store.failed.has(p) && !store.loading.has(p)) return p;
      }
      return null;
    },
    isComplete: () => store.loaded.size >= store.totalPages,
    failedPageCount: () => store.failed.size,
    loadedPageCount: () => store.loaded.size,
    totalPages: () => store.totalPages,
    async loadRange(from: number, to: number, signal: AbortSignal | null): Promise<void> {
      store.loadLog.push(`${from}-${to}`);
      for (let p = from; p <= to; p++) {
        if (signal?.aborted) throw new DOMException('aborted', 'AbortError');
        if (store.loaded.has(p) || store.failed.has(p)) continue;
        store.loaded.set(p, entriesOf(p));
      }
    },
    estimatePageForNumber(n: number): number | null {
      return Math.max(1, Math.min(store.totalPages, Math.ceil(n / store.perPage)));
    },
    async retryFailedPages(signal: AbortSignal | null): Promise<void> {
      for (const p of [...store.failed]) {
        if (signal?.aborted) throw new DOMException('aborted', 'AbortError');
        store.failed.delete(p);
        store.loaded.set(p, entriesOf(p));
      }
    },
  };
}

function collect(updates: { state: TocSearchPhase; results: TocSearchResult[] }[]) {
  return {
    update(state: TocSearchPhase, results: TocSearchResult[]): void {
      updates.push({ state, results });
    },
  };
}

const tick = (ms = 300) => new Promise((r) => setTimeout(r, ms));
/** 等待控制器任务真正结束 */
async function settle(ms = 600): Promise<void> {
  await tick(ms);
}

describe('TocSearchController', () => {
  it('穷尽（失败页 + 无结果）后收敛：无 renderSearch→loadAll 回路', async () => {
    const store = makeStore(5, 10);
    // 模拟：第 1 页已加载；第 2 页失败；其余未加载
    store.loaded.set(1, Array.from({ length: 10 }, (_, i) => entry(i + 1)));
    store.failed.add(2);
    const deps = makeDeps(store);
    const updates: { state: TocSearchPhase; results: TocSearchResult[] }[] = [];
    const c = new TocSearchController(deps, collect(updates));

    c.setQuery('9999');
    await settle(800);

    // 任务只把可加载页各加载一次；失败页不会被重新请求
    const pagesLoaded = [...deps.store.loaded.keys()].sort((a, b) => a - b);
    expect(pagesLoaded).toEqual([1, 3, 4, 5]);
    // 收敛：最后一帧是 done+exhausted+failedPages=1
    const last = updates[updates.length - 1]!;
    expect(last.state.kind).toBe('done');
    if (last.state.kind === 'done') {
      expect(last.state.exhausted).toBe(true);
      expect(last.state.complete).toBe(false);
      expect(last.state.failedPages).toBe(1);
    }
    expect(last.results.length).toBe(0);
    // 关键：任务结束后 store 不再有新的加载调用（无回路）
    const loadCount = deps.store.loadLog.length;
    await settle(400);
    expect(deps.store.loadLog.length).toBe(loadCount);
    expect(c.busy).toBe(false);
  });

  it('快速连续输入只启动一个任务（debounce + generation）', async () => {
    const store = makeStore(5, 10);
    const deps = makeDeps(store);
    const updates: { state: TocSearchPhase; results: TocSearchResult[] }[] = [];
    const c = new TocSearchController(deps, collect(updates));

    c.setQuery('2');
    c.setQuery('28');
    c.setQuery('287');
    c.setQuery('2871');
    await settle(800);

    // 只有一个任务启动过：加载序列 = 首任务（探针 + 兜底）或最后一次查询的任务
    const loads = deps.store.loadLog;
    // 每次 loadRange 调用只覆盖少数页面（probe/scan 粒度小），而不是 4 轮全量
    const perCall = loads.map((l) => l.split('-').map(Number));
    expect(perCall.length).toBeGreaterThan(0);
    // 加载总量最多 = 全量扫描一次（5 页）的规模，不允许四轮 × 全量
    expect(perCall.length).toBeLessThanOrEqual(8);
    // 最终结果来自 2871 查询：未找到（测试数据无此章），但状态收敛
    const last = updates[updates.length - 1]!;
    expect(['done', 'cancelled', 'debouncing']).toContain(last.state.kind);
    expect(c.busy).toBe(false);
  });

  it('数字章节号走 probe：命中后停止，不加载全目录', async () => {
    const store = makeStore(44, 100);
    const deps = makeDeps(store);
    const updates: { state: TocSearchPhase; results: TocSearchResult[] }[] = [];
    const c = new TocSearchController(deps, collect(updates));

    // 第 2871 章 ≈ 第 29 页
    c.setQuery('2871');
    await settle(800);

    const loads = deps.store.loadLog.map((l) => l.split('-').map(Number));
    // 全部是单页 probe
    for (const [from, to] of loads) expect(from).toBe(to);
    // 命中即停：最多 probe 页数 = 1 + 2*radius = 7
    expect(loads.length).toBeLessThanOrEqual(7);
    // 只加载了估计页附近，绝不等于全量 44 页
    expect(deps.store.loaded.size).toBeLessThan(10);
    // 有结果
    const last = updates[updates.length - 1]!;
    expect(last.results.length).toBeGreaterThan(0);
    expect(last.results[0]!.entry.number).toBe(2871);
  });

  it('取消后旧任务不再回调 UI，且不再继续加载', async () => {
    const store = makeStore(44, 100);
    const deps = makeDeps(store);
    const updates: { state: TocSearchPhase; results: TocSearchResult[] }[] = [];
    const c = new TocSearchController(deps, collect(updates));

    c.setQuery('3000');
    await tick(120); // 让任务跑起来
    c.cancel();
    await settle(500);

    const loads = deps.store.loadLog.length;
    await settle(400);
    expect(deps.store.loadLog.length).toBe(loads);
    const last = updates[updates.length - 1]!;
    expect(last.state.kind).toBe('cancelled');
    expect(c.busy).toBe(false);
  });

  it('已加载数据命中：零网络请求直接出结果', async () => {
    const store = makeStore(5, 10);
    store.loaded.set(1, Array.from({ length: 10 }, (_, i) => entry(i + 1)));
    const deps = makeDeps(store);
    const updates: { state: TocSearchPhase; results: TocSearchResult[] }[] = [];
    const c = new TocSearchController(deps, collect(updates));

    c.searchNow('3');
    await settle(300);

    expect(deps.store.loadLog.length).toBe(0);
    const last = updates[updates.length - 1]!;
    expect(last.results.length).toBeGreaterThan(0);
    expect(last.results[0]!.entry.number).toBe(3);
  });

  it('resumeAfterRetry 先重试失败页再继续搜索，且只执行一次', async () => {
    const store = makeStore(5, 10);
    store.failed.add(2);
    const deps = makeDeps(store);
    const updates: { state: TocSearchPhase; results: TocSearchResult[] }[] = [];
    const c = new TocSearchController(deps, collect(updates));

    c.setQuery('25');
    await settle(800);
    const last = updates[updates.length - 1]!;
    expect(last.state.kind).toBe('done');
    expect(deps.store.failed.size).toBe(1);

    c.resumeAfterRetry('25');
    await settle(800);

    expect(deps.store.failed.size).toBe(0);
    const finalState = updates[updates.length - 1]!;
    expect(finalState.results.length).toBeGreaterThan(0);
    expect(finalState.results[0]!.entry.number).toBe(25);
  });

  it('isAbortError 识别 AbortError', () => {
    expect(isAbortError(new DOMException('aborted', 'AbortError'))).toBe(true);
    expect(isAbortError(new Error('x'))).toBe(false);
  });
});
