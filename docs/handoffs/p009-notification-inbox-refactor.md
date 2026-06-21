### 작업명

알림 원본 저장소 통합: notification inbox 도입과 기존 store 정리

### 목적

현재 Wallet 로그, 정기결제 후보, 카드 인사이트 후보가 같은 raw 알림을 중복 저장하고 있으므로, 공통 notification inbox 저장소 하나를 기준으로 기능별 상태만 파생 관리하도록 정리한다.

### 변경 대상 파일

- `app/src/main/java/com/example/gemma4ondevicetest/wallet/*`
- 필요 시 `app/src/main/java/com/example/gemma4ondevicetest/MainActivity.kt`
- 필요 시 `docs/modules/wallet-expense-feature.md`
- 필요 시 `docs/modules/card-expense-ledger.md`
- 필요 시 `project.md`

### 구현 요구사항

- raw 알림 원본을 저장하는 공통 저장소를 새로 도입한다.
- 저장소는 최소한 아래 정보를 유지해야 한다.
- raw 알림 본문/제목/패키지/notificationKey/postedAt/monthKey
- Wallet 파서 처리 결과 상태
- 정기결제 후보 분석 대상 여부/분석 시각
- 카드 인사이트 분석 대상 여부/분석 시각
- 기존 `WalletNotificationLogStore`, `SubscriptionNotificationStore`, `CardExpenseCandidateStore`가 raw 알림 전체를 따로 저장하지 않도록 정리한다.
- 가능하면 위 store들은 제거하거나, 남기더라도 공통 inbox를 읽는 thin adapter 역할만 하게 만든다.
- `WalletNotificationListenerService`는 raw 알림을 공통 inbox에 1회만 기록하고, 이후 기능별 coordinator는 그 레코드를 참조/갱신하도록 바꾼다.
- `SubscriptionAnalysisCoordinator`, `SubscriptionAnalysisWorker`, `CardExpenseInsightWorker`, `WalletExpenseCoordinator`의 동작이 새 inbox 구조에서 유지되어야 한다.
- 월 변경 정책이 깨지지 않아야 한다.
- Wallet 거래 저장소와 AI 분석 결과 저장소는 유지해도 되지만, raw inbox 중복 저장은 제거해야 한다.
- 기존 UI에서 보이는 알림 로그, 정기결제 후보 분석, 카드 인사이트 분석이 계속 동작해야 한다.
- 구조가 바뀌면 관련 문서를 함께 갱신한다.

### 금지 사항

- 카드 거래 저장소 자체를 Room 등으로 대규모 교체하지 말 것
- 기존 기능을 없애지 말 것
- raw 알림을 다시 여러 prefs에 복제하는 방식으로 남기지 말 것
- 사용자 기존 변경을 덮어쓰지 말 것

### 검증 방법

- listener에서 raw 알림 append 경로가 1곳인지 코드 경로 확인
- subscription/card insight worker가 공통 inbox에서 pending 대상을 읽는지 확인
- wallet log 화면이 공통 inbox 기반으로 계속 표시되는지 확인
- 월 변경 시 이전 달 raw 상태가 비워지는지 확인
- 가능하면 빌드 또는 최소 컴파일 검증 수행
- 빌드 불가 시 사유 명시

### 완료 조건

- raw 알림 원본 저장이 공통 inbox 1곳으로 정리된다.
- Wallet/정기결제/카드 인사이트는 raw 복제 저장 없이 상태만 관리한다.
- 기존 UI/worker 흐름이 유지된다.
- 수정 파일, 검증 결과, 남은 리스크를 보고한다.

### 보고 형식

- 수정 파일
- 주요 변경 내용
- 검증 내용
- 남은 리스크 또는 확인 필요 사항
