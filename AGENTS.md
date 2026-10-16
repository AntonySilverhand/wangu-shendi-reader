# AGENTS.md

给在此仓库工作的编码代理的说明。**先读本文件再动手。**

## 项目是什么

万书阅读器：手机优先、干净、可离线的中文小说阅读网站 + Android APK。
书源是万书阁（wanshuge.org）与 69书吧（www.69shuba.com）：可在书源中搜索并阅读任意书，预置书为《万古神帝》（万书阁 book 36780）。
前端是原生 TypeScript（无框架）+ Vite；后端是一份同构代码，本地跑 Node、线上跑 Cloudflare Worker。
另有 Android WebView 壳（`android/`）与开发中的纯原生 App（`android-native/`，见其 AGENTS.md）。

## 常用命令

```bash
npm install
npm run dev              # Vite dev（内置 /api，默认 5173）
npm run build            # 网页版构建到 dist/
npm run build:android    # Android 目标构建到 dist-android/
bash tools/verify.sh     # 构建 + 起服务 + 聚焦回归（约 1 分钟，提交前必跑）
node test/e2e.mjs <url> 1   # E2E 第 1 段（29 项）
node test/e2e.mjs <url> 2   # E2E 第 2 段（29 项，本环境单命令限 2 分钟故分段）
node test/e2e.mjs <url> 3   # E2E 第 3 段（多书：搜索→详情→阅读→书架，API 由 fixture 模拟）
node test/e2e.mjs <url> 4   # E2E 第 4 段（69书吧：切换书源/粘贴网址/人机验证提示，API 由 fixture 模拟）
bash tools/test-android-source.sh   # Android 壳书源白名单 JVM 单测（无需 SDK）
node test/verify-delivery.mjs   # 交付验证（内容完整性/旧缓存补全/连续翻章/无轮询）
node test/check-layout.mjs      # 宽屏形态验证（1100/1500 断点）
npm test                 # 单元测试（vi test）
npm run typecheck        # tsc --noEmit，必须零错误
bash tools/reader-server.sh start|stop|restart|status 8787   # 本地生产预览
bash android/build.sh 0.0.18   # 本地打 APK（产物 artifacts/wangu-reader-v0.0.18.apk；ANDROID_KEYSTORE 指定签名密钥）
git tag vX.Y.Z && git push origin vX.Y.Z   # 发布：CI（.github/workflows/release-apk.yml）构建并建 Release
```

## 目录结构

```
src/shared/    同构：source.ts（万书阁适配 + 上游队列/字符集/人机验证识别）、source-69shuba.ts（69书吧适配）、
               sources.ts（按书号分派书源、封面、粘贴网址解析）、api.ts（/api 路由 + 缓存接口）
src/server/    Node：dev.ts（静态+API）、middleware.ts、file-cache.ts（磁盘缓存）、vite-plugin.ts
src/worker/    Cloudflare Worker 入口（Cache API）
src/web/       app.ts（编排 + 路由）cover.ts（封面）
               views/（reader/toc/settings/bookmarks/search=章内搜索/discover=书源搜索/home=书架）
               store/（settings/personal/library=书架/db/toc/chapter/download/txt/remote）
android/       WebView 壳：MainActivity + AssetClient（https://reader.local 资源拦截）
               + SourceProxy/SourceRules（受限书源代理：页面/搜索 POST/封面）+ build.sh
test/          单元测试、真实 HTML fixtures、e2e.mjs、验证脚本
tools/         reader-server.sh、verify.sh、run-e2e.sh、make-icons.py
```

## 必须遵守的约束（踩过的坑）

