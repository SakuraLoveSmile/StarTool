/**
 * v12 多图附件：客户端校验、解码、等比缩小与「不可绘制内容」检测。
 *
 * 与截图（capture.ts）的限制一致：最长边 ≤2048px、≤400 万像素、≤5 MiB；
 * 格式仅限 PNG / JPEG / WebP 静态图（动图 WebP 拒绝）。手动图片先魔数校验
 * 再解码；超出尺寸/像素上限的等比缩小重编码，仍超体积上限的提示但保留草稿。
 */
export declare const MAX_IMAGES = 5;
export declare const MAX_IMAGE_EDGE = 2048;
export declare const MAX_IMAGE_PIXELS = 4000000;
export declare const MAX_IMAGE_BYTES: number;
/** 体积超限时的重编码次数（与截图 MAX_RECODE_ATTEMPTS 一致）。 */
export declare const MAX_IMAGE_RECODE_ATTEMPTS = 3;
export type ImageMime = 'image/png' | 'image/jpeg' | 'image/webp';
export interface DecodedImage {
    width: number;
    height: number;
    /** CanvasImageSource（真实浏览器为 ImageBitmap / HTMLImageElement；测试可注入任意绘制源）。 */
    source: CanvasImageSource;
    /** 使用后是否需要 close()（ImageBitmap 资源释放）。 */
    close?: () => void;
}
export interface PreparedImage {
    blob: Blob;
    mime: ImageMime;
    width: number;
    height: number;
    /** 最终字节数仍 >5 MiB：提示用户但不丢草稿（提交会被服务端 413 拒绝）。 */
    oversize: boolean;
}
export type PrepareImageError = 'invalid_type' | 'decode_failed' | 'empty';
export declare class ImageInputError extends Error {
    readonly reason: PrepareImageError;
    constructor(reason: PrepareImageError, detail: string);
}
/** 文件名基名（去路径分隔符）；无扩展名或扩展名不符时按 mime 修正。 */
export declare function normalizeImageFilename(name: string, mime: ImageMime, fallback: string): string;
export interface DecodeOptions {
    /** 测试注入：替代 createImageBitmap / <img> 解码。 */
    decode?: (blob: Blob) => Promise<DecodedImage>;
}
/** 解码图片为可绘制源（优先 createImageBitmap，降级 <img>）。 */
export declare function decodeImage(blob: Blob, opts?: DecodeOptions): Promise<DecodedImage>;
/** 等比缩小到限制内（最长边 / 总像素），返回缩放后的目标尺寸（不变则返回原尺寸）。 */
export declare function fitWithinLimits(width: number, height: number): {
    width: number;
    height: number;
};
/**
 * 规范化待提交图片：解码 → 需要时等比缩小到边/像素上限 → 体积仍超 5MiB 时
 * 继续缩小重编码（至多 MAX_IMAGE_RECODE_ATTEMPTS 次）。
 *
 * 输出 mime：保持原格式重编码（JPEG/WebP）；重编码失败或来源已是 PNG 时用 PNG。
 * 仍超限时返回 oversize=true（调用方提示并保留草稿，由服务端最终裁决）。
 */
export declare function prepareImageForUpload(blob: Blob, opts?: DecodeOptions): Promise<PreparedImage>;
/**
 * 对已解码图片重新导出（编辑器保存结果）：渲染出的位图按 PNG 编码并套用
 * 同一套尺寸/体积收缩规则。canvas 为空或编码失败返回 null。
 */
export declare function exportEditedImage(canvas: HTMLCanvasElement): Promise<PreparedImage | null>;
/**
 * 统计视口内**可能无法进入截图位图**的嵌入内容：
 * - 跨源 iframe（未授权 CORS 的嵌入页无法被 DOM 重绘捕获）；
 * - 跨源 <img>/<image> 未声明 crossorigin（html2canvas useCORS 下会拉取失败或污染画布）。
 * 返回检测到的元素数量；>0 时组件在预览旁提示「部分内容可能未入图」并允许手动加图。
 */
export declare function countUndrawableContent(doc: Document, ignore?: (el: Element) => boolean): number;
