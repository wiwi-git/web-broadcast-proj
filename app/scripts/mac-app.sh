#!/usr/bin/env bash
# 맥 앱을 개발 빌드로 만들어 아이콘을 넣고 실행한다 (macOS).
#
#   app/scripts/mac-app.sh
#
# 개발 빌드(dx build)는 Info.plist에 CFBundleIconFile(icon.icns)만 적고 파일은 넣지 않아 Dock에 기본 아이콘이 뜬다.
# 그래서 icons/macos.icns를 넣고 다시 서명한다. 배포 패키지(dx bundle --platform macos)는 dx가 아이콘을 넣는다.
set -euo pipefail

app_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$app_dir"

dx build --platform desktop

bundle="target/dx/web-broadcast-app/debug/macos/WebBroadcastApp.app"
[ -d "$bundle" ] || { echo "빌드한 앱이 없습니다: $bundle" >&2; exit 1; }

icon_file="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIconFile' "$bundle/Contents/Info.plist" 2>/dev/null || echo icon.icns)"
cp icons/macos.icns "$bundle/Contents/Resources/$icon_file"
# 파일을 넣으면 서명이 깨지므로 이 Mac에서만 쓰는 서명(ad-hoc)으로 다시 서명한다
codesign --force --deep --sign - "$bundle"
# Finder·Dock이 예전 아이콘을 붙잡고 있지 않게 한다
touch "$bundle"

# 이미 켜져 있으면 끄고 새로 연다
pkill -f "$bundle/Contents/MacOS" 2>/dev/null || true
# 끄는 중인 앱이 남아 있으면 open이 실패(-600)하므로 다 꺼질 때까지 기다린다
for _ in $(seq 1 20); do
  pgrep -f "$bundle/Contents/MacOS" >/dev/null || break
  sleep 0.25
done
open "$bundle"
