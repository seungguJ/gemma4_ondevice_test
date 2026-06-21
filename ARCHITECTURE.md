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
| App Usage Logging | Usage Access 기반 기기 전체 앱 사용 세션 수집, allowlist 필터, 7일 SQLite 저장, next app 학습 CSV export, 뷰어 화면 | `docs/app-usage-personalization-plan.md` |

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
  usage/AppUsageCollector.kt
  usage/AppUsageLogStore.kt
  usage/AppUsageSyncWorker.kt

Static/Data Layer
  assets/knowledge/*
  manifest.json
  filesDir/knowledge/*
  SharedPreferences
  wallet transaction storage
```

## 모듈별 파일 구조 원칙

프로젝트가 커질수록 파일 위치는 기능 이름보다 책임 경계가 먼저 보여야 합니다. 새 파일은 아래 계층 중 하나에만 책임을 가져야 하며, 한 파일이 UI 상태, 저장소, Worker, 모델 추론을 동시에 다루면 분리 대상입니다.

```text
app/src/main/java/com/example/gemma4ondevicetest/
  app/
    AppState.kt                     -- 앱 전역 화면 상태 모델
    AppCoordinator.kt               -- 화면 액션을 feature coordinator로 위임
  model/
    ModelStore.kt                   -- 모델 파일/선택 상태
    LlmEngine.kt                    -- 실제 LiteRT-LM singleton
    ModelRuntimeGate.kt             -- 모델 실행 조건/경합/언로드 정책의 단일 진입점
  chat/
    ChatSession*.kt                 -- 채팅 세션 저장/모델
    ChatResponder.kt                -- 채팅 추론 orchestration
  knowledge/
    AgentRouter.kt
    ManifestLoader.kt
    KnowledgePromptBuilder.kt
    DocumentImporter.kt
  schedule/
    ScheduleModels.kt
    CalendarReader.kt
    SchedulePromptBuilder.kt
    ScheduleWorker.kt
    ScheduleWorkScheduler.kt
    ScheduleScreen.kt
  wallet/
    intake/                         -- 알림 수신, 권한, raw inbox
    transaction/                    -- 규칙 기반 파싱, 중복 제거, 월 집계
    subscription/                   -- 정기결제 후보/분석 상태/Worker
    insight/                        -- 카드 인사이트 후보/분석 상태/Worker
  usage/
    AppUsage*.kt                    -- 앱 사용 로그 수집/저장/동기화
```

현재 파일을 즉시 모두 이동할 필요는 없습니다. 다만 새 기능을 추가하거나 큰 수정을 할 때는 위 구조를 목표로 잡고, 기존 루트 파일에 로직을 더 쌓지 않습니다.

## 의존 방향

허용되는 의존 방향은 아래와 같습니다.

```text
UI -> App Coordinator -> Feature Coordinator -> Domain Rules -> Store/Worker
                                 |
                                 v
                           ModelRuntimeGate -> LlmEngine
```

- UI는 저장소와 Worker를 직접 많이 호출하지 않고, 가능한 feature coordinator를 통해 호출합니다.
- Worker와 Service는 UI 상태에 의존하지 않습니다.
- Parser, Rules, Reducer는 Android Context 없이 순수 함수에 가깝게 유지합니다.
- Store는 데이터 읽기/쓰기만 담당하고 모델 실행, 화면 이동, 알림 예약을 하지 않습니다.
- `LlmEngine` 직접 호출은 `ModelRuntimeGate` 또는 그에 준하는 단일 게이트 계층에서만 허용하는 방향으로 정리합니다.
- Wallet 알림 수신 경로는 규칙 기반 저장까지만 담당합니다. AI 분석 실행 여부는 scheduler/worker/gate가 결정합니다.

## AI 모델 런타임 불변 조건

아래 조건은 성능 최적화보다 우선합니다. 이를 깨는 변경은 회귀로 봅니다.

1. 모델 상시 로드는 금지합니다.
   앱 시작, 화면 진입, 알림 수신, Worker 예약, 데이터 저장만으로 `LlmEngine.loadModel()`을 호출하면 안 됩니다.

2. 실시간 AI 분석은 금지합니다.
   새 알림이 들어왔다는 이유만으로 정기결제/카드 인사이트 모델 분석을 바로 실행하면 안 됩니다. 알림 수신 직후 허용되는 작업은 raw 저장, 규칙 기반 후보 판정, 지연 작업 예약뿐입니다.

3. 자동 금융 분석은 조건을 모두 만족해야 합니다.
   정기결제/카드 인사이트 자동 분석은 `24시간 대기 + 충전 중 + 배터리 100% + 다른 화면/Worker가 모델 미사용` 조건을 모두 통과해야 합니다.

4. 강제분석만 예외입니다.
   사용자가 직접 누른 `지금 분석`, `재분석`, `전체 재분석` 같은 명시적 액션만 24시간 대기와 배터리 게이트를 우회할 수 있습니다. 그래도 추론 종료 후 `clearSession()`과 `LlmEngine.free()`는 반드시 보장해야 합니다.

5. 앱이 백그라운드로 가거나 종료되면 모델을 내려야 합니다.
   `MainActivity.onStop()`과 화면 종료 경로에서 `LlmEngine.free()`가 호출되는 정책을 유지합니다. 백그라운드 Worker가 모델을 로드했다면 해당 Worker의 `finally` 블록에서 세션 정리와 `free()`를 수행해야 합니다.

6. 모델 경합은 보류해야 합니다.
   화면이 채팅/일정 요약으로 모델을 사용 중이면 백그라운드 금융 분석은 새 모델 로드를 시도하지 않고 retry 또는 보류 상태로 남아야 합니다.

## 유지보수 기준

- 한 파일이 500줄을 넘고 서로 다른 feature 상태를 함께 갖고 있으면 분리 후보로 봅니다.
- 새 화면 상태는 `MainActivity`에 직접 누적하기보다 feature별 state holder 또는 coordinator로 분리합니다.
- 새 백그라운드 작업은 `Scheduler`, `Worker`, `Store`, `Rules`를 분리합니다.
- 외부 이벤트 입력원(Notification, Alarm, Boot, Usage Access)은 수집과 저장까지만 담당하고, 무거운 계산이나 모델 추론을 직접 실행하지 않습니다.
- 월별 raw 데이터는 보관 기간을 명시하고, 장기 보존이 필요한 값은 summary snapshot으로 축약합니다.
- SharedPreferences JSON이 커지거나 cross-feature join이 필요해지면 Room/SQLite로 승격합니다.

## 성능 기준

- 알림 리스너, BroadcastReceiver, 화면 recomposition 경로에서는 모델 로드, 대형 파일 복사, 전체 raw 재파싱을 금지합니다.
- 모델 파일 복사(`prepareRuntimeModelFile`)는 사용자가 모델을 선택하거나 실제 추론 직전에만 수행합니다.
- 카드 인사이트처럼 작은 모델 컨텍스트에 취약한 기능은 batch size를 작게 유지하고, 실패 시 compact prompt 또는 규칙 기반 fallback을 사용합니다.
- 화면 조회는 저장된 summary와 index를 읽는 방식으로 유지하고, 화면 진입 때 raw 알림 전체를 매번 다시 분석하지 않습니다.
- Worker 재시도는 backoff를 사용하고, 조건 미충족 상태에서 tight loop를 만들면 안 됩니다.

## 변경 체크리스트

새 기능이나 리팩터링 PR은 아래 질문에 답할 수 있어야 합니다.

- 이 파일은 어느 모듈과 계층에 속하는가?
- UI, coordinator, store, worker, parser 책임이 섞이지 않았는가?
- 알림 수신이나 앱 시작만으로 모델이 로드되는 경로가 생기지 않았는가?
- 자동 AI 분석이 24시간/충전/100%/모델 미사용 조건을 모두 지키는가?
- 강제분석 액션이 아닌데 배터리 게이트나 쿨다운을 우회하지 않는가?
- 모든 추론 경로에 `clearSession()`과 `LlmEngine.free()`가 있는가?
- 앱이 백그라운드로 가거나 종료될 때 모델이 내려가는가?
- Worker, 화면, Service가 동시에 모델을 잡는 경합 경로가 없는가?
- 검증은 단위 테스트, 정적 리뷰, 수동 시나리오 중 무엇으로 했는가?

## 핵심 런타임 흐름

### 1. 앱 시작

1. `MainActivity.onCreate()`가 홈을 기본 화면으로 두고 세션, 모델 선택, 일정 설정을 복원합니다.
2. `refreshRuntimeState()`가 모델 상태와 지식 도구 상태를 UI에 반영합니다.
3. 앱 시작만으로 모델을 상시 로드하면 안 됩니다. 모델 상태는 UI에 표시하되, 실제 로드는 사용자 요청 또는 허용된 추론 직전에만 수행합니다.
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

### 7. 금융 AI 분석

1. 알림 수신 경로는 정기결제/카드 인사이트 후보 여부만 표시하고 모델을 실행하지 않습니다.
2. Scheduler는 자동 분석을 24시간 지연 작업으로 예약합니다.
3. Worker는 실행 시점에 배터리 상태, 충전 상태, 마지막 성공 시각, 모델 사용 여부를 다시 확인합니다.
4. 조건을 통과하지 못하면 모델을 로드하지 않고 상태 메시지와 retry/backoff만 남깁니다.
5. 조건을 통과한 경우에만 모델을 로드하고 단건 또는 제한된 batch를 분석합니다.
6. `finally`에서 세션을 정리하고, Worker가 로드한 모델이면 반드시 `LlmEngine.free()`를 호출합니다.
7. 사용자가 누른 강제분석만 24시간 대기와 배터리 게이트를 우회합니다.

### 8. 홈 화면 요약

1. 홈 화면이 카드 사용내역, 일정, 채팅 진입 카드를 노출
2. 카드 영역은 당월 합계와 권한 상태를 요약
3. 일정 영역은 일정 화면으로 진입하는 카드 역할 수행
4. 채팅 영역은 일반 대화와 문서 기반 질문 진입점 역할 수행
5. 앱 사용 로그 영역은 Usage Access 권한 상태와 수집 현황 진입점 역할 수행

### 9. 기기 전체 앱 사용 세션 수집

1. 사용자가 Usage Access 권한을 허용
2. `AppUsageCollector`가 `UsageEvents`를 읽음
3. 사용자 지정 packageName 우선 allowlist와 앱 라벨 fallback 매칭을 검사
4. foreground/background 이벤트를 세션 단위로 변환
5. `AppUsageLogStore`가 SQLite DB에 저장
6. 7일보다 오래된 raw 세션을 삭제
7. `AppUsageSyncWorker`가 주기 동기화를 수행
8. 홈/전용 화면에서 요약과 최근 세션을 표시
9. 필요 시 현재 보관 중인 7일 세션을 next app 학습 예제 CSV로 내보냄

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
| `SharedPreferences/notification_inbox_store` | Wallet Notification Intake | 당월 raw 알림, 정기결제/카드 인사이트 eligibility/analyzed 상태 |
| `SharedPreferences/wallet_card_transactions` | Card Expense Ledger | 당월 카드 거래, 중복 제거 기준, 저장 월 키 |
| `SharedPreferences/card_expense_insight_candidates` | Card Expense Insight | 거래 후보 분석 여부, 후보별 카테고리 |
| `SharedPreferences/card_expense_insight_store` | Card Expense Insight | 현재 월 인사이트 리포트, 최대 12개월 월간 summary history |
| `SharedPreferences/subscription_insight_store` | Subscription Analysis | 정기결제 분석 리포트와 pending 상태 메시지 |
| `app_usage_logs.db` | App Usage Logging | 기기 전체 앱 사용 세션, 동기화 메타 |

## DB 테이블 / JSON 스키마 요약

### SQLite: `app_usage_logs.db`

| 테이블 | 컬럼 | 용도 |
|---|---|---|
| `app_usage_sessions` | `id`, `package_name`, `app_category`, `started_at_millis`, `ended_at_millis`, `duration_seconds`, `weekday`, `hhmm` | UsageEvents를 foreground 세션으로 변환한 7일 raw 로그 |
| `app_usage_meta` | `meta_key`, `meta_value` | 마지막 동기화 시각, 마지막 처리 이벤트, pending foreground 세션 |

`app_usage_sessions`는 `(package_name, started_at_millis, ended_at_millis)` unique 제약을 가지며, `started_at_millis DESC`, `package_name` 인덱스를 둡니다.

### Wallet SharedPreferences JSON

| 저장소 | 핵심 필드 | 용도 |
|---|---|---|
| `notification_inbox_store.entries` | `id`, `monthKey`, `packageName`, `appLabel`, `postedAt`, `title`, `text`, `bigText`, `subText`, `notificationKey`, `subscriptionEligible`, `subscriptionAnalyzedAt`, `cardInsightEligible`, `cardInsightAnalyzedAt` | raw 알림 원본과 기능별 분석 상태의 단일 source |
| `wallet_card_transactions.records` | `id`, `monthKey`, `sourcePackage`, `notificationKey`, `approvedAt`, `postedAt`, `cardLabel`, `merchantName`, `amount`, `currency`, `status`, `rawTitle`, `rawBody`, `createdAt`, `dedupeKey` | 카드 거래 저장과 월 집계 |
| `card_expense_insight_candidates` | `analyzed_transaction_ids`, `candidate_categories` | inbox 밖 거래 후보의 분석 여부와 후보별 분류 결과 |
| `card_expense_insight_store.insight_report` | `monthKey`, `generatedAt`, `lastCompletedAt`, `pendingCount`, `analyzedCandidateCount`, `statusMessage`, `categoryBreakdowns`, `topMerchants` | 현재 월 카드 인사이트 리포트 |
| `card_expense_insight_store.insight_history` | `monthKey`, `lastCompletedAt`, `analyzedCandidateCount`, `totalAmount`, `totalCount`, `categoryBreakdowns`, `topMerchants` | 전월 비교용 월간 summary snapshot |
| `subscription_insight_store.analysis_report` | `generatedAt`, `lastCompletedAt`, `statusMessage`, `pendingCount`, `candidates` | 정기결제 후보 분석 결과 |

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
- Wallet 기능이 커지면서 intake, transaction, subscription, insight 책임이 한 패키지에 섞여 있습니다.
- 삼성 Wallet 수집 자체는 AI가 아니라 규칙 기반 필터와 정규식 파서가 기본입니다.
- 정기결제/카드 인사이트 AI 분석은 후보 저장 이후 지연 작업과 게이트를 통해서만 실행되어야 합니다.

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

- `MainActivity` 오케스트레이션을 feature별 coordinator 또는 ViewModel 계층으로 분리
- `LlmEngine` 직접 접근을 `ModelRuntimeGate` 같은 단일 게이트로 모아 모델 로드/해제/경합 정책을 중앙화
- Wallet 패키지를 `intake`, `transaction`, `subscription`, `insight` 하위 패키지로 점진 분리
- 지식 라우팅을 키워드 기반에서 모델 기반 분류로 교체할 경우에도 상시 모델 로드는 금지
- 문서 캐시 무효화 정책 명시
- 일정 대상 계정 설정을 UI 또는 설정 저장소로 이동
- SharedPreferences 기반 대형 JSON 저장소는 데이터가 커지는 모듈부터 SQLite/Room으로 승격

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
