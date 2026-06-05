# Architecture

## 목적

이 문서는 `README.md`와 `docs/modules/*.md`를 묶어 현재 시스템이 어떻게 연결되는지 설명합니다.  
세부 구현보다 모듈 경계, 데이터 흐름, 변경 시 영향 범위를 빠르게 파악하는 데 목적이 있습니다.

## 시스템 한 줄 요약

사용자 입력을 받아 온디바이스 Gemma 모델로 응답하는 금융 도우미 Android 앱이며, 홈 화면에서 카드 사용내역, 일정, 채팅으로 진입합니다. 필요 시 로컬 지식 문서를 프롬프트에 주입하고, 일정 모듈은 캘린더 데이터를 요약해 알림으로 보냅니다. 삼성 Wallet 알림에서 카드 사용금액을 추출하는 비AI 백그라운드 수집 기능도 포함합니다.

## 모듈 맵

| 모듈 | 역할 | 상세 문서 |
|---|---|---|
| App Shell | 홈/카드/일정/채팅 포함 전역 상태, 화면 전환, UI 액션 오케스트레이션 | `docs/modules/app-shell.md` |
| Chat Session | 세션/메시지 저장, 채팅 응답 흐름 | `docs/modules/chat-session.md` |
| Model Runtime | 모델 선택, 다운로드, 엔진 로드, 추론 | `docs/modules/model-runtime.md` |
| Knowledge Routing | 질문 분류, 문서 선택, 프롬프트 주입 | `docs/modules/knowledge-routing.md` |
| Document Import | txt 업로드, 섹션 분석, 사용자 문서 저장 | `docs/modules/document-import.md` |
| Schedule | 캘린더 조회, 요약, 알림 스케줄링 | `docs/modules/schedule.md` |
| Build And Assets | Gradle, manifest, 번들 모델, 다운로드 스크립트 | `docs/modules/build-assets.md` |
| Wallet Notification Intake | 삼성 Wallet 알림 수집과 권한 진입점 | `docs/modules/wallet-notification-intake.md` |
| Wallet Transaction Parser | 알림 문구에서 거래 정보 추출 | `docs/modules/wallet-transaction-parser.md` |
| Card Expense Ledger | 거래 저장, 중복 제거, 집계 | `docs/modules/card-expense-ledger.md` |
| Wallet Expense Feature | 기능 전체 연결 구조와 구현 순서 | `docs/modules/wallet-expense-feature.md` |

## 상위 계층 구조

```text
UI Layer
  GemmaComposeUi.kt
  ScheduleScreen.kt

Application Layer
  MainActivity.kt

Domain/Coordinator Layer
  AgentRouter.kt
  KnowledgePromptBuilder.kt
  DocumentImporter.kt
  SchedulePromptBuilder.kt
  ScheduleWorkScheduler.kt

Runtime/Infrastructure Layer
  LlmEngine.kt
  ModelStore.kt
  ManifestLoader.kt
  ChatSessionStore.kt
  CalendarReader.kt
  ScheduleWorker.kt
  WalletNotificationListenerService.kt
  CardTransactionStore.kt

Static/Data Layer
  assets/knowledge/*
  manifest.json
  filesDir/knowledge/*
  SharedPreferences
  wallet transaction storage
```

## 핵심 런타임 흐름

### 1. 앱 시작

1. `MainActivity.onCreate()`가 홈을 기본 화면으로 두고 세션, 모델 선택, 일정 설정을 복원합니다.
2. `refreshRuntimeState()`가 모델 상태와 지식 도구 상태를 UI에 반영합니다.
3. 모델이 이미 준비되어 있으면 `autoLoadMainModel()`이 로드를 시도합니다.
4. `GemmaApp(...)`가 전역 상태와 콜백을 받아 화면을 구성합니다.

### 2. 일반 채팅 응답

1. 사용자가 메시지 전송
2. `sendPrompt()`가 세션에 사용자 메시지를 추가
3. `buildModelPrompt()`가 질문을 검사
4. 지식 매칭이 없으면 일반 프롬프트 사용
5. 지식 매칭이 있으면 관련 문서를 프롬프트에 주입
6. `LlmEngine.generateForSession()`이 세션별 응답 생성
7. 응답을 세션에 저장하고 UI에 표시

### 3. 지식 문서 주입 응답

1. `ManifestLoader.getTools()`가 번들/사용자 문서를 병합
2. `AgentRouter.route()`가 키워드 기반으로 카테고리 선택
3. `KnowledgePromptBuilder`가 선택 문서를 읽음
4. 참고 문서를 포함한 프롬프트를 생성
5. 모델 응답 상단에 참고 문서 목록 추가

### 4. 사용자 문서 업로드

1. 사용자가 txt 선택
2. `DocumentImporter`가 섹션 분리와 분류 추천 수행
3. UI에서 사용자가 분류를 확정
4. 섹션별 txt 파일을 내부 저장소에 기록
5. `manifest_override.json`을 갱신
6. 다음 질문부터 병합된 지식 문서로 사용

### 5. 일정 요약과 알림

1. 일정 화면 또는 스케줄러가 캘린더 데이터를 조회
2. `SchedulePromptBuilder`가 일정 요약 프롬프트 생성
3. `LlmEngine`이 요약 결과 생성
4. 결과가 부족하면 raw fallback 사용
5. 화면에 표시하거나 알림 본문으로 발송

### 6. 삼성 Wallet 카드 사용금액 수집

