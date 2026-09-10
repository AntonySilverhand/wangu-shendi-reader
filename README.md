# 万古神帝 · 个人阅读器

一个干净、专注、适合长时间阅读的**个人**小说阅读网站，只服务于《万古神帝》（书源：万书阁 book 36780）。正文是视觉中心，手机优先，同时适配折叠屏、平板、桌面与外接显示器。

> 声明：本项目仅供个人阅读使用。正文按需从公开书源获取并缓存在使用者本机，不打包、不再分发正文内容。书源结构可能变化，适配层已与阅读器解耦，便于单独修复。

---

## 快速开始

```bash
npm install          # 安装依赖（Node >= 22）
npm run dev          # Vite 开发服务器（内置 /api，默认 http://localhost:5173）
npm run build        # 构建到 dist/
npm run serve:prod   # 本地预览构建产物 + /api（默认 http://localhost:8787）
npm test             # 书源解析单元测试（真实 HTML fixture）
npm run typecheck    # TypeScript 检查
bash tools/verify.sh   # 构建 + 起服务 + 聚焦回归验证（2 分钟内）
bash tools/run-e2e.sh  # 完整端到端套件（后台运行，日志 artifacts/e2e.log）
python3 tools/make-icons.py   # 重新生成 PWA PNG 图标（可选）
```

本地服务器会绑定 `0.0.0.0` 并打印局域网地址，手机连同一 Wi-Fi 即可访问测试。

---

## 技术选型与理由

| 层 | 选择 | 理由 |
|---|---|---|
| 前端 | 原生 TypeScript + Vite，无框架 | 阅读器是长生命周期页面，DOM 结构简单；无框架运行时约 25KB gzip，启动快、内存小、无虚拟 DOM 开销，便于精确控制重排与阅读位置 |
| 后端 | 一份同构代码，两处运行：Node（本地）/ Cloudflare Worker（线上） | 浏览器跨域限制必须由后端取书源；同构保证本地与线上行为一致，也避免被平台锁定 |
| 正文缓存 | IndexedDB（按章） | 单章几十 KB，按章存储便于按需淘汰与统计；localStorage 只放设置/进度/书签等小数据 |
| 离线外壳 | Service Worker + Workbox 风格的极简自写缓存 | 只缓存应用外壳，正文由 IndexedDB 管理，避免两层缓存互相干扰 |
| 字体 | 系统字体栈 | 零网络请求、零 FOUT，中文显示稳定，功耗低 |

### 目录结构

```
src/
  shared/         # 同构：书源适配、HTML 清洗、API 路由、缓存接口
    source.ts     #   ├─ 受限 URL 构建、目录/章节解析、重传章节去重排序
    api.ts        #   └─ /api/health、/api/toc、/api/chapter
  worker/         # Cloudflare Worker 入口（线上）
  server/         # Node 中间件 + 本地预览服务器（本地）
  web/            # 前端
    app.ts        #   应用编排：路由、阅读流程、全局输入
    views/        #   阅读器、目录、设置、书签、章内搜索、首页
    store/        #   settings/personal/db/toc/chapter/download/txt
    styles/       #   四套主题 + 布局（CSS 变量驱动）
public/           # manifest、Service Worker、图标
test/             # 单元测试 + 真实 HTML fixture + e2e.mjs
```

**解耦**：书源结构只出现在 `src/shared/source.ts`。若书源改版，只需更新该文件的解析函数与 fixture 测试；正文清洗、存储、阅读界面均不受影响。

---

## 书源适配说明（实测结论）

以 2026-09 的实际抓取为准：

- 目录：`/book/36780/`（第 1 页，详情页）与 `/book/36780/{n}.html`（第 n+1 页），共 44 页。每页含置顶的“新书 + 番外”块和页尾“分节阅读第 1 节…”快捷链接，解析时按位置与章节号中位数过滤。
- 章节第 1 页：`/book/36780_{chapterId}.html`；后续分页：`/book/36780/{chapterId}_{page}.html`。
- 正文以 `document.writeln(qsbs.bb('<base64>'))` 形式嵌入，Base64 解码后是 `<p>` 片段；无编码时回退解析 `#content`。
- **分页与“下一章”的区分**：同一章节的多页链接指向相同 chapterId（不同 `_n` 后缀），而“下一章”指向不同 chapterId。适配层统一按 chapterId 判断，自动合并分页、按段落去重、保持顺序；最后一页的“下一章”才作为真正的下一章。
- **源站数据质量**：同一章存在多次上传（例如 `第731章 圣威` 与 `第七百三十一章 圣威`），也出现过同名不同号的章节。合并策略：同章号优先保留有标题的版本；同章号不同标题的条目保留（源站错号）；番外按番外序号排序并保留最新上传。
- 源站偶发连接重置/超时，适配层设置了超时、有限重试与指数退避；成功结果在服务端（Worker Cache API / 内存）与客户端（IndexedDB）双重缓存，避免重复抓取。

**后端接口限制**（防止成为任意 URL 代理）：

- 只接受 `/api/chapter?id=<1-12 位数字>`、`/api/toc?from=&to=`（跨度 ≤ 6、页码 ≤ 80）、`/api/health`。
- URL 只能由固定 origin + book id 拼接；跟随重定向后再次校验 host 必须属于 `wanshuge.org`；校验 `content-type`、响应体大小上限；不透明地丢弃非正文内容（广告、跟踪脚本从不进入存储）。
- 未实现：登录、付费、验证码绕过的任何逻辑。

---

