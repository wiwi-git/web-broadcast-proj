#!/bin/sh
# 유튜브 동시 송출 중계 (ADR-0007). MediaMTX가 방송 영상이 들어오면(runOnAvailable) 띄우고, 영상이 끊기면 끈다.
#
# 1. 서버 내부 API에 이 경로($MTX_PATH)의 유튜브 주소를 묻는다. 동시 송출을 켜지 않은 방송이면 바로 끝낸다.
# 2. ffmpeg가 MediaMTX 안의 RTSP로 방송을 읽어 영상은 그대로, 소리는 Opus → AAC로 바꿔 유튜브에 보낸다.
# 3. 연결 상태(연결 중/송출 중/실패)를 서버에 알린다. 끊기면 10초 뒤 다시 시도하고, 세 번 다시 해도 안 되면 실패로 끝낸다.
#    한동안(60초) 잘 보내다 끊긴 것은 새로 센다.
#
# 필요한 환경변수: SIMULCAST_SERVER_URL(서버 주소), SIMULCAST_RELAY_SECRET(서버의 simulcast.relay-secret과 같은 값).
# 둘 중 하나라도 없으면 동시 송출을 하지 않는다. (MTX_로 시작하는 이름은 MediaMTX가 자기 설정으로 읽으므로 쓰지 않는다)
# YouTube Key가 든 주소는 로그에 남기지 않는다.

set -u

RETRIES=3
RETRY_WAIT_SECONDS=10
LIVE_AFTER_SECONDS=5
STABLE_SECONDS=60

SERVER="${SIMULCAST_SERVER_URL:-}"
SECRET="${SIMULCAST_RELAY_SECRET:-}"
[ -n "$SERVER" ] && [ -n "$SECRET" ] || exit 0
API="$SERVER/api/internal/simulcast"
SOURCE="rtsp://127.0.0.1:${RTSP_PORT:-8554}/$MTX_PATH"

log() { echo "simulcast $MTX_PATH: $*" >&2; }

# 204(중계 안 함)면 빈 값, 서버에 닿지 않으면 실패
target=$(wget -q -O - --header "X-Relay-Secret: $SECRET" "$API/target?path=$MTX_PATH") || {
  log "서버에서 유튜브 주소를 받지 못함"
  exit 0
}
[ -n "$target" ] || exit 0

report() {
  wget -q -O /dev/null --header "X-Relay-Secret: $SECRET" --post-data "" \
    "$API/status?path=$MTX_PATH&status=$1" || log "상태($1)를 서버에 알리지 못함"
}

ffmpeg_log="/tmp/simulcast-$$.log"
ffmpeg_pid=""
watcher_pid=""

# MediaMTX가 방송이 끝나 이 스크립트를 끌 때 ffmpeg도 함께 끈다
stop() {
  [ -n "$watcher_pid" ] && kill "$watcher_pid" 2>/dev/null
  [ -n "$ffmpeg_pid" ] && kill "$ffmpeg_pid" 2>/dev/null
  wait
  rm -f "$ffmpeg_log"
  exit 0
}
trap stop INT TERM

failures=0
while :; do
  report CONNECTING
  started=$(date +%s)
  ffmpeg -hide_banner -loglevel error -nostdin \
    -rtsp_transport tcp -i "$SOURCE" \
    -map 0:v:0 -map "0:a:0?" -c:v copy -c:a aac -b:a 128k -ar 44100 \
    -f flv "$target" 2>"$ffmpeg_log" &
  ffmpeg_pid=$!

  # ffmpeg는 연결에 성공했다고 알려 주지 않는다. 몇 초 동안 끊기지 않으면 보내고 있는 것으로 본다
  ( sleep "$LIVE_AFTER_SECONDS"; kill -0 "$ffmpeg_pid" 2>/dev/null && report LIVE ) &
  watcher_pid=$!

  wait "$ffmpeg_pid"
  ffmpeg_pid=""
  kill "$watcher_pid" 2>/dev/null
  watcher_pid=""

  # YouTube Key가 든 주소를 가리고 마지막 오류만 남긴다
  sed "s|$target|<youtube>|g" "$ffmpeg_log" | tail -n 3 | while read -r line; do log "ffmpeg: $line"; done

  if [ $(( $(date +%s) - started )) -ge "$STABLE_SECONDS" ]; then
    failures=0
  fi
  failures=$((failures + 1))
  if [ "$failures" -gt "$RETRIES" ]; then
    log "유튜브 연결을 $RETRIES번 다시 시도했지만 실패"
    report FAILED
    rm -f "$ffmpeg_log"
    exit 0
  fi
  log "유튜브 연결이 끊김. ${RETRY_WAIT_SECONDS}초 뒤 다시 시도 ($failures/$RETRIES)"
  sleep "$RETRY_WAIT_SECONDS" &
  wait $!
done
