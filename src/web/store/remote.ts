/** 后端 API 客户端：超时、有限重试、退避。只访问本站 /api/。 */

export interface ApiErrorBody {
  error: { code: string; message: string; retryable: boolean };
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly code: string,
    readonly retryable: boolean,
    readonly status: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export interface ApiGetOptions {
  signal?: AbortSignal;
  retries?: number;
  timeoutMs?: number;
  onRetry?: (attempt: number, delayMs: number) => void;
}

const sleep = (ms: number, signal?: AbortSignal): Promise<void> =>
  new Promise((resolve, reject) => {
    if (signal?.aborted) { reject(new DOMException('aborted', 'AbortError')); return; }
    const abort = () => {
      clearTimeout(t);
      reject(new DOMException('aborted', 'AbortError'));
    };
    const t = setTimeout(() => {
      signal?.removeEventListener('abort', abort);
      resolve();
    }, ms);
    signal?.addEventListener('abort', abort, { once: true });
  });

export async function apiGet<T>(path: string, opts: ApiGetOptions = {}): Promise<T> {
  const retries = opts.retries ?? 3;
  const timeoutMs = opts.timeoutMs ?? 20_000;
  let lastError: unknown = null;

  for (let attempt = 0; attempt <= retries; attempt++) {
    if (opts.signal?.aborted) throw new DOMException('aborted', 'AbortError');
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    const onAbort = () => controller.abort();
    opts.signal?.addEventListener('abort', onAbort, { once: true });
    try {
      const res = await fetch(path, {
        signal: controller.signal,
        headers: { accept: 'application/json' },
        credentials: 'same-origin',
      });
      if (!res.ok) {
        let body: ApiErrorBody | null = null;
        try {
          body = (await res.json()) as ApiErrorBody;
        } catch {
          body = null;
        }
        const retryable = body?.error?.retryable ?? res.status >= 500;
        throw new ApiError(
          body?.error?.message ?? `请求失败（HTTP ${res.status}）`,
          body?.error?.code ?? 'http',
          retryable,
          res.status,
        );
      }
      return (await res.json()) as T;
    } catch (err) {
      lastError = err;
      const aborted =
        (err instanceof DOMException && err.name === 'AbortError') ||
        (err instanceof Error && err.name === 'AbortError');
      if (opts.signal?.aborted) throw new DOMException('aborted', 'AbortError');
      const retryable =
        err instanceof ApiError ? err.retryable : !aborted || !opts.signal?.aborted;
      if (!retryable || attempt === retries) throw err;
      const delay = Math.min(5000, 600 * 2 ** attempt) + Math.random() * 300;
      opts.onRetry?.(attempt + 1, delay);
      await sleep(delay, opts.signal);
    } finally {
      clearTimeout(timer);
      opts.signal?.removeEventListener('abort', onAbort);
    }
  }
  throw lastError ?? new Error('请求失败');
}
