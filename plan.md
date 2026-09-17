# 原生 Android 阅读器开发执行计划

> 本计划替代原来的 edge-to-edge 改造计划。旧计划保存在 [`docs/legacy-edge-to-edge-plan-v0.0.15.md`](docs/legacy-edge-to-edge-plan-v0.0.15.md)，旧 APK 验证记录在 [`android/VALIDATION.md`](android/VALIDATION.md)。
>
> **状态：调查与规划完成，原生重写尚未开始。不要把本文中的目标、示例命令、测试名称当成已经实现或通过。**
>
> 调查基线：git `adfe753d96cce633652730bddbb56a3fcd34dcdb`，现有 APK `org.wanshu.reader`，versionCode `15`，versionName `0.0.15`。

## 0. 首先读懂需求与执行规则

### 0.1 用户最终确认的范围（优先于此前讨论）

1. 开发真正的原生 Android 阅读器，不再用 WebView 渲染日常书架、正文、设置或执行书源解析。
2. 保留现有功能，修复阅读时没有明确入口返回主页面、切换其他书的问题。
3. 增加可开关的 **“阅读时自动缓存”**：用户阅读时，在不打扰阅读的情况下下载内容；先下载后续章节，条件允许时把后续全部缓存，再补齐前面已读章节。
4. **不需要应用退到后台、锁屏或被杀后继续下载。** 离开应用就暂停；下次阅读根据落盘状态继续。不要实现常驻服务、WorkManager 周期任务、开机广播、前台服务通知、精确闹钟或后台保活。
5. 目标是低内存、低功耗、流畅、轻量；不能为了“下载得快”增加并发、持续唤醒或牺牲主动翻章速度。
6. 用户反馈正常阅读未遇限速、源站速度充足：**正常状态持续利用闲置请求能力缓存，不预设源站很慢，不人为每下几章就长时间休眠**；只在真实限流/错误或设备约束出现时退避。仍不承诺网站未来永远可用。
7. **下载进度必须可见、持久化、可续接。阅读位置≠下载起点。** 已完整落盘章节零网络跳过；重新打开应用/开关功能/换章不从阅读位置重新下载。显示全书完整缓存进度和当前位置之后连续可离线余量，见6.3、6.5。

**术语约定**：本文的“自动下载/静默下载/预下载”均指前台阅读期间的低优先级任务，不是 Android 后台执行。

### 0.2 给执行模型的工作规则

- 先读 `AGENTS.md`，再完整读本文。严格按 P0→P12 顺序；阶段内每个编号任务独立实现、测试，避免一次性生成整个项目。
- 不先写一套漂亮界面再补数据与解析；先建立行为测试与数据契约。
- 现有网页与旧 APK 是回归基线，不删除 `src/`、`android/`、`public/` 或原测试。
- 新工程在 `android-native/`，不把新 Gradle 源码塞进旧 `android/java/` 参与旧脚本编译。
- 不添加联网书库、任意书源、登录、云同步、TTS、广告、统计 SDK、付费/验证码绕过；这些都不在当前范围。
- 保持手写 Java 类为顶层类，不使用匿名类、内部类、嵌套类、lambda；适用于新工程，以免低能力执行者同时维护两套编码规则。第三方库与代码生成产物不受此限制。在新目录添加 `AGENTS.md` 说明。
- 所有 I/O、数据库、解析、搜索在有界执行器上做；主线程只处理状态与布局。禁止 `allowMainThreadQueries()`。
- 不为通过测试放宽安全限制、降低完整性要求或删除失败用例。
- 先写失败测试再修已知缺陷。每阶段记录修改文件、实际命令、结果、阻塞项到 `docs/native-progress.md`。
- 没有设备就标记“未测”，不能把 JVM/网页测试算作 Android 真机验证。
- 没有原签名私钥就停止“覆盖升级发布”，不能生成新密钥后声称可无损升级。
- 本文件的勾选框只在相应验收真正完成后更新；计划编写不计为实现。

## 1. 调查结果与基线

### 1.1 实际架构

| 范围 | 源码事实 | 原生迁移要求 |
|---|---|---|
| APK | `android/java/org/wanshu/reader/MainActivity.java` 创建系统 WebView，加载 `https://reader.local/` | 原生 Activity + Android Views；迁移旧数据时才允许临时 WebView |
| 资源/代理 | `AssetClient.java` 拦截固定 origin，`SourceProxy.java` 提供受限 HTTP 传输 | 日常运行不需要 localhost 服务器、网页 API 或 JS 引擎 |
| 书源 | `src/shared/source.ts` 约 1200 行，包含目录去重、多物理页合并、回环探针、优先级队列 | 不能用“抓一次 HTML、取 #content”代替；逐项迁移并做 fixture 对照 |
| 在线书 | 固定万书阁 `36780`《万古神帝》 | 保留该在线书；“其他小说”现有能力主要是导入多个 TXT，不是全站选书 |
| 书架 | `home-view.ts` + `app.ts` 已有首页、本地书列表、切书 | 新书架必须同时列出在线书和所有本地书，读本地书后也能回在线书 |
| 导航 | 阅读顶栏/底栏没有“书架”按钮；Android 返回依赖 WebView history | 建立显式原生路由与返回顺序，不复刻 hash/history 缺陷 |
| 自动预取 | `app.ts::schedulePrefetch` 延迟 1600ms 只预取下一章，隐藏时取消 | 保留兼容开关，新自动缓存共用同一个队列，不再额外并发 |
| 手动下载 | `store/download.ts` 临时双 worker；只检查记录存在，状态不持久化 | 持久化任务、完整性判定、暂停/恢复、统一调度 |
| 进度 | `store/personal.ts` 存段落序号 + UTF-16 字符偏移，每书独立 | 原生仍用同样语义；不保存滚动百分比代替精确位置 |
| 显示 | v0.0.15 有安全区、IME、五主题、沉浸模式与共享弹层生命周期 | 行为保留，但原生只保留一个 insets 所有者，不搬 CSS bridge |

### 1.2 不能照抄的现有缺陷/文档偏差

- README 的 APK 章节仍提到本地监听服务器，但当前代码已经使用固定 `https://reader.local/`；以代码为准。
- README 有旧的 960px 宽屏断点，当前 `layout.ts` 是 `<840 / 840–1599 / ≥1600`。
- 主题实际是五种，不是部分文案里的“四套”。
- `purgeOutdatedChapters()` 会删除旧远程缓存；新版本应保留旧内容供离线阅读，再标记需要验证，不能升级就清空。
- 当前下载把“有记录”当作“已完成”；`loadChapterRecord()` 写盘失败仍可能返回正文。原生下载完成必须同时满足内容完整和落盘成功。
- `clearBookContent()` 未按 `source` 限制，数据页对本地书调用它可能删除不可重新获取的 TXT。新清缓存入口只能删除远程内容；删除本地书必须独立确认。
- `switchBook()` 会重建下载管理器；原生下载协调器不能绑定某个 Activity/书籍页面实例。
- 当前设置默认 `maxWidth=36`，重置按钮却写 `38`；原生统一为 36，并用测试固定。
- `fetchChapter()` 的注释说仅删除分页交界重叠，但实际合并循环会删除页内相邻相同段落。新增“同页合法重复句”测试，原生不得照抄这个丢文缺陷；差异写入契约变更记录。
- 导入个人数据的“取消”目前意味着整体替换。原生改成明确的“合并 / 替换 / 取消”，替换再确认。

### 1.3 本次实际执行的检查

- `npm ci --ignore-scripts`：成功；安装报告有 2 项开发依赖 moderate 告警，未执行强制升级。
- `npm run typecheck`：通过。
- `npm test`：7 文件、66 测试通过。
- `npm run build`、`npm run build:android`：通过。
- `bash tools/test-android-display.sh`：11 项 JVM 几何断言通过。
- 当前环境是 x86_64；默认 Java 为 26；`~/android-sdk` 当前只有 platform 30、build-tools 35.0.0；没有可直接使用的 Gradle 命令。
- `adb devices -l`：无连接设备。未测真机性能、功耗、APK 升级或系统栏。
- 仓库没有 `android/debug.keystore`。旧构建脚本会自动生成它；**自动生成的新 key 不能替代用户已安装 APK 的旧签名**。
- 旧验证文档来自另一环境，其 ARM/SDK 信息不代表当前机器；不要复制旧的“本环境已通过”结论。

## 2. 功能保留清单（最终交付逐项勾选）

“保留”指用户能力与数据兼容，不要求像素级照搬网页。PWA 安装等仅网页有意义的能力保留在网页，不在 APK 中伪造。

