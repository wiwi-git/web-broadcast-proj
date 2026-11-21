#!/usr/bin/env bash
# k6를 설치하지 않고 Docker로 부하 테스트를 돌린다.
#   로컬 쿠버네티스: load-test/run.sh   (컨테이너 안에서 호스트의 localhost:8180은 host.docker.internal:8180)
#   OCI 운영(K3s):   BASE_URL=https://live.duse.dev VUS=20 HOLD=3m load-test/run.sh
# 실행할 때마다 회원 하나(load로 시작)와 방송·댓글이 생긴다. 끝나면 load-test/cleanup.sql로 지운다.
set -euo pipefail
cd "$(dirname "$0")"
docker run --rm -i \
  -e BASE_URL="${BASE_URL:-http://host.docker.internal:8180}" \
  -e VUS="${VUS:-30}" -e HOLD="${HOLD:-3m}" \
  -v "$PWD":/scripts:ro \
  grafana/k6:0.54.0 run /scripts/load.js
