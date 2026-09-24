package app.startool.android.update

import app.startool.android.update.model.UpdateManifest

/**
 * P1：把更新清单解析成可直接下载的 APK 源。
 *
 * 职责边界：
 *  - 只负责「选出哪条 URL 下载」+「套用用户选择的代理镜像」；
 *  - 不发起网络请求，因此可以纯本地单元测试；
 *  - 安全：先校验原始地址是官方仓库直链，再套代理，避免镜像源被诱导指向任意域名。
 */
data class ApkSource(
    /** 已套用代理前缀的最终下载地址。 */
    val url: String,
    /** 清单声明的字节数；为空时由下载器回退到 HTTP Content-Length。 */
    val expectedSizeBytes: Long?,
    /** 清单声明的 SHA-256（小写十六进制）；为空时跳过哈希校验、只校验体积。 */
    val expectedSha256: String?,
)

class ApkSourceResolver(
    private val preferenceStore: UpdatePreferenceStore,
) {

    /**
     * @return 可下载的 APK 源；清单缺少合法直链时返回 null，
     *         调用方应降级到「跳浏览器」兜底路径。
     */
    fun resolve(manifest: UpdateManifest): ApkSource? {
        val raw = manifest.apkUrl?.trim().orEmpty()
        if (raw.isEmpty()) return null
        if (!raw.startsWith(OFFICIAL_DOWNLOAD_PREFIX, ignoreCase = false)) return null
        if (!raw.endsWith(".apk", ignoreCase = true)) return null
        if (raw.any { it.isWhitespace() }) return null

        val effectiveUrl = preferenceStore.proxySource.wrapUrl(
            targetUrl = raw,
            customPrefix = preferenceStore.customProxyPrefix,
        )

        return ApkSource(
            url = effectiveUrl,
            expectedSizeBytes = manifest.apkSizeBytes?.takeIf { it > 0L },
            expectedSha256 = manifest.apkSha256
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.length == SHA256_HEX_LENGTH && it.all { c -> c in "0123456789abcdef" } },
        )
    }

    companion object {
        /** 官方仓库前缀，与 UpdateChecker.OFFICIAL_RELEASE_URL_PREFIX 同源。 */
        const val OFFICIAL_DOWNLOAD_PREFIX = "https://github.com/SakuraLoveSmile/StarTool/"
        private const val SHA256_HEX_LENGTH = 64
    }
}
