/** HTTP 契约客户端（docs/api.md v1）。运行时零依赖，仅使用 fetch。 */
export type FeedbackStatus = 'received' | 'processing' | 'archiving' | 'archived' | 'needs_review' | 'failed';
export interface FeedbackContext {
    appVersion?: string;
    pageLabel?: string;
}
/**
 * T1 局部截图选区：0..1 归一化，**相对原始捕获视口**（即 viewportWidth/Height
 * 描述的那个视口），恒有 x+width≤1、y+height≤1。
 * 整窗截图省略该字段（与旧客户端一致）；`pixelWidth/Height` 表示选区裁剪后的
 * 最终输出像素，`viewportWidth/Height` 仍为原始捕获视口语义。
 */
export interface FeedbackCaptureRegion {
    x: number;
    y: number;
    width: number;
    height: number;
}
export interface FeedbackCaptureInfo {
    /** 逻辑视口尺寸（CSS 像素）。 */
    viewportWidth?: number;
    viewportHeight?: number;
    /** 截图输出位图尺寸（物理像素），与逻辑视口区分（实施计划 2.3；两端统一字段名）。 */
    pixelWidth?: number;
    pixelHeight?: number;
    capturedAt?: string;
    releasePoint?: {
        x: number;
        y: number;
    };
    /** 局部截图时的归一化选区；整窗截图省略。 */
    region?: FeedbackCaptureRegion;
}
export interface FeedbackLogAttachment {
    filename: string;
    source: 'auto' | 'manual';
    blob: Blob;
    byteSize?: number;
    sha256?: string;
}
/** v12 图片来源（写入 metadata.images[].source）。 */
export type FeedbackImageSource = 'capture' | 'manual' | 'edited';
/** v12 有序图片描述符：与 multipart `images` 部件按顺序一一对应。 */
export interface FeedbackImageDescriptor {
    /** 客户端生成的稳定 ID（1..64 字符，同一次提交内唯一）；编辑替换内容时 ID 可复用。 */
    id: string;
    source: FeedbackImageSource;
    filename: string;
    sha256: string;
    mime: 'image/png' | 'image/jpeg' | 'image/webp';
}
/** 待提交图片（描述符之外的二进制内容）。 */
export interface FeedbackImageInput {
    id: string;
    source: FeedbackImageSource;
    filename: string;
    blob: Blob;
}
export interface FeedbackSubmitPayload {
    idempotencyKey: string;
    appId: string;
    /** 可选：组件上报的软件名称（服务端仅在管理员未设置名称时采用）。 */
    appName?: string;
    text: string;
    context?: FeedbackContext;
    capture?: FeedbackCaptureInfo;
    screenshot?: Blob;
    /** v12：有序图片列表（≤5，与 `screenshot` 互斥）。 */
    images?: FeedbackImageInput[];
    logs?: FeedbackLogAttachment[];
}
export interface FeedbackSubmitResponse {
    feedbackId: string;
    status: FeedbackStatus;
    replayed?: boolean;
    user?: AuthUser;
    quota?: Quota;
    /** T4：反馈已保存，等待管理员配置 / 确认来源 / 人工归档，或已进入归档流程。 */
    collectionState?: CollectionState;
}
/**
 * 组件侧「已保存、等待什么」状态（旧服务端不返回该字段，缺省即可忽略）。
 * 等待态不是失败：组件应提示已保存，并停止无意义的轮询。
 */
export type CollectionState = 'waiting_configuration' | 'waiting_source_confirmation' | 'waiting_manual_archive' | 'queued';
/** 等待态的中文提示（queued 与未知状态返回 null，按原有“整理中”展示）。 */
export declare function collectionWaitingText(state?: CollectionState): string | null;
/** 已登录账号的最小信息（服务端不返回密码或哈希）。 */
export interface AuthUser {
    id: string;
    username: string;
    role: 'admin' | 'user';
}
/** 普通账号的每日提交额度（数值形态；unlimited 缺省即 false）。 */
export interface LimitedQuota {
    unlimited?: false;
    dailyLimit: number;
    used: number;
    remaining: number;
    /** 下次额度刷新时间（北京时间次日零点，ISO-8601 UTC）。 */
    resetAt: string;
}
/** 管理员账号的每日提交额度：不受次数限制（不返回 dailyLimit / remaining 数值）。 */
export interface UnlimitedQuota {
    unlimited: true;
    used: number;
    /** 下次额度刷新时间（北京时间次日零点，ISO-8601 UTC）。 */
    resetAt: string;
}
/** 每日提交额度（服务端按北京时间日切分）：管理员为不限形态，普通账号为数值形态。 */
export type Quota = LimitedQuota | UnlimitedQuota;
export interface LoginResponse {
    ok: boolean;
    token: string;
    expiresAt: string;
    user: AuthUser;
    /** 额度（双形态之一）；无法解析的形态归一为缺省——组件据此隐藏额度行。 */
    quota?: Quota;
    /** v12：服务端能力声明（如 `{ dialogue: true, images: 5 }`）。 */
    capabilities?: FeedbackCapabilities;
}
/**
 * v12 服务端能力：`images` 为多图附件上限（≤5 之外的值按服务端声明取信）；
 * 旧服务端无此字段（或不返回 `capabilities`）→ `undefined`，组件保持单图模式并提示升级。
 */
