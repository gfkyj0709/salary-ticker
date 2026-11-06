# 2단계: Firebase 랭킹 연결

## 1. Firebase 콘솔
1. 프로젝트를 만들고 Android 앱을 추가한다. 패키지명은 `com.yj.salaryticker`이다.
2. 받은 `google-services.json` 파일을 `app/` 폴더에 넣는다.
3. **Authentication**에서 익명 로그인을 켠다.
4. **Firestore**를 만든다.

## 2. Gradle
```kotlin
// build.gradle.kts (root) plugins
id("com.google.gms.google-services") version "4.4.2" apply false

// app/build.gradle.kts plugins
id("com.google.gms.google-services")

// app/build.gradle.kts dependencies
implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
implementation("com.google.firebase:firebase-firestore")
implementation("com.google.firebase:firebase-auth")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
```
[확인필요] 위 버전은 작성 시점 기준이다. 실제 최신 버전은 Firebase 공식 문서에서 확인한다.

## 3. 구현체 (`data/FirestoreLeaderboardRepository.kt`)
```kotlin
package com.yj.salaryticker.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * Firestore ranking: daily_rankings/{yyyy-MM-dd}/users/{uid}
 * 날짜별 컬렉션에 uid 단위로 오늘 누적액 저장.
 */
class FirestoreLeaderboardRepository : LeaderboardRepository {

    private val auth = FirebaseAuth.getInstance()      // Auth / 인증
    private val db = FirebaseFirestore.getInstance()   // DB / 파이어스토어

    /** @return 오늘 날짜 users 컬렉션 */
    private fun todayUsers() = db.collection("daily_rankings")
        .document(LocalDate.now().toString())          // Day doc / 날짜 문서
        .collection("users")                           // Users / 사용자 목록

    /**
     * @param nickname 닉네임
     * @param amount 누적액
     */
    override suspend fun submitMyScore(nickname: String, amount: Double) {
        val uid = auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid // Anonymous login / 익명 로그인
        todayUsers().document(uid).set(mapOf("nickname" to nickname, "amount" to amount)).await() // Upsert / 덮어쓰기
    }

    /** @return 상위 50명 실시간 Flow */
    override fun observeRanking(): Flow<List<RankEntry>> = callbackFlow {
        val reg = todayUsers()
            .orderBy("amount", Query.Direction.DESCENDING) // Sort on server / 서버 정렬
            .limit(50)                                     // Top 50 / 상위 50
            .addSnapshotListener { snap, _ ->
                val me = auth.currentUser?.uid             // My uid / 내 uid
                val list = snap?.documents.orEmpty().map { d ->
                    RankEntry(
                        userId = d.id,
                        nickname = d.getString("nickname") ?: "?",
                        amount = d.getDouble("amount") ?: 0.0,
                        isMe = d.id == me,                 // Mark me / 내 항목 표시
                    )
                }
                trySend(list)                              // Push / 방출
            }
        awaitClose { reg.remove() }                        // Unsubscribe / 구독 해제
    }
}
```

## 4. 교체
`MainViewModel.kt`에서 한 줄만 바꾼다.
```kotlin
private val leaderboard: LeaderboardRepository = FirestoreLeaderboardRepository()
```
`overlayMe()` 함수 안의 `FakeLeaderboardRepository.ME_ID`는 `auth.currentUser?.uid`로 바꾼다.

## 5. 보안 규칙 (최소)
```
rules_version = '2';
service cloud.firestore {
  match /databases/{db}/documents {
    match /daily_rankings/{day}/users/{uid} {
      allow read: if true;
      allow write: if request.auth != null && request.auth.uid == uid
                   && request.resource.data.amount is number
                   && request.resource.data.amount < 2000000; // 하루 상한 (어뷰징 방지)
    }
  }
}
```
[확인필요] 하루 상한 값은 서비스 정책에 맞게 다시 정해야 한다.
