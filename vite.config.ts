import { defineConfig, type Plugin } from 'vite';
import { readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { apiPlugin } from './src/server/vite-plugin.ts';

const buildId = process.env.BUILD_ID ?? String(Date.now());

/** 把构建产物清单写入 Service Worker 的预缓存列表 */
function swPrecachePlugin(): Plugin {
  return {
    name: 'reader-sw-precache',
    apply: 'build',
    async writeBundle(options, bundle) {
      const outDir = options.dir ?? 'dist';
      const assets = bundle
        ? Object.keys(bundle).map((name) => `/${name.replace(/^\.\//, '')}`)
        : [];
      const precache = ['/', '/index.html', '/manifest.webmanifest', '/icon.svg', ...assets];
      const unique = [...new Set(precache)];
      const swPath = join(outDir, 'sw.js');
      try {
        const source = await readFile(swPath, 'utf8');
        const next = source
          .replace('/*__PRECACHE__*/[]', JSON.stringify(unique))
          .replace('/*__BUILD_ID__*/"dev"', JSON.stringify(buildId));
        await writeFile(swPath, next);
      } catch {
        /* sw.js 不存在时忽略 */
      }
    },
  };
}

export default defineConfig({
  root: 'src/web',
  publicDir: '../../public',
  plugins: [apiPlugin(), swPrecachePlugin()],
  define: {
    __BUILD_ID__: JSON.stringify(buildId),
  },
  build: {
    outDir: '../../dist',
    emptyOutDir: true,
    target: 'es2022',
    cssTarget: 'chrome105',
    assetsDir: 'assets',
    sourcemap: false,
  },
  server: {
    host: true,
    port: 5173,
  },
  preview: {
    host: true,
    port: 4173,
  },
});
