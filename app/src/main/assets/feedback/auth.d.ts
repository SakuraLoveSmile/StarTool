/**
 * 令牌登录使用的客户端标签（后台会话列表据此辨识来源）。
 * 形如 `web:<appId>`，长度受服务端 100 字符上限约束。
 */
export declare function webClientLabel(appId: string): string;
/** 登录失败提示（可安全展示）。区分凭据错误、来源未允许、限流与网络异常。 */
export declare function loginErrorMessage(err: unknown): string;