export interface FeedbackCapabilities {
    dialogue?: boolean;
    images?: number;
}
export interface SessionResponse {
    authenticated: boolean;
    kind?: string;
    clientLabel?: string | null;
    expiresAt: string;
    user: AuthUser;
    /** 额度（双形态之一）；无法解析的形态归一为缺省——组件据此隐藏额度行。 */
    quota?: Quota;
    /** v12：服务端能力声明（如 `{ dialogue: true, images: 5 }`）。 */
    capabilities?: FeedbackCapabilities;
}
export interface FeedbackRecord {
    id: string;
    status: FeedbackStatus;
    createdAt: string;
    updatedAt: string;
    errorSummary?: string | null;
    kaneoUrl?: string | null;
    /** T4：等待配置 / 等待来源确认 / 等待人工归档 / 已排队；旧服务端可能不返回。 */
    collectionState?: CollectionState;
}
/** 契约统一错误：`{ error: { code, message } }`；message 可安全展示。 */
export declare class ApiError extends Error {
    readonly status: number;
    readonly code: string;
    /** 429 daily_quota_exceeded 等错误体携带的同结构额度（若有）。 */
    readonly quota?: Quota;
    constructor(status: number, code: string, message: string, quota?: Quota);
}
export declare function joinApi(apiBase: string, path: string): string;
/** 客户端生成的幂等键（UUID v4 形态，≤200 字符）。 */
export declare function uuid(): string;
/** 握手 nonce：32 hex 字符。 */
export declare function randomNonce(): string;
/** 宽松解析 capabilities.images：正整数取信（封顶 5），其余形态一律视为「缺失」。 */
export declare function parseCapabilities(raw: unknown): FeedbackCapabilities | undefined;
/** Blob 内容的 SHA-256（小写十六进制，64 字符）。 */
export declare function sha256Hex(blob: Blob): Promise<string>;
/** 与 DOM fetch 兼容的最小签名（见 diagnostics.wrapFetch）。 */
export type FetchLike = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;
/**
 * 组件在宿主面板内登录：`POST /api/auth/login`（令牌模式）。
 * 显式携带 clientLabel 与 appId，由服务端按该应用允许来源校验浏览器 Origin；
 * 令牌仅存内存，密码不持久化。响应含 user 与 quota。
 */
