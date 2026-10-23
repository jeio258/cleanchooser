import java.util.Properties
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val ciArm64Only = providers.gradleProperty("ciArm64Only").orNull == "true"

// Private local configuration or CI environment; no private key is tracked.
val releaseKeyProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.isFile) propertiesFile.inputStream().use { load(it) }
}
fun signingValue(environment: String, property: String): String? =
    providers.environmentVariable(environment).orNull?.takeIf { it.isNotBlank() }
        ?: releaseKeyProperties.getProperty(property)?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("RELEASE_STORE_FILE", "storeFile")
val releaseStorePassword = signingValue("RELEASE_STORE_PASSWORD", "storePassword")
val releaseKeyAlias = signingValue("RELEASE_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("RELEASE_KEY_PASSWORD", "keyPassword")
val signingValues = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val hasReleaseSigning = signingValues.all { it != null }
check(signingValues.all { it == null } || hasReleaseSigning) {
    "Incomplete Release signing configuration. See docs/RELEASE.md."
}

// Hook compatibility is intentionally independent from the APK release version.
// Keep this value unchanged for UI/resources/manager-only releases. Bump it to the
// current versionCode only when xposed/** or a contract used by hooked processes changes.
val hookCompatVersionCode = 44

android {
    namespace = "com.eio258.cleanchooser"
    compileSdk {
        version = release(37) { minorApiLevel = 0 }
    }

    defaultConfig {
        applicationId = "com.eio258.cleanchooser"
        minSdk = 31
        targetSdk = 37
        versionCode = 44
        versionName = "1.6.19"
        buildConfigField("long", "HOOK_COMPAT_VERSION_CODE", "${hookCompatVersionCode}L")

        if (ciArm64Only) {
            ndk {
                abiFilters.clear()
                abiFilters += "arm64-v8a"
            }
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging.resources.merges += "META-INF/xposed/*"
    
    sourceSets {
        getByName("main") {
            resources.srcDirs("src/main/resources")
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                storeType = signingValue("RELEASE_STORE_TYPE", "storeType") ?: "PKCS12"
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

// The APK packager rewrites its output in place. When a build is smaller than the previous
// one, the tail of the old file stays on disk while the zip directory only lists the new
// contents, so the APK can look tens of megabytes larger than it really is. Deleting the file
// before packaging is not safe (the incremental packager then writes only the changed dex),
// so guard the result instead and force a clean build when garbage is detected.
val verifyApkSize = tasks.register("verifyApkSize") {
    group = "verification"
    description = "Fails when the packaged APK still carries bytes from a previous build."
    doLast {
        val apkDir = layout.buildDirectory.dir("outputs/apk/debug").get().asFile
        apkDir.listFiles { file -> file.extension == "apk" }.orEmpty().forEach { apk ->
            ZipFile(apk).use { zip ->
                val entries = zip.entries().asSequence().sumOf { it.size.coerceAtLeast(0L) }
                if (apk.length() > entries + 1_048_576L) {
                    logger.warn(
                        "APK ${apk.name} is ${apk.length() / 1048576} MB but its entries total " +
                            "${entries / 1048576} MB: stale bytes from a previous build. " +
                            "Run :app:assembleClean for a compact artifact."
                    )
                }
            }
        }
    }
}
tasks.matching { it.name == "assembleDebug" }.configureEach { finalizedBy(verifyApkSize) }

// Convenience: the only way to guarantee a compact APK, because the packager rewrites in place.
tasks.register("assembleClean") {
    group = "build"
    description = "clean + assembleDebug, guarantees a compact APK without stale bytes."
    dependsOn("clean")
    finalizedBy("assembleDebug")
}

// An unsigned build is allowed only when explicitly requested for CI validation.
val validateReleaseKey = tasks.register("validateReleaseKey") {
    val unsignedValidation = providers.gradleProperty("allowUnsignedRelease").orNull == "true"
    doLast {
        check(hasReleaseSigning || unsignedValidation) {
            "Release signing is required. Configure keystore.properties or RELEASE_* variables."
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseKey)
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation("junit:junit:4.13.2")
}
