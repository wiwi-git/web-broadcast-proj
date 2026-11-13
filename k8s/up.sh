#!/usr/bin/env bash
# 로컬 쿠버네티스(Docker Desktop Kubernetes)에 Web Broadcast 서비스를 띄운다.
#   1. 서버 이미지를 빌드한다 (서버와 워커가 같은 이미지를 쓴다)
#   2. metrics-server를 설치한다 (HPA가 CPU 사용률을 읽는 데 필요)
#   3. MediaMTX 설정을 ConfigMap으로 올리고 앱을 적용한다
# 다시 실행해도 된다. 코드를 바꾼 뒤 실행하면 새 이미지로 Pod를 다시 띄운다.
set -euo pipefail
cd "$(dirname "$0")/.."

NAMESPACE=webbroadcast
IMAGE=web-broadcast-server:local

context=$(kubectl config current-context 2>/dev/null || true)
if [[ "$context" != "docker-desktop" ]]; then
  echo "kubectl 컨텍스트가 docker-desktop이 아닙니다 (현재: ${context:-없음})."
  echo "Docker Desktop 설정에서 Kubernetes를 켠 뒤 'kubectl config use-context docker-desktop'을 실행하세요."
  exit 1
fi

echo "▶ 서버 이미지 빌드: $IMAGE"
docker build -t "$IMAGE" server

echo "▶ metrics-server 설치"
kubectl apply -k k8s/metrics-server

echo "▶ 앱 적용"
kubectl apply -f k8s/app/namespace.yaml
kubectl -n "$NAMESPACE" create configmap mediamtx-config --from-file=mediamtx/mediamtx.yml \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -k k8s/app

# 휴대폰에서 송출하려면 .env의 LAN_IP를 WebRTC 후보로 알린다 (compose와 같은 방식)
if [[ -f .env ]] && grep -q '^LAN_IP=.\+' .env; then
  lan_ip=$(grep '^LAN_IP=' .env | cut -d= -f2)
  kubectl -n "$NAMESPACE" set env deployment/mediamtx MTX_WEBRTCADDITIONALHOSTS="$lan_ip"
fi

# 같은 태그로 다시 빌드했으면 Pod가 새 이미지를 쓰도록 다시 띄운다
kubectl -n "$NAMESPACE" rollout restart deployment/server deployment/worker

echo "▶ 준비될 때까지 기다리는 중"
kubectl -n "$NAMESPACE" rollout status deployment/postgres --timeout=180s
kubectl -n "$NAMESPACE" rollout status deployment/server --timeout=300s
kubectl -n "$NAMESPACE" rollout status deployment/worker --timeout=300s
kubectl -n kube-system rollout status deployment/metrics-server --timeout=180s

kubectl -n "$NAMESPACE" get hpa,deploy,svc
echo
echo "API: http://localhost:8080   (웹: cd web && npm run dev → http://localhost:5173)"
echo "스케일 관찰: k8s/watch-scale.sh   부하: load-test/run.sh"
