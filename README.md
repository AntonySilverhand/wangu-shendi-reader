# 万书阅读器 · 个人小说阅读器

一个干净、专注、适合长时间阅读的**个人**小说阅读网站。可在书源（万书阁、69书吧、飘天文学、爱下电子书）中**按书名/作者搜索并阅读任意小说**，也可直接粘贴书籍/章节网址打开，书架管理多本书（含本地导入的 TXT 与整本下载的书）；预置书为《万古神帝》（book 36780）。正文是视觉中心，手机优先，同时适配折叠屏、平板、桌面与外接显示器。

> 声明：本项目仅供个人阅读使用。正文按需从公开书源获取并缓存在使用者本机，不打包、不再分发正文内容。书源结构可能变化，适配层已与阅读器解耦，便于单独修复。

---

v0.0.13 预发布：[修复说明、验证记录、Android 真机验收与旧版数据迁移注意事项](docs/v0.0.13-validation.md)。Android 使用固定 `https://reader.local/` origin，无本地监听端口。

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

**解耦**：书源结构只出现在书源适配文件（万书阁 `src/shared/source.ts`、69书吧 `src/shared/source-69shuba.ts`、飘天文学 `src/shared/source-piaotia.ts`、爱下电子书 `src/shared/source-ixdzs.ts`），`src/shared/sources.ts` 按书号分派。若书源改版，只需更新对应文件的解析函数与 fixture 测试；正文清洗、存储、阅读界面均不受影响。

---

## 书源适配说明（实测结论）

以 2026-09 的实际抓取为准：

- 目录：`/book/36780/`（第 1 页，详情页）与 `/book/36780/{n}.html`（第 n+1 页），共 44 页。每页含置顶的“新书 + 番外”块和页尾“分节阅读第 1 节…”快捷链接，解析时按位置与章节号中位数过滤。
- 章节第 1 页：`/book/36780_{chapterId}.html`；后续分页：`/book/36780/{chapterId}_{page}.html`。
- 正文以 `document.writeln(qsbs.bb('<base64>'))` 形式嵌入，Base64 解码后是 `<p>` 片段；无编码时回退解析 `#content`。
- **分页与“下一章”的区分**：同一章节的多页链接指向相同 chapterId（不同 `_n` 后缀），而“下一章”指向不同 chapterId。源站按钮文字不可信（第一页的“下一章”按钮经常指向同章下一页，中间页的“上一章”也指向同章上一页），适配层完全按 URL 中的 chapterId 判定：同 id = 同章分页，不同 id 的首页链接 = 真正相邻章节。分页发现用 frontier：每页发现的所有同章分页链接都会被抓取，不再依赖单条“下一页”链。
- **终章证据（complete 语义）**：complete 只来自积极证据——① 末页存在指向其它 chapterId 的真正下一章；② 探针确认不存在更大的同章分页。源站对超出末尾的分页不回 404，而是回环到第一页（内容重复、`lastread.set`/标题自报页码回退），回环与 404 都被视为“该分页不存在”的积极证据。只有第 0 页时即使有“下一章”链接也会探一次 `_1`（防御源站 CDN 旧模板），每章最多探一次。无法确认时 complete=false（服务端只缓存 2 分钟、客户端后台重新验证），绝不把“没发现分页”当成完整。
- **正文合并**：按 pageIndex 排序合并；仅消除“整页重发”与“分页处上页末段=下页首段”两类重复，原文中合法重复句（连声惊呼等）原样保留。缓存键 `chapter:v4`（客户端 `CHAPTER_CACHE_VERSION=4`），旧版本可能把残缺单页当成 complete 缓存 30 天，升版本后自动绕过。
- **源站数据质量**：同一章存在多次上传（例如 `第731章 圣威` 与 `第七百三十一章 圣威`），也出现过同名不同号的章节。合并策略：同章号优先保留有标题的版本；同章号不同标题的条目保留（源站错号）；番外按番外序号排序并保留最新上传。
- 源站偶发连接重置/超时，适配层设置了超时、有限重试与指数退避；成功结果在服务端（Worker Cache API / 内存）与客户端（IndexedDB）双重缓存，避免重复抓取。

**多书与搜索**：

