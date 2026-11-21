# OCI 단일 서버 오토스케일 (K3s·HPA)

Status: done

과제: 치지직 만들기 ⑦ (Codemit, classes/6/problems/76). 로컬 오토스케일(⑤)을 OCI 운영(⑥)에 적용한다.
채점은 소스·설정·README의 정적 평가이며, 체크리스트 7개 항목을 모두 충족해야 한다.

## 배경 (2026-11-21 확인)

- OCI VM은 `VM.Standard.A1.Flex` **2 OCPU / 12GB** (arm64, Neoverse-N1)이다. README에는 1 OCPU·6GB로 남아 있다.
- live.duse.dev는 재부팅 뒤 compose 컨테이너가 올라오지 않아 꺼져 있다(502). 데이터 볼륨
  `web-broadcast_postgres-data`, `web-broadcast_media-data`는 남아 있다.
- 같은 VM에서 `short-url`(compose, s.duse.dev, 127.0.0.1:8190)이 돌고 있다. 이번 작업에서 건드리지 않는다.

## 결정 사항

| # | 결정 |
| --- | --- |
| D1 | web-broadcast 운영을 compose에서 K3s로 **완전히 옮긴다**. Docker는 short-url용으로 함께 남는다 |
| D2 | HTTPS는 호스트 **Caddy가 계속 맡는다**. K3s는 `--disable traefik`으로 설치한다. Caddyfile은 바꾸지 않는다 |
| D3 | 이미지는 맥(arm64)에서 `docker build` → `docker save \| ssh … sudo k3s ctr images import -`. 레지스트리 없음, `imagePullPolicy: Never` 유지. x86 PC는 `docker buildx --platform linux/arm64` |
| D4 | 매니페스트를 kustomize **base + overlays(local, oci)**로 나눈다. 로컬(⑤) 동작은 그대로 유지한다 |
| D5 | 기존 운영 데이터를 옮긴다: DB는 `pg_dump`/`pg_restore`, 미디어 볼륨은 K3s PVC 경로로 복사 |
| D6 | 실제로 OCI에 배포하고 부하를 걸어 README에 진짜 기록을 남긴다. VM을 바꾸는 단계는 실행 직전 사용자에게 확인받는다 |
| D7 | `duse-status-json.py`가 서비스별로 compose 또는 k8s 상태를 읽게 고친다(`services.json`에 `kind`) |
| D8 | HPA 기준은 2 OCPU·12GB. server **최소 2 / 최대 4**, CPU 목표 60%, server CPU 요청 250m |
| D9 | OCI에서는 워커 HPA를 빼고 worker replicas **1 고정**. 로컬은 1~2 유지 |
| D10 | OCI의 축소 안정화 시간은 **기본값 5분**(behavior.scaleDown 생략 또는 300s). 로컬은 60초 유지 |
| D11 | 포트: server 8180, MediaMTX 8888·8889는 ServiceLB(`LoadBalancer`), WebRTC 8189 TCP·UDP는 MediaMTX `hostPort`. 외부 노출은 OCI 보안 목록이 막는다 |
| D12 | 부하: 기존 `load-test/load.js`를 `BASE_URL=https://live.duse.dev`, 가상 사용자 20명·유지 3분으로 맥에서 실행. `VUS`, `HOLD` 환경변수로 조절. 측정 뒤 테스트 회원 삭제 SQL 제공·실행 |
| D13 | `compose.prod.yaml` 삭제, README "운영 서버 배포"를 "OCI 운영 (K3s)"로 교체. `compose.yaml`(로컬 개발)은 유지 |
| D14 | 브랜치 `oci-k3s` → main PR → 머지 뒤 GitHub 저장소 주소 제출. 커밋·푸시·머지·제출은 각 단계에서 확인 |

## 최대 파드 수 산정 (README에 옮길 근거)

```
할당 가능 CPU 2000m (2 OCPU)
 - K3s 시스템 (coredns, metrics-server, local-path, svclb)   ≈ 200m
 - postgres 100m, mediamtx 100m (OCI 오버레이에서 요청량 지정)
 - worker 500m × 1
 = 1100m 남음 → server 250m × 4 = 1000m  ⇒ maxReplicas 4
메모리 12GB: server 4 × 768Mi + worker 2Gi + postgres 512Mi + mediamtx 256Mi + K3s ≈ 1GB ≈ 6.8GB
 (short-url, Caddy, OS 여유 포함해도 12GB 안)
```

최종 값은 설치 뒤 `kubectl describe node`의 Allocatable과 실제 요청량 합으로 다시 확인해서 README에 적는다.

## 구현 범위

