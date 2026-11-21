#!/usr/bin/env bash
# OCI VM 한 대(Ubuntu 24.04, arm64)에 단일 노드 K3s를 설치한다. VM에서 한 번 실행한다.
#   scp deploy/k3s/install.sh ubuntu@<VM>:/tmp/ && ssh ubuntu@<VM> 'bash /tmp/install.sh'
#
# 1. OCI Ubuntu 이미지의 iptables는 INPUT·FORWARD 끝에 REJECT가 있어서 Pod 네트워크(10.42.0.0/16)를 막는다.
#    Pod에서 나가고 들어오는 트래픽을 그 앞에서 허용하고, /etc/iptables/rules.v4에도 넣어 재부팅 뒤에도 남긴다.
# 2. K3s를 설치한다. HTTPS는 호스트의 Caddy가 맡으므로 내장 Traefik은 끈다(80/443을 두고 다투지 않게).
#    Metrics Server, ServiceLB(LoadBalancer), local-path(PVC)는 K3s 기본 구성요소를 그대로 쓴다.
# 3. ubuntu 사용자가 sudo 없이 kubectl을 쓰게 kubeconfig를 복사한다.
# 다시 실행해도 된다.
set -euo pipefail

POD_CIDR=10.42.0.0/16
RULES_FILE=/etc/iptables/rules.v4

echo "▶ iptables: Pod 네트워크 허용"
add_rule() {
  local chain=$1; shift
  sudo iptables -C "$chain" "$@" 2>/dev/null || sudo iptables -I "$chain" 1 "$@"
  local line="-A $chain $*"
  if ! sudo grep -qxF -- "$line" "$RULES_FILE"; then
    # 같은 체인의 REJECT 줄 바로 앞에 넣는다
    sudo sed -i "/^-A $chain -j REJECT/i $line" "$RULES_FILE"
  fi
}
add_rule INPUT -s "$POD_CIDR" -j ACCEPT
add_rule FORWARD -s "$POD_CIDR" -j ACCEPT
add_rule FORWARD -d "$POD_CIDR" -j ACCEPT

echo "▶ K3s 설치 (stable 채널, Traefik 끔)"
if ! command -v k3s >/dev/null; then
  curl -sfL https://get.k3s.io | INSTALL_K3S_CHANNEL=stable \
    INSTALL_K3S_EXEC="server --disable traefik --write-kubeconfig-mode 600" sh -
fi

echo "▶ kubeconfig"
mkdir -p "$HOME/.kube"
sudo cp /etc/rancher/k3s/k3s.yaml "$HOME/.kube/config"
sudo chown "$(id -u):$(id -g)" "$HOME/.kube/config"
chmod 600 "$HOME/.kube/config"
# K3s의 kubectl은 KUBECONFIG가 없으면 root 전용 /etc/rancher/k3s/k3s.yaml을 읽으려 한다.
# ssh로 바로 실행하는 명령에도 적용되게 /etc/environment(PAM)에 넣는다
if ! grep -q '^KUBECONFIG=' /etc/environment; then
  echo "KUBECONFIG=$HOME/.kube/config" | sudo tee -a /etc/environment >/dev/null
fi
export KUBECONFIG=$HOME/.kube/config

echo "▶ 노드 준비 대기"
kubectl wait --for=condition=Ready node --all --timeout=180s
kubectl -n kube-system rollout status deployment/metrics-server --timeout=180s

kubectl get nodes -o wide
k3s --version | head -1