| ID | 必须具备的能力 | 基线文件 | 原生目标/验收 |
|---|---|---|---|
| F01 | 书架、书名/作者、继续阅读、从头读、每书进度、最近阅读 | `home-view.ts`, `personal.ts` | 在线书 + 多 TXT 同列；历史每书去重保留30条，首页至少展示最近5条 |
| F02 | **阅读返回书架与换书** | `app.ts`, `MainActivity.java` | 可见“书架”入口 + 系统返回；A→B→A 各自恢复；同 chapterId 不串书 |
| F03 | 章节标题、正文、字数/段数、本地/离线/缺页状态 | `reader.ts` | 缓存优先；缺页不能显示成完整；错误页仍能回书架 |
| F04 | 上下章、章首章尾、目录跳转、重取本章/继续补全 | `reader.ts`, `app.ts` | 首尾禁用正确；目录缺失时可用可靠 prev/next 兜底；旧内容不被较短结果覆盖 |
| F05 | 目录主线/番外、当前项、已缓存标记、分页懒加载、加载完整目录、失败重试 | `toc.ts`, `toc-view.ts` | RecyclerView 虚拟化；完整/部分/旧缓存标记区分；4300+项不卡 |
| F06 | 目录搜标题、章号、ID；数字定位；渐进搜索与取消 | `toc-search.ts` | 已缓存命中零请求；失败穷尽后停止；无 render→fetch 闭环 |
| F07 | 五主题：light/dark/black/eink/paper；静态纹理可关 | `settings.ts`, CSS | 系统栏、弹窗、键盘周边、启动面全覆盖；eink 无装饰动画 |
| F08 | system/hei/serif/kai；字号14–28、行距1.3–2.4、段距0–1.6、边距8–64、行宽24–60 | `settings.ts` | 即时预览、持久化、恢复默认；缺系统字体明确回退，不下载大字体 |
| F09 | 段落+字符精确位置；旋转、分屏、改排版后恢复 | `reader.ts` | 同一文本锚点；不能只用 RecyclerView 行号/像素 |
| F10 | 添加/取消、列表、摘要、时间、跳转、单删/清空书签 | `bookmarks-view.ts` | 同章不同位置可跳转；成功提示必须在事务提交后 |
| F11 | 本章搜索、总命中数、上/下一个、高亮、关闭恢复 | `search-view.ts` | 搜索不会改变正文/偏移；大量命中仍可遍历；输入法不遮挡 |
| F12 | 触摸滚动/正文点击唤栏、滚动自动收栏、鼠标、键盘快捷键 | `reader.ts`, `app.ts` | 保留 ←/→、T/F/B/S/逗号、Esc、Home/End；编辑时不抢键；文字可选择复制 |
| F13 | 手机/横屏/平板/折叠/大窗口布局，目录栏与进度栏 | `layout.ts`, CSS | 按窗口 dp 宽度响应，不按设备型号；内容限制行宽；点击目标≥48dp |
| F14 | edge-to-edge、四边 cutout、IME、可选沉浸、可选阅读常亮 | `native-display.ts`, Java display 类 | 沉浸默认关，编辑/弹层暂退出；常亮只在前台阅读生效 |
| F15 | 手动范围下载、当前起50/100/300章、全部、进度、失败列表、取消/重试 | `download.ts`, `settings-view.ts` | 持久化、可暂停恢复；“全部”包含主线与番外，不只当前已载目录 |
| F16 | **可开关的阅读时自动缓存** | 新增 | 后续优先→前文补齐；前台才运行；用户操作优先；退避可恢复 |
| F17 | 缓存章数/字节/空间、清理正文、离线阅读与未缓存提示 | `db.ts`, `settings-view.ts` | 统计不读正文全集；清缓存不动个人数据/本地书；可知道连续离线余量 |
| F18 | 多 TXT 导入、标题识别、自动进入/继续、本地删除、导出缓存 TXT | `txt.ts`, `app.ts` | SAF 文件读写；流式导入导出；导出按目录顺序含番外，缺章/部分章提示 |
| F19 | 个人 JSON 导入导出、merge/replace、设置/书签/进度/历史 | `personal.ts` | 兼容 version1 格式；明确不包含正文；取消不做替换 |
| F20 | 帮助、快捷键、版本、数据/保存失败诊断 | `settings-view.ts` | 本地保存说明，无云同步；源站不可用不阻止本地阅读 |
| F21 | 安装到主屏幕、Service Worker 离线壳、网页键鼠/宽屏 | `public/`, `src/web/` | 网页维持可用；原生 APK 自带壳，不显示 PWA 安装按钮 |
| F22 | 老 APK 原地升级数据迁移 | 新增兼容工作 | 书签/进度/设置/目录/TXT/缓存不因重写丢失，见第8节 |

最终为每个 ID 在 `docs/native-feature-matrix.md` 填：实现文件、自动测试、手工步骤、状态。没有证据的项不能勾“完成”。

## 3. 技术方案：固定选择，避免执行中反复改架构

### 3.1 工程与依赖

- 语言：Java，JDK 17 构建；Android Views/XML + AndroidX Activity、RecyclerView、Lifecycle；不使用 Compose/Flutter/React Native/游戏引擎。
- 核心逻辑独立 `:core` Java library，Android UI/存储/传输在 `:app`；核心测试不要求设备。
- 数据库：Room（Java annotationProcessor），两个 SQLite 文件，详见第4节。
- 传输：`HttpURLConnection`，手动逐跳验证重定向、有界流读取；不引入多个 HTTP 客户端。
- HTML：jsoup 用于结构扫描/实体处理；书源语义以 fixtures 和 `source.ts` 为准，不能让 HTML 库重排正文边界。
- 并发：应用级有限线程池 + 取消令牌 + 主线程 Handler；网络最多2条工作线程、个人/内容DB各1个有界执行器、解析/搜索共享1个CPU执行器，UI只观察状态。设置Room执行器，不同时叠加一套默认线程池；无 Rx、无 DI 框架、无自建线程海。
- 权限：日常只需要 `INTERNET`、`ACCESS_NETWORK_STATE`；文件走SAF，电量/省电状态使用普通系统API。移除旧下载无关的 `WAKE_LOCK`，不申请通知、前台服务、精确闹钟、开机自启、全盘存储或忽略电池优化权限。
- 后台设施：**不引入 WorkManager，不实现 Service/JobScheduler/AlarmManager 下载**。
- 不打包在线正文、网页运行时、网络字体；只允许少量现有 HTML fixture 用于测试，不能进入 release assets。

P0 使用以下固定版本作为起点，下载解析通过后写入 version catalog 与 dependency verification；若仓库不可获得/不兼容，停在 P0 查官方兼容表并记录替代，不用动态 `+` 版本：

| 项目 | 初始固定选择 |
|---|---|
| AGP / Gradle Wrapper / JDK | 8.13.2 / 8.13 / 17 |
| minSdk / compileSdk / targetSdk | 24 / 36 / 36 |
| AndroidX core / activity / recyclerview | 1.16.0 / 1.10.1 / 1.4.0 |
| lifecycle（仅需要的 ViewModel/LiveData 模块） | 2.9.1 |
| Room runtime/compiler/testing | 2.7.2（全部同版本） |
| jsoup | 1.20.1 |
| 测试 | JUnit 4.13.2；AndroidX test runner 1.6.2、ext-junit 1.2.1、Espresso 3.6.1 |

注意：这些是实施起点，不是本次已经构建验证的新工程。不要使用当前默认 JDK 26 碰运气。不要因为旧 APK target34 就忽略 target36 的 edge-to-edge、返回手势、大屏行为变化。

### 3.2 目录与主要职责（均为待新建）

```text
android-native/
  AGENTS.md
  settings.gradle.kts, build.gradle.kts, gradle.properties
  gradlew, gradlew.bat, gradle/wrapper/*, gradle/libs.versions.toml
  core/src/main/java/org/wanshu/reader/core/
    model/       BookKey, ChapterKey, ReadingAnchor, TocEntry, ChapterResult
    source/      SourceUrls, SourceParser, ChapterAssembler, TocMerger
    download/    DownloadPlanner, RetryPolicy, EligibilityPolicy
    text/        TxtParser, TextBlockBuilder, AnchorMapper, ChapterSearchEngine
    util/        Clock, RandomSource, CancellationToken, Base64Decoder
  core/src/test/java/...       纯逻辑/fixture/假时钟测试
  app/src/main/
    AndroidManifest.xml
    java/org/wanshu/reader/
      ReaderApplication.java, MainActivity.java, AppContainer.java
      navigation/  AppNavigator, ReaderRoute, BackController
      data/        personal/, content/, repository/, export/
      source/      SourceHttpClient, RequestScheduler, AndroidBase64Decoder
      download/    DownloadCoordinator, ReadingSessionGate, DownloadStatus
      ui/          shelf/, reader/, toc/, search/, settings/, bookmarks/, downloads/
      display/     InsetsController, ThemeController, ImmersiveController
      migration/   LegacyMigrationActivity, MigrationBridge, MigrationImporter
    res/           layout/, values/, drawable/, xml/
    assets/legacy-migration/   最小迁移 HTML/JS，不是完整网页阅读器
  app/src/test/...
  app/src/androidTest/...
  app/schemas/                 提交 Room schema JSON
  signing.properties.example  只写字段示例，不写真实密钥/密码

tools/native/                 待编写：build、verify、upgrade、perf 脚本
contracts/native/             fixture 清单、源解析期望、旧数据结构说明
```

包目录可在阶段内细分，但不要同时建立多套 Repository/调度器。`AppContainer` 手动组装单例；对象持有 Application context，不持有已销毁 Activity。

### 3.3 发布身份

- release 保持 applicationId `org.wanshu.reader`，使用原签名，versionCode 必须严格大于设备已装版本且不小于16；从实际发布历史决定版本名，不机械写成旧版本。
- 日常 native debug 默认加 `.native.dev` applicationIdSuffix，避免误覆盖用户旧数据。
- 增加独立 `upgradeTest` 构建类型：同 applicationId、原 key、仅测试设备使用。普通 `.native.dev` 不能验证真实升级迁移。
- 新 release 构建必须要求显式签名配置；缺 key 报错，禁止回退自动生成 debug key。
- release 无调试后门、无导出的测试组件、无 WebView debugging。升级测试钩子只能存在于测试 source set。

## 4. 数据与一致性契约（P1 实现前先建测试）

### 4.1 身份与偏移

- 所有章节 key 都是 `(bookId, chapterId)`，两者存字符串；不能只用数字章号/目录 index。
- 在线 `bookId="36780"`；迁移 `local-*` 必须保持原 ID。新 TXT 用随机 UUID 前缀，避免时间戳冲突。
- `ReadingAnchor = bookId + chapterId + paragraphIndex + offsetUtf16 + updatedAt`；paragraph 为逻辑段落序号，offset 是 JS/Java String 都使用的 UTF-16 code units，不是 UTF-8 字节数或 Unicode code point 数。
- 目录 index 是可变展示信息，不是锚点主键。存储时保持原始段落边界、换行和顺序；显示分块不能改变逻辑段落编号。
- 加入可选 `paragraphHash + quote + contentRevision` 便于补全文本后找回位置；旧数据缺这些字段仍可恢复。emoji/代理对边界要有测试。
- 字符数沿用 UTF-16 长度；存储体积单独使用实际 UTF-8 字节数，不能混用。

