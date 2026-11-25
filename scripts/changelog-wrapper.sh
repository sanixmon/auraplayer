#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHANGELOG_FILE="${ROOT_DIR}/CHANGELOG.md"
ASSET_CHANGELOG="${ROOT_DIR}/app/src/main/assets/CHANGELOG.md"

VERSION=""
APK_PATH=""
OUT_PATH=""
SYNC_ASSETS=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version)
      VERSION="${2:-}"
      shift 2
      ;;
    --apk)
      APK_PATH="${2:-}"
      shift 2
      ;;
    --out)
      OUT_PATH="${2:-}"
      shift 2
      ;;
    --sync-assets)
      SYNC_ASSETS=true
      shift
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

if [[ "${SYNC_ASSETS}" == "true" ]]; then
  if [[ -f "${CHANGELOG_FILE}" ]]; then
    mkdir -p "$(dirname "${ASSET_CHANGELOG}")"
    cp "${CHANGELOG_FILE}" "${ASSET_CHANGELOG}"
    echo "Synced ${CHANGELOG_FILE} -> ${ASSET_CHANGELOG}"
  fi
  if [[ -z "${OUT_PATH}" && -z "${VERSION}" ]]; then
    exit 0
  fi
fi

if [[ -z "${VERSION}" ]]; then
  VERSION="$(grep -E 'versionName\s*=\s*"[^"]+"' "${ROOT_DIR}/app/build.gradle.kts" | head -n 1 | sed -E 's/.*"([^"]+)".*/\1/')"
fi
VERSION="${VERSION#v}"
VERSION_CODE="$(grep -E 'versionCode\s*=\s*[0-9]+' "${ROOT_DIR}/app/build.gradle.kts" | head -n 1 | sed -E 's/[^0-9]*([0-9]+).*/\1/' || echo "1")"
COMMIT_SHA="$(git -C "${ROOT_DIR}" rev-parse --short HEAD 2>/dev/null || echo "unknown")"

extract_section() {
  local target_ver="$1"
  if [[ ! -f "${CHANGELOG_FILE}" ]]; then
    return 1
  fi
  awk -v ver="${target_ver}" '
    BEGIN { found=0; printing=0 }
    /^## \[/ {
      if (printing) exit 0
      if (index($0, "## [" ver "]") == 1) {
        found=1
        printing=1
        next
      }
    }
    printing { print }
    END { if (!found) exit 1 }
  ' "${CHANGELOG_FILE}"
}

SECTION_BODY=""
if SECTION_BODY="$(extract_section "${VERSION}" 2>/dev/null)"; then
  :
else
  # Fallback to recent commits since previous tag if version section is missing
  LAST_TAG="$(git -C "${ROOT_DIR}" describe --tags --abbrev=0 2>/dev/null || true)"
  if [[ -n "${LAST_TAG}" ]]; then
    COMMITS="$(git -C "${ROOT_DIR}" log "${LAST_TAG}..HEAD" --pretty=format:'- %s' 2>/dev/null || true)"
  else
    COMMITS="$(git -C "${ROOT_DIR}" log -n 10 --pretty=format:'- %s' 2>/dev/null || true)"
  fi
  SECTION_BODY=$'### Changes\n'"${COMMITS:-"- Maintenance and stability improvements."}"
fi

APK_BLOCK=""
if [[ -n "${APK_PATH}" && -f "${APK_PATH}" ]]; then
  APK_NAME="$(basename "${APK_PATH}")"
  APK_SHA256="$(sha256sum "${APK_PATH}" | awk '{print $1}')"
  APK_SIZE_BYTES="$(wc -c < "${APK_PATH}" | tr -d ' ')"
  APK_SIZE_MB="$(awk -v b="${APK_SIZE_BYTES}" 'BEGIN { printf "%.2f MB", b / 1048576 }')"
  APK_BLOCK=$(cat <<EOF

---

### Release Artifact
| Field | Value |
| --- | --- |
| **Version** | \`v${VERSION}\` (build \`${VERSION_CODE}\`) |
| **ABI** | \`arm64-v8a\` |
| **APK** | \`${APK_NAME}\` (${APK_SIZE_MB}) |
| **Commit** | \`${COMMIT_SHA}\` |
| **SHA-256** | \`${APK_SHA256}\` |

> Existing Aura Player users can update directly from **Settings → Updates & Changelog**.
EOF
)
fi

WRAPPED_OUTPUT=$(cat <<EOF
## Aura Player v${VERSION}

${SECTION_BODY}
${APK_BLOCK}
EOF
)

if [[ -n "${OUT_PATH}" ]]; then
  mkdir -p "$(dirname "${OUT_PATH}")"
  printf '%s\n' "${WRAPPED_OUTPUT}" > "${OUT_PATH}"
  echo "Generated wrapped release notes -> ${OUT_PATH}"
else
  printf '%s\n' "${WRAPPED_OUTPUT}"
fi
