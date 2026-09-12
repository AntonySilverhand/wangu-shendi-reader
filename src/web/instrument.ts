/**
 * 防 Freeze 仪表：进程内计数器，供 dev/test 通过 window.__readerDebug 读取。
 * - 生产环境不输出任何控制台内容（零刷屏）；
 * - 计数器为纯递增/赋值，开销可忽略；
 * - 若测试注入 window.__readerDebugSAB（Int32Array），每个 bump 会同步
 *   Atomics.add 到共享内存，供 Web Worker 从被冻结的主线程之外读取
 *  （用于 busy-loop 取证）。
 */

import { upstreamQueueSize, upstreamActiveCount } from '../shared/source.ts';

export interface ReaderDebugStats {
  renderSearchCount: number;
  loadAllForSearchCount: number;
  tocLoadRangeCalls: number;
  tocLoadRangeChunks: number;
  tocPagesLoaded: number;
  tocPagesFailed: number;
  tocFailedPages: number[];
  tocLoadedPages: number;
  tocTotalPages: number;
  tocLoadingPages: number;
  nextMissingPage: number | null;
  searchState: 'idle' | 'debouncing' | 'running' | 'exhausted' | 'found' | 'cancelled';
  searchGeneration: number;
  upstreamQueued: number;
  upstreamActive: number;
  chapterOpens: number;
  chapterStaleAborts: number;
  personalSaves: number;
  personalSaveFails: number;
  layoutModeChanges: number;
}

/** 与 ReaderDebugStats 中可计数的字段一一对应（SAB 槽位） */
export const COUNTER_INDEX: Record<string, number> = {
  renderSearchCount: 0,
  loadAllForSearchCount: 1,
  tocLoadRangeCalls: 2,
  tocLoadRangeChunks: 3,
  tocPagesLoaded: 4,
  tocPagesFailed: 5,
  searchGeneration: 6,
  chapterOpens: 7,
  chapterStaleAborts: 8,
  personalSaves: 9,
  personalSaveFails: 10,
  layoutModeChanges: 11,
};

export const debugStats: ReaderDebugStats = {
  renderSearchCount: 0,
  loadAllForSearchCount: 0,
  tocLoadRangeCalls: 0,
  tocLoadRangeChunks: 0,
  tocPagesLoaded: 0,
  tocPagesFailed: 0,
  tocFailedPages: [],
  tocLoadedPages: 0,
  tocTotalPages: 44,
  tocLoadingPages: 0,
  nextMissingPage: null,
  searchState: 'idle',
  searchGeneration: 0,
  upstreamQueued: 0,
  upstreamActive: 0,
  chapterOpens: 0,
  chapterStaleAborts: 0,
  personalSaves: 0,
  personalSaveFails: 0,
  layoutModeChanges: 0,
};

export function bump(name: keyof ReaderDebugStats): void {
  (debugStats as unknown as Record<string, unknown>)[name] =
    (debugStats[name] as number) + 1;
  const idx = COUNTER_INDEX[name];
  if (idx !== undefined && typeof window !== 'undefined') {
    const sab = (window as unknown as { __readerDebugSAB?: Int32Array }).__readerDebugSAB;
    if (sab && sab.length > idx) {
      try {
        Atomics.add(sab, idx, 1);
      } catch {
        /* 共享内存不可用时忽略（仅测试取证用途） */
      }
    }
  }
}

export function setSearchState(state: ReaderDebugStats['searchState']): void {
  debugStats.searchState = state;
}

export function syncTocCounters(partial: {
  failedPages?: number[];
  loadedPages?: number;
  totalPages?: number;
  loadingPages?: number;
  nextMissingPage?: number | null;
}): void {
  if (partial.nextMissingPage !== undefined) debugStats.nextMissingPage = partial.nextMissingPage;
  if (partial.failedPages) debugStats.tocFailedPages = partial.failedPages;
  if (partial.loadedPages !== undefined) debugStats.tocLoadedPages = partial.loadedPages;
  if (partial.totalPages !== undefined) debugStats.tocTotalPages = partial.totalPages;
  if (partial.loadingPages !== undefined) debugStats.tocLoadingPages = partial.loadingPages;
}

export function installDebugHooks(): void {
  // 按需读取，无轮询；Web 端队列在服务端，此处仅反映当前 JS realm。
  Object.defineProperties(debugStats, {
    upstreamQueued: { get: upstreamQueueSize, enumerable: true },
    upstreamActive: { get: upstreamActiveCount, enumerable: true },
  });
  (window as unknown as { __readerDebug?: ReaderDebugStats }).__readerDebug = debugStats;
}

export { debugStats as default };
