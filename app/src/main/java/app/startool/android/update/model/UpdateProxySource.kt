package app.startool.android.update.model

/**
 * 更新与下载的代理镜像源（加速通道）。
 */
enum class UpdateProxySource(
    val id: String,
    val displayName: String,
    val proxyPrefix: String, // 代理前缀，例如 "https://ghproxy.net/"
) {
    Direct("direct", "官方直连 (GitHub)", ""),
    GhProxyNet("ghproxy_net", "国内加速 (ghproxy.net)", "https://ghproxy.net/"),
    GhFastTop("ghfast_top", "国内加速 (ghfast.top)", "https://ghfast.top/"),
    Custom("custom", "自定义代理前缀", "");

    fun wrapUrl(targetUrl: String, customPrefix: String = ""): String {
        val prefix = when (this) {
            Direct -> ""
            GhProxyNet -> GhProxyNet.proxyPrefix
            GhFastTop -> GhFastTop.proxyPrefix
            Custom -> customPrefix.trim().let { if (it.isNotEmpty() && !it.endsWith("/")) "$it/" else it }
        }
        if (prefix.isBlank()) return targetUrl
        val cleanTarget = targetUrl.trim()
        return "$prefix$cleanTarget"
    }

    companion object {
        fun fromId(id: String?): UpdateProxySource {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: Direct
        }
    }
}