## 阅读体验

- **四套主题 + 纯黑**：亮色 / 暗色 / 纯黑 / 仿电子墨水（关闭动效、灰阶）/ 纸张（静态细纹可关）。主题覆盖正文、目录、菜单、弹窗、加载骨架。首屏前由内联脚本应用主题，避免闪白。
- **排版可调并持久化**：字体（系统/黑体/宋体/楷体）、字号 14–28px、行距 1.3–2.4、段间距、页边距、宽屏行宽。
- **阅读位置 = 段落序号 + 段落内字符偏移**：改字号、改行距、旋转、折叠展开、窗口缩放后按锚点恢复，不依赖滚动像素。进度节流写入 localStorage，并在切章、隐藏页面、`pagehide` 时补写。
- **输入方式**：触屏滑动与点击唤出工具栏；鼠标滚轮、点击、滚动条；键盘 `←/→` 翻章、`T` 目录、`F` 章内搜索、`B` 书签、`,`/`S` 设置、`Esc` 关闭浮层。输入框聚焦时全局快捷键不拦截，浏览器原生滚动/查找/缩放/选字不受影响。触摸目标 ≥ 44px，底栏在键鼠环境自动隐藏（基于 hover/pointer 能力而非屏幕尺寸）。
- **全尺寸布局**：断点依据可用宽度；手机单栏，≥960px 显示目录侧栏并限制行宽；处理 `safe-area-inset`、动态地址栏与虚拟键盘（`interactive-widget=resizes-content`）。
- **必要功能**：目录（4300+ 章虚拟列表 + 搜索 + 懒加载）、上下章、自动保存与继续阅读、书签、章内搜索（CSS Custom Highlight API，回退 `<mark>`）、按范围下载（进度/失败/取消/重试）、缓存容量查看与清理、个人数据导出/导入、本地 TXT 导入与已缓存导出、PWA 安装。
- **离线边界**：已下载章节可离线阅读；未下载章节仍需联网。个人数据（书签/进度/设置）仅存本机，**不会自动跨设备同步**。

---

## 性能设计

- 静止阅读无动画循环、无轮询、无定时网络请求；页面隐藏时暂停预取与屏幕常亮锁。
- 滚动处理使用 `requestAnimationFrame` 节流 + `passive` 监听；位置捕获用二分查找段落，每次约 10 次布局读取。
- 按章渲染，离开章节即替换 DOM；目录为固定行高虚拟列表；不把整本书放进内存。
- 预取仅下一章，可在设置关闭，并尊重 `navigator.connection.saveData` 与 2G 网络。
- 无大面积模糊/滤镜/动态纹理；仅顶栏使用轻量 `backdrop-filter`，电子墨水主题与 `prefers-reduced-motion` 下关闭全部动效。
- 实测（本地生产构建 + Chromium，结果见 `artifacts/e2e-results.json`、`artifacts/verify-final.json`）：
  - JS 25.9KB gzip、CSS 5.6KB gzip（无框架、无网络字体）
  - 启动到可交互 ~200ms；进入章节（含网络与渲染）~950ms（书源已缓存时）
  - 静止阅读 4s 内网络请求 ≤1；JS 堆 ~10MB；8 种视口无横向溢出，桌面行宽 648px（36 字/行）
  - 改字号后位置锚定：段落 44 → 段落 44；连续缩放：段落 23 → 段落 23
  - 完整 E2E 55/55 通过（主题、目录搜索、章内搜索、书签、离线、多视口、键鼠、性能）

## 数据与缓存

| 数据 | 位置 | 清理影响 |
|---|---|---|
| 设置、阅读进度、书签、最近阅读 | localStorage | 清理正文缓存不受影响，可导出/导入 JSON |
| 正文缓存、目录缓存、本地导入书 | IndexedDB `reader-db` | 可随时清理并重新获取（本地导入书除外） |

浏览器存储有配额限制；设置页显示 `navigator.storage.estimate()` 的用量与配额。缓存写入失败不会影响当次阅读。

---

## 部署到 Cloudflare（无需自购服务器）

`wrangler.jsonc` 已就绪：静态资源走 Workers Static Assets，`/api/*` 走 Worker，书源缓存用 Cache API。

```bash
npm i -g wrangler        # 或使用 devDependencies 中的 npx wrangler
npx wrangler login       # 或 export CLOUDFLARE_API_TOKEN=...
npm run deploy           # build + wrangler deploy
```

发布后得到 `https://<name>.<subdomain>.workers.dev` 链接。若使用 Cloudflare Tunnel 暴露本机服务，请把隧道指向 `npm run serve:prod` 的端口；但这种方式依赖本机常开，**不满足“日常阅读不依赖电脑开机”**，仅在临时联调时使用。

## 已知限制 / 未验证项

- 书源本身有缺章与重复上传（源站问题），阅读器按源站现有目录呈现并做去重排序。
- 真机功耗、帧率与续航未测量（当前环境无真机，未编造数据）；性能结论来自桌面 Chromium 与本地网络，移动端为视口模拟。
- 完整 E2E 在 8 种视口 + 6 类交互场景下 55/55 通过；最近修复（隐藏属性、行宽公式、TXT 打开行为）由 `tools/verify.sh` 聚焦回归全部覆盖。
- 跨设备进度同步未实现；多设备各自独立。
- TXT 导入的章节识别基于常见标题格式，复杂排版可能需要手动整理。
- 折叠屏展开/收起通过 Playwright 视口模拟验证，未在真实折叠屏上验证。
