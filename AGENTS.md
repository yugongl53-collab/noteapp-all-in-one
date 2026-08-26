# NoteApp 项目级 Agent 规范

本文件补充 NoteApp 仓库特有约束；通用工作方式、文档规范、依赖审批、Git 分支与 PR 流程继承全局 `AGENTS.md`，此处不重复。

## 项目现状与交付边界

- 本项目是单模块、单用户、单设备、本地优先的 Android 个人效率应用，包含灵感、学期日程、提醒、事件池、番茄钟、时间记录、统计和 JSON 备份恢复。
- M0 至 M7 功能已实现；M8 已完成 API 35 模拟器上的自动化验收、Lint、Debug 和未签名 Release 构建。
- API 31 最低版本兼容、Android 12+ 实体手机系统行为、用户 release keystore 签名和安装冒烟仍是正式交付前置条件。完成前只能称为 Debug 验收包或未签名 Release 构建，不能宣称正式 Release 已交付。
- MVP 不包含账号、云同步、多人协作、桌面端、应用商店发布或番茄钟自动生成 `TimeRecord`。不得把规划功能写成已实现行为。

## 工程结构

- `app/src/main/java/com/yuncun/noteapp/`：Android 生产代码，按 `data`、`domain`、`pomodoro`、`reminder` 和 `ui` 分包。
- `app/src/test/`：无需 Android 运行时的纯 Kotlin 单元测试。
- `app/src/androidTest/`：Room、DataStore、Compose、系统 Intent 与 Android 生命周期集成测试。
- `app/schemas/`：Room Schema 快照；数据库版本变化时必须同步更新。
- `docs/product/`：产品需求和页面交互；`docs/reference/`：数据模型、统计口径、品牌与测试规范；`docs/how-to/`：实施、验收和签名步骤；`docs/adr/`：长期架构决策。
- `.github/workflows/ci.yml`：GitHub Actions 自动化门禁。

新增文件放入职责最接近的现有目录。MVP 继续使用单 `app` 模块，不为未确认需求预建模块或抽象层。

## 本仓库的分支与 worktree

- 任何代码或文档修改都必须从最新 `main` 创建独立分支，禁止直接在 `main` 上工作或提交。
- `/home/yuncun/111project/noteapp all-in-one` 是主工作目录。创建分支前若本地只有 `main`，第一个功能分支可以与 `main` 串行共用该目录。
- 创建分支前若已经有两个或更多本地分支，任何新分支都必须使用 `git worktree add` 创建独立工作目录；一个额外分支对应一个 `worktree`，不得复用或切换现有功能分支的工作目录。
- 本仓库的新 `worktree` 放在项目上一级目录 `/home/yuncun/111project/`，使用 `noteapp-<任务简称>` 形式的唯一目录名，并从最新 `main` 派生。
- 创建前必须检查 `git status`、本地分支和 `git worktree list`。工作目录存在未提交改动时不得切换分支、暂存到其他任务或移动这些改动。
- Pull Request 保留分支上的独立提交，禁止提前压缩提交；CI 通过后使用普通 merge commit 合并，禁止 squash merge 或 rebase merge。
- PR 合并后，主工作目录中的功能分支切回 `main` 再删除；独立工作目录中的功能分支先移除对应 `worktree`，再删除本地分支，并清理远程分支。

## 技术基线

- Kotlin `2.0.21`、Android Gradle Plugin `8.7.3`、Jetpack Compose 和 Material 3。
- `applicationId = "com.yuncun.noteapp"`，`minSdk = 31`，`compileSdk = 35`，`targetSdk = 35`。
- Java/Kotlin 字节码目标为 17；Gradle 使用 JDK 17 或 21，CI 使用 Temurin JDK 17。不要使用当前构建链尚不支持的 JDK 25。
- 不在 `gradle.properties` 中提交机器专属的 `org.gradle.java.home`；本地 JDK 通过 Android Studio、`JAVA_HOME` 或用户级 Gradle 配置选择。
- 本地数据使用 Room 和 DataStore；导航、生命周期状态和异步任务使用 Navigation Compose、ViewModel 与 Coroutines；JSON 使用 kotlinx.serialization。
- 已批准的依赖以 `gradle/libs.versions.toml` 和 `app/build.gradle.kts` 为准。新增依赖仍须按全局规范先说明收益并取得用户同意。
- 只适配手机竖屏并跟随系统浅色或深色主题，不引入网络账号或云端状态。

