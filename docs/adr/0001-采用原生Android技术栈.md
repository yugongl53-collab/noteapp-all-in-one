# ADR 0001：采用原生 Android 技术栈

## 状态

已接受，2026-08-24。

## 背景

一站笔记的 MVP 只面向 Android 手机，需要可靠处理本地结构化数据、学期日期展开、后台计时、准点提醒和系统权限。当前仓库只有产品规划文档，尚无应用工程；技术选择应优先降低单端实现和系统能力适配成本。

## 决策

- 使用 Kotlin、Jetpack Compose 和 Material 3 开发原生 Android 应用，`applicationId` 为 `com.yuncun.noteapp`。
- 最低支持 Android 12（API 31），`compileSdk` 与 `targetSdk` 当前使用 API 35，只适配手机竖屏并跟随系统明暗主题。
- MVP 使用单 `app` 模块并按功能分包，不为暂不支持的平台或功能预建模块。
- 使用 Room 保存结构化业务数据，DataStore 保存轻量设置和活动番茄钟状态；使用 Navigation Compose、ViewModel 与 Coroutines 组织导航、生命周期状态和异步任务。
- 使用 kotlinx.serialization 生成和校验带格式版本的 JSON 备份，避免手写字段解析和类型转换。
- 使用 Android Studio 稳定版及其内置 JDK 和配置的 Android SDK。项目创建后只通过已提交的 Gradle Wrapper 构建，不依赖全局 Gradle。
- 首版交付本地 APK，使用用户自行保管且不提交 Git 的 release keystore 签名。

## 备选方案

### Flutter

Flutter 可为未来跨平台保留空间，但 MVP 没有 iOS 或桌面端目标，后台计时、准点提醒和 Android 权限仍需要平台适配层，因此不采用。

### React Native

React Native 适合已有 Web 技术团队，但本项目没有既有前端代码，日期规则和后台系统能力需要额外桥接，维护面大于原生方案，因此不采用。

### 只使用系统 SDK

直接使用 SQLite、手写导航和 JSON 解析可以减少声明依赖，但会显著增加数据库迁移、生命周期恢复、导入校验和测试代码。已批准的 AndroidX 与 Kotlin 依赖能减少这些重复实现，因此不采用纯系统 SDK 方案。

## 影响

- API 31 以下设备无法安装应用；MVP 不承担老旧 Android 版本兼容成本。
- Compose 与 Material 3 需要同时验证浅色、深色、字体放大和竖屏滚动状态。
- Android Studio 可以直接使用已配置的 SDK 和内置 JDK；Android Studio 不提供项目可依赖的独立 Gradle 命令，Gradle 版本由 Wrapper 固定并在首次同步时获取。
- 模拟器验收前必须安装 API 31 或更高版本系统镜像并创建 AVD；准点提醒、后台计时和省电策略还需要实体设备验证。
- 新增本 ADR 所列名单之外的依赖时，仍需先说明简化收益并取得用户确认。
