/**
 * 独立 Node 服务器：本地预览构建产物 + /api（与 Cloudflare Worker 同一套实现）。
 * 用法：node --experimental-strip-types src/server/dev.ts [--dist dist] [--port 8787]
 */
import { createServer } from 'node:http';
import { createReadStream } from 'node:fs';
import { readFile, stat } from 'node:fs/promises';
import { join, extname, normalize, resolve } from 'node:path';
import { gzipSync } from 'node:zlib';
import { networkInterfaces } from 'node:os';
import { FileApiCache } from './file-cache.ts';
import { createApiMiddleware } from './middleware.ts';

const args = process.argv.slice(2);
const getArg = (name: string, fallback: string): string => {
  const i = args.indexOf(name);
  return i >= 0 && args[i + 1] ? args[i + 1]! : fallback;
};

const port = parseInt(getArg('--port', process.env.PORT ?? '8787'), 10);
const distDir = resolve(getArg('--dist', 'dist'));

const MIME: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.webp': 'image/webp',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
  '.txt': 'text/plain; charset=utf-8',
  '.map': 'application/json; charset=utf-8',
};

const cache = new FileApiCache(resolve('.cache/api'));
const api = createApiMiddleware(cache);

const server = createServer(async (req, res) => {
  api(req, res, () => {
    void serveStatic(req, res);
  });
});

async function serveStatic(
  req: import('node:http').IncomingMessage,
  res: import('node:http').ServerResponse,
): Promise<void> {
  try {
    const url = new URL(req.url ?? '/', `http://${req.headers.host ?? 'localhost'}`);
    if (req.method !== 'GET' && req.method !== 'HEAD') {
      res.statusCode = 405;
      res.end('Method Not Allowed');
      return;
    }
    let pathname = decodeURIComponent(url.pathname);
    if (pathname.endsWith('/')) pathname += 'index.html';
    let filePath = normalize(join(distDir, pathname));
    if (!filePath.startsWith(distDir)) {
      res.statusCode = 403;
      res.end('Forbidden');
      return;
    }
    let info = await stat(filePath).catch(() => null);
    if (!info || info.isDirectory()) {
      // SPA 回退
      filePath = join(distDir, 'index.html');
      info = await stat(filePath).catch(() => null);
      if (!info) {
        res.statusCode = 404;
        res.end('Build not found. Run `npm run build` first.');
        return;
      }
    }
    const ext = extname(filePath).toLowerCase();
    const mime = MIME[ext] ?? 'application/octet-stream';
    const etag = `W/"${info.size}-${Math.round(info.mtimeMs)}"`;
    if (req.headers['if-none-match'] === etag) {
      res.statusCode = 304;
      res.end();
      return;
    }
    const immutable = pathname.startsWith('/assets/');
    res.setHeader('content-type', mime);
    res.setHeader('etag', etag);
    res.setHeader(
      'cache-control',
      immutable ? 'public, max-age=31536000, immutable' : 'no-cache',
    );
    if (req.method === 'HEAD') {
      res.statusCode = 200;
      res.end();
      return;
    }
    const compressible = /^(text\/|application\/(json|manifest\+json)|image\/svg)/.test(mime);
    if (compressible && /\bgzip\b/.test(req.headers['accept-encoding'] ?? '')) {
      const body = await readFile(filePath);
      const gzipped = gzipSync(body);
      res.setHeader('content-encoding', 'gzip');
      res.setHeader('content-length', String(gzipped.byteLength));
      res.statusCode = 200;
      res.end(gzipped);
      return;
    }
    res.statusCode = 200;
    createReadStream(filePath).pipe(res);
  } catch (err) {
    res.statusCode = 500;
    res.end(err instanceof Error ? err.message : 'error');
  }
}

server.listen(port, '0.0.0.0', () => {
  const addresses: string[] = [];
  for (const list of Object.values(networkInterfaces())) {
    for (const entry of list ?? []) {
      if (entry.family === 'IPv4' && !entry.internal) addresses.push(`http://${entry.address}:${port}`);
    }
  }
  console.log(`\n  阅读器已启动（本地预览）`);
  console.log(`  本机:   http://localhost:${port}`);
  for (const addr of addresses) console.log(`  局域网: ${addr}`);
  console.log('');
});
