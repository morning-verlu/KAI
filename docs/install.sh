#!/usr/bin/env sh
set -eu

VERSION="${KAIOS_VERSION:-0.3.1}"
REPO="morning-verlu/KAI"
BASE_URL="https://github.com/${REPO}/releases/download/v${VERSION}"
ARCHIVE="kaios-${VERSION}.zip"
CHECKSUMS="kaios-${VERSION}-checksums.txt"
INSTALL_DIR="${KAIOS_INSTALL_DIR:-"$HOME/.kaios"}"
BIN_DIR="${INSTALL_DIR}/bin"

need() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "kaios installer: missing required command: $1" >&2
    exit 1
  fi
}

sha256_file() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  elif command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    echo "kaios installer: missing shasum or sha256sum" >&2
    exit 1
  fi
}

find_readme_context() {
  pattern="$1"
  for candidate in ./*; do
    if [ -f "$candidate" ]; then
      name="${candidate#./}"
      case "$name" in
        $pattern)
          printf ' --context %s' "$name"
          return 0
          ;;
      esac
    fi
  done
  return 1
}

readme_context_arg() {
  find_readme_context '[Rr][Ee][Aa][Dd][Mm][Ee].[Mm][Dd]' && return
  find_readme_context '[Rr][Ee][Aa][Dd][Mm][Ee].[Mm][Aa][Rr][Kk][Dd][Oo][Ww][Nn]' && return
  find_readme_context '[Rr][Ee][Aa][Dd][Mm][Ee]' && return
}

project_run_command() {
  kaios_cmd="$1"
  context="$(readme_context_arg || true)"
  printf '%s run --index .%s --out artifacts/project.md --trace-out artifacts/trace.json --force "summarize this project"' "$kaios_cmd" "$context"
}

need curl
need unzip
need awk

TMP_DIR="$(mktemp -d 2>/dev/null || mktemp -d -t kaios)"
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT INT TERM

echo "Installing KAI OS CLI v${VERSION}"
echo "Install dir: ${INSTALL_DIR}"

cd "$TMP_DIR"
curl -fsSL -o "$ARCHIVE" "${BASE_URL}/${ARCHIVE}"
curl -fsSL -o "$CHECKSUMS" "${BASE_URL}/${CHECKSUMS}"

EXPECTED="$(awk -v archive="$ARCHIVE" '$2 == archive { print $1 }' "$CHECKSUMS")"
if [ -z "$EXPECTED" ]; then
  echo "kaios installer: checksum entry not found for ${ARCHIVE}" >&2
  exit 1
fi

ACTUAL="$(sha256_file "$ARCHIVE")"
if [ "$EXPECTED" != "$ACTUAL" ]; then
  echo "kaios installer: checksum mismatch for ${ARCHIVE}" >&2
  echo "expected: ${EXPECTED}" >&2
  echo "actual:   ${ACTUAL}" >&2
  exit 1
fi

unzip -q "$ARCHIVE"
mkdir -p "$INSTALL_DIR"
rm -rf "${INSTALL_DIR}/kaios-${VERSION}"
mv "kaios-${VERSION}" "$INSTALL_DIR/"
mkdir -p "$BIN_DIR"
ln -sfn "${INSTALL_DIR}/kaios-${VERSION}/bin/kaios" "${BIN_DIR}/kaios"

echo
echo "KAI OS CLI installed:"
echo "  ${BIN_DIR}/kaios"
echo
if command -v kaios >/dev/null 2>&1; then
  PROJECT_RUN="$(project_run_command kaios)"
  echo "Try:"
  echo "  kaios tour"
  echo "  kaios quickstart"
  echo "  kaios review"
  echo "  kaios evidence --summary"
  echo "  kaios next"
  echo "  ${PROJECT_RUN}"
  echo "  kaios ps"
  echo "  kaios trace --check"
else
  PROJECT_RUN="$(project_run_command "${BIN_DIR}/kaios")"
  echo "Add this to your shell profile if kaios is not on PATH:"
  echo "  export PATH=\"${BIN_DIR}:\$PATH\""
  echo
  echo "Try now:"
  echo "  ${BIN_DIR}/kaios tour"
  echo "  ${BIN_DIR}/kaios quickstart"
  echo "  ${BIN_DIR}/kaios review"
  echo "  ${BIN_DIR}/kaios evidence --summary"
  echo "  ${BIN_DIR}/kaios next"
  echo "  ${PROJECT_RUN}"
  echo "  ${BIN_DIR}/kaios ps"
  echo "  ${BIN_DIR}/kaios trace --check"
fi
