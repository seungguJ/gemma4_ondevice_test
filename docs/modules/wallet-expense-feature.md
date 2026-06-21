# Wallet Expense Feature Module

## 목적

삼성 Wallet 알림을 받아 카드 사용금액을 확인하는 전체 기능의 상위 설계 문서입니다.  
이 문서는 구현 순서와 모듈 간 연결 지점을 설명합니다.

이 기능은 알림 수집 + 규칙 기반 파싱 + 월별 집계로 기본 동작하며, 정기결제 후보와 동일한 1차 규칙 기반 후보 집합을 이용한 AI 카테고리 인사이트 분석을 함께 제공합니다.

## 목표 기능

- 삼성 Wallet 알림 수신
- 카드 결제 알림만 선별
- 결제 금액, 가맹점, 승인시각 추출
- 로컬에 당월 거래만 저장
- 홈 화면과 카드 사용내역 화면에서 최근 거래/월별 합계 조회
- 정기결제 후보와 동일한 규칙 기반 후보 집합으로 AI 카테고리 분석 (단건 연속 처리)
- 카드 사용내역의 `이번 달 누적`에서 분석 기준 금액을 표시하고, `분석 리포트 보기`로 카테고리별 비율 시각화와 분석 내역 제공
- 카드 사용내역 화면에서 전월 대비/월별 변화 비교 제공
- 홈 화면 카드에 대표 카테고리 인사이트 문구 노출
- 추후 채팅이나 지식 응답과 연결 가능한 구조 확보

## 카드 인사이트 분석 흐름

```text
WalletRawNotification
  -> NotificationInboxStore.appendRaw()
  -> SubscriptionAnalysisRules.evaluate()  ← 정기결제와 동일 게이트
  -> NotificationInboxStore markAnalysisEligible()
  -> CardExpenseInsightScheduler.enqueue()
  -> CardExpenseInsightWorker
      -> battery gate: charging + 100%
      -> cooldown gate: lastCompletedAt + 24h
      -> LlmEngine (load → generate → clearSession → free)
      -> JSON 파싱
      -> CardExpenseInsightStore.saveReport()
  -> WalletScreen 내 분석 리포트 (카테고리 시각화 화면)
```

## 카드 인사이트 파일

```text
wallet/
  NotificationInboxStore.kt         — 공통 raw 알림 inbox + 기능별 상태 플래그
  CardExpenseInsightModels.kt       — ExpenseCategoryBreakdown, CardExpenseInsightReport
  CardExpenseCandidateStore.kt      — inbox 기반 카드 인사이트 후보 adapter
  CardExpenseInsightStore.kt        — AI 분석 결과 저장소
  CardExpenseInsightWorker.kt       — AI 배치 분석 워커
  CardExpenseInsightScheduler.kt    — WorkManager 스케줄러
```

## 현재 Module View

```text
WalletNotificationListenerService
  -> WalletExpenseCoordinator
      -> NotificationInboxStore
      -> SubscriptionAnalysisRules / SubscriptionNotificationStore
      -> WalletNotificationFilter
      -> WalletNotificationParser / WalletParserRules
      -> CardExpenseRepository / CardTransactionStore
      -> CardExpenseCandidateStore
      -> SubscriptionAnalysisScheduler
      -> CardExpenseInsightScheduler

MainActivity / GemmaComposeUi
  -> WalletScreen
      -> WalletTransactionsTab
          -> 분석 리포트 보기
              -> WalletInsightTab
              -> CategoryDetailCards
              -> AnalysisHistoryList
  -> SubscriptionScreen
  -> App Status / Home summary
```

카드 인사이트는 별도 상위 탭이 아니라 카드 사용내역 화면의 `분석 리포트 보기` 확장 영역으로 노출됩니다. 홈 화면은 카드/정기결제/앱 사용 로그 진입 요약만 제공하고, 월간 상세 분석은 Wallet 화면에서 확인합니다.

## 카테고리 분류 기준

카테고리는 소비지출 통계의 대분류를 앱 카드 알림 분석에 맞게 축약해 사용합니다.

