# project.md

## 목적

이 문서는 Codex와 Claude가 함께 사용하는 작업 현황판이다.

- 현재 진행 작업
- 담당자
- 상태
- 최근 결정 사항
- 다음 액션

## 시작 전 참고 순서

작업 시작 전 기본 확인 순서는 아래와 같다.

1. `project.md`
2. `docs/collab_protocol.md`
3. 본인 역할 문서 (`AGENTS.md` 또는 `CLAUDE.md`)
4. `business_context.md`
5. `design.md`
6. `README.md`
7. 관련 `docs/modules/*.md`

## 상태 규칙

- `todo`: 시작 전
- `in_progress`: 진행 중
- `review`: 구현 완료, 검토 대기
- `blocked`: 외부 확인 필요
- `done`: 검증과 문서 반영까지 완료

## 현재 작업 현황

| ID | 작업명 | 담당 | 상태 | 산출물 | 비고 |
|---|---|---|---|---|---|
| P-001 | 협업 문서 체계 수립 | Codex | done | `AGENTS.md`, `CLAUDE.md`, `project.md`, `business_context.md`, `design.md`, `docs/collab_protocol.md`, `docs/template.md` | 초기 운영 문서 |
| P-002 | 앱 대표 이미지 개선 반영 | Claude | done | 앱 아이콘 및 UI 이미지 교체 | 빌드 검증은 Java 환경 필요 |
| P-003 | Codex-Claude 자동 인계 실행층 추가 | Codex | done | `docs/agent_runtime.md`, `scripts/claude_handoff.sh`, 협업 문서 갱신 | 문서 규약을 실제 CLI 호출 흐름으로 연결 |
| P-004 | Git 업로드 제외 규칙 및 개인정보 제거 | Codex | done | `.gitignore`, `CalendarReader.kt`, 관련 문서 갱신 | 로컬 산출물과 하드코딩 이메일 제거 |

## 최근 결정

- Codex는 관리자 역할, Claude는 실무자 역할로 분리한다.
- 역할 문서는 `AGENTS.md`, `CLAUDE.md` 두 파일만 유지한다.
- 진행 상태 공유의 단일 기준 문서는 `project.md`다.
- 협업 프로토콜은 `docs/collab_protocol.md`에서 관리한다.
- 자동 위임은 문서만으로 활성화하지 않고 `docs/agent_runtime.md`와 `scripts/claude_handoff.sh`를 함께 유지한다.
- 로컬 설정, 에이전트 로그, 대형 모델 파일, 개인 계정 정보는 Git 추적 대상에서 제외한다.

## 다음 작업 작성 규칙

새 작업을 추가할 때 아래 항목을 같이 기록한다.

- 작업 목적
- 시작 조건
- 완료 조건
- 관련 문서
- 관련 코드 파일

## 완료 정의

- 요구사항 반영 완료
- 기본 검증 완료
- 리스크 기록 완료
- 필요한 문서 갱신 완료
