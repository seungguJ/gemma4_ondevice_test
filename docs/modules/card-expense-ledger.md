# Card Expense Ledger Module

## 목적

파싱된 카드 거래를 저장하고, 중복 제거와 집계를 담당하는 모듈입니다.

이 모듈의 목표는 "장기 거래 raw 보관"이 아니라 "당월 카드 사용량 계산 + 전월 비교용 월간 요약 보존"입니다.

## 제안 패키지 구조

```text
app/src/main/java/com/example/gemma4ondevicetest/wallet/
  CardTransactionStore.kt
  CardExpenseRepository.kt
  CardExpenseModels.kt
  CardExpenseDeduplicator.kt
```

## 제안 파일 책임

### `CardTransactionStore.kt`

- 로컬 저장소 접근
- 처음 구현은 `SharedPreferences`, JSON 파일, 또는 간단한 로컬 DB로 시작 가능
- 추후 Room으로 교체 가능
- 현재 요구사항 기준으로는 raw 거래는 당월만 유지하고, 카드 인사이트 월간 요약은 별도 저장소에서 보존

### `CardExpenseRepository.kt`

- 저장/조회 API 제공
- 거래 추가, 취소 반영, 기간별 조회
- 화면과 서비스 사이의 경계 역할

### `CardExpenseModels.kt`

- 저장용 거래 모델
- 예: `CardTransactionRecord`, `MonthlyCardSummary`

### `CardExpenseDeduplicator.kt`

- 중복 알림으로 같은 거래가 두 번 저장되지 않도록 처리
- `notificationKey`, `postedAt`, `amount`, `merchantName`, `approvedAt` 조합 기반 중복 판정

## 제안 저장 모델

```text
CardTransactionRecord
- id
- monthKey
- sourcePackage
- notificationKey
- approvedAt
- postedAt
- cardLabel
- merchantName
- amount
- currency
- status
- rawTitle
- rawBody
- createdAt
- dedupeKey
```

## 현재 저장 구현

`CardTransactionStore`는 아직 Room/SQLite가 아니라 `SharedPreferences("wallet_card_transactions")`에 JSON 배열로 저장합니다.

| 키 | 값 | 설명 |
|---|---|---|
| `records` | `CardTransactionRecord[]` JSON | 당월 카드 거래 원본 |
| `stored_month` | `yyyy-MM` | 저장소가 마지막으로 유지한 월 키 |

`records`의 JSON 필드는 `CardTransactionRecord`와 동일하게 `id`, `monthKey`, `sourcePackage`, `notificationKey`, `approvedAt`, `postedAt`, `cardLabel`, `merchantName`, `amount`, `currency`, `status`, `rawTitle`, `rawBody`, `createdAt`, `dedupeKey`를 저장합니다.

현재 집계는 DB materialized view가 아니라 `CardExpenseRepository.getMonthlySummary()`가 `records`를 읽어 `grossApproved`, `grossCancelled`, `netSpent`를 계산하는 방식입니다.

## 권장 저장 정책

- 월 단위 리셋 정책을 기본으로 합니다.
- 현재 월 raw 데이터만 유지합니다.
- 월이 바뀌면 공통 notification inbox의 이전 달 raw 상태와 transaction 저장소를 모두 비웁니다.
- 1차 구현은 append-only보다 upsert 형태가 안전합니다.
- 취소 알림은 초기 버전에서 `취소 합계` 또는 `음수 효과`로 반영합니다.
- 월별 합계는 조회 시 계산하는 단순 구조가 적합합니다.
- 전월 비교에 필요한 카테고리 합계와 요약은 `CardExpenseInsightStore` history에 snapshot으로 남깁니다.

## 월 리셋 규칙

- 기준 시간대: `Asia/Seoul`
- 월 키 형식: `yyyy-MM`
- 검사 시점:
  - 앱 시작 시
  - 알림 수집 시
  - 저장 직전
- `storedMonth != currentMonth` 이면:
  - notification inbox의 이전 달 raw 상태 삭제
  - 카드 거래 저장소 삭제
  - 카드 인사이트 현재 리포트를 월간 summary로 아카이브
  - 현재 월 키 갱신

## 저장 대상 규칙

저장 대상:

- 삼성 Wallet 출처
- 카드 정보가 없어도 승인 또는 카드 취소로 판정된 알림
- 파서 필수 조건을 만족한 거래

저장 제외:

- 계좌이체
- 송금
- 입출금
- 충전
- 자동이체
- 포인트/혜택/광고/이벤트 알림

## 권장 조회 API

- `saveParsedTransaction(...)`
- `findRecentTransactions(limit)`
- `findTransactionsByMonth(yearMonth)`
- `getMonthlySummary(yearMonth)`
- `markTransactionCancelled(...)`
- `resetIfMonthChanged(currentMonth)`

## 요구사항별로 볼 위치

| 요구사항 | 먼저 볼 위치 |
|---|---|
| 같은 거래가 두 번 저장됨 | `CardExpenseDeduplicator.kt`, `CardTransactionStore.kt` |
| 월별 카드 사용금액 합계 필요 | `CardExpenseRepository.kt`, `CardExpenseModels.kt` |
| 승인 취소 반영 필요 | `CardExpenseRepository.kt` |
| 로컬 저장소를 Room으로 바꾸고 싶음 | `CardTransactionStore.kt` |
| 월이 바뀌면 전월 데이터 리셋 | `CardTransactionStore.kt`, `CardExpenseRepository.kt` |

## 중복 제거 규칙

우선순위:

1. `notificationKey` 동일 시 중복
2. 동일 `dedupeKey` 가 짧은 시간 내 반복되면 중복

권장 `dedupeKey` 조합:

- `status`
- `amount`
- `approvedAt` 또는 `postedAt`
- `merchantName`
- `cardLabel`

예:

- `APPROVED|12500|2026-06-03T14:21|스타벅스 강남점|삼성카드`

## 월 집계 규칙

필요한 값:

- `grossApproved`
- `grossCancelled`
- `netSpent`

공식:

- `grossApproved = APPROVED 금액 합`
- `grossCancelled = CANCELLED 금액 합`
- `netSpent = grossApproved - grossCancelled`

## 주의사항

- 알림은 재발송되거나 수정될 수 있으므로 중복 제거 기준이 중요합니다.
- 파싱 결과가 불완전한 경우 저장하지 않거나 `pending` 상태로 보관하는 정책이 필요합니다.
- 삼성 Wallet 외 다른 카드 알림을 나중에 받아도 저장 모델이 버틸 수 있게 `sourcePackage`를 유지하는 편이 낫습니다.
- 현재 목적은 월 사용량 확인이므로, 상세 회계 시스템처럼 과도한 정합성보다 보수적 저장과 간단한 월 집계가 우선입니다.

## 함께 읽을 문서

- `docs/modules/wallet-transaction-parser.md`
- `docs/modules/wallet-expense-feature.md`
