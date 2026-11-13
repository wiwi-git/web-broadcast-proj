#!/usr/bin/env bash
# 5초마다 HPA가 본 CPU 사용률과 Pod 수를 한 줄씩 찍는다. 부하를 거는 동안 다른 터미널에서 켜 둔다.
#   k8s/watch-scale.sh | tee scale-log.txt
set -euo pipefail
NAMESPACE=webbroadcast

printf '%-8s  %-22s  %-22s\n' "시각" "server (CPU/목표, Pod)" "worker (CPU/목표, Pod)"
while true; do
  line=$(date +%H:%M:%S)
  for name in server worker; do
    cpu=$(kubectl -n "$NAMESPACE" get hpa "$name" \
      -o jsonpath='{.status.currentMetrics[0].resource.current.averageUtilization}' 2>/dev/null || true)
    target=$(kubectl -n "$NAMESPACE" get hpa "$name" \
      -o jsonpath='{.spec.metrics[0].resource.target.averageUtilization}' 2>/dev/null || true)
    replicas=$(kubectl -n "$NAMESPACE" get deploy "$name" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || true)
    line+=$(printf '  %-22s' "${cpu:-?}%/${target}%, ${replicas:-0}개")
  done
  echo "$line"
  sleep 5
done
