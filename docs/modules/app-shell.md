# App Shell Module

## 책임

- 앱 전체 화면 상태 관리
- 홈 화면에서 카드 사용내역/일정/채팅 진입 제공
- Drawer 기반 화면 전환
- UI 액션을 도메인 로직에 연결
- 문서 보기, 모델 화면, 일정 화면 같은 상위 네비게이션 제어

## 먼저 읽을 파일

- `app/src/main/java/com/example/gemma4ondevicetest/MainActivity.kt`
- `app/src/main/java/com/example/gemma4ondevicetest/GemmaComposeUi.kt`

## 이 모듈이 가진 상태

- 현재 화면 `AppScreen`
- 홈 카드에 노출할 카드 요약 상태
- 홈 일정 카드에 반영할 일정 상태
- 현재 세션과 세션 목록
- 선택된 모델 소스
- 모델 로드 상태와 로딩 메시지
- 지식 도구 목록
- 문서 업로드 임시 상태
- 일정 화면 UI 상태
- 카드 사용내역 화면용 최근 거래/권한 상태

## 진입점

- 앱 시작: `MainActivity.onCreate()`
- 모델 로드: 사용자 명시 액션 또는 허용 조건을 통과한 Worker만 수행
- 화면 전환: `GemmaApp(...)` 콜백 연결
- 런타임 상태 동기화: `refreshRuntimeState()`

## 요구사항별로 볼 위치

| 요구사항 | 볼 파일/함수 |
|---|---|
| 새 화면 추가 | `GemmaComposeUi.kt`의 `AppScreen`, `GemmaApp`, `AppDrawer` |
| 홈 화면 진입 카드 수정 | `GemmaComposeUi.kt`의 `HomeScreen` |
| 홈 일정 카드 수정 | `GemmaComposeUi.kt`의 `HomeScreen`, `scheduleUiState` |
| 카드 사용내역 화면 수정 | `GemmaComposeUi.kt`, `MainActivity.refreshWalletState()` |
| Drawer 메뉴 수정 | `GemmaComposeUi.kt` |
| 앱 시작 시 초기화 수정 | `MainActivity.onCreate()` |
| 화면별 상태 연결 수정 | `MainActivity.setContent { GemmaApp(...) }` |
| 모델 로드/언로드 버튼 동작 수정 | `MainActivity.toggleModel()` |

## 수정 시 주의

- 현재 오케스트레이션이 `MainActivity`에 집중되어 있어, 화면 액션 수정이 다른 모듈 상태와 쉽게 얽힙니다.
- `refreshRuntimeState()`는 모델 상태, 지식 도구, 사용자 문서를 함께 갱신하므로 호출 타이밍을 함부로 줄이면 UI 동기화가 깨질 수 있습니다.
- `onStop()`에서 `LlmEngine.free()`가 호출되므로 백그라운드 전환 시 모델 유지 정책을 바꾸려면 여기부터 봐야 합니다.

## 함께 읽을 문서

- 채팅 수정이면 `docs/modules/chat-session.md`
- 모델 동작 수정이면 `docs/modules/model-runtime.md`
- 일정 화면 수정이면 `docs/modules/schedule.md`