- 所有书共用同一套模板：目录 `/book/{书号}/`、`/book/{书号}/{n}.html`，章节 `/book/{书号}_{id}.html`。目录第 1 页（详情页）的 `og:novel:*` 提供书名、作者、分类、状态、简介与首章；总页数取自第 2 页的分页下拉（≤100 章的书只有一页）。
- 部分书正文是标题后的裸 `<p>`，适配层有第三级回退；源站对部分章节设了会员门槛（“后续内容已被隐藏…”），阅读器只标记并提示，**不绕过**。
- 站内搜索为 `POST /search.html`（字段 `s`），源站约 5 秒限频，服务端在统一上游队列外再加 5 秒搜索闸门，结果缓存 30 分钟。
- 封面取 `/img/{书号}.jpg`；网页端直接加载（no-referrer），Android 壳经受限代理加载。

**69书吧（www.69shuba.com）**：

- 书号在应用内写作 `69shuba-{源站书号}`（如 `69shuba-51254`），与万书阁的纯数字书号互不冲突，书架/进度/书签/缓存照常按书号隔离。
- jieqi 模板、**GBK 编码**：详情页 `/book/{n}.htm`（og meta / `div.booknav2`）、目录页 `/book/{n}/`（单页列出全部章节，`div#catalog li[data-num]`，按 data-num 排序，目录顺序即阅读顺序）、章节页 `/txt/{n}/{章节号}`（`div.txtnav`，不分页；上一章/下一章来自 `bookinfo.preview_page/next_page`）。
- 搜索：`POST /modules/article/search.php`，表单 `searchkey=<GBK 百分号编码>&searchtype=all`；唯一结果时源站直接跳详情页，也能解析。封面在 `static.69shuba.com/files/article/image/{n/1000}/{n}/{n}s.jpg`。
- **该站整站前置 Cloudflare**。开发环境（云端机房 IP）访问任何页面都只拿到人机验证页，因此解析器是按已知模板写的，fixture 为合成样例（见 `test/fixtures/69shuba/README.md`），**尚未用真实页面验证**；封面图床可正常访问。遇到人机验证时接口返回 `code: "blocked"` 与中文提示，阅读器原样展示，**不尝试绕过**。是否可用取决于取书的网络（自托管服务器 / Worker / 手机）是否被源站放行。

**飘天文学（www.piaotia.com）**：

- 书号写作 `piaotia-{源站书号}`（如 `piaotia-7906`）。jieqi 模板、**GBK 编码**，正文是纯 HTML（无脚本加密、目前无人机验证），开发环境实测可直接访问。
- 详情页 `/bookinfo/{a}/{n}.html`、目录页 `/html/{a}/{n}/`（单页，`div.centent` 内按卷分组的 `ul > li > a`）、章节页 `/html/{a}/{n}/{章节号}.html`（`&nbsp;` 缩进 + `<br>`，不分页），其中 `{a} = ⌊n/1000⌋`。搜索为 `POST /modules/article/search.php`，表单 `searchtype=articlename&searchkey=<GBK 编码书名>`，唯一结果时跳详情页。
- 实测《万古神帝》（piaotia-7906）：4215 章；万书阁/爱下电子书里是占位文字的第 3578、3579、3582、3583、3595、3602 章在这里都有完整正文。

**自动换源补全占位章**：

- 采集站常把“本章节内容正在手打中…”这类占位文字当正文上传。阅读器识别到占位章（清洗后只剩“本章在来源中没有正文”）时，自动依次尝试：本机已有的同名书（整本下载/导入）→ 飘天文学 → 万书阁 → 69书吧（排除当前书源），取到正文就直接替换显示，章首一行说明来源；下一章是占位章时在预取阶段提前换源，翻页即显示。
- 实测各站章节号经常对不上（同一章“第3579章 回城”在别站是“第三千五百五十八章 回城”，“第3595章 谋未来”在飘天文学是“第三千六百六十四章”），所以按**章名**匹配，同名时按全书相对位置取最近的一章；书名必须一致、作者已知时必须一致。爱下电子书 4259 章对飘天文学目录逐章匹配，4213 章命中、无错配。
- 换源内容只用于展示，不写入缓存、不改原书目录/进度/书签；仍按“清理站点广告”设置清洗。
- 不同站点的整本 TXT 并不能互相补洞：爱下电子书的简体与繁体（`_tw`）压缩包是同一份内容的繁简转换（4259 章逐章段落数一致，6 个占位章完全相同），万书阁与它同源、占位章也相同。

**爱下电子书（ixdzs8.com，整本下载型）**：

