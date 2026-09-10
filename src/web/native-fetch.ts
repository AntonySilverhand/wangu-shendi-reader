/**
 * Android APK 专用：把 /api/* 的请求在 WebView 内用打包的 handleApi 处理，
 * 书源 HTML 通过本机 /native/source 代理获取（原生层无跨域限制）。
 */
import { handleApi, MemoryApiCache } from '../shared/api.ts';
import type { FetchLike } from '../shared/source.ts';

export function installNativeFetch(): void {
  const cache = new MemoryApiCache(500);

  const nativeFetcher: FetchLike = async (url) => {
    const res = await fetch(`/native/source?url=${encodeURIComponent(url)}`);
    const body = await res.text();
    const finalUrl = res.headers.get('x-final-url') ?? url;
    const out = new Response(body, {
      status: res.status,
      headers: { 'content-type': 'text/html; charset=utf-8' },
    });
    Object.defineProperty(out, 'url', { value: finalUrl });
    return out;
  };

  const realFetch = window.fetch.bind(window);
  window.fetch = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const raw =
      typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    try {
      const url = new URL(raw, location.origin);
      if (url.origin === location.origin && url.pathname.startsWith('/api/')) {
        return handleApi(new URL(url.pathname + url.search, 'https://reader.local'), {
          cache,
          fetcher: nativeFetcher,
        });
      }
    } catch {
      /* 回退到真实 fetch */
    }
    return realFetch(input as RequestInfo, init);
  };
}
