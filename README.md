# HiALife

使用 **Compose Multiplatform** 开发的海信空调客户端，Android、iOS 和 Desktop 共用界面及业务逻辑，直接调用已有海信爱家接口，无需部署中间服务。

## 功能

- 海信账号密码登录、已有 `session.json` 导入、退出并清除本地会话。
- 自动保存轮换后的 access / refresh token，临期续期，认证失败后最多重试一次。
- 查询账号下的空调，切换设备，显示在线状态、室温和设定温度。
- 整机开关、温度、运行模式、风速，以及新风开关、内外循环、新风风速。
- 控制项及取值从设备能力接口读取；设备离线或请求进行中时禁用操作。
- 指令提交后最长约 20 秒回读状态；不会将“HTTP 请求成功”等同于设备已执行。
- 手机纵向布局、桌面设备侧栏、独立演示模式。演示模式不调用网络接口。

## 开发运行

需要 JDK 17、Android SDK 36；iOS 需要 macOS + Xcode（iOS 15+，支持 Apple Silicon 模拟器与真机）。工程自带 Gradle Wrapper。首次构建需要下载依赖。

```bash
# 桌面端：macOS / Windows / Linux（Windows 使用 gradlew.bat）
./gradlew :composeApp:run

# Android APK
./gradlew :composeApp:assembleDebug

# iOS 共享框架
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64

# 协议测试
./gradlew :composeApp:desktopTest

# 当前操作系统的桌面安装包
./gradlew :composeApp:packageDistributionForCurrentOS
```

Android 的 SDK 路径放在不提交的 `local.properties` 中：`sdk.dir=/你的/Android/sdk`，也可配置 `ANDROID_HOME`。用 Android Studio 打开本目录即可运行 `composeApp`。

iOS 打开 `iosApp/iosApp.xcodeproj`，选择 `iosApp` Scheme 与目标模拟器运行。Xcode 的 Build Phase 自动调用 Gradle 构建共享框架。部署真机时在 Signing & Capabilities 中选择自己的开发团队。macOS 构建 DMG、Windows 构建 MSI、Linux 构建 DEB，需在相应系统上执行打包。

## 登录与会话

首次启动可用海信账号密码登录，也可点击“使用已有会话登录”，在本机粘贴旧脚本 `.local/session.json` 内容。不要将账号、密码或会话提交到 Git。密码只用于登录，不持久保存。

- Android：Android Keystore 中的 AES-GCM 密钥加密会话，禁止系统备份。
- iOS：存放于 Keychain，使用仅限本机的访问策略。
- Desktop：会话存放在 `~/.hialife/session.json`；POSIX 系统目录权限 0700、文件 0600。该文件为明文令牌，使用系统用户目录保护；Windows 继承用户目录 ACL。

密码登录入口根据已有脚本迁移，**尚未使用真实账号密码验证**。若官方服务要求额外验证，需先在官方客户端完成验证，再导入已有会话。现有令牌也可能失效，需要重新登录。

## 代码结构

```text
composeApp/src/commonMain/    共享 Compose UI、状态管理、API、设备能力解析
composeApp/src/androidMain/   Android 入口、OkHttp、JCA 加密、Keystore
composeApp/src/iosMain/       iOS 入口、Darwin 网络、CommonCrypto、Keychain
composeApp/src/desktopMain/   桌面入口、OkHttp、JCA 加密、本地会话存储
iosApp/                      SwiftUI 宿主与 Xcode 工程
hialife.py                   原有接口工具（保留）
docs/API-RESEARCH.md          既有协议分析与历史验证证据
```

公共网页客户端签名常量来自已有脚本，不是账号凭据。所有请求使用 HTTPS，不打印令牌或原始登录响应。控制能力来自实际型号定义；不同型号返回的命令、可选值及状态映射可不同。

接口来源、已验证范围及原有 Python 用法见 [API 研究记录](docs/API-RESEARCH.md)。真实空调的控制效果需要实际设备回读确认，演示数据不能作为真实执行证据。

本次构建、接口查询与运行检查见 [验证记录](docs/VALIDATION.md)。
