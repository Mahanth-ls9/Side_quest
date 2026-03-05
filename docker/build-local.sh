#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

IMAGE_NAME="${IMAGE_NAME:-audio-downloader-pro-builder}"
INSTALLER_TYPE="${INSTALLER_TYPE:-app-image}"

mkdir -p dist

echo "[1/3] Building Docker image: $IMAGE_NAME"
docker build -t "$IMAGE_NAME" -f docker/Dockerfile .

echo "[2/3] Running packaging container (INSTALLER_TYPE=$INSTALLER_TYPE)"
docker run --rm \
  -e INSTALLER_TYPE="$INSTALLER_TYPE" \
  -v "$ROOT_DIR/dist:/dist" \
  "$IMAGE_NAME"

echo "[3/3] Done. Artifacts are in: $ROOT_DIR/dist"
