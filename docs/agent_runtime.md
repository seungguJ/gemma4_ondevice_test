# agent_runtime.md

## 목적

이 문서는 Codex가 Claude를 자동 호출해 작업을 위임할 때 사용하는 실행 규칙을 정의한다.

- 역할 문서를 실제 CLI 호출로 연결한다.
- 입력 형식과 출력 형식을 고정한다.
- 호출 이력을 남겨 추적 가능하게 만든다.

## 구성 요소

- 역할 정의: `AGENTS.md`, `CLAUDE.md`
- 협업 규약: `docs/collab_protocol.md`
- 입력 템플릿: `docs/template.md`
- 실행 스크립트: `scripts/claude_handoff.sh`
- 실행 로그: `.claude/handoffs/`

## 사전 조건

- `claude` CLI가 설치되어 있어야 한다.
- 자동 위임 전에 한 번은 `claude auth login --claudeai` 또는 적절한 인증 방식으로 인증을 마쳐야 한다.
- Claude가 읽어야 할 프로젝트 파일은 현재 작업 디렉터리 아래에 있어야 한다.

## 기본 원칙

1. 역할 문서만 수정해서 자동 실행이 되었다고 간주하지 않는다.
2. 자동 위임은 반드시 스크립트 또는 그와 동등한 명시적 호출을 통해 수행한다.
3. Codex는 Claude 호출 전에 목적, 범위, 금지 사항, 검증 방법을 적는다.
4. Claude 결과는 초안일 뿐이며, 최종 승인과 사용자 응답은 Codex가 담당한다.
5. 실행 결과와 프롬프트는 가능한 한 파일로 남긴다.

## 표준 호출 흐름

1. Codex가 `project.md`에서 작업 상태와 충돌 여부를 확인한다.
2. Codex가 `docs/template.md`의 자동 위임 입력 템플릿으로 작업 파일을 만든다.
3. Codex가 `scripts/claude_handoff.sh --task-file <path>`를 실행한다.
4. 스크립트는 프롬프트와 응답을 `.claude/handoffs/`에 저장한다.
5. Codex가 결과를 리뷰하고 승인 여부를 결정한다.
6. 필요하면 `project.md`와 관련 문서를 갱신한다.

## 입력 파일 규칙

- 형식은 Markdown을 사용한다.
- 최소 항목은 작업명, 목적, 변경 대상 파일, 구현 요구사항, 금지 사항, 검증 방법, 완료 조건이다.
- Claude가 바로 보고할 수 있도록 보고 형식도 함께 포함한다.
- 관련 문서 경로는 가능한 한 구체적으로 적는다.

## 출력 파일 규칙

- 기본 출력은 Markdown 텍스트다.
- 실행 시각을 포함한 `*.prompt.md`, `*.response.md`, `*.meta.txt`를 남긴다.
- 메타 파일에는 호출 모드와 입력 파일 경로를 기록한다.

## 권장 사용 예시

```bash
scripts/claude_handoff.sh --task-file docs/handoffs/p004.md
scripts/claude_handoff.sh --task-file docs/handoffs/p005.md --json
scripts/claude_handoff.sh --task-file docs/handoffs/p006.md --label review-pass-1
```

## 주의사항

- 자동 호출은 Claude가 실제 코드 수정까지 수행할 수 있는 강한 권한을 줄 수 있다.
- 따라서 Codex는 불필요하게 넓은 작업 범위를 한 번에 넘기지 않는다.
- 기존 사용자 변경이 많은 파일은 먼저 충돌 위험을 점검한다.
- 검증 실패 또는 문서 불일치가 있으면 승인 전에 보완 요청을 남긴다.
- 인증이 안 된 상태에서는 스크립트가 실패하며, 이 경우 먼저 `claude auth login --claudeai` 또는 적절한 인증 절차를 수행해야 한다.
