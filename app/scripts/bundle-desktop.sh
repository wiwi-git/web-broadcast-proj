#!/usr/bin/env bash
# 운영 서버(https://live.duse.dev)를 쓰는 데스크톱 배포 패키지를 만든다.
#
#   app/scripts/bundle-desktop.sh            # 지금 OS의 패키지 (macOS → .dmg, Windows → .msi)
#   SERVER=https://example.com app/scripts/bundle-desktop.sh   # 다른 서버로 빌드
#
# 결과: app/dist/WebBroadcast-macos-arm64.dmg 또는 app/dist/WebBroadcast-windows-x64.msi
# Windows에서는 Git Bash로 실행한다 (GitHub Actions의 windows-latest도 같다).
# 필요한 것: Rust(stable), dx 0.7 (`cargo binstall dioxus-cli@0.7.10`)
#
# 서버 주소는 빌드할 때 앱에 들어간다(src/config.rs의 option_env!). 앱은 서버가 준 상대 경로를
# 아래 주소로 바꿔 요청한다. 운영 서버는 Caddy가 한 도메인에서 경로로 나눠 넘긴다(deploy/Caddyfile).
set -euo pipefail

app_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$app_dir"

server="${SERVER:-https://live.duse.dev}"
export API_ORIGIN="$server"          # /api, /media → Spring
export HLS_ORIGIN="$server/hls"      # /hls → MediaMTX HLS
export WHIP_ORIGIN="$server/whip"    # /whip → MediaMTX WHIP
export WEB_ORIGIN="$server"          # 후원 결제는 기본 브라우저의 웹 화면에서 한다

case "$(uname -s)" in
  Darwin)
    os=macos
    # dx가 만든 .app은 실행 파일만 링커가 임시 서명해서 번들 전체가 봉인되지 않는다.
    # 내려받은 Mac이 "손상된 앱"으로 거부하지 않게 .app을 받아 다시 서명하고 DMG는 아래에서 직접 만든다
    package_type=macos
    ;;
  MINGW* | MSYS* | CYGWIN*)
    os=windows
    package_type=msi
    ;;
  *)
    echo "macOS나 Windows(Git Bash)에서 실행하세요: $(uname -s)" >&2
    exit 1
    ;;
esac

case "$(uname -m)" in
  arm64 | aarch64) arch=arm64 ;;
  x86_64 | amd64) arch=x64 ;;
  *) arch="$(uname -m)" ;;
esac

out_dir="$app_dir/target/desktop-bundle"
rm -rf "$out_dir"
# 서버 주소가 바뀌어도 다시 컴파일되도록 이전 빌드 결과의 주소를 무효로 만든다
touch src/config.rs

dx bundle --platform desktop --release --package-types "$package_type" --out-dir "$out_dir"

mkdir -p dist

if [ "$os" = macos ]; then
  built="$(find "$out_dir" -maxdepth 2 -name "*.app" -print -quit)"
  [ -n "$built" ] || { echo "앱을 찾지 못했습니다 ($out_dir/*.app)" >&2; exit 1; }
  # Apple 개발자 인증서가 없으므로 임시(ad-hoc) 서명으로 번들 전체를 봉인한다. 공증은 하지 않는다
  codesign --force --deep --sign - "$built"
  codesign --verify --deep --strict "$built"

  # 앱과 응용 프로그램 폴더 바로가기를 담은 DMG (끌어 놓아 설치)
  staging="$out_dir/dmg"
  mkdir -p "$staging"
  cp -R "$built" "$staging/"
  ln -s /Applications "$staging/Applications"
  target="dist/WebBroadcast-$os-$arch.dmg"
  rm -f "$target"
  hdiutil create -volname "Web Broadcast" -srcfolder "$staging" -fs HFS+ -format UDZO "$target" >/dev/null
else
  built="$(find "$out_dir" -name "*.$package_type" -print -quit)"
  [ -n "$built" ] || { echo "패키지를 찾지 못했습니다 ($out_dir/*.$package_type)" >&2; exit 1; }
  # 다운로드 페이지(deploy/download/index.html)가 가리키는 고정 이름으로 복사한다
  target="dist/WebBroadcast-$os-$arch.$package_type"
  cp "$built" "$target"
fi
echo "만들었습니다: app/$target (서버: $server)"
