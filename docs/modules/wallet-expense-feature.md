# Wallet Expense Feature Module

## 목적

삼성 Wallet 알림을 받아 카드 사용금액을 확인하는 전체 기능의 상위 설계 문서입니다.  
이 문서는 구현 순서와 모듈 간 연결 지점을 설명합니다.

이 기능은 AI를 사용하지 않고, 알림 수집 + 규칙 기반 파싱 + 월별 집계만으로 구현하는 것을 전제로 합니다.

## 목표 기능

- 삼성 Wallet 알림 수신
- 카드 결제 알림만 선별
- 결제 금액, 카드명, 가맹점, 승인시각 추출
- 로컬에 당월 거래만 저장
- 홈 화면과 카드 사용내역 화면에서 최근 거래/월별 합계 조회
- 추후 채팅이나 지식 응답과 연결 가능한 구조 확보

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
  -> RawNotificationStore
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

- 분석은 알림 수신 직후 수행합니다.
- 화면 조회 시점에는 raw 알림 전체를 다시 파싱하지 않습니다.
- 화면에서는 이미 저장된 거래를 집계만 합니다.
- 파싱 실패 raw만 당월 내 재분석 대상으로 남길 수 있습니다.

## DB 정책

- DB는 당월 데이터만 유지합니다.
- 월이 바뀌면 기존 월 raw/transaction 데이터를 모두 삭제합니다.
- 목적은 "한 달 카드 사용량 확인"이므로 장기 거래 이력 저장은 범위 밖입니다.

## 카드 알림 판정 원칙

저장 허용:

- 금액 존재
- 카드성 키워드 존재
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
3. 원시 알림 로그 저장
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
- raw/transaction 모두 당월만 유지
