# 原生 Android 构建工具链与环境说明

## 当前续作环境（2026-10-04）

以下为本轮实际环境；第 1 节保留原交接环境，不是当前主机：

- 工作目录 `/home/droid/coding/novel`，Linux ARM64。
- 独立 JDK 17：`$HOME/opt/jdk-17.0.20.1+1`；SDK：`$HOME/android-sdk`，已装 platform 36。全局 Java 未修改。
- 构建：`JAVA_HOME="$HOME/opt/jdk-17.0.20.1+1" bash tools/native/build.sh check --offline :app:assembleDebugAndroidTest --console=plain`。脚本会验证 Java 17/SDK；不执行 shell `cd`。
- 本机 SDK 的签名/包信息检查用 `build-tools/34.0.0/apksigner`、`aapt`；另有 `35.0.0-2` 目录。Gradle 可以正常构建，仍有 SDK 重复安装位置警告，不把旧交接的路径/目录名当作事实。
- 本轮 debug APK：`org.wanshu.reader.native.dev`，code 16/name 0.0.16，min24/target36，APK 签名校验通过；约 3.5 MiB。仅独立调试包，不是原签名覆盖升级证据。
- `adb devices -l` 无设备；instrumentation APK 仅编译。生产签名配置、旧版数据迁移与设备升级均未验证。

## 1. 原交接工具链环境记录

- **操作系统**: Linux x86_64
- **全局 Java**: OpenJDK 26.0.2.1（保留系统全局，不修改，避免影响其他系统工具）
- **独立 JDK 17 (本工程构建专用)**: `/home/antony/opt/jdk-17.0.19+10` (Eclipse Temurin 17.0.19+10)
  - 验证命令: `/home/antony/opt/jdk-17.0.19+10/bin/java -version`
  - 构建要求: 所有原生构建与测试脚本强制设置 `JAVA_HOME=/home/antony/opt/jdk-17.0.19+10`
- **Android SDK 根目录**: `/home/antony/android-sdk`
  - `cmdline-tools`: `latest` (v20.0)
  - `platforms`: `android-36` (初始基线包含 `android-30`)
  - `build-tools`: `36.0.0` (初始基线包含 `35.0.0`)
- **Gradle 版本**: Gradle 8.13 (Gradle Wrapper)
  - AGP (Android Gradle Plugin): 8.13.2
- **真机/模拟器连接状态**:
  - `adb devices -l`: 当前无连接设备。所有真机性能/电量/系统栏验证标记为“待真机测试”，不以 JVM 测试冒充真机证据。
- **发布签名私钥状态**:
  - 仓库内无生产签名私钥与 keystore。旧构建脚本自动生成的 debug 密钥不能用于覆盖安装旧生产包。
  - 正式覆盖升级发布目前处于阻断状态，需用户提供原始签名密钥；日常测试使用独立的 `.native.dev` 调试包名。

## 2. 依赖管理与固定版本规范

遵循 `plan.md` 3.1 节版本：
- AGP: 8.13.2
- Gradle Wrapper: 8.13
- compileSdk / targetSdk: 36
- minSdk: 24
- AndroidX Core: 1.16.0
- AndroidX Activity: 1.10.1
- AndroidX RecyclerView: 1.4.0
- AndroidX Lifecycle: 2.9.1
- Room: 2.7.2
- jsoup: 1.20.1
- JUnit: 4.13.2
