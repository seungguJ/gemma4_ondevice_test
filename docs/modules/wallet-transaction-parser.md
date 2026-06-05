# Wallet Transaction Parser Module

## 목적

삼성 Wallet 알림 텍스트에서 카드 사용 거래 정보를 안정적으로 추출하는 모듈입니다.

이 모듈은 AI 없이 정규식과 키워드 규칙만으로 동작합니다.

## 제안 패키지 구조

```text
app/src/main/java/com/example/gemma4ondevicetest/wallet/
  WalletNotificationParser.kt
  WalletParserRules.kt
  WalletNotificationModels.kt
```

## 제안 파일 책임

### `WalletNotificationParser.kt`

- 원시 알림을 입력받아 거래 후보로 변환
- 금액, 카드명, 승인시각, 가맹점, 승인/취소 여부 추출
- 파싱 실패 시 사유 반환

### `WalletParserRules.kt`

- 정규식과 후처리 규칙 보관
- 금액 패턴, 날짜/시각 패턴, 취소 문구 패턴 정의
- 삼성 Wallet 알림 포맷 변형 대응 규칙 추가

### `WalletNotificationModels.kt`

- 파싱 결과 모델 정의
- 예: `ParsedCardTransaction`, `ParseFailure`, `TransactionStatus`

## 제안 파싱 결과 모델

```text
ParsedCardTransaction
- sourcePackage
- notificationKey
- monthKey
- approvedAt
- cardLabel
- merchantName
- amount
- currency
- installmentText
- status
- rawTitle
- rawBody
- dedupeKey
```

## 권장 파싱 전략

1. 제목, 본문, bigText를 우선순위로 합쳐 원문 문자열 생성
2. 제외 키워드가 있는지 먼저 검사
3. 카드 승인/취소 상태 키워드 추출
4. 금액 패턴 추출
5. 날짜/시간 패턴 추출
6. 카드명/가맹점 추정
7. `해외`, `일시불`, `할부` 같은 부가 문구 추출
8. 필수 필드 부족 시 실패 객체 반환

## 분석 시점 규칙

- 파싱은 알림 수신 직후 바로 수행합니다.
- 월 사용량 확인이 목적이므로 raw를 쌓아두고 나중에 전체를 재분석하는 구조를 기본 경로로 쓰지 않습니다.
- 다만 파싱 실패 raw는 당월 내 재분석 후보로 남길 수 있습니다.

## 권장 금액 규칙

- `12,345원`
- `KRW 12,345`
- `12,345`

금액 파싱 시 주의:

- 쉼표 제거 후 `Long`
- 원화 기준 우선 처리
- 포인트 적립, 잔액, 할인액을 승인금액으로 잘못 잡지 않도록 분리

## 카드 승인 판정 규칙

카드 거래로 인정하려면 아래 조건을 모두 만족하는 편이 안전합니다.

- 금액 패턴 존재
- 상태 키워드 존재
  - `승인`, `결제`, `사용`, `취소`
- 카드성 키워드 존재
  - `카드`, `신용`, `체크`, 또는 카드사명
- 제외 키워드 없음

## 비카드 제외 규칙

다음이 포함되면 파싱 성공으로 처리하지 않습니다.

- `계좌이체`
- `이체`
- `송금`
- `입금`
- `출금`
- `계좌`
- `잔액`
- `충전`
- `자동이체`
- `포인트`
- `캐시백`
- `이벤트`
- `광고`
- `혜택`
- `펀드`
- `보험`
- `대출`
- `납부`

## 상태 분류 규칙

- `취소`, `승인취소`, `결제취소` 포함: `CANCELLED`
- `승인`, `결제`, `사용` 포함: `APPROVED`
- 둘 다 불명확: 실패 또는 `UNKNOWN`

## 필수 성공 조건

최소 성공 조건:

- `amount` 존재
- `status` 가 `APPROVED` 또는 `CANCELLED`
- `monthKey` 계산 가능

선택 필드:

- `approvedAt`
- `merchantName`
- `cardLabel`
- `installmentText`

## 예시 분류

허용 예:

- `삼성카드 12,500원 승인`
- `체크카드 8,900원 사용`
- `신용카드 34,000원 결제취소`

제외 예:

- `계좌이체 50,000원`
- `출금 12,000원`
- `입금 300,000원`
- `충전 20,000원`
- `자동이체 45,000원`
- `포인트 2,000 적립`

## 요구사항별로 볼 위치

| 요구사항 | 먼저 볼 위치 |
|---|---|
| 금액 파싱이 자주 실패함 | `WalletNotificationParser.kt`, `WalletParserRules.kt` |
| 승인 취소/부분 취소 구분 필요 | `WalletParserRules.kt`, `TransactionStatus` |
| 카드명/가맹점 추출 정확도 개선 | `WalletNotificationParser.kt` |
| 샘플 알림 기반 테스트 케이스 추가 | `WalletParserRules.kt`, 추후 parser test 파일 |

## 주의사항

- 삼성 Wallet 알림 포맷은 공식 스키마가 아니라 문자열 기반일 가능성이 큽니다.
- 파서 단계는 "추정"을 하더라도, 저장 단계에서는 confidence 또는 필수 필드 검증이 필요합니다.
- 취소 알림은 초기 버전에서 `음수 집계` 또는 `취소 합계`로 처리하는 단순 정책이 가장 안전합니다.
- 목표가 월 사용량 확인이므로, 카드가 아닌 금융 알림을 잘못 포함하지 않는 보수적 판정이 더 중요합니다.

## 함께 읽을 문서

- `docs/modules/wallet-notification-intake.md`
- `docs/modules/card-expense-ledger.md`
- `docs/modules/wallet-expense-feature.md`
