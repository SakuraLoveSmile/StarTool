import java.security.MessageDigest
import java.util.HexFormat

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------
// P0 签名配置：优先级 环境变量(CI Secrets) > keystore.properties(本机) > debug 回退
// 没有 release 密钥时回退 debug 签名，保证本地与 CI 构建都能通过；但两次 CI 各自
// 生成随机 debug 密钥，覆盖安装会报 INSTALL_FAILED_UPDATE_INCOMPATIBLE，因此对外
// 分发的构建必须在 CI 配置 RELEASE_* Secrets（或提交 keystore.properties 的等价值）。
// ---------------------------------------------------------------------------
val keystoreProperties: Map<String, String> =
    rootProject.file("keystore.properties").let { f ->
        if (!f.exists()) {
            emptyMap()
        } else {
            // 手动解析 key=value：Kotlin DSL 中 `java` 扩展访问器会遮蔽 java.util 包名，
            // 而 java.util.Properties 又不在 Kotlin DSL 默认导入里，故不使用 Properties。
            f.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("!") }
                .mapNotNull { line ->
                    val idx = line.indexOf('=')
                    if (idx <= 0) {
                        null
                    } else {
                        line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                    }
                }
                .toMap()
        }
    }

fun releaseSecret(envKey: String, propKey: String): String? =
    System.getenv(envKey)?.trim()?.takeIf { it.isNotEmpty() }
        ?: keystoreProperties[propKey]?.trim()?.takeIf { it.isNotEmpty() }

val releaseStoreFilePath: String? = releaseSecret("RELEASE_STORE_FILE", "storeFile")
val releaseStorePassword: String? = releaseSecret("RELEASE_STORE_PASSWORD", "storePassword")
val releaseKeyAlias: String? = releaseSecret("RELEASE_KEY_ALIAS", "keyAlias")
val releaseKeyPassword: String? = releaseSecret("RELEASE_KEY_PASSWORD", "keyPassword")

val releaseKeystoreFile: File? =
    releaseStoreFilePath?.let { rootProject.file(it) }?.takeIf { it.exists() }

val hasReleaseKeystore: Boolean = releaseKeystoreFile != null &&
    releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null

if (!hasReleaseKeystore) {
    logger.lifecycle(
        "[StarTool] 未发现 release 密钥 -> release 构建回退 debug 签名（仅限开发，不可对外分发）。",
    )
}

android {
    namespace = "app.startool.android"
    compileSdk = 36

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "app.startool.android.gemini"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 端到端验收测试运行在独立 applicationId 的 e2e 构建类型上，
        // 避免测试清理真实试用数据。
        testBuildType = "e2e"
    }

    buildTypes {
        debug {
            // 试用 APK：固定 applicationId，使用本机 debug 签名。
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
        create("e2e") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".e2e"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        disable += listOf("UnusedIds")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.coroutines.android)
    implementation(libs.androidx.webkit)
    ksp(libs.room.compiler)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.org.json)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}

val manifestAppId = android.defaultConfig.applicationId ?: "app.startool.android.gemini"
val manifestVersionCode = android.defaultConfig.versionCode ?: 3
val manifestVersionName = android.defaultConfig.versionName ?: "0.1.2"
val manifestMinSdk = android.defaultConfig.minSdk ?: 26
val manifestOutputDir = layout.buildDirectory.dir("outputs")

tasks.register("generateUpdateManifest") {
    description = "根据当前构建元数据生成发布清单 startool-update.json"
    group = "publishing"
    val outDir = manifestOutputDir
    val id = manifestAppId
    val code = manifestVersionCode
    val name = manifestVersionName
    val minSdk = manifestMinSdk

    // P1：清单要携带 APK 的 sha256 / 体积 / 直链，必须等 APK 打包完成。
    dependsOn("packageRelease")
    val apkFile = layout.buildDirectory.file("outputs/apk/release/app-release.apk")

    doLast {
        // releaseUrl 必须指向「本次真正发布的 tag」，不能由 versionName 推导：
        // 两者不一致时（例如热修复只改 tag），清单里的链接会指向另一个旧 release。
        val releaseTag = if (System.getenv("GITHUB_REF_TYPE") == "tag") {
            System.getenv("GITHUB_REF_NAME") ?: "v$name"
        } else {
            "v$name"
        }
        val releaseUrl = "https://github.com/SakuraLoveSmile/StarTool/releases/tag/$releaseTag"

        val targetDir = outDir.get().asFile
        targetDir.mkdirs()
        val outFile = File(targetDir, "startool-update.json")

        val apk = apkFile.get().asFile
        var sha256Line = ""
        var sizeLine = ""
        var apkUrlLine = ""
        if (apk.exists()) {
            val digest = MessageDigest.getInstance("SHA-256")
            apk.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val sha256 = HexFormat.of().formatHex(digest.digest())
            sha256Line = ",\n          \"apkSha256\": \"$sha256\""
            sizeLine = ",\n          \"apkSizeBytes\": ${apk.length()}"

            // 直链只在「打 tag 的 CI 构建」下才与 GitHub Release 的资产名一致。
            // 本地 / workflow_dispatch 构建的资产名是 StarTool-<run>-release.apk，
            // 留空让客户端回退到 GitHub API 的 assets 解析，避免给出错误地址。
            if (System.getenv("GITHUB_REF_TYPE") == "tag") {
                apkUrlLine = ",\n          \"apkUrl\": \"https://github.com/SakuraLoveSmile/StarTool/releases/download/$releaseTag/StarTool-$releaseTag-release.apk\""
            }
        } else {
            logger.warn("APK 未找到，清单省略 apkSha256/apkSizeBytes/apkUrl: ${apk.absolutePath}")
        }

        val manifestJson = """
        {
          "schemaVersion": 2,
          "applicationId": "$id",
          "versionCode": $code,
          "versionName": "$name",
          "minSdk": $minSdk,
          "notes": "StarTool $name 版本发布",
          "releaseUrl": "$releaseUrl"$sha256Line$sizeLine$apkUrlLine
        }
        """.trimIndent()
        outFile.writeText(manifestJson)
        println("Generated update manifest: ${outFile.absolutePath}")
    }
}