### 4.2 两个数据库，职责隔离

**`reader-personal.db`**（清缓存绝不触碰）：

- `reading_progress`：每书一条最新锚点，复合章节身份，提交序号防止旧异步写覆盖新位置。
- `bookmarks`：原 ID、书/章、锚点、摘要、创建时间；所有增删操作事务提交成功再确认。
- `reading_history`：每书去重最近30条。
- `settings`：五主题、排版、prefetch、常亮、沉浸及格式版本。
- `last_route`：书架或正在读的书/章；冷启动可恢复，但始终能回书架。
- `migration_runs`：来源、阶段、检查点、校验摘要、错误；不包含正文日志。

**`reader-content.db`**（内容与可恢复下载状态）：

- `books`：在线/本地、标题、作者、本地导入状态 `IMPORTING/READY/FAILED`。
- `toc_pages` + `toc_entries`：保存源页分组、加载/失败状态和合并后的稳定排序键；不丢番外。
- `chapters`：标题、source、完整性、sourceSemanticsVersion、charCount、bytes、prev/next、revision、missingPages、fetchedAt。
- `chapter_blocks`：分块正文与逻辑段落映射，单行序列化目标不超过128KiB，避免 SQLite CursorWindow 大字段；索引 `(bookId, chapterId, blockIndex)`。
- `source_pages`：未完成章节已获取的物理页、页号、正文段落/签名、发现链接、终章证据；成功页可恢复，不存广告/整份原始 HTML；大页亦需分块。完整正文提交后在同一事务删除不再需要的临时物理页，避免整本内容永久存两份；未完整的好页保留以续传。
- `download_policies`：每书自动缓存开关、范围、网络策略、容量；本地书不创建远程策略。
- `download_tasks`：key、需求标记 AUTO/PREFETCH/MANUAL、状态、优先级、attempts、nextAttemptAt、错误类型、运行 token；建立 `(bookId, state, priority, nextAttemptAt)` 索引。
- `download_plans`：planId、bookId、范围、首次启用参考章ID、当前推进阶段、后续/前文扫描检查点（chapterId+TOC revision）、策略版本。开关关闭不删除计划；完成章集合/完整性由章节表决定，检查点不能代替逐key验证。
- `source_cooldown`：源站级冷却截止与暂停原因，跨重启保存；不能重启即绕开限流。

Room schema 从1开始，启用 schema export；每次变化写 migration 和迁移测试，禁止 destructive migration。正文写入 + 相应 task 完成放在**同一个 content DB 事务**中。

跨两个数据库的操作不能假装是一个事务：迁移/导入用 staging + 检查点，最后 READY；个人记录引用不存在的正文时也保留（可提示重新导入），不能外键级联删除用户书签。

### 4.3 完整性与持久化

1. `complete=true` 必须来自解析的终章积极证据、所有已知必需页到齐，不等于 `missingPages=[]`。
2. 本地 READY 书内容视为完整，不要求具有远程 cache version。
3. “已下载”要求内容事务提交成功，且远程章节完整、版本受信任。内存可读但磁盘失败只提示“未保存”，不能加完成计数。
4. 旧版本或部分缓存先展示；联网且会话允许时补全。离线也能看已有部分，明确标注，不升级即删。
5. 新结果字符数或段落数更少时不覆盖旧结果；也不把旧长内容的 complete 状态直接换成新短结果的 true。记录冲突供重试，不错误凑成“完整”。
6. 原生修正页内重复段丢失后使用独立的 `sourceSemanticsVersion=5`；旧网页 v4 导入后保留可读，标记待验证。不要把 native DB schema version、旧 JS cache v4 和 sourceSemanticsVersion 混为一谈。
7. 连续离线余量：从当前目录位置的下一项向后扫描，遇到第一个不完整/不受信任/缺失章节停止；目录未完整时写“已知目录内至少N章”，不能称“整本已下载”。
8. 缓存统计只查元数据聚合，不 `getAll` 全正文；内存不因缓存书籍数量线性增长。配额统计包含远程正文和部分页临时数据，另用文件系统剩余空间防止SQLite/WAL等开销撑满磁盘；不用每章VACUUM，不能以逻辑正文大小冒充实际磁盘占用。
9. 删除远程缓存前暂停该书任务并递增内容 generation，拒绝旧在途结果回写，防止清完立即被旧响应填回。清理确认里可默认同时暂停该书自动缓存。
10. 清缓存 SQL 必须限定 remote；本地删除是独立操作并保留个人数据，除非用户另外明确要求删除。导入中断可清 staging，但不可删已 READY 原书。

## 5. 原生书源与统一请求队列

### 5.1 安全传输（不能放宽旧边界）

- 只构建本书目录/章节 URL，不向 UI/导入文件暴露任意 URL fetch。
- 允许域名仅 `wanshuge.org`、`www.wanshuge.org`；默认 HTTPS；验证协议、host、userinfo、默认端口、规范路径，每一跳**发请求前**验证，重定向最多5跳。
- chapterId 1–12位数字；物理页索引0–39；目录页1–80，后续目录页 URL 是 `page-1`。
- 拒绝路径穿越、编码绕过、非书籍路径、外域重定向、奇怪端口、无效 MIME；响应体解压后流式限制4MiB，不能读完整后才检查。
- 连接/读取超时各10秒；支持取消时断开当前连接，释放流。网络线程不可因 UI 退出而持续做重试。
- 保留状态码和 `Retry-After`。404 后续探针与章节首页404区别处理；403/429/验证页不能伪装成空正文或终章证据。
- 旧代码有 HTTPS→HTTP 回退；原生仅在明确传输失败且域名/路径合法时允许同源受限回退，不在403/429/验证码/TLS证书错误后切协议重试，不关闭证书校验。
- Android network security config 如需 HTTP，仅对这两个域名开放，禁止全应用 cleartext。给 HTTPS 故障、允许回退、拒绝回退分别写测试。
- 无脚本执行，无验证码识别/代理轮换/登录绕过，无高频重试来对抗网站限制。

### 5.2 解析迁移顺序

逐项移植并测试，不能直接靠线上抓取判断正确：

1. URL 构建/解析、实体/标题/中文数字、`normalizeTitle`。
2. `qsbs.bb(base64)` UTF-8 正文与老 `#content` 降级；过滤模板噪声，不删除合法重复句。
3. 从真实 DOM 链接识别同章分页，忽略 script/style/comment 中伪链接；同 ID=分页，不同 ID 首页才可能是邻章。不要相信“下一章”的文字。
4. 页码自报 `lastread.set`/标题、整页重复与回环识别。
5. frontier 抓取：按页号稳定合并，保留已成功页，失败页单独记；每章每轮至多一次额外末尾探针，最多40页。
6. 末页真正邻章/404探针/回环给积极证据；首页有下一章链接仍探 `_1`；达到上限不算完整。
7. 合并只消除整页重发及分页边界“前页末段=后页首段”；**页内重复段原样保留**。有缺失的中间页时不要把非相邻页当作连续页去重。
8. 目录原始分组→中位数过滤噪声→同 ID 去重→同号有标题优先→同号不同标题保留→无号尾声→番外排序，同标题番外保留最大数字 ID。
9. 总目录页数解析，44仅为兜底，不能把所有书和所有版本永久写死44页。

`Base64Decoder` 是 core 接口：JVM 测试用 Java 实现，Android 用 `android.util.Base64`，保证 API24 兼容；不要误用仅新 API 可用的标准库方法而漏测低版本。

### 5.3 队列契约

全进程只有一个 `RequestScheduler`，所有物理页请求（正文、目录、手动下载、预取、自动缓存、补全、重试）都经过它：

- 总并发 ≤2，所有请求启动间隔 ≥160ms；自动缓存正常初始间隔250ms，在连续成功10个物理页、无前台请求等待时可降至160ms。所有类型共用启动节奏，不是给每个 worker 单独 sleep 后相加形成突发。
- 正常时成功一个物理页就继续处理下一个就绪页，没有固定长休息、每章额外秒级sleep或“充电才整本下载”。源站吞吐由实际响应决定，不做探测速率上限的压测，不以提并发追求带宽占满。
- 自动/预取/目录扫描等非当前阅读请求同时最多占1个网络槽；另一个留给用户请求，避免源站一慢导致全局队头阻塞。预留槽不是闲置时也必须填满，有限并发下持续提前下载已足够，真机指标后再讨论改变上限。
- 优先级：当前章主动读取/补全 → 用户点开的目录定位 → 手动指定下载 → 自动后续缓存/普通预取 → 后文远端/前文补齐/全目录扫描。
- 当前阅读缺内容时不启动新的低优先级请求；正在完成的低优先级页可安全写盘，但不继续该任务下一页。
- 每章任务单飞，每物理页 key 单飞。用户翻到正在预取的章节时**提升/订阅已有请求**，不要重新抓一份。
- 取消订阅者与取消共享请求分开：用户取消手动范围，但阅读仍需要同一页时不能取消阅读。
- 源站限流 cooldown 是全队列约束，用户手动点击不能绕过 Retry-After 连续轰炸。
- 一个请求结束/约束变化/新任务到来触发 pump；无任务时没有循环、timer 或线程自旋。
- 一个最早就绪时间的一次性 timer 足够；退到后台取消 timer。不能每秒扫描所有任务。

## 6. 阅读时自动缓存：明确行为与状态机

### 6.1 用户界面与默认值

设置中的名称固定为 **“阅读时自动缓存”**，说明：

> 阅读时提前保存后续章节，整本模式会再补齐前面的章节。离开应用或锁屏即暂停；下次阅读继续。网站限制或存储不足时可能暂停。

