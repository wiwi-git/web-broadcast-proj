#!/usr/bin/env bash
# docker compose로 운영하던 데이터를 K3s로 옮긴다. VM에서 한 번만 실행한다.
#   ssh ubuntu@<VM> 'bash -s' < deploy/k3s/migrate-from-compose.sh
#
# 순서: deploy/k3s/install.sh → secrets.sh → deploy.sh(빈 DB로 한 번 뜬다) → 이 스크립트
#
# 1. compose의 DB 볼륨(web-broadcast_postgres-data)으로 임시 PostgreSQL을 띄워 pg_dump 한다.
# 2. K3s의 server·worker를 0개로 내린다 (HPA는 Pod가 0개면 조절하지 않는다).
# 3. K3s의 DB를 비우고 덤프를 복원한다. 회원·로그인 세션·방송·동영상·댓글·후원이 모두 들어 있다.
# 4. compose의 미디어 볼륨(업로드 원본, HLS)을 K3s의 media-data PVC 폴더로 복사한다.
# 5. server 2개, worker 1개로 다시 띄운다. 이후는 HPA가 조절한다.
# 6. 꺼져 있던 compose 컨테이너를 지운다. compose 볼륨은 되돌릴 때를 위해 남겨 둔다.
set -euo pipefail

NAMESPACE=webbroadcast
PG_VOLUME=web-broadcast_postgres-data
MEDIA_VOLUME=web-broadcast_media-data
DUMP=/tmp/webbroadcast.dump

echo "▶ 1. compose DB 덤프"
docker rm -f pg-export >/dev/null 2>&1 || true
# 데이터가 이미 있으므로 POSTGRES_PASSWORD는 쓰이지 않는다 (이미지가 요구해서 넣을 뿐)
docker run -d --name pg-export -v "$PG_VOLUME":/var/lib/postgresql -e POSTGRES_PASSWORD=unused postgres:18 >/dev/null
until docker exec pg-export pg_isready -U webbroadcast -d webbroadcast >/dev/null 2>&1; do sleep 1; done
docker exec pg-export pg_dump -U webbroadcast -d webbroadcast -Fc > "$DUMP"
docker rm -f pg-export >/dev/null
ls -lh "$DUMP"

echo "▶ 2. K3s server·worker 내리기"
kubectl -n "$NAMESPACE" scale deployment/server deployment/worker --replicas=0
kubectl -n "$NAMESPACE" wait --for=delete pod -l 'app in (server,worker)' --timeout=120s

echo "▶ 3. K3s DB에 복원"
PG_POD=$(kubectl -n "$NAMESPACE" get pod -l app=postgres -o jsonpath='{.items[0].metadata.name}')
kubectl -n "$NAMESPACE" exec "$PG_POD" -- psql -U webbroadcast -d postgres -v ON_ERROR_STOP=1 \
  -c 'DROP DATABASE IF EXISTS webbroadcast WITH (FORCE)' -c 'CREATE DATABASE webbroadcast OWNER webbroadcast'
kubectl -n "$NAMESPACE" exec -i "$PG_POD" -- pg_restore -U webbroadcast -d webbroadcast --no-owner < "$DUMP"

echo "▶ 4. 미디어 파일 복사"
PV=$(kubectl -n "$NAMESPACE" get pvc media-data -o jsonpath='{.spec.volumeName}')
PV_PATH=$(kubectl get pv "$PV" -o jsonpath='{.spec.hostPath.path}{.spec.local.path}')
sudo rsync -a "/var/lib/docker/volumes/$MEDIA_VOLUME/_data/" "$PV_PATH/"
sudo du -sh "$PV_PATH"

echo "▶ 5. 다시 띄우기"
kubectl -n "$NAMESPACE" scale deployment/server --replicas=2
kubectl -n "$NAMESPACE" scale deployment/worker --replicas=1
kubectl -n "$NAMESPACE" rollout status deployment/server --timeout=600s
kubectl -n "$NAMESPACE" rollout status deployment/worker --timeout=600s

echo "▶ 6. compose 컨테이너 지우기 (볼륨은 남김)"
docker ps -a --filter label=com.docker.compose.project=web-broadcast -q | xargs -r docker rm -f
rm -f "$DUMP"

kubectl -n "$NAMESPACE" exec "$PG_POD" -- psql -U webbroadcast -d webbroadcast -c \
  'SELECT (SELECT count(*) FROM app_user) AS users, (SELECT count(*) FROM video) AS videos, (SELECT count(*) FROM broadcast) AS broadcasts'