### 1. 매니페스트 (`k8s/`)
- `k8s/base/`: 지금 `k8s/app`의 공통 리소스(namespace, config, postgres, media-storage, mediamtx, server, worker, server HPA).
- `k8s/overlays/local/`: 워커 HPA(1~2), server HPA 축소 60초. `k8s/up.sh`가 이 오버레이를 적용한다.
- `k8s/overlays/oci/`: worker replicas 1, server HPA 2~4·축소 기본값, postgres·mediamtx CPU 요청량, MediaMTX hostPort 8189와
  `MTX_WEBRTCADDITIONALHOSTS`(공인 IP), MediaMTX HLS/WHIP Service를 LoadBalancer로.
- metrics-server: 로컬만 `k8s/metrics-server` 설치. K3s는 내장 Metrics Server를 쓴다.
- 검증: `kubectl kustomize k8s/overlays/{local,oci}`가 오류 없이 나오고, HPA의 `scaleTargetRef`가 `server` Deployment를,
  Service selector가 같은 라벨을 가리킨다.

### 2. 배포 스크립트
- `deploy/k3s/install.sh` (VM에서): iptables `FORWARD REJECT` 처리, K3s 설치(`--disable traefik`, kubeconfig 권한).
- `deploy/k3s/deploy.sh` (맥에서): 이미지 빌드 → VM import → MediaMTX ConfigMap 생성 → `kubectl apply -k k8s/overlays/oci` → rollout 대기.
- 비밀값(DB 비밀번호, toss, simulcast)은 VM에서 `kubectl create secret`으로 만들고 저장소에는 넣지 않는다.
- `deploy/k3s/migrate-from-compose.sh` 또는 README 절차: compose DB dump → K3s postgres restore, 미디어 파일 복사, compose 컨테이너 삭제.

### 3. 데이터 유지 (체크리스트 5)
- 세션: Spring Session JDBC → PostgreSQL. 데이터(Broadcast·Video·Comment·Donation): PostgreSQL.
- PostgreSQL 데이터와 업로드·HLS 파일: K3s `local-path` PVC (호스트 디스크). Pod가 바뀌어도 남는다.
- README에 "파드 교체 시 유지되는 것" 표를 둔다.

### 4. 부하 (체크리스트 6)
- `load-test/load.js`: `VUS`, `HOLD` 환경변수.
- `load-test/run.sh`: `BASE_URL` 예시에 공개 주소 추가.
- 측정 기록: `load-test/results/oci-*.txt` (k6 결과, `kubectl get hpa,pods -w` 기록, `kubectl top pods`).

### 5. 상태 카드
- `deploy/duse-dev/duse-status-json.py`: `kind: "k8s"`이면 `kubectl -n <ns> get deploy -o json`으로 준비된 Deployment 수를 센다.
  `kind`가 없거나 `compose`이면 지금 방식.
- `deploy/duse-dev/services.json`: live 항목을 k8s로, short-url 항목 추가(서버 파일과 맞춤).

### 6. README (체크리스트 2, 3, 7)
- "OCI VM" 표를 2 OCPU·12GB로 수정하고 비용(여전히 무료 한도 안) 갱신.
- 새 섹션 "OCI 운영 (K3s)": 구조도, K3s 설치, arm64 이미지 빌드·import, 배포, 비밀값, 데이터 이전, Caddy 연결,
  최대 파드 수 산정 근거, 단일 VM 장애 영향, 확인 절차(`get nodes`, `top pods`, `get hpa -w`, `get pods -w`), 부하 조건과 전·중·후 결과.
- 단일 VM 장애 영향: VM이 죽으면 모든 Pod·DB·MediaMTX가 함께 멈추고, HPA는 같은 VM 안에서만 늘리므로 VM 장애를 막지 못한다.
  디스크가 살아 있으면 재부팅 뒤 K3s가 자동으로 다시 띄운다. 부트 볼륨이 손상되면 데이터가 사라진다(백업 없음).
- "로컬 오토스케일" 섹션의 경로를 `k8s/overlays/local`로 고친다.

## 직접 확인 (실행 순서)
1. 매니페스트 렌더링·dry-run 통과
2. (확인 후) VM: compose 데이터 dump → compose 내리기 → K3s 설치 → 배포 → 데이터 복원
3. `kubectl get nodes` 1대, `kubectl top pods` 지표 확인
4. https://live.duse.dev 방송 목록·기존 데이터 확인, 데스크톱 앱 주소 그대로 동작
5. 부하 전·중·후 HPA·Pod 기록, 부하 끝나고 5분 뒤 2개로 복귀 확인
6. 테스트 회원 삭제, 상태 카드 확인
7. README 기록, 커밋·PR (확인 후)

## 범위 밖
- short-url을 K3s로 옮기기
- 여러 노드, 오브젝트 스토리지, DB 백업 자동화
