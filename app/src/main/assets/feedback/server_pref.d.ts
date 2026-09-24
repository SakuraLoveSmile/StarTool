/**
 * T6：自定义 Feedback 服务器地址——规范化、校验与本机偏好存储。
 *
 * 覆盖偏好按「宿主存储空间（localStorage 源）+ appId + 规范化默认地址」隔离：
 * 换默认地址或换 appId 时读到的是不同槽位，绝不把 A 服务的覆盖带到 B。
 * 偏好只存服务器地址——绝不持久化草稿、密码或令牌。
 */
export type NormalizeServerBaseResult = {
    ok: true;
    base: string;
} | {
    ok: false;
    reason: string;
};
/**
 * 规范化用户输入的服务器地址。
 *
 * 规则：去空白 → 必须 http(s) → 主机非空 → 拒绝内嵌凭据 / 查询参数 / # 片段；
 * 输出为「协议小写 + host（URL 解析已小写主机名并省略默认端口）+ 路径前缀
 * （只去末尾斜杠，支持部署路径前缀）」。
 *
 * [opts.pageIsHttps] 为 true 时拒绝 http 覆盖：HTTPS 页面发起 HTTP 请求会被
 * 浏览器按混合内容拦截，提前在校验层给出可读原因。
 */
export declare function normalizeServerBase(raw: string, opts?: {
    pageIsHttps?: boolean;
}): NormalizeServerBaseResult;
/**
 * 覆盖偏好存储键：`feedback-widget.server-override.<base64url(规范化默认地址 + NUL + appId)>`。
 * 默认地址规范化失败时按其去空白原值派生——仍保证逐槽位隔离，不会串到其他身份的偏好。
 */
export declare function serverOverrideStorageKey(appId: string, defaultApiBase: string): string;
/** 读取已保存的覆盖地址（只读工具，测试与宿主调试可用）；
 * 存储不可用或所存值非法时返回 null（视为无覆盖）。 */
export declare function readServerOverride(appId: string, defaultApiBase: string): string | null;
/** 写入 / 清除覆盖偏好。返回是否成功落盘（失败时调用方提示「仅本次生效」）。 */
export declare function writeServerOverride(appId: string, defaultApiBase: string, value: string | null): boolean;
