#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RAGENT_PROJECT_DIR="${RAGENT_PROJECT_DIR:-${ROOT_DIR}/../ragent}"
BUILD_TIMEOUT_SECONDS="${RAGENT_BUILD_TIMEOUT_SECONDS:-480}"
BUILD_LOG="${RAGENT_BUILD_LOG:-${ROOT_DIR}/.runtime/ragent-integration/ragent-build.log}"

if [[ ! -r "${RAGENT_PROJECT_DIR}/pom.xml" || ! -r "${RAGENT_PROJECT_DIR}/bootstrap/pom.xml" ]]; then
  echo "Associated Ragent Maven project is missing: ${RAGENT_PROJECT_DIR}" >&2
  exit 1
fi

mkdir -p "$(dirname "${BUILD_LOG}")"
export MAVEN_OPTS="${RAGENT_BUILD_MAVEN_OPTS:--Xms128m -Xmx512m -XX:+UseSerialGC}"

timeout --signal=TERM --kill-after=20s "${BUILD_TIMEOUT_SECONDS}s" \
  mvn -nsu -f "${RAGENT_PROJECT_DIR}/pom.xml" -pl bootstrap -am \
    -DskipTests -Dspotless.check.skip=true -Dspotless.apply.skip=true \
    package >"${BUILD_LOG}" 2>&1

RAGENT_JAR="${RAGENT_JAR:-${RAGENT_PROJECT_DIR}/bootstrap/target/bootstrap-0.0.1-SNAPSHOT.jar}"
if [[ ! -s "${RAGENT_JAR}" ]]; then
  echo "Ragent build completed but executable jar is missing: ${RAGENT_JAR}" >&2
  exit 1
fi

echo "Ragent artifact ready: ${RAGENT_JAR}"
