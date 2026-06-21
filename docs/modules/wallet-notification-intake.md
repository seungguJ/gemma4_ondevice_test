# Wallet Notification Intake Module

## 목적

삼성 Wallet 알림을 앱이 수신할 수 있게 만드는 모듈입니다.  
핵심 책임은 "어떤 알림을 받을 것인가"와 "어떤 알림만 다음 단계로 넘길 것인가"입니다.

이 기능은 AI를 사용하지 않고 Android 알림 리스너와 문자열 규칙 기반으로 동작하는 것을 전제로 합니다.

## 제안 패키지 구조

```text
app/src/main/java/com/example/gemma4ondevicetest/wallet/
  WalletNotificationListenerService.kt
  WalletNotificationFilter.kt
  WalletNotificationModels.kt
  WalletNotificationPermissionManager.kt
```

## 제안 파일 책임

### `WalletNotificationListenerService.kt`

- `NotificationListenerService` 구현
- `onNotificationPosted()`에서 알림 원문 수집
- `NotificationInboxStore`에 raw 알림을 공통 포맷으로 upsert
- 삼성 Wallet 패키지 여부 1차 필터링
- 파싱 모듈로 넘길 원시 데이터 구성
- 월 변경 여부 확인 후 저장소 리셋 트리거

### `WalletNotificationFilter.kt`

- 허용 패키지명 관리
- 카드 사용 알림처럼 보이는지 제목/본문 1차 판별
- 프로모션, 광고, 일반 이벤트 알림 제외
- 계좌이체, 송금, 입출금, 충전 같은 비카드 금융 알림 제외

### `WalletNotificationModels.kt`

- 원시 알림 모델 정의
- 예: `WalletRawNotification`, `WalletNotificationSource`

### `WalletNotificationPermissionManager.kt`

- 알림 리스너 권한 상태 확인
- 시스템 설정 화면 이동 유도
- UI에서 상태 표시할 때 사용할 헬퍼

## 제안 데이터 모델

```text
WalletRawNotification
- monthKey
- packageName
- appLabel
- postedAt
- title
- text
- bigText
- subText
- extrasDump
- notificationKey
```

## 먼저 구현할 때 읽을 파일

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/gemma4ondevicetest/MainActivity.kt`
- `app/src/main/java/com/example/gemma4ondevicetest/GemmaComposeUi.kt`

## 필요한 Android 포인트

- `NotificationListenerService` 선언
- `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`
- 알림 리스너 활성화 여부 확인
- Android 설정의 Notification Access 화면으로 이동

## 분석 시점 규칙

- 알림이 뜨는 순간 수집합니다.
- 수집 직후 1차 필터를 통과한 알림만 raw 저장 대상으로 봅니다.
- 이 단계에서는 "나중에 한꺼번에 분석"하지 않고, 다음 단계 파서로 즉시 넘기는 구조를 전제로 합니다.

## 수집 허용 규칙

필수 조건:

- 삼성 Wallet 허용 패키지명과 일치
- 제목, 본문, bigText 중 하나 이상 존재
- 금융성 알림으로 보이는 텍스트 포함

권장 금융성 키워드 예:

- `승인`
- `결제`
- `사용`
- `취소`
- `카드`

## 즉시 제외 규칙

다음 단어가 있으면 카드 거래 후보에서 제외합니다.

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
- `혜택`
- `이벤트`
- `광고`
- `쿠폰`
- `ATM`
- `이용한도`
- `한도`

## Raw 저장 정책

- raw 알림도 무제한 저장하지 않습니다.
- 당월 카드 후보 알림만 저장합니다.
- 월 키는 `Asia/Seoul` 기준 `yyyy-MM` 을 사용합니다.
- raw 알림 원본은 `NotificationInboxStore` 한 곳에 저장하고, 기능별 분석 여부는 해당 inbox 엔트리의 상태 플래그로 관리합니다.
- 월 변경 감지 시 raw 저장소는 전부 비웁니다.

## 현재 Inbox 저장소

`NotificationInboxStore`는 `SharedPreferences("notification_inbox_store")`를 사용합니다.

| 키 | 값 | 설명 |
|---|---|---|
| `month_key` | `yyyy-MM` | 현재 저장 중인 월 |
| `entries` | `NotificationInboxEntry[]` JSON | 당월 raw 알림과 기능별 상태 |

`NotificationInboxEntry` 필드:

```text
id
monthKey
packageName
appLabel
postedAt
title
text
bigText
subText
notificationKey
subscriptionEligible
subscriptionAnalyzedAt
cardInsightEligible
cardInsightAnalyzedAt
```

현재 최대 저장 개수는 400건입니다. `SubscriptionNotificationStore`와 `CardExpenseCandidateStore`는 별도 raw 복사본을 만들지 않고 이 inbox를 adapter처럼 사용합니다.

## 이 모듈의 출력

파싱 모듈로 넘길 정제 전 알림 객체:

- 삼성 Wallet에서 온 알림
- 카드 결제 가능성이 있는 알림
- 제목/본문이 비어 있지 않은 알림
- 비카드 금융 키워드가 제거된 알림

## 요구사항별로 볼 위치

| 요구사항 | 먼저 볼 위치 |
|---|---|
| 삼성 Wallet 알림을 앱이 못 받는 문제 | `WalletNotificationListenerService.kt`, `AndroidManifest.xml` |
| 알림 권한 유도 UI 추가 | `WalletNotificationPermissionManager.kt`, `MainActivity.kt`, `GemmaComposeUi.kt` |
| 광고성 알림 제외 강화 | `WalletNotificationFilter.kt` |

## 주의사항

- 삼성 Wallet의 실제 패키지명과 알림 포맷은 기기/버전에 따라 달라질 수 있습니다.
- 알림 본문 포맷이 자주 바뀔 수 있으므로 수집 단계는 가능한 한 원문을 많이 보존해야 합니다.
- 이 단계에서는 금액 파싱을 하지 말고, 수집과 필터링만 담당하는 편이 안전합니다.
- raw 저장은 디버깅과 재분석 용도지만, 보관 범위는 "당월"로 제한합니다.

## 함께 읽을 문서

- `docs/modules/wallet-transaction-parser.md`
- `docs/modules/wallet-expense-feature.md`
