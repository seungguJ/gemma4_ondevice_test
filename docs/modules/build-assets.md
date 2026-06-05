# Build And Assets Module

## 책임

- Android 앱 빌드 설정
- 권한 선언
- 번들 asset 포함
- 외부 모델 다운로드 스크립트 유지

## 먼저 읽을 파일

- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `scripts/download_function_gemma.sh`
- `gradle/libs.versions.toml`

## 현재 빌드 관련 포인트

- `compileSdk`와 `targetSdk`는 36
- `minSdk`는 24
- Compose 활성화
- LiteRT-LM Android `0.10.0`
- WorkManager 사용
- ABI 필터: `arm64-v8a`, `x86_64`

## 번들 모델 처리

- 루트의 `mobile-actions_q8_ekv1024.litertlm` 파일이 존재하면
- `prepareBundledFunctionGemma` 태스크가
- `build/generated/assets/functionGemma/models/`로 복사하고
- main assets source set에 포함합니다

## 요구사항별로 볼 위치

| 요구사항 | 볼 파일/함수 |
|---|---|
| SDK/의존성 버전 변경 | `app/build.gradle.kts`, `gradle/libs.versions.toml` |
| 새 권한 추가 | `AndroidManifest.xml` |
| 번들 모델 포함 방식 변경 | `app/build.gradle.kts` |
| Hugging Face 다운로드 스크립트 수정 | `scripts/download_function_gemma.sh` |
| ABI 지원 범위 조정 | `app/build.gradle.kts` |

## 수정 시 주의

- 현재 README와 코드 기준으로 번들 모델은 빌드에 포함될 수 있지만, 런타임에서 자동 사용 흐름이 핵심 경로는 아닙니다.
- `download_function_gemma.sh`는 Hugging Face 토큰과 라이선스 승인을 전제로 합니다.
- WSL 환경에서는 `cmd.exe /c gradlew.bat assembleDebug` 경로가 가장 덜 깨집니다.

## 함께 읽을 문서

- 모델 사용 흐름은 `docs/modules/model-runtime.md`
- 전체 시스템 연결은 `ARCHITECTURE.md`
