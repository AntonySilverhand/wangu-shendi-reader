# AGENTS.md

给在此仓库工作的编码代理的说明。**先读本文件再动手。**

## 项目是什么

《万古神帝》个人阅读器：手机优先、干净、可离线的中文小说阅读网站 + Android APK。
书源是万书阁（wanshuge.org，book 36780）。前端是原生 TypeScript（无框架）+ Vite；
后端是一份同构代码，本地跑 Node、线上跑 Cloudflare Worker。另有 Android WebView 壳。

## 常用命令

```bash
npm install
npm run dev              # Vite dev（内置 /api，默认 5173）
npm run build            # 网页版构建到 dist/
npm run build:android    # Android 目标构建到 dist-android/
bash tools/verify.sh     # 构建 + 起服务 + 聚焦回归（约 1 分钟，提交前必跑）
node test/e2e.mjs <url> 1   # E2E 第 1 段（29 项）
node test/e2e.mjs <url> 2   # E2E 第 2 段（29 项，本环境单命令限 2 分钟故分段）
node test/verify-delivery.mjs   # 交付验证（内容完整性/旧缓存补全/连续翻章/无轮询）
node test/check-layout.mjs      # 宽屏形态验证（1100/1500 断点）
npm test                 # 单元测试（vi test）
npm run typecheck        # tsc --noEmit，必须零错误
bash tools/reader-server.sh start|stop|restart|status 8787   # 本地生产预览
bash android/build.sh 0.0.9    # 打 APK（产物 artifacts/wangu-reader-v0.0.9.apk）
gh release create vX.Y.Z artifacts/*.apk ...   # 发布
```

## 目录结构

```
src/shared/    同构：source.ts（书源适配/解析）、api.ts（/api 路由 + 缓存接口）
src/server/    Node：dev.ts（静态+API）、middleware.ts、file-cache.ts（磁盘缓存）、vite-plugin.ts
src/worker/    Cloudflare Worker 入口（Cache API）
src/web/       app.ts（编排）views/（reader/toc/settings/bookmarks/search/home）
               store/（settings/personal/db/toc/chapter/download/txt/remote）
android/       原生壳：MainActivity + LocalServer（本地资源服务 + 书源代理）+ build.sh
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
7. **不要做的**：任意 URL 代理、绕过登录/付费/验证码、把整本书打包进仓库、把正文缓存和个人数据（书签/进度/设置）混在一起。
8. **保持轻量**：不引入前端框架/网络字体；静态资源体积目标 < 150KB gzip。

## 书源适配速查

- 目录：`/book/36780/`（第 1 页）与 `/book/36780/{n}.html`，共 44 页；每页含置顶"新书+番外"块与页尾快捷链接，用 `classifyTocItems` 的章节号中位数过滤 + 尾部剔除。
- 章节第 1 页 `/book/36780_{id}.html`，后续分页 `/book/36780/{id}_{n}.html`（n 从 1 起）。
- 正文是 `document.writeln(qsbs.bb('<base64>'))`，解码后为 `<p>`；无编码时回退 `#content`。
- **同 chapterId 的分页链接 = 下一页；不同 chapterId = 下一章**。合并分页、按段落去重、保持顺序；后页失败时保留已取内容并标 `missingPages`（客户端提示"继续加载"）。
- 源站有重复上传与错号：同章号优先保留有标题版本，同号不同标题都保留；番外按序号归位、同标题保留最新 id。
- 源站偶发连接重置，属正常现象：靠重试 + 缓存消化，不要因此改成并发。

## 验证要求（提交前）

- `npm run typecheck` 零错误、`npm test` 全绿。
- 改动阅读/缓存/位置逻辑：跑 `node test/verify-delivery.mjs`。
- 改动布局/主题/交互：跑 E2E 两段（`1` 和 `2`）或至少 `tools/verify.sh`。
- 改动 Android 壳：确认 `bash android/build.sh <ver>` 成功，且 `aapt dump badging` 的 versionCode/Name 正确。
- 提交信息写清版本与修复内容；发布用 `gh release create`，APK 作为 asset。

## 数据与存储

| 数据 | 位置 | 说明 |
|---|---|---|
| 设置/进度/书签/历史 | localStorage `reader.settings.v1` / `reader.personal.v1` | 清理正文缓存不影响；可导出/导入 |
| 正文/目录/本地书 | IndexedDB `reader-db` (chapters/toc) | 可重新获取（本地导入书除外） |
| 服务端缓存 | `.cache/api`（Node）/ Cache API（Worker） | 已 gitignore |

## 设计基线（改动时保持）

中文阅读默认：字号 18px、行距 1.8、段间距 0.7em、页边距 20px、行宽约 36 字；
阅读位置 = 段落序号 + 段落内字符偏移；滚动中不做逐帧 Range 取样，滚动停止后才精确取样；
静止时零轮询、零动画循环；预取仅下一章且尊重 `saveData`。
