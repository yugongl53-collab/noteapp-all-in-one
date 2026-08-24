# Repository Guidelines

## 项目结构与模块组织

本仓库当前处于一站笔记的产品规划阶段，尚无应用源码或可安装版本。`README.md` 是项目入口；`docs/product/` 存放产品需求和页面交互；`docs/reference/` 记录数据模型与统计口径；`docs/how-to/` 提供 MVP 实施和验证步骤；`docs/adr/` 保存长期有效的架构决策。新增文档应放入最贴近其用途的现有目录，不要为尚未实现的模块预建空目录。

## 构建、测试与本地开发

技术基线已确定为 Kotlin、Jetpack Compose、Material 3、`minSdk 31` 和 `compileSdk`/`targetSdk 37`，具体取舍见[技术栈 ADR](docs/adr/0001-采用原生Android技术栈.md)。当前仍没有 Gradle 构建配置或自动化测试命令。提交前至少运行：

```bash
rtk git diff --check
rtk git status --short
rtk rg '\]\([^)]*\.md' README.md docs
```

前两项检查 Markdown 空白错误和变更范围；最后一项列出文档链接，需人工确认目标存在。创建应用工程时必须同时提交 Gradle Wrapper，并把真实的构建、运行和测试命令补充到本文件及 `README.md`；不得要求全局安装 Gradle。

开发使用 Android Studio 稳定版及其内置 JDK 和 Android SDK。模拟器验收需要 API 31 或更高版本的系统镜像与 AVD，通知、准点提醒和后台计时还必须在实体 Android 12 或更高版本手机上验证。用户保管的 release keystore、密码和本地 SDK 路径不得提交 Git。

## 编写风格与命名约定

文档正文使用中文，保持短段落、描述性标题和可执行说明。路径、命令、字段名与标准术语保留原文，例如 `TimeRecord`、`startAt` 和 `work_study`。文件名采用能表达单一主题的中文名称；代码标识符沿用数据模型中的英文命名。不要把规划写成已实现行为，也不要复制可链接到唯一事实来源的规则。

代码必须按函数或关键逻辑段补充简短注释，重点解释业务流程、边界条件、异常处理和外部接口适配，不逐行重复代码。优先采用单 `app` 模块和按功能分包；MVP 不为假设中的未来扩展预建模块或抽象层。

## 依赖约定

已批准使用 Room、DataStore、Navigation Compose、ViewModel、Coroutines 和 kotlinx.serialization。它们分别减少手写 SQLite 与迁移、设置持久化、导航、生命周期状态、异步任务和 JSON 编解码代码。新增名单之外的依赖前，必须说明能简化的内容和程度，并由用户决定是否安装。

## 测试与验证要求

当前验证以文档一致性为主：核对链接、示例、默认值、MVP 边界，以及 `README.md` 的导航入口。未来实现功能时，先用测试复现规则或缺陷，再提交代码；日期展开、记录重叠、随机候选过滤、提醒调度、备份导入和统计计算必须有自动化测试。测试文件命名应遵循最终选定框架的惯例，并在创建测试配置时记录覆盖率门槛。

## 提交与 Pull Request

现有历史采用 Conventional Commits 风格，如 `docs: 建立 NoteApp 产品规划文档`。继续使用 `docs:`、`feat:`、`fix:`、`test:` 等类型，主题使用简洁中文且一次提交只处理一个目标。Pull Request 应说明变更目的、验证结果和文档影响；解决 issue 时在正文中使用 `Closes #编号`。界面变更需附截图或录屏，行为或数据口径变化需同步更新相关文档。