export declare function login(apiBase: string, input: {
    username: string;
    password: string;
    clientLabel: string;
    appId: string;
}, opts?: {
    fetchFn?: FetchLike;
}): Promise<LoginResponse>;
/** 查询会话（用于刷新额度 / 校验令牌），使用 Bearer，不依赖跨站 Cookie。20 秒超时。 */
export declare function getSession(apiBase: string, token: string, opts?: {
    fetchFn?: FetchLike;
}): Promise<SessionResponse>;
export declare function logout(apiBase: string, token: string, opts?: {
    fetchFn?: FetchLike;
}): Promise<void>;
/** v12 能力探测：`GET /api/features`（无需登录，跨源放行）。响应体不含 images 时 `images` 为 undefined。 */
export declare function getFeatures(apiBase: string, opts?: {
    fetchFn?: FetchLike;
}): Promise<FeedbackCapabilities>;
export declare function submitFeedback(apiBase: string, token: string, payload: FeedbackSubmitPayload, opts?: {
    fetchFn?: FetchLike;
}): Promise<FeedbackSubmitResponse>;
export type IssueStatus = 'open' | 'waiting_user' | 'waiting_admin' | 'resolved';
export interface FeedbackMessageAttachment {
    id: string;
    /** v12 新增 `image`（带 mime/width/height）。 */
    kind: 'screenshot' | 'log' | 'image';
    filename: string;
    byteSize: number;
    sha256: string;
    mime?: string | null;
    width?: number | null;
    height?: number | null;
    createdAt: string;
}
export interface FeedbackMessageItem {
    id: string;
    seq: number;
    senderType: 'user' | 'admin' | 'system';
    senderId: string;
    senderName: string;
    text: string;
    /** v12：管理员解决/请求补充时的处理说明（随状态变更持久化，对用户可见）。 */
    resolutionNote?: string | null;
    createdAt: string;
    attachments: FeedbackMessageAttachment[];
}
export interface UserFeedbackListItem {
    id: string;
    appId: string;
    title: string | null;
    text: string;
    status: FeedbackStatus;
    issueStatus: IssueStatus;
    dialogueVersion: number;
    archiveRound: number;
    lastActivityAt: string;
    createdAt: string;
    unread: boolean;
    messageCount: number;
    latestMessage: {
        seq: number;
        senderType: string;
        senderName: string;
        text: string;
        createdAt: string;
    } | null;
}
export interface UserFeedbackListResponse {
    items: UserFeedbackListItem[];
    total: number;
    page: number;
    limit: number;
    unreadTotal: number;
}
export interface UnreadSummaryResponse {
    unreadFeedbacksCount: number;
    totalUnreadMessages: number;
}
export interface FeedbackDialogueResponse {
    feedback: {
        id: string;
        appId: string;
        title: string | null;
        text: string;
        status: FeedbackStatus;
        issueStatus: IssueStatus;
        dialogueVersion: number;
        archiveRound: number;
        mgmtState: string;
        createdAt: string;
        updatedAt: string;
        lastActivityAt: string;
        kaneoTaskId?: string | null;
        kaneoTaskUrl?: string | null;
        context?: Record<string, string> | null;
        screenshot?: {
            width: number;
            height: number;
            byteSize: number;
            sha256: string;
        } | null;
        logs?: Array<{
            id: string;
            filename: string;
            byteSize: number;
            sha256?: string;
            source: string;
        }> | null;
        /** v12：初始反馈的有序图片列表（历史单图记录经兼容读取进入同一列表）。 */
        images?: Array<{
            id: string;
            sort: number;
            source: string;
            mime: string;
            filename: string;
            byteSize: number;
            sha256: string;
            width?: number | null;
            height?: number | null;
            createdAt?: string;
        }> | null;
        /** v12：最近一次处理说明（管理员标记解决时必填）。 */
        resolutionNote?: string | null;
    };
    messages: FeedbackMessageItem[];
    lastReadSeq: number;
}
export interface ReplyPayload {
    text: string;
    idempotencyKey?: string;
    screenshot?: Blob;
    /** v12：回复图片（≤5，与 `screenshot` 互斥）。 */
    images?: FeedbackImageInput[];
    logs?: File[];
}
export declare function getFeedback(apiBase: string, token: string, id: string, opts?: {
    fetchFn?: FetchLike;
}): Promise<FeedbackRecord>;
export declare function getUnreadSummary(apiBase: string, token: string, appId?: string, opts?: {
    fetchFn?: FetchLike;
}): Promise<UnreadSummaryResponse>;
export declare function listMyFeedbacks(apiBase: string, token: string, opts?: {
    page?: number;
    limit?: number;
    appId?: string;
    issueStatus?: IssueStatus;
    fetchFn?: FetchLike;
}): Promise<UserFeedbackListResponse>;
export declare function getFeedbackDialogue(apiBase: string, token: string, feedbackId: string, opts?: {
    fetchFn?: FetchLike;
}): Promise<FeedbackDialogueResponse>;
export declare function sendFeedbackReply(apiBase: string, token: string, feedbackId: string, payload: ReplyPayload, opts?: {
    fetchFn?: FetchLike;
}): Promise<{
    ok: boolean;
    message: FeedbackMessageItem;
    dialogueVersion: number;
    issueStatus: IssueStatus;
}>;
export declare function markFeedbackRead(apiBase: string, token: string, feedbackId: string, lastReadSeq: number, opts?: {
    fetchFn?: FetchLike;
}): Promise<{
    ok: boolean;
    lastReadSeq: number;
}>;
/**
 * v12 附件取回：图片/日志 URL 不携带凭据，一律以 Bearer `fetch` 取回二进制
 * 后转对象 URL 渲染或触发下载——绝不把 URL 直接挂到 <img>/<a>（跨源下必然 401）。
 * 返回 Blob（调用方负责 `URL.createObjectURL` / `URL.revokeObjectURL`）。
 */
export declare function fetchAttachmentBlob(apiBase: string, token: string, path: string, opts?: {
    fetchFn?: FetchLike;
}): Promise<Blob>;
/** 按 Unicode 码点统计长度（契约要求 1..10000 码点）。 */
export declare function codePointLength(text: string): number;
export declare const MAX_TEXT = 10000;