1. **Node 直接运行 TS**（`--experimental-strip-types`）：`src/shared`、`src/server` 中**禁止**参数属性（`constructor(private x: T)`）、enum、namespace。只用可擦除语法。
2. **Android 的 d8 对 JDK21 编译的内部类会崩溃**：`android/java` 下**禁止匿名类/内部类/嵌套类/lambda**，只写顶层类，兄弟类之间通过构造参数传引用。
3. **上游请求必须走 `source.ts` 的队列**（并发上限 2 + 最小间隔 160ms + 优先级）。章节等用户请求用默认 `high`，目录/预取传 `highPriority: false`。禁止绕过队列直接 fetch，也禁止改回全局串行——全局串行会造成队头阻塞（源站一慢，全线卡住）。
4. **缓存键改动必须升版本**（如 `chapter:v2:`）。服务端磁盘缓存（`.cache/api`）与客户端 IndexedDB 都可能残留旧数据；改缓存语义时同步升 `CHAPTER_CACHE_VERSION`，并让旧记录失效后**先显示缓存再后台补全**（不可阻塞阅读）。
5. **`complete` 语义**：只有 `ChapterResult.complete === true` 才算完整；客户端把 `undefined` 当不完整处理（触发补全）。章节内容永远"宁多勿少"——补全结果更短时不要覆盖。
6. **Service Worker 缓存必须按构建号隔离**（`reader-shell-<build>` / `reader-runtime-<build>`），激活时清理旧版本；否则旧 JS 会持续提供，造成"修了还在复现"。
7. **不要做的**：任意 URL 代理、绕过登录/付费/验证码/源站会员门槛/Cloudflare 人机验证、把整本书打包进仓库、把正文缓存和个人数据（书签/进度/设置/书架）混在一起。上游只允许由**校验过的书号/章节号**拼出的路径：万书阁的目录页与章节页、`POST /search.html`（表单仅 `s=`）与 `/img/{书号}.jpg`；69书吧的 `/book/{n}.htm`、`/book/{n}/`、`/txt/{n}/{id}`、`POST /modules/article/search.php`（表单仅 `searchkey=…&searchtype=all`）与 `static.69shuba.com` 封面。`source.ts`/`source-69shuba.ts` 与 `SourceRules.java` 保持同一白名单；原生 `UrlSafetyValidator` 目前只有万书阁（原生尚未接入 69书吧，接入时同步）。人机验证页一律报 `blocked`（不重试、不求解）。
8. **保持轻量**：不引入前端框架/网络字体；静态资源体积目标 < 150KB gzip。
9. **书号即 bookId，书号决定书源**：万书阁书 bookId = 源站数字书号（`/^[1-9]\d{0,8}$/`），69书吧书为 `69shuba-{数字}`，本地书为 `local-xxx`。判定统一用 `sources.ts` 的 `sourceOfBook`/`isRemoteBookId`，不要在别处手写正则。IndexedDB、个人数据、书架都按 bookId 关联；所有书源函数显式传 bookId，不得回退到预置书（36780 只在 API 缺省 `book` 参数时作为兼容默认）。新增书源：加一个适配文件 + 在 `sources.ts` 注册 + 新书号前缀，不要复用纯数字书号。
10. **切书时防串书**：异步回调/位置保存要核对 bookId（如 `ReaderView.currentBookId`），否则旧书的滚动保存会写进新书进度。

## 书源适配速查

- 所有书同一套模板。以下 `{b}` 为书号（如 36780、104429）。
- 目录：`/book/{b}/`（第 1 页 = 详情页，含 `og:novel:*` 书籍信息与 `og:novel:read_url` 首章）与 `/book/{b}/{n}.html`；总页数来自第 2 页的分页 `<select>`（≤100 章的书只有一个选项 → 1 页；超出末页会回环）。《万古神帝》44 页。每页含置顶"最新章节/新书+番外"块与页尾快捷链接，用 `classifyTocItems` 的章节号中位数过滤 + 尾部剔除，合并按 id（上传顺序）。
- 章节第 1 页 `/book/{b}_{id}.html`，后续分页 `/book/{b}/{id}_{n}.html`（n 从 1 起）。
- 正文是 `document.writeln(qsbs.bb('<base64>'))`，解码后为 `<p>`；无编码时回退 `#content`，再回退 `h2.chapter-title` 之后的裸 `<p>`。
- 源站会员门槛：“后续内容已被隐藏，请升级VIP会员后继续阅读。”→ 标记 `restricted`、提示用户，**不绕过**。
- 搜索：`POST /search.html`，表单 `s=<关键词>`（GET 无效）；桌面模板 `ul#novel-search li`（s1 分类/s2 书名/s3 作者/s4 最新章/s5 日期），移动 UA 模板为 `a.list`。源站约 5 秒限频（`search_time` cookie）→ `MIN_SEARCH_GAP_MS`。三端统一用桌面 UA。
- 封面：`/img/{b}.jpg`（无封面时返回默认图）。
- **同 chapterId 的分页链接 = 下一页；不同 chapterId = 下一章**。合并分页、按段落去重、保持顺序；后页失败时保留已取内容并标 `missingPages`（客户端提示"继续加载"）。
- 源站有重复上传与错号：同章号优先保留有标题版本，同号不同标题都保留；番外按序号归位、同标题保留最新 id。
- 源站偶发连接重置，属正常现象：靠重试 + 缓存消化，不要因此改成并发。

