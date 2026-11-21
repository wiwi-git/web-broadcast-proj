#!/usr/bin/env bash
# 맥(또는 PC)에서 실행해 OCI VM의 K3s에 Web Broadcast를 배포한다. 코드를 바꿀 때마다 다시 실행한다.
#   deploy/k3s/deploy.sh
#
# 1. 서버 이미지를 linux/arm64로 빌드한다 (VM이 Ampere A1이라 arm64). Apple Silicon 맥은 그대로 arm64이고,
#    x86 PC는 Docker buildx가 QEMU로 arm64를 빌드한다(느리다). 서버와 변환 워커가 같은 이미지를 쓴다.
# 2. 레지스트리 없이 이미지를 ssh로 보내 K3s의 containerd에 넣는다 (K3s는 Docker와 이미지 저장소가 따로다).
#    Deployment는 imagePullPolicy: Never로 이 이미지를 쓴다.
# 3. MediaMTX 설정 ConfigMap과 k8s/overlays/oci를 맥에서 렌더링해 VM의 kubectl로 적용한다.
# 4. 같은 태그로 이미지를 바꿨으므로 server·worker를 다시 띄우고 준비될 때까지 기다린다.
#    MediaMTX는 진행 중인 방송이 끊기므로 다시 띄우지 않는다. mediamtx.yml을 바꿨으면 직접
#    kubectl -n webbroadcast rollout restart deployment/mediamtx
#
# 먼저 VM에 deploy/k3s/install.sh(K3s 설치)와 deploy/k3s/secrets.sh(비밀 값)를 실행해 둔다.
set -euo pipefail
cd "$(dirname "$0")/../.."

OCI_HOST=${OCI_HOST:-ubuntu@161.33.218.209}
OCI_KEY=${OCI_KEY:-$HOME/.ssh/oci_duse}
NAMESPACE=webbroadcast
IMAGE=web-broadcast-server:local

remote() { ssh -i "$OCI_KEY" "$OCI_HOST" "$@"; }

if [[ "${SKIP_BUILD:-}" != 1 ]]; then
  echo "▶ 이미지 빌드 (linux/arm64): $IMAGE"
  docker build --platform linux/arm64 -t "$IMAGE" server

  echo "▶ 이미지 보내기 → K3s containerd"
  docker save "$IMAGE" | gzip | remote 'gunzip | sudo k3s ctr images import -'
fi

echo "▶ 매니페스트 적용 (k8s/overlays/oci)"
{
  kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml
  echo ---
  kubectl -n "$NAMESPACE" create configmap mediamtx-config \
    --from-file=mediamtx/mediamtx.yml --from-file=mediamtx/simulcast.sh --dry-run=client -o yaml
  echo ---
  kubectl kustomize k8s/overlays/oci
} | remote 'kubectl apply -f -'

echo "▶ 다시 띄우고 준비 대기"
remote "kubectl -n $NAMESPACE rollout restart deployment/server deployment/worker \
  && kubectl -n $NAMESPACE rollout status deployment/postgres --timeout=180s \
  && kubectl -n $NAMESPACE rollout status deployment/mediamtx --timeout=180s \
  && kubectl -n $NAMESPACE rollout status deployment/server --timeout=600s \
  && kubectl -n $NAMESPACE rollout status deployment/worker --timeout=600s \
  && kubectl -n $NAMESPACE get hpa,deploy,svc,pvc"

echo
echo "확인: curl -s https://live.duse.dev/actuator/health"
