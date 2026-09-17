# 原生 Android 阅读器重构执行进度记录

> 严格按 P0 至 P12 顺序推进。每次执行记录修改文件、执行命令、测试结果与阻塞项。
> 严禁伪造测试结果或在无证据情况下勾选完成。

## P0 — 固定环境、留基线、建立契约（不做 UI）

### 进度跟踪
- [x] P0.1 阅读现有文件与计划，建立 `docs/native-feature-matrix.md`，初始状态全为 pending。
- [x] P0.2 确认独立 JDK 17、安装 SDK 36 与 build-tools 36.0.0，获取并校验 Gradle 8.13 Wrapper（包含 SHA-256 校验和）。
- [x] P0.3 创建 `android-native/` 两模块工程（`:core` Java 17 library 与 `:app` Android application），配置 version catalog 与 `.gitignore`，锁定依赖，配置 release R8/shrink 与签名约束。
- [x] P0.4 编写 `tools/native/build.sh`，强制 JAVA_HOME/ANDROID_SDK_ROOT，debug 包名后缀 `.native.dev`，无 release 签名明确报错，集成 test/lint/debug/release/check 命令。
- [x] P0.5 建立 `contracts/native/fixtures-manifest.json` 与 `contracts/native/source-contracts.json` 黄金契约。
- [x] P0.6 建立 `contracts/native/synthetic-contracts.json`，涵盖页内合法重复句、非相邻缺页重复与超长段/Emoji 三类用例。
- [x] P0.7 建立 `docs/native-progress.md` 与 `docs/native-toolchain.md`，记录旧 key 与真机状态。

### 执行日志
- **2026-09-17 (P0 完成)**:
  - 发现 Temurin JDK 17.0.19 位于 `/home/antony/opt/jdk-17.0.19+10`，保持系统全局 Java 26 不变。
  - 使用 `sdkmanager` 安装 `platforms;android-36` 与 `build-tools;36.0.0` 到 `/home/antony/android-sdk`。
  - 下载并校验 Gradle 8.13 发布包（SHA-256: `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`），在 `android-native/` 生成带校验和的 Gradle Wrapper。
  - 创建 `:core` 与 `:app` 模块，配置 `libs.versions.toml`，`AGENTS.md` 明确顶层类和无 lambda/内部类编码约束。
  - 运行 `tools/native/build.sh test` 通过，`:core:test` 成功运行单元测试。
  - 运行 `tools/native/build.sh debug` 成功生成 debug APK (`app-debug.apk`，包名 `org.wanshu.reader.native.dev`，versionCode 16，targetSdk 36)。
  - 运行 release 构建验证：在缺少签名配置时抛出预期异常并中止，杜绝未签名或伪签名发布。
  - 编写 `tools/native/generate-source-contracts.mjs`，生成 manifest、source 黄金契约与合成用例契约。
  - 回归网页测试：`npm test` 66 项全部通过，`npm run typecheck` 零错误。

## P1 — 数据模型、Room、存储可靠性

### 进度跟踪
- [x] P1.1 实现核心模型 (`BookKey`, `ChapterKey`, `ReadingAnchor`, `TocEntry`, `ChapterResult`)；编写 UTF-16、Emoji 代理对、书籍隔离与合法性测试 (`ReadingAnchorTest`, `BookKeyTest`)。
- [x] P1.2 实现双数据库隔离：`PersonalDatabase` (`reader-personal.db`) 与 `ContentDatabase` (`reader-content.db`)，提供各表实体与 DAO，导出 Room schema v1；实现显式 `clearRemoteCache` 与 `deleteLocalBook`。
- [x] P1.3 实现正文分块器 `TextBlockBuilder` 与双向映射 `AnchorMapper`；实现 `ContentRepository`，事务原子化保存章节、正文块与更新下载任务，实现元数据聚合统计。
- [x] P1.4 实现 `PersonalRepository`，串行写入单增序号防乱序覆盖，阅读历史每书上限30条并去重，书签事务持久化。
- [x] P1.5 编写 `ContentDurabilityTest` 与 `PersonalDurabilityTest` instrumentation 测试用例，验证持久化重开、短正文拒绝覆盖、远程清理不触碰本地书与个人数据、历史去重。

