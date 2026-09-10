import { App } from './app.ts';

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
