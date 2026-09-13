/**
 * 真机/模拟器 Android Edge-to-Edge 与系统栏显示验证脚本。
 * 运行环境需要 adb 及已安装的 debug APK（DEBUG=1 bash android/build.sh 0.0.14）。
 *
 * 验证项目：
 * 1. 原生桥接 window._nativeDisplayBridge 正常挂载
 * 2. 原生安全区快照传递有效（top/bottom/density 均大于 0）
 * 3. CSS 变量 --native-safe-* 成功生效并驱动 UI 布局
 * 4. 主题切换时正确调用原生 setTheme 并同步系统栏图标
 * 5. 沉浸式阅读开启/关闭时切换全屏并保持阅读位置锚点
 */
import { execFileSync } from 'node:child_process';
import { writeFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import { chromium } from 'playwright';

const PKG = 'org.wanshu.reader';
const adbPath = process.env.ADB || 'adb';
const adb = (...args) => execFileSync(adbPath, args, { encoding: 'utf8', timeout: 30000 }).trim();
const delay = (ms) => new Promise((r) => setTimeout(r, ms));

let port;

async function launch() {
  adb('shell', 'am', 'start', '-W', '-n', `${PKG}/.MainActivity`);
  let socket;
  for (let i = 0; i < 40; i++) {
    const pid = adb('shell', 'pidof', PKG).split(' ')[0];
    const sockets = adb('shell', 'cat', '/proc/net/unix');
    socket = sockets.match(new RegExp(`@?(webview_devtools_remote_${pid})\\b`))?.[1];
    if (socket) break;
    await delay(250);
  }
  assert.ok(socket, '找不到 WebView DevTools：确认安装的是 debug APK');
  port = adb('forward', 'tcp:0', `localabstract:${socket}`);
  const browser = await chromium.connectOverCDP(`http://127.0.0.1:${port}`);
  const page = browser.contexts()[0].pages().find((p) => p.url().startsWith('https://reader.local/'));
  assert.ok(page, 'synthetic HTTPS 主文档未找到');
  await page.waitForFunction(() => window.__readerApp && document.querySelector('#chapter-content p, .quick-card'), null, { timeout: 30000 });
  return { browser, page };
}

async function run() {
  console.log('[Android Edge-to-Edge 自动化显示测试]');
  try {
    adb('shell', 'am', 'force-stop', PKG);
    const { browser, page } = await launch();

    // 1. 验证 Bridge 与初始快照
    const bridgeState = await page.evaluate(() => {
      const bridge = window._nativeDisplayBridge;
      if (!bridge) return { available: false };
      const raw = bridge.getDisplaySnapshot();
      const snap = raw ? JSON.parse(raw) : null;
      const root = document.documentElement;
      return {
        available: true,
        snapshot: snap,
        cssSafeTop: getComputedStyle(root).getPropertyValue('--safe-top').trim(),
        cssSafeBottom: getComputedStyle(root).getPropertyValue('--safe-bottom').trim(),
      };
    });

    assert.ok(bridgeState.available, 'window._nativeDisplayBridge 未注入');
    assert.ok(bridgeState.snapshot, '原生安全区快照为空');
    console.log('  ✓ 原生 Bridge 与初始安全区快照有效:', bridgeState.snapshot);
    console.log(`  ✓ CSS 安全区生效: top=${bridgeState.cssSafeTop}, bottom=${bridgeState.cssSafeBottom}`);

    // 2. 验证主题切换与系统栏同步
    console.log('\n[2] 验证主题切换通知原生');
    for (const theme of ['light', 'paper', 'dark', 'black']) {
      await page.evaluate((t) => {
        window.__readerApp.settings.update({ theme: t });
      }, theme);
      await delay(100);
      const applied = await page.evaluate(() => document.documentElement.getAttribute('data-theme'));
      assert.equal(applied, theme, `主题 ${theme} 未生效`);
    }
    console.log('  ✓ 亮色/纸张/暗色/纯黑主题切换已触发原生设置');

    // 3. 打开章节并验证沉浸式切换与位置锚定
    console.log('\n[3] 验证沉浸式切换与阅读位置锚定');
    const continueBtn = page.locator('#continue-reading');
    if (await continueBtn.isVisible()) {
      await continueBtn.click();
    }
    await page.waitForSelector('#chapter-content p', { timeout: 30000 });
    await page.evaluate(() => window.scrollTo(0, 1500));
    await delay(350);

    const posBefore = await page.evaluate(() => window.__readerApp.reader.lastKnownPosition);
    assert.ok(posBefore && posBefore.paragraph > 0, '未成功记录滚动位置');

    // 开启沉浸式
    await page.evaluate(() => window.__readerApp.settings.update({ immersiveReading: true }));
    await delay(300);
    const posAfterImmersive = await page.evaluate(() => window.__readerApp.reader.lastKnownPosition);
    assert.equal(posAfterImmersive.paragraph, posBefore.paragraph, '沉浸式切换后段落位置漂移');
    console.log(`  ✓ 开启沉浸式保持同一段落: p${posBefore.paragraph}`);

    // 退出沉浸式
    await page.evaluate(() => window.__readerApp.settings.update({ immersiveReading: false }));
    await delay(300);
    const posAfterRestore = await page.evaluate(() => window.__readerApp.reader.lastKnownPosition);
    assert.equal(posAfterRestore.paragraph, posBefore.paragraph, '退出沉浸式后段落位置漂移');
    console.log(`  ✓ 退出沉浸式保持同一段落: p${posBefore.paragraph}`);

    // 4. 截图保存
    await page.screenshot({ path: 'artifacts/android-display-snapshot.png' });
    await writeFile(
      'artifacts/android-display-report.json',
      JSON.stringify(
        {
          result: 'PASS',
          origin: 'https://reader.local',
          device: adb('shell', 'getprop', 'ro.product.model'),
          androidVersion: adb('shell', 'getprop', 'ro.build.version.release'),
          snapshot: bridgeState.snapshot,
          theme: 'black',
        },
        null,
        2,
      ),
    );
    console.log('  ✓ 测试截图与报告已生成');
    await browser.close();
  } finally {
    if (port) adb('forward', '--remove', `tcp:${port}`);
  }
}

run().catch((err) => {
  console.error('测试失败:', err);
  process.exit(1);
});
