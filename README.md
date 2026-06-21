# Flow

LiteRT-LM 기반 온디바이스 금융 도우미 Android 앱입니다.  
현재 프로젝트는 `홈`, `카드 사용내역`, `일정`, `채팅 세션`, `모델 런타임`, `지식 문서 라우팅`, `문서 업로드`, `빌드/에셋`, `삼성 Wallet 알림 분석`, `기기 전체 앱 사용 로그` 모듈로 나뉩니다.

이 README는 전체 인덱스 역할만 합니다. 상세 동작은 모듈별 문서를 먼저 읽는 것이 효율적입니다.

## 협업 문서

- `project.md`: 작업 현황판
- `AGENTS.md`: Codex 관리자 지침
- `CLAUDE.md`: Claude 실무 지침
- `business_context.md`: 제품과 비즈니스 맥락
- `design.md`: 디자인 원칙
- `docs/collab_protocol.md`: Codex와 Claude 협업 프로토콜
- `docs/agent_runtime.md`: Codex가 Claude를 자동 호출할 때의 실행 규칙
- `docs/template.md`: 작업 지시 및 보고 템플릿

자동 위임을 실제로 쓰려면 `claude` CLI 인증이 먼저 필요합니다. 첫 실행 전 `claude auth login --claudeai` 또는 적절한 인증 방식을 완료해야 합니다.

## 로컬 파일 정책

- `local.properties`, `.claude/`, `.codex/`, `.gradle/`, `.idea/`, `build/` 산출물은 Git에 올리지 않는다.
- 로컬에서만 쓰는 모델 파일(`*.litertlm`)은 기본적으로 Git 추적 대상에서 제외한다.
- 개인 이메일, 로컬 SDK 경로, 개별 사용자 계정 식별자는 코드나 문서에 하드코딩하지 않는다.

## 전체 구조

```text
.
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   └── knowledge/
│       ├── java/com/example/gemma4ondevicetest/
│       │   ├── MainActivity.kt
│       │   ├── GemmaComposeUi.kt
│       │   ├── LlmEngine.kt
│       │   ├── ModelStore.kt
│       │   ├── AgentRouter.kt
│       │   ├── KnowledgePromptBuilder.kt
│       │   ├── ManifestLoader.kt
│       │   ├── DocumentImporter.kt
│       │   ├── ChatSessionStore.kt
│       │   ├── usage/
│       │   └── schedule/
│       └── res/
├── docs/
│   ├── agent_runtime.md
│   └── modules/
│       ├── app-shell.md
│       ├── chat-session.md
│       ├── model-runtime.md
│       ├── knowledge-routing.md
│       ├── document-import.md
│       ├── schedule.md
│       ├── build-assets.md
│       ├── wallet-notification-intake.md
│       ├── wallet-transaction-parser.md
│       ├── card-expense-ledger.md
│       └── wallet-expense-feature.md
├── scripts/
│   └── claude_handoff.sh
└── ARCHITECTURE.md
```

## Module View

![Flow module view](docs/diagrams/module-view.svg?v=4)

PlantUML source: `docs/diagrams/module-view.puml`
`App Preferences`는 채팅 세션, 모델 선택, 일정 설정 같은 SharedPreferences 저장소를 묶어 표현한 것입니다. Wallet 데이터는 `Wallet SharedPreferences`, 앱 사용 로그는 `app_usage_logs.db`로 분리해 표현합니다.

## DB / Storage View

| 저장소 | 매체 | 주요 테이블/키 | 보관 정책 | 소유 모듈 |
|---|---|---|---|---|
| `app_usage_logs.db` | SQLite | `app_usage_sessions`, `app_usage_meta` | raw 세션 7일 | App Usage Logging |
| `notification_inbox_store` | SharedPreferences JSON | `month_key`, `entries` | 당월 raw 알림 최대 400건 | Wallet Notification Intake |
| `wallet_card_transactions` | SharedPreferences JSON | `stored_month`, `records` | 당월 카드 거래 | Card Expense Ledger |
| `card_expense_insight_candidates` | SharedPreferences JSON | `analyzed_transaction_ids`, `candidate_categories` | 당월 분석 상태 | Card Expense Insight |
| `card_expense_insight_store` | SharedPreferences JSON | `insight_report`, `insight_history` | 현재 월 리포트 + 최대 12개월 history | Card Expense Insight |
| `subscription_insight_store` | SharedPreferences JSON | `analysis_report` | 최근 정기결제 분석 리포트 | Subscription Analysis |

현재 코드 기준으로 SQLite 테이블은 앱 사용 로그에만 있습니다. Wallet 쪽은 아직 Room/SQLite가 아니라 SharedPreferences JSON 저장소이며, 데이터가 커지면 `notification_inbox_store`, `wallet_card_transactions`, `card_expense_insight_store` 순서로 SQLite/Room 승격 후보입니다.

## 모듈 문서