- 默认关闭，不擅自对旧用户开启整本下载。
- 每在线书开关；范围“后续50章 / 后续200章 / 整本”，启用时默认“整本”，清楚展示流量与空间提示。
- 默认只在非计费网络自动下载；允许用户明确开启计费网络。文案不能把所有 Wi-Fi 都说成免费，使用 Android `NET_CAPABILITY_NOT_METERED`。
- 电量≤20%、系统省电/数据节省模式下暂停自动任务；恢复条件时由前台事件重评估。不影响已缓存阅读，主动网络读取仍由用户决定。
- 远程缓存默认上限256MiB，可选128/256/512MiB/自定义合理范围；临近系统低存储阈值提前暂停。默认不自动淘汰已缓存内容，避免“存完整本”和滚动删除互相打架。
- 保留原“预取下一章”设置：自动缓存关时沿用；自动缓存开时合并为同队列需求，不重复抓取。两者都关则没有推测性下载，但当前章必要补全仍是阅读行为。
- 本地 TXT 不显示可执行的自动联网下载开关，不能错用同名 chapterId 请求在线书。
- 阅读页只显示轻量状态/入口，例如“后续连续可离线126章”；不每章 Toast、不闪动进度、不反复打断。
- 下载页可查看**持久化整本进度**、完整/部分/待下/暂停/失败、当前正在下载章节、本次新增、暂停原因、重试时间；阅读页也能看到简要下载进度，不只藏在设置里，格式见6.5。进度事件合并更新最多1次/秒，无变化时不刷新；重开页面立即从DB恢复，不先显示0%。

### 6.2 前台生命周期门（这是本需求最重要的边界）

`ReadingSessionGate` 只有下列条件同时满足才允许 AUTO/PREFETCH：

```text
Activity 已 resumed
AND 存在当前 reader 会话（含从阅读打开的设置/目录弹层）
AND 屏幕 interactive 且未锁定
AND 不是导入/迁移等高负载流程
AND 本书启用相应策略且网络/电量/空间符合
```

- 回到书架：自动缓存暂停；切换到本地书：在线书自动缓存暂停；切换到另一本在线书（将来扩展）只调度当前阅读书。
- Home 键、切到其他 app、锁屏、`onPause`：立即关闭 gate、撤掉未启动请求、取消自动/下载在途连接，已提交内容保留；取消不是失败，不累加 attempts。
- 允许一次已完成的结果做有界提交，但不能再发下一页。用 generation 拒绝旧 UI 回调，不能在 `finally` 中无条件重启队列。
- 旋转/Activity 重建也可短暂暂停；恢复后仅重新打开 gate，不新建重复协调器、不重置冷却、不重建整本队列。
- 多窗口保守处理：只有 resumed 且书页有效时工作；不能因为进程仍存活就在用户不用 app 时下载。
- 在阅读弹层中可继续自动缓存；不要将普通 Dialog 的 windowFocus 变化误判成离开应用。
- **手动下载也不提供退后台持续下载**：在应用前台（包括书架/下载页）可继续，离开应用/锁屏暂停。页面清楚标注“离开应用后暂停”。
- 不需要、也不申请下载用 CPU WakeLock。`FLAG_KEEP_SCREEN_ON` 只服务用户主动开启的“阅读常亮”，退阅读/后台立刻撤销。

### 6.3 排序与持久化

`DownloadPlanner` 输入：当前书/章、稳定 TOC、缓存元数据、用户策略、任务状态；输出：有限批次的需求 key 和优先级。不执行网络或 UI。

整本计划**首次启用**时记录参考章 `initialAnchorChapterId`，建立以下顺序：

1. 当前章缺页由阅读请求高优先级补全。
2. 初始参考章后的50章作为近端缓冲，按目录顺序向后。
3. 其余后续内容（含番外/尾声）按目录顺序向后，直到目录末尾。
4. 初始参考章前的内容，从最近的前章向前补齐到书首。

**再次启动不是再次首次启用**：保留同一planId和下载检查点，先修复上次未完成任务，再从尚未完成的地方推进。读到哪儿只影响紧急缺口优先级，不覆盖 `initialAnchorChapterId` 或将下载检查点改成当前阅读章。

后续50/200模式只安排指定后续窗口，不补前文；窗口随阅读前进时只补新进入范围且未缓存的章节，原缓存与计数保留。完整缓存直接跳过；部分/旧版需补全或重验，不当作完成。

- 队列存 key，不存整本正文；一次只加载最多100条候选任务到内存。用已索引的未完成任务推进，不每翻一章从头扫描数千条缓存、更不重发数千个HTTP请求。
- 用户跳章时，仅把**新阅读位置附近真正缺失的后续章节**提升；附近都完整则继续原下载检查点。合并相同key，不取消并重建整个计划。
- 例：读第60章时已完整缓存61–160；重开仍读80，下一待下载通常应是161，而不是重新请求81–160。若跳到200且201未缓存，先补201附近缺口，再继续尚未完成的原计划；完整缓存从不因为重排而失效。
- 检查点是性能优化不是唯一真相：目录更新导致重排时按chapterId恢复，事务提交后推进；强杀后即使检查点落后，也通过完整记录零网络跳过。不能用“最后下载章号=160”假设所有前章均完整，失败洞仍单独排队。
- 同一解析版本的完整记录没有“每次阅读都刷新”或短TTL自动重抓；只有用户显式重取、确实损坏、解析语义升级或已验证的内容修订才重新取。条件GET/304仍是网络请求，不能充当零重复下载。
- 目录未完整：优先载当前/后续所需页；逐步加载剩余页；不能仅下载已知目录后声称“整本完成”。失败目录页显示明确状态，不空转。
- 手动范围和自动计划重叠时用需求标记合并；关闭自动只移除 AUTO/PREFETCH 相关需求，不删除手动任务/已下载正文。取消手动范围也不破坏阅读需求。
- 断网/空间不足/离开阅读时保存待办；重新阅读后重新评估就绪条件。
- 冷启动把上个进程遗留 RUNNING 恢复为待处理，验证已提交内容后跳过；不依赖上次 `onDestroy` 有机会执行。
- 暂停开关与任务取消必须立即反馈；当前已完成事务可保留，但关闭后不能自动发新请求。

执行模型应按以下伪代码组织推进，不能用 `for (chapter=current; chapter<last; chapter++) fetch(force=true)`：

```text
onReaderReady / onResume / onPolicyChanged / onTaskCommitted:
  重新评估gate；关闭则cancel未完成执行、移除timer并返回
  载入现有plan或仅在不存在时创建，恢复遗留RUNNING为PENDING
  将当前阅读附近的缺失项提升；不重置plan的下载检查点
  按索引取有限批待办；没有就绪项则设一个最早到期timer或结束
  对候选key再查有效完整记录：有则修正task完成并零网络跳过
  否则恢复该章source_pages及已发现frontier，仅请求尚缺的页
  每页均经RequestScheduler；每次发请求前再查gate/cancel/cooldown
  好页事务写入；有完整证据才合并并提交chapters+blocks+task完成+checkpoint
  提交成功才发进度事件并推进；无活动需求则停止，不递归空转
```

`onResume`仅在进程启动时清理前一运行token；同进程普通resume不能把仍有效的手动/阅读请求误设PENDING。部分页续传必须重建已取页签名、已发现链接、已尝试终章探针等解析状态，不只是从 `missingPages[0]` 开始抓；未发现的后页仍可能需要继续发现。

推荐任务状态（枚举定义成顶层类型）：

```text
PENDING -> RUNNING -> COMPLETE
                  -> RETRY_AT          可重试，nextAttemptAt 已落盘
                  -> NEEDS_ACTION      验证/解析异常/重试预算耗尽
                  -> PENDING           生命周期/用户取消当前执行，不算失败
PENDING/RETRY_AT + gate关闭 = 等待，不占工作线程
没有任何需求标记 -> CANCELLED          保留已提交章节
```

暂停原因是协调器派生状态（NOT_READING/APP_HIDDEN/OFFLINE/METERED/BATTERY_LOW/STORAGE_LOW/RATE_LIMITED/USER_PAUSED），不要为显示原因不停修改整本数千条记录。

### 6.4 有限重试与反爬友好

- HttpClient 只做一次物理请求；重试集中在协调器。禁止 HTTP层×章节层×下载层重复重试倍增。
- 普通超时/连接重置/5xx：建议5s、30s、2min、10min、30min，加小幅 jitter；单任务最多5次自动重试后 NEEDS_ACTION。假时钟测试，不在单测里真等半小时。
- 429、明确限流503：遵守 `Retry-After` 秒数或 HTTP-date；缺省源站冷却60s指数增长至30min。截止时间持久化，不因 app 重开归零；cooldown 期间不新发请求。
- 403/验证码/挑战页面：停止该源自动任务，显示“网站需要人工处理，自动缓存已暂停”；不自动解码挑战、不打开带特权 bridge 的网页处理它。
- 正文解析为空/结构异常：保留旧内容，有限失败后 NEEDS_ACTION，不当作完成、不无穷探页。
- 404只在有证据的后续页探针中表示不存在；章节首页404是该章失败，不能生成空的完整章节。
- 持久化成功的物理页可在下次继续，不重抓所有好页；若页布局/解析版本已变，整章重验但原文本仍可读，避免拼接不同版本页面。
- “重试失败”仍受源站 cooldown、存储、前台 gate 约束。手机时间修改时要限制异常长/负等待，活动间隔用 monotonic clock、跨进程截止用 wall clock。

### 6.5 下载进度展示与续传验收（必须实现）

阅读工具栏/轻量状态行显示例如：

```text
下载中 · 整本 1,280 / 4,330章（29.6%） · 后续连续可离线126章
已暂停 · 整本 1,280 / 4,330章 · 仅非计费网络下载
```

详细下载页显示：完整1280、部分3、待下载3047（含失败待处理数）、当前章标题/物理页进度、本次新增24章、已占远程缓存空间、暂停原因/可重试时间。这里章节分母必须是完整已知TOC去重后的总数，示例数字不是写死的真实书目。

