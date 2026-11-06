// App module plugins / 앱 모듈 플러그인
plugins {
    id("com.android.application")             // Android app / 안드로이드 앱
    id("org.jetbrains.kotlin.android")        // Kotlin / 코틀린
    id("org.jetbrains.kotlin.plugin.compose") // Compose compiler / 컴포즈 컴파일러
    id("com.google.gms.google-services")      // Reads google-services.json / 파이어베이스 설정 읽기
}

android {
    namespace = "com.yj.salaryticker" // Package namespace / 패키지 네임스페이스
    compileSdk = 34                   // Compile against API 34 / API 34 기준 컴파일

    defaultConfig {
        applicationId = "com.yj.salaryticker" // Play Store ID / 플레이스토어 앱 ID
        minSdk = 26                           // Android 8.0+ / 안드로이드 8.0 이상
        targetSdk = 34                        // Target API / 타겟 API
        versionCode = 4                       // Internal version / 내부 버전
        versionName = "0.4.0"                 // Display version / 표시 버전
    }

    buildTypes {
        release {
            isMinifyEnabled = false // No shrinking for MVP / MVP라 난독화 안 함
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17 // Java 17 source / 자바 17 소스
        targetCompatibility = JavaVersion.VERSION_17 // Java 17 bytecode / 자바 17 바이트코드
    }

    kotlinOptions {
        jvmTarget = "17" // Kotlin JVM target / 코틀린 JVM 타겟
    }

    buildFeatures {
        compose = true // Enable Compose / 컴포즈 활성화
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00") // Compose version set / 컴포즈 버전 묶음
    implementation(composeBom)                                            // Apply BOM / BOM 적용

    implementation("androidx.core:core-ktx:1.13.1")                          // Kotlin extensions / 코틀린 확장
    implementation("androidx.activity:activity-compose:1.9.2")               // setContent {} / 컴포즈 액티비티
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")   // viewModel() in Compose / 컴포즈용 뷰모델
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")     // collectAsStateWithLifecycle / 생명주기 인식 수집
    implementation("androidx.datastore:datastore-preferences:1.1.1")         // Local key-value store / 로컬 저장소
    implementation("androidx.compose.ui:ui")                                 // Compose UI / 컴포즈 UI
    implementation("androidx.compose.material3:material3")                   // Material 3 / 머티리얼3
    implementation("androidx.compose.material:material-icons-core")          // Basic icons / 기본 아이콘
    implementation("androidx.compose.ui:ui-tooling-preview")                 // Preview / 프리뷰
    debugImplementation("androidx.compose.ui:ui-tooling")                    // Preview tooling (debug) / 디버그용 툴링
    testImplementation("junit:junit:4.13.2")                                 // Unit tests / 단위테스트

    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))      // Firebase version set / 파이어베이스 버전 묶음
    implementation("com.google.firebase:firebase-firestore")                 // Ranking DB / 랭킹 DB
    implementation("com.google.firebase:firebase-auth")                      // Anonymous login / 익명 로그인
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1") // Task.await() / 코루틴 연동
}