- `app-shell`: 화면 전환, 전역 상태, 액션 연결
- `chat-session`: 세션/메시지 저장과 응답 흐름
- `model-runtime`: 모델 선택, 다운로드, 런타임 로드
- `knowledge-routing`: manifest 기반 지식 선택과 프롬프트 주입
- `document-import`: txt 업로드와 사용자 지식 문서 등록
- `schedule`: 캘린더 조회, 일정 요약, 알림 실행
- `build-assets`: Gradle, manifest, 번들 모델, 스크립트
- `wallet-notification-intake`: NotificationListenerService 기반 수집 경계
- `wallet-transaction-parser`: 삼성 Wallet 알림 문구에서 카드 사용 데이터 추출
- `card-expense-ledger`: 거래 저장, 중복 제거, 집계
- `wallet-expense-feature`: 기능 전체 오케스트레이션과 구현 순서
- `app-usage-personalization-plan`: 기기 전체 앱 사용 로그 수집 현황과 개인화 확장 계획

## 요구사항별로 먼저 볼 파일

| 요구사항 | 먼저 볼 문서 | 핵심 파일 |
|---|---|---|
| 앱 시작 흐름, 홈/카드/일정/채팅 화면 이동, 전역 상태 수정 | `docs/modules/app-shell.md` | `MainActivity.kt`, `GemmaComposeUi.kt` |
| 채팅 세션 저장 방식, 응답 생성 흐름 수정 | `docs/modules/chat-session.md` | `ChatSessionStore.kt`, `MainActivity.kt`, `LlmEngine.kt` |
| 모델 선택, 다운로드, 로드 실패 수정 | `docs/modules/model-runtime.md` | `ModelStore.kt`, `LlmEngine.kt`, `MainActivity.kt` |
| 지식 문서 선택 기준, 금융 문서 응답 품질 수정 | `docs/modules/knowledge-routing.md` | `AgentRouter.kt`, `ManifestLoader.kt`, `KnowledgePromptBuilder.kt`, `assets/knowledge/manifest.json` |
| 문서 업로드, 섹션 분리, 사용자 문서 저장 수정 | `docs/modules/document-import.md` | `DocumentImporter.kt`, `ManifestLoader.kt`, `MainActivity.kt` |
| 캘린더 권한, 일정 표시, 알림 요약 수정 | `docs/modules/schedule.md` | `CalendarReader.kt`, `SchedulePromptBuilder.kt`, `ScheduleWorker.kt`, `ScheduleWorkScheduler.kt` |
| 빌드 설정, asset 포함, 모델 번들링 수정 | `docs/modules/build-assets.md` | `app/build.gradle.kts`, `AndroidManifest.xml`, `scripts/download_function_gemma.sh` |
| 삼성 Wallet 알림 수집 구현 | `docs/modules/wallet-notification-intake.md` | `AndroidManifest.xml`, `MainActivity.kt`, `wallet/WalletNotificationListenerService.kt` |
| 삼성 Wallet 알림에서 카드 금액 파싱 구현 | `docs/modules/wallet-transaction-parser.md` | `wallet/WalletNotificationParser.kt`, `wallet/WalletNotificationModels.kt` |
| 카드 거래 저장/월별 합계/중복 제거 구현 | `docs/modules/card-expense-ledger.md` | `wallet/CardTransactionStore.kt`, `wallet/CardExpenseRepository.kt` |
| 기능 전체 연결 순서와 화면 반영 | `docs/modules/wallet-expense-feature.md` | `MainActivity.kt`, `GemmaComposeUi.kt`, `wallet/*` |
| 기기 전체 앱 사용 로그 DB/뷰어 구현 | `docs/app-usage-personalization-plan.md` | `usage/*`, `MainActivity.kt`, `GemmaComposeUi.kt`, `AndroidManifest.xml` |

현재 앱 사용 로그 수집은 모든 앱을 저장하지 않고, `사용자 지정 packageName 우선 allowlist + 앱 라벨 fallback` 기준으로만 저장합니다. raw 세션은 최대 7일만 보관하고, 뷰어에서 현재 7일 데이터를 `next app` 학습 예제 CSV로 내보낼 수 있습니다. 세부 정책은 `docs/app-usage-personalization-plan.md`를 참고합니다.

## 빠른 읽기 순서

1. `README.md`
2. 요구사항에 맞는 `docs/modules/*.md`
3. `ARCHITECTURE.md`
4. 실제 코드 파일

## 현재 시스템 요약

- 앱은 `홈 -> 카드 사용내역 / 일정 / 채팅` 진입 구조를 가지며, UI와 오케스트레이션은 `MainActivity.kt`에 많이 집중되어 있습니다.
- 모델 추론은 `LlmEngine.kt` 하나가 세션별 `Conversation`을 관리합니다.
- 지식 응답은 현재 Function Calling이 아니라 키워드 매칭 기반 라우팅입니다.
- 업로드한 문서는 앱 내부 저장소와 override manifest로 병합됩니다.
- 일정 기능은 별도 `schedule` 패키지로 분리되어 있습니다.
- 삼성 Wallet 알림 분석 기능은 Notification inbox, 당월 거래 집계, 정기결제 후보 분석, 카드 인사이트 요약까지 연결되어 있습니다.
- 삼성 Wallet raw 알림과 거래 데이터는 당월만 유지하고, 카드 인사이트는 월간 summary snapshot으로 최대 12개월 비교합니다.

## 상세 문서

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
- `docs/app-usage-personalization-plan.md`
- `docs/agent_runtime.md`
- `ARCHITECTURE.md`
