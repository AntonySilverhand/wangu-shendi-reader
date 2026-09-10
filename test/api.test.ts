import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { handleApi, MemoryApiCache } from '../src/shared/api.ts';
import type { ChapterResult, TocRangeResult } from '../src/shared/source.ts';

const here = dirname(fileURLToPath(import.meta.url));
const fixture = (name: string) => readFileSync(join(here, 'fixtures', name), 'utf8');

function fakeFetcher(routes: Record<string, string>) {
  return async (input: string): Promise<Response> => {
    const url = new URL(input);
    const path = url.pathname + (url.search || '');
    // 命中规则按 pathname 匹配（忽略协议与 host）
    const html = routes[url.pathname];
    if (html === undefined) {
      return new Response('not found', { status: 404, headers: { 'content-type': 'text/html' } });
    }
    void path;
    return new Response(html, {
      status: 200,
      headers: { 'content-type': 'text/html; charset=utf-8' },
    });
  };
}

const ctx = () => ({ cache: new MemoryApiCache(), now: () => 1_700_000_000_000 });

describe('API 路由（同构 handler）', () => {
  it('/api/health', async () => {
    const res = await handleApi(new URL('https://x/api/health'), ctx());
    expect(res.status).toBe(200);
    const body = (await res.json()) as { ok: boolean; book: { id: string } };
    expect(body.ok).toBe(true);
    expect(body.book.id).toBe('36780');
  });

  it('章节：合并分页并返回上一章/下一章', async () => {
    const fetcher = fakeFetcher({
      '/book/36780_38621328.html': fixture('chapter-38621328-p1.html'),
      '/book/36780/38621328_1.html': fixture('chapter-38621328-p2.html'),
      '/book/36780/38621328_2.html': fixture('chapter-38621328-p3.html'),
    });
    const res = await handleApi(new URL('https://x/api/chapter?id=38621328'), {
      ...ctx(),
      fetcher,
    });
    expect(res.status).toBe(200);
    const body = (await res.json()) as ChapterResult;
    expect(body.title).toBe('第二千七百七十一章 无疆到来');
    expect(body.pageCount).toBe(3);
    expect(body.paragraphs.length).toBeGreaterThan(80);
    expect(body.prevId).toBe('38621326');
    expect(body.nextId).toBe('38621330');
    expect(body.charCount).toBeGreaterThan(2500);
    // 段落不重复
    expect(new Set(body.paragraphs).size).toBe(body.paragraphs.length);
  });

  it('目录范围：返回分页分组', async () => {
    const fetcher = fakeFetcher({
      '/book/36780/': fixture('toc-page1.html'),
      '/book/36780/1.html': fixture('toc-page2.html'),
    });
    const res = await handleApi(new URL('https://x/api/toc?from=1&to=2'), { ...ctx(), fetcher });
    expect(res.status).toBe(200);
    const body = (await res.json()) as TocRangeResult;
    expect(body.totalPages).toBe(44);
    expect(body.pages).toHaveLength(2);
    expect(body.pages[0]!.page).toBe(1);
    expect(body.pages[0]!.entries.some((e) => e.displayTitle === '第1章')).toBe(true);
    expect(body.pages[1]!.entries.some((e) => e.extra)).toBe(true);
  });

  it('参数校验：非法 id、非法范围、越界页码', async () => {
    const bad = await handleApi(new URL('https://x/api/chapter?id=../etc'), ctx());
    expect(bad.status).toBe(400);
    const badRange = await handleApi(new URL('https://x/api/toc?from=1&to=20'), ctx());
    expect(badRange.status).toBe(400);
    const badPage = await handleApi(new URL('https://x/api/toc?from=0&to=1'), ctx());
    expect(badPage.status).toBe(400);
    const missing = await handleApi(new URL('https://x/api/unknown'), ctx());
    expect(missing.status).toBe(404);
  });

  it('上游失败返回可重试错误', { timeout: 30_000 }, async () => {
    const failing = async () => {
      throw new Error('network down');
    };
    const res = await handleApi(new URL('https://x/api/chapter?id=123'), {
      ...ctx(),
      fetcher: failing,
    });
    expect(res.status).toBe(502);
    const body = (await res.json()) as { error: { retryable: boolean } };
    expect(body.error.retryable).toBe(true);
  });

  it('重定向到非书源域名时拒绝', async () => {
    const redirecting = async (): Promise<Response> => {
      const res = new Response(fixture('chapter-38621328-p1.html'), {
        status: 200,
        headers: { 'content-type': 'text/html' },
      });
      Object.defineProperty(res, 'url', { value: 'https://evil.example/book/36780_1.html' });
      return res;
    };
    const res = await handleApi(new URL('https://x/api/chapter?id=123'), {
      ...ctx(),
      fetcher: redirecting,
    });
    expect(res.status).toBe(502);
  });

  it('缓存命中后不再访问上游', async () => {
    let calls = 0;
    const fetcher = async (): Promise<Response> => {
      calls++;
      return new Response(fixture('chapter-38621328-p1.html'), {
        status: 200,
        headers: { 'content-type': 'text/html' },
      });
    };
    const context = { ...ctx(), fetcher };
    await handleApi(new URL('https://x/api/chapter?id=999'), context);
    const after1 = calls;
    await handleApi(new URL('https://x/api/chapter?id=999'), context);
    expect(after1).toBeGreaterThan(0);
    expect(calls).toBe(after1);
  });
});
