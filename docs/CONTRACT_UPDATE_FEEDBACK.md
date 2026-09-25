# StarTool 检查更新与 Feedback 接入施工合同（T0 冻结）

本文档是各子模块与 subagent 的唯一契约，规定更新检查（T1）与 Feedback 接入（T2）的接口签名、数据格式、测试标识与边界责任。

## 1. 模块划分与代码归属

| 模块 / 路径 | 所有者 | 交付产物与职责 |
|---|---|---|
| `app.startool.android.update` | 更新 subagent | UpdateManifest、UpdateCheckResult、UpdatePreferenceStore、UpdateChecker、`generateUpdateManifest`、`ApkSourceResolver`、`ApkDownloader`、`ApkVerifier`、`UpdateDownloadManager`、`InstallOutcomeMapper`、`ApkInstaller`、`InstallStatusReceiver` 及对应 JVM 单元测试 |
| `app.startool.android.ui.update` | 主 agent | `UpdateDialog`（公共更新弹窗 + `UpdateDownloadAction`）、`UpdateProgressSection`（下载/安装进度与结果） |
| `packages/web/`（Feedback 仓库） | Web 组件 subagent | 宿主扩展接口（`sessionStore`、`handleBackPressed`、`onOpenExternal`、`onSaveAttachment`）、事件派发、对应测试与产物打包 |
| `app.startool.android.feedback` | Android 适配 subagent | KeystoreSessionStore、ActivityCaptureProvider、FeedbackNativeBridge、WebViewAssetLoader 宿主壳、FeedbackFab 悬浮球、脱敏诊断采集及对应测试 |
| 共享入口与根界面（`StarToolRoot`、`SettingsScreen`、`MainActivity`、`AppContainer`、Manifest、Gradle 配置） | 主 agent | 统一注入依赖、挂载悬浮球与更新提示横幅、页面视觉一致性、产物集成与整体端到端验收 |

---

## 2. T1：更新检查与应用内更新契约

### 2.1 清单格式（`startool-update.json`）

`schemaVersion` 2。**新增的三个 APK 字段全部可选**：缺失或为 `null` 时读取端必须照常解析（保证新客户端读旧清单、老客户端读新清单都不失败）。

```json
{
  "schemaVersion": 2,
  "applicationId": "app.startool.android.gemini",
  "versionCode": 3,
  "versionName": "0.1.2",
  "minSdk": 26,
  "notes": "更新说明内容...",
  "releaseUrl": "https://github.com/SakuraLoveSmile/StarTool/releases/tag/v0.1.2",
  "apkUrl": "https://github.com/SakuraLoveSmile/StarTool/releases/download/v0.1.2/StarTool-v0.1.2-release.apk",
  "apkSha256": "e620b23c292a186045088911761455a87263b19c679f9375b24d6e5c5c41fc62",
  "apkSizeBytes": 8609114
}
```

字段来源与写入规则（`generateUpdateManifest`，`app/build.gradle.kts`）：
- `apkSha256` / `apkSizeBytes`：`dependsOn(packageRelease)` 后对 APK 流式计算，**始终写入**。
- `apkUrl`：**仅在打 tag 的 CI 构建**下写入（取 `GITHUB_REF_NAME`）。本地与 `workflow_dispatch` 的资产名是 `StarTool-<run>-release.apk`，与 tag 构建不一致，此时留空，客户端回退到 GitHub API 的 assets 解析。
- `releaseUrl`：同样取 `GITHUB_REF_NAME`（非 tag 构建回退 `v<versionName>`），**不由 `versionName` 推导**，否则 tag 与 versionName 不一致时会指向另一个旧 release。

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
    // P1 新增，一律可选
    val apkUrl: String? = null,
    val apkSha256: String? = null,     // 读取时归一为小写
    val apkSizeBytes: Long? = null,
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

### 2.4 应用内下载与安装（P2 / P3）

**下载源解析**（`ApkSourceResolver`）
8. 只接受 `apkUrl` 以 `https://github.com/SakuraLoveSmile/StarTool/` 开头且以 `.apk` 结尾的直链；不满足返回 `null`，调用方**降级为跳浏览器**，不得提示失败。
9. **先校验原始地址是官方直链，再套用代理镜像前缀**，避免镜像源被诱导指向任意域名。
10. `apkSha256` 归一为小写且长度为 64 的十六进制，否则丢弃；`apkSizeBytes <= 0` 同样丢弃。

**下载**（`ApkDownloader`）
11. 流式写盘（不得整包读入内存），进度回调需节流；`Content-Length` 未知时 `total` 回 `null`，UI 走不确定进度条。
12. 手动跟随 3xx 以保住 `Range` 头；支持 Range 续传；服务端返回 200 而非 206 时**必须从头覆盖**，不得盲目 append。
13. 读到 `-1` 但已下载字节数 < `Content-Length` 时判为 `Interrupted`，**绝不返回 `Completed`**（否则截断的 APK 会进入安装）。

