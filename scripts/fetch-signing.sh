#!/usr/bin/env bash
# fetch-signing.sh — pull release signing credentials from Shinkofa-Vault.
#
# Vault (VPS) is the source of truth for the Hoso release keystore. This
# script SSHes to the VPS, asks the vault to regenerate the env file from
# encrypted secrets, then writes the credentials locally where Gradle
# can find them at build time.
#
# What it writes:
#   ~/.android-keystores/hoso-release.jks   (binary, decoded from base64)
#   <repo>/local.properties                 (signing.* keys appended)
#
# Both targets are gitignored. The .jks is rebuilt from base64 every run,
# so losing the local file is harmless — Vault re-emits it.
#
# Usage:
#   ./scripts/fetch-signing.sh
#
# Requirements:
#   - SSH alias 'vps' configured (key auth)
#   - Shinkofa-Vault at /home/ubuntu/Shinkofa-Vault on VPS
#   - sops + age set up there (Vault README)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KSTORE_DIR="$HOME/.android-keystores"
KSTORE_PATH="$KSTORE_DIR/hoso-release.jks"
LOCAL_PROPS="$REPO_ROOT/local.properties"

mkdir -p "$KSTORE_DIR"

echo "==> Asking Vault (VPS) to regenerate hoso.env..."
ssh vps 'bash -l -c "~/Shinkofa-Vault/scripts/generate-envs.sh hoso >/dev/null && cat ~/Shinkofa-Vault/envs/hoso.env"' > /tmp/hoso.env

# Source the env file in a subshell so vars don't leak into the parent.
# shellcheck disable=SC1091
set -a; . /tmp/hoso.env; set +a
shred -u /tmp/hoso.env 2>/dev/null || rm -f /tmp/hoso.env

: "${HOSO_KEYSTORE_B64:?missing in vault env}"
: "${HOSO_KEYSTORE_PASSWORD:?missing in vault env}"
: "${HOSO_KEY_ALIAS:?missing in vault env}"
: "${HOSO_KEY_PASSWORD:?missing in vault env}"

echo "==> Restoring keystore to $KSTORE_PATH..."
echo "$HOSO_KEYSTORE_B64" | base64 -d > "$KSTORE_PATH"
chmod 600 "$KSTORE_PATH"

# Strip any pre-existing signing.* lines so reruns stay idempotent.
echo "==> Updating local.properties signing config..."
touch "$LOCAL_PROPS"
grep -v -E '^signing\.' "$LOCAL_PROPS" > "$LOCAL_PROPS.tmp" || true
mv "$LOCAL_PROPS.tmp" "$LOCAL_PROPS"

# Gradle needs a native path; Git Bash /c/... is treated as relative on Windows.
# Convert to Windows form when cygpath is available; escape backslashes for .properties.
if command -v cygpath >/dev/null 2>&1; then
    KSTORE_PROP_PATH="$(cygpath -w "$KSTORE_PATH" | sed 's/\\/\\\\/g')"
else
    KSTORE_PROP_PATH="$KSTORE_PATH"
fi

{
    echo "signing.storeFile=$KSTORE_PROP_PATH"
    echo "signing.storePassword=$HOSO_KEYSTORE_PASSWORD"
    echo "signing.keyAlias=$HOSO_KEY_ALIAS"
    echo "signing.keyPassword=$HOSO_KEY_PASSWORD"
} >> "$LOCAL_PROPS"

echo "==> Done. You can now run: ./gradlew assembleRelease"