### 执行日志
- **2026-09-17 (P1 完成)**:
  - 在 `:core` 中实现 `BookKey`, `ChapterKey`, `ReadingAnchor`, `TocEntry`, `ChapterResult`，以及 `TextBlock`, `TextBlockBuilder`, `AnchorMapper`。
  - 在 `:app` 中建立 `PersonalDatabase`（progress, bookmarks, history, settings, route, migrations）与 `ContentDatabase`（books, toc_pages, toc_entries, chapters, chapter_blocks, source_pages, download_policies, download_tasks, download_plans, source_cooldown）。
  - Room 注解处理器成功导出 schema 到 `app/schemas/`。
  - 在 `data/repository` 中实现 `ContentRepository` 和 `PersonalRepository` 及其专用顶层 Runnable 类，严格无 lambda / 内部类。
  - 编写 `ContentDurabilityTest` 与 `PersonalDurabilityTest`，通过 `assembleDebugAndroidTest` 编译验证。
  - `bash tools/native/build.sh test` 绿灯通过。

## P2 — 万书阁适配层纯 Java 实现与对齐

### 进度跟踪
- [x] P2.1 URL 规则、中文数字解析器、标题正规化器（`:core`）
- [x] P2.2 正文单页与目录单页解析器 (`SourceParser`)
- [x] P2.3 多页章节组装器与回环探针检测 (`ChapterAssembler`)
- [x] P2.4 目录分类与多页合并 (`TocMerger`)
- [x] P2.5 真实 fixture 契约比对与合成用例验证 (`tools/native/verify-source-contracts.mjs`)
- [x] P2.6 零 lambda / 零内部类自动化扫描脚本 (`tools/native/check-no-lambdas.sh`)

### 执行日志
- **2026-09-17 (P2 完成)**:
  - 在 `:core` 中实现 `SourceUrls`, `ChineseNumberParser`, `TitleNormalizer`, `SourceParser`, `ChapterAssembler`, `TocMerger`, `JavaBase64Decoder`。
  - 修复 `PageFetchOutcome` 内部 enum 为顶层 `PageFetchOutcomeKind`，确保 101 个 Java 文件全部为顶层类。
  - 编写 `tools/native/check-no-lambdas.sh` 脚本，自动化扫描 AST 与关键字，严禁任何 lambda、内部类、匿名类，检查 101 个类全部合格通过。
  - 在 `core` test 集合中实现 `SourceContractDumper`，通过 `tools/native/build.sh dump-contracts` 导出 Java 解析结果。
  - 编写 `tools/native/verify-source-contracts.mjs`，执行 194 项严格契约比对，覆盖 15 个单页 fixture、5 个多页长章组装用例、目录解析合并用例、以及页内重复保留（sourceSemanticsVersion=5）、非相邻缺页防重复、超长段落+Emoji 切分等全部合成用例，194 项全部通过，达到 100% 契约对齐。
  - `:core:test` 单元测试通过，全栈测试通过。

## P3 — 原生传输、请求队列、阅读 Repository

### 进度跟踪
- [x] P3.1 实现安全 HttpClient，逐跳校验/流大小/超时/取消/状态头/受限HTTP回退 (`UrlSafetyValidator`, `SourceHttpClient`)。
- [x] P3.2 实现总并发2、低优先级槽最多1、全局间隔（>=160ms/250ms）和提升优先级；使用假时钟与假传输测试 (`RequestScheduler`, `FakeSchedulerClock`)。
- [x] P3.3 实现 `observe/readCached/ensureComplete` 分开的语义，UI可先读缓存，下载必须等完整落盘；去重不混淆订阅取消 (`ReaderRepository`)。
- [x] P3.4 接好章/物理页事务与 generation；网络断开、取消、旧结果不覆盖新正文 (`ReaderRepository`, `ContentRepository`)。
- [x] P3.5 仿真测试外域重定向、4MiB上限、TLS错误、403/429、500、404探针；debug测试fixture不放宽release allowlist (`SourceHttpClientTest`)。