`식비, 교통, 쇼핑, 구독, 주거/공과금, 생활, 의료, 교육, 통신, 문화/여가, 금융, 기타`

- `식비`: 떡볶이, 분식, 식당, 카페, 배달음식, 편의점 식품 등 먹는 것과 관련된 지출
- `교통`: 택시, 버스, 지하철, 철도, 항공, 주유, 주차 등 이동 관련 지출
- 목록에 맞는 항목이 없거나 확신이 낮으면 `기타`로 분류합니다.

- LLM 프롬프트는 `WalletNotificationParser.extractMerchantName` + `WalletParserRules.parseAmount`로 미리 추출한 가맹점명/금액을 입력으로 주고, 카드사명(알림 발신 정보)은 가맹점명·representativeNames·topMerchants에 쓰지 말도록 명시한다. 카드사명이 가맹점으로 잘못 분류되는 것을 막기 위함.
- `CardExpenseInsightWorker`는 후보별 카테고리를 `CardExpenseCandidateStore`에 기록한 뒤, 분석 완료 후보 전체를 다시 읽어 리포트를 재계산한다. 당월 내 여러 번 분석하거나 단건 재분석해도 금액이 이중 집계되지 않는다.
- 온디바이스 모델 컨텍스트를 안정적으로 유지하기 위해 카드 인사이트는 한 번에 1건만 분석하고, 남은 후보가 있으면 다음 작업으로 이어서 처리합니다.
- AI 응답이 비어 있으면 `CardExpenseInsightWorker`가 입력을 줄인 compact prompt로 1회 재시도합니다. 재시도 후에도 응답이 없거나 JSON/categorization 형식이 깨지면 Android Logcat에 응답 일부를 남기고 화면에는 실패 사유를 표시합니다.

## 제안 패키지 구조

```text
app/src/main/java/com/example/gemma4ondevicetest/wallet/
  WalletNotificationListenerService.kt
  WalletNotificationFilter.kt
  WalletNotificationModels.kt
  WalletNotificationPermissionManager.kt
  WalletNotificationParser.kt
  WalletParserRules.kt
  CardTransactionStore.kt
  CardExpenseRepository.kt
  CardExpenseModels.kt
  CardExpenseDeduplicator.kt
  WalletExpenseCoordinator.kt
```

## 모듈 간 흐름

```text
Samsung Wallet Notification
  -> WalletNotificationListenerService
  -> month reset check
  -> NotificationInboxStore
  -> WalletNotificationFilter
  -> WalletNotificationParser
  -> CardExpenseDeduplicator
  -> CardExpenseRepository
  -> UI / Chat / Summary
```

## 제안 코디네이터

### `WalletExpenseCoordinator.kt`

역할:

- 수집, 파싱, 저장을 한 흐름으로 연결
- 실패 로그와 파싱 실패 사유 수집
- 향후 UI 상태 업데이트 또는 디버그 화면 연결
- 비카드 금융 알림을 초기에 차단
- 월 변경 시 저장소 리셋 호출

예상 처리 순서:

1. raw notification 수신
2. 현재 월 키 확인 및 필요 시 저장소 리셋
3. 패키지/제목/본문 필터 통과 확인
4. raw 알림 저장
5. 거래 파싱 시도
6. 카드 거래가 아니면 종료
7. 중복 여부 확인
8. 거래 저장
9. 집계 갱신 또는 화면 반영

## 분석 시점

