# Chat Session Module

## 책임

- 채팅 세션 생성, 선택, 삭제
- 메시지 저장과 제목 갱신
- 사용자 입력을 모델 프롬프트로 변환
- 모델 응답을 세션 단위로 유지

## 먼저 읽을 파일

- `app/src/main/java/com/example/gemma4ondevicetest/ChatSession.kt`
- `app/src/main/java/com/example/gemma4ondevicetest/ChatMessage.kt`
- `app/src/main/java/com/example/gemma4ondevicetest/ChatSessionStore.kt`
- `app/src/main/java/com/example/gemma4ondevicetest/MainActivity.kt`
- `app/src/main/java/com/example/gemma4ondevicetest/LlmEngine.kt`
- `app/src/main/java/com/example/gemma4ondevicetest/GemmaComposeUi.kt`

## 데이터 모델

- `ChatSession`: 세션 ID, 종류, 제목, 메시지 목록, 갱신 시각
- `ChatMessage`: 텍스트와 사용자 여부
- `ChatKind`: 현재는 `GENERAL` 하나만 사용
- 채팅은 홈 화면의 독립 진입점이며 일정/카드 사용내역과 분리된 화면으로 유지

## 핵심 흐름

1. `createNewSession()` 또는 기존 세션 선택
2. `sendPrompt()`가 사용자 메시지를 저장
3. 필요 시 이전 대화 일부를 `buildConversationHistory()`로 구성
4. `buildModelPrompt()`가 일반 질문 또는 지식 주입 프롬프트 생성
5. `LlmEngine.generateForSession()`이 세션별 `Conversation`으로 응답 생성
6. 응답을 메시지 목록에 다시 저장

## 저장 방식

- 세션과 메시지는 `SharedPreferences`에 JSON 문자열로 저장됩니다.
- 활성 세션 ID도 별도 key로 저장됩니다.

## 요구사항별로 볼 위치

| 요구사항 | 볼 파일/함수 |
|---|---|
| 세션 저장 포맷 변경 | `ChatSessionStore.kt` |
| 세션 생성 규칙 변경 | `ChatSessionStore.createSession()`, `MainActivity.createNewSession()` |
| 응답 생성 전 프롬프트 수정 | `MainActivity.buildModelPrompt()` |
| 이전 대화 포함 정책 수정 | `MainActivity.buildConversationHistory()` |
| 응답 상단 포맷 수정 | `MainActivity.formatModelReply()` |

## 수정 시 주의

- `LlmEngine`는 세션 ID별 `Conversation` 캐시를 유지합니다.
- 세션 삭제 시 `LlmEngine.clearSession(sessionId)`를 함께 호출해야 KV 캐시가 남지 않습니다.
- 현재 `ChatKind`는 확장 여지가 있지만 실제 분기 로직은 거의 없습니다. 새 채팅 종류를 추가하면 UI, 세션 생성, 프롬프트 정책을 같이 수정해야 합니다.

## 함께 읽을 문서

- 모델 동작은 `docs/modules/model-runtime.md`
- 지식 주입은 `docs/modules/knowledge-routing.md`
