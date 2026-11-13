#!/usr/bin/env bash
# k6를 설치하지 않고 Docker로 부하 테스트를 돌린다. 컨테이너 안에서 호스트의 localhost:8080은
# host.docker.internal:8080이다. 다른 주소를 치려면 BASE_URL=http://... load-test/run.sh
set -euo pipefail
cd "$(dirname "$0")"
docker run --rm -i \
  -e BASE_URL="${BASE_URL:-http://host.docker.internal:8080}" \
  -v "$PWD":/scripts:ro \
  grafana/k6:0.54.0 run /scripts/load.js