- 书号写作 `ixdzs-{源站书号}`。书页 `/read/{n}/` 一次给出书籍信息与完整目录，搜索为 `GET /bsearch?q=`，都可正常访问。
- **章节页前置 JS 安全验证**（“正在验证浏览器”），阅读器不绕过，因此不逐章在线阅读，而是使用站方公开的“TXT下载”（`down7.ixdzs8.com/{n}.zip`）：在详情里点“下载整本”，本机解压后按书页目录切章，保存为离线书。章节号与站点一致，重新下载即更新，阅读进度和书签保留。
- 实测《万古神帝》（ixdzs-137695）：压缩包 14.3MB，下载+解压+切章+保存约 14 秒，4273 章与目录逐条对齐、无缺章。

**后端接口限制**（防止成为任意 URL 代理）：

- 只接受 `/api/chapter?book=<书号>&id=<1-12 位数字>`、`/api/toc?book=<书号>&from=&to=`（跨度 ≤ 6、页码 ≤ 999）、`/api/search?q=<1-30 字>&source=<wanshuge|69shuba|ixdzs|piaotia>`、`/api/health`；`book` 缺省为预置书（兼容旧客户端），书号须为 1–9 位数字或 `69shuba-`/`ixdzs-`/`piaotia-`+1–9 位数字（`ixdzs-` 书的 `/api/chapter` 直接拒绝，需整本下载）。
- URL 只能由固定 origin + 校验过的书号/章节号拼接（搜索只能是固定的 `/search.html` + `s=` 表单，或 `/modules/article/search.php` + `searchkey=&searchtype=all`（69书吧）/ `searchtype=articlename&searchkey=`（飘天文学）表单）；跟随重定向后再次校验 host 必须属于该书源（`wanshuge.org` / `www.69shuba.com` / `www.piaotia.com`）；校验 `content-type`、响应体大小上限；不透明地丢弃非正文内容（广告、跟踪脚本从不进入存储）。
- 未实现：登录、付费、验证码、Cloudflare 人机验证绕过的任何逻辑。

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
| 正文缓存、目录缓存、本地导入书、整本下载的书 | IndexedDB `reader-db` | 可随时清理并重新获取（本地导入书除外；整本下载的书需重新下载） |

浏览器存储有配额限制；设置页显示 `navigator.storage.estimate()` 的用量与配额。缓存写入失败不会影响当次阅读。

---

## Android APK（独立运行，无需后端）

```bash
bash tools/setup-android-sdk.sh   # 一次性：安装 SDK 命令行工具 + build-tools 34.0.0 + android-34 到 ~/android-sdk（需 JDK 17+）
npm run build:android          # 构建 Android 目标 Web 资源（dist-android/；build.sh 会自动调用）
bash android/build.sh 0.0.17   # 打包 APK（x86_64 用 SDK 里的 aapt/zipalign/apksigner；arm64 用 apt 的同名软件包）
# 产物：artifacts/wangu-reader-v0.0.17.apk（未设 ANDROID_KEYSTORE 时用 android/debug.keystore 调试签名）
bash tools/test-android-source.sh   # 书源白名单单测（无需 SDK）
```

APK 内置 Web 应用，页面固定在 `https://reader.local/` origin，由 WebView 拦截层直接从 assets 提供（无本地监听端口）；书源请求（目录/章节页、站内搜索 POST、封面图）由原生层按白名单代理获取，正文解析、缓存、阅读界面与网页版共用同一套代码。已下载章节可离线阅读，无需电脑或服务器。

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
- 69书吧解析器未经真实页面验证（开发环境被 Cloudflare 拦截），首次真实使用时若目录/正文为空，请保存对应页面替换 `test/fixtures/69shuba/` 中的样例后修正解析；原生 App（`android-native/`）尚未接入 69书吧与爱下电子书。
- 爱下电子书只能整本下载后阅读（站点章节页有安全验证）；连载中的书需手动“重新下载（更新）”获取新章节；该书源暂不显示封面（封面地址是哈希值，无法由书号拼出）。
- 真机功耗、帧率与续航未测量（当前环境无真机，未编造数据）；性能结论来自桌面 Chromium 与本地网络，移动端为视口模拟。
- 完整 E2E 在 8 种视口 + 6 类交互场景下 55/55 通过；最近修复（隐藏属性、行宽公式、TXT 打开行为）由 `tools/verify.sh` 聚焦回归全部覆盖。
- 跨设备进度同步未实现；多设备各自独立。
- TXT 导入的章节识别基于常见标题格式，复杂排版可能需要手动整理。
- 折叠屏展开/收起通过 Playwright 视口模拟验证，未在真实折叠屏上验证。
