#!/usr/bin/env bash
set -euo pipefail

INSTALLER_TYPE="${INSTALLER_TYPE:-app-image}"
APP_VERSION="${APP_VERSION:-1.0.0}"
APP_NAME="Audio Downloader Pro"
JAR_NAME="audio-downloader-pro-${APP_VERSION}.jar"

mkdir -p /dist

mvn -DskipTests package

jpackage \
  --name "${APP_NAME}" \
  --app-version "${APP_VERSION}" \
  --input target \
  --main-jar "${JAR_NAME}" \
  --main-class org.springframework.boot.loader.launch.JarLauncher \
  --type "${INSTALLER_TYPE}" \
  --dest /dist \
  --vendor "Audio Downloader Pro Team" \
  --java-options "-Xmx1g"

echo "Build complete. Installer output is in /dist"
