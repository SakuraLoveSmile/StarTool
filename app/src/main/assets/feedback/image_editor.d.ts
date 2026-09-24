/**
 * v12 图片编辑器（面板内全屏覆盖层）。
 *
 * 统一交互（T2，两端一致）：**调整选区 → 应用裁剪 → 保存图片**。
 * - 裁剪工具的选区在松手后**持续绘制**（边框 + 外部暗化遮罩 + 八个控制点），
 *   支持选区内拖动移动、控制点缩放、覆盖层内重新框选；
 * - 右侧（窄屏为下方）独立「裁剪结果」预览随选区同步更新，并显示输出尺寸；
 * - 存在未应用选区时**禁止保存**（禁用并说明原因），避免保存内容与预期不同；
 * - 辅助边框 / 控制点 / 棋盘网格只存在于显示层：保存走 compose()（base + ops），
 *   绝不把任何辅助元素写进输出位图。
 *
 * 工具：裁剪、矩形标注（默认红 3px 描边）、不透明遮挡（与截图遮挡同色
 * rgb(110,110,115)）、撤销、取消、保存。
 *
 * 操作模型：对基础位图依次应用有序操作列表（矩形/遮挡直接绘制在当前画布坐标系，
 * 裁剪重建画布并切换到新坐标系）；撤销 = 弹出最后一个操作并重放。
 * 自动敏感遮挡在截图时已写入位图（见 capture.ts），早于编辑器且不可逆——
 * 编辑器里不提供「恢复被自动遮挡区域」的能力，用户操作只能继续叠加。
 */
/** 矩形标注默认色（红色 3px 描边）。 */
export declare const ANNOTATION_COLOR = "#ff3b30";
export declare const ANNOTATION_WIDTH = 3;
export type EditorTool = 'crop' | 'rect' | 'mask';
export interface ImageEditorHandle {
    /** 关闭编辑器（不保存）；等价于取消。 */
    close(): void;
    /** 编辑器当前是否挂载中。 */
    readonly open: boolean;
}
export interface OpenImageEditorOptions {
    /** 已解码的图片来源（ImageBitmap / HTMLImageElement / 测试注入的绘制源）。 */
    image: CanvasImageSource;
    width: number;
    height: number;
    /** 挂载点：组件 ShadowRoot（样式与事件都在 shadow 内）。 */
    mount: ParentNode;
    /** 保存：把最终画布交给调用方（编码与限额收缩由调用方负责）。 */
    onSave: (canvas: HTMLCanvasElement) => void | Promise<void>;
    onCancel?: () => void;
}
/**
 * 打开全屏图片编辑器；返回句柄（close 等价于取消）。
 * 编辑期间宿主面板仍在 shadow 内：覆盖层 z-index 高于面板与放大弹窗。
 */
export declare function openImageEditor(opts: OpenImageEditorOptions): ImageEditorHandle;
