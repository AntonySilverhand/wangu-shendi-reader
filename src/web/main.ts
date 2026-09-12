import { App } from './app.ts';
import { installDebugHooks } from './instrument.ts';

installDebugHooks();

// Android APK：在 WebView 内用同构 API 处理 /api/*，书源由原生代理获取
if (import.meta.env.VITE_TARGET === 'android') {
  const { installNativeFetch } = await import('./native-fetch.ts');
  installNativeFetch();
}

const app = new App();
void app.start();

// 供自动化测试/调试使用
(window as unknown as { __readerApp?: App }).__readerApp = app;

// Android APK：APK 内置资源，Service Worker 会与 shouldInterceptRequest 冲突、
// 造成旧 JS 持续运行/缓存污染，且无离线意义 → 明确禁用；Web/PWA 版保留。
const androidTarget = import.meta.env.VITE_TARGET === 'android';
if ('serviceWorker' in navigator && location.protocol.startsWith('http')
  && !androidTarget && location.hostname !== 'reader.local') {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      /* 离线增强失败不影响在线阅读 */
    });
  });
}
