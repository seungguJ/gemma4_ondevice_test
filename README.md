# gemma4_ondevice_test

간단한 Android 온디바이스 채팅 테스트 앱입니다. `kakao-talk-auto-bot`의 `LlmEngine.kt` 구조를 기준으로 LiteRT-LM `Engine` 초기화 방식을 최소화해서 분리했습니다.

## 구성

- `.litertlm` 모델 파일 선택
- 앱 내부 저장소로 복사
- 로컬 `Engine` 로드/언로드
- 단일 화면 채팅 UI

## 실행 메모

- Android 에뮬레이터보다 실제 ARM64 기기에서 테스트하는 편이 안전합니다.
- 모델 파일은 사용자가 직접 준비해야 합니다.
- 첫 로드는 모델 크기에 따라 시간이 걸릴 수 있습니다.
