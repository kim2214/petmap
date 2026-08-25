pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 네이버 지도 SDK 저장소
        maven { url = uri("https://repository.map.naver.com/archive/maven") }
    }
}

rootProject.name = "PetMap"
include(":app")
// Baseline Profile 생성기 (com.android.test). 앱 빌드에는 프로필 파일만 쓰이고 모듈 자체는 실려 가지 않는다
include(":baselineprofile")
 