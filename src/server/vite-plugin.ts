import type { Plugin } from 'vite';
import { MemoryApiCache } from '../shared/api.ts';
import { createApiMiddleware } from './middleware.ts';

/** 在 Vite dev/preview server 上挂载与生产 Worker 相同的 /api 实现 */
export function apiPlugin(): Plugin {
  const cache = new MemoryApiCache();
  const attach = (middlewares: {
    use: (fn: (req: never, res: never, next: never) => void) => void;
  }) => {
    const handler = createApiMiddleware(cache);
    middlewares.use(handler as unknown as (req: never, res: never, next: never) => void);
  };
  return {
    name: 'reader-api',
    configureServer(server) {
      attach(server.middlewares as never);
    },
    configurePreviewServer(server) {
      attach(server.middlewares as never);
    },
  };
}