1. `NotificationListenerService`가 삼성 Wallet 알림 수신
2. 현재 월 키를 확인하고 필요 시 저장소 리셋
3. 수집 모듈이 원시 알림을 필터링
4. raw 알림을 당월 저장소에 기록
5. 파서 모듈이 카드명, 금액, 승인시각, 가맹점을 규칙 기반으로 추출
6. ledger 모듈이 중복 여부를 확인한 뒤 저장
7. 홈 또는 카드 사용내역 화면에서 최근 거래/월별 합계를 조회

### 7. 홈 화면 요약

1. 홈 화면이 카드 사용내역, 일정, 채팅 진입 카드를 노출
2. 카드 영역은 당월 합계와 권한 상태를 요약
3. 일정 영역은 일정 화면으로 진입하는 카드 역할 수행
4. 채팅 영역은 일반 대화와 문서 기반 질문 진입점 역할 수행

## 저장소별 데이터 소유권

| 저장 위치 | 소유 모듈 | 내용 |
|---|---|---|
| `SharedPreferences/chat_sessions` | Chat Session | 세션 목록, 활성 세션 |
| `SharedPreferences/model_store` | Model Runtime | 선택 모델, 저장 위치 URI/path |
| `SharedPreferences/schedule_prefs` | Schedule | 알림 시간, 사용 여부 |
| `filesDir/llm_runtime/` | Model Runtime | 실제 추론에 사용할 모델 복사본 |
| `filesDir/knowledge/custom/` | Document Import | 업로드 문서 섹션 파일 |
| `filesDir/knowledge/manifest_override.json` | Knowledge Routing + Document Import | 사용자 문서 메타데이터 |
| `assets/knowledge/` | Knowledge Routing | 번들 지식 문서 |
| 추후 `SharedPreferences` 또는 `filesDir/wallet/` | Card Expense Ledger | 당월 카드 거래 및 raw 알림 |

## 현재 설계의 특징

### 강점

- 기능이 작고 명확한 파일들로 어느 정도 나뉘어 있습니다.
- 문서 업로드와 번들 자산 병합 구조가 단순합니다.
- 일정 모듈이 별도 패키지로 분리되어 있어 관심사 구분이 비교적 명확합니다.
- 삼성 Wallet 기능은 일정 모듈과 비슷하게 백그라운드 입력원을 가지므로 별도 패키지로 두는 것이 자연스럽습니다.

### 제약

- `MainActivity`에 상태와 오케스트레이션이 집중되어 있습니다.
- 지식 라우팅은 의미 기반이 아니라 키워드 매칭 기반입니다.
- `LlmEngine`가 singleton이므로 병렬 기능 확장 시 경합 가능성이 있습니다.
- 일정 조회 로직에 특정 Gmail 계정 상수가 남아 있습니다.
- 삼성 Wallet 기능은 아직 문서만 있고 실제 구현은 없습니다.
- 삼성 Wallet 기능은 AI가 아니라 규칙 기반 필터와 정규식 파서로 설계합니다.

## 요구사항이 들어왔을 때의 읽기 전략

### UI 요구사항

1. `docs/modules/app-shell.md`
2. `GemmaComposeUi.kt`
3. 필요 시 `ScheduleScreen.kt`

### 모델 관련 요구사항

1. `docs/modules/model-runtime.md`
2. `ModelStore.kt`
3. `LlmEngine.kt`

### 지식 응답 관련 요구사항

1. `docs/modules/knowledge-routing.md`
2. `ManifestLoader.kt`
3. `AgentRouter.kt`
4. `KnowledgePromptBuilder.kt`

### 문서 업로드 관련 요구사항

1. `docs/modules/document-import.md`
2. `DocumentImporter.kt`
3. `MainActivity.kt`

### 일정 관련 요구사항

1. `docs/modules/schedule.md`
2. `CalendarReader.kt`
3. `SchedulePromptBuilder.kt`
4. `ScheduleWorker.kt`

### 삼성 Wallet 카드 사용 분석 요구사항

1. `docs/modules/wallet-expense-feature.md`
2. `docs/modules/wallet-notification-intake.md`
3. `docs/modules/wallet-transaction-parser.md`
4. `docs/modules/card-expense-ledger.md`

## 변경 영향 포인트

- `MainActivity.kt`를 건드리면 화면 상태, 모델 상태, 문서 상태, 일정 상태가 함께 영향을 받을 수 있습니다.
- `ModelStore.kt`와 `LlmEngine.kt`를 건드리면 채팅과 일정 요약이 동시에 영향을 받습니다.
- `ManifestLoader.kt`를 건드리면 지식 문서 읽기와 업로드 문서 병합이 둘 다 바뀝니다.
- `ScheduleWorker.kt`는 앱 UI 없이 동작하므로 모델 로드/해제 정책과 충돌하지 않는지 확인이 필요합니다.
- Wallet 알림 수집 서비스도 UI 없이 동작하므로 저장소 경쟁과 권한 흐름을 별도로 검토해야 합니다.

## 확장 방향

- `MainActivity` 오케스트레이션을 feature별 controller 또는 ViewModel 계층으로 분리
- 지식 라우팅을 키워드 기반에서 모델 기반 분류로 교체
- 문서 캐시 무효화 정책 명시
- 일정 대상 계정 설정을 UI 또는 설정 저장소로 이동
- 삼성 Wallet 알림 분석 기능 구현

## 참조 문서

- `README.md`
- `docs/modules/app-shell.md`
- `docs/modules/chat-session.md`
- `docs/modules/model-runtime.md`
- `docs/modules/knowledge-routing.md`
- `docs/modules/document-import.md`
- `docs/modules/schedule.md`
- `docs/modules/build-assets.md`
- `docs/modules/wallet-notification-intake.md`
- `docs/modules/wallet-transaction-parser.md`
- `docs/modules/card-expense-ledger.md`
- `docs/modules/wallet-expense-feature.md`
