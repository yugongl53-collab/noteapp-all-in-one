# Repository Guidelines

## 项目结构与模块组织

本仓库已完成 M0 工程骨架、M1 数据层与公共规则、M2 灵感闭环、M3 学期日程闭环、M4 日程提醒与权限适配和 M5 事件池与番茄钟；M6 至 M7 的业务页面或系统能力仍待实现。`app/src/main/` 存放 Android 源码，`app/src/test/` 存放纯 Kotlin 测试，`app/src/androidTest/` 存放 Room、DataStore、Compose 与 Android 集成测试，`app/schemas/` 保存 Room Schema 快照。`README.md` 是项目入口；`docs/product/` 存放产品需求和页面交互；`docs/reference/` 记录数据模型与统计口径；`docs/how-to/` 提供 MVP 实施和验证步骤；`docs/adr/` 保存长期有效的架构决策。新增内容应放入最贴近其用途的现有目录，不要为尚未实现的模块预建空目录。

## 构建、测试与本地开发

技术基线已确定为 Kotlin、Jetpack Compose、Material 3、`minSdk 31` 和 `compileSdk`/`targetSdk 35/37`，具体取舍见[技术栈 ADR](docs/adr/0001-采用原生Android技术栈.md)。项目使用内置的 Gradle Wrapper 执行所有构建与测试。提交前至少运行：

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
rtk git diff --check
rtk git status --short
```

开发使用 Android Studio 稳定版及其内置 JDK 和 Android SDK。模拟器验收需要 API 31 或更高版本的系统镜像与 AVD，通知、准点提醒和后台计时还必须在实体 Android 12 或更高版本手机上验证。用户保管的 release keystore、密码和本地 SDK 路径不得提交 Git。

## 编写风格与命名约定

文档正文使用中文，保持短段落、描述性标题和可执行说明。路径、命令、字段名与标准术语保留原文，例如 `TimeRecord`、`startAt` 和 `work_study`。文件名采用能表达单一主题的中文名称；代码标识符沿用数据模型中的英文命名。不要把规划写成已实现行为，也不要复制可链接到唯一事实来源的规则。

代码必须按函数或关键逻辑段补充简短注释，重点解释业务流程、边界条件、异常处理和外部接口适配，不逐行重复代码。优先采用单 `app` 模块和按功能分包；MVP 不为假设中的未来扩展预建模块或抽象层。

## 依赖约定

已批准使用 Room、DataStore、Navigation Compose、ViewModel、Coroutines 和 kotlinx.serialization。它们分别减少手写 SQLite 与迁移、设置持久化、导航、生命周期状态、异步任务和 JSON 编解码代码。新增名单之外的依赖前，必须说明能简化的内容和程度，并由用户决定是否安装。

## 测试与验证要求

测试的详细规则以[代码测试规范](docs/reference/代码测试规范.md)为唯一事实来源。当前 Android 工程已配置本地单元测试、仪器测试、Lint 和构建门禁，其中仪器测试需要已连接的 API 31 或更高版本设备；任何业务功能、缺陷修复、数据迁移或外部接口适配都必须先定义正常、边界和失败用例，再实现代码。

提交代码前至少满足以下门禁：

- 新增或修改的业务规则有对应的自动化测试；修复缺陷时先提交能稳定复现问题的失败用例，再修复至通过。
- 优先使用本地单元测试覆盖纯 Kotlin 规则；Room、Compose、系统权限、文件和 Android 生命周期行为放到相应的集成测试或仪器测试，不能用低层测试冒充系统验收。
- 测试必须可重复、可独立运行，不依赖真实当前时间、随机结果、执行顺序、外网或固定设备数据；时钟、时区和随机源应可控。
- 日期展开、记录重叠、随机候选过滤、提醒调度、备份导入、统计计算和数据库迁移属于发布关键路径，必须覆盖正常、边界、无效输入和失败恢复。
- 受影响的单项测试通过后，还必须运行所属模块测试和仓库规定的完整回归门禁；不得通过删除测试、放宽断言、忽略失败或无限重试制造通过结果。
- 新增测试依赖仍受“依赖约定”约束；覆盖率只用于发现缺口，不能替代对业务场景和断言质量的审查。

工程创建后，必须把真实可运行的单元测试、仪器测试、Lint 和构建命令同步到本文件、`README.md` 与代码测试规范；测试结果、未执行项及原因应写入提交或 Pull Request 说明。

## 提交与 Pull Request

现有历史采用 Conventional Commits 风格，如 `docs: 建立 NoteApp 产品规划文档`。继续使用 `docs:`、`feat:`、`fix:`、`test:` 等类型，主题使用简洁中文且一次提交只处理一个目标。Pull Request 应说明变更目的、验证结果和文档影响；解决 issue 时在正文中使用 `Closes #编号`。界面变更需附截图或录屏，行为或数据口径变化需同步更新相关文档。