**校验**（`ApkVerifier`）
14. 顺序固定为**先体积后哈希**；`apkSha256` 缺失时降级为只校验体积。
15. 校验失败必须删除文件，**不得进入 `Ready` 状态**。

**安装**（`ApkInstaller` + `InstallStatusReceiver`）
16. 使用 `PackageInstaller` 而非 `ACTION_VIEW` + `startActivityForResult`：Android 11 起后者拿不到真实安装结果（恒为 `RESULT_CANCELED`），做不出结果提示。
17. API 形状：`createSession` → `openSession(id).use { session.openWrite / session.fsync / session.commit }`。`PackageInstaller` 自身没有 `openWrite`/`fsync`/`commit`。
18. `PendingIntent` 必须 `FLAG_MUTABLE`（系统回填 `EXTRA_STATUS`），Android 12+ 用 `IMMUTABLE` 会抛异常。
19. `STATUS_PENDING_USER_ACTION(-1)` 必须把回执里的确认 `Intent` 拉到前台，否则系统弹窗不出现。
20. 状态码取值以 compileSdk 36 实测为准（**非连续序号**）：`SUCCESS=0`、`PENDING_USER_ACTION=-1`、`FAILURE=1`、`BLOCKED=2`、`ABORTED=3`、`INVALID=4`、`CONFLICT=5`、`STORAGE=6`、`INCOMPATIBLE=7`、`TIMEOUT=8`。
21. 用户取消 / 会话中止归为 `Cancelled`，**不作为错误提示**。
22. 签名不一致（`INSTALL_FAILED_UPDATE_INCOMPATIBLE` 等）必须给出「先导出备份、再卸载旧版」的可执行中文指引，而非英文错误码。
23. 安装成功先写入 `pendingInstallTargetVersionCode/Name` 再等回执；安装失败必须清掉 `pending`，否则进程被杀后冷启动会误报「更新成功」。
24. 冷启动时 `UpdateDownloadManager.init` 比对 `pending` 与实际 `versionCode`，一次性补发「已成功更新到 x」或「上次更新未完成」，展示后即消费。

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
  onSaveAttachment?: (file: { name: string; mimeType: string; dataUrl: string }) => void | Promise<void>;
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
  - `update_dialog_download`: “下载更新”按钮（P4 起文案由“前往下载”改为“下载更新”）
  - `update_dialog_remind_later`: “稍后提醒”按钮
  - `update_dialog_ignore`: “忽略此版本”按钮
  - `update_dialog_close`: 关闭弹窗按钮
  - `update_banner`: 自动发现更新的轻量横幅提示
  - `update_banner_action`: 横幅提示中的“查看”按钮

- 应用内下载 / 安装（P2 / P3，前缀 `settings_update_`）：
  - `settings_update_download_section`: 下载与安装进度区容器
  - `settings_update_progress`: 下载进度条
  - `settings_update_progress_text`: 进度百分比与字节数文案
  - `settings_update_cancel_button`: 下载中“取消”
  - `settings_update_cancelled`: 已取消下载文案
  - `settings_update_cancelled_close`: 取消后“关闭”
  - `settings_update_ready`: “下载完成，已通过完整性校验”
  - `settings_update_install_button`: “安装 vX.Y.Z”按钮
  - `settings_update_permission_hint`: 未授权安装提示
  - `settings_update_permission_button`: “去授权”按钮
  - `settings_update_permission_cancel`: 授权提示“稍后”
  - `settings_update_installing`: 安装进行中文案
  - `settings_update_install_success`: 安装成功文案
  - `settings_update_restart_button`: “立即重启”按钮
  - `settings_update_install_success_close`: 成功提示“稍后”
  - `settings_update_install_error`: 安装失败文案
  - `settings_update_install_error_detail`: 安装失败技术细节
  - `settings_update_install_retry_button`: “重新安装”按钮
  - `settings_update_install_error_close`: 失败提示“关闭”
  - `settings_update_install_cancelled`: 安装被取消文案
  - `settings_update_install_cancelled_close`: 取消安装“关闭”
  - `settings_update_download_error`: 下载失败文案（含断点/校验/HTTP 分类）
  - `settings_update_download_retry_button`: 下载失败“继续下载 / 重试”

- 反馈相关（前缀 `feedback_` / `settings_feedback_`）：
  - `settings_feedback_card`: 设置页中的帮助与反馈卡片
  - `settings_feedback_entry`: 设置页“打开反馈”按钮
  - `settings_feedback_fab_switch`: 设置页“显示悬浮球”开关
  - `feedback_fab`: 应用内 48dp 悬浮球
  - `feedback_overlay`: 反馈面板容器
  - `feedback_close_button`: 原生辅助关闭按钮
