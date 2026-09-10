import { App } from './app.ts';

// Android APK：在 WebView 内用同构 API 处理 /api/*，书源由原生代理获取
if (import.meta.env.VITE_TARGET === 'android') {
  const { installNativeFetch } = await import('./native-fetch.ts');
  installNativeFetch();
}

const app = new App();
void app.start();

// 供自动化测试/调试使用
(window as unknown as { __readerApp?: App }).__readerApp = app;

if ('serviceWorker' in navigator && location.protocol.startsWith('http')) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      /* 离线增强失败不影响在线阅读 */
    });
  });
}
