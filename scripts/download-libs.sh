#!/usr/bin/env bash
# Download the prebuilt native cores used by the EXA Android app.
# Versions are pinned and every archive is verified against a known SHA-256.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LIBS="$ROOT/app/libs"
mkdir -p "$LIBS"

LIBV2RAY_TAG="v26.10.31"
HEV_TAG="2.19.3"
LIBV2RAY_SHA256="2efdf731b1ed624eb1cb0d5a4d0aa95c0672e89f9702226d1ba2dc90308630b6"
HEV_SHA256="ab6c581e6157680cfe5687ee7bbdcf95405c006c2db3701879ec44b9e5f83836"

verify() {
  local file="$1" expected="$2"
  if [ ! -f "$file" ]; then
    return 1
  fi
  local actual
  actual="$(sha256sum "$file" | awk '{print $1}')"
  if [ "$actual" != "$expected" ]; then
    echo "✗ SHA-256 mismatch for $file" >&2
    echo "  expected: $expected" >&2
    echo "  actual:   $actual" >&2
    echo "  Delete the file and re-run this script." >&2
    return 1
  fi
  return 0
}

if ! verify "$LIBS/libv2ray.aar" "$LIBV2RAY_SHA256"; then
  echo "→ libv2ray.aar ($LIBV2RAY_TAG)"
  rm -f "$LIBS/libv2ray.aar"
  curl -fL --retry 3 -o "$LIBS/libv2ray.aar" \
    "https://github.com/2dust/AndroidLibXrayLite/releases/download/${LIBV2RAY_TAG}/libv2ray.aar"
  verify "$LIBS/libv2ray.aar" "$LIBV2RAY_SHA256" || exit 1
fi

if ! verify "$LIBS/hev-socks5-tunnel.aar" "$HEV_SHA256"; then
  echo "→ hev-socks5-tunnel.aar ($HEV_TAG)"
  rm -f "$LIBS/hev-socks5-tunnel.aar"
  curl -fL --retry 3 -o "$LIBS/hev-socks5-tunnel.aar" \
    "https://github.com/heiher/hev-socks5-tunnel/releases/download/${HEV_TAG}/hev-socks5-tunnel.aar"
  verify "$LIBS/hev-socks5-tunnel.aar" "$HEV_SHA256" || exit 1
fi

echo "✓ native libraries verified"
ls -lh "$LIBS"/*.aar
