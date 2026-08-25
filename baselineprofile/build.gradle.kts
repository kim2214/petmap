plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.androidx.baselineprofile)
}

// Baseline Profile 생성기 모듈. 앱(:app)의 nonMinifiedRelease 를 기기에서 구동해 시작·주요 화면의
// 핫 경로를 수집하고, 결과를 app/src/release/generated/baselineProfiles/ 에 저장한다.
android {
    namespace = "com.kimdev.petmap.baselineprofile"
    compileSdk = 37

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    defaultConfig {
        // 프로필 수집은 API 28+ (루팅) 또는 API 33+ (일반 기기·에뮬레이터)에서만 가능
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

baselineProfile {
    // Gradle Managed Device 대신 adb 로 연결된 기기/에뮬레이터를 쓴다 (이미지 다운로드 없이 로컬 AVD 재사용)
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
