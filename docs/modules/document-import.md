# Document Import Module

## 책임

- 외부 txt 문서 읽기
- 섹션 분리
- 섹션별 카테고리 추천
- 사용자 지식 문서 파일 저장
- override manifest 반영

## 먼저 읽을 파일

- `app/src/main/java/com/example/gemma4ondevicetest/DocumentImporter.kt`
- `app/src/main/java/com/example/gemma4ondevicetest/ManifestLoader.kt`
- `app/src/main/java/com/example/gemma4ondevicetest/MainActivity.kt`

## 핵심 흐름

1. `MainActivity.importDocument()`
2. `DocumentImporter.readTextFromUri()`
3. `DocumentImporter.detectSections()`
4. UI에서 `SectionEditState` 수정
5. `MainActivity.confirmDocumentImport()`
6. `DocumentImporter.saveSections()`
7. `ManifestLoader.addOverrideCategories()`

## 섹션 분리 규칙

- `#`, `##` 헤더를 새로운 섹션 시작점으로 사용
- 충분한 내용이 쌓인 뒤 빈 줄이 나오면 섹션 분리
- 길이 80자 이하 결과는 대부분 제외
- 적절한 섹션이 없으면 원문 전체를 한 섹션으로 처리

## 저장 구조

- 파일 저장: `filesDir/knowledge/custom/`
- 메타데이터 저장: `filesDir/knowledge/manifest_override.json`

## 요구사항별로 볼 위치

| 요구사항 | 볼 파일/함수 |
|---|---|
| 문서 형식 지원 확대 | `DocumentImporter.readTextFromUri()` |
| 섹션 분리 규칙 조정 | `DocumentImporter.splitIntoSections()` |
| 자동 카테고리 추천 조정 | `DocumentImporter.scoreSection()` |
| 저장 파일명 정책 조정 | `DocumentImporter.saveSections()` |
| 업로드 UI 수정 | `MainActivity.importDocument()`, `GemmaComposeUi.kt` |

## 수정 시 주의

- 현재 추천은 기존 카테고리 키워드 수 기반이라 새 문서의 분류 품질이 높지 않을 수 있습니다.
- `confirmedMajor`와 `confirmedMiddle`이 비어 있으면 저장되지 않습니다.
- 저장 후 `refreshRuntimeState()`가 호출되어야 UI와 manifest 캐시가 갱신됩니다.

## 함께 읽을 문서

- 지식 병합과 라우팅은 `docs/modules/knowledge-routing.md`
- 화면 상태는 `docs/modules/app-shell.md`