## 69书吧适配速查

- 书号 `69shuba-{n}`；页面 GBK 编码（`decodeHtml` 按 Content-Type/meta 解码；Android 壳原样转交字节与 charset）。
- 详情 `/book/{n}.htm`：og meta 优先，缺失时读 `div.booknav2`（h1 / 作者：authorarticle 链接 / 分类：）与 `div.navtxt`。
- 目录 `/book/{n}/`：单页全量，只取 `div#catalog` 内 `li[data-num] > a[href=/txt/{n}/{id}]`，按 data-num 升序（缺失时按文档顺序、倒序则翻转）。顺序以源站为准，`mergeBookToc` 只去重，**不套用万书阁的错号/重传启发式**；番外原位保留（extra=false）。API 只返回第 1 页、`totalPages: 1`。
- 章节 `/txt/{n}/{id}`：不分页；正文为 `div.txtnav` 内裸文本 + `<br>`，剔除 h1、`div.txtinfo`、`div#txtright`、`div.contentadv` 等与站点广告行、“(本章完)”，首行重复标题去掉；上一章/下一章取 `bookinfo.preview_page/next_page`（指向书页/目录即无），缺失时按按钮文字。识别到正文与导航才 `complete: true`。
- 搜索 `POST /modules/article/search.php`，`searchkey` 为 GBK 百分号编码（`encodeGbkComponent`，用运行时 GBK 解码器反推码表），结果 `div.newbox li`；唯一结果时跳详情页。另有 5 秒搜索闸门。
- **整站前置 Cloudflare**：云端开发环境拿到的都是挑战页，解析器与 fixture（`test/fixtures/69shuba/`，GBK 合成样例）尚未经真实页面验证；拿到真实页面后优先替换 fixture 再改解析。

## 验证要求（提交前）

- `npm run typecheck` 零错误、`npm test` 全绿。
- 改动阅读/缓存/位置逻辑：跑 `node test/verify-delivery.mjs`。
- 改动布局/主题/交互：跑 E2E 两段（`1` 和 `2`）或至少 `tools/verify.sh`。
- 改动 Android 壳：跑 `bash tools/test-android-source.sh`，并确认 `bash android/build.sh <ver>` 成功、`aapt dump badging` 的 versionCode/Name 正确。
- 提交信息写清版本与修复内容；发布：先改 `android/AndroidManifest.xml` 的 versionCode/Name，写 `docs/releases/vX.Y.Z.md`（首行为标题），再推送 `vX.Y.Z` 标签，由 CI（启用后）用仓库 secret `ANDROID_KEYSTORE_B64` 中的固定密钥签名并发布。**不要换签名密钥**（换了用户就得卸载重装）。

## 数据与存储

| 数据 | 位置 | 说明 |
|---|---|---|
| 设置/进度/书签/历史 | localStorage `reader.settings.v1` / `reader.personal.v1` | 清理正文缓存不影响；可导出/导入 |
| 书架（远程书元数据、当前书） | localStorage `reader.library.v1` | 随个人数据导出；预置书只加入一次 |
| 正文/目录/本地书 | IndexedDB `reader-db` (chapters/toc) | 可重新获取（本地导入书除外） |
| 服务端缓存 | `.cache/api`（Node）/ Cache API（Worker） | 已 gitignore |

## 设计基线（改动时保持）

中文阅读默认：字号 18px、行距 1.8、段间距 0.7em、页边距 20px、行宽约 36 字；
阅读位置 = 段落序号 + 段落内字符偏移；滚动中不做逐帧 Range 取样，滚动停止后才精确取样；
静止时零轮询、零动画循环；预取仅下一章且尊重 `saveData`。