- “整本进度”从DB完整且已提交的记录与TOC交集计算，既有缓存也计入；不是本次for循环走了多少步，不把跳过重复记录计为本次新增。
- `完整 + 部分 + 尚无正文 = 已知目录总数`；失败是可重叠的任务诊断子集，不再重复加进总数。没有得到完整TOC时显示“已完整缓存1,280章，目录尚未齐”，不给误导性的全书百分比。
- 本次新增是在本次阅读会话内从非完整变为完整并成功提交的章数；历史整本进度跨重启保留。下载到哪个章节要显示标题/ID，不只画一个进度条。
- 显示“连续可离线”与“整本完整缓存”两个不同指标：全书已存1,280章不意味着当前位置之后连续1,280章可读。
- 关闭/开启自动、返回书架/再进入、杀进程/重开不得清零整本进度或重置计划；主动清缓存后相应下降是正确行为，明确说明原因。
- 每次提交触发元数据增量更新；面板打开一次聚合查询；不要为刷新进度每秒读正文/整表扫描。无需常驻倒计时，展示可重试的时刻即可。
- 必测：61–160已完整且81–160位于当前读点之后，重启/开关/旋转各3次，FakeSource断言这些完整章节所有物理页新增请求数均为0；部分161已有第0/1页时只补未完成页及必要终章探针。
- 必测：任务已写盘但UI尚未收到通知即强杀，下次完整计数正确、不重复请求；写盘失败则不前移检查点/计完成；目录增补时分母调整但已有缓存不失效。

## 7. 原生阅读 UI 与位置恢复

### 7.1 页面结构与导航

单 Activity，原生书架/阅读/面板，`AppNavigator` 管路由；ViewModel 管当前阅读请求 generation。导航身份必须含 bookId。

- 阅读顶栏最左放明确的“书架”图标/文字及无障碍描述；目录另有入口；正文隐藏工具栏时点击正文即可唤出。
- 章末提供“上一章 / 下一章 / 目录 / 书架 / 下载 / 重取本章”，错误页也保留书架入口。
- 系统返回顺序：系统 IME/文字选择优先 → 顶层对话框 → 搜索/紧凑目录 → 阅读返回书架 → 书架交给系统退出。不要逐章回退历史。
- 返回书架前抓取旧书精确位置并入持久化写队列；切新书后不能用新 bookId 保存旧文本锚点。
- 页面请求响应必须验证 `(bookId, chapterId, generation)`；返回书架后晚到的响应可以按有效需求写缓存，但绝不能把界面拉回阅读。
- 书签跳同章不同位置不能被“同章已经打开”去重吞掉。
- 快速 A→B→A、阅读加载中返回、失败页返回、旋转后返回均需独立测试。

### 7.2 正文渲染的固定方案

使用 RecyclerView + 可选择的原生 TextView **显示块**，不自写 Canvas 中文排版器，也不把整本书放入一个 TextView。

- 逻辑层保留段落；`TextBlockBuilder` 合并短段落成约4–8K UTF-16显示块，巨大单段按安全字符边界拆块，并存块内范围到 `(paragraphIndex, offsetUtf16)` 的映射。
- 阅读页内存保留当前可见块、有限邻近块与元数据，正文 LRU 上限8MiB；TOC 元数据可以整本保留，但正文不能。
- 一个 TextView 块内支持原生长按选择/复制。跨块选择不能被忽略：增加上下文菜单“从此处开始选择 / 选择到此处 / 复制所选”，用两个逻辑锚点流式取得范围文本。UI 明示所选范围，可取消，测试跨多个段落与块的完整复制；不能只保留“复制本段”就宣称功能等价。
- 不使用 `Spannable` 给整本书加样式；只为显示块构造跨度。首行缩进、段间距、行高与文字选择要同时测试。
- 对超长 TXT 不先完整 `readText()`；不能在启动时为几万个段落建立 TextView。
- 搜索在逻辑文本上进行，命中转换为块内高亮；仅给可见块加 span，搜索不更改正文。大量命中用有界窗口/分页结果保持所有结果可遍历，不截断功能。

### 7.3 锚点捕获和恢复算法

1. 阅读区上方约30%高度作为锚线（扣除安全区与可见工具栏）；记录当前 layout revision。
2. 找到锚线穿过的显示块，用 `TextView.getLayout()` 的 line/offset API 得到块内 UTF-16 offset，映射到逻辑段落/字符。
3. 滚动中仅低成本记录候选；滚动停止后做精确取样；连续滚动最多每1秒保存一次，静止时无 timer 续写。
4. 切章、回书架、onPause、添加书签、排版变更前主动取样。数据库写在串行持久化执行器，递增序号防乱序；不得假设系统强杀前总能调用 onPause。
5. 恢复时定位包含锚点的块→`scrollToPositionWithOffset`→下一次布局完成后用文字行位置校正一次。允许有限2–3次收敛，禁止每帧持续校正。
6. 改字号/行距/宽度/insets前保存锚点；重建块/布局后恢复。IME 编辑时不抢滚动，关闭后恢复正文锚点。
7. 内容补全后若插入了前面的缺页，先用 paragraphHash/quote 在邻近文本找回；找不到则夹紧旧锚点并说明内容变更，不静默跳章首。
8. 精确验收：恢复的逻辑字符仍在同一可见行，偏差不超过一行；测试同时验证段落+字符而非只检查“滚动位置不为0”。

### 7.4 排版、窗口、无障碍

- 原网页 px 数值迁移为字体 sp、边距 dp；默认18sp、行高1.8、段距0.7em、边距20dp、行宽约36个字。保留用户数值，不误将 px 直接当设备物理像素。
- 允许系统字体缩放，测1.0/1.3/1.5/2.0；字体缺失时回退系统中文字体，不打包数十MiB字体。
- 可先以840/1600dp作为兼容布局断点；宽度不足时单栏，medium可收目录，wide增加进度栏。分屏/折叠以实际窗口宽度实时决定，不锁 orientation。
- 原生窗口 edge-to-edge，背景可到屏边，内容安全区取系统栏/cutout逐边 max；IME 与导航区域不能相加两次。
- 使用 WindowInsetsCompat 等原生设施，**不要复用 WebView 专用 `DisplayLayout` 的缩高逻辑再加 native padding**。
- 五主题覆盖启动 window、状态/导航图标、工具栏、对话框、列表和骨架；黑色主题背景真黑，paper纹理静态可关。
- 沉浸默认关；只在 reader 且无编辑/阻挡面板时隐藏系统栏；手势临时唤出时不抢回。
- `keepScreenAwake` 默认关；只用窗口 flag，不持有 CPU WakeLock。
- 内容描述、TalkBack阅读顺序、焦点、键盘导航、至少48dp触摸目标；鼠标滚轮/选字不被正文点击监听拦截。

## 8. 老 APK 数据迁移与文件互通（必须完成，不能降为可选）

### 8.1 原数据清单

旧稳定 origin `https://reader.local/` 的默认 WebView profile：

| 旧位置 | 内容 |
|---|---|
| localStorage `reader.settings.v1` | Settings 全字段 |
| localStorage `reader.personal.v1` | `{version:1, books:{bookId:{progress,bookmarks,history}}}` |
| localStorage `reader.localbooks.v1` | 本地书元信息（id/title/chapters/bytes/importedAt） |
| IndexedDB `reader-db`, version1, `chapters` | `key=bookId:chapterId`、正文数组、source、v/complete、missingPages等 |
| IndexedDB `reader-db`, `toc` | bookId、按页 entries、totalPages、updatedAt |
| 原生 display prefs | 仅启动主题辅助值，不替代 Settings 主数据 |

早于固定 origin 的随机端口版本不能自动保证找回；只能从仍可访问的旧版导出个人数据，并保留 TXT 原文件。清楚写出这个历史限制。

### 8.2 临时迁移 WebView 的允许范围

原生不理解 Chromium 的 IndexedDB 文件格式，**禁止直接解析/复制其内部 LevelDB 来冒充迁移**。使用一个只在升级导入阶段运行的隔离迁移 Activity：

1. 保持包名/签名/默认 WebView data directory；不能调用 `setDataDirectorySuffix`，不能 clearData/uninstall，也不能把升级测试改成新包名。
2. 在原 origin 加载 APK 内最小迁移页面，例如 `https://reader.local/native-migration.html`；不要启动旧 `app.ts`，否则可能触发网络、旧缓存 purge 或新写入。
3. 不为检测而在每次启动都初始化 WebView。新装设备（可结合 firstInstallTime/lastUpdateTime 与原生迁移标志）默认不创建；升级用户展示一次“迁移旧数据”，明确启动后才创建。设置中保留手动恢复入口用于检测漏判。
4. 禁止所有外部导航、子框架、网络、file/content URL、多窗口；只允许固定迁移 HTML/JS。CSP 禁止 frame/connect/object，JS bridge 只接受版本化数据块，不开放执行命令/任意路径。
5. 检查并处理原 origin 可能遗留的 Service Worker：旧 APK 通常不注册，但不能假定没有；只停用/注销控制迁移页的旧 worker，不删除 IDB/LS。确认加载的是 migration 版本握手后才开放数据接口。
6. 使用 IDB cursor 分批读，不用 `getAll()` 导出整本；单消息目标≤64KiB。大章节/段落切 chunk，带 runId、recordKey、序号、总数和校验值。
7. 每次读批使用新的 readonly transaction；等待 native ACK 时不能把 IDB transaction 挂起等它超时。检查点按已确认 key/块保存，可重开 cursor 接续。
8. bridge 回调只校验和入有界队列；SQLite I/O 在后台，ACK 在主线程通知 JS。限制字段大小/总大小/并发，仅固定操作白名单。
9. staging 写入、每记录幂等 upsert、每批事务确认；原 bookId/chapterId/书签ID保持不变。通过计数及流式规范摘要比对正文顺序、设置与个人数据，不能只比总章数。
10. 所有确认通过后标 migration COMPLETE，移除 bridge 并 destroy WebView；默认后续启动不再创建。迁移期间的 WebView renderer 可能由系统稍后回收，不能承诺 destroy 后进程内存立即归零；下次冷启动性能必须不启动引擎。
11. 失败/用户取消/强杀都保留旧存储和 native staging；允许继续。没有用户同意不得清旧 profile。只展示必要错误，不把正文/书签明文写日志。
12. 已存在新原生进度时，重试迁移只能按时间/幂等规则合并，不能旧记录覆盖较新的原生记录。

