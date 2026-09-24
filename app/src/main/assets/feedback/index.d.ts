import { FeedbackWidget, type FeedbackSubmittedDetail, type FeedbackLogFile, type FeedbackLogProvider, type DraftImage, type FeedbackHostSession, type FeedbackSessionStore, type FeedbackHostBridge } from './element';
export { FeedbackWidget };
export type { FeedbackSubmittedDetail, FeedbackLogFile, FeedbackLogProvider, DraftImage, FeedbackHostSession, FeedbackSessionStore, FeedbackHostBridge, };
export type { FeedbackStatus, FeedbackContext, FeedbackRecord, FeedbackCaptureInfo, FeedbackCaptureRegion, FeedbackLogAttachment, FeedbackSubmitPayload, FeedbackSubmitResponse, AuthUser, Quota, LoginResponse, SessionResponse, FeedbackCapabilities, FeedbackImageDescriptor, FeedbackImageInput, FeedbackImageSource, } from './api';
export { FeedbackDiagnosticsRecorder, FeedbackErrorCapture, wrapFetch, } from './diagnostics';
export type { DiagnosticsEntry, DiagnosticsRecorderOptions, FeedbackErrorCaptureOptions, } from './diagnostics';
export type { FetchLike } from './api';
export { normalizeServerBase } from './server_pref';
export type { NormalizeServerBaseResult } from './server_pref';
/** 元素标签名。 */
export declare const FEEDBACK_ELEMENT_TAG = "feedback-widget";
/** 幂等注册自定义元素（多次 import 安全）。 */
export declare function defineFeedbackWidget(tag?: string): typeof FeedbackWidget;
export interface OpenFeedbackOptions {
    apiBase?: string;
    appId?: string;
    appVersion?: string;
    pageLabel?: string;
    side?: 'left' | 'right';
    theme?: 'system' | 'light' | 'dark';
    showLauncher?: boolean;
    launcherBottom?: string;
    launcherMode?: 'tab' | 'orb';
    captureMode?: 'off' | 'viewport';
    logProvider?: import('./element').FeedbackLogProvider;
    /** v12：诊断记录器（注入后组件请求经 wrapFetch 记录；身份切换时清空缓冲）。 */
    diagnosticsRecorder?: import('./diagnostics').FeedbackDiagnosticsRecorder;
    sessionStore?: FeedbackSessionStore;
    hostBridge?: FeedbackHostBridge;
}
/**
 * 打开页面上的反馈面板；若尚无 `<feedback-widget>` 元素则动态创建。
 * 传入 options 时同步配置到（新建的或已有的）元素上。
 */
export declare function openFeedback(options?: OpenFeedbackOptions): FeedbackWidget;
