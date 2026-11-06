# 초당월급 (SalaryTicker)

이 앱은 시작 버튼을 누르면 오늘 번 돈을 초 단위로 보여주고, 그 금액으로 랭킹 경쟁을 하는 Android 앱이다. Kotlin과 Jetpack Compose로 만들었다.

## 실행 방법
1. Android Studio에서 `Open`을 눌러 이 폴더를 연다.
2. Gradle Sync를 한다. wrapper jar 파일이 없다는 안내가 나오면 그대로 진행하거나 터미널에서 `gradle wrapper`를 실행한다.
3. 기기나 에뮬레이터에서 실행한 뒤 **설정** 탭에서 월급을 입력하고 **카운터** 탭에서 시작 버튼을 누른다.

## 구조
| 파일 | 역할 | Java/Spring으로 치면 |
|---|---|---|
| `domain/SalaryCalculator.kt` | 초당 급여와 누적액 계산 | static 유틸 클래스 |
| `data/SalaryPrefs.kt` | 설정과 세션을 DataStore에 저장 | DAO |
| `data/LeaderboardRepository.kt` | 랭킹 인터페이스와 Fake 구현체 | interface + Impl |
| `ui/MainViewModel.kt` | 1초 티커, 시작/정지, 랭킹 동기화 | Service |
| `ui/screens/*` | 카운터, 랭킹, 설정 화면 | View |

## 핵심 설계
- **공식**: 초당 급여 = 월급(연봉이면 ÷12) ÷ (이번 달 근무일 × 하루 근무시간 × 3600). 근무일은 `WorkCalendar`가 평일에서 공휴일을 빼서 자동 계산한다. [확인필요] 공휴일 목록은 매년 갱신해야 한다.
- **금액은 저장하지 않는다.** 시작 시각(`runningSinceMs`)만 저장하고, 화면을 그릴 때마다 `(현재시각 - 시작시각) × 초당급여`로 다시 계산한다. 그래서 앱을 꺼도 금액이 계속 쌓인 것으로 계산된다.
- **날짜가 바뀌면 리셋된다.** 랭킹 기준이 '오늘 누적'이기 때문이다.
- **랭킹 업로드는 10초마다 한 번만 한다.** 서버 비용을 줄이기 위해서다. 내 순위는 로컬의 실시간 값으로 덮어써서 매초 움직인다.

## 다음 단계
- Firebase 랭킹: `docs/FIREBASE_GUIDE.md`를 보면 된다. 클래스 하나만 교체하면 된다.
- 위젯이나 상단 알림에 금액을 표시하는 기능 [작성필요]
- 닉네임 비속어 필터와 랭킹 어뷰징 방지 (예: 하루 최대 근무시간 제한) [작성필요]
