/* 应用外壳离线缓存。正文缓存由应用层（IndexedDB）管理，不走这里。 */
const BUILD_ID = /*__BUILD_ID__*/"dev";
const SHELL_CACHE = `reader-shell-${BUILD_ID}`;
const RUNTIME_CACHE = `reader-runtime-${BUILD_ID}`;
const PRECACHE = /*__PRECACHE__*/[];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(SHELL_CACHE)
      .then((cache) => cache.addAll(PRECACHE))
      .catch(() => undefined)
      .then(() => self.skipWaiting()),
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys
            .filter(
              (key) =>
                (key.startsWith('reader-shell-') && key !== SHELL_CACHE) ||
                (key.startsWith('reader-runtime-') && key !== RUNTIME_CACHE),
            )
            .map((key) => caches.delete(key)),
        ),
      )
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== self.location.origin) return;
  // /api 由应用自行缓存（IndexedDB），避免 SW 与业务缓存重复
  if (url.pathname.startsWith('/api/')) return;

  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .then((response) => {
          const copy = response.clone();
          caches.open(RUNTIME_CACHE).then((cache) => cache.put('/index.html', copy)).catch(() => undefined);
          return response;
        })
        .catch(() =>
          caches.match('/index.html').then((cached) => cached ?? caches.match('/')),
        ),
    );
    return;
  }

  if (url.pathname.startsWith('/assets/') || url.pathname === '/icon.svg') {
    event.respondWith(
      caches.match(request).then(
        (cached) =>
          cached ??
          fetch(request).then((response) => {
            if (response.ok) {
              const copy = response.clone();
              caches.open(RUNTIME_CACHE).then((cache) => cache.put(request, copy)).catch(() => undefined);
            }
            return response;
          }),
      ),
    );
    return;
  }

  event.respondWith(
    caches.match(request).then((cached) => cached ?? fetch(request)),
  );
});
