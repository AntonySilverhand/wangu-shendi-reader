import type { IncomingMessage, ServerResponse } from 'node:http';
import { handleApi, type ApiCache } from '../shared/api.ts';

export type NextFn = (err?: unknown) => void;

/** Node（Vite dev server / 自托管）上的 /api 中间件，复用 Worker 同一套 handler */
export function createApiMiddleware(cache: ApiCache) {
  return async function apiMiddleware(
    req: IncomingMessage,
    res: ServerResponse,
    next: NextFn,
  ): Promise<void> {
    const rawUrl = req.url ?? '/';
    if (!rawUrl.startsWith('/api/')) {
      next();
      return;
    }
    if (req.method !== 'GET' && req.method !== 'HEAD') {
      res.statusCode = 405;
      res.setHeader('content-type', 'application/json; charset=utf-8');
      res.end(JSON.stringify({ error: { code: 'method', message: '仅支持 GET', retryable: false } }));
      return;
    }
    try {
      const url = new URL(rawUrl, `http://${req.headers.host ?? 'localhost'}`);
      const response = await handleApi(url, { cache });
      res.statusCode = response.status;
      response.headers.forEach((value, key) => {
        if (key.toLowerCase() === 'content-encoding') return;
        res.setHeader(key, value);
      });
      const buffer = Buffer.from(await response.arrayBuffer());
      res.setHeader('content-length', String(buffer.byteLength));
      if (req.method === 'HEAD') {
        res.end();
        return;
      }
      res.end(buffer);
    } catch (err) {
      res.statusCode = 500;
      res.setHeader('content-type', 'application/json; charset=utf-8');
      res.end(
        JSON.stringify({
          error: { code: 'internal', message: err instanceof Error ? err.message : 'error', retryable: true },
        }),
      );
    }
  };
}
