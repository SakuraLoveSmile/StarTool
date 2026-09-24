# StarTool 检查更新与 Feedback 接入施工合同（T0 冻结）

本文档是各子模块与 subagent 的唯一契约，规定更新检查（T1）与 Feedback 接入（T2）的接口签名、数据格式、测试标识与边界责任。

## 1. 模块划分与代码归属

| 模块 / 路径 | 所有者 | 交付产物与职责 |
|---|---|---|
| `app.startool.android.update` | 更新 subagent | UpdateManifest、UpdateCheckResult、UpdatePreferenceStore、UpdateChecker、Gradle 构建任务 `generateUpdateManifest` 及对应 JVM 单元测试 |
| `packages/web/`（Feedback 仓库） | Web 组件 subagent | 宿主扩展接口（`sessionStore`、`handleBackPressed`、`onOpenExternal`、`onSaveAttachment`）、事件派发、对应测试与产物打包 |
| `app.startool.android.feedback` | Android 适配 subagent | KeystoreSessionStore、ActivityCaptureProvider、FeedbackNativeBridge、WebViewAssetLoader 宿主壳、FeedbackFab 悬浮球、脱敏诊断采集及对应测试 |
| 共享入口与根界面（`StarToolRoot`、`SettingsScreen`、`MainActivity`、`AppContainer`、Manifest、Gradle 配置） | 主 agent | 统一注入依赖、挂载悬浮球与更新提示横幅、页面视觉一致性、产物集成与整体端到端验收 |

---

## 2. T1：更新检查契约

### 2.1 清单格式（`startool-update.json`）
```json
{
  "schemaVersion": 1,
  "applicationId": "app.startool.android.gemini",
  "versionCode": 2,
  "versionName": "0.2.0",
  "minSdk": 26,
  "notes": "更新说明内容...",
  "releaseUrl": "https://github.com/SakuraLoveSmile/StarTool/releases/tag/v0.2.0"
}
```

### 2.2 核心数据模型与检查结果
```kotlin
data class UpdateManifest(
    val schemaVersion: Int,
    val applicationId: String,
    val versionCode: Int,
    val versionName: String,
    val minSdk: Int,
    val notes: String,
    val releaseUrl: String,
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(
        val manifest: UpdateManifest,
        val currentVersionCode: Int,
        val currentVersionName: String,
    ) : UpdateCheckResult

    data class UpToDate(
        val currentVersionCode: Int,
        val currentVersionName: String,
    ) : UpdateCheckResult

    data object NoReleaseFound : UpdateCheckResult

    data class IncompatibleSystem(
        val manifest: UpdateManifest,
        val currentSdk: Int,
        val minSdk: Int,
    ) : UpdateCheckResult

    data class NetworkError(
        val message: String,
        val cause: Throwable? = null,
    ) : UpdateCheckResult

    data class InvalidManifest(
        val message: String,
    ) : UpdateCheckResult
}
```

### 2.3 校验与升级判断规则
1. 校验 `applicationId == BuildConfig.APPLICATION_ID`，不匹配返回 `InvalidManifest("应用标识不符")`。
2. 校验 `releaseUrl` 属于 `https://github.com/SakuraLoveSmile/StarTool/`，否则返回 `InvalidManifest("非官方下载链接")`。
3. 校验 `minSdk <= Build.VERSION.SDK_INT`，不满足返回 `IncompatibleSystem`。
4. 按整数 `versionCode` 比对：`manifest.versionCode > currentVersionCode` 即为 `UpdateAvailable`，否则为 `UpToDate`。
5. HTTP 404 或 GitHub Release 列表为空返回 `NoReleaseFound`，不误报最新版。
6. 自动检查节流：两次自动检查之间间隔需 $\ge 24$ 小时（86400 秒）；若版本被标记为“稍后提醒”，至少 24 小时内不弹出；若版本被标记为“忽略此版本”，自动检查不提示，但手动检查依然显示。
7. 手动检查：随时可用，若有正在进行的请求则合并，成功与失败均以清晰文字展示，包含重试入口。

