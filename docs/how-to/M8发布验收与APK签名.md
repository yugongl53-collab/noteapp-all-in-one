# M8 发布验收与 APK 签名

## 1. 用途与当前结论

本文供准备“一站笔记”首版安装包的开发者使用，解决从启动 AVD、运行全量门禁到使用用户密钥签名并核对 APK 的单一交付问题。业务验收数据以[MVP 执行计划与验证指南](MVP实施与验证指南.md#9-最小验收数据集)为准，测试规则以[代码测试规范](../reference/代码测试规范.md)为准。

2026-08-25 已在 `NoteApp_API35` AVD（API 35、手机竖屏）完成自动化验收：83 项本地单元测试和 43 项仪器测试均为 0 失败，Lint、Debug APK 与未签名 Release APK 构建成功。当前机器没有 API 31 系统镜像、实体 Android 12+ 手机或用户 release keystore，因此最低版本兼容、真实通知/后台/重启行为和签名安装仍未完成；这些项目不能由 API 35 模拟器或 Debug 签名替代。

## 2. 前置条件

- Android Studio 稳定版、JDK 17 或 JDK 21，并安装项目当前使用的 Android SDK 35、Build Tools、Platform Tools 与 Emulator；当前构建链不支持 JDK 25。
- 一个 API 31 AVD，用于最低支持版本回归；更高 API AVD只能补充验证，不能替代 API 31 边界。
- 一台 Android 12 或更高版本实体手机，用于通知、准点提醒、后台、省电策略、进程终止和设备重启验收。
- 用户自行创建并保管的 release keystore。密钥、密码、本地 SDK 路径和 APK 均不得加入 Git。
- 番茄钟不处于运行或暂停状态，且已准备 MVP 指南第 9 节的验收数据。

## 3. 启动 AVD 并运行自动化门禁

先列出并启动已创建的 AVD。`<AVD_NAME>` 是占位符，应替换为本机名称；命令会启动可见模拟器窗口。

```bash
${ANDROID_SDK_ROOT}/emulator/emulator -list-avds
${ANDROID_SDK_ROOT}/emulator/emulator -avd <AVD_NAME> -no-snapshot -no-audio
${ANDROID_SDK_ROOT}/platform-tools/adb devices -l
${ANDROID_SDK_ROOT}/platform-tools/adb shell getprop sys.boot_completed
${ANDROID_SDK_ROOT}/platform-tools/adb shell getprop ro.build.version.sdk
```

`sys.boot_completed` 预期为 `1`，设备状态预期为 `device`，API 31 验收时 SDK 值预期为 `31`。随后在仓库根目录按顺序执行：

```bash
./gradlew test
./gradlew lint
./gradlew connectedDebugAndroidTest
./gradlew assembleDebug
./gradlew assembleRelease
git diff --check
git status --short
```

测试报告位于 `app/build/reports/tests/`、`app/build/reports/lint-results-debug.html` 和 `app/build/reports/androidTests/connected/debug/`。Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`；未配置用户密钥时，Release 任务只产生 `app/build/outputs/apk/release/app-release-unsigned.apk`。

## 4. 人工全链路与界面验收

使用 MVP 指南第 9 节数据依次完成灵感、学期日程、提醒、事件池、番茄钟、时间记录、统计和 JSON 备份八条流程。每条流程至少验证正常保存、无效输入、删除或取消、应用重启后持久化和与相邻模块的数据一致性；导入失败不得产生部分替换，番茄钟不得自动生成 `TimeRecord`。

界面回归分别在浅色和深色主题下执行，并把系统字体调到默认与最大档。检查手机竖屏、空状态、长灵感、长事件名称、长备份摘要和页面滚动，确保主操作能够显示、滚动到达并点击。四大主 Tab（日程、灵感、工具箱、设置）应顺畅切换并支持状态恢复，“设置”页应正常进入权限与备份操作。

实体手机还必须记录设备型号、Android 版本和以下结果：通知权限授予与拒绝、精确闹钟权限授予与撤销、提醒准点性、后台番茄钟、进程终止、设备重启、时区变化和厂商省电策略。模拟器通过不能替代这些系统行为。

## 5. 数据、权限与隐私核对

- Room 当前数据库版本为 `1`，`app/schemas/` 已保存版本 1 快照。首版没有旧的已发布 Schema，因此无需迁移；以后提升数据库版本时必须提供从每个已发布版本到当前版本的迁移和仪器测试，不能使用破坏性回退。
- JSON 备份当前固定为 `formatVersion = 1`，未知版本、悬空引用、无效字段和重叠时间记录必须在写数据库前拒绝。
- Manifest 只声明通知、精确闹钟和开机重建所需权限；文件导入导出使用系统文件选择器，不申请宽泛存储权限。
- 自动云备份排除数据库与偏好；设备到设备迁移允许搬迁它们。手动导出的 JSON 是可直接读取的个人数据，导出前必须显示明文风险提示。

## 6. 使用用户 keystore 签名

先运行 `./gradlew assembleRelease` 生成未签名 APK，再使用已安装 Build Tools 中的 `apksigner`。`<BUILD_TOOLS_VERSION>`、`<KEYSTORE_PATH>`、`<KEY_ALIAS>` 和 `<OUTPUT_PATH>` 都是占位符。不要把密码写进命令、脚本、`local.properties` 或提交记录；省略密码参数，让工具在终端中安全提示输入。

```bash
${ANDROID_SDK_ROOT}/build-tools/<BUILD_TOOLS_VERSION>/apksigner sign \
  --ks <KEYSTORE_PATH> \
  --ks-key-alias <KEY_ALIAS> \
  --out <OUTPUT_PATH>/NoteApp-1.0.0.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk

${ANDROID_SDK_ROOT}/build-tools/<BUILD_TOOLS_VERSION>/apksigner verify --verbose --print-certs \
  <OUTPUT_PATH>/NoteApp-1.0.0.apk
sha256sum <OUTPUT_PATH>/NoteApp-1.0.0.apk
```

预期 `apksigner verify` 显示 `Verifies` 且至少存在一个签名者。交付记录必须保存 `versionName = 1.0.0`、`versionCode = 1`、SHA-256、签名证书摘要、验证设备和安装冒烟结果；APK 放在用户指定的仓库外目录，不提交 Git。

## 7. 常见失败处理

- `adb devices` 显示 `offline`：等待 AVD 完成开机；仍未恢复时执行 `${ANDROID_SDK_ROOT}/platform-tools/adb kill-server` 后重新检查，必要时从 AVD Manager 冷启动。
- `connectedDebugAndroidTest` 报告没有设备：确认设备状态为 `device`、API 不低于 31，并解锁实体手机和接受调试授权。
- 仪器测试失败：查看 `app/build/reports/androidTests/connected/debug/index.html`，先保留首个失败证据并修复原因，不用重试掩盖不稳定测试。
- `apksigner verify` 报 `DOES NOT VERIFY`：确认验证的是签名后的输出文件，不是 `app-release-unsigned.apk`；不得把 Debug keystore 当作 release keystore。
- Release 安装提示签名不一致：设备上已有同 `applicationId` 但不同证书的版本。先导出备份，再由用户确认是否卸载旧版本；卸载会删除应用本地数据。

## 8. 发布完成判定

只有 API 31 自动化与八条人工流程、实体手机系统行为、签名校验和安装冒烟全部通过，且 Git 中不存在密钥、密码、本地路径或 APK，才能把 M8 标记为完成。若任一外部前置条件缺失，应记录设备、命令、结果和阻断原因，保持为“待正式交付”，不能用未签名 Release 或 Debug APK 代替。
