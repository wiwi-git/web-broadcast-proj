#!/usr/bin/env bash
# 연결한 아이폰에 앱을 빌드·서명해 설치하고 실행한다 (macOS, Xcode 필요).
#
#   app/scripts/ios-device.sh
#
# 먼저 Xcode에서 Bundle Identifier `com.webbroadcast.app`으로 빈 앱을 아이폰에 한 번 실행해
# 프로비저닝 프로파일을 만들어 둔다. dx가 그 프로파일을 찾아 서명한다.
#
# 환경 변수 (모두 생략 가능):
#   LAN_IP    아이폰이 접속할 Mac 주소. 기본은 Wi-Fi(en0) 주소
#   DEVICE    설치할 기기 이름. 기본 "iPhone"
#   IDENTITY  서명 인증서 이름. 기본은 키체인의 첫 "Apple Development" 인증서
#
# dx(0.7)는 iOS 빌드에 아이콘을 넣지 않아서, 빌드한 앱에 icons/ios/*.png를 넣고 다시 서명한다.
# 아이콘 파일 이름은 Dioxus.toml의 [ios.plist] CFBundleIcons와 맞아야 한다.
set -euo pipefail

app_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$app_dir"

lan_ip="${LAN_IP:-$(ipconfig getifaddr en0)}"
device="${DEVICE:-iPhone}"
identity="${IDENTITY:-$(security find-identity -v -p codesigning | sed -n 's/.*"\(Apple Development[^"]*\)".*/\1/p' | head -1)}"
[ -n "$identity" ] || { echo "Apple Development 서명 인증서가 없습니다." >&2; exit 1; }

host="http://$lan_ip"
echo "서버 주소: $host · 기기: $device · 서명: $identity"

API_ORIGIN="$host:8180" HLS_ORIGIN="$host:8888" WHIP_ORIGIN="$host:8889" WEB_ORIGIN="$host:5173" \
  dx bundle --platform ios --device "$device" --codesign true --apple-team-id "$identity"

bundle="target/dx/web-broadcast-app/debug/ios/WebBroadcastApp.app"
[ -d "$bundle" ] || { echo "빌드한 앱이 없습니다: $bundle" >&2; exit 1; }

# 아이콘을 넣으면 서명이 깨지므로, dx가 넣은 권한(entitlements)을 그대로 꺼내 다시 서명한다
entitlements="$(mktemp)"
trap 'rm -f "$entitlements"' EXIT
codesign -d --entitlements - --xml "$bundle" > "$entitlements" 2>/dev/null
cp icons/ios/AppIcon*.png "$bundle/"
codesign --force --sign "$identity" --entitlements "$entitlements" --generate-entitlement-der "$bundle"

devices_json="$(mktemp)"
trap 'rm -f "$entitlements" "$devices_json"' EXIT
xcrun devicectl list devices --json-output "$devices_json" >/dev/null 2>&1
device_id="$(python3 - "$devices_json" "$device" <<'EOF'
import json, sys
devices = json.load(open(sys.argv[1]))["result"]["devices"]
for d in devices:
    props, hw = d.get("deviceProperties", {}), d.get("hardwareProperties", {})
    if hw.get("platform") == "iOS" and sys.argv[2].lower() in (props.get("name", "") + " " + hw.get("marketingName", "")).lower():
        print(d["identifier"])
        break
EOF
)"
[ -n "$device_id" ] || { echo "연결된 기기를 찾지 못했습니다: $device" >&2; exit 1; }

xcrun devicectl device install app --device "$device_id" "$bundle"
xcrun devicectl device process launch --device "$device_id" com.webbroadcast.app
