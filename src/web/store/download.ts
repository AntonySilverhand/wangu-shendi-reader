/**
 * 下载管理：用户主动下载指定章节范围。
 * - 并发 2、请求间隔 250ms，避免给书源压力
 * - 支持取消、失败记录与重试
 * - 已缓存章节直接跳过
 */
import type { TocEntry } from '../../shared/source.ts';
import { getChapter } from './db.ts';
import { loadChapterRecord } from './chapter.ts';

export interface DownloadState {
  status: 'idle' | 'running' | 'cancelling' | 'done';
  fromIndex: number;
  toIndex: number;
  total: number;
  done: number;
  skipped: number;
  failed: { id: string; title: string; message: string }[];
  currentId: string | null;
  startedAt: number | null;
  finishedAt: number | null;
}

export interface DownloadEvents {
  getEntry: (index: number) => TocEntry | null;
  getEntryById: (id: string) => TocEntry | null;
  isOnline: () => boolean;
}

const initialState = (): DownloadState => ({
  status: 'idle',
  fromIndex: 0,
  toIndex: -1,
  total: 0,
  done: 0,
  skipped: 0,
  failed: [],
  currentId: null,
  startedAt: null,
  finishedAt: null,
});

export class DownloadManager {
  private state: DownloadState = initialState();
  private controller: AbortController | null = null;
  private listeners = new Set<(s: DownloadState) => void>();

  constructor(
    private bookId: string,
    private events: DownloadEvents,
  ) {}

  subscribeDownload(fn: (s: DownloadState) => void): () => void {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }

  getState(): DownloadState {
    return this.state;
  }

  private set(patch: Partial<DownloadState>): void {
    this.state = { ...this.state, ...patch };
    for (const fn of this.listeners) fn(this.state);
  }

  cancel(): void {
    if (this.state.status !== 'running') return;
    this.set({ status: 'cancelling' });
    this.controller?.abort();
  }

  async retryFailed(): Promise<void> {
    const ids = this.state.failed.map((f) => f.id).filter(Boolean);
    if (ids.length === 0) return;
    this.set({ status: 'running', failed: [], finishedAt: null, startedAt: Date.now() });
    await this.runIds(ids, 0, 0);
  }

  async start(fromIndex: number, toIndex: number): Promise<void> {
    if (this.state.status === 'running') return;
    const ids: string[] = [];
    for (let i = fromIndex; i <= toIndex; i++) {
      const entry = this.events.getEntry(i);
      if (entry) ids.push(entry.id);
    }
    this.set({
      ...initialState(),
      status: 'running',
      fromIndex,
      toIndex,
      total: ids.length,
      startedAt: Date.now(),
    });
    await this.runIds(ids, fromIndex, toIndex);
  }

  private async runIds(ids: string[], fromIndex: number, toIndex: number): Promise<void> {
    if (!this.events.isOnline()) {
      this.set({
        status: 'done',
        finishedAt: Date.now(),
        failed: [{ id: '', title: '', message: '当前离线，无法下载' }],
      });
      return;
    }
    this.controller?.abort();
    const controller = new AbortController();
    this.controller = controller;
    const queue = [...ids];
    let done = this.state.status === 'running' && this.state.total > 0 ? this.state.done : 0;
    let skipped = this.state.skipped;
    const failed: { id: string; title: string; message: string }[] = [];
    const total = this.state.total || ids.length;
    this.set({ status: 'running', failed: [], total, fromIndex, toIndex });

    const worker = async (): Promise<void> => {
      for (;;) {
        if (controller.signal.aborted) return;
        const id = queue.shift();
        if (id === undefined) return;
        const entry = this.events.getEntryById(id);
        this.set({ currentId: id });
        try {
          const cached = await getChapter(this.bookId, id);
          if (cached) {
            skipped++;
            done++;
            this.set({ done, skipped });
          } else {
            await loadChapterRecord(this.bookId, id, { signal: controller.signal });
            done++;
            this.set({ done });
          }
        } catch (err) {
          if (controller.signal.aborted) return;
          const message = err instanceof Error ? err.message : String(err);
          failed.push({ id, title: entry?.displayTitle ?? id, message });
          this.set({ failed: [...failed] });
        }
        // 控制请求节奏，降低被限流的概率
        await new Promise((r) => setTimeout(r, 250));
      }
    };

    await Promise.all([worker(), worker()]);
    this.set({
      status: 'done',
      done,
      skipped,
      failed,
      finishedAt: Date.now(),
      currentId: null,
    });
    this.controller = null;
  }
}
