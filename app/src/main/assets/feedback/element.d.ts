import { type FeedbackCaptureInfo, type FeedbackImageSource, type FeedbackStatus, type IssueStatus } from './api';
import { type FeedbackDiagnosticsRecorder } from './diagnostics';
import { type CaptureProvider } from './capture';
export interface FeedbackLogFile {
    filename: string;
    blob: Blob;
}
export type FeedbackLogProvider = () => Promise<FeedbackLogFile | FeedbackLogFile[] | null | undefined> | FeedbackLogFile | FeedbackLogFile[] | null | undefined;
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
    onPanelOpen?: (detail: {
        page?: string;
    }) => void;
    onPanelClose?: () => void;
    onSaveAttachment?: (file: {
        name: string;
        mimeType: string;
        dataUrl?: string;
    }) => void | Promise<void>;
    onOpenExternal?: (url: string) => void;
}
/** v12 用户端统一显示文案（docs/api.md：open 待处理 / waiting_admin 待管理员回复）。 */
export declare const ISSUE_STATUS_LABELS: Record<IssueStatus, string>;
/**
 * 草稿图片（v12 多图模型）：自动截图与用户手动添加的图片共存于同一列表。
 * `source` 为提交口径（capture/manual/edited）；`origin` 记录最初来源，
 * 用于「重新截图」定位该替换哪一张（编辑过的截图仍视为截图）。
 */
