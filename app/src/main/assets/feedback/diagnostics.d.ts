/**
 * v12 诊断日志接入（docs/integration.md §5.6）。
 *
 * - `FeedbackDiagnosticsRecorder`：通用内存诊断记录器，缓冲至多 200 条 /
 *   256KiB（超出丢弃最旧），白名单字段（时间、事件、错误码、请求状态、耗时、
 *   pageLabel），**不记录**请求正文、凭据、完整 URL 或任意异常文本；
 *   `clear()` 清空缓冲（账号/服务切换=新世代即丢弃旧写入）。
 * - `wrapFetch`：`fetch` 包装器，记录每次请求的 method、HTTP 状态与耗时
 *   （白名单字段，不记录 URL、正文或凭据）；返回可直接使用的 fetch 函数，
 *   不替换全局 fetch。
 * - `FeedbackErrorCapture`：显式启用的未捕获异常/未处理拒绝监听
 *   （window error / unhandledrejection），不全局替换 console，保留宿主
 *   原有异常处理（只是额外挂 listener），`uninstall()` 可卸载。
 *
 * `recorder.toLogFile()` 产出与 `FeedbackLogProvider` 兼容的
 * `{ filename, blob }` 附件（.jsonl，UTF-8 文本），随提交进入日志附件流程。
 */
export interface DiagnosticsEntry {
    /** 事件名（白名单短名，如 'fetch'、'error'、'unhandledrejection'、'note'）。 */
    event: string;
    /** ISO-8601 时间戳；缺省为记录时刻。 */
    at?: string;
    /** 错误码 / 异常构造名（如 'AbortError'、'TypeError'）；不记录 message 文本。 */
    code?: string;
    /** HTTP 状态码。 */
    status?: number;
    /** 耗时毫秒。 */
    durationMs?: number;
    /** HTTP 方法（GET/POST…）。 */
    method?: string;
    /** 宿主提供的页面标识。 */
    pageLabel?: string;
}
export interface DiagnosticsRecorderOptions {
    /** 记录时附带宿主页面标识（每条记录可单独覆盖）。 */
    pageLabel?: string | (() => string | null | undefined);
    /** 最大条数（默认 200）。 */
    maxEntries?: number;
    /** 最大字节数（默认 256KiB，按 JSONL 序列化字节数估算）。 */
    maxBytes?: number;
}
/** 内存诊断记录器（200 条 / 256KiB 环形丢弃最旧）。 */
export declare class FeedbackDiagnosticsRecorder {
    private readonly maxEntries;
    private readonly maxBytes;
    private readonly pageLabelOf;
    private entries;
    private bytes;
    private dropped;
    constructor(opts?: DiagnosticsRecorderOptions);
    /** 记录一条白名单事件；非白名单字段（message/正文/URL/凭据）一律被丢弃。 */
    record(entry: DiagnosticsEntry): void;
    /** 便捷：记录 HTTP 请求结果（不记录 URL/正文/凭据）。 */
    recordFetch(input: {
        method?: string;
        status?: number;
        durationMs?: number;
        code?: string;
    }): void;
    /** 当前缓冲条数（含被丢弃计数见 droppedCount）。 */
    get size(): number;
    /** 因上限被丢弃的旧条目数（诊断时可见「曾丢弃」）。 */
    get droppedCount(): number;
    /** 新世代：清空缓冲（账号/服务切换时调用，旧写入绝不带入新身份）。 */
    clear(): void;
    /** 序列化为 JSONL 文本（UTF-8）。空缓冲返回空串。 */
    toJsonl(): string;
    /**
     * 产出与 `FeedbackLogProvider` 兼容的附件：空缓冲返回 null（不附加空文件），
     * 否则返回 `{ filename, blob }`（.jsonl、UTF-8、≤1MiB 由缓冲上限保证）。
     */
    toLogFile(filename?: string): {
        filename: string;
        blob: Blob;
    } | null;
}
export type FetchLike = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;
/**
 * `fetch` 包装器：记录 method / HTTP 状态 / 耗时 / 网络错误码到 recorder，
 * 返回等价的 fetch 函数（Promise/异常原样透传）。**不修改全局 fetch**：
 * 需要全宿主生效时由宿主显式安装（如 `window.fetch = wrapFetch(recorder)`，
 * 并可自行保存旧引用卸载）。
 *
 * 白名单：只记录 method、status、durationMs、错误构造名；不记 URL、请求体、凭据。
 */
export declare function wrapFetch(recorder: FeedbackDiagnosticsRecorder, base?: FetchLike): FetchLike;
export interface FeedbackErrorCaptureOptions {
    /** 监听目标（默认 window）。 */
    target?: Window;
}
/**
 * 显式启用的异常捕获：往 window 上额外挂 `error` / `unhandledrejection`
 * 监听器——只记录异常的构造名与来源类型（**不记录 message/堆栈/URL**），
 * 不替换 console、不阻止宿主原有处理、不 preventDefault。`uninstall()` 卸载。
 */
export declare class FeedbackErrorCapture {
    private readonly recorder;
    private readonly target;
    private installed;
    private readonly onError;
    private readonly onRejection;
    constructor(recorder: FeedbackDiagnosticsRecorder, opts?: FeedbackErrorCaptureOptions);
    /** 是否已挂载监听器。 */
    get active(): boolean;
    /** 挂载监听（幂等）。宿主原有 window.onerror / 监听器完全不受影响。 */
    install(): void;
    /** 卸载监听（幂等）。 */
    uninstall(): void;
}