- 후보 판정과 저장은 알림 수신 직후 수행합니다.
- 알림 수신 직후에는 후보 저장과 자동 분석 작업 예약까지만 수행하고, 실제 모델 실행은 하지 않습니다.
- 실시간 AI 분석은 금지합니다. 새 알림이 들어왔다는 이유만으로 `LlmEngine.loadModel()` 또는 추론을 호출하면 안 됩니다.
- 모델 상시 로드는 금지합니다. 정기결제/카드 인사이트는 분석 조건이 확정되기 전까지 모델을 메모리에 올려 두면 안 됩니다.
- 자동 분석 작업은 24시간 지연 후 실행 대상으로 잡습니다.
- 자동 AI 실행은 `24시간 대기 + 충전 중 + 배터리 100% + 다른 화면/Worker가 모델 미사용` 조건을 모두 만족할 때만 시작합니다.
- 마지막 성공 시각 기준 24시간 안에는 다시 실행하지 않습니다.
- 단, 카드 사용내역의 분석 리포트 안에 있는 `지금 분석`은 사용자가 명시적으로 요청한 강제 분석으로 보고 배터리 게이트와 24시간 쿨다운을 적용하지 않습니다.
- `재분석`, `전체 재분석`도 사용자가 직접 누른 강제분석 액션일 때만 자동 조건을 우회할 수 있습니다.
- `지금 분석`은 이번 보정 용도로 카드 인사이트 후보뿐 아니라 현재 카드 거래 저장소의 미분석 거래 내역도 일회성 입력으로 포함합니다. 이후 자동 분석은 다시 공통 inbox의 카드 인사이트 후보를 기준으로 독립적으로 진행합니다.
- 카드 사용내역의 분석 리포트는 분석 입력 후보 목록을 표시해 어떤 알림/거래가 분석 완료 또는 대기 상태인지 확인할 수 있어야 합니다.
- 카드 사용내역의 분석 리포트는 세 가지 동작을 구분해 제공합니다. `지금 분석`은 대기 중인 내역만 분석하고, `전체 재분석`은 현재 달 결과만 비운 뒤(월별 기록은 유지) 모든 내역을 처음부터 다시 분류하며, `초기화`는 확인 다이얼로그를 거쳐 현재 달 결과·카테고리·월별 기록을 모두 삭제합니다.
- 분석 워커는 1건씩 분류한 카테고리를 후보 id 기준으로 `CardExpenseCandidateStore`에 저장하고, 카테고리 상세 카드를 펼치면 해당 카테고리에 속한 개별 내역을 확인할 수 있습니다.
- 리포트는 누적하지 않고, 매 분석 시 분석 완료된 모든 후보 + 기록된 카테고리로부터 통째로 다시 만듭니다(`buildReportFromAnalyzed`). 단일 소스에서 파생하므로 재분석/부분 재분석에서도 금액이 이중 집계되지 않습니다.
- 분석 내역 목록의 각 항목에는 `재분석` 버튼이 있어 특정 한 건만 다시 분류할 수 있습니다. 해당 후보만 대기 상태(`markPending`)로 돌리고 다시 분석하면, 리포트가 단일 소스에서 재계산되어 새 분류가 그대로 반영됩니다.
- 화면 조회 시점에는 raw 알림 전체를 다시 파싱하지 않습니다.
- 화면에서는 이미 저장된 거래를 집계만 합니다.
- 카드 사용내역 `이번 달 누적`은 인사이트 분석 결과 금액이 있으면 해당 금액을 우선 표시하고, 아직 분석 결과가 없으면 인사이트 후보 수집 금액을 표시해 내역과 인사이트의 금액 기준이 달라 보이지 않게 합니다.
- 파싱 실패 raw만 당월 내 재분석 대상으로 남길 수 있습니다.
- 월이 바뀌면 직전 월의 AI 분석 결과는 월간 summary snapshot으로 아카이브합니다.

## DB 정책

- raw 거래/알림 DB는 당월 데이터만 유지합니다.
- raw 알림 원본은 `NotificationInboxStore` 한 곳에 저장하고, Wallet/정기결제/카드 인사이트는 기능별 상태만 파생 관리합니다.
- 월이 바뀌면 기존 월 raw/transaction 데이터를 모두 삭제합니다.
- 다만 카드 인사이트는 `CardExpenseInsightStore`에 월간 summary snapshot을 최대 12개월 보관합니다.
- summary snapshot에는 `totalAmount`, `totalCount`, `categoryBreakdowns`, `topMerchants`를 유지합니다.
- 카드 인사이트는 카테고리 분류 역할만 담당하며 전체 지출 요약 문장(`overallSummary`)은 생성하지 않습니다.

## 현재 저장소 스키마

