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

- 사용자 가시 저장 위치: `Download/gemma4_ondevice_test/`
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
4. 선택 모델 갱신 후 `LlmEngine.loadModel()`

### 엔진 로드

1. `ModelStore.prepareRuntimeModelFile()`
2. `LlmEngine.loadModel()`
3. `Engine.initialize()`
4. 세션별 `Conversation` 생성

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

## 함께 읽을 문서

- 상위 액션 연결은 `docs/modules/app-shell.md`
- 빌드/에셋 포함은 `docs/modules/build-assets.md`