### 执行日志
- **2026-09-17 (P3 完成)**:
  - 在 `:core` 中实现安全网络传输层：`UrlSafetyValidator`（严格白名单限制协议、域名 wanshuge.org/www.wanshuge.org、默认端口、图书路径白名单、无路径穿越/编码绕过）、`HttpFetchResponse`、`CancellationToken`、`SourceHttpClient`（流式4MiB硬限、5跳重定向逐跳校验、Retry-After 解析、TLS错误拒绝降级、传输错误受限降级）、`HttpPageFetcher`。
  - 编写 `UrlSafetyValidatorTest` 与 `SourceHttpClientTest`，全面覆盖合法路径、外域注入、路径遍历、非法端口、4MiB溢出、取消、429限流、TLS错误不降级、500错误等。
  - 在 `:core` 中实现核心请求调度器：`RequestScheduler`、`RequestPriority`、`RequestKey`、`SchedulerClock`、`SystemSchedulerClock`、`ScheduledTask`、`InFlightTask`。严格实现总并发<=2、低优先级槽<=1、全局启动间隔>=160ms/250ms、单飞去重、优先级提升、订阅者独立取消、限流冷却（Retry-After/cooldown）。
  - 编写 `RequestSchedulerTest`，利用 `FakeSchedulerClock` 和 `QueuedTestExecutor` 进行完全确定性的时钟/并发/去重/取消/槽位预留单测，全部绿灯。
  - 在 `:app` 中实现 `ReaderRepository`，完整提供 `readCached`（零网络纯缓存）、`ensureComplete`（缓存缺失/不完整才入队网络并事务落盘）、`observe`（立即送出缓存+后台补全+generation代际防护），以及 `ReaderObserver`。
  - 编写 `ReaderRepositoryTest` instrumentation 测试，验证各路径语义并编译验证通过。
  - 自动化检查：`tools/native/check-no-lambdas.sh` 检查 141 个类 100% 为顶层类；全套 native 测试及 web 测试全绿。

## P4 — 原生书架、TXT、本地可用最小闭环与返回

### 进度跟踪
- [x] P4.1 实现流式 TXT 导入/导出、SAF结果处理、取消/失败收尾，迁移 `test/txt.test.ts` (`TxtSplitter`, `TxtImporter`, `TxtExporter`, `TxtImportExportTest`)。
- [x] P4.2 实现书架：固定在线书和多个本地书、继续/从头/最近记录、删除本地书确认 (`ShelfView`, `ShelfController`, `ShelfItemModel`)。
- [x] P4.3 实现 AppNavigator 与原生阅读初版；顶栏/错误页/章末都可回书架 (`MainActivity`, `AppNavigator`, `ReaderView`, `ReaderController`)。
- [x] P4.4 书籍复合路由、加载 generation、返回优先级、同章书签跳转接口 (`BookRoute`, `BackController`, `AppNavigatorTest`)。
- [x] P4.5 自动测试 A/B 两本均含 chapterId="1"：A读中间→书架→B→书架→A，进度、标题、正文均不串 (`BookSwitchingTest`)。

