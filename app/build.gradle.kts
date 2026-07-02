import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// local.properties 에서 비공개 키를 읽어 BuildConfig / Manifest 로 주입한다.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(key: String, default: String = "") = localProps.getProperty(key) ?: default

// 릴리스 서명 설정(keystore.properties). 없으면 디버그 서명으로 폴백한다(로컬 검증용).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "com.kimdev.petmap"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kimdev.petmap"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 네이버 지도 Client ID (NCP key id) — local.properties 의 naver.map.clientId 값
        manifestPlaceholders["naverMapClientId"] = prop("naver.map.clientId", "PLACEHOLDER")
        // 공공데이터포털 서비스 키
        buildConfigField("String", "PUBLIC_DATA_SERVICE_KEY", "\"${prop("public.data.serviceKey")}\"")

        // AdMob: 디버그 빌드는 Google 공식 테스트 ID 사용(아래 release 에서 실제 ID 로 덮어씀).
        // ID 는 APK 에 포함되어 비밀이 아니므로 하드코딩한다.
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
        buildConfigField(
            "String", "ADMOB_BANNER_UNIT_ID",
            "\"ca-app-pub-3940256099942544/6300978111\"",
        )
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 릴리스 빌드만 실제 AdMob ID 사용
            manifestPlaceholders["admobAppId"] = "ca-app-pub-1641853512361199~7767904833"
            buildConfigField(
                "String", "ADMOB_BANNER_UNIT_ID",
                "\"ca-app-pub-1641853512361199/6256079867\"",
            )
            // 실제 keystore 가 있으면 릴리스 서명, 없으면 로컬 검증용으로 디버그 서명
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    // ABI 별 APK 분리(네이티브 라이브러리 4종 → 기기별 1종)로 APK 용량 대폭 축소.
    // release 빌드에만 적용(debug 는 유니버설 app-debug.apk 유지해 개발 설치 편의).
    // 스토어 배포는 bundleRelease(.aab) 권장.
    splits {
        abi {
            // APK 빌드(assembleRelease)에서만 ABI 분리. bundleRelease(.aab)는 Play 가 분리하므로 끔.
            val isApkRelease = gradle.startParameter.taskNames.any {
                it.contains("assembleRelease", ignoreCase = true)
            }
            isEnable = isApkRelease
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            isUniversalApk = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // minSdk 24 에서 java.time 등 Java 8+ API 사용을 위한 디슈가링
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Java 8+ API 디슈가링(java.time 등) — minSdk 24 지원
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // AndroidX core / lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform) // 광고 동의(UMP)
    implementation(libs.play.review)             // 인앱 리뷰

    // Firebase (Crashlytics + Analytics). 실제 동작은 google-services.json 이 있어야 활성화됨.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Hilt (DI)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Room (local DB)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Map + Location
    implementation(libs.naver.map.compose)
    implementation(libs.naver.map.location)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.accompanist.permissions)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// google-services.json 이 있을 때만 Firebase 플러그인 적용 (없으면 빌드는 정상, Firebase 비활성)
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
    println("Firebase: google-services.json 발견 → Crashlytics/Analytics 활성화")
} else {
    println("Firebase: google-services.json 없음 → Firebase 비활성(앱은 정상 빌드)")
}
