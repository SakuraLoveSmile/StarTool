/**
 * 截图生成与敏感区遮挡内部模块（实施计划 2.1 / 2.3）。
 *
 * 设计要点：
 * - 内置 html2canvas-pro 路径：在克隆上按布局查找密码输入与 [data-feedback-capture-mask]，
 *   修改克隆前先记录变换后的遮挡区域（裁定到截图视口），仅在克隆中隐藏敏感子树并保持
 *   原布局尺寸（visibility 方案，绝不替换节点、不改 display 类型）；清除背景图、图片
 *   来源与伪元素内容。
 * - 位图级最终保护：重绘得到最终输出位图后，在其上再填充不透明遮挡矩形（向外取整并
 *   外扩一个输出像素），随后逐区域采样验证；遮挡验证完成前绝不编码 PNG / 生成预览。
 * - 失败规则：有效且仍可见的敏感节点无法定位、坐标非有限、遮挡结果无法验证 → 本次截图
 *   失败（调用方保留旧草稿与旧截图）；明确在截图视口外可忽略；不允许静默跳过。
 * - captureProvider 扩展契约：保留原参数与返回值，新增可选 signal、视口信息与同帧遮挡
 *   信息；组件统一校验 PNG、尺寸、文件大小与遮挡坐标；页面存在可见敏感区域而提供者无法
 *   保证同帧遮挡时拒绝使用该截图；无敏感标记时旧回调保持兼容。
 * - 图片限制（两端统一）：scale = min(dpr, 2, 2048/最长逻辑边, sqrt(4_000_000/逻辑面积))；
 *   编码超过 5MiB 最多缩小重编码三次，仍超限则本次截图失败。
 */
export declare const MAX_CAPTURE_EDGE = 2048;
export declare const MAX_CAPTURE_PIXELS = 4000000;
export declare const MAX_PNG_BYTES: number;
export declare const MAX_RECODE_ATTEMPTS = 3;
export declare const RECODE_FACTOR = 0.8;
/** 遮挡覆盖色（与最终位图填充一致，供采样验证）。 */
export declare const MASK_COVER_RGB: {
    readonly r: 110;
    readonly g: 110;
    readonly b: 115;
};
export declare const MASK_COVER_CSS = "rgb(110, 110, 115)";
/** 用户可见的截图失败提示：保留旧草稿与旧截图，可重试或继续文字反馈。 */
export declare const CAPTURE_FAILURE_MESSAGE = "\u622A\u56FE\u672A\u5B8C\u6210\uFF0C\u53EF\u91CD\u8BD5\u6216\u7EE7\u7EED\u6587\u5B57\u53CD\u9988";
export interface CaptureRect {
    x: number;
    y: number;
    width: number;
    height: number;
}
export interface LogicalViewport {
    width: number;
    height: number;
    scrollX: number;
    scrollY: number;
    dpr: number;
}
export type CaptureErrorReason = 'aborted' | 'locate-failed' | 'provider-invalid' | 'provider-unsafe' | 'verify-failed' | 'size-limit';
export declare class CaptureError extends Error {
    readonly reason: CaptureErrorReason;
    /** 面向用户的提示（不含内部细节）。 */
    readonly userMessage: string;
    constructor(reason: CaptureErrorReason, detail: string, userMessage?: string);
}
export interface CaptureProviderContext {
    /** 原有参数：灵感球拖拽落点（0..1 视口比例）。 */
    releasePoint?: {
        x: number;
        y: number;
    };
    /** 新增：本次捕获的取消信号（会话失效时 abort）。 */
    signal?: AbortSignal;
    /** 新增：截图视口的逻辑信息（CSS 像素）。 */
    viewport?: LogicalViewport;
    /** 新增：宿主页面当前可见敏感区域数量（>0 时提供者必须给出同帧遮挡证明）。 */
    sensitiveRegionCount?: number;
}
export interface CaptureProviderResult {
    /** 原有返回：PNG 截图字节。 */
    blob: Blob;
    /** 原有返回：图像输出像素尺寸（旧回调语义）。 */
    width: number;
    height: number;
    /** 新增可选：逻辑视口尺寸（不传时按旧语义取 width/height）。 */
    viewport?: {
        width: number;
        height: number;
    };
    /** 新增可选：同帧遮挡证明——提供者必须确认遮挡与截图发生在同一帧。 */
    sameFrameMasking?: boolean;
    /**
     * 新增可选：同帧遮挡区域列表，坐标以输出像素计。
     * sameFrameMasking 为 true 时应列出全部被遮挡区域（可为空数组，表示本页面无敏感内容）。
     */
    maskedRegions?: CaptureRect[];
}
export type CaptureProvider = (ctx: CaptureProviderContext) => Promise<CaptureProviderResult>;
export interface ValidatedCapture {
    blob: Blob;
    viewportWidth: number;
    viewportHeight: number;
    outputWidth: number;
    outputHeight: number;
    maskedRegions: CaptureRect[];
}
/**
 * 组件统一校验 captureProvider 返回值：PNG 类型、尺寸上限、文件大小、遮挡坐标。
 * 页面存在可见敏感区域（sensitiveRegionCount > 0）而提供者未给出同帧遮挡证明时拒绝。
 */
