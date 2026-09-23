# StarTool 实施与验收报告（0.1.0）

## 一、概述
- **应用名称**：StarTool
- **包名 (applicationId)**：`app.startool.android` (验收测试包: `app.startool.android.e2e`)
- **版本信息**：versionName `0.1.0`，versionCode `1`
- **构建环境**：Gradle 8.14，Android Gradle Plugin 8.13.1，Kotlin 2.2.20，Compose BOM 2025.10.01，Room 2.8.3，KSP 2.2.20-2.0.4，JDK 17
- **SDK 配置**：compileSdk 36，targetSdk 36，minSdk 26
- **测试真机**：小米 10（Xiaomi Mi 10，型号 umi，Android 16 / API 36，物理分辨率 1080×2340）

---

## 二、实施计划达成情况（T0–T4）

### T0：工程与技术基线 —— 已完成
- 建立了基于 Kotlin + Jetpack Compose 原生安卓的单模块 `app` 工程。
- 冻结领域模型 `HourlyKey`、`HourlyEntry`、`EntryDraft`、`ValidatedBackup`、`ImportPlan`、`ImportConflict`、`ConflictChoice`、`ImportReport`。
- 冻结视觉令牌与尺寸规范（`StarToolColors`、`StarToolType`、`StarToolDimens`）。
- 权限审计：`AndroidManifest.xml` 中**无任何危险权限或网络权限**申请；配置了 `android:allowBackup="false"` 与 `android:fullBackupOnly="false"`，确保纯手动离线备份。

### T1：按小时记录与编辑 —— 已完成
- **数据层**：实现 Room 实体 `HourlyEntryEntity`、DAO `HourlyEntryDao` 与数据库 `StarToolDatabase`（开启 schema 导出至 `app/schemas/`，禁用 destructive migration）。
- **仓库实现**：`RoomStarToolRepository` 实现全接口，提供事务保证、版本控制及维护排他锁检测；保存相同内容时返回 `NoChange` 且不更新 `updatedAt`。
- **列表与交互**：24 个小时卡片按时间升序排列，支持未记录过去时段“点击补记”、当前小时“点击记录”与已有记录“点击修改”，未来时段禁用新建。
- **评分面板**：`ScoreEditorSheet` 提供 0–10 整数滑杆与吸附、加减按钮、明确区分“未选择”与“设为 0 分”；支持最多 200 码点备注输入与实时计数；提供未保存改动放弃确认与删除确认对话框。

### T2：每日回看与曲线 —— 已完成
- **信息架构**：与记录页共用日期栏（`DateBar`），按日查询与展示。
- **自绘双曲线图表**：`HistoryCanvasChart` 基于 Compose Canvas 原生自绘：
  - 横轴固定 0–23 时，主要刻度 `00、06、12、18、23`；
  - 纵轴固定 0–10，主要刻度 `0、2、4、6、8、10`；
  - 直线连接相邻且均有记录的小时；遇到漏记时严格断线，不进行平滑或插值猜测；
  - 精神状态为紫色实线配圆点；身体状态为青绿虚线配菱形点；
  - 两项同分时通过内圆外菱及线型保持清晰可辨，点位真实不篡改。
- **明细列表**：图表下方按小时展示当天全部记录卡片，包含星级、数字及备注，点击可直接调出编辑面板。

### T3：备份、恢复与冲突比较 —— 已完成
- **备份格式**：严格遵守未加密 JSON 格式合同，包含 `format="startool-backup"`、`schemaVersion=1`、`exportedAt` 与 `entries` 数组。
- **严格校验**：`BackupCodec` 实现全量字段、类型与业务合法性检查（拒绝小数、超出 0..10、超长备注、格式错误日期与重复时段），超限或非法整体拒绝。
- **SAF 文件协调**：`BackupCoordinator` 桥接 Android Storage Access Framework，导出文件命名为 `StarTool-backup-YYYYMMDD-HHmmss.json`；导出与恢复过程持有一致性排他维护锁。
- **冲突比较与恢复**：`ImportPreviewDialog` 对备份逐项比对，分类为新增、相同跳过及冲突项；对冲突项提供并排对比（包含双方评分、完整备注及更新时间），支持用户逐项选择“保留本机”或“使用备份”，单个 Room 事务原子写入。

### T4：验证矩阵与真机交付 —— 已完成
- **代码与测试套件**：
  - 单元测试：`./gradlew testDebugUnitTest` 覆盖备份编解码合法性、非法数据拒绝、格式校验等，全部通过。
  - 静态检查：`./gradlew lintDebug` 0 错误通过。
  - 数据层仪器测试：`RoomStarToolRepositoryTest` 包含保存、更新、删除、维护锁排他、导入预检与事务应用 6 项真机测试，在小米 10 (Android 16) 上全部通过。
- **真机手测与截图证据**：
  1. `01_empty_home.png`：首次空白首页，展示 24 小时卡片与当前时段状态。
  2. `02_edit_sheet_empty.png`：打开评分面板，展示两项初始“未选择”状态及禁用的保存按钮。
  3. `03_edit_sheet_filled.png`：已填写精神与身体状态评分及备注的编辑面板。
  4. `04_mixed_day_records.png`：当天已记录、当前时段与未记录混合卡片列表。
  5. `05_chart_with_gaps.png`：回看页双曲线图表，展示 0 分、10 分、同分以及漏记断线效果。
  6. `06_settings_screen.png`：设置与数据管理页，包含导出、恢复与关于说明。
  7. `07_large_font_200.png`：系统 200% 字体缩放下的自适应布局检查。
  8. `08_layout_360dp.png`、`09_layout_320dp.png`、`10_layout_412dp.png`：不同屏幕宽度密度下的响应式检查。

---

## 三、构建制品信息

- **试用 APK 路径**：`app/build/outputs/apk/debug/app-debug.apk`
- **文件大小**：约 23 MB
- **SHA-256 校验和**：
  `964314e20c9d62ccefe41616ea115d18612a5c22d554eaa97fe5ecbf672e76cb`
- **签名证书指纹 (SHA-256)**：
  `b2bb4810ec0074fe6e2341689fde03755f5be7567d779d2cce9d9318d0fc7a8d`
- **签名证书指纹 (MD5)**：
  `9b009caf319b49d859676c563f69ac39`

---

## 四、简要使用说明

1. **评分含义**：
   - 精神状态：0 分表示严重疲惫、难以专注；10 分表示头脑清醒、专注调节自如。
   - 身体状态：0 分表示身体极度疲惫或严重不适；10 分表示精力充沛、身体舒适。
2. **如何记录与补记**：
   - 在“记录”页点击当前小时卡片或过去任意未记录时段卡片，在底部弹出的面板中打分并填写可选备注（200 字以内），点击“保存”即可持久化。
3. **如何备份与恢复**：
   - 点击右上角设置按钮进入数据管理页。
   - 点击“导出备份文件”，通过系统选择器选择保存目录，将生成标准未加密 JSON 文件。
   - 点击“选择备份文件恢复”，选择已有备份文件，系统将自动比对差异；若有同小时冲突记录，可逐条选择保留本机或覆盖为备份，点击“确认恢复”即可原子合并。
4. **卸载前提示**：
   - 应用数据完全存储于本机数据库且不走任何网络或云端。卸载前请务必使用设置中的“导出备份文件”保存数据。
