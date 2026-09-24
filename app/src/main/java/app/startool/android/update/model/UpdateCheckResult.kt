package app.startool.android.update.model

sealed interface UpdateCheckResult {
    data class UpdateAvailable(
        val manifest: UpdateManifest,
        val currentVersionCode: Int,
        val currentVersionName: String,
    ) : UpdateCheckResult

    data class UpToDate(
        val currentVersionCode: Int,
        val currentVersionName: String,
    ) : UpdateCheckResult

    data object NoReleaseFound : UpdateCheckResult

    data class IncompatibleSystem(
        val manifest: UpdateManifest,
        val currentSdk: Int,
        val minSdk: Int,
    ) : UpdateCheckResult

    data class NetworkError(
        val message: String,
        val cause: Throwable? = null,
    ) : UpdateCheckResult

    data class InvalidManifest(
        val message: String,
    ) : UpdateCheckResult
}
