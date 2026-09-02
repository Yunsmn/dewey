#!/usr/bin/env bash
# Fetches the encoder the benchmark and the app both use.
# Not committed: 113MB of weights do not belong in git history.
set -euo pipefail

DEST="${1:-$(dirname "$0")/model}"
REPO="https://huggingface.co/Xenova/multilingual-e5-small/resolve/main"

mkdir -p "$DEST"
for file in onnx/model_quantized.onnx tokenizer.json tokenizer_config.json config.json; do
  echo "fetching $file"
  curl -sL --fail -o "$DEST/$(basename "$file")" "$REPO/$file"
done
echo "encoder in $DEST"