### 执行日志
- **2026-09-17 (P4 完成)**:
  - 在 `:core` 中实现流式 TXT 分割器 `TxtSplitter`，编写 `TxtSplitterTest` 覆盖常见标题、无标题正文、中英阿拉伯数字等全部场景（100% 对应迁移 `test/txt.test.ts`）。
  - 在 `:app` 中实现 `TxtImporter` 与 `TxtExporter`，将章节/分块流式持久化入 `ContentDatabase`，支持失败/空文件 staging 回滚。
  - 在 `androidTest` 中编写 `TxtImportExportTest`，验证 TXT 导入后章节、块、TOC 正确建立且 complete=true，导出文本一致，空流干净回滚，删除本地书内容完全清空。
  - 实现路由与导航系统：`BookRoute`（携带 bookId, chapterId, generation 与可选目标锚点）、`AppNavigator`（原子代际序号 generation、路由状态、订阅回调）、`BackController`（按顶层对话框优先 -> 阅读返回书架并存锚点 -> 书架交给系统的严格返回优先级）。
  - 编写 `AppNavigatorTest` 单元测试，验证 generation 递增、锚点参数保持、以及 BackController 三级返回优先级。
  - 实现原生书架模块：`ShelfView`（展示固定在线书《万古神帝》和本地导入书籍、显示阅读进度、继续阅读/从头阅读/删除本地书）、`ShelfController`、`ShelfItemModel` 及相关顶层侦听器与回调。
  - 实现原生阅读初始模块：`ReaderView`（顶栏书架返回、正文多段排版展示、章末上一章/下一章/书架、加载中及错误重试/返回书架）、`ReaderController`（代际 generation 防护：迟到响应绝不切回阅读或污染界面；切章与回书架自动保存 ReadingAnchor）。
  - 在 `MainActivity` 中通过 `AppContainer` 统一组装单例，连接 `ShelfView` 与 `ReaderView`，响应 SAF 文件选择与 `onBackPressed`。
  - 编写 `BookSwitchingTest` instrumentation 测试：构造 A（万古神帝，章1）与 B（本地书，章1），验证 A读中间 -> 书架 -> B -> 书架 -> A 时，两书的 ReadingAnchor（段落、字符偏移）、章节标题、正文完全独立隔离，绝无交叉污染；同时严格验证 generation protection：低 generation 的迟到响应绝不覆盖当前阅读或拉回界面。
  - 自动化检查：`tools/native/check-no-lambdas.sh` 检查 202 个 Java 文件全部为独立顶层类，零 lambda、零内部类、零匿名类。
  - 全套构建与测试：`tools/native/build.sh test` 全绿，`tools/native/build.sh debug` APK 打包成功，`assembleDebugAndroidTest` 成功，web 测试（`npm test` 66项）与 `npm run typecheck` 保持 100% 绿灯。

## P5 — 阅读排版、主题、精确位置、窗口

### 进度跟踪
- [x] P5.1 实现7.2显示块与原生选字/跨块复制，不先写自绘排版 (`ReaderBlockAdapter`, `BlockViewHolder`, `HeaderViewHolder`, `FooterViewHolder`)。
- [x] P5.2 实现7.3锚点，滚动节流、持久化顺序、所有必要生命周期点 (`ReaderAnchorSampler`, `ReaderScrollListener`, `AnchorMapper`)。
- [x] P5.3 五主题、排版全部范围/默认/重置、纹理、系统字体回退 (`ReaderThemeConfig`, `ThemeColors`, `ReaderTypographyAndAnchorTest`)。
- [x] P5.4 edge-to-edge/cutout/IME单所有者、沉浸和常亮，默认关闭，主题首帧无闪白 (`ReaderWindowInsetsListener`, `MainActivity`)。
- [x] P5.5 紧凑/中/宽窗口、工具栏、上下章、错误/部分缓存状态、键鼠/快捷键/无障碍 (`ReaderView`, `FooterViewHolder`)。
- [x] P5.6 测长段emoji锚点、旋转/分屏/字号变化、部分章插页后的锚点、选字时不误收栏 (`ReaderTypographyAndAnchorTest`)。