export declare function validateProviderResult(res: CaptureProviderResult, sensitiveRegionCount: number): ValidatedCapture;
export interface CollectOptions {
    /** 返回 true 表示该元素（及其子树）不参与截图（如组件自身），跳过计数与遮挡。 */
    ignore?: (el: Element) => boolean;
}
/** 递归收集敏感节点（穿透 open shadow root）。 */
export declare function collectSensitiveNodes(root: Document | ShadowRoot | Element, out?: Element[]): Element[];
/** 收集元素子树（含 open shadow root 内容）的全部元素。 */
export declare function collectSubtreeElements(root: Element | ShadowRoot | Document, out?: Element[]): Element[];
interface ViewportClip {
    width: number;
    height: number;
}
/**
 * 计算一个敏感节点（mask 元素取其可见子树并集）在截图视口中的可见区域。
 * - 返回 null：不在截图范围内（明确可忽略）。
 * - 抛 CaptureError('locate-failed')：仍可见但坐标非有限 / 无法测量。
 */
export declare function sensitiveVisibleRect(el: Element, win: Window, vp: ViewportClip, opts: {
    subtree: boolean;
}): CaptureRect | null;
/**
 * 统计宿主页面中“有效且仍可见”（有布局、可见、与截图视口相交）的敏感节点数。
 * 坐标非有限时抛错——不允许静默跳过。
 */
export declare function countVisibleSensitiveRegions(doc: Document, opts?: CollectOptions): number;
export declare function logicalViewport(win: Window & typeof globalThis): LogicalViewport;
/** 两端统一缩放公式（实施计划 2.3）。 */
export declare function computeCaptureScale(vp: {
    width: number;
    height: number;
}, dpr: number): number;
/**
 * 仅在克隆中隐藏敏感子树：先记录遮挡区域，再修改克隆（顺序不可颠倒）。
 * 保持原布局尺寸：只设置 visibility / background-image / 图片来源，
 * 绝不替换节点、绝不改 display 类型（避免破坏 inline/flex/grid/table 布局）。
 *
 * expectedVisibleCount：真实文档中“有效且仍可见”的敏感节点数；克隆上按布局定位数
 * 不足说明有可见敏感节点无法定位 → 本次截图失败（不允许静默跳过）。
 */
export declare function recordAndApplyCloneMask(cloneDoc: Document, vp: ViewportClip, expectedVisibleCount?: number): CaptureRect[];
/** 逻辑视口坐标 → 输出像素矩形：向外取整并外扩一个输出像素，再裁到画布内。 */
export declare function maskRectToOutputPixels(rect: CaptureRect, ratioX: number, ratioY: number, canvasW: number, canvasH: number): CaptureRect;
/** 采样验证每个遮挡矩形确实被不透明覆盖色覆盖（含四角与内部网格）。 */
export declare function verifyCanvasMaskCoverage(canvas: HTMLCanvasElement, rectsPx: CaptureRect[]): void;
export interface BuiltinCaptureOptions {
    viewport: LogicalViewport;
    /** 真实文档中统计到的可见敏感节点数；克隆上定位数不足即失败。 */
    expectedSensitiveCount: number;
    signal?: AbortSignal;
    ignore?: (el: Element) => boolean;
    /** 测试注入：替代真实 html2canvas-pro 的动态导入。 */
    html2canvas?: (el: HTMLElement, options?: Record<string, unknown>) => Promise<HTMLCanvasElement>;
}
export interface BuiltinCaptureResult {
    blob: Blob;
    viewportWidth: number;
    viewportHeight: number;
    outputWidth: number;
    outputHeight: number;
    maskedRegions: CaptureRect[];
}
/**
 * 内置视口截图：克隆遮挡 → 重绘 → 最终位图填充 + 验证 → 全部通过后才编码 PNG。
 */
export declare function captureViewport(opts: BuiltinCaptureOptions): Promise<BuiltinCaptureResult>;
export {};
