import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val versionProps = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
val appVersionName: String = versionProps.getProperty("versionName").trim()

// 0.1.0 -> 100, 1.2.3 -> 10203. scripts/release_manifest.py, Updater.codeOf와 같은 규칙.
val appVersionCode: Int = appVersionName.split(".").map { it.toInt() }.let { (major, minor, patch) ->
    require(minor < 100 && patch < 100) { "minor/patch must be < 100: $appVersionName" }
    major * 10000 + minor * 100 + patch
}

// 서명 키는 저장소에 두지 않는다. CI는 GitHub Secrets에서, 로컬은 환경변수로 받는다.
// 키가 없으면 디버그 키로 서명하며, 그 APK는 정식 설치본을 업데이트할 수 없다.
val releaseKeystore: String? = System.getenv("AA_NAVI_KEYSTORE")?.takeIf { it.isNotBlank() && file(it).exists() }
val releaseKeyPassword: String? = System.getenv("AA_NAVI_KEYSTORE_PASSWORD")

android {
    namespace = "com.leepy0.aanavi"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.leepy0.aanavi"
        minSdk = 29
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = releaseKeyPassword
                keyAlias = "aanavi"
                keyPassword = releaseKeyPassword
                storeType = "pkcs12"
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
