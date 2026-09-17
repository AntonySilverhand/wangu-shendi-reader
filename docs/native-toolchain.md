# 原生 Android 构建工具链与环境说明

## 1. 工具链环境记录

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
