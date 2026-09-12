/**
 * 同构 API 层：Cloudflare Worker 与本地 Node 服务器共用。
 * 仅暴露受限路由，不接受任意 URL。
 */
import {
  BOOK,
  fetchChapter,
  fetchTocRange,
  SourceError,
  type ChapterResult,
  type FetchLike,
  type TocRangeResult,
} from './source.ts';

export interface CachedResponse {
  status: number;
  body: string;
  headers?: Record<string, string>;
}

export interface ApiCache {
  get(key: string): Promise<CachedResponse | null>;
  put(key: string, value: CachedResponse, ttlSeconds: number): Promise<void>;
}

export interface ApiContext {
  cache: ApiCache;
  fetcher?: FetchLike;
  now?: () => number;
  /** 测试注入：覆盖书源重试次数（默认走 source.ts 的 MAX_RETRIES） */
  retries?: number;
  signal?: AbortSignal;
}

export const CACHE_TTL = {
  chapter: 30 * 24 * 3600,
  chapterPartial: 120,
  toc: 6 * 3600,
  health: 0,
} as const;

const MAX_TOC_RANGE = 6;
const MAX_TOC_PAGE = 80;

interface ApiErrorBody {
  error: { code: string; message: string; retryable: boolean };
}

function json(
  data: unknown,
  init: { status?: number; headers?: Record<string, string> } = {},
): Response {
  return new Response(JSON.stringify(data), {
    status: init.status ?? 200,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      ...(init.headers ?? {}),
    },
  });
}

function errorResponse(err: unknown): Response {
  if (err instanceof SourceError) {
    const status =
      err.code === 'timeout'
        ? 504
        : err.code === 'http' && err.status
          ? 502
          : err.code === 'invalid'
            ? 400
            : 502;
    const body: ApiErrorBody = {
      error: { code: err.code, message: err.message, retryable: err.retryable },
    };
    return json(body, { status, headers: { 'cache-control': 'no-store' } });
  }
  const message = err instanceof Error ? err.message : String(err);
  const body: ApiErrorBody = { error: { code: 'internal', message, retryable: true } };
  return json(body, { status: 500, headers: { 'cache-control': 'no-store' } });
}

async function cachedJson(
  ctx: ApiContext,
  key: string,
  ttl: number,
  produce: () => Promise<unknown>,
): Promise<Response> {
  if (ttl > 0) {
    const hit = await ctx.cache.get(key);
    if (hit) {
      return json(JSON.parse(hit.body), {
        status: hit.status,
        headers: { ...(hit.headers ?? {}), 'x-reader-cache': 'hit', 'cache-control': 'no-store' },
      });
    }
  }
  const data = await produce();
  const body = JSON.stringify(data);
  if (ttl > 0) {
    await ctx.cache.put(key, { status: 200, body }, ttl);
  }
  return json(data, {
    headers: { 'x-reader-cache': 'miss', 'cache-control': 'no-store' },
  });
}

function parseIntParam(
  params: URLSearchParams,
  name: string,
  min: number,
  max: number,
): number | null {
  const raw = params.get(name);
  if (raw === null || !/^\d{1,4}$/.test(raw)) return null;
  const n = parseInt(raw, 10);
  if (n < min || n > max) return null;
  return n;
}

export async function handleApi(
  url: URL,
  ctx: ApiContext,
): Promise<Response> {
  try {
    switch (url.pathname) {
      case '/api/health': {
        return json({
          ok: true,
          book: { id: BOOK.id, title: BOOK.title, author: BOOK.author },
          now: (ctx.now ?? Date.now)(),
        });
      }

      case '/api/toc': {
        const from = parseIntParam(url.searchParams, 'from', 1, MAX_TOC_PAGE);
        const to = parseIntParam(url.searchParams, 'to', 1, MAX_TOC_PAGE);
        if (from === null || to === null || to < from || to - from + 1 > MAX_TOC_RANGE) {
          return json(
            {
              error: {
                code: 'bad_range',
                message: `目录范围无效（from/to，跨度≤${MAX_TOC_RANGE}）`,
                retryable: false,
              },
            },
            { status: 400 },
          );
        }
        return await cachedJson(ctx, `toc:v2:${BOOK.id}:${from}-${to}`, CACHE_TTL.toc, () =>
          fetchTocRange(from, to, { fetcher: ctx.fetcher, signal: ctx.signal, retries: ctx.retries }),
        );
      }

      case '/api/chapter': {
        const id = url.searchParams.get('id') ?? '';
        if (!/^\d{1,12}$/.test(id)) {
          return json(
            { error: { code: 'bad_id', message: '章节 id 无效', retryable: false } },
            { status: 400 },
          );
        }
        {
          // v4：分页发现改为 frontier + 终章证据；旧版本可能把“未发现分页”
          // 的残缺一章缓存成 complete:true 30 天，必须绕过。
          const key = `chapter:v4:${BOOK.id}:${id}`;
          const hit = await ctx.cache.get(key);
          if (hit) {
            return json(JSON.parse(hit.body), {
              headers: { 'x-reader-cache': 'hit', 'cache-control': 'no-store' },
            });
          }
          const data = await fetchChapter(id, { fetcher: ctx.fetcher, retries: ctx.retries, signal: ctx.signal, highPriority: url.searchParams.get('background') !== '1' });
          const ttl = data.complete ? CACHE_TTL.chapter : CACHE_TTL.chapterPartial;
          await ctx.cache.put(key, { status: 200, body: JSON.stringify(data) }, ttl);
          return json(data, {
            headers: { 'x-reader-cache': 'miss', 'cache-control': 'no-store' },
          });
        }
      }

      default:
        return json(
          { error: { code: 'not_found', message: '接口不存在', retryable: false } },
          { status: 404 },
        );
    }
  } catch (err) {
    return errorResponse(err);
  }
}

export interface ApiResultTypes {
  chapter: ChapterResult;
  toc: TocRangeResult;
}

/* ------------------------------------------------------------------ */
/* In-memory cache（Node dev / 单机自托管）                             */
/* ------------------------------------------------------------------ */

export class MemoryApiCache implements ApiCache {
  private store = new Map<string, { value: CachedResponse; expires: number }>();
  private maxEntries: number;

  constructor(maxEntries = 2000) {
    this.maxEntries = maxEntries;
  }

  async get(key: string): Promise<CachedResponse | null> {
    const hit = this.store.get(key);
    if (!hit) return null;
    if (hit.expires < Date.now()) {
      this.store.delete(key);
      return null;
    }
    // LRU 触碰
    this.store.delete(key);
    this.store.set(key, hit);
    return hit.value;
  }

  async put(key: string, value: CachedResponse, ttlSeconds: number): Promise<void> {
    this.store.set(key, { value, expires: Date.now() + ttlSeconds * 1000 });
    while (this.store.size > this.maxEntries) {
      const oldest = this.store.keys().next().value;
      if (oldest === undefined) break;
      this.store.delete(oldest);
    }
  }

  get size(): number {
    return this.store.size;
  }
}
