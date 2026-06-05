# Knowledge Routing Module

## 책임

- 번들 지식 manifest 로드
- 사용자 override manifest 병합
- 질문과 카테고리 키워드 매칭
- 선택된 문서를 프롬프트에 주입

## 먼저 읽을 파일

- `app/src/main/java/com/example/gemma4ondevicetest/KnowledgeModels.kt`
- `app/src/main/java/com/example/gemma4ondevicetest/ManifestLoader.kt`
- `app/src/main/java/com/example/gemma4ondevicetest/AgentRouter.kt`
- `app/src/main/java/com/example/gemma4ondevicetest/KnowledgePromptBuilder.kt`
- `app/src/main/assets/knowledge/manifest.json`

## 현재 구조

- `KnowledgeTool`: 도메인 단위
- `KnowledgeCategory`: 실제 문서 단위
- `ManifestLoader`: 번들 + 사용자 문서 병합
- `AgentRouter`: 키워드 기반 선택
- `KnowledgePromptBuilder`: 문서 본문을 읽어 최종 프롬프트 생성

## 핵심 흐름

1. `ManifestLoader.getTools()`로 도구 목록 로드
2. `AgentRouter.route()`가 질문과 각 카테고리 키워드 매칭
3. 점수 높은 카테고리 최대 2개 선택
4. `KnowledgePromptBuilder.loadText()`가 asset 또는 사용자 파일을 읽음
5. 참고 문서를 포함한 프롬프트를 모델에 전달

## 현재 제약

- Function Calling 기반 분류가 아니라 키워드 수 기반 매칭입니다.
- 도메인은 사실상 `finance` 하나만 번들 제공됩니다.
- 카테고리 선택 점수가 낮아도 키워드만 맞으면 문서가 주입될 수 있습니다.

## 요구사항별로 볼 위치

| 요구사항 | 볼 파일/함수 |
|---|---|
| 새 지식 도메인 추가 | `assets/knowledge/manifest.json`, 새 txt asset |
| 키워드 분류 정확도 조정 | `AgentRouter.route()` |
| 프롬프트 문구 수정 | `KnowledgePromptBuilder.buildAgentPromptResult()` |
| 문서 병합 로직 수정 | `ManifestLoader.kt` |
| 지식 문서 캐시 정책 수정 | `KnowledgePromptBuilder.loadText()` |

## 수정 시 주의

- `ManifestLoader`는 내부 캐시를 가지므로 문서 추가 후 `invalidate()` 또는 `reloadTools()` 흐름을 유지해야 합니다.
- 사용자 문서는 `filePath`, 번들 문서는 `assetName`을 사용합니다.
- `KnowledgePromptBuilder`는 텍스트 캐시를 유지하므로 파일 변경 후 캐시 무효화가 필요할 수 있습니다.

## 함께 읽을 문서

- 업로드 경로는 `docs/modules/document-import.md`
- 응답 연결은 `docs/modules/chat-session.md`
