/**
 * Cloudflare Worker 入口（部署时使用；本地开发走 Vite 插件 + Node dev server）。
 * 静态资源由 Workers Static Assets 提供，/api/* 由 Worker 处理。
 */
import { handleApi, type ApiCache, type CachedResponse } from '../shared/api.ts';

interface Env {
  ASSETS: { fetch: (request: Request) => Promise<Response> };
}

const CACHE_ORIGIN = 'https://reader-cache.invalid/';

/** Cloudflare Cache API 实现（跨 isolate 生效，降低对书源的重复请求） */
function createWorkerCache(): ApiCache {
  // Cloudflare Workers 只提供 caches.default（DOM 类型里没有该字段）
  const cache = (caches as unknown as { default: Cache }).default;
  return {
    async get(key: string): Promise<CachedResponse | null> {
      const hit = await cache.match(CACHE_ORIGIN + encodeURIComponent(key));
      if (!hit) return null;
      return { status: hit.status, body: await hit.text() };
    },
    async put(key: string, value: CachedResponse, ttlSeconds: number): Promise<void> {
      const response = new Response(value.body, {
        status: value.status,
        headers: {
          'content-type': 'application/json; charset=utf-8',
          'cache-control': `public, max-age=${ttlSeconds}`,
        },
      });
      await cache.put(CACHE_ORIGIN + encodeURIComponent(key), response);
    },
  };
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname.startsWith('/api/')) {
      if (request.method !== 'GET' && request.method !== 'HEAD') {
        return new Response(
          JSON.stringify({ error: { code: 'method', message: '仅支持 GET', retryable: false } }),
          { status: 405, headers: { 'content-type': 'application/json; charset=utf-8' } },
        );
      }
      return handleApi(url, { cache: createWorkerCache() });
    }
    return env.ASSETS.fetch(request);
  },
};
