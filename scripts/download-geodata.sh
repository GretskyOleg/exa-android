#!/usr/bin/env bash
# Download Xray geo databases (geoip.dat / geosite.dat) into app assets.
# The files are also committed, so this is only needed to refresh them.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/app/src/main/assets"
TAG="202611042203"
mkdir -p "$DEST"

fetch() {
  local name="$1" expected="$2"
  local target="$DEST/$name"
  echo "→ $name ($TAG)"
  curl -fL --retry 3 -o "$target" \
    "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/download/${TAG}/${name}"
  local actual
  actual="$(sha256sum "$target" | awk '{print $1}')"
  if [ "$actual" != "$expected" ]; then
    echo "✗ SHA-256 mismatch for $name" >&2
    echo "  expected: $expected" >&2
    echo "  actual:   $actual" >&2
    exit 1
  fi
}

fetch "geoip.dat" "06c1d50d0013af57ffe78c5af93f0dc6aa998890f3ee231393816f9117810c4a"
fetch "geosite.dat" "916fea0cea925cc47f09001dfac652d5e8edb6db69ff13ad68e2b4ac9e8ecc1b"
echo "✓ geo databases verified"
ls -lh "$DEST"/*.dat
