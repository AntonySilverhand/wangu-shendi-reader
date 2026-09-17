# AGENTS.md (android-native)

给在此原生 Android 模块工作的编码代理的说明。**先读本文件再动手。**

## 编码约束与规则

1. **手写 Java 代码结构**：
   - 所有的手写 Java 类必须是**顶层类**（Top-level class）。
   - **严禁**使用匿名类（anonymous inner class）、局部类、成员内部类（inner class）、静态嵌套类（static nested class）以及 lambda 表达式。
   - 兄弟类、辅助类、监听器、Runnable/Callable、回调接口与实现类全部抽取为独立的顶层 `.java` 文件。
   - 兄弟类之间通过构造函数传引用或接口解耦。
   - 第三方库与注解处理器/代码生成产物（如 Room 生成的 DAO 实现等）不受此限制。

2. **线程模型与 I/O 规则**：
   - 所有数据库操作、网络请求、文件 I/O、HTML/文本解析、搜索等耗时操作必须在明确的有界后台执行器上执行。
   - **禁止** `allowMainThreadQueries()`。
   - 主线程只负责 UI 渲染与状态绑定。

3. **生命周期与无后台原则**：
   - 绝不引入常驻后台服务、WorkManager 周期任务、前台服务常驻通知、唤醒锁或开机广播。
   - 离开前台或锁屏时，所有自动缓存/下载调度必须立即暂停。

4. **工具链要求**：
   - 强制使用独立安装的 JDK 17 (`/home/antony/opt/jdk-17.0.19+10`)。
   - 强制使用 Android SDK 36 (`/home/antony/android-sdk`)。
   - 构建命令统一由 `./gradlew` 或 `tools/native/build.sh` 驱动。
