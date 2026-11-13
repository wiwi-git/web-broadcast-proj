#!/usr/bin/env bash
# Web Broadcast 서비스를 클러스터에서 지운다. DB와 영상 파일(PVC)도 함께 지워진다.
# metrics-server는 다른 용도로도 쓸 수 있으므로 남겨 둔다.
set -euo pipefail
kubectl delete namespace webbroadcast --ignore-not-found
