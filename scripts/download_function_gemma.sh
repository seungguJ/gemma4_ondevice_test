#!/usr/bin/env bash
set -euo pipefail

MODEL_REPO="${MODEL_REPO:-litert-community/functiongemma-270m-ft-mobile-actions}"
OUTPUT_DIR="app/src/main/assets/models"
MODEL_API_URL="https://huggingface.co/api/models/${MODEL_REPO}"

if [[ -z "${HF_TOKEN:-}" ]]; then
  echo "HF_TOKEN is required."
  echo "1. Log in to Hugging Face."
  echo "2. Accept the Gemma license for ${MODEL_REPO}."
  echo "3. Create a read token."
  echo "4. Run: HF_TOKEN=hf_xxx scripts/download_function_gemma.sh"
  exit 1
fi

mkdir -p "${OUTPUT_DIR}"

MODEL_FILE="${MODEL_FILE:-$(
  curl \
    --fail \
    --silent \
    --show-error \
    --header "Authorization: Bearer ${HF_TOKEN}" \
    "${MODEL_API_URL}" \
    | grep -o '"rfilename":"[^"]*\.litertlm"' \
    | head -n 1 \
    | cut -d '"' -f 4
)}"

if [[ -z "${MODEL_FILE}" ]]; then
  echo "Could not find a .litertlm file in ${MODEL_REPO}."
  echo "Set MODEL_FILE explicitly if the API response format changed."
  exit 1
fi

OUTPUT_FILE="${OUTPUT_DIR}/${MODEL_FILE}"
MODEL_URL="https://huggingface.co/${MODEL_REPO}/resolve/main/${MODEL_FILE}"

curl \
  --fail \
  --location \
  --continue-at - \
  --header "Authorization: Bearer ${HF_TOKEN}" \
  "${MODEL_URL}" \
  --output "${OUTPUT_FILE}"

echo "Downloaded ${MODEL_REPO}/${MODEL_FILE} to ${OUTPUT_FILE}"
