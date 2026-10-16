/** Android: 同构 API + 受限原生 HTML 传输。取消必须传到底层 fetch。 */
import { handleApi, MemoryApiCache } from '../shared/api.ts';
import type { FetchLike } from '../shared/source.ts';

export function installNativeFetch(): void {
  const cache = new MemoryApiCache(500);
  const realFetch = window.fetch.bind(window);
  const nativeFetcher: FetchLike = async (url, init) => {
    // 站内搜索为 POST：拦截层拿不到请求体，表单经 query 传给原生，由原生发 POST
    const form = init?.method === 'POST' && typeof init.body === 'string' ? `&form=${encodeURIComponent(init.body)}` : '';
    const res = await realFetch(`/native/source?url=${encodeURIComponent(url)}${form}`, { signal: init?.signal });
    // 原样转交字节与字符集：69书吧是 GBK，res.text() 会按 UTF-8 解码成乱码
    const out = new Response(await res.arrayBuffer(), {
      status: res.status,
      headers: { 'content-type': res.headers.get('content-type') ?? 'text/html; charset=utf-8' },
    });
    Object.defineProperty(out, 'url', { value: res.headers.get('x-final-url') ?? url });
    return out;
  };

  window.fetch = (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const raw = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    const url = new URL(raw, location.origin);
    if (url.origin !== location.origin || !url.pathname.startsWith('/api/')) return realFetch(input, init);
    const signal = init?.signal ?? (input instanceof Request ? input.signal : undefined);
    if (signal?.aborted) return Promise.reject(new DOMException('aborted', 'AbortError'));
    return new Promise((resolve, reject) => {
      const abort = () => reject(new DOMException('aborted', 'AbortError'));
      signal?.addEventListener('abort', abort, { once: true });
      void handleApi(url, { cache, fetcher: nativeFetcher, signal: signal ?? undefined })
        .then(resolve, reject)
        .finally(() => signal?.removeEventListener('abort', abort));
    });
  };
}