## 业务事实来源

实现或审查业务行为时按以下文档核对，不在代码或本文件中另造口径：

- [产品需求文档](docs/product/产品需求文档.md)：功能范围、业务规则和验收标准。
- [页面与交互说明](docs/product/页面与交互说明.md)：导航、页面状态和用户反馈。
- [数据模型与统计口径](docs/reference/数据模型与统计口径.md)：实体、时间、提醒、统计和备份契约。
- [代码测试规范](docs/reference/代码测试规范.md)：测试分层、关键路径和交付门禁。
- [M8 发布验收与 APK 签名](docs/how-to/M8发布验收与APK签名.md)：设备验证、签名和正式交付条件。
- [技术栈 ADR](docs/adr/0001-采用原生Android技术栈.md)：原生 Android 技术选型及边界。

文档之间出现矛盾时，不自行选择口径；先对照当前代码和测试定位差异，再请用户确认唯一事实来源应如何修正。

## 实现约束

- Kotlin 代码标识符沿用数据模型中的英文命名；代码注释和项目文档使用中文。
- 时间逻辑必须显式处理设备当前时区、自然日、跨午夜和夏令时，不使用固定 24 小时替代自然日长度。
- 随机抽取只能使用启用候选，并允许注入可控随机源；测试不得依赖真实随机结果。
- 时间记录必须拒绝无效范围和任意重叠，但允许首尾相接；编辑记录时排除自身。
- 日程提醒必须正确处理权限不足、关闭、修改、删除、过期、重启和时区变化，不能把“已保存”错误显示为“提醒已生效”。
- 番茄钟使用绝对截止时间恢复状态，阶段切换需要用户确认，过期通知不补发，也不自动创建时间记录。
- JSON 导入先完整校验，再在事务中整体替换；未知版本、字段错误、悬空引用或重叠记录必须在写入前拒绝，失败不得留下部分数据。
- Room Schema、JSON `formatVersion` 或公共数据结构变化属于兼容性变更，必须同时更新迁移或导入策略、测试和对应参考文档。

## 测试与验证

变更前先从[代码测试规范](docs/reference/代码测试规范.md)选择能发现风险的最低测试层级。业务功能、缺陷修复、迁移和系统适配必须覆盖正常、边界、无效输入与失败恢复；缺陷修复先提交能稳定复现问题的失败用例。

常用门禁：

```bash
# 快速验证纯 Kotlin 规则
./gradlew testDebugUnitTest

# 完整本地自动化门禁
./gradlew test
./gradlew lint
./gradlew assembleDebug assembleDebugAndroidTest
./gradlew assembleRelease

# 需要已连接 API 31+ 模拟器或实体设备
./gradlew connectedDebugAndroidTest

# 提交前仓库检查
git diff --check
git status --short
```

- GitHub CI 对 `main` 的 push 和 Pull Request 运行 `git diff --check`、本地单元测试、Lint、Debug/测试 APK 构建及未签名 Release 构建；CI 不运行设备仪器测试。
- 日期展开、重叠检测、随机过滤、提醒调度、番茄状态恢复、统计、备份导入和数据库迁移属于发布关键路径，必须覆盖适用边界。
- 测试必须可重复、相互隔离，不依赖真实当前时间、不可控随机、执行顺序、外网、个人目录、固定 AVD 名称或已有设备数据。
- 通知准点性、后台运行、进程终止、设备重启、时区变化和厂商省电策略只能由 Android 12+ 实体手机最终验收；自动化测试不能替代。
- 纯文档修改无需运行 Gradle 测试，但必须检查路径、链接、命令和事实与代码一致，并运行 `git diff --check`。

## 安全与发布

- release keystore、密码、签名配置、本地 SDK 路径、个人数据、备份文件和 APK 不得提交 Git。
- 手动 JSON 备份是 UTF-8 明文个人数据；涉及导出时必须保留风险提示并使用系统文件选择器。
- 不使用 Debug keystore 冒充 release 签名，不把未签名 APK 描述为可正式交付版本。
- 界面变更的 Pull Request 附截图或录屏；行为、数据格式或统计口径变化同步更新中文文档。
- 提交信息使用 Conventional Commits，例如 `feat:`、`fix:`、`test:`、`docs:`；一次提交只处理一个可独立说明和验证的目标。