### 8.3 文件互通与备份

- 个人 JSON 保留 `app:"wangu-shendi-reader", version:1, exportedAt, settings, personal` 结构；本地 bookId 保持一致才能正确关联。新增 native 字段放可忽略扩展，提供兼容导出。
- merge：书签按ID去重，进度取较新 updatedAt，历史按章去重取新时间最多30；replace要独立确认；取消无副作用。
- 旧个人 JSON 不含 TXT/正文，也不含完整本地书元数据，不能宣传“导出个人数据=整库备份”。
- TXT 用 Storage Access Framework，不申请广泛文件权限；UTF-8兼容现有行为，检测BOM，失败给明确提示；额外编码支持不阻塞本版。
- 导入为流式解析+批事务+READY提交；失败回滚/可清未完成 staging，不覆盖已存在书；正确识别中文章回、序章/番外、Chapter N、数字标题以及无标题正文。
- 导出 TXT 按完整 TOC 顺序流式输出含番外，明确是否含部分章及缺失数；不能只导出 mainCount 范围。
- 文件体积/记录数/JSON深度设置防御上限并提示，不把恶意输入放到主线程；不将导入路径直接拼成文件系统路径。
- Android Auto Backup 不应被当成升级保证；明确 backup/dataExtraction 规则，默认排除可再获取的远程正文和迁移临时数据，个人数据与本地书的备份策略须在帮助说明中一致，避免半库恢复。

## 9. 分阶段实施任务（每阶段一个可验收交付）

### P0 — 固定环境、留基线、建立契约（不做 UI）

- [x] P0.1 阅读现有文件与本计划，在 `docs/native-feature-matrix.md` 建F01–F22表，全部初始 pending。
- [x] P0.2 确认并安装独立 JDK17、SDK36、兼容 build-tools，生成 Gradle Wrapper并校验下载；不能改全局 Java 去破坏其他项目。
- [x] P0.3 创建 `android-native/` 两模块空工程、version catalog、`.gitignore`（build/.gradle/local.properties/signing文件）；锁依赖，release启用R8/resource shrinking。
- [x] P0.4 `tools/native/build.sh` 明确使用 JAVA_HOME/ANDROID_SDK_ROOT；debug默认隔离包名，release无签名报错；加 lint/test 构建命令。
- [x] P0.5 建 fixture manifest，记录已有HTML的路径/用途/hash；将 source.ts 的解析结果和现有断言制作黄金契约，放 `contracts/native/`。与既有fixture相关的期望只能由脚本生成/人工审查，不手编整章文本。
- [x] P0.6 加三类合成用例：页内重复合法句、非相邻缺页重复、超长段/emoji。记录原生预期与旧 bug 差异，不能为黄金一致删除修复。
- [x] P0.7 建 `docs/native-progress.md`、`docs/native-toolchain.md`；记录旧 APK/key 是否拿到及真机是否可用。

**通过条件**：空 native APK可构建安装并显示原生占位书架；`:core:test` 可跑；网页66测试仍绿；确定旧签名缺失是发布阻塞项而非忽略。原生占位页不创建 WebView。

### P1 — 数据模型、Room、存储可靠性

- [x] P1.1 实现第4节 key/anchor/model；写 UTF-16、书籍ID隔离、数值合法性测试。
- [x] P1.2 实现两个DB与DAO，提交schema；显式的 clearRemoteCache/deleteLocalBook，不留通用无条件清库按钮。
- [x] P1.3 实现正文块映射/有限读取、事务保存、元数据统计、版本/完整性判定。
- [x] P1.4 实现个人 Repository、串行写入序号、history去重、merge/replace协议，保存错误可观察。
- [x] P1.5 测磁盘写失败、事务中断、旧缓存、较短补全、导入 staging 中断；完成计数不能提前增加。

**通过条件**：DB真实文件 reopen 后个人数据/正文一致；清远程缓存不动本地/个人；跨库中断可恢复；测试使用真实 Room（instrumentation），不能只测内存 Map。

### P2 — 书源纯解析等价与缺文防护

- [x] P2.1 按5.2的顺序实现 URL/标题/正文/目录解析；每项先移植对应 `test/source.test.ts` 测试。
- [x] P2.2 实现可注入 fetcher 的 ChapterAssembler；fixture覆盖现有1页/3页/5页、回环、缺页、旧模板、40页上限。
- [x] P2.3 新增页内合法重复保留、边界重复仅删一次、半页失败后续恢复、非法/空正文、取消不当失败。
- [x] P2.4 编写 `tools/native/verify-source-contracts.mjs`：比较TS基线/Java输出的章ID、标题、paragraphs逐项、字数、邻章、complete/missingPages和TOC排序；已批准bug修复用显式例外契约。

**通过条件**：全部现有解析场景迁移绿，末页正文逐段存在；不能只比“段数>0”；无需真实源站才能通过。

### P3 — 原生传输、请求队列、阅读 Repository

- [x] P3.1 实现安全 HttpClient，逐跳校验/流大小/超时/取消/状态头/受限HTTP回退。
- [x] P3.2 实现总并发2、低优先级槽最多1、全局间隔和提升优先级；使用假时钟与假传输测试。
- [x] P3.3 实现 `observe/readCached/ensureComplete` 分开的语义，UI可先读缓存，下载必须等完整落盘；去重不混淆订阅取消。
- [x] P3.4 接好章/物理页事务与 generation；网络断开、取消、旧结果不覆盖新正文。
- [x] P3.5 仿真测试外域重定向、4MiB上限、TLS错误、403/429、500、404探针；debug测试fixture不放宽release allowlist。

**通过条件**：当前章在低优先级慢请求存在时能用预留槽读取；所有上游请求可计数证明经过同一队列；release无任意URL入口。

### P4 — 原生书架、TXT、本地可用最小闭环与返回

- [x] P4.1 实现流式 TXT 导入/导出、SAF结果处理、取消/失败收尾，迁移 `test/txt.test.ts`。
- [x] P4.2 实现书架：固定在线书和多个本地书、继续/从头/最近记录、删除本地书确认。
- [x] P4.3 实现 AppNavigator 与原生阅读初版；顶栏/错误页/章末都可回书架。
- [x] P4.4 书籍复合路由、加载 generation、返回优先级、同章书签跳转接口。
- [x] P4.5 自动测试 A/B 两本均含 chapterId="1"：A读中间→书架→B→书架→A，进度、标题、正文均不串。

**通过条件**：断网可导入和读TXT；“回书架换书”新需求闭环通过；无网页承载；加载中返回不被迟到结果拉回。

### P5 — 阅读排版、主题、精确位置、窗口

- [x] P5.1 实现7.2显示块与原生选字/跨块复制，不先写自绘排版。
- [x] P5.2 实现7.3锚点，滚动节流、持久化顺序、所有必要生命周期点。
- [x] P5.3 五主题、排版全部范围/默认/重置、纹理、系统字体回退。
- [x] P5.4 edge-to-edge/cutout/IME单所有者、沉浸和常亮，默认关闭，主题首帧无闪白。
- [x] P5.5 紧凑/中/宽窗口、工具栏、上下章、错误/部分缓存状态、键鼠/快捷键/无障碍。
- [x] P5.6 测长段emoji锚点、旋转/分屏/字号变化、部分章插页后的锚点、选字时不误收栏。

**通过条件**：排版调整不跳章首；相同锚点可见误差≤一行；屏幕外/后台不保持常亮；低API与target36系统栏需要实机/模拟器证据。

### P6 — 目录、搜索、书签、设置与帮助功能齐全

- [ ] P6.1 目录RecyclerView、主线+番外、当前项与完整/部分缓存标记、懒加载、加载全目录与失败重试。
- [ ] P6.2 移植目录搜索状态机：260ms防抖、generation、数字probe半径3、每批4页、200展示结果上限与“还有结果”说明；无结果穷尽60秒零请求零自旋。
- [ ] P6.3 本章搜索120ms防抖、忽略大小写、命中总数/循环上下个、Enter/ShiftEnter、关闭释放焦点；搜索跨度映射正确。
- [ ] P6.4 书签添加取消/列表摘要/删除清空/精确跳转，默认近邻段落去重行为保持合理。
- [ ] P6.5 缓存统计/空间/清理、个人JSON导入导出、帮助、快捷键与保存错误诊断。
- [ ] P6.6 本地导入书不走在线加载；缺目录/空目录/失败目录所有输入仍响应。

**通过条件**：F01–F14、F17–F20全部有原生测试映射；导入取消不覆盖；书签保存失败不报成功；旧目录 freeze 场景不得复现。

### P7 — 持久化手动下载（先不接自动规划）

- [ ] P7.1 download_tasks + 单例 DownloadCoordinator + 应用前台 gate；状态可恢复、单飞、按key查缓存。
- [ ] P7.2 范围输入验证、当前起50/100/300、全书含番外、目录不完整时先可见地补目录。
- [ ] P7.3 暂停/恢复/取消/重试失败、完整与部分分开计数、持久化后才“已下载”。
- [ ] P7.4 只在app前台工作；返回书架/关下载面板不会丢任务；离开应用暂停、重进可恢复。
- [ ] P7.5 磁盘满、断网、强杀、重复开始、取消旧token再开始新token、正在清缓存的竞态测试。

**通过条件**：手动任务执行中切书不串状态；重新启动可见原任务；取消旧请求的 finally 不覆盖新任务；后台不发新请求，无Service/通知。

