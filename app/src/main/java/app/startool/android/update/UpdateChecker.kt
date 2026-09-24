package app.startool.android.update

import android.os.Build
import app.startool.android.update.model.UpdateCheckResult
import app.startool.android.update.model.UpdateManifest
import app.startool.android.update.model.UpdateProxySource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

class UpdateChecker(
    val preferenceStore: UpdatePreferenceStore,
    val currentApplicationId: String,
    val currentVersionCode: Int,
    val currentVersionName: String,
    val httpFetcher: HttpFetcher = DefaultHttpFetcher(),
    val currentSdkInt: Int = Build.VERSION.SDK_INT,
    val clock: () -> Long = System::currentTimeMillis,
    val updateUrl: String = DEFAULT_UPDATE_URL,
) {
    constructor(
        httpFetcher: HttpFetcher,
        preferenceStore: UpdatePreferenceStore,
        currentApplicationId: String,
        currentVersionCode: Int,
        currentVersionName: String,
    ) : this(
        preferenceStore,
        currentApplicationId,
        currentVersionCode,
        currentVersionName,
        httpFetcher,
    )

    private val fetchMutex = Mutex()
    private var inFlightFetch: Deferred<RawFetchResult>? = null
    private val _discoveredUpdate = MutableStateFlow<UpdateManifest?>(null)
    val discoveredUpdate: StateFlow<UpdateManifest?> = _discoveredUpdate.asStateFlow()

    fun clearDiscoveredUpdate() {
        _discoveredUpdate.value = null
    }

    private sealed interface RawFetchResult {
        data class Success(val manifest: UpdateManifest) : RawFetchResult
        data object NoReleaseFound : RawFetchResult
        data class NetworkError(val message: String, val cause: Throwable? = null) : RawFetchResult
        data class InvalidManifest(val message: String) : RawFetchResult
    }

    suspend fun checkUpdate(isManual: Boolean): UpdateCheckResult {
        val now = clock()
        if (!isManual) {
            if (!preferenceStore.autoCheckEnabled) {
                return UpdateCheckResult.UpToDate(currentVersionCode, currentVersionName)
            }
            val elapsed = now - preferenceStore.lastAutoCheckTimestamp
            if (elapsed in 0 until THROTTLE_INTERVAL_MS) {
                return UpdateCheckResult.UpToDate(currentVersionCode, currentVersionName)
            }
            val remindElapsed = now - preferenceStore.remindLaterTimestamp
            if (remindElapsed in 0 until REMIND_LATER_INTERVAL_MS) {
                return UpdateCheckResult.UpToDate(currentVersionCode, currentVersionName)
            }
        }

        val rawResult = performNetworkFetch()

        val result = when (rawResult) {
            is RawFetchResult.NoReleaseFound -> UpdateCheckResult.NoReleaseFound
            is RawFetchResult.NetworkError -> UpdateCheckResult.NetworkError(rawResult.message, rawResult.cause)
            is RawFetchResult.InvalidManifest -> UpdateCheckResult.InvalidManifest(rawResult.message)
            is RawFetchResult.Success -> evaluateManifest(rawResult.manifest, isManual)
        }
        if (result is UpdateCheckResult.UpdateAvailable) {
            _discoveredUpdate.value = result.manifest
        }
        if (!isManual && result !is UpdateCheckResult.NetworkError) {
            preferenceStore.lastAutoCheckTimestamp = clock()
        }

        return result
    }

    private suspend fun performNetworkFetch(): RawFetchResult {
        var isLeader = false
        val deferred: Deferred<RawFetchResult> = fetchMutex.withLock {
            val active = inFlightFetch
            if (active != null) {
                active
            } else {
                val newDeferred = CompletableDeferred<RawFetchResult>()
                inFlightFetch = newDeferred
                isLeader = true
                newDeferred
            }
        }

        if (!isLeader) {
            return deferred.await()
        }

        try {
            val result = executeNetworkFetch()
            (deferred as CompletableDeferred<RawFetchResult>).complete(result)
            return result
        } catch (e: CancellationException) {
            val errResult = RawFetchResult.NetworkError("检查更新已取消", e)
            (deferred as CompletableDeferred<RawFetchResult>).complete(errResult)
            throw e
        } catch (e: Throwable) {
            val errResult = RawFetchResult.NetworkError(e.message ?: "网络请求失败", e)
            (deferred as CompletableDeferred<RawFetchResult>).complete(errResult)
            return errResult
        } finally {
            fetchMutex.withLock {
                if (inFlightFetch === deferred) {
                    inFlightFetch = null
                }
            }
        }
    }

    private suspend fun executeNetworkFetch(): RawFetchResult {
        val proxy = preferenceStore.proxySource
        val customPrefix = preferenceStore.customProxyPrefix

        // 1. 如果配置了代理加速，优先尝试通过 ghproxy 拉取静态 release asset 清单
        if (proxy != UpdateProxySource.Direct) {
            val proxiedDirectManifestUrl = proxy.wrapUrl(DIRECT_MANIFEST_URL, customPrefix)
            val proxyResult = fetchManifestDirectUrl(proxiedDirectManifestUrl)
            if (proxyResult !is RawFetchResult.NetworkError) {
                return proxyResult
            }
        }

        // 2. 尝试标准 API 方式
        val response = try {
            httpFetcher.fetch(updateUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 如果官方 API 失败且此前未走过代理，回退尝试公共加速源兜底
            if (proxy == UpdateProxySource.Direct) {
                val fallbackUrl = UpdateProxySource.GhProxyNet.wrapUrl(DIRECT_MANIFEST_URL)
                val fallbackRes = fetchManifestDirectUrl(fallbackUrl)
                if (fallbackRes is RawFetchResult.Success || fallbackRes is RawFetchResult.NoReleaseFound) {
                    return fallbackRes
                }
            }
            return RawFetchResult.NetworkError(e.message ?: "网络请求异常", e)
        }

        if (response.statusCode == 404) {
            return RawFetchResult.NoReleaseFound
        }
        if (response.statusCode !in 200..299) {
            // API 非 2xx 时，尝试走代理直链兜底
            val fallbackUrl = if (proxy != UpdateProxySource.Direct) {
                proxy.wrapUrl(DIRECT_MANIFEST_URL, customPrefix)
            } else {
                UpdateProxySource.GhProxyNet.wrapUrl(DIRECT_MANIFEST_URL)
            }
            val fallbackRes = fetchManifestDirectUrl(fallbackUrl)
            if (fallbackRes is RawFetchResult.Success || fallbackRes is RawFetchResult.NoReleaseFound) {
                return fallbackRes
            }
            return RawFetchResult.NetworkError("HTTP ${response.statusCode}")
        }
        val body = response.body
        if (body.isNullOrBlank()) {
            return RawFetchResult.InvalidManifest("清单内容为空")
        }

        return parseManifestOrAsset(body)
    }

    private suspend fun fetchManifestDirectUrl(url: String): RawFetchResult {
        return try {
            val resp = httpFetcher.fetch(url)
            if (resp.statusCode == 404) {
                RawFetchResult.NoReleaseFound
            } else if (resp.statusCode in 200..299 && !resp.body.isNullOrBlank()) {
                val manifest = UpdateManifest.fromJson(resp.body)
                if (manifest != null) RawFetchResult.Success(manifest) else RawFetchResult.InvalidManifest("清单格式错误")
            } else {
                RawFetchResult.NetworkError("HTTP ${resp.statusCode}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RawFetchResult.NetworkError(e.message ?: "加速源请求失败", e)
        }
    }
    private suspend fun parseManifestOrAsset(body: String): RawFetchResult {
        val trimmed = body.trim()
        if (trimmed == "[]") {
            return RawFetchResult.NoReleaseFound
        }

        if (trimmed.startsWith("[")) {
            val array = try {
                JSONArray(trimmed)
            } catch (_: Exception) {
                return RawFetchResult.InvalidManifest("清单解析失败")
            }
            if (array.length() == 0) {
                return RawFetchResult.NoReleaseFound
            }
            val firstRelease = array.optJSONObject(0)
                ?: return RawFetchResult.NoReleaseFound
            return parseReleaseJsonObject(firstRelease)
        }

        val json = try {
            JSONObject(trimmed)
        } catch (_: Exception) {
            return RawFetchResult.InvalidManifest("清单解析失败")
        }

        if (json.optString("message") == "Not Found") {
            return RawFetchResult.NoReleaseFound
        }

        if (json.has("schemaVersion")) {
            val manifest = UpdateManifest.fromJson(trimmed)
                ?: return RawFetchResult.InvalidManifest("清单格式错误")
            return RawFetchResult.Success(manifest)
        }

        if (json.has("assets")) {
            return parseReleaseJsonObject(json)
        }

        return RawFetchResult.InvalidManifest("未知清单格式")
    }

    private suspend fun parseReleaseJsonObject(releaseJson: JSONObject): RawFetchResult {
        val assets = releaseJson.optJSONArray("assets")
        if (assets == null || assets.length() == 0) {
            return RawFetchResult.NoReleaseFound
        }
        var downloadUrl: String? = null
        var detectedApkUrl: String? = null
        var detectedApkSize: Long? = null
        // 注意不能 break：APK 资产可能排在清单资产之前，必须扫完全部 assets。
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val assetName = asset.optString("name")
            if (assetName == MANIFEST_ASSET_NAME) {
                downloadUrl = asset.optString("browser_download_url")
                continue
            }
            // P1：顺带捕获 APK 直链与体积，作为清单缺少 apkUrl 时的兜底下载源。
            if (assetName.endsWith(".apk", ignoreCase = true)) {
                val candidate = asset.optString("browser_download_url")
                if (candidate.startsWith(ApkSourceResolver.OFFICIAL_DOWNLOAD_PREFIX)) {
                    detectedApkUrl = candidate
                    detectedApkSize = asset.optLong("size").takeIf { it > 0L }
                }
            }
        }
        if (downloadUrl.isNullOrBlank()) {
            return RawFetchResult.NoReleaseFound
        }

        val proxy = preferenceStore.proxySource
        val effectiveDownloadUrl = proxy.wrapUrl(downloadUrl, preferenceStore.customProxyPrefix)

        val assetResponse = try {
            httpFetcher.fetch(effectiveDownloadUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return RawFetchResult.NetworkError(e.message ?: "获取更新清单失败", e)
        }

        if (assetResponse.statusCode == 404) {
            return RawFetchResult.NoReleaseFound
        }
        if (assetResponse.statusCode !in 200..299) {
            return RawFetchResult.NetworkError("HTTP ${assetResponse.statusCode}")
        }
        val assetBody = assetResponse.body
        if (assetBody.isNullOrBlank()) {
            return RawFetchResult.InvalidManifest("清单内容为空")
        }
        val manifest = UpdateManifest.fromJson(assetBody)
            ?: return RawFetchResult.InvalidManifest("清单格式错误")
        // P1：清单自身没有 apkUrl/apkSizeBytes 时，用刚从 release assets 解析到的直链补齐，
        // 这样老清单（schemaVersion 1）也能走应用内下载。
        val enriched = manifest.copy(
            apkUrl = manifest.apkUrl ?: detectedApkUrl,
            apkSizeBytes = manifest.apkSizeBytes ?: detectedApkSize,
        )
        return RawFetchResult.Success(enriched)
    }
    private fun evaluateManifest(manifest: UpdateManifest, isManual: Boolean): UpdateCheckResult {
        if (manifest.applicationId != currentApplicationId) {
            return UpdateCheckResult.InvalidManifest("应用标识不符")
        }
        if (!manifest.releaseUrl.startsWith(OFFICIAL_RELEASE_URL_PREFIX)) {
            return UpdateCheckResult.InvalidManifest("非官方下载链接")
        }
        if (manifest.minSdk > currentSdkInt) {
            return UpdateCheckResult.IncompatibleSystem(
                manifest = manifest,
                currentSdk = currentSdkInt,
                minSdk = manifest.minSdk,
            )
        }
        if (!isManual) {
            val ignored = preferenceStore.ignoredVersionCode
            if (ignored != null && manifest.versionCode <= ignored) {
                return UpdateCheckResult.UpToDate(currentVersionCode, currentVersionName)
            }
        }
        return if (manifest.versionCode > currentVersionCode) {
            UpdateCheckResult.UpdateAvailable(
                manifest = manifest,
                currentVersionCode = currentVersionCode,
                currentVersionName = currentVersionName,
            )
        } else {
            UpdateCheckResult.UpToDate(
                currentVersionCode = currentVersionCode,
                currentVersionName = currentVersionName,
            )
        }
    }

    fun markRemindLater(timestamp: Long = clock()) {
        preferenceStore.remindLaterTimestamp = timestamp
    }

    fun ignoreVersion(versionCode: Int) {
        preferenceStore.ignoredVersionCode = versionCode
    }

    fun clearIgnoredVersion() {
        preferenceStore.ignoredVersionCode = null
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        preferenceStore.autoCheckEnabled = enabled
    }

    fun getEffectiveReleaseUrl(manifest: UpdateManifest): String {
        val proxy = preferenceStore.proxySource
        return proxy.wrapUrl(manifest.releaseUrl, preferenceStore.customProxyPrefix)
    }

    companion object {
        const val DEFAULT_UPDATE_URL = "https://api.github.com/repos/SakuraLoveSmile/StarTool/releases/latest"
        const val DIRECT_MANIFEST_URL = "https://github.com/SakuraLoveSmile/StarTool/releases/latest/download/startool-update.json"
        const val MANIFEST_ASSET_NAME = "startool-update.json"
        const val OFFICIAL_RELEASE_URL_PREFIX = "https://github.com/SakuraLoveSmile/StarTool/"
        const val THROTTLE_INTERVAL_MS = 24 * 3600 * 1000L
        const val REMIND_LATER_INTERVAL_MS = 24 * 3600 * 1000L
    }
}
