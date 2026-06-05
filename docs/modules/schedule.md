# Schedule Module

## 책임

- 캘린더 권한 확인
- 앞으로 7일 일정 조회
- 홈 화면 일정 카드 상태 제공
- 일정 목록 프롬프트 생성
- Gemma 출력 보정
- 알림 스케줄 등록 및 부팅 후 복원

## 먼저 읽을 파일

- `app/src/main/java/com/example/gemma4ondevicetest/schedule/CalendarReader.kt`
- `app/src/main/java/com/example/gemma4ondevicetest/schedule/SchedulePromptBuilder.kt`
- `app/src/main/java/com/example/gemma4ondevicetest/schedule/ScheduleWorker.kt`
- `app/src/main/java/com/example/gemma4ondevicetest/schedule/ScheduleWorkScheduler.kt`
- `app/src/main/java/com/example/gemma4ondevicetest/schedule/ScheduleScreen.kt`
- `app/src/main/java/com/example/gemma4ondevicetest/schedule/ScheduleAlarmReceiver.kt`
- `app/src/main/java/com/example/gemma4ondevicetest/schedule/BootReceiver.kt`

## 핵심 흐름

### 화면에서 직접 조회

1. 권한 확인
2. `CalendarReader.getAllUpcomingWeekEvents()`
3. `CalendarReader.getUpcomingWeekEvents()`
4. `SchedulePromptBuilder.build()`
5. 모델 응답 후 `resolveLines()`로 결과 보정
6. `ScheduleUiState.entries`를 일정 화면이 사용하고, 홈 화면은 이 상태를 카드 문구에만 반영

### 알림 작업

1. 사용자가 시각 지정
2. `ScheduleWorkScheduler.schedule()`
3. `AlarmManager`가 `ScheduleAlarmReceiver` 호출
4. `ScheduleWorker` 실행
5. 일정 조회 후 Gemma 요약
6. `ScheduleNotificationHelper.showSummary()` 호출

## 데이터 범위

- UI 전체 일정: 모든 Google 캘린더 대상
- Gemma 요약용 일정: 앱이 접근 가능한 모든 Google 계정 캘린더 대상

## 요구사항별로 볼 위치

| 요구사항 | 볼 파일/함수 |
|---|---|
| 일정 조회 범위 변경 | `CalendarReader.kt` |
| Google 계정 필터 로직 변경 | `CalendarReader.getCalendarIds()` |
| 알림 시간 저장 방식 변경 | `ScheduleWorkScheduler.kt` |
| 요약 프롬프트 변경 | `SchedulePromptBuilder.build()` |
| Gemma 출력이 누락될 때 fallback 조정 | `SchedulePromptBuilder.resolveLines()` |
| 일정 화면 UI 수정 | `ScheduleScreen.kt` |
| 홈 일정 카드 문구 수정 | `GemmaComposeUi.kt`, `MainActivity.refreshHomeSchedulePreview()` |

## 수정 시 주의

- `ScheduleWorker`는 모델이 로드되지 않은 상태에서도 자체적으로 모델 로드를 시도합니다.
- 요약 실패 시에도 알림을 아예 못 보내기보다 raw fallback이 동작하도록 설계되어 있습니다.
- Exact alarm 권한과 Android 버전별 정책을 같이 봐야 합니다.
- 개인 Gmail 주소를 코드에 하드코딩하지 않고, 계정 선택이 필요하면 설정값 또는 사용자 선택 흐름으로 분리해야 합니다.

## 함께 읽을 문서

- 모델 로드 정책은 `docs/modules/model-runtime.md`
- 상위 화면 연결은 `docs/modules/app-shell.md`
