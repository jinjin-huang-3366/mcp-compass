#!/usr/bin/env sh
set -eu

MAVEN_VERSION="3.9.16"
MAVEN_BASE="${MAVEN_USER_HOME:-${HOME}/.m2}/wrapper/dists/mcp-compass/apache-maven-${MAVEN_VERSION}"
MAVEN_BIN="${MAVEN_BASE}/bin/mvn"

if [ ! -x "${MAVEN_BIN}" ]; then
  ARCHIVE="${TMPDIR:-/tmp}/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
  URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
  mkdir -p "$(dirname "${MAVEN_BASE}")"
  echo "Downloading Apache Maven ${MAVEN_VERSION}..." >&2
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "${URL}" -o "${ARCHIVE}"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "${URL}" -O "${ARCHIVE}"
  else
    echo "curl or wget is required to bootstrap Maven." >&2
    exit 1
  fi
  rm -rf "${MAVEN_BASE}"
  tar -xzf "${ARCHIVE}" -C "$(dirname "${MAVEN_BASE}")"
  rm -f "${ARCHIVE}"
fi

exec "${MAVEN_BIN}" "$@"
