#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  scripts/claude_handoff.sh --task-file <path> [--label <name>] [--json]

Options:
  --task-file <path>   Markdown file containing the handoff task.
  --label <name>       Optional label used in output filenames.
  --json               Request JSON output from Claude.
  --help               Show this help message.

Prerequisite:
  Run `claude /login` once before the first automated handoff.
EOF
}

TASK_FILE=""
LABEL=""
OUTPUT_FORMAT="text"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --task-file)
      TASK_FILE="${2:-}"
      shift 2
      ;;
    --label)
      LABEL="${2:-}"
      shift 2
      ;;
    --json)
      OUTPUT_FORMAT="json"
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -z "${TASK_FILE}" ]]; then
  echo "--task-file is required." >&2
  usage >&2
  exit 1
fi

if [[ ! -f "${TASK_FILE}" ]]; then
  echo "Task file not found: ${TASK_FILE}" >&2
  exit 1
fi

if ! command -v claude >/dev/null 2>&1; then
  echo "claude CLI is not installed or not in PATH." >&2
  exit 1
fi

ROOT_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd
)"
cd "${ROOT_DIR}"

RUN_DIR="${ROOT_DIR}/.claude/handoffs"
mkdir -p "${RUN_DIR}"

STAMP="$(date '+%Y%m%d-%H%M%S')"
if [[ -n "${LABEL}" ]]; then
  SAFE_LABEL="$(printf '%s' "${LABEL}" | tr ' /' '__')"
else
  SAFE_LABEL="$(basename "${TASK_FILE}" .md)"
fi

PROMPT_PATH="${RUN_DIR}/${STAMP}-${SAFE_LABEL}.prompt.md"
RESPONSE_EXT="md"
if [[ "${OUTPUT_FORMAT}" == "json" ]]; then
  RESPONSE_EXT="json"
fi
RESPONSE_PATH="${RUN_DIR}/${STAMP}-${SAFE_LABEL}.response.${RESPONSE_EXT}"
META_PATH="${RUN_DIR}/${STAMP}-${SAFE_LABEL}.meta.txt"

CLAUDE_ROLE_TEXT="$(cat "${ROOT_DIR}/CLAUDE.md")"
TASK_TEXT="$(cat "${TASK_FILE}")"

cat > "${PROMPT_PATH}" <<EOF
# Claude Handoff Prompt

## Runtime Context

- Follow the project rules in \`CLAUDE.md\`.
- Read any referenced project documents before editing code.
- Return the result using the report format requested in the task file.

## Task

${TASK_TEXT}
EOF

{
  echo "timestamp=${STAMP}"
  echo "task_file=${TASK_FILE}"
  echo "label=${SAFE_LABEL}"
  echo "output_format=${OUTPUT_FORMAT}"
  echo "prompt_file=${PROMPT_PATH}"
  echo "response_file=${RESPONSE_PATH}"
} > "${META_PATH}"

set +e
claude \
  --print \
  --output-format "${OUTPUT_FORMAT}" \
  --permission-mode bypassPermissions \
  --add-dir "${ROOT_DIR}" \
  --append-system-prompt "${CLAUDE_ROLE_TEXT}" \
  "$(cat "${PROMPT_PATH}")" \
  2>&1 | tee "${RESPONSE_PATH}"
STATUS=${PIPESTATUS[0]}
set -e

if [[ ${STATUS} -ne 0 ]]; then
  echo >&2
  echo "Claude handoff failed with exit code ${STATUS}." >&2
  echo "If Claude is not authenticated yet, run \`claude /login\` and retry." >&2
  echo "See ${RESPONSE_PATH} for the captured output." >&2
  exit "${STATUS}"
fi

echo
echo "Saved prompt to ${PROMPT_PATH}"
echo "Saved response to ${RESPONSE_PATH}"
echo "Saved metadata to ${META_PATH}"
