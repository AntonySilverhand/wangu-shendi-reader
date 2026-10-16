import { describe, expect, it } from 'vitest';
import { TocStore } from '../src/web/store/toc.ts';
import type { TocEntry, TocRangeResult } from '../src/shared/source.ts';

const entry = (id: string, n: number): TocEntry => ({
  id,
  title: `第${n}章`,
  displayTitle: `第${n}章`,
  number: n,
  extra: false,
});

/** 假 loader：按书返回固定的目录分页 */
function loader(totalPages: number, pages: Record<number, TocEntry[]>, fail: number[] = []) {
  const calls: string[] = [];
  const fn = (async (path: string) => {
    calls.push(path);
    const u = new URL(path, 'https://x');
    const from = Number(u.searchParams.get('from'));
    const to = Number(u.searchParams.get('to'));
    const result: TocRangeResult = { from, to, totalPages, pages: [], failedPages: [] };
    for (let p = from; p <= to; p++) {
      if (pages[p] && !fail.includes(p)) result.pages.push({ page: p, entries: pages[p]! });
      else if (p <= totalPages) result.failedPages.push(p);
    }
    return result;
  }) as unknown as ConstructorParameters<typeof TocStore>[1];
  return { fn, calls };
}

describe('TocStore：单页目录', () => {
  it('69书吧（目录只有 1 页）：请求 1–4 页后状态为完整，不出现“页加载失败”', async () => {
    const { fn } = loader(1, { 1: [entry('30001001', 1), entry('30001002', 2)] });
    const store = new TocStore('69shuba-51254', fn);
    await store.loadRange(1, 4);
    expect(store.totalPages).toBe(1);
    expect(store.status).toBe('complete');
    expect(store.failedPages.size).toBe(0);
    expect(store.all.map((e) => e.id)).toEqual(['30001001', '30001002']);
  });

  it('多页书真实失败的页仍记为失败（等待用户重试）', async () => {
    const { fn } = loader(3, { 1: [entry('1000001', 1)], 2: [entry('1000002', 2)], 3: [entry('1000003', 3)] }, [2]);
    const store = new TocStore('36780', fn);
    await store.loadRange(1, 2);
    await store.loadRange(1, 3);
    expect(store.totalPages).toBe(3);
    expect([...store.failedPages]).toEqual([2]);
    expect(store.status).toBe('exhausted');
  });
});