export interface DraftImage {
    /** 稳定 ID（提交描述符复用；编辑保存替换内容时 ID 不变）。 */
    id: string;
    blob: Blob;
    /** 预览用对象 URL（断开重连时按 blob 重建）。 */
    url: string;
    source: FeedbackImageSource;
    /** 最初来源：'capture' 表示该图由截图产生（含后续编辑），重拍/移除截图针对它。 */
    origin: 'capture' | 'manual';
    filename: string;
    mime: string;
    width: number;
    height: number;
    /** 截图元数据（仅截图来源图片携带）。 */
    captureInfo?: FeedbackCaptureInfo | null;
    /** 捕获时检测到不可绘制内容（跨源 iframe / 无 CORS 图像）→ 预览旁提示。 */
    hasUndrawable?: boolean;
    /** 客户端收缩后仍 >5 MiB：提示用户并保留草稿（服务端会按 413 拒绝）。 */
    oversize?: boolean;
}
export interface FeedbackSubmittedDetail {
    feedbackId: string;
    status: FeedbackStatus;
    replayed: boolean;
}
export declare class FeedbackWidget extends HTMLElement {
    /** Shadow DOM 模式：默认 open；注册前可设 FeedbackWidget.shadowMode='closed'。 */
    static shadowMode: 'open' | 'closed';
    static get observedAttributes(): string[];
    private readonly root;
    private launcher;
    private orb;
    private panel;
    private metaInfo;
    /** 截图区 = 图片网格（有图才可见）+ 提示 + 操作区（没有截图时是「截取当前页面」）。 */
    private shotArea;
    private screenshotWrap;
    private screenshotThumbBox;
    private screenshotThumb;
    /** v12：手动添加图片入口与隐藏文件选择器。 */
    private addImageBtn;
    private imageFileInput;
    /** 预览旁提示：部分内容可能未入图 / 服务端单图降级。 */
    private shotHintEl;
    /** 图片错误提示（类型不符、超限等）。 */
    private shotErrorEl;
    /** 首个截图入口（无截图时显示）：默认 capture-mode=off 宿主唯一的手动截图方式。 */
    private captureBtn;
    private retakeBtn;
    private removeBtn;
    private zoomModal;
    private zoomImg;
    private zoomCloseBtn;
    private textarea;
    private counter;
    private submitBtn;
    private statusRegion;
    private errorRegion;
    /** 今日剩余额度（登录后显示；未登录/未取得时隐藏）。 */
    private quotaInfo;
    /** 额度用尽提示（含下次可提交时间）。 */
    private quotaBlockedInfo;
    /** 面板内登录表单（点击「登录并提交」展开，不打开新窗口）。 */
    private loginPanel;
    private loginUsername;
    private loginPassword;
    private loginErrorEl;
    private loginConfirmBtn;
    private loginCancelBtn;
    /** T6：面板主体（设置视图展开时整体隐藏）。 */
    private body;
    /** T6：服务器设置入口与视图。 */
    private settingsBtn;
    private settingsView;
    private settingsInput;
    private settingsCurrent;
    private settingsDefaultRow;
    private settingsDefaultText;
    private settingsErrorEl;
    private settingsHintEl;
    private settingsSaveBtn;
    private settingsRestoreBtn;
    private settingsCancelBtn;
    private settingsConfirmEl;
    private settingsConfirmText;
    /** 日志附件区 */
    private logsArea;
    private logsCountEl;
    private logsList;
    private addLogBtn;
    private logFileInput;
    private logStatusEl;
    private logErrorEl;
    /** 采集失败时的显式重试入口（挂在 logErrorEl 内，错误清空时随之移除）。 */
    private logRetryBtn;
    private logPreviewModal;
    private logPreviewTitle;
    private logPreviewBody;
    private logPreviewCloseBtn;
    /** v11 对话与未读徽标 */
    private launcherBadge;
    private orbBadge;
    private navTabs;
    private tabComposeBtn;
    private tabHistoryBtn;
    private tabHistoryBadge;
    private activeTab;
    private unreadCount;
    private summaryPollTimer;
    /** 历史与对话视图 */
    private historyContainer;
    private historyListView;
    private dialogueView;
    private activeDialogueId;
    private dialoguePollTimer;
    /** v12 我的反馈列表：当前页码与状态筛选。 */
    private historyPage;
    private historyIssueStatus;
    /** 对话视图中已渲染到的最大消息 seq（增量追加用）。 */
    private dialogueRenderedSeq;
    /** Bearer 附件转出的对象 URL（对话关闭/重建时统一回收）。 */
    private readonly attachmentUrls;
    /** 回复草稿图片（v12 多图，与新建反馈同一模型）。 */
    private dialogueReplyImages;
    /** v12 能力：服务端声明的最大图片数；未确认时按多图乐观放行（登录后由会话校准）。 */
    private maxImages;
    /** 服务端是否已明确返回 capabilities（缺 images → 单图模式 + 升级提示）。 */
    private capabilitiesKnown;
    /** features 探测在途标记（避免重复请求）。 */
    private featuresProbing;
    /** 当前打开的图片编辑器句柄（同时最多一个）。 */
    private editorHandle;
    /** v12 诊断记录器：宿主可选注入；身份世代递增（服务/账号切换）时清空缓冲。 */
    private _diagnostics;
    private _diagnosticsFetch;
    get diagnosticsRecorder(): FeedbackDiagnosticsRecorder | undefined;
    set diagnosticsRecorder(r: FeedbackDiagnosticsRecorder | undefined);
    /** 组件内部请求使用的 fetch：注入记录器时经 wrapFetch 记录（白名单字段）。 */
    private get apiFetch();
    private _logProvider?;
    private logsCollecting;
    private logTimeout;
    private phase;
    private polling;
    private pollTimer;
    private pollDelay;
    private pollStartedAt;
    private openState;
    private authRequired;
    /** 当前登录账号（登录响应提供；仅用于展示与额度归属）。 */
    private authUser;
    /** 最近一次取得的每日额度；resetAt 之后重新查询，不在本地擅自重置。 */
    private quota;
    private loginVisible;
    private loginBusy;
    private loginError;
    private visibilityHandler;
    /** 手动刷新服务端记录状态进行中（按钮禁用 + 防重入）。 */
    private refreshing;
    /**
     * T4：当前记录的「等待项」提示（等待配置 / 等待来源确认 / 等待人工归档）。
     * 非空表示记录已妥善保存、只是在等管理员操作——此时**不轮询**（等待不是处理中，
     * 无限轮询没有意义），改为提示 + 手动刷新；管理员处理后刷新即可看到结果。
     */
    private waitingNotice;
    /**
     * 登录操作序号（T1-A）：每次发起登录递增；面板关闭 / 取消登录 /
     * 组件卸载 / 服务身份变化都会递增使在途登录失效，并清除密码、
     * 登录忙碌状态与待自动提交标记。
     *
     * 登录回调返回后必须同时满足「序号未变 + 身份世代未变 + 组件仍连接」，
     * 才允许写入令牌、更新界面或自动提交；失败回调同样校验，
     * 防止旧登录的错误覆盖新登录。由于 close() 也会递增序号，
     * 「序号仍有效」等价于「面板自登录发起以来没有被关闭」。
     */
    private loginSeq;
    /**
     * 额度查询序号（T1-B）：提交成功 / 收到额度错误 / 关闭 / 卸载 /
     * 退到后台 / 身份变化都会递增使在途查询失效；查询结果除校验身份、
     * 令牌外还要校验该序号，避免旧查询覆盖刚扣除后的次数。
     */
    private quotaSeq;
    /** 额度查询定时器（resetAt 单次 或 额度用尽后每 30 秒）；同时最多一个。 */
    private quotaTimer;
    /** 额度查询进行中：同一时刻最多一个额度查询。 */
    private quotaInFlight;
    /** 最近一次额度刷新失败（网络等）：保留旧额度，30 秒后重试。 */
    private quotaRefreshFailed;
    /**
     * 「待立即刷新」标记（T1-B）：打开面板 / 回到前台的刷新被旧查询或登录
     * 忙碌挡下时登记，不得丢弃；旧查询结束后或忙碌结束后立即补发一次。
     * 多次登记合并为一次；关闭 / 卸载 / 退到后台 / 身份变化 / 401 时清除，
     * 重新打开按当前身份重新登记。
     */
    private quotaRefreshPending;
    /**
     * 服务身份世代（epoch）：`api-base` 或 `app-id` 变化时递增。
     * 每个异步操作在开始时捕获当前 epoch，恢复后先校验：
     * epoch 已变 → 该结果是旧服务 / 旧身份的，一律成为 no-op，
     * 绝不写入新身份的状态（提交响应、轮询记录、捕获会话、登录握手回调）。
     */
    private identityEpoch;
    /** 灵感球拖拽状态 */
    private isDragging;
    private orbPointerId;
    private isCapturing;
    /** 进行中的捕获会话控制器：cancelCapture / 卸载时 abort，使旧会话失效。 */
    private captureController;
    /** 捕获会话序号：新会话 / 失效操作递增；异步步骤后校验，旧会话结果一律丢弃。 */
    private captureSeq;
    /** 正在进行的捕获会话：普通呼出合并到该会话，不重复发起。 */
    private captureInFlight;
    /** 局部选区覆盖层与相关控件（在 buildDom 中构建，默认 hidden）。 */
    private regionOverlay;
    private regionBg;
    private regionFrame;
    private regionSizeEl;
    private regionErrorEl;
    private regionUseBtn;
    private regionAllBtn;
    private regionCancelBtn;
    /** 进行中的选区会话；null 表示覆盖层未打开。 */
    private regionSession;
    /** 覆盖层自己的键盘监听（面板关闭时 keydownHandler 未注册，Esc 必须由它处理）。 */
    private regionKeyHandler;
    /** T4：日志摘要区是否展开（默认只显示简短摘要）。 */
    private logsOpen;
    private logsToggleBtn;
    private logsSummaryEl;
    private logsBody;
    private recollectLogBtn;
    /**
     * 未提交草稿：组件实例所有（断开重连保留字节并重建自己的预览 URL），
     * 仅存内存，刷新即弃，绝不用 localStorage；appId 变化时整体废弃，
     * 旧捕获/旧草稿不得写入新身份。
     */
    private readonly draft;
    /** 当前提交快照（冻结）；失败且草稿未变时复用（同 key 同字节重试）。 */
    private submitSnapshot;
    /** 结果未知的原提交请求：草稿被修改时保留供人工核对，不静默覆盖。 */
    private unconfirmedRequest;
    /** 日志采集异步操作序号，防止迟到结果写入已切换的身份或草稿。 */
    private logOpSeq;
    private invalidateLogCollection;
    /**
     * 自定义日志提供者（L1-Web）：
     * 自动采集环境日志或诊断信息。返回单个或多个日志文件对象。
     */
    get logProvider(): FeedbackLogProvider | undefined;
    set logProvider(p: FeedbackLogProvider | undefined);
    /**
     * 自定义截图提供者（扩展契约，实施计划 2.1）：
     * 保留原参数与返回值；ctx 新增可选 signal / viewport / sensitiveRegionCount，
     * 结果新增可选 viewport / sameFrameMasking / maskedRegions（输出像素坐标）。
     * 页面无可见敏感区域时旧式回调（只返回 {blob,width,height}）保持兼容。
     * 替换提供者（含置空）会使进行中的旧捕获会话失效。
     */
    private _captureProvider?;
    get captureProvider(): CaptureProvider | undefined;
    set captureProvider(p: CaptureProvider | undefined);
    private _sessionStore?;
    private _hostBridge?;
    private hostSessionLoading;
    get hostBridge(): FeedbackHostBridge | undefined;
    set hostBridge(bridge: FeedbackHostBridge | undefined);
    get sessionStore(): FeedbackSessionStore | undefined;
    set sessionStore(store: FeedbackSessionStore | undefined);
    get isOpen(): boolean;
    handleBackPressed(): boolean;
    openExternal(url: string): void;
    saveAttachment(file: {
        name: string;
        mimeType: string;
        dataUrl?: string;
    }, blob?: Blob): Promise<void>;
    private loadHostSession;
    private clearHostSession;
    /**
     * T6：本机选择的自定义服务器覆盖（已规范化地址）；null 表示使用宿主 api-base。
     * 覆盖偏好按 localStorage 源 + appId + 规范化默认地址逐槽位隔离，
     * 仅在构造/身份属性变化时加载，不反写宿主配置。
     */
    private serverOverride;
    /** T6：本机偏好是否可写（读取或写入失败置 false，设置视图据此提示「仅本次生效」）。 */
    private serverPrefWritable;
    /** T6：最近一次加载覆盖所用的偏好键——断开重连 / 重复调用不重复读取，避免吞掉「仅本次生效」的会话内选择。 */
    private serverOverrideKeyLoaded;
    /** T6：设置视图是否展开（未登录也可进入）。 */
    private settingsOpen;
    /** T6：设置视图内的「确认切换」块是否展开（有草稿或结果未确认提交时先确认）。 */
    private settingsConfirmOpen;
    /** T6：等待确认的目标覆盖（null=恢复默认，undefined=无待确认切换）。 */
    private pendingServerTarget;
    /** 令牌仅存组件实例内存；页面刷新后靠重新握手恢复。 */
    private accessToken;
    private tokenExpiresAt;
    private idempotencyKey;
    private lastFeedbackId;
    private lastSubmittedText;
    private lastErrorSummary;
    private lastRecord;
    private keydownHandler;
    private handledKey;
    constructor();
    get apiBase(): string | null;
    set apiBase(v: string | null);
    /**
     * T6：当前实际使用的服务器地址（只读）：
     * 本机覆盖优先于宿主 `api-base`；未设置覆盖时返回宿主默认值。
     */
    get effectiveApiBase(): string | null;
    get appId(): string | null;
    set appId(v: string | null);
    /**
     * 可选的软件名称（随提交上报）。
     * 服务端只在**管理员尚未设置名称**时采用；管理员设置过就绝不会被覆盖。
     */
    get appName(): string | null;
    set appName(v: string | null);
    get appVersion(): string | null;
    set appVersion(v: string | null);
    get pageLabel(): string | null;
    set pageLabel(v: string | null);
    get side(): 'left' | 'right';
    set side(v: 'left' | 'right');
    get theme(): 'system' | 'light' | 'dark';
    set theme(v: 'system' | 'light' | 'dark');
    get showLauncher(): boolean;
    set showLauncher(v: boolean);
    get launcherBottom(): string;
    set launcherBottom(v: string | null);
    get launcherMode(): 'tab' | 'orb';
    set launcherMode(v: 'tab' | 'orb');
    get captureMode(): 'off' | 'viewport';
    set captureMode(v: 'off' | 'viewport');
    private reflect;
    open(): void;
    close(): void;
    cancelCapture(): void;
    /**
     * 使当前捕获会话失效：序号先行失效 → abort 取消；失效方负责恢复 UI
     * 与清理 in-flight（旧会话的 finally 不会再恢复新会话的 UI）。
     */
    private invalidateCaptureSession;
    /** 恢复被捕获流程隐藏的组件 UI（会话失效方负责恢复）。 */
    private restoreCaptureUi;
    /** prefers-reduced-motion：灵感球取消光晕流动与缩放。 */
    private prefersReducedMotion;
    /**
     * 灵感球拖拽松手 → 局部选区流程（T1）：
     * - 图片已达上限且无可替换截图：先提示整理附件，**不发起捕获**；
     * - 已有捕获会话在途：合并到该会话，不重复发起；
     * - 捕获失败沿用现有失败提示（草稿与旧图保留，绝不静默回退成整屏截图）。
     */
    private startRegionCapture;
    /** 草稿是否已有内容（有文字、图片或日志的草稿再次呼出时恢复草稿、不重拍）。 */
    private isDraftDirty;
    /** 草稿中由截图产生的图片（重拍 / 移除截图的目标；编辑过的截图仍算）。 */
    private captureImage;
    /** 组件自身 UI 不参与截图，也不计入宿主页面敏感区域。 */
    private isOwnFeedbackUi;
    /**
     * 普通呼出（launcher / orb 点击 / 拖拽落点）：
     * - 已有草稿（文字或截图）→ 恢复草稿打开面板，不重拍；
     * - 正在捕获 → 合并到进行中的会话，不重复发起；
     * - 提交进行中 → 禁止编辑类操作，直接忽略。
     */
    captureAndOpen(opts?: {
        releasePoint?: {
            x: number;
            y: number;
        };
    }): Promise<void>;
    /**
     * 重拍（内部明确入口）：可取消旧会话后重新捕获；只有重拍替换旧截图，
     * 且失败不丢旧图（runCapture 失败路径不触碰草稿）。
     */
    retakeScreenshot(): Promise<void>;
    /**
     * 面板内的手动截图入口（首次截图与重拍共用同一条路径）。
     *
     * 刻意**不**走 `captureAndOpen()`：那条路径是「呼出」语义——草稿一旦有内容
     * （哪怕只有文字）就只恢复草稿开面板、绝不重拍，于是默认 `capture-mode="off"`
     * 的宿主在用户先输入文字后，就再没有任何补拍入口（本次修复的缺口）。
     * 这里复用显式重拍流程：可替换旧图，失败不丢旧图与文字。
     *
     * 按钮在捕获期间已禁用；这里再挡一次重入（禁用态只是视觉 / a11y 保证，
     * 程序化调用与键盘连击不该产生并发会话）。
     */
    private captureFromPanel;
    /**
     * 捕获期间面板被 `visibility: hidden` 隐藏，真实浏览器会把焦点丢到 body
     * （面板内的按钮 / textarea 全部失焦）。两种情况要把焦点还回去：
     * 焦点已经不在面板内（`null`），或**卡在一个已经隐藏的控件上**
     * （个别引擎不主动移除隐藏元素的焦点）。优先点击前的元素（若它已隐藏 /
     * 禁用则回到 textarea），键盘用户不会在截图后凭空失去落点；
     * 用户已主动聚焦到可见的别处时绝不抢焦点。
     */
    private restorePanelFocus;
    /**
     * 图片区同步（v12 多图）：**预览网格与操作分离**。
     * - 网格只在真的有可渲染对象 URL 时出现：绝不留下空 src 的破图占位
     *   （历史故障与其成因见 styles.ts 的 `[hidden]` 兜底注释）。
     * - 首个单元格是稳定 DOM（legacy .fb-screenshot-thumb-box/.fb-screenshot-thumb），
     *   其余单元格按 images[1..] 重建；每张图可放大、编辑、删除。
     * - 无截图 → 只显示「截取当前页面」；有截图 → 「重新截图 / 移除截图」；
     *   「添加图片」在未满上限（capabilities.images 或 5）时可用；服务端
     *   明确不支持多图时提示「升级服务端以支持多图」。
     * - 提交、轮询与捕获会话进行中，所有图片操作一律禁用（防重入）。
     */
    private syncShotUi;
    /**
     * 图片角标文案（T2.5）：局部截图（带 region）/ 截图 / 图片 + 编辑标记。
     * 编辑过的图仍带「已编辑」；带 region 的截图标「局部截图」。
     */
    private imageBadgeText;
    /** 图片尺寸读数（输出像素，宽×高）。 */
    private imageSizeText;
    /** 构建第 index 张图片的网格单元格（index≥1 的次要单元格，重建型）。 */
    private buildImageCell;
    /** 图片错误提示（shotErrorEl）。 */
    private showImageError;
    /**
     * 把文件加入草稿图片列表（选择 / 拖入 / 粘贴共用）。
     * 校验 → 解码 → 需要时等比缩小；全部失败给出可读错误，部分成功保留成功的。
     * 数量超过 maxImages（capabilities 或 5）时丢弃超出的并提示。
     */
    private addImageFiles;
    /** 新增一张草稿图片（manual 来源；截图走 capture 路径）。 */
    private pushDraftImage;
    /** 按 ID 删除一张草稿图片（释放对象 URL）。 */
    private removeImage;
    /**
     * 打开全屏编辑器编辑指定图片：保存结果经统一限额收缩后**原位替换**
     * （同 ID、不占新名额），来源标记为 'edited'；取消不改动草稿。
     */
    private openEditorForImage;
    /**
     * 单次捕获会话主体：每个异步步骤后与更新草稿 / 开面板前都校验会话有效性。
     *
     * `regionFlow=true`（灵感球拖拽）时，捕获成功**不写草稿**：位图被冻结并交给
     * 局部选区覆盖层，用户确认（使用此区域 / 截取整个窗口）后才裁剪并 commit，
     * 取消则整体丢弃；失败路径与直接路径完全一致（提示失败、保留草稿与旧图）。
     */
    private runCapture;
    /**
     * 把一次已通过校验的冻结捕获写入草稿（直接路径与选区确认路径共用）：
     * 重拍原位替换（同 ID、不占名额），首次则新增；`region` 仅在局部截图时写入
     * `captureInfo`，整窗截图省略该字段（与旧客户端一致）。
     */
    private commitCapture;
    /** 打开覆盖层：冻结位图铺满显示，初始选区以松手位置为中心并整体移回可见区。 */
    private openRegionFlow;
    /** 覆盖层显示尺寸 ↔ 捕获视口逻辑像素的换算（位图铺满覆盖层显示）。 */
    private regionScale;
    /** 指针事件 → 选区坐标系（捕获视口逻辑像素）。 */
    private regionPoint;
    /** 重绘选区框（位置 / 尺寸 / 输出尺寸读数）：覆盖层打开期间持续生效。 */
    private renderRegion;
    private onRegionPointerDown;
    private onRegionPointerMove;
    private onRegionPointerEnd;
    /**
     * 确认：`useSelection=true` 裁剪冻结位图到选区（region 随元数据透传），
     * false 使用整张冻结位图（region 省略，与旧客户端一致）。
     * 裁剪失败保持覆盖层打开并给出可重试提示，绝不静默回退成整屏截图。
     */
    private confirmRegion;
    /** 取消：丢弃冻结画面并恢复 UI，不覆盖原草稿、不报失败；焦点回到灵感球。 */
    private cancelRegion;
    /** 收起覆盖层（任何失效路径共用）：回收对象 URL、移除监听、还原焦点。 */
    private dismissRegionFlow;
    /** 覆盖层键盘：Esc=取消、回车=使用此区域、方向键=移动、Shift+方向键=缩放、Tab 焦点锁。 */
    private onRegionKeydown;
    private openZoomModal;
    private closeZoomModal;
    /** 移除截图（origin=capture 的那一张；手动图片不受影响），并递增草稿版本。 */
    private removeScreenshot;
    /** 清空草稿（提交成功后 / appId 变化时），字节与预览 URL 一并释放。 */
    private clearDraft;
    private isMobile;
    private updateAriaModal;
    /** 一次性事件绑定（构造期）：重挂载绝不重复绑定。 */
    private bindEvents;
    connectedCallback(): void;
    disconnectedCallback(): void;
    attributeChangedCallback(name: string, oldVal: string | null, newVal: string | null): void;
    /**
     * `api-base` 变化 = 完整身份切换：取消并清空一切属于旧服务的东西
     * （捕获会话 / 提交快照 / 幂等键 / 握手 / 轮询 / 令牌 / 任务态 / 草稿）。
     * 新服务必须重新登录，旧令牌绝不外泄给新基址；旧服务的迟到响应由 epoch 校验丢弃。
     */
    private resetForServiceSwitch;
    /**
     * `app-id` 变化：同一服务内的身份切换——令牌保留（同一服务），
     * 但旧身份的草稿 / 捕获 / 提交结果 / 轮询 / 握手全部作废。
     */
    private resetForAppIdSwitch;
    private updateLauncherBottomCss;
    private updateMetaInfo;
    private initOrbGesture;
    private buildDom;
    /**
     * T6：服务器设置视图（面板内、与主体互斥显示；未登录也可进入）。
     * 提供地址输入、保存、取消、恢复默认与当前有效地址展示；
     * 地址变化且有草稿 / 未确认提交时先展开内联确认块。
     */
    private buildSettingsView;
    private buildHistoryView;
    private switchTab;
    private syncUnreadBadge;
    /**
     * 未读摘要轮询（v12 口径）：**不再以面板打开为前提**——已登录且页面可见时
     * 每 30 秒查一次 unread-summary（面板关闭也要给入口徽标供数）。
     * 面板销毁 / 页面隐藏 / 退出登录时停止；提交跟踪进行中时跳过（提交轮询优先）。
     */
    private startSummaryPolling;
    private stopSummaryPolling;
    private pollSummary;
    private startDialoguePolling;
    private stopDialoguePolling;
    private loadHistoryList;
    private openDialogue;
    private closeDialogue;
    /** 回收本次对话用过的 Bearer 附件对象 URL。 */
    private revokeAttachmentUrls;
    /** Bearer 取回附件并转对象 URL（v12：历史图片/日志绝不以裸 URL 渲染）。 */
    private attachmentUrl;
    /** 以 Bearer 方式取回附件并渲染进 <img>（失败降级为文件名文本，不阻断消息）。 */
    private renderAttachmentImage;
    /** 以 Bearer 方式取回附件并触发保存/下载（宿主 bridge / 浏览器下载）。 */
    private downloadAttachment;
    /** 清空回复草稿图片（释放对象 URL）。 */
    private clearReplyImages;
    private dialogueReplyText;
    private dialogueReplyLogs;
    private dialogueMessagesBox;
    private dialogueStatusBadge;
    private dialogueResolutionNoteEl;
    private loadDialogue;
    /** 移除一张回复草稿图片（释放对象 URL）。 */
    private removeReplyImage;
    /** 编辑回复草稿图片：保存原位替换（不占名额），取消不动。 */
    private openReplyEditor;
    /**
     * 渲染完整对话（非轮询 / 首次打开）：清空后重建原始反馈块 + 全部消息，
     * 记录已渲染的最大 seq 供增量追加。
     */
    private renderDialogueMessages;
    /**
     * 增量渲染（轮询）：只追加 seq 更大的新消息；用户正在阅读时不强跳滚动
     * （已在底部才跟随到底），回复草稿与滚动位置保持不动。
     */
    private appendDialogueMessages;
    /** 日志摘要行文案（T4）：默认只显示「已附诊断日志 / 暂无日志」这类简短摘要。 */
    private logsSummaryText;
    private setLogsOpen;
    private toggleLogs;
    private collectLogs;
    private handleLogFileSelect;
    private removeLog;
    private openLogPreview;
    private closeLogPreview;
    private syncLogsUi;
    private tokenValid;
    private failPhase;
    private onPrimaryAction;
    /** 4xx 视为服务端明确处理并拒绝（未保存）；其余（网络错误/5xx/408/429）结果未知。 */
    private isUnknownOutcome;
    private submit;
    private startPolling;
    private schedulePoll;
    private pollTick;
    /**
     * 依记录状态更新阶段 / 结果 / 状态文本（轮询与手动刷新共用）。
     * 返回 true 表示已进入终态（调用方应停止轮询）。
     */
    private applyRecordState;
    private resumePolling;
    private stopPolling;
    /**
     * 服务端已接收但后台处理失败时的手动刷新：只读 `GET /api/feedback/:id`，
     * 更新 phase / lastRecord / lastErrorSummary / 状态文本。绝不重复 POST。
     * 与轮询共用身份世代校验：身份切换后的迟到结果一律丢弃。
     */
    private refreshLastRecord;
    /**
     * 复制反馈标识：剪贴板不可用（或写入失败）时静默降级，绝不抛错、绝不发请求。
     */
    private copyFeedbackId;
    /** 按当前（appId, api-base）槽位读取本机覆盖；键未变时跳过，保留会话内未落盘的选择。 */
    private loadServerOverride;
    /** 两个地址规范化后是否相同（不可规范化的按去空白原值比较）。 */
    private sameNormalizedBase;
    private pageIsHttps;
    private openServerSettings;
    private closeServerSettings;
    private setSettingsHint;
    /**
     * 写入 / 清除本机覆盖偏好；返回是否落盘成功（失败时调用方提示「仅本次生效」）。
     * 偏好只存地址——不持久化草稿、密码或令牌。
     */
    private persistServerOverride;
    /**
     * 保存设置视图输入：校验 → 与当前有效地址相同仅落盘（不触发身份重置）→
     * 不同且有草稿 / 未确认提交时先展开确认块 → 确认后切换。
     */
    private saveServerSettings;
    /** 「恢复默认」：与保存共用确认与提交流程，目标覆盖为 null。 */
    private requestRestoreDefault;
    /** 地址即将变化：有草稿或结果未确认提交时先展开内联确认块，否则直接切换。 */
    private requestServerSwitch;
    private confirmServerSwitch;
    private cancelServerSwitch;
    /**
     * 应用覆盖并切换身份：相同规范化地址不触发重置由调用方保证；
     * 这里落盘偏好 → epoch 递增作废旧服务在途请求 → 完整服务切换重置 → 探测连通性。
     */
    private commitServerSwitch;
    /**
     * 保存后探测新地址连通性（GET /healthz，不带凭据）：
     * 失败只提示，不回切——连接失败不自动换回其他服务器（T6）。
     */
    private probeServer;
    /** 设置视图的只读展示同步（当前有效地址 / 宿主默认 / 确认块可见性）。 */
    private syncSettingsUi;
    private pendingSubmit;
    /** 展开面板内账号密码表单（不打开新窗口）；pendingSubmit=true 时登录成功后只提交一次。 */
    private beginLogin;
    /** 取消登录：收起表单并让在途登录失效，草稿与截图保留。 */
    private cancelLogin;
    /**
     * 使尚未完成的登录接续失效：递增登录序号，清除密码、登录忙碌状态与
     * 待自动提交标记。**不撤销**已经建立的有效登录态，也不动草稿与截图。
     * 调用点：关闭面板、取消登录、组件卸载、服务身份变化。
     */
    private invalidateLogin;
    /**
     * 登录回调是否仍然有效：序号未变（面板自登录发起以来没有被关闭、
     * 也没有被取消 / 卸载 / 换身份）+ 服务身份世代未变 + 组件仍连接。
     */
    private loginStillValid;
    private doLogin;
    private cancelQuotaTimer;
    /**
     * 使在途额度查询失效并取消定时查询：关闭 / 卸载 / 退到后台 / 身份变化 /
     * 提交成功 / 收到额度错误时调用。旧查询结果不得再写回额度。
     */
    private invalidateQuotaRefresh;
    /**
     * 统一调度额度查询（同一时刻最多一个定时器）：
     * - 未登录 / 面板未打开 / 应用不在前台 → 不排程；
     * - 刷新失败 → 30 秒后重试（不对过期 resetAt 立即循环请求）；
     * - 额度用尽 → 每 30 秒一次，并与 resetAt 合并取更早者
     *   （后台调高额度后最多 30 秒即可恢复，无需关闭重开面板）；
     * - 其它状态 → 只在服务端 resetAt 排单次查询，到期只向服务端取最新值。
     */
    private scheduleQuotaRefresh;
    /**
     * 刷新额度与会话信息：打开面板、应用恢复前台、提交完成、额度用尽轮询时调用。
     * 不在本地擅自重置——跨过 resetAt 后由服务端返回新的 used / remaining。
     * 结果除校验身份与令牌外还校验查询序号：提交成功后的旧查询不得把次数加回。
     * 被旧查询 / 登录忙碌挡下时登记「待立即刷新」，结束后立即补发（T1-B）。
     */
    private refreshQuota;
    /**
     * 消费「待立即刷新」意图（T1-B）：优先于定时规则——旧查询结束 / 忙碌结束
     * 后条件仍满足时立即补发一次并清除标记；否则按现有 30 秒 / resetAt 规则
     * 排定时器（不会紧密循环，也不会因一次跳过而永久停摆）。
     */
    private consumeQuotaRefreshIntent;
    /** 额度查询结果是否仍然有效：序号 + 身份世代 + 令牌三者都要匹配。 */
    private quotaResultValid;
    /**
     * 应用服务端能力声明（登录 / 会话响应附带 `capabilities`）：
     * - `images: N` → 允许多图（上限取 min(N, 5)）；
     * - 字段缺失（旧服务端）→ 单图模式 + 升级提示；
     * - 字段存在且为 0/负值 → 同样按单图处理。
     * 会话响应携带 `capabilities` 对象即视为「已确认」（缺失 images 也确认）。
     */
    private applyCapabilities;
    /**
     * 能力校准顺序：认证响应（login / session 的 capabilities）优先；
     * 能力仍未知且需要判断（提交多图）时才探测 `GET /api/features`
     * （无需登录，跨源放行）。探测成功即确认（响应缺 images → 单图模式）；
     * 探测失败保持未确认——乐观放行多图，由服务端裁决。
     * `GET /api/features` 同时以 api.ts 导出供宿主主动探测。
     */
    private probeFeatures;
    /** 公开 API：展开面板内登录表单（保留旧签名的返回值）。 */
    startLogin(): string;
    /**
     * 宿主注入会话：与面板内登录等价，但凭据由宿主提供——例如同源管理后台
     * 以自身 Cookie 会话调 `POST /api/auth/handshake` 换取的握手令牌。
     * 令牌同样仅存内存；宿主负责到期前续注（重新调用本方法）或在自身
     * 会话结束时调用 dropSession()。注入后组件自动拉取会话身份与额度。
     * 注入非法参数（空令牌 / 不可解析的过期时刻）按 no-op 处理。
     */
    adoptSession(session: {
        accessToken: string;
        expiresAt: number | string;
    }): void;
    /**
     * 宿主清除注入的会话（如宿主自身退出登录）：丢弃令牌与身份，回到
     * 需要登录态；草稿 / 截图 / 日志保留（与令牌被撤销同语义）。
     */
    dropSession(): void;
    private resetToCompose;
    private renderStatus;
    private syncUi;
    private onKeydown;
    private focusables;
}
