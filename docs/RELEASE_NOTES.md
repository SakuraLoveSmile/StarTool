# StarTool 实施 / 验收记录

> 沿用 T0–T4 编号。每项完成时更新状态与证据。

## T0 工程与技术基线 —— 已完成

- 单模块 `app`，Kotlin 2.2.20 + AGP 8.13.1 + Gradle 8.14 + JDK 17，compileSdk/targetSdk 36，minSdk 26。
- Compose BOM 2025.10.01，Material 3；Room 2.8.3 + KSP 2.2.20-2.0.4（schema 导出到 `app/schemas/`）。
- applicationId `app.startool.android`，versionName 0.1.0，versionCode 1。
- e2e 构建类型 `app.startool.android.e2e` 用于仪器测试隔离。
- Manifest 无权限申请；`allowBackup=false`、`fullBackupOnly=false`。
- domain/ 接口冻结：`HourlyKey`、`HourlyEntry`、`EntryDraft`、`ValidatedBackup`、`ImportPlan`、`ImportConflict`、`ConflictChoice`、`ImportReport`、`OpResult`、`ImportApplyResult`、`StarToolRepository`、`BackupFileGateway`。
- 冻结视觉令牌 `StarToolColors`/`StarToolType`/`StarToolDimens`；共享组件 `DateBar`（含滚轮日期选择）、`StarRating`、`LoadingView`/`ErrorView`/`EmptyView`。
- `AppContainer`：手动 DI，含 repository（暂为 Fake）、selectedDateStore、clock、fileGateway（SAF）、maintenance 锁、mainTabRequests 导航总线。
- 冻结 testTag 清单见 docs/CONTRACT.md §6。
- 验证：`assembleDebug` 构建通过；小米 10（Android 16）安装并启动到 MainActivity 成功。

## T1 记录与编辑 —— 实现中

- 数据层（Room）：子 agent A 施工中。
- 记录页 + 共享评分面板：子 agent B 施工中。

## T2 回看与曲线 —— 实现中

- 子 agent C 施工中。

## T3 备份、恢复与冲突比较 —— 实现中

- 子 agent D 施工中。

## T4 验证 —— 待开始

- [ ] `./gradlew testDebugUnitTest lintDebug assembleDebug`
- [ ] `./gradlew connectedE2eAndroidTest`（小米 10）
- [ ] 真机手测清单（见计划 §9.3）
- [ ] 截图归档
- [ ] APK SHA-256 / 签名指纹记录

## 已知问题 / 待验证项

-（持续更新）
