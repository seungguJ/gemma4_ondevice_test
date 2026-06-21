# App Usage Personalization Plan

## 목적

기기 전체 앱 사용 패턴을 로컬에 수집해, 이후 온디바이스 개인화 추천 모델 학습에 사용할 수 있는 기반을 만든다.

## 현재 현황

### 완료된 항목

- `Usage Access` 권한 진입점 추가
- `UsageEvents` 기반 foreground 세션 수집기 추가
- 로컬 SQLite DB(`app_usage_logs.db`) 추가
- 앱 allowlist 필터 추가
  - 포함: 네가 지정한 packageName allowlist, packageName token, 앱 라벨 allowlist
  - 제외: allowlist 밖 앱 전체
- 저장 스키마 정의
  - `package_name`
  - `app_category` (참고용 저장값)
  - `started_at_millis`
  - `ended_at_millis`
  - `duration_seconds`
  - `weekday`
  - `hhmm`
- 최근 처리 시각과 pending foreground 세션 메타 저장
- 수동 동기화 액션 추가
- 15분 주기 WorkManager 동기화 예약 추가
- 5초 미만 foreground 세션 제외
- raw 앱 사용 세션은 최대 7일만 보관
- 현재 보관 중인 7일 세션을 `next app` 학습 예제 CSV로 내보내는 기능 추가
- 앱 내부 뷰어 화면 추가
  - 권한 상태
  - 세션 수 / 앱 수 / 누적 시간 / 마지막 동기화 시각
  - 상위 사용 앱
  - 최근 세션 목록
  - 학습 CSV 내보내기
  - DB 비우기

### 아직 하지 않은 항목

- 학습용 feature table 생성
- 추천 노출/클릭 로그 저장
- 앱 카테고리 정규화
- 백그라운드 수집 안정화 검증
- 대량 로그 보관 정책
- 모델 학습/추론 모듈

## 현재 설계 요약

```text
Usage Access Permission
  -> AppUsageCollector
  -> AppUsageLogStore(SQLite)
  -> AppUsageSyncWorker / AppUsageSyncScheduler
  -> MainActivity state refresh
  -> App Usage Viewer Screen
```

세션 단위 저장을 우선한다. 원시 이벤트 전체를 저장하지 않고, `foreground -> background` 구간을 하나의 사용 세션으로 변환해 적재한다.

현재는 아래 조건을 모두 통과한 앱만 적재한다.

1. packageName 또는 앱 라벨이 allowlist와 매칭될 것
2. foreground 세션 길이가 5초 이상일 것

보관 정책:

- raw 세션은 최대 7일만 보관한다.
- 첫 동기화 또는 정책 초기화 후에는 최근 7일 범위까지 UsageEvents를 백필한다.
- 후속 동기화에서는 마지막 처리 이벤트 이후 데이터를 누적하고, 7일보다 오래된 세션을 삭제한다.

추천 정책:

- 1차 목표는 최근 7일 사용 데이터 기반 `next app` 추천이다.
- 학습 샘플은 시간순 세션에서 `현재/최근 상태 -> 다음 시작 앱(package_name)` 구조로 생성한다.
- 같은 시간대 자주 쓰는 앱은 같은 7일 DB에서 `weekday + hhmm bucket -> package_name 빈도`로 집계한다.

`AppUsageCollector`는 런처에 아이콘이 있는 앱(`PackageManager.getLaunchIntentForPackage`가 null이 아닌 패키지)만 세션으로 적재한다. 시스템 UI, 입력기, 백그라운드 서비스 컴포넌트 등 런처 아이콘이 없는 패키지는 수집 단계에서 제외해, 사용자가 실제로 실행하는 설치된 앱만 DB에 남도록 한다.

## 저장 데이터 의미

- `hhmm`: 세션 시작 시각을 `0930`, `2215` 같은 정수로 저장
- `weekday`: 월=1, 일=7
- `duration_seconds`: 세션 길이
- `app_category`: Android 공식 앱 카테고리 정수값

## 현재 SQLite 스키마

DB 이름은 `app_usage_logs.db`, 현재 버전은 `5`입니다.

### `app_usage_sessions`

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | `INTEGER PRIMARY KEY AUTOINCREMENT` | 내부 row id |
| `package_name` | `TEXT NOT NULL` | 앱 패키지명 |
| `app_category` | `INTEGER NOT NULL` | Android 앱 카테고리 |
| `started_at_millis` | `INTEGER NOT NULL` | foreground 시작 시각 |
| `ended_at_millis` | `INTEGER NOT NULL` | foreground 종료 시각 |
| `duration_seconds` | `INTEGER NOT NULL` | 세션 길이 |
| `weekday` | `INTEGER NOT NULL` | 월=1, 일=7 |
| `hhmm` | `INTEGER NOT NULL` | 시작 시각 HHMM |

제약:

- `UNIQUE(package_name, started_at_millis, ended_at_millis)`
- `idx_app_usage_started_at` on `started_at_millis DESC`
- `idx_app_usage_package_name` on `package_name`

### `app_usage_meta`

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `meta_key` | `TEXT PRIMARY KEY` | 메타 키 |
| `meta_value` | `TEXT NOT NULL` | 문자열 값 또는 JSON |

현재 사용하는 메타 키:

