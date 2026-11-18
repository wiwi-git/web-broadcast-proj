#!/usr/bin/env bash
# 웹 파비콘(web/public/favicon.svg)으로 앱 아이콘을 만든다. macOS에서 실행한다 (qlmanage, ImageMagick 필요).
#
#   app/icons/make-icons.sh
#
# 만드는 파일 (app/icons/):
#   icon.svg                     파비콘을 흰 배경 정사각형 가운데에 둔 원본
#   icon-1024.png                투명 없는 정사각형 (iOS 원본)
#   ios/AppIcon*.png             iOS 홈 화면 아이콘. dx가 iOS 빌드에 아이콘을 넣지 않아 scripts/ios-device.sh가 넣는다
#   macos-1024.png               macOS용. macOS는 모서리를 깎아 주지 않으므로 둥근 사각형으로 만든다
#   icon.ico                     Windows용
set -euo pipefail

dir="$(cd "$(dirname "$0")" && pwd)"
favicon="$dir/../../web/public/favicon.svg"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# 파비콘(48x46)을 80x80 안 가운데에 두고 흰 배경을 깐다
python3 - "$favicon" "$dir/icon.svg" <<'EOF'
import re, sys
s = open(sys.argv[1]).read()
s = re.sub(r'<svg[^>]*>',
           '<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" fill="none" viewBox="-16 -17 80 80">'
           '<rect x="-16" y="-17" width="80" height="80" fill="#ffffff"/>', s, count=1)
open(sys.argv[2], 'w').write(s)
EOF

# ImageMagick의 SVG 렌더러는 필터·마스크를 제대로 그리지 못해 WebKit(Quick Look)으로 그린다
qlmanage -t -s 1024 -o "$work" "$dir/icon.svg" >/dev/null 2>&1
magick "$work/icon.svg.png" -alpha off "$dir/icon-1024.png"

mkdir -p "$dir/ios"
for spec in 60x60@2x:120 60x60@3x:180 76x76@2x:152 83.5x83.5@2x:167; do
  name="${spec%%:*}" size="${spec##*:}"
  magick "$dir/icon-1024.png" -resize "${size}x${size}" -alpha off "$dir/ios/AppIcon${name}.png"
done

# macOS 아이콘 격자: 1024 캔버스에 824 둥근 사각형(반지름 185)
magick "$dir/icon-1024.png" -resize 824x824 \
  \( -size 824x824 xc:black -fill white -draw "roundrectangle 0,0 823,823 185,185" \) \
  -alpha off -compose CopyOpacity -composite \
  -compose over -background none -gravity center -extent 1024x1024 "$dir/macos-1024.png"

magick "$dir/icon-1024.png" -define icon:auto-resize=256,128,64,48,32,16 "$dir/icon.ico"

echo "아이콘을 만들었습니다: $dir"