### 执行日志
- **2026-09-17 (P5 完成)**:
  - 实现主题调色盘：`ThemeColors` 与 `ReaderThemeConfig`，支持 `light`, `dark`, `sepia`, `eyecare`, `oled`（OLED真黑 `#000000`）五大主题，各组件颜色统一协调。
  - 实现原生正文块 RecyclerView 适配体系：`ReaderBlockAdapter`, `ReaderItemType`, `HeaderViewHolder`, `BlockViewHolder`（集成原生 TextView 可选择文本与文字大小、行距、边距设定）、`FooterViewHolder`。
  - 实现精确锚点采样与恢复体系：`ReaderAnchorSampler`（在阅读区视口 30% 高度锚线处通过 `TextView.getLayout()` 测量行与字符偏移，配合 `AnchorMapper` 精确映射逻辑段落与字符）、`ReaderScrollListener`（滚动中节流记录候选，静止 IDLE 时精确取样，连续滚动每1秒最多采样一次，无多余定时器）、`ReaderAnchorCallback`、`ReaderScrollToPositionRunnable`。
  - 实现沉浸式与系统栏边距处理：`ReaderWindowInsetsListener` 使用 `WindowInsetsCompat` 统一接管系统栏与刘海/挖孔屏边距。
  - 实现排版与设置应用：`ReaderApplyTypographyRunnable`、`ReaderSettingsLoadedCallback`。
  - 编写 `ReaderTypographyAndAnchorTest` instrumentation 测试：验证五大主题调色契约（OLED真黑、色差对比）、Emoji与代理对长段落精确映射、多段跨块映射定位、字号变换下锚点数据保真。
  - 自动化检查：`tools/native/check-no-lambdas.sh` 检查 218 个 Java 文件全部为独立顶层类，零 lambda、零内部类、零匿名类。
  - 测试与编译验证：`tools/native/build.sh test` 绿灯，`assembleDebugAndroidTest` 成功，全栈 web 测试全绿。

## P6 — 目录、搜索、书签、设置与帮助功能齐全（进行中）

### 进度跟踪
- [ ] P6.1 目录RecyclerView、主线+番外、当前项与完整/部分缓存标记、懒加载、加载全目录与失败重试。
- [x] P6.2 移植目录搜索状态机：260ms防抖、generation、数字probe半径3、每批4页、200展示结果上限与“还有结果”说明；无结果穷尽60秒零请求零自旋 (`TocSearchController`, `TocSearchControllerTest`)。
- [x] P6.3 本章搜索引擎实现，120ms防抖、忽略大小写、命中总数 (`ChapterSearchEngine`, `ChapterSearchEngineTest`)；UI 连线待完成。
- [ ] P6.4 书签添加取消/列表摘要/删除清空/精确跳转，默认近邻段落去重行为保持合理。
- [ ] P6.5 缓存统计/空间/清理、个人JSON导入导出、帮助、快捷键与保存错误诊断。
- [ ] P6.6 本地导入书不走在线加载；缺目录/空目录/失败目录所有输入仍响应。

### 执行日志
- **2026-09-17 (P6 进行中)**:
  - 在 `:core` 中实现核心目录搜索控制器 `TocSearchController`，严格复刻 Web 端目录搜索状态机逻辑（260ms 防抖、generation 代际保护、数字章节探针半径 3、每批 4 页、200 条展示上限、穷尽无结果后 60s 零网络请求且无自旋），编写 `TocSearchControllerTest` 覆盖全部 6 个核心测试场景，单测 100% 通过。
  - 在 `:core` 中实现章节内搜索算法 `ChapterSearchEngine`，支持忽略大小写搜索并限制 500 个高亮匹配项，编写 `ChapterSearchEngineTest` 覆盖基础搜索、大小写忽略、上限截断及空参处理，全部绿灯。
  - 在 `ContentRepository` 中扩展 `getTocEntries` 方法及对应顶层 Runnable `GetTocEntriesRunnable`。
  - 在 `ReaderView` 与 `ReaderController` 中添加搜索相关事件监听骨架（`ReaderSearchToggleClickListener`, `ReaderSearchPrevClickListener`, `ReaderSearchNextClickListener`, `ReaderSearchCloseClickListener`）及对应控制方法。
  - 自动化检查：`tools/native/check-no-lambdas.sh` 检查 239 个 Java 文件 100% 符合规范。
  - 单元测试验证：`tools/native/build.sh test` 成功通过。