---

## 3. T2：Feedback 接入契约

### 3.1 Web 组件宿主接口（TypeScript）
```ts
export interface FeedbackHostSession {
  accessToken: string;
  expiresAt: number | string;
}

export interface FeedbackSessionStore {
  loadSession(): Promise<FeedbackHostSession | null> | FeedbackHostSession | null;
  saveSession(session: FeedbackHostSession): Promise<void> | void;
  clearSession(): Promise<void> | void;
}

export interface FeedbackHostBridge {
  sessionStore?: FeedbackSessionStore;
  onPanelOpen?: (detail: { page?: string }) => void;
  onPanelClose?: () => void;
  onSaveAttachment?: (file: { name: string; mimeType: string; dataUrl?: string }) => void | Promise<void>;
  onOpenExternal?: (url: string) => void;
}
```
Web 组件公共方法：
- `handleBackPressed(): boolean`：若裁剪/涂鸦编辑器打开则关闭并返回 true；若反馈面板打开则关闭并返回 true；否则返回 false。

### 3.2 安全存储契约（Android Keystore）
- 隔离维度：`apiBase + "#" + appId` 作为 key。
- 内容加密：经 Android Keystore AES-GCM 保护，密文保存在 SharedPreferences 中。
- 绝不持久化明文密码。
- 会话过期或收到 401 自动清除。

### 3.3 截图与图像尺寸限制
- 统一限制：最长边 $\le 2048$ px，总像素 $\le 4,000,000$（400万像素），单图体积 $\le 5$ MiB。
- 悬浮球点击时先截图再打开面板；从设置打开不自动截图。
- 若已有未提交草稿，打开优先恢复草稿；用户点击“重拍”失败时保留原有草稿与截图。
- 敏感区与自身 UI 保护：截图前隐藏悬浮球与 Feedback 本身；多层 Dialog 按层级合成。

### 3.4 诊断信息收集白名单
- 仅允许包含：
  - 应用版本：`versionName`, `versionCode`
  - 系统信息：`Build.VERSION.RELEASE`, `Build.VERSION.SDK_INT`
  - 设备信息：`Build.MANUFACTURER`, `Build.MODEL`
  - 脱敏错误摘要（若有）
- **严禁采集**：数据库内容、星级评分、个人备注、备份文件、系统全量 logcat。

---

## 4. 冻结测试标识（testTag）

- 更新相关（前缀 `update_` / `settings_update_`）：
  - `settings_update_card`: 设置页中的检查更新卡片
  - `settings_update_status`: 当前更新状态文案
  - `settings_update_check_button`: 手动“检查更新”按钮
  - `settings_update_auto_switch`: “自动检查更新”开关
  - `settings_update_retry_button`: 检查失败时的“重试”按钮
  - `update_dialog`: 发现更新或更新详情弹窗
  - `update_dialog_title`: 更新弹窗标题
  - `update_dialog_notes`: 更新说明内容
  - `update_dialog_download`: “前往下载”按钮
  - `update_dialog_remind_later`: “稍后提醒”按钮
  - `update_dialog_ignore`: “忽略此版本”按钮
  - `update_dialog_close`: 关闭弹窗按钮
  - `update_banner`: 自动发现更新的轻量横幅提示
  - `update_banner_action`: 横幅提示中的“查看”按钮

- 反馈相关（前缀 `feedback_` / `settings_feedback_`）：
  - `settings_feedback_card`: 设置页中的帮助与反馈卡片
  - `settings_feedback_entry`: 设置页“打开反馈”按钮
  - `settings_feedback_fab_switch`: 设置页“显示悬浮球”开关
  - `feedback_fab`: 应用内 48dp 悬浮球
  - `feedback_overlay`: 反馈面板容器
  - `feedback_close_button`: 原生辅助关闭按钮