### P8 — 阅读时自动缓存（用户新增功能）

- [ ] P8.1 实现纯 DownloadPlanner 与排序单测：后50→其余后续→前文；50/200有限模式不补前文。
- [ ] P8.2 接设置默认关、整本默认范围、网络/电量/省电/空间策略；旧prefetch并入同队列。
- [ ] P8.3 实现 ReadingSessionGate：阅读+弹层允许、书架/本地书/后台/锁屏暂停，旋转幂等恢复。
- [ ] P8.4 统一需求标记、用户阅读提升、手动范围重叠、持久化planId/初始锚点/下载检查点；读点变化只提升真正缺失的近端内容，不重置计划。
- [ ] P8.5 正常连续调度与成功后恢复节奏；仅实际错误触发源站cooldown、有限退避、NEEDS_ACTION；所有等待时间落盘，不持线程sleep半小时。
- [ ] P8.6 实现6.5完整进度：阅读页简要进度+连续离线余量、下载页分项/当前章/本次新增/暂停原因；重开不清零，无每章提示或动画。
- [ ] P8.7 用合成200章，每章多物理页：读第60章，验证61开始→后续到末尾→59向前；用户跳到150且151未存时优先151，旧已提交内容保留。
- [ ] P8.8 关闭自动、回书架、Home、锁屏、强杀后验证没有新请求；恢复阅读不重抓完整页、不重置限流。
- [ ] P8.9 实现6.5零重复请求/部分页续传/进度跨强杀用例，断言已存61–160后阅读80时从161继续；不能仅检查“最终都有缓存”。

**通过条件**：无感下载两个目标都满足——不阻塞主动阅读且不用app时停止；没有任何系统后台组件；不是把“预取下一章”换个名字当整本自动缓存。

### P9 — 旧版迁移与真正覆盖升级

- [ ] P9.1 最小固定origin迁移assets/Activity/安全bridge，默认日常启动不创建WebView。
- [ ] P9.2 LS/IDB分批读取、staging/ACK/断点/幂等/摘要验证；本地TXT优先保护。
- [ ] P9.3 兼容个人JSON version1，旧v4远程内容显示优先、待验证不删除；原始锚点保留。
- [ ] P9.4 获取真实旧APK与原key；比较签名，确认versionCode递增，再进行原地 `install -r`。
- [ ] P9.5 测旧版有主题/两本TXT/同章多个书签/中段进度/部分远程缓存，迁移后逐字段、逐段摘要相同。
- [ ] P9.6 在迁移每阶段强杀再重进、重复迁移、低空间、坏记录、桥接超大消息、未授权导航；失败旧数据始终还在。
- [ ] P9.7 迁移完成后冷启动使用Perfetto/进程证据确认日常阅读不加载WebView；保留设置里的手动恢复入口。

**通过条件**：真实旧包升级通过，不靠重新安装/清数据伪造；无key/设备则本阶段明确阻塞，不能发布“无损升级”。

### P10 — 功能全量回归与网页兼容

- [ ] P10.1 完成第10节测试矩阵，F01–F22有证据，无遗漏功能。
- [ ] P10.2 API24/28/30/34/35/36核心路径；至少一台物理设备测离线、输入法、返回、长时间阅读。
- [ ] P10.3 target36下返回手势、横屏/大屏、系统字号与insets；Dialog叠加不重复恢复沉浸。
- [ ] P10.4 跑网页 typecheck/test/build 和既有聚焦验证，网页PWA保留。若改了web共享契约，还须按 `AGENTS.md` 跑对应E2E/delivery。
- [ ] P10.5 检查Android manifest：无无关权限，无service/receiver后台下载，release组件最小导出。

**通过条件**：功能矩阵全覆盖；任何未测设备项单列，不能用网页截图代替。

### P11 — 实测性能与功耗，再做针对性优化

- [ ] P11.1 按第11节采集同机同数据旧APK/native release基线，分自动缓存关/开两组。
- [ ] P11.2 如果内存超标先查文本全集加载、显示块LRU、Activity引用、重复订阅，不先引入自绘引擎。
- [ ] P11.3 如果翻章卡顿先查主线程SQL/解析/布局和网络队头；如果静止耗电先查timer、无意义进度通知、重复目录扫描。
- [ ] P11.4 100次切章/换书、30分钟阅读与30分钟后台暂停，记录请求数、线程、内存趋势与wake lock。
- [ ] P11.5 R8后重测反射生成代码、Room与迁移bridge，确保release不是只有debug能跑。

**通过条件**：结构性节能验收全满足；真机指标有可复查报告，不写“理论一定更省电”。性能不达标有根因与解决，而不是删功能。

### P12 — 发布、说明、维护交接

- [ ] P12.1 native release构建、签名/版本/aapt验证、真实覆盖升级，产物命名独立避免覆盖旧APK。
- [ ] P12.2 更新README/新工程AGENTS/验证文档，明确原生启动、构建、调试、网页维护、迁移限制、阅读时下载边界。
- [ ] P12.3 保留旧APK与数据导出指引；不能把安装低versionCode旧包称为安全回滚。严重问题用更高versionCode修复包；不卸载解决。
- [ ] P12.4 先预发布给用户验收，再按授权发布GitHub release；不要自动将“构建成功”标稳定版。
- [ ] P12.5 最终交接列实际完成feature、测试结果、性能报告、仍未测设备、已知限制；不得隐藏签名/迁移阻塞。

## 10. 必须建立的自动测试与人工验收

### 10.1 纯逻辑/fixture测试

建议测试类（名字可以直接采用）：

- `SourceUrlsTest`：合法构建与host/路径/端口攻击输入。
- `SourceParserParityTest`、`ChapterAssemblerTest`、`TocMergerTest`：现有35个source测试场景+新增丢文/边界用例。
- `RequestSchedulerTest`：并发/间隔/优先级/槽预留/同页单飞/消费者取消/无任务无timer。
- `DownloadPlannerTest`：顺序、跨番外、目录未完整、跳章重排、完整跳过、部分重试、手动合并、planId/初始锚点/检查点跨会话不重置。
- `DownloadProgressTest`：全书/窗口/本次/连续余量区别、目录未知不虚报百分比、完成提交后计数、强杀恢复、完整章新增请求为0、部分章只补缺页。
- `RetryPolicyTest`：429秒数/date、503、403、验证码、网络失败、时钟修改、冷却跨重启。
- `ReadingSessionGateTest`：reader/dialog/shelf/本地书/onPause/锁屏/旋转、两开关组合；离开时无新请求。
- `AnchorMapperTest`、`TextBlockBuilderTest`：UTF-16、emoji、多段/超长段、选择范围与显示分块不改逻辑索引。
- `TxtParserTest`、`PersonalImportTest`、`TocSearchControllerTest`：复用旧用例并加强取消/异常边界。

FakeSource 必须可注入每页响应/状态/延迟/错误/Retry-After，FakeClock可推进；测试不能用持续真实源站请求验证并发和限流。

### 10.2 Room/Android instrumentation测试

- `ContentDurabilityTest`：事务提交/回滚/重开、仅完整落盘计数、较短更新拒绝、部分页恢复、清理generation。
- `PersonalDurabilityTest`：立即加书签后冷启、进度写乱序、每书独立、merge/replace/cancel。
- `ReaderNavigationTest`：A→书架→B→A、慢加载返回、失败页返回、同章书签、系统返回不逐章倒退。
- `ReaderAnchorTest`：改字号/边距/窗口/系统栏后锚点；文本补全重锚；禁止只测滚动像素。
- `NativeSearchTest`：快速输入取消、无结果60秒收敛、10k命中可上下遍历、编辑不拦快捷键。
- `DownloadLifecycleTest`：前台慢源自动下载、切后台/锁屏请求停止、恢复继续、cancel竞态、用户打开在途章提升。
- `OfflineContinuityTest`：生成连续完整100章、飞行模式冷启动连续翻读；第一个缺口明确报未缓存，不空白伪成功。
- `StorageSafetyTest`：满盘/配额暂停、不误删TXT、删除当前本地书安全回书架、导入中断无半本READY。
- `LegacyMigrationTest`：字段/UTF-16/正文摘要/重复导入/中断恢复；真实旧版数据测试另用升级脚本。

### 10.3 集成场景表（每行必须有结果）

| 场景 | 预期 |
|---|---|
| 新装断网，导入两本TXT，退出/重开 | 能读、能换书、独立恢复；不发远程请求 |
| 读在线第60章，开启整本缓存，继续读 | 后续先行；连续离线余量增长；当前阅读请求优先 |
| 已存61–160，重开阅读80，再切换自动开关/旋转 | 整本进度保持，从161继续；81–160请求数为0 |
| 下载161第0/1页后离开app，再继续阅读 | 好页保留，只请求缺页及必要终章探针，不整章重来 |
| 网络正常、当前阅读命中本地 | 低优先级持续推进，无人为长休息，进度可见 |
| 开自动后进设置/目录，再关闭弹层 | 会话不丢；下载不重复；沉浸状态正确 |
| 回书架/切本地书/锁屏/切其他app | 自动请求停止；没有后台service/闹钟；已落盘不丢 |
| 手动下载时关面板、切书、退后台 | app前台不丢任务，后台暂停，恢复可继续 |
| 429 + Retry-After，随后关app重开 | 不提前重试，不倒计时轮询；显示冷却原因 |
| 403/验证页 | 暂停待操作；不把验证文案缓存成正文 |
| 某章第2页失败但后页可取得 | 原文已有部分可读，标不完整；以后补齐不丢末页 |
| 存储不足/事务失败 | 不显示已下载；旧内容/书签/TXT仍在 |
| 下载中清缓存/关闭自动 | 旧在途结果不重新填回，手动需求处理符合用户选择 |
| 目录只加载4页就点整本 | 继续逐步取目录，分母不冒充全书，包含番外 |
| 连续快速返回/切章/旋转100次 | 无串书、无旧回调抢界面、无订阅/Activity泄漏 |
| 原地升级 v0.0.15→native | 不卸载、不清数据、签名一致；迁移后位置/书签/TXT一致 |