- `last_synced_at`
- `last_processed_event_at`
- `pending_foreground_sessions`

학습 CSV export 컬럼:

- `example_id`
- `event_started_at_millis`
- `event_started_at_iso`
- `weekday`
- `hhmm`
- `time_bucket_30m`
- `previous_app`
- `previous_app_label`
- `previous_duration_seconds`
- `previous_category`
- `recent_app_1`
- `recent_app_2`
- `recent_app_3`
- `recent_sequence`
- `gap_since_previous_seconds`
- `label_next_app`
- `label_next_app_label`
- `label_next_started_at_millis`
- `label_next_started_at_iso`

CSV 생성 규칙:

- 시간순 raw 세션을 기반으로 만든다.
- 같은 앱이 연속으로 기록된 경우 하나의 세션 흐름으로 압축한다.
- 각 행은 `현재 세션 -> 다음 시작 앱` 한 쌍이다.
- `recent_app_1`은 현재 앱, `recent_app_2/3`은 그 이전 앱이다.
- 학습 예제를 만들려면 최소 2개 이상의 세션이 필요하다.

현재 allowlist:

- 네이버 웹툰
- 네이버페이
- 배달의 민족
- 빗썸
- 삼성증권
- 신한은행
- 신한카드
- 영웅문S글로벌
- 카카오T
- 카카오내비
- 카카오맵
- 카카오버스
- 카카오지하철
- 카카오톡
- 캐치테이블
- 캘린더
- 코레일톡
- 토스
- GPT
- Chrome
- Claude
- Instagram
- KB Pay
- 새마을금고
- monimo
- 네이버
- T멤버십
- YouTube
- YouTube Music

주의:

- 패키지명이 아니라 앱 라벨 기반 정규화 매칭이다.
- 현재는 packageName 직접 매칭을 우선하고 앱 라벨 기반 정규화 매칭을 fallback으로 사용한다.
- 앱 라벨 매칭에서는 공백, 대소문자, 일부 특수문자를 무시한다.
- packageName이 allowlist에 없고 앱 라벨이 기기에서 다르게 표시되면 누락될 수 있다.
- `GPT`는 실제 앱 라벨이 `ChatGPT`일 수 있어 부분 매칭으로 처리한다.
- 정책 버전 변경 시 기존 세션과 증분 처리 기준을 초기화해 새 기준으로 최근 7일을 다시 백필한다.
- 5초 미만 세션은 앱 전환, 딥링크, 알림 탭 직후 종료 같은 노이즈로 보고 저장하지 않는다.

이 스키마는 이후 아래 feature로 확장 가능하다.

- 시간대 bucket
- sin/cos 시간 인코딩
- 직전 앱
- 최근 N개 앱 시퀀스
- 앱 전환 간격
- 추천 노출 후 실제 실행 여부

## 다음 단계 계획

### Phase 1. 데이터 품질 안정화

- 여러 제조사/OS 버전에서 `UsageEvents` 수집 품질 확인
- 세션 누락/중복 여부 점검
- 장시간 foreground 유지 앱의 처리 정책 점검
- DB 증가 속도 확인
- allowlist packageName과 실제 기기 packageName 차이 점검
- allowlist 이름과 실제 기기 앱 라벨 차이 점검
- 학습 CSV export 결과로 7일 데이터가 학습 샘플 생성에 충분한지 확인

### Phase 2. 학습용 피처 계층 추가

- 최근 앱 시퀀스 요약 테이블 추가
- `previous_app_package`
- `recent_sequence`
- `time_bucket`
- `session_count_by_slot`
- 추천 평가용 label 구조 설계

### Phase 3. 추천용 로그 추가

- 추천 노출 시각 저장
- 추천된 앱 목록 저장
- 실제 클릭/실행 결과 저장
- `impression -> outcome` 연결 키 설계

### Phase 4. 모델 MVP

- 1차 후보
  - 시간대 빈도 기반
  - Markov 전이 기반
  - 온라인 로지스틱 회귀
- 평가 지표
  - Top-1 accuracy
  - Top-3 accuracy
  - 추천 후 실행 전환율

### Phase 5. UX 확장

- 홈 추천 카드
- 위젯
- 시간대별 루틴 요약
- 앱 카테고리별 묶음 추천

## 리스크

- `UsageEvents` 보관 기간과 제조사 구현 차이
- 일부 앱의 foreground/background 이벤트 불안정성
- 권한 허용 UX가 좋지 않음
- 7일 raw 보관만으로 패턴이 약한 앱은 학습 샘플이 부족할 수 있음
- 기기 전체 앱 사용 기록은 민감 정보이므로 내보내기/공유 정책을 별도로 정해야 함
- packageName과 앱 라벨이 모두 예상과 다르면 allowlist에 있어도 수집되지 않을 수 있음

## 다음 작업 시작 시 우선 확인 파일

1. `project.md`
2. `docs/app-usage-personalization-plan.md`
3. `README.md`
4. `ARCHITECTURE.md`
5. `app/src/main/java/com/example/gemma4ondevicetest/usage/*`
6. `app/src/main/java/com/example/gemma4ondevicetest/MainActivity.kt`
7. `app/src/main/java/com/example/gemma4ondevicetest/GemmaComposeUi.kt`
