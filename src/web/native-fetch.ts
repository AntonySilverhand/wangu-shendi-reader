/** Android: 同构 API + 受限原生 HTML 传输。取消必须传到底层 fetch。 */
import { handleApi, MemoryApiCache } from '../shared/api.ts';
import type { FetchLike } from '../shared/source.ts';

export function installNativeFetch(): void {
  const cache = new MemoryApiCache(500);
  const realFetch = window.fetch.bind(window);
  const nativeFetcher: FetchLike = async (url, init) => {
    const res = await realFetch(`/native/source?url=${encodeURIComponent(url)}`, { signal: init?.signal });
    const out = new Response(await res.text(), {
      status: res.status,
      headers: { 'content-type': 'text/html; charset=utf-8' },
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