| 저장소 | 매체 | 주요 키/필드 | 보관 |
|---|---|---|---|
| `notification_inbox_store` | SharedPreferences JSON | `month_key`, `entries[]` (`subscriptionEligible`, `cardInsightEligible`, `subscriptionAnalyzedAt`, `cardInsightAnalyzedAt`) | 당월 raw 알림 최대 400건 |
| `wallet_card_transactions` | SharedPreferences JSON | `stored_month`, `records[]` (`amount`, `status`, `merchantName`, `dedupeKey`) | 당월 거래 |
| `card_expense_insight_candidates` | SharedPreferences JSON | `analyzed_transaction_ids`, `candidate_categories` | 당월 후보별 분석 상태 |
| `card_expense_insight_store` | SharedPreferences JSON | `insight_report`, `insight_history` | 현재 월 리포트, 최대 12개월 월간 summary |
| `subscription_insight_store` | SharedPreferences JSON | `analysis_report` | 최근 정기결제 분석 리포트 |

현재 Wallet 기능에는 실제 SQLite 테이블이 없습니다. 문서에서 DB라고 부르는 raw 거래/알림 저장소는 구현상 SharedPreferences JSON이며, 데이터량 증가나 cross-feature join이 필요해질 때 Room/SQLite로 승격합니다.

## 카드 알림 판정 원칙

저장 허용:

- `결제 완료` 문구 존재
- 금액 존재
- 승인/사용/결제/취소 키워드 존재
- 제외 키워드 없음

저장 제외:

- 계좌이체
- 송금
- 입금
- 출금
- 계좌
- 충전
- 자동이체
- 포인트
- 캐시백
- 광고
- 이벤트
- 혜택

## 기존 프로젝트와의 연결 지점

### `AndroidManifest.xml`

- Notification listener service 추가 필요

### `MainActivity.kt`

- 알림 접근 권한 상태 표시
- 사용자가 설정 화면으로 이동하는 액션 추가
- 홈 화면과 카드 사용내역 화면에 월 합계/최근 거래 상태 연결

### `GemmaComposeUi.kt`

- 홈에서 `카드 사용내역`, `일정`, `채팅` 빠른 진입 제공
- 카드 사용내역 화면에서 월 합계와 최근 거래 표시

### `ARCHITECTURE.md`

- 이 기능이 추가되면 schedule과 별도로 background ingestion 모듈이 하나 더 생깁니다.

## 구현 우선순위

1. 알림 리스너 서비스 등록
2. 월 리셋 정책 구현
3. 공통 notification inbox 저장
4. 비카드 제외 필터 구현
5. 금액 파서 구현
6. 거래 저장소 구현
7. 간단한 리스트/월별 합계 UI

## 요구사항별로 먼저 볼 문서

| 요구사항 | 먼저 볼 문서 |
|---|---|
| 삼성 Wallet 알림을 수집하고 싶다 | `docs/modules/wallet-notification-intake.md` |
| 알림에서 카드 금액을 뽑고 싶다 | `docs/modules/wallet-transaction-parser.md` |
| 중복 제거와 월별 집계를 만들고 싶다 | `docs/modules/card-expense-ledger.md` |
| 기능 전체를 어디에 연결할지 알고 싶다 | `docs/modules/wallet-expense-feature.md` |

## 위험 요소

- 삼성 Wallet 알림 포맷이 고정되어 있지 않을 수 있음
- Notification access는 사용자가 직접 허용해야 함
- 카드사별 문구 차이로 파서 규칙이 쉽게 깨질 수 있음
- 취소/부분취소/해외 승인 처리 정책을 초기에 정해야 함
- 카드가 아닌 금융 알림을 잘못 포함하면 월 사용량이 틀어지므로 제외 규칙을 보수적으로 잡아야 함

## 권장 초기 범위

- 삼성 Wallet 패키지 1개만 지원
- 원화 승인금액만 우선 지원
- 최근 거래 조회 + 월 합계까지만 구현
- 채팅 연동은 2차 단계로 미룸
- raw/transaction은 당월만 유지하고, 카드 인사이트 summary만 월별 history로 보관
