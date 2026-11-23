#!/bin/sh
# 영상 촬영용 데모 방송을 띄운다.
# 별도 프로필의 Chrome 창을 가짜 카메라(demo-media/cam.mjpeg, mic.wav)로 열고 https://live.duse.dev/broadcast 로 간다.
# 로그인과 "방송 시작"은 사람이 직접 한다(비밀번호는 스크립트가 다루지 않는다).
#
#   sh presentation/oci-deploy/tools/demo-broadcast.sh
#
# 끝낼 때는 방송 화면에서 "방송 종료"를 누르고 창을 닫는다.
set -e
HERE=$(cd "$(dirname "$0")/.." && pwd)
MEDIA="$HERE/demo-media"
PROFILE="$HERE/demo-media/chrome-profile"
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

for f in cam.mjpeg mic.wav; do
  [ -f "$MEDIA/$f" ] || { echo "없음: $MEDIA/$f"; exit 1; }
done
[ -x "$CHROME" ] || { echo "Google Chrome이 필요합니다: $CHROME"; exit 1; }

# --real: 가짜 카메라 없이 같은 프로필로 연다(진짜 카메라로 비교할 때)
if [ "$1" = "--real" ]; then
  "$CHROME" --user-data-dir="$PROFILE" --no-first-run --no-default-browser-check \
    --window-size=1280,900 https://live.duse.dev/broadcast >/dev/null 2>&1 &
  echo "진짜 카메라를 쓰는 Chrome 창을 열었어요. 카메라 권한을 허용해 주세요."
  exit 0
fi

"$CHROME" \
  --user-data-dir="$PROFILE" \
  --no-first-run --no-default-browser-check \
  --use-fake-ui-for-media-stream \
  --use-fake-device-for-media-stream \
  --use-file-for-fake-video-capture="$MEDIA/cam.mjpeg" \
  --use-file-for-fake-audio-capture="$MEDIA/mic.wav" \
  --window-size=1280,900 \
  https://live.duse.dev/broadcast >/dev/null 2>&1 &
echo "데모 방송용 Chrome 창을 열었어요. 데모 방송자 계정으로 로그인한 뒤 방송을 시작해 주세요."
