// Root build: declare plugin versions only / 루트 빌드: 플러그인 버전만 선언
plugins {
    id("com.android.application") version "8.5.2" apply false                 // Android Gradle Plugin / 안드로이드 그래들 플러그인
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false            // Kotlin Android / 코틀린 안드로이드
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.20" apply false     // Compose compiler (Kotlin 2.x) / 컴포즈 컴파일러
    id("com.google.gms.google-services") version "4.4.2" apply false           // Firebase config plugin / 파이어베이스 설정 플러그인
}
