# Model Runtime Module

## 책임

- 모델 소스 정의
- 모델 다운로드 또는 가져오기
- 외부 저장 위치와 앱 내부 런타임 복사 관리
- LiteRT-LM 엔진 로드/해제
- 세션별 추론 실행

## 먼저 읽을 파일

- `app/src/main/java/com/example/gemma4ondevicetest/ModelStore.kt`
- `app/src/main/java/com/example/gemma4ondevicetest/LlmEngine.kt`
- `app/src/main/java/com/example/gemma4ondevicetest/MainActivity.kt`

## 현재 모델 소스

- `CUSTOM`
- `GEMMA_4`

## 저장 구조

- 사용자 가시 저장 위치: `Download/flow/`
- 앱 런타임 복사 위치: `filesDir/llm_runtime/`

## 핵심 흐름

### 로컬 파일 선택

1. `OpenDocument`로 URI 선택
2. `ModelStore.importModel()` 호출
3. `CUSTOM`이면 SAF URI를 그대로 저장
4. 실제 엔진 로드 직전에 런타임 경로로 복사

### Gemma 4 다운로드

1. `MainActivity.downloadGemmaModel()`
2. `ModelStore.downloadModel()`
3. 공개 다운로드 경로에 저장
4. 선택 모델 상태 갱신
5. 실제 로드는 사용자가 모델 로드를 누르거나 허용된 추론 흐름에 진입했을 때 수행

### 엔진 로드

1. 사용자 명시 액션 또는 허용 조건을 통과한 Worker가 로드를 요청
2. `ModelRuntimeGate` 또는 호출 경로가 모델 경합/조건을 확인
3. `ModelStore.prepareRuntimeModelFile()`
4. `LlmEngine.loadModel()`
5. `Engine.initialize()`
6. 세션별 `Conversation` 생성

## 요구사항별로 볼 위치

| 요구사항 | 볼 파일/함수 |
|---|---|
| 모델 다운로드 URL 변경 | `ModelStore.GEMMA_4` |
| 다른 모델 소스 추가 | `ModelStore`의 `ModelSource`, `allSources` |
| 로드 실패 원인 추적 | `LlmEngine.loadModel()` |
| 모델 메모리 해제 정책 수정 | `LlmEngine.free()`, `MainActivity.onStop()` |
| 응답 최대 길이/컨텍스트 수정 | `LlmEngine.LlmConfig` |

## 수정 시 주의

- `LlmEngine`는 프로세스 전역 singleton입니다.
- 이미 로드된 엔진이 있으면 `loadModel()`은 재초기화를 건너뜁니다.
- 에뮬레이터 차단 로직이 `LlmEngine.isUnsupportedEmulator()`에 있습니다.
- `prepareRuntimeModelFile()`은 큰 파일 복사를 유발하므로 반복 호출 비용을 고려해야 합니다.
- 모델을 평소 상주 상태로 유지하는 구현은 금지합니다. 앱 시작, 알림 수신, 화면 진입, Worker 예약만으로 `LlmEngine`을 미리 올려 두면 안 됩니다.
- 실시간 AI 분석은 금지합니다. 알림 수신 직후에는 규칙 기반 후보 저장과 작업 예약까지만 수행하고, 모델 추론을 바로 시작하면 안 됩니다.
- 자동 AI 분석은 기능별 조건을 모두 만족할 때만 허용합니다. 정기결제/카드 인사이트는 `24시간 대기 + 충전 중 + 배터리 100% + 다른 모델 작업 없음` 조건을 통과해야 합니다.
- 예외는 사용자가 명시적으로 요청한 강제분석뿐입니다. `지금 분석`, `재분석`, `전체 재분석`처럼 화면 액션으로 시작된 작업만 24시간 대기와 배터리 게이트를 우회할 수 있습니다.
- 새 기능은 추론 직전 로드, 종료 직후 `clearSession()` 및 `free()`를 보장해야 합니다.
- Worker나 서비스는 화면이 이미 모델을 사용하는 경우 경쟁 로드를 피해야 합니다.

## 함께 읽을 문서

- 상위 액션 연결은 `docs/modules/app-shell.md`
- 빌드/에셋 포함은 `docs/modules/build-assets.md`