## 11. 轻量、性能与功耗验收

### 11.1 必须满足的结构性约束（硬门槛）

- 普通启动/阅读不创建WebView；release不打包完整网页assets或远程正文。
- 无持续帧循环、无1秒进度轮询、无后台下载组件、无自动CPU WakeLock。
- 自动与预取都关、目录/正文已本地就绪时：静止60秒无源站请求、无周期性进度保存、无持续invalidate。
- 自动开但完成/暂停/离开阅读时：无周期唤醒；只保留前台必要的一次性到期事件，后台连该timer也撤销。
- 不把整本正文加载到内存；缓存容量增长不导致等比例内存增长；没有主线程I/O。
- Home/锁屏后传输取消进入收尾，允许有限提交，不再启动请求；后台30分钟应用源站新增请求为0。
- 下载与阅读共享限流队列，阅读延迟不能被整本任务排在后面。

### 11.2 初始量化目标（待真机基线校准，不是已取得数据）

在一台明确记录型号/系统/刷新率的中端物理机、release构建、合成标准数据上：

| 指标 | 初始预算 |
|---|---|
| APK下载体积（不含测试） | 目标≤8MiB；超过须列依赖/资源组成及理由，不删功能凑数 |
| 冷启动到书架可操作（本地） | 中位≤600ms，p95≤1s |
| 本地/缓存章节切换（普通章≤2万UTF-16） | 中位≤100ms，p95≤200ms，正确位置已恢复 |
| 稳态总PSS（所有属于app的进程） | 阅读目标≤100MiB；与旧WebView实测比较，不能拿JS heap代替PSS |
| 60Hz滚动 | 按实际frame deadline统计，jank比例<5%，无>100ms应用长帧 |
| 100次切书/切章后内存 | 回收稳定后不持续线性增长，净增长目标<10MiB |
| 静止阅读（自动关） | 应用CPU时间≤测量时长1%，无周期网络/渲染/锁 |

这些预算如需调整，先给相同测试条件的证据并记录批准原因，不能悄悄把测试阈值放宽到必过。

### 11.3 测量方法

编写 `tools/native/perf.sh` 与 `docs/native-performance.md`，只在授权测试设备运行：

1. 同设备、同亮度、同主题、同刷新率、同网络、同章节、同阅读动作；热状态/后台其他应用尽量一致。
2. 分四组：旧APK自动关、native自动关、旧手动/预取对照、native自动开；不能用native大量下载与旧版不联网直接比电量。
3. `am start -W` 可做启动近似，但首个可读正文另打trace marker；冷/热启动分别20次，记录中位/p95。
4. `dumpsys meminfo <package>` 统计总PSS，结合Perfetto看CPU/帧/线程；旧版额外包含renderer进程，不只主进程。
5. 静止阅读、滚动、切章、下载各采trace；battery耗电测试至少30分钟、重复3次，避免只看1%电量跳变。
6. 自动下载用本地注入固定延迟的FakeSource/测试源，记录成功完整章节数/请求数/字节/CPU；不能通过真实网站压测获取吞吐。
7. `batterystats`/Battery Historian/系统power rails（设备支持时）作为证据；没有可量化能量数据就报告CPU/网络/唤醒代理指标，不能编造mAh。
8. 明确屏幕耗电可能占主导，“原生”不自动等于省电；要求native静止/后台不比旧版增加无用活动，自动缓存的额外功耗与有效下载量一起报告。
9. adb battery reset/unplug、旋转设置、性能锁定等操作仅测试机可用，脚本用trap恢复；不对用户日常手机擅自改全局设置或清数据。

## 12. 执行命令与验收材料

以下 native 命令在 P0 建好工程后才可用；不要在文件不存在时声称通过。

```bash
# 网页基线（仓库根目录）
npm ci
npm run typecheck
npm test
npm run build
npm run build:android
bash tools/test-android-display.sh

# 原生：显式设置已安装的 JDK17 路径，不使用默认 JDK26
export JAVA_HOME=/absolute/path/to/jdk-17
export ANDROID_SDK_ROOT="$HOME/android-sdk"
cd android-native
./gradlew --version
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
# 上面产物是隔离包名的开发版本
adb devices -l
./gradlew :app:connectedDebugAndroidTest
# release需要原签名配置；没有就必须失败，不生成替代key
./gradlew :app:assembleRelease :app:lintRelease
cd ..
```

P0/P9/P11还需编写并记录帮助信息的脚本：

```text
tools/native/verify-source-contracts.mjs
  离线解析契约对照，不访问真实源站。
tools/native/verify.sh
  Java单测、lint、构建、契约；设备测试明确分开；任一失败非零退出。
tools/native/upgrade-test.sh <old.apk> <native-upgrade.apk> --confirm-test-device
  检查包名/版本/证书，旧版造测试数据→保存规范摘要→install -r→迁移→逐项验证。
  不 uninstall，不 pm clear，不伪造旧版本，不使用用户日常设备。
tools/native/perf.sh <package> --confirm-test-device
  记录设备/场景/构建hash，收集trace/PSS/帧/网络计数；不导出私人正文。
```

设备升级必须使用真实旧APK而非拿新代码改versionName冒充旧版。脚本找不到原key/设备/测试hook时输出 BLOCKED，不输出 PASS。

最终提交/交接应包含：

- `android-native/` 可重复构建工程及schema/依赖锁，不含key/密码/build产物。
- `docs/native-feature-matrix.md`：全部feature对应实现与证据。
- `docs/native-progress.md`：逐阶段实际结果，失败/阻塞如实记载。
- `docs/native-toolchain.md`、`docs/native-validation.md`、`docs/native-performance.md`。
- `contracts/native/` 与可离线运行的核心回归、原生设备测试。
- 在 `artifacts/native/` 保存测试报告/截图/trace（gitignore），私密内容不进入仓库。
- APK签名/版本验证结果；预发布说明清楚写“阅读时下载，离开应用暂停”，不要宣传后台常驻下载。

## 13. 关键失败模式速查（执行中随时核对）

| 错误实现 | 为什么不接受 | 正确做法 |
|---|---|---|
| 用另一个WebView/JS引擎重用source.ts当原生 | 没达到低占用原生目标 | Java纯解析+契约测试 |
| 引入WorkManager/前台服务保活 | 用户已明确不需要后台下载 | 生命周期gate+持久化队列 |
| 每章一个Timer/线程 | 空转、线程/唤醒膨胀 | 一个协调器、有界执行器、一个最早到期timer |
| 只要读到缓存就标下载成功 | 缺页/写盘失败仍不可离线读完整章 | 完整证据+事务成功 |
| 凭章号排序，忽略ID与番外 | 错号/重传/番外会丢或串 | TOC规范合并+复合key |
| 用户返回书架后网络结果再打开书 | 生命周期竞态，复现原问题 | generation与route双重校验 |
| 清正文缓存调用清整个content数据库 | 删除TXT与任务/目录 | 远程专用DAO+显式删除本地书 |
| 自动缓存开关关了，旧worker继续抓 | 隐形耗电/违背开关 | generation+需求标记+cancel token |
| 每次阅读重新以当前章创建整本计划 | 扫描/下载重复、进度清零 | 持久化planId与检查点，读点只调整缺失项优先级 |
| 每次预取都force刷新已完整章节 | 缓存形同虚设、浪费请求 | 完整有效记录零网络命中，无阅读触发短TTL |
| 把本次遍历/跳过数当整本下载进度 | 重启清零、虚报新增 | 已提交完整元数据统计，本次新增单独显示 |
| 同章去重导致无法跳到另一书签 | 去重了用户跳转意图 | 数据单飞与导航锚点分离 |
| 文本按屏幕块重新编号段落 | 改字号就丢进度 | 原始段落/UTF-16映射不变 |
| 因“重复句”对全部正文Set去重 | 丢小说正文 | 只删整页重复和相邻物理页边界重叠 |
| 升级重新生成debug.keystore | 不能覆盖安装旧app | 取回原key；否则阻塞发布 |
| 迁移调用旧app启动代码 | 触发旧purge/网络并破坏基线 | 独立只读迁移页+分批ACK |
| 没有真机却写功耗更低XX% | 无证据 | 标待测，报告真实可观测指标 |

## 14. 官方资料入口（实施阶段涉及对应 API 时核对）

以下是实施核对入口，不代表本次已逐页在线验证；尤其依赖版本与target36行为须在P0/P5查当前文档。

- Android Views/RecyclerView：https://developer.android.com/develop/ui/views/layout/recyclerview
- Activity 生命周期：https://developer.android.com/guide/components/activities/activity-lifecycle
- Android 16 行为变化：https://developer.android.com/about/versions/16/behavior-changes-16
- edge-to-edge：https://developer.android.com/develop/ui/views/layout/edge-to-edge
- 返回导航：https://developer.android.com/guide/navigation/custom-back
- Room 数据库迁移：https://developer.android.com/training/data-storage/room/migrating-db-versions
- SAF：https://developer.android.com/training/data-storage/shared/documents-files
- WebView bridge 安全：https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges
- Network Security Config：https://developer.android.com/privacy-and-security/security-config
- AGP 版本/工具链：https://developer.android.com/build/releases/gradle-plugin
- AndroidX release notes：https://developer.android.com/jetpack/androidx/versions
- 应用性能工具：https://developer.android.com/topic/performance

---

**最终完成定义**：原生日常阅读不启动浏览器引擎；现有阅读/排版/目录/搜索/书签/TXT/数据功能全部保留；旧数据可安全迁移；能明确回书架换书；阅读时可无感逐步缓存后续乃至整本，离开应用即暂停；实际测试证明不串书、不丢文、不误报下载完成、无无用后台活动。任何一项缺失，都不能称为完整迁移交付。
