# StarTool 并行施工合同（T0 冻结）

本文档是各子 agent 的唯一共享合同。任何对共享文件的变更只能由主 agent 执行。

## 1. 环境

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd "/Users/sakurasep/Documents/Code/Project/Personal Project/StarTool"
```

- 构建：`./gradlew assembleDebug`（或 `compileDebugKotlin` 只编译）。
- 单元测试：`./gradlew testDebugUnitTest`。
- 设备测试：`./gradlew connectedE2eAndroidTest`（e2e 构建类型，applicationId `app.startool.android.e2e`，与试用包 `app.startool.android` 隔离）。
- 本机已装 Android SDK 36、Build Tools 36.0.0；已连接小米 10（adb 序列号 8085d06e，Android 16 / API 36）。
- **禁止**修改全局 Java 配置；**禁止**降低 minSdk/删除测试来让构建通过；构建失败先报告给主 agent。

## 2. 模块独占边界（严格遵守）

| 包/路径 | 所有者 |
|---|---|
| `domain/`、`ui/theme/`、`ui/components/`、根包（AppContainer/MainActivity/StarToolRoot/SelectedDateStore/StarToolApplication）、`fake/`、Gradle 配置、`docs/` | 主 agent（只读，禁止改） |
| `app.startool.android.data`（data/） | 子 agent A |
| `app.startool.android.ui.record` | 子 agent B |
| `app.startool.android.ui.history` | 子 agent C |
| `app.startool.android.backup`、`app.startool.android.ui.backup` | 子 agent D |
| `app/src/androidTest/**` | 子 agent E 主（验收测试放 `acceptance` 包）；A 可放 `acceptance/data` 子包的数据库仪器测试 |
| `app/src/test/**` | 各 agent 在自己模块下放 `test/`（test/java/app/startool/android/<模块>），互相不干扰 |

- 跨模块需求（新接口、新共享组件）写进自己模块的 TODO 注释并在汇报中列出，由主 agent 统一处理。
- 禁止新建顶层模块、禁止引入新第三方库（尤其图表库、Hilt、导航框架、Gson/Moshi——JSON 用 android 平台 `org.json`）。
- 现有文件签名冻结：`RecordScreen(container, showSnackbar, modifier)`、`HistoryScreen(container, showSnackbar, modifier)`、`SettingsScreen(container, onBack, showSnackbar, modifier)` —— 参数列表不得改。

## 3. 核心接口（已在代码中冻结）

`app.startool.android.domain`：
`HourlyKey(date, hour)`、`HourlyEntry`、`EntryDraft`、`ValidatedBackup`、`ImportPlan`、`ImportConflict`、`ConflictChoice`、`ImportReport`、`BackupSnapshot`、`OpResult`（Success/NoChange/BlockedByMaintenance/Failure）、`ImportApplyResult`（Completed/PreviewStale/Failure）、`StarToolRepository`、`BackupFileGateway`。

`AppContainer` 提供：`repository`、`selectedDateStore`（共享所选日期）、`clock`、`fileGateway`（SAF）、`maintenance.busy: StateFlow<Boolean>` 与 `maintenance.runExclusive { }`、`appScope`。

`FakeStarToolRepository`（`app.startool.android.fake`）已实现全部接口，可直接驱动 UI。

## 3.1 共享评分面板

`ScoreEditorSheet` 放在 `ui/components/`（主 agent 名下），但**由子 agent B 编写实现**：
签名冻结为
`fun ScoreEditorSheet(draft: EntryDraft, existingEntry: HourlyEntry?, saving: Boolean, deleting: Boolean, maintenanceBusy: Boolean, onDraftChange: (EntryDraft) -> Unit, onSave: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit)`（B 可加默认参数，不可改已有参数语义）。
面板内部包含标题、两项 ScoreInput（滑杆 0–10 整数吸附 + 加减按钮 + "设为 0 分"）、备注框（≤200 码点+计数）、保存/删除按钮、未保存改动确认。B 实现后 C 直接复用，不要在 ui/history 里另写编辑面板。

## 4. 时间与分数规则（不可变通）

- 记录键 = 本地日历日期 + 小时 0–23；`13:00` 属 13:00–13:59。
- 分数 0–10 整数；0 分是合法值，与"未选择"（null）严格区分。

- 备注 ≤200 Unicode 码点，可空字符串。
- 未来时段禁止新建；因导入/系统时间导致已存在的未来记录可看可改可删。
- 保存成功才提示"已保存"；失败保留输入可重试。
- 时间戳用 UTC Instant（createdAt/updatedAt），仅用于备份比较。

## 5. 视觉令牌（只用这些，不自造）

`StarToolColors`：Background `#FAF8F3`、Surface `#FFFFFF`、TextPrimary `#24272B`、TextSecondary `#62666D`、Primary `#6554A4`（主操作/精神）、Physical `#237A68`（身体）、Error `#B3261E`、CardBorder `#DDDAD3`。
`StarToolType`：PageTitle/HourTitle/Body/Caption。
`StarToolDimens`：Space 4/8/12/16/24dp、页面边距 16dp、卡片 padding 16dp/间距 12dp/圆角 16dp/边框 1dp、触控目标 ≥48dp、编辑面板顶部圆角 24dp、图表高 240dp、内容最大宽 560dp。
固定浅色主题；最小触控 48dp；文字跟随系统缩放。

## 6. 冻结测试标识（testTag，全部小写+下划线）

导航/共享：`nav_record`、`nav_history`、`settings_entry`、`date_prev`、`date_next`、`date_today`、`date_picker`、`date_picker_confirm`、`date_picker_cancel`、`loading_view`、`error_view`、`error_retry`、`empty_view`、`empty_action`。
记录页（B）：`hour_list`、`hour_<0..23>`（如 `hour_13`）、`edit_sheet`、`sheet_date_hour`、`mental_slider`、`mental_plus`、`mental_minus`、`mental_set_zero`、`mental_score_text`、`physical_slider`、`physical_plus`、`physical_minus`、`physical_set_zero`、`physical_score_text`、`note_field`、`note_counter`、`save_entry`、`delete_entry`、`discard_dialog`、`discard_confirm`、`discard_keep_editing`、`recorded_count_text`。
回看页（C）：`history_chart`、`chart_legend_mental`、`chart_legend_physical`、`history_count_text`、`history_list`、`history_item_<0..23>`、空态用共享 `empty_view`/`empty_action`。
设置/备份（D）：`settings_export`、`settings_import`、`settings_about`、`import_preview`、`import_summary_text`、`conflict_list`、`conflict_item_<index>`、`conflict_keep_local_<index>`、`conflict_use_backup_<index>`、`confirm_import`、`import_result`、`export_progress`、`import_progress`。

新增 testTag 只能在自己模块内、且以模块前缀开头（`record_`/`history_`/`backup_`/`settings_`）。

## 7. 并行期间的构建纪律

- 多个 agent 同时跑 Gradle 会互相等待锁——属于正常现象，命令会排队完成，不要误判为挂死。
- 跑 `./gradlew` 时加 `--console=plain`，失败时把 `tail` 的完整错误贴进汇报。
- 同一文件最多重试 3 次编辑；不行就记录现场并在汇报中说明。
- 不得 `git commit`；主 agent 统一提交。不得删除/改名他人文件。
