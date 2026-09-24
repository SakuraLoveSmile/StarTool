/**
 * T1 局部截图选区（灵感球拖拽 → 松手 → 调整选区 → 确认 → 填写反馈）。
 *
 * 坐标约定（与冻结规格一致）：
 * - 选区状态 `RegionRect` 始终以**捕获视口逻辑像素**（capture.viewportWidth/Height
 *   描述的那个视口）为坐标系；覆盖层把冻结位图铺满整个窗口显示，显示时按
 *   「窗口尺寸 / 捕获视口尺寸」换算，指针事件再反向换算回选区坐标系。
 * - `NormalizedRegion`（提交用 `capture.region`）是 0..1 归一化值，同样相对
 *   **原始捕获视口**，并保证 x+width≤1、y+height≤1。
 * - 裁剪时归一化值直接乘冻结位图尺寸即可得到像素矩形（位图完整覆盖捕获视口），
 *   因此不依赖 DPR / 捕获 scale 的额外换算。
 *
 * 本模块保持纯函数（几何）+ 一个位图裁剪入口，DOM 交互在 element.ts 的覆盖层里。
 */
/** 选区矩形：捕获视口逻辑像素坐标。 */
export interface RegionRect {
    x: number;
    y: number;
    width: number;
    height: number;
}
/** 归一化选区（提交 metadata 的 `capture.region`）：0..1，相对原始捕获视口。 */
export interface NormalizedRegion {
    x: number;
    y: number;
    width: number;
    height: number;
}
export interface RegionViewport {
    width: number;
    height: number;
}
/** 选区最小边长（逻辑像素）。 */
export declare const MIN_REGION_SIZE = 48;
/** 初始选区宽 / 高上限（逻辑像素）。 */
export declare const REGION_DEFAULT_WIDTH = 400;
export declare const REGION_DEFAULT_HEIGHT = 260;
/** 键盘调整选区的步长（逻辑像素）。 */
export declare const REGION_KEY_STEP = 8;
/** 八个缩放控制点方位。 */
export type RegionHandle = 'nw' | 'n' | 'ne' | 'e' | 'se' | 's' | 'sw' | 'w';
export declare const REGION_HANDLES: readonly RegionHandle[];
/** 把选区夹回捕获视口内，并强制最小 48×48（视口本身更小时取视口尺寸）。 */
export declare function clampRegion(sel: RegionRect, viewport: RegionViewport): RegionRect;
/**
 * 松手位置处的初始选区：
 * 宽 `min(400, 视口宽×80%)`、高 `min(260, 视口高×40%)`，中心为松手点（0..1），
 * 靠边时整体移回可见区域内，并强制最小 48×48。
 */
export declare function initialRegionSelection(release: {
    x: number;
    y: number;
}, viewport: RegionViewport): RegionRect;
/** 移动选区（键盘方向键 / 拖动），保持尺寸并夹回视口。 */
export declare function moveRegion(sel: RegionRect, dx: number, dy: number, viewport: RegionViewport): RegionRect;
/** 按控制点方位缩放选区，强制最小 48×48 并夹回视口。 */
export declare function resizeRegion(sel: RegionRect, handle: RegionHandle, dx: number, dy: number, viewport: RegionViewport): RegionRect;
/** 由拖拽起点 / 终点归一化出选区（允许反向拖拽；小于 48×48 时撑到最小）。 */
export declare function regionFromDrag(start: {
    x: number;
    y: number;
}, end: {
    x: number;
    y: number;
}, viewport: RegionViewport): RegionRect;
/**
 * 选区 → 提交用 `capture.region`：0..1 归一化、相对原始捕获视口，
 * 向下取整到 1e-6，保证 x+width≤1、y+height≤1。
 */
export declare function normalizeRegion(sel: RegionRect, viewport: RegionViewport): NormalizedRegion;
/** 归一化选区 → 冻结位图像素矩形（整数、夹在位图内、至少 1×1）。 */
export declare function regionToBitmapRect(region: NormalizedRegion, bitmapWidth: number, bitmapHeight: number): {
    x: number;
    y: number;
    width: number;
    height: number;
};
export interface CroppedRegionImage {
    blob: Blob;
    width: number;
    height: number;
}
/**
 * 把冻结的全视口位图裁到选区：
 * 选区（捕获视口逻辑像素）→ 归一化 → 位图像素 → drawImage 源矩形 → PNG 编码
 * （编码沿用统一的尺寸 / 体积收缩规则）。
 *
 * 源矩形取整数像素，避免小数源坐标在最近邻采样下落到无效像素。
 */
export declare function cropFrozenRegion(blob: Blob, sel: RegionRect, viewport: RegionViewport): Promise<CroppedRegionImage>;
