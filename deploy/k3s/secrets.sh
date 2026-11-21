#!/usr/bin/env bash
# VM의 /opt/web-broadcast/.env(권한 600, git에 없음)에서 비밀 값을 읽어 K3s Secret을 만든다. VM에서 실행한다.
#   ssh ubuntu@<VM> 'bash -s' < deploy/k3s/secrets.sh
# .env 항목은 deploy/env.example 참고. 다시 실행하면 값을 덮어쓴다(바꾼 뒤 rollout restart 필요).
#
# - db-credentials: 필수. DB를 처음 만들 때의 비밀번호와 같아야 한다
# - toss-keys:      선택. 두 값이 모두 있을 때만 만든다 (없으면 후원만 막힌다)
# - simulcast:      선택. 두 값이 모두 있을 때만 만든다 (없으면 유튜브 동시 송출만 막힌다)
set -euo pipefail

ENV_FILE=${ENV_FILE:-/opt/web-broadcast/.env}
NAMESPACE=webbroadcast

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

apply_secret() {
  local name=$1; shift
  kubectl -n "$NAMESPACE" create secret generic "$name" "$@" --dry-run=client -o yaml | kubectl apply -f -
}

kubectl get namespace "$NAMESPACE" >/dev/null 2>&1 || kubectl create namespace "$NAMESPACE"

apply_secret db-credentials \
  --from-literal=DB_USERNAME=webbroadcast \
  --from-literal=DB_PASSWORD="${DB_PASSWORD:?DB_PASSWORD가 .env에 없습니다}"

if [[ -n "${TOSS_CLIENT_KEY:-}" && -n "${TOSS_SECRET_KEY:-}" ]]; then
  apply_secret toss-keys \
    --from-literal=TOSS_CLIENT_KEY="$TOSS_CLIENT_KEY" --from-literal=TOSS_SECRET_KEY="$TOSS_SECRET_KEY"
fi

if [[ -n "${SIMULCAST_ENCRYPTION_KEY:-}" && -n "${SIMULCAST_RELAY_SECRET:-}" ]]; then
  apply_secret simulcast \
    --from-literal=SIMULCAST_ENCRYPTION_KEY="$SIMULCAST_ENCRYPTION_KEY" \
    --from-literal=SIMULCAST_RELAY_SECRET="$SIMULCAST_RELAY_SECRET"
fi

kubectl -n "$NAMESPACE" get secrets
