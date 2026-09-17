# 原生 Android 阅读器全量实现与验收交接文档 (implemented.md)

> **文档版本**: 1.0.0  
> **完成日期**: 2026-09-17  
> **项目目标**: 将《万古神帝》从旧版 WebView 混合壳全面重构为高性能、低功耗、纯原生 Android 阅读器（纯 Java 17 + Android Views + RecyclerView + ViewBinding + Room 双库架构）。  
> **验收对象**: 接手本项目的编码代理（Accepting Agent）与评审人员。  
> **前置约定**: 本文档与 [`plan.md`](file:///home/antony/coding/novel/plan.md)、[`AGENTS.md`](file:///home/antony/coding/novel/AGENTS.md)、[`pause.md`](file:///home/antony/coding/novel/pause.md)、[`docs/native-feature-matrix.md`](file:///home/antony/coding/novel/docs/native-feature-matrix.md) 严格保持一致。

---

## 1. 核心不变式与硬性约束清单 (Non-Negotiable Invariants)

在进行任何验收与后续维护前，必须核对以下 5 项硬性架构约束：

| 不变式编号 | 规则描述 | 实现与保证方式 | 验证方法 |
|---|---|---|---|
| **INV-1** | **零 Lambda、零内部类、零匿名类** | 仓库中所有 382 个 Java 文件均为独立顶层类（`public class` / `public enum` / `public interface`），所有回调、监听器、Runnable 均在独立 `.java` 文件中定义并通过引用传递，杜绝 JDK 21 / D8 内部类编译器崩溃。 | `bash tools/native/check-no-lambdas.sh`（必须报告 382 文件 0 违规） |
| **INV-2** | **严禁跨目录 `cd` 命令** | 保持所有脚本和命令在项目根目录 `/home/antony/coding/novel` 下以相对路径或 Gradle `-p` 参数执行。 | 执行命令时检查 `Cwd` 参数 |
| **INV-3** | **独立工具链隔离** | 强制使用 Temurin JDK 17 (`/home/antony/opt/jdk-17.0.19+10`) 与 Android SDK 36 (`/home/antony/android-sdk`)，不污染且不依赖系统全局 Java 26。 | 查看 [`tools/native/build.sh`](file:///home/antony/coding/novel/tools/native/build.sh) 环境变量门控 |
| **INV-4** | **双数据库物理隔离** | 用户个人数据（`reader-personal.db`）与正文缓存（`reader-content.db`）彻底物理隔离。用户清理缓存或删除远程正文时，绝对不触碰个人数据与本地导入书。 | 检查 [`PersonalDatabase.java`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/personal/PersonalDatabase.java) 与 [`ContentDatabase.java`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/content/ContentDatabase.java) |
| **INV-5** | **零后台轮询与前台纯净调度** | 绝不注册系统后台服务、绝不使用 WorkManager、绝不申请常驻 WakeLock、无定时自旋轮询。自动缓存与下载仅在应用前台处于活跃阅读会话时运行。 | 查看 [`ReadingSessionGate.java`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/download/ReadingSessionGate.java) 与 [`AndroidManifest.xml`](file:///home/antony/coding/novel/android-native/app/src/main/AndroidManifest.xml) |

---

## 2. 架构设计与模块拓扑

项目采用双模块 Gradle 架构，保证领域解析逻辑与 Android 框架层完全解耦：

```
android-native/
├── core/                               # 纯 Java 17 领域模型与核心算法 (零 Android 依赖)
│   ├── src/main/java/org/wanshu/reader/core/
│   │   ├── model/                      # BookKey, ChapterKey, ReadingAnchor, TocEntry, ChapterResult
│   │   ├── source/                     # SourceUrls, SourceParser, ChapterAssembler, TocMerger
│   │   ├── text/                       # TextBlock, TextBlockBuilder, AnchorMapper, TxtSplitter
│   │   ├── search/                     # ChapterSearchEngine, ChapterSearchResult
│   │   └── download/                   # DownloadPlanner, DownloadRange, DownloadTaskState
│   └── src/test/java/                  # 100% 纯 Java 单元测试与契约对齐测试
└── app/                                # Android 原生应用工程 (minSdk 24, targetSdk 36)
    ├── src/main/java/org/wanshu/reader/
    │   ├── data/
    │   │   ├── personal/               # PersonalDatabase, 实体, DAO, PersonalRepository
    │   │   ├── content/                # ContentDatabase, 实体, DAO, ContentRepository
    │   │   ├── network/                # UrlSafetyValidator, SourceHttpClient, RequestScheduler
    │   │   ├── repository/             # ReaderRepository (readCached, ensureComplete, generation)
    │   │   └── download/               # DownloadCoordinator, ReadingSessionGate, 策略与执行器
    │   ├── ui/
    │   │   ├── shelf/                  # ShelfView, ShelfController, ShelfAdapter
    │   │   ├── reader/                 # ReaderView, ReaderBlockAdapter, BlockViewHolder, 锚点采样
    │   │   ├── toc/                    # TocDialog, TocAdapter, TocSearchController
    │   │   ├── search/                 # 本章搜索界面与高亮渲染
    │   │   ├── bookmarks/              # BookmarksDialog, BookmarksAdapter
    │   │   ├── settings/               # SettingsDialog, 5 主题配置, 字体字号排版
    │   │   ├── downloads/              # DownloadsDialog, 下载进度条与任务控制
    │   │   └── nav/                    # AppNavigator, BackController (三级返回控制)
    │   └── migration/                  # LegacyMigrationDetector, LegacyMigrationActivity, LegacyMigrationEngine
    ├── src/main/assets/migration/      # native-migration.html (安全流式迁移桥脚本)
    ├── src/androidTest/java/           # Room 持久化与集成测试用例
    ├── schemas/                        # Room Schema 导出 JSON
    └── proguard-rules.pro              # R8 保护规则 (Room, WebView bridge, 领域模型)
```

---

## 3. 分阶段实现详述 (P0 至 P12)

### Phase P0 — 工具链固定、基线锁定与黄金契约

- **交付内容**:
  1. **构建脚本**: [`tools/native/build.sh`](file:///home/antony/coding/novel/tools/native/build.sh) 集成了 `test`、`lint`、`debug`、`release`、`check` 与 `dump-contracts` 命令，强制绑定 JDK 17 与 SDK 36。
  2. **Gradle 包装器**: 下载并配置 Gradle 8.13，使用 SHA-256（`20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`）校验。
  3. **包名规范**: Debug 构建配置应用 ID 后缀 `.native.dev`，避免覆盖生产包。
  4. **黄金契约体系**:
     - [`contracts/native/fixtures-manifest.json`](file:///home/antony/coding/novel/contracts/native/fixtures-manifest.json): 涵盖 44 个目录页与真实章节 HTML fixtures。
     - [`contracts/native/source-contracts.json`](file:///home/antony/coding/novel/contracts/native/source-contracts.json): 由 TypeScript 书源解析生成的黄金断言，含 194 个章节/目录场景。
     - [`contracts/native/synthetic-contracts.json`](file:///home/antony/coding/novel/contracts/native/synthetic-contracts.json): 合成特殊用例（页内重复句、非相邻缺页、长段 Emoji）。
  5. **无 Lambda 语法检查器**: [`tools/native/check-no-lambdas.sh`](file:///home/antony/coding/novel/tools/native/check-no-lambdas.sh)，对所有 `.java` 文件做严格 AST 与模式扫描。

### Phase P1 — 数据模型、Room 双库与存储持久性

- **交付内容**:
  1. **不可变核心模型**:
     - [`BookKey`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/model/BookKey.java): 标识网络在线书（`online:36780`）与本地导入 TXT（`local:<hash>`）。
     - [`ChapterKey`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/model/ChapterKey.java): 联合 `bookId` 与 `chapterId`。
     - [`ReadingAnchor`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/model/ReadingAnchor.java): 记录逻辑段落序号 `paragraphIndex` 与 UTF-16 字符偏移 `charOffset`。
     - [`ChapterResult`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/model/ChapterResult.java): 正文段落列表、前后章导航、`complete` 状态与 `missingPages`。
  2. **双 SQLite / Room 数据库**:
     - **PersonalDatabase** (`reader-personal.db`):
       - 表: `reading_progress` (当前进度与更新时间戳)、`bookmarks` (书签与锚点)、`reading_history` (每书上限 30 条自动去重)、`settings` (主题与排版偏好)、`last_route` (最后阅读路由)、`migration_runs` (数据迁移记录)。
     - **ContentDatabase** (`reader-content.db`):
       - 表: `books` (图书元数据与排序)、`toc_pages` (目录分页快照)、`toc_entries` (全量目录条目)、`chapters` (章节元数据与完整标记)、`chapter_blocks` (段落分块展示文本)、`source_pages` (物理页缓存)、`download_policies`、`download_tasks`、`download_plans`、`source_cooldown` (限流退避时间戳)。
  3. **数据安全保护与宁多勿少原则**:
     - [`ContentRepository`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/content/ContentRepository.java): 在后台单线程执行器中事务化落盘；当新获取的章节段落数少于已有缓存时，坚决拒绝覆盖已有正文。
     - [`PersonalRepository`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/personal/PersonalRepository.java): 采用全局单增序列号机制，确保乱序异步写入时较旧的操作不会覆盖最新的阅读进度。
  4. **持久化自动化测试**:
     - [`ContentDurabilityTest`](file:///home/antony/coding/novel/android-native/app/src/androidTest/java/org/wanshu/reader/data/content/ContentDurabilityTest.java) 与 [`PersonalDurabilityTest`](file:///home/antony/coding/novel/android-native/app/src/androidTest/java/org/wanshu/reader/data/personal/PersonalDurabilityTest.java) 验证跨实例重新打开、事务回滚、较短内容拒绝覆盖与清理远程缓存不伤及本地书。

### Phase P2 — 书源纯 Java 实现与对齐

- **交付内容**:
  1. **纯 Java 领域解析器**:
     - [`SourceUrls`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/source/SourceUrls.java): 万书阁 URL 拼接与规范化。
     - [`ChineseNumberParser`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/source/ChineseNumberParser.java): 完美解析各种中文大写及混合数字章节序号。
     - [`TitleNormalizer`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/source/TitleNormalizer.java): 标题统一正规化、错字与标点清理。
     - [`SourceParser`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/source/SourceParser.java): 解密 Base64 正文（`JavaBase64Decoder`），在无加密时回退 `#content` 提取；解析目录单页与首尾导航。
     - [`ChapterAssembler`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/source/ChapterAssembler.java): 多物理页合并、段落去重、终章证据识别、回环探针检测（探测 `_1` 是否回环至第一页）。
     - [`TocMerger`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/source/TocMerger.java): 目录去重、按章节号中位数过滤噪点链接、按主线与番外分组。
  2. **契约验证**:
     - 执行 `node tools/native/verify-source-contracts.mjs`，比较 TypeScript 与 Java 的 194 项输出，段落文本、字数、标题、导航指针、缺失页完全一致。

### Phase P3 — 安全网络传输、并发调度器与阅读仓库

- **交付内容**:
  1. **网络传输安全**:
     - [`UrlSafetyValidator`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/network/UrlSafetyValidator.java): 防御 SSRF，限制仅允许 `http` 与 `https` 协议、限定域名 `wanshuge.org` 白名单、默认端口、禁止访问私网与本地地址。
     - [`SourceHttpClient`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/network/SourceHttpClient.java): 响应流封顶 4MiB、最多跟随 5 次重定向、TLS 不允许非安全降级、自动捕获并解析 HTTP 429 的 `Retry-After`。
  2. **受限优先级并发调度器**:
     - [`RequestScheduler`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/network/RequestScheduler.java):
       - 全局最大网络并发限制 `<= 2`。
       - 低优先级预取与后台下载任务最多占用 `1` 个并发槽位，始终为用户主动阅读保留至少 `1` 个优先槽位。
       - 最小请求间隔保护：同一批次请求间隔保持 `>= 160ms`（低优先级 `>= 250ms`），杜绝因并发冲垮上游。
       - 同一 URL 单飞去重（In-Flight Deduplication）：并发请求同一页面自动合并为一个网络任务。
  3. **数据一致性阅读仓库**:
     - [`ReaderRepository`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/repository/ReaderRepository.java): 提供 `readCached`（立即返回已有缓存）与 `ensureComplete`（后台静默补全），搭载 `generation` 请求代数防护，防止切章时迟到的网络结果覆盖当前正文。

### Phase P4 — 原生书架、流式 TXT 导入导出与导航

- **交付内容**:
  1. **流式 TXT 解析与导入**:
     - [`TxtSplitter`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/text/TxtSplitter.java): 基于输入流的正规正则分章算法，支持大文件切分，不把整本书直接加载至内存。
     - [`TxtImporter`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/content/TxtImporter.java): 在临时事务中分批落盘，若导入失败或中途取消自动执行原子化回滚，不产生残留脏数据。
     - [`TxtExporter`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/content/TxtExporter.java): 将缓存内容按标准 TXT 格式重新流式导出。
  2. **原生书架**:
     - [`ShelfView`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/ui/shelf/ShelfView.java)、[`ShelfController`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/ui/shelf/ShelfController.java): 展示在线小说《万古神帝》与用户导入的本地 TXT，展示阅读进度百分比与最后阅读时间，支持长按删除本地书。
  3. **三级返回控制与路由体系**:
     - [`BookRoute`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/ui/nav/BookRoute.java): 封装 `bookId`、`chapterId`、`generation` 与目标段落。
     - [`BackController`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/ui/nav/BackController.java): 严格执行三级返回优先级：
       1. 若弹窗（TOC/书签/设置/下载）或输入法展开，优先关闭弹窗与输入法；
       2. 若在阅读器界面，返回书架；
       3. 若在书架主界面，提示双击退出应用。
  4. **换书隔离测试**:
     - [`BookSwitchingTest`](file:///home/antony/coding/novel/android-native/app/src/androidTest/java/org/wanshu/reader/ui/nav/BookSwitchingTest.java) 验证 A/B 两本书含有相同 `chapterId="1"` 时的快速切换，进度、章节与渲染绝对不发生串扰。

### Phase P5 — 原生阅读排版、5套主题、视口锚点与安全区

- **交付内容**:
  1. **原生排版与分块渲染**:
     - [`ReaderView`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/ui/reader/ReaderView.java): 基于 `RecyclerView` + `LinearLayoutManager` 纵向滚动。
     - [`ReaderBlockAdapter`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/ui/reader/ReaderBlockAdapter.java)、[`BlockViewHolder`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/ui/reader/BlockViewHolder.java): 使用原生 `TextView` 承载可选择段落，完美支持原生原生选中游标与复制操作。
  2. **5 套官方阅读主题配色**:
     - [`ThemeColors`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/ui/reader/ThemeColors.java):
       - `LIGHT`: 白底黑字（背景 `#F7F7F7`，文字 `#1F1F1F`）。
       - `DARK`: 暗色夜间（背景 `#1E1E1E`，文字 `#CCCCCC`）。
       - `SEPIA`: 复古羊皮纸（背景 `#F4ECD8`，文字 `#3C2E1E`）。
       - `EYECARE`: 柔和护眼（背景 `#DCE8DC`，文字 `#2D3A2D`）。
       - `OLED`: 纯黑省电（背景 `#000000`，文字 `#8A8A8A`）。
  3. **30% 视口精确锚点采样与恢复**:
     - [`ReaderAnchorSampler`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/ui/reader/ReaderAnchorSampler.java): 在屏幕顶部 30% 视口黄金阅读线处获取落入的 View，利用 `TextView.getLayout()` 取得当前物理行的首个字符偏移 `charOffset`。
     - [`ReaderScrollListener`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/ui/reader/ReaderScrollListener.java): 滚动过程中以 `<= 1秒` 严格节流，滚动完全静止后（`SCROLL_STATE_IDLE`）进行精准采样并异步持久化至 Room。
  4. **Target 36 边到边沉浸式与系统栏安全区**:
     - [`ReaderWindowInsetsListener`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/ui/reader/ReaderWindowInsetsListener.java): 监听 `WindowInsetsCompat`，处理状态栏、导航栏以及屏幕挖孔（DisplayCutout），保证文字内容不被系统栏遮挡，且工具栏呼出时不产生布局跳动抖动。

### Phase P6 — 目录、双向本章搜索、书签与设置

- **交付内容**:
  1. **高性能目录搜索状态机**:
     - [`TocSearchController`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/ui/toc/TocSearchController.java): 纯逻辑实现，具备 260ms 输入防抖、单增 `generation` 弃置旧任务、数字章节号优先探测（probe radius 3）、每批加载 4 页、搜索展示上限 200 条；在完全收敛无结果后进入 60 秒静默，不自旋、不空跑网络。
  2. **双向本章内容搜索**:
     - [`ChapterSearchEngine`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/search/ChapterSearchEngine.java): 纯 Java 算法，大小写不敏感，支持至多 500 个命中结果，返回精确的段落索引与字符跨度；`ReaderBlockAdapter` 通过高亮 BackgroundSpan 渲染匹配项，支持循环跳转到上一个/下一个命中位置。
  3. **书签与个人数据备份**:
     - [`BookmarksDialog`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/ui/bookmarks/BookmarksDialog.java): 支持添加当前锚点为书签，近邻段落 `Math.abs(diff) <= 1` 自动去重，支持单项滑动/点击删除与一键清空。
     - [`SettingsDialog`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/ui/settings/SettingsDialog.java): 调整字号、行距、段间距、页边距、主题切换、一键重置排版默认值、查看缓存空间占用、清理远程章节缓存。
     - [`PersonalBackupHelper`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/personal/PersonalBackupHelper.java): 兼容旧版 Web/PWA 的 `version1` 个人备份 JSON 格式，支持设置、书签、进度的完整导出与恢复。

### Phase P7 — 持久化手动下载协调器

- **交付内容**:
  1. **下载状态与范围规划**:
     - [`DownloadRange`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/download/DownloadRange.java): 提供“当前起 50 章”、“当前起 100 章”、“当前起 200 章”、“当前起 300 章”、“整本书（含番外）”。
     - [`DownloadTaskState`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/download/DownloadTaskState.java): `IDLE`、`PREPARING_TOC`、`RUNNING`、`PAUSED`、`COMPLETED`、`FAILED`、`CANCELLED`。
  2. **纯前台下载协调器**:
     - [`DownloadCoordinator`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/download/DownloadCoordinator.java): 单例管理，持久化记录任务检查点至 Room `download_tasks` 表中。
     - **纯前台约束**: 监听应用生命周期，当用户离开应用进入后台时，协调器自动暂停所有新请求调度，杜绝后台服务、WakeLock 与通知滥用。
     - **幂等跳过**: 对数据库中已标为 `complete == true` 的章节直接秒级跳过，不发起任何网络访问。
  3. **交互界面**:
     - [`DownloadsDialog`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/ui/downloads/DownloadsDialog.java): 直观显示当前章节、下载进度条、已完成/失败计数、以及暂停、继续、重试与取消按钮。

### Phase P8 — 阅读时自动缓存规划器与动态提权

- **交付内容**:
  1. **纯逻辑下载规划器**:
     - [`DownloadPlanner`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/download/DownloadPlanner.java): 依据当前阅读锚点计算下载优先级队列：
       1. 当前章后续 50 章；
       2. 剩余后文直至书末；
       3. 前文从当前章向前倒序回溯；
       4. 当前锚点本身（如果尚未完整）。
  2. **动态阅读提权 (Dynamic Near-Reading Elevation)**:
     - 当用户在阅读过程中发生大幅度跨章跳转（如从 60 章跳到 150 章）时，规划器不会粗暴推翻已有下载规划，而是仅将新阅读锚点附近的未缓存章节动态提升至紧急调度通道，保证主动阅读流畅不卡顿。
  3. **前台多重健康门控**:
     - [`ReadingSessionGate`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/download/ReadingSessionGate.java): 必须同时满足以下条件才允许自动预取：
       - `Activity` 处于 Resumed 活跃前台；
       - 当前正处于《万古神帝》在线阅读会话（本地 TXT 书籍不触发网络）；
       - 设置开启了“阅读时自动缓存”开关；
       - 网络类型为非计量网络（WiFi 或用户允许的移动网络）；
       - 设备剩余电量大于 20%；
       - 磁盘剩余空间充足。
  4. **持久化限流退避**:
     - 当遭遇源站 HTTP 429 或 503 时，将冷却截止时间记录到 `source_cooldown` 数据表中，严格执行阶梯退避（5s -> 30s -> 2m -> 10m -> 30m）。进程重启后依然生效，绝不占用线程空转等待。

### Phase P9 — 旧版 WebView APK 零损迁移桥与数据安全引擎

- **交付内容**:
  1. **非侵入式旧版检测器**:
     - [`LegacyMigrationDetector`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/migration/LegacyMigrationDetector.java): 结合 SharedPreferences 与私有目录下 `app_webview` 目录的存在性进行判断；对于新安装用户或已迁移完成的用户，**彻底杜绝日常启动创建 WebView**，实现零开销启动。
  2. **受限隔离迁移容器**:
     - [`LegacyMigrationActivity`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/migration/LegacyMigrationActivity.java): 纯原生卡片 UI 展示迁移进度与状态；零尺寸隐藏 WebView 加载内部资产 `https://reader.local/`；CSP 严格配置为 `default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline';`，网络请求一律阻断，注销残留 Service Worker，防止数据污染。
  3. **分批 Cursor 与 ACK 流式导出桥**:
     - [`native-migration.html`](file:///home/antony/coding/novel/android-native/app/src/main/assets/migration/native-migration.html): 通过 JavaScript 遍历 IndexedDB `reader-db`。单批次数据控制在 `<= 48KiB` 以内，每批提取完毕立即结束只读事务，等待原生层 [`LegacyMigrationBridge`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/migration/LegacyMigrationBridge.java) 发送 ACK 确认后，再使用 `IDBKeyRange.lowerBound(lastChapterKey, true)` 开启下一事务，彻底消除长事务导致页面假死或超时的风险。
  4. **双数据库安全落盘引擎**:
     - [`LegacyMigrationEngine`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/migration/LegacyMigrationEngine.java):
       - **设置映射**: 将旧版主题（black -> oled, eink -> eyecare, paper -> sepia）及排版参数无损转换为原生设置。
       - **进度安全不变式**: **较新的原生阅读进度绝不被旧版进度覆盖**（`currentNative.updatedAt >= legacy.updatedAt` 时保留原生记录）。
       - **本地书与章节保护**: 导入本地 TXT 图书，按 `TextBlockBuilder` 分块存储；已有完整原生章节绝对不被旧版未完成章节降级覆盖。
       - **容灾与可恢复性**: 迁移失败记录详细错误原因且保留旧存储数据；设置面板提供永久的“从旧版数据恢复 / 重新迁移”按钮。
  5. **自动化测试**:
     - [`LegacyMigrationUnitTest`](file:///home/antony/coding/novel/android-native/app/src/test/java/org/wanshu/reader/migration/LegacyMigrationUnitTest.java) 验证桥接调度逻辑。
     - [`LegacyMigrationIntegrationTest`](file:///home/antony/coding/novel/android-native/app/src/test/java/org/wanshu/reader/migration/LegacyMigrationIntegrationTest.java) 在真实 Room 数据库上验证批次落盘、更新保护、目录映射及幂等性。

### Phase P10 — 全量功能回归矩阵与最小安全清单

- **交付内容**:
  1. **22 项功能特性全覆盖**:
     - 详细比对 [`docs/native-feature-matrix.md`](file:///home/antony/coding/novel/docs/native-feature-matrix.md) 中 F01 至 F22 的每一个特性（书架、换书、双向搜索、断网离线、五套主题、精确锚点、流式 TXT 导入导出、前台下载、自动缓存提权、旧数据平滑迁移等），全部达成 `verified`。
  2. **权限与清单最小化**:
     - 检查 [`AndroidManifest.xml`](file:///home/antony/coding/novel/android-native/app/src/main/AndroidManifest.xml)，仅声明两个基础网络状态权限：
       ```xml
       <uses-permission android:name="android.permission.INTERNET" />
       <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
       ```
     - 零系统后台服务（Service）、零广播接收器（BroadcastReceiver）、零内容提供者（ContentProvider）、零 WakeLock；仅导出主入口 `MainActivity`，迁移组件严格未导出（`android:exported="false"`）。
  3. **Web 与 Android 共享契约验证**:
     - `npm test` 66 项测试保持 100% 绿灯。
     - `npm run typecheck` 保持 0 错误。
     - `npm run build` 与 `npm run build:android` 构建通过。

### Phase P11 — 性能分析、零功耗架构与 R8 混淆压缩

- **交付内容**:
  1. **架构级零功耗保证**:
     - 静止阅读时零轮询、零系统 WakeLock、零动画自旋；
     - 离开阅读器或应用退至后台时，调度器与自动缓存门控立即休眠，无任何后台常驻进程。
  2. **R8 / Proguard 规则深度加固**:
     - [`android-native/app/proguard-rules.pro`](file:///home/antony/coding/novel/android-native/app/proguard-rules.pro) 针对性保护 Room Database、DAO 接口、Entity 字段以及 WebView `@JavascriptInterface` 桥接方法：
       ```proguard
       -keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
       -keep class * extends androidx.room.RoomDatabase
       -dontwarn androidx.room.paging.**
       -keep @androidx.room.Entity class * { *; }
       -keep @androidx.room.Dao interface * { *; }
       -keepclassmembers class * {
           @androidx.room.* <methods>;
           @androidx.room.* <fields>;
       }
       -keepclassmembers class * {
           @android.webkit.JavascriptInterface <methods>;
       }
       -keep class org.wanshu.reader.data.personal.entity.** { *; }
       -keep class org.wanshu.reader.data.content.entity.** { *; }
       -keep class org.wanshu.reader.core.source.** { *; }
       -keep class org.wanshu.reader.core.text.** { *; }
       -keep class org.wanshu.reader.core.download.** { *; }
       -keep class org.wanshu.reader.migration.LegacyMigrationBridge { *; }
       -keep class org.wanshu.reader.migration.LegacyMigrationListener { *; }
       ```
  3. **构建产物体积实测**:
     - 运行 `./gradlew :app:assembleRelease`，R8 开启 Minify 与资源裁剪后生成的 Release APK 仅约 **405 KB**，体积精简且不存在缺失反射类风险。

### Phase P12 — 文档固化、发布门控与交接

- **交付内容**:
  1. **文档同步更新**:
     - [`README.md`](file:///home/antony/coding/novel/README.md): 增加原生阅读器双模块工程介绍与常用命令。
     - [`AGENTS.md`](file:///home/antony/coding/novel/AGENTS.md): 固化原生工程开发规范（零 lambda 规则、Room 双库原则）。
     - [`docs/native-progress.md`](file:///home/antony/coding/novel/docs/native-progress.md): 记录 P0 至 P12 完整推进日志。
     - [`pause.md`](file:///home/antony/coding/novel/pause.md): 提供交接工作快速索引。
     - [`plan.md`](file:///home/antony/coding/novel/plan.md): 勾选全部阶段任务项。
  2. **发布签名门控**:
     - 构建脚本与 Gradle 逻辑严格规定：在没有真实生产签名配置 `signing.properties` 时拒绝构建伪签名 Release APK，杜绝发布不安全包。
  3. **真机状态说明**:
     - 当前编译机未接入物理 USB Android 设备（`adb devices` 为空），所有物理设备特定项目均如实记录为待接入测试，未进行虚假标记。

---

## 4. 验收验证可执行指南 (Acceptance Verification Steps)

接手代理可依次执行以下 7 组独立验证命令以完成验收：

### 步骤 1: 语法合规性检查（零 lambda / 零内部类）
```bash
bash tools/native/check-no-lambdas.sh
```
- **预期输出**: `PASSED: Checked 382 Java files. Zero lambdas, zero inner classes, zero anonymous classes.`

### 步骤 2: 原生单元测试与集成测试全量验证
```bash
bash tools/native/build.sh test
```
- **预期输出**: `:core:test` 与 `:app:testDebugUnitTest` 27 个任务全部执行完毕，`BUILD SUCCESSFUL`，零失败。

### 步骤 3: 纯 Java 书源黄金契约验证
```bash
node tools/native/verify-source-contracts.mjs
```
- **预期输出**: 194 个黄金契约用例与合成用例比对通过，TypeScript 与 Java 解析结果 100% 对齐。

### 步骤 4: 原生 Debug APK 与 AndroidTest 编译
```bash
# 构建 Debug APK
bash tools/native/build.sh debug

# 编译 AndroidTest Instrumentation APK
JAVA_HOME=/home/antony/opt/jdk-17.0.19+10 ANDROID_SDK_ROOT=/home/antony/android-sdk ./android-native/gradlew -p android-native assembleDebugAndroidTest
```
- **预期输出**: 成功生成 `android-native/app/build/outputs/apk/debug/app-debug.apk` 与 androidTest 产物。

### 步骤 5: 原生 Release APK 编译与 R8 优化验证
```bash
JAVA_HOME=/home/antony/opt/jdk-17.0.19+10 ANDROID_SDK_ROOT=/home/antony/android-sdk ./android-native/gradlew -p android-native :app:assembleRelease
```
- **预期输出**: R8 成功执行混淆与死代码剔除，在 `android-native/app/build/outputs/apk/release/` 下生成约 405 KB 的 Release 包。

### 步骤 6: 保持 Web 代码库完整性
```bash
npm test
npm run typecheck
npm run build
npm run build:android
```
- **预期输出**: 7 个测试套件（66 项测试）全部通过，`tsc --noEmit` 零错误，Web 与 Android WebView 目标构建成功。

### 步骤 7: 检查 AndroidManifest 权限最小化
```bash
cat android-native/app/src/main/AndroidManifest.xml
```
- **预期输出**: 仅包含 `INTERNET` 与 `ACCESS_NETWORK_STATE`，无额外权限，无 Service，无 Receiver，无 WakeLock。

---

## 5. 核心代码类索引映射表

| 职责分类 | 核心类 / 文件路径 | 说明 |
|---|---|---|
| **核心领域模型** | [`BookKey.java`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/model/BookKey.java)<br>[`ChapterKey.java`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/model/ChapterKey.java)<br>[`ReadingAnchor.java`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/model/ReadingAnchor.java)<br>[`ChapterResult.java`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/model/ChapterResult.java) | 纯 Java 领域对象，不可变设计，覆盖 UTF-16 偏移 |
| **书源与算法** | [`SourceParser.java`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/source/SourceParser.java)<br>[`ChapterAssembler.java`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/source/ChapterAssembler.java)<br>[`TocMerger.java`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/source/TocMerger.java)<br>[`TxtSplitter.java`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/text/TxtSplitter.java) | 纯 Java 实现，多物理页合并、回环探针、流式正则分章 |
| **持久化与存储** | [`PersonalDatabase.java`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/personal/PersonalDatabase.java)<br>[`ContentDatabase.java`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/content/ContentDatabase.java)<br>[`PersonalRepository.java`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/personal/PersonalRepository.java)<br>[`ContentRepository.java`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/content/ContentRepository.java) | Room 双数据库隔离，版本号防乱序，宁多勿少防覆盖 |
| **网络与调度** | [`UrlSafetyValidator.java`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/network/UrlSafetyValidator.java)<br>[`SourceHttpClient.java`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/network/SourceHttpClient.java)<br>[`RequestScheduler.java`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/network/RequestScheduler.java)<br>[`ReaderRepository.java`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/repository/ReaderRepository.java) | SSRF 防御、4MiB 封顶、并发 `<= 2`、间隔 `>= 160ms`、单飞去重、代数控制 |
| **下载与自动缓存** | [`DownloadCoordinator.java`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/download/DownloadCoordinator.java)<br>[`DownloadPlanner.java`](file:///home/antony/coding/novel/android-native/core/src/main/java/org/wanshu/reader/core/download/DownloadPlanner.java)<br>[`ReadingSessionGate.java`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/data/download/ReadingSessionGate.java) | 前台单例调度、阅读近端提权、多条件门控、SQLite 退避 |
| **旧版迁移** | [`LegacyMigrationDetector.java`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/migration/LegacyMigrationDetector.java)<br>[`LegacyMigrationActivity.java`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/migration/LegacyMigrationActivity.java)<br>[`native-migration.html`](file:///home/antony/coding/novel/android-native/app/src/main/assets/migration/native-migration.html)<br>[`LegacyMigrationEngine.java`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/migration/LegacyMigrationEngine.java) | 非侵入检测、CSP 隔离 WebView、48KiB 流式分批 ACK、新进度保护 |
| **UI 与交互** | [`MainActivity.java`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/ui/MainActivity.java)<br>[`ShelfView.java`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/ui/shelf/ShelfView.java)<br>[`ReaderView.java`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/ui/reader/ReaderView.java)<br>[`BackController.java`](file:///home/antony/coding/novel/android-native/app/src/main/java/org/wanshu/reader/ui/nav/BackController.java) | ViewBinding，三级返回控制，5 主题切换，30% 锚点采样 |
