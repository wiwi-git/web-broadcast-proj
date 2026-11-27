# Web Broadcast

웹 방송 플랫폼. 휴대폰 카메라로 라이브 방송을 하고 시청하는 기능과, 녹화된 동영상을 올리고 시청하는 기능이 있다. 회원은 방송과 동영상에 댓글을 남기고, 방송 중인 방송자에게 후원할 수 있다(토스페이먼츠 테스트 결제).
용어는 [GLOSSARY.md](GLOSSARY.md), 주요 결정은 [docs/adr/](docs/adr/)에 있다.

```
server/     Spring Boot 4 (Java 25) API 서버, 그리고 같은 코드로 띄우는 ffmpeg HLS 변환 워커
web/        React + Vite + TypeScript
app/        Rust(Dioxus) 클라이언트. Windows·macOS 데스크톱 앱 (모바일·웹도 같은 코드)
deploy/     운영 서버(OCI) 설정: Caddy, 다운로드 페이지, duse.dev 첫 화면, 송신 상한
mediamtx/   라이브 미디어 서버(MediaMTX) 설정
k8s/        로컬 쿠버네티스 배포와 오토스케일(HPA) 설정
load-test/  k6 부하 테스트
```

## 공개 범위와 권한

시청은 누구나 할 수 있다. 무언가를 만들거나 바꾸려면 로그인해야 하고, 만든 사람만 고치거나 지울 수 있다. 모든 권한은 서버가 검사하며, 웹 화면의 버튼 숨김은 편의일 뿐이다.

| 누가 | 할 수 있는 일 |
| --- | --- |
| 누구나 (로그인 없이) | 라이브 목록·동영상 목록 보기, 라이브와 동영상 시청, 댓글 목록 보기, 방송에 완료된 후원 보기 (후원자 닉네임·금액·메시지) |
| 로그인한 회원 | 위의 모든 것, 동영상 업로드, 방송 시작, 댓글 쓰기, 방송 중인 남의 방송에 후원, 자기 후원 내역(보낸 후원·받은 후원) 보기, 남의 방송·동영상·댓글 신고(대상마다 한 번) |
| 콘텐츠 소유자 (올린 사람·방송한 사람) | 동영상 제목·설명 수정과 삭제, 방송 제목 수정·종료·삭제, 방송 키 재발급 |
| 댓글 작성자 | 자기 댓글 수정·삭제. 콘텐츠 소유자라도 남의 댓글은 지울 수 없다 |
| 후원자 | 자기 후원의 결제 승인·취소 처리. 완료된 후원은 되돌릴 수 없다 |
| 운영자 (`role`이 `ADMIN`인 회원) | 처리 전 신고 보기(웹 `/admin/reports`), 신고된 방송·동영상·댓글 내리기(숨김)와 기각, 회원 목록·검색과 정지·해제(웹 `/admin/users`). 운영자는 정지할 수 없다 |

- 한 회원이 동시에 진행할 수 있는 방송은 하나다.
- 처리 중이거나 처리에 실패한 동영상은 목록에 나오지 않지만, 주소를 알면 상세 정보는 볼 수 있다. 준비 상태의 방송도 목록에는 나오지 않는다.
- 회원의 아이디는 다른 사람에게 보이지 않고 닉네임만 보인다.
- 운영자가 내린 방송·동영상·댓글은 지우지 않고 숨긴다. 소유자를 포함해 누구에게도 보이지 않으며(404), 방송은 진행 중이면 끝난다. 동영상의 HLS 파일은 주소를 아는 사람에게는 계속 열린다.
- 정지된 회원은 로그인할 수 없고, 정지하는 순간 로그인된 세션이 모두 끊기며 진행 중인 방송이 끝난다. 이미 올린 콘텐츠는 그대로 남는다.
- 운영자는 DB에서 지정한다: `update app_user set role = 'ADMIN' where username = '아이디';`. 역할은 로그인할 때 세션에 담기므로 지정한 뒤 다시 로그인해야 한다.
- 결제 대기·실패·취소·만료된 후원은 후원자 본인만 후원 내역에서 볼 수 있다. 공개되는 것은 완료된 후원뿐이다.
- 로그인은 서버 세션 쿠키로 유지되며, 30분 동안 요청이 없으면 만료된다. 상태를 바꾸는 요청에는 CSRF 토큰이 필요하다 ([ADR-0003](docs/adr/0003-session-cookie-auth.md)).
- 영상 송출(WHIP)만은 로그인 세션이 아닌 방송 키로 허가한다. 미디어 서버가 브라우저 쿠키를 받지 못하기 때문이다. 방송 키는 소유자에게만 발급된다.

## 후원

로그인한 회원이 방송 중인 방송의 방송자에게 1,000원부터 500,000원까지 1,000원 단위로 후원한다. 메시지는 100자까지 붙일 수 있고, 자기 방송에는 후원할 수 없다. 결제는 [토스페이먼츠 카드/간편결제 통합결제창](https://docs.tosspayments.com/guides/v2/payment-window/integration)의 **테스트 모드**로만 동작해 실제로 돈이 빠져나가지 않는다.

1. 후원 화면에서 금액·메시지를 고르고 "후원하기"를 누르면, 서버가 후원을 결제 대기로 만든다. 그 id를 결제 주문번호(`orderId`)로 토스페이먼츠 결제창이 열리고, 결제수단은 결제창에서 고른다.
2. 결제창에서 인증을 마치면 `/donations/success`로 돌아온다. 웹이 `paymentKey`와 금액을 서버에 보내면, 서버는 저장한 금액과 같은지 확인한 뒤 토스페이먼츠 승인 API를 부른다. 금액이 다르면 승인하지 않고 실패로 끝낸다.
3. 승인되면 완료가 되고, 3초 안에 시청 화면과 방송자 화면에 알림이 뜨고 댓글 패널에 남는다.

실패와 취소는 이렇게 처리한다.

- 결제창을 닫으면 취소, 결제가 거절되면 실패가 된다. 실패 사유는 후원 내역에 남는다. PC에서는 결제창을 닫으면 페이지에서 바로 처리하고, 휴대폰에서는 `/donations/fail`로 돌아와 처리한다.
- 승인 결과를 모르면(타임아웃 등) 결제 대기로 남긴다. 결과 화면의 "다시 확인"은 같은 멱등키로 승인을 다시 요청하고, 30분이 지나면 서버의 정리 작업이 토스페이먼츠에 `orderId`로 결제를 조회해 결과를 맞춘다. 승인을 요청한 적 없는 결제 대기는 30분 뒤 만료된다.
- 결제하는 사이 방송이 끝나도 승인한다. 방송이 삭제되어도 후원 기록은 남는다.

### 토스페이먼츠 키 넣기

**API 개별 연동 키**를 쓴다. 사업자 등록이나 전자결제 계약 없이 [개발자센터](https://developers.tosspayments.com/my/api-keys)에 이메일로 가입하면 받을 수 있고, 테스트 결제내역도 개발자센터에서 볼 수 있다. 공개된 문서용 키는 쓸 수 없으므로 본인 키가 필요하다.

1. `server/config/local.properties.example`을 `server/config/local.properties`로 복사한다. 이 파일은 git에 올라가지 않는다.
2. 개발자센터 API 키 메뉴의 **API 개별 연동 키** 한 쌍을 적는다. 클라이언트 키(`test_ck_…`)와 시크릿 키(`test_sk_…`)는 같은 쌍이어야 한다.
3. 서버를 다시 시작한다. `server/` 폴더에서 실행해야 이 파일을 읽는다(`./gradlew bootRun` 또는 `java -jar build/libs/…`).

파일 대신 `TOSS_CLIENT_KEY`, `TOSS_SECRET_KEY` 환경변수로 넣어도 된다. 둘 다 있으면 파일이 이긴다. 다른 기능은 키가 없어도 동작하고, 후원만 "결제가 설정되지 않았습니다"로 막힌다. 결제위젯 키(`test_gck_`)나 라이브 키를 넣으면 서버가 시작하지 않는다.

## 유튜브 동시 송출

방송을 시작할 때 "유튜브에도 송출"을 고르면 같은 방송이 방송자 본인의 유튜브 채널에도 라이브로 나간다. 송출은 한 번만 하고, MediaMTX가 띄운 ffmpeg가 영상은 그대로, 소리만 AAC로 바꿔 유튜브에 다시 보낸다([ADR-0007](docs/adr/0007-simulcast-relayed-by-mediamtx-ffmpeg.md)). 방송자는 유튜브 스튜디오의 스트림 키를 방송하기 화면에서 한 번 등록한다. 서버는 키를 암호화해 저장하고 끝 네 자리만 보여 준다. 유튜브 연결이 실패해도 방송은 계속되고, 방송자 화면에 "유튜브: 연결 실패"가 뜬다.

### 동시 송출 켜기

서버와 MediaMTX가 같은 중계 비밀 값을 알아야 한다. 값이 없으면 동시 송출만 화면에 나타나지 않는다.

1. 루트 `.env`에 `SIMULCAST_RELAY_SECRET=<openssl rand -hex 32 값>`을 넣고 `docker compose up -d mediamtx`로 MediaMTX를 다시 띄운다.
2. `server/config/local.properties`에 같은 값과 암호화 키를 넣고 서버를 다시 시작한다.
   ```properties
   simulcast.relay-secret=<1과 같은 값>
   simulcast.encryption-key=<openssl rand -base64 32 값>
   ```
   암호화 키를 바꾸면 저장된 유튜브 키를 읽을 수 없으니 다시 등록해야 한다.

유튜브 대신 로컬에서 받아 보려면 RTMP 수신기를 띄우고, 서버의 수신 주소를 바꾼다.

```bash
docker compose --profile simulcast-test up -d rtmp-sink
```

```properties
simulcast.youtube-url=rtmp://rtmp-sink:1935/live2
```

수신기에 들어온 방송은 `docker compose logs rtmp-sink`에서 `live2/<등록한 키>` 경로로 보인다.

## 개발 환경 실행

필요한 것: JDK 25, Node 24, Docker, ffmpeg (`brew install ffmpeg`)

```bash
docker compose up -d          # PostgreSQL + MediaMTX
cd server && ./gradlew bootRun   # http://localhost:8180
cd web && npm install && npm run dev   # http://localhost:5173
```

웹 개발 서버는 `/api`, `/media` 요청을 Spring(8180)으로, `/hls`, `/whip` 요청을 MediaMTX(8888, 8889)로 넘긴다. 업로드된 파일은 `server/data/`에 쌓인다.

### 휴대폰으로 방송하기

휴대폰 브라우저는 HTTPS 페이지에서만 카메라를 열어 주고, WebRTC 연결에는 PC의 LAN IP가 필요하다.

1. `.env.example`을 `.env`로 복사하고 `LAN_IP`에 PC의 IP를 적는다 (macOS: `ipconfig getifaddr en0`). 그다음 `docker compose up -d`로 MediaMTX를 다시 띄운다.
2. `cd web && npm run dev:https`로 실행한다.
3. 휴대폰을 PC와 같은 Wi-Fi에 연결하고 `https://<LAN_IP>:5173/broadcast`로 접속한다. 자체 서명 인증서 경고는 한 번 넘기면 된다.

방화벽이 켜져 있으면 5173(TCP)과 8189(UDP/TCP) 포트를 허용해야 한다.

## 서버 컨테이너로 실행

```bash
docker compose --profile app up -d --build                   # PostgreSQL + API 서버 + 변환 워커 1대
docker compose --profile app up -d --scale worker=3          # 변환 워커를 3대로 늘린다
```

API 서버는 업로드를 받아 처리 중 상태로 남기기만 한다. 변환은 `worker` 컨테이너들이 DB 대기열에서 하나씩 나눠 가져가 처리한다([ADR-0004](docs/adr/0004-transcoding-worker-with-db-queue.md)). 워커가 죽으면 점유 기한(60초)이 지난 뒤 다른 워커가 이어받는다. 개발할 때 `./gradlew bootRun`으로 서버 하나만 띄우면 그 서버가 변환까지 맡는다.

## 로컬 오토스케일 (쿠버네티스)

서버를 로컬 쿠버네티스에서 여러 Pod로 띄우고, CPU 사용률에 따라 Pod 수가 자동으로 늘고 줄게 한다. 같은 매니페스트를 OCI VM 한 대의 K3s에 올린 운영 구성은 아래 ["OCI 운영 (K3s)"](#oci-운영-k3s)에 있다.

### 구현 범위

| 요구 | 구현 |
| --- | --- |
| 컨테이너 이미지 | [server/Dockerfile](server/Dockerfile). API 서버와 변환 워커가 같은 이미지를 쓴다 |
| 여러 인스턴스 실행 | Docker Desktop Kubernetes의 Deployment: [server](k8s/base/server.yaml), [worker](k8s/base/worker.yaml). 공통 매니페스트는 [k8s/base](k8s/base), 로컬 전용 설정은 [k8s/overlays/local](k8s/overlays/local)에 있다 |
| 지표 기반 자동 조절 | [HPA](k8s/base/autoscaling.yaml)(워커는 [worker-hpa.yaml](k8s/overlays/local/worker-hpa.yaml)). API 서버는 CPU 60% 기준 **2~4개**, 변환 워커는 CPU 70% 기준 **1~2개**. 최대값은 Docker Desktop 자원(CPU 10개, 메모리 8GB) 안에 들어오게 정했다. CPU 지표는 [metrics-server](k8s/metrics-server/kustomization.yaml)가 모은다 |
| 요청 분산 | `server` Service(LoadBalancer)가 준비된 서버 Pod들에 요청을 나눈다. Docker Desktop은 이것을 `localhost:8180`에 연다. Pod는 `/actuator/health/readiness`가 통과해야 요청을 받는다 |
| 상태를 인스턴스 밖에 | 로그인 세션(Spring Session JDBC), Broadcast(라이브 상태)·Video·Comment·Donation, 변환 대기열은 모두 [PostgreSQL](k8s/base/postgres.yaml)에 있다. 업로드한 영상과 HLS 파일은 모든 Pod가 함께 마운트하는 [media-data 볼륨](k8s/base/media-storage.yaml)에 있다. 서버 Pod는 메모리에 상태를 두지 않는다 |
| 부하 발생 | [load-test/load.js](load-test/load.js) (k6, Docker로 실행) |

함께 고려한 것:

- **주기 작업**: Broadcast 동기화와 오래된 Donation 정리는 서버 Pod마다 돈다. 둘 다 낙관적 잠금(`@Version`)으로 저장하므로, 여러 Pod가 같은 행을 동시에 바꿔도 하나만 반영되고 나머지는 다음 주기에 다시 맞춘다.
- **영상 변환**: 무거운 변환은 API 서버 밖의 워커 Pod가 맡는다([ADR-0004](docs/adr/0004-transcoding-worker-with-db-queue.md)). 그래서 업로드가 몰려도 변환이 API 서버의 CPU를 쓰지 않고, 워커만 따로 늘어난다. API 서버는 업로드 저장과 ffprobe 검사만 한다.
- **MediaMTX**: WebRTC 연결을 직접 들고 있어서 한 대로 둔다. 송출 허가는 `server` Service로 물어보므로 서버 Pod 수와 상관없이 동작한다.
- **줄어들 때**: Pod는 종료 신호를 받으면 처리 중인 요청을 마치고 내려간다(Spring Boot graceful shutdown). 워커는 변환 중이던 Video의 점유를 풀어서 다른 워커가 바로 이어받게 한다.
- **한계**: Docker Desktop은 노드가 하나라서 `ReadWriteOnce` 볼륨도 모든 Pod가 함께 쓸 수 있다. 노드가 여러 대면 `ReadWriteMany` 볼륨이나 S3 같은 오브젝트 스토리지가 필요하다.

### 실행 방법

필요한 것: Docker Desktop(설정 → Kubernetes → Enable Kubernetes), kubectl. k6는 Docker 이미지로 실행하므로 설치하지 않아도 된다.

```bash
docker compose down                 # compose로 띄운 것이 있으면 내린다 (8180 포트를 같이 쓴다)
kubectl config use-context docker-desktop
k8s/up.sh                           # 이미지 빌드 + metrics-server 설치 + 배포 (처음엔 몇 분 걸린다)
cd web && npm run dev               # 웹은 그대로 http://localhost:5173 (API는 localhost:8180 Service로 간다)
```

지울 때는 `k8s/down.sh`를 실행한다. DB와 영상 파일도 함께 지워진다.

### 환경변수

서버와 워커는 [k8s/base/config.yaml](k8s/base/config.yaml)의 ConfigMap과 [k8s/overlays/local/db-credentials.yaml](k8s/overlays/local/db-credentials.yaml)의 Secret에서 값을 받는다. 아래 DB 계정은 로컬 개발용 예시 값이다.

| 이름 | 예시 | 설명 |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://postgres:5432/webbroadcast` | 공용 DB |
| `DB_USERNAME` / `DB_PASSWORD` | `webbroadcast` / `webbroadcast` | Secret `db-credentials` |
| `MEDIAMTX_API_URL` | `http://mediamtx-api:9997` | 방송 상태 동기화 |
| `MEDIA_STORAGE_ROOT` | `/data` | 공유 볼륨 마운트 위치 |
| `VIDEO_WORKER_ENABLED` | `false` (서버), 워커는 `SPRING_PROFILES_ACTIVE=worker` | 변환을 누가 맡는지 |
| `TOSS_CLIENT_KEY` / `TOSS_SECRET_KEY` | `test_ck_…` / `test_sk_…` | 선택. Secret `toss-keys`로 넣는다 |
| `LAN_IP` (`.env`) | `192.168.0.10` | 선택. 휴대폰 송출용. `k8s/up.sh`가 MediaMTX에 넘긴다 |
| `SIMULCAST_ENCRYPTION_KEY` / `SIMULCAST_RELAY_SECRET` | `openssl rand -base64 32` / `openssl rand -hex 32` | 선택. 유튜브 동시 송출. Secret `simulcast`로 넣으면 서버와 MediaMTX가 함께 읽는다 |

토스페이먼츠 테스트 키는 저장소에 넣지 않고 클러스터에 직접 만든다.

```bash
kubectl -n webbroadcast create secret generic toss-keys \
  --from-literal=TOSS_CLIENT_KEY=test_ck_... --from-literal=TOSS_SECRET_KEY=test_sk_...
kubectl -n webbroadcast rollout restart deployment/server
```

유튜브 동시 송출도 같은 방식으로 넣는다.

```bash
kubectl -n webbroadcast create secret generic simulcast \
  --from-literal=SIMULCAST_ENCRYPTION_KEY="$(openssl rand -base64 32)" \
  --from-literal=SIMULCAST_RELAY_SECRET="$(openssl rand -hex 32)"
kubectl -n webbroadcast rollout restart deployment/server deployment/mediamtx
```

### 스케일 아웃·인 확인 절차

1. 터미널 A에서 Pod 수를 기록한다: `k8s/watch-scale.sh | tee scale-log.txt`
2. 터미널 B에서 부하를 건다: `load-test/run.sh`
   - 가상 사용자 30명이 로그인(bcrypt라 CPU를 많이 씀)과 목록 조회를 3분 동안 반복한다.
   - 동시에, 시작할 때 만든 회원의 세션 쿠키 하나로 매 반복마다 로그인 상태, 방송, 댓글을 조회한다. 요청이 어느 Pod로 가든, Pod 수가 바뀌는 중이든 같은 결과여야 한다(`checks{kind:state}` 99% 이상이 기준).
3. 부하가 도는 동안 `server` Pod가 2개에서 늘어나는지 본다.
4. 부하가 끝나고 1~2분 기다리면 다시 2개로 줄어드는지 본다.
5. 도중에 `kubectl -n webbroadcast get hpa`로 HPA가 본 CPU 사용률을 확인할 수 있다.

### 확인한 결과 (2026-11-13, MacBook · Docker Desktop Kubernetes v1.36 · CPU 10개 / 메모리 8GB)

**API 서버**: `load-test/run.sh` (가상 사용자 30명, 3분 50초). 원본 기록은 [load-test/results/server-scale.txt](load-test/results/server-scale.txt)와 [k6-server.txt](load-test/results/k6-server.txt)에 있다. 아래는 값이 바뀐 줄만 뽑은 것이다.

```
시각      server (CPU/목표, Pod)   worker (CPU/목표, Pod)
09:26:15  7%/60%, 2개              1%/70%, 1개      ← 부하 전, 최소 2개
09:27:08  369%/60%, 2개            0%/70%, 1개      ← 부하 시작, 목표 초과
09:27:18  369%/60%, 3개            0%/70%, 1개      ← 스케일 아웃
09:27:23  369%/60%, 4개            0%/70%, 1개      ← 최대 4개
09:30:07  195%/60%, 4개            0%/70%, 1개
09:31:11  3%/60%, 3개              0%/70%, 1개      ← 부하 끝, 스케일 인
09:32:09  2%/60%, 2개              0%/70%, 1개      ← 최소 2개로 복귀
```

부하가 걸린 동안(스케일 아웃 포함) 상태 확인 결과 (k6). 스케일 인은 부하가 끝난 뒤 일어나서, 줄어드는 동안의 요청은 확인하지 않았다:

```
✓ 로그인 유지   ✓ 방송 유지   ✓ 댓글 유지
✓ { kind:state }...: 100.00% 16719 out of 16719
✓ http_req_failed..: 0.00%   0 out of 39015
```

**변환 워커**: 1080p 60초 영상 6개를 업로드했다. 원본 기록은 [load-test/results/worker-scale.txt](load-test/results/worker-scale.txt)에 있다.

```
시각      server (CPU/목표, Pod)   worker (CPU/목표, Pod)
09:32:37  2%/60%, 2개              0%/70%, 1개      ← 업로드 전
09:33:09  2%/60%, 2개              399%/70%, 2개    ← 변환 시작, 워커 스케일 아웃 (최대 2개)
09:35:11  4%/60%, 2개              1%/70%, 1개      ← 6개 모두 공개된 뒤 스케일 인
```

API 서버의 CPU는 그대로였다. 무거운 변환이 워커로만 가기 때문이다.

확인하면서 겪은 문제:

- Service 이름이 `server`라서 쿠버네티스가 `SERVER_PORT=tcp://…` 환경변수를 넣었고, Spring이 이것을 포트 설정으로 읽어 시작하지 못했다. 그래서 `enableServiceLinks: false`로 막았다.
- 처음에는 HPA 최대값을 서버 5개, 워커 4개로 잡았다. 서버 부하와 영상 업로드를 함께 걸었더니 최대로 늘어난 Pod들의 메모리 한도 합이 Docker Desktop 메모리(8GB)를 넘었고, Docker Desktop 전체가 멈췄다. 그래서 최대값을 자원 안에 들어오게 줄였다(서버 4개, 워커 2개).
- 워커 메모리 한도가 1GiB(JVM 최대 힙 비율 75%)일 때 1080p 변환 중 ffmpeg가 종료 코드 137(SIGKILL)로 끝났다. 메모리 부족으로 추정해 JVM 최대 힙을 20%, 한도를 2GiB로 바꿨고, 그 뒤 6개 모두 정상 변환됐다. OOM 이벤트 자체는 확인하지 않았다([관찰 기록](load-test/results/worker-observations.md)).

## API

🔓 누구나, 🔑 로그인 필요, 👤 소유자 또는 작성자만, 🛡️ 운영자만. 🔑·👤 요청은 `X-XSRF-TOKEN` 헤더에 `XSRF-TOKEN` 쿠키 값을 담아야 한다.

| 메서드 | 경로 | | 설명 |
| --- | --- | --- | --- |
| `GET` | `/api/auth/me` | 🔓 | `{user}`. 로그인하지 않았으면 `user`가 null. `user.admin`이 true면 운영자다. CSRF 토큰 쿠키도 이때 받는다 |
| `POST` | `/api/auth/signup` | 🔓 | JSON `{username, password, nickname}`. 가입하면 바로 로그인된다 |
| `POST` | `/api/auth/login` | 🔓 | JSON `{username, password}`. 정지된 회원이면 403 |
| `POST` | `/api/auth/logout` | 🔑 | 204 |
| `POST` | `/api/videos` | 🔑 | multipart: `file`, `title`, `description`. 201과 처리 중 상태의 Video |
| `GET` | `/api/videos?page=0` | 🔓 | 공개된 Video 목록, 최신순 20개씩 |
| `GET` | `/api/videos/{id}` | 🔓 | Video 하나. 공개 상태면 `playbackUrl`(HLS)과 `thumbnailUrl` 포함 |
| `PATCH` | `/api/videos/{id}` | 👤 | JSON `{title, description}` |
| `DELETE` | `/api/videos/{id}` | 👤 | 파일과 댓글도 함께 지운다. 204 |
| `GET` | `/media/videos/{id}/...` | 🔓 | HLS 플레이리스트·세그먼트·썸네일 |
| `POST` | `/api/broadcasts` | 🔑 | JSON `{title, simulcast}`. 준비 상태의 Broadcast와 `streamKey`, `whipUrl`. 진행 중인 방송이 있으면 409와 `activeBroadcastId`. `simulcast`가 true인데 유튜브 키가 없으면 400 |
| `GET` | `/api/broadcasts` | 🔓 | `live`(방송 중)와 `recentlyEnded`(최근 종료 20개) |
| `GET` | `/api/broadcasts/{id}` | 🔓 | Broadcast 하나. 방송 중이면 `playbackUrl`(LL-HLS) 포함. 소유자가 보면 `simulcastStatus`(`CONNECTING`/`LIVE`/`FAILED`, 동시 송출을 켠 방송만) 포함 |
| `PATCH` | `/api/broadcasts/{id}` | 👤 | JSON `{title}` |
| `POST` | `/api/broadcasts/{id}/end` | 👤 | 204 |
| `DELETE` | `/api/broadcasts/{id}` | 👤 | 진행 중이면 송출을 끊는다. 댓글도 함께 지운다. 204 |
| `POST` | `/api/broadcasts/{id}/stream-key` | 👤 | 준비 상태일 때 방송 키를 새로 발급한다. 이전 키는 무효가 된다 (서버는 키의 해시만 저장한다) |
| `GET` | `/api/videos/{id}/comments?before=` | 🔓 | 최신순 20개씩. 응답의 `nextCursor`를 `before`로 넘기면 이어서 온다 |
| `GET` | `/api/broadcasts/{id}/comments` | 🔓 | 최근 100개, 오래된 순 |
| `POST` | `/api/videos/{id}/comments`, `/api/broadcasts/{id}/comments` | 🔑 | JSON `{body}` (500자 이하) |
| `PATCH` | `/api/comments/{id}` | 👤 | JSON `{body}`. 수정되면 `updatedAt`이 채워진다 |
| `DELETE` | `/api/comments/{id}` | 👤 | 204 |
| `GET` | `/api/payments/config` | 🔓 | `{clientKey}` 결제창 클라이언트 키. 키가 설정되지 않았으면 null |
| `POST` | `/api/broadcasts/{id}/donations` | 🔑 | JSON `{amount, message}`. 방송 중일 때만. 201과 `{donation, orderId, orderName, customerKey}`. 결제 키가 없으면 503 |
| `GET` | `/api/broadcasts/{id}/donations` | 🔓 | 완료된 후원 최근 100개, 오래된 순 |
| `POST` | `/api/donations/{id}/approve` | 👤 | JSON `{paymentKey, amount}`. 후원자만. 결과를 아직 모르면 503이며 같은 요청을 다시 보내면 된다 |
| `POST` | `/api/donations/{id}/abandon` | 👤 | JSON `{code, message}` (결제 서비스가 `failUrl`로 준 값). 후원자만 |
| `GET` | `/api/donations/sent` | 🔑 | 내가 보낸 후원 최근 50개 (모든 상태) |
| `GET` | `/api/donations/received` | 🔑 | 내가 받은 완료된 후원 최근 50개와 전체 건수·합계 |
| `GET` | `/api/simulcast` | 🔑 | 내 동시 송출 설정 `{available, youtubeKeyLast4, onByDefault}`. `available`이 false면 서버에 설정이 없다 |
| `PUT` | `/api/simulcast/youtube-key` | 🔑 | JSON `{key}`. 유튜브 스트림 키 등록·변경. 끝 네 자리만 돌려준다 |
| `DELETE` | `/api/simulcast/youtube-key` | 🔑 | 204 |
| `POST` | `/api/reports` | 🔑 | JSON `{targetType, targetId, reason, detail}`. `targetType`은 `BROADCAST`/`VIDEO`/`COMMENT`, `reason`은 `SPAM`/`HATE`/`SEXUAL`/`VIOLENCE`/`COPYRIGHT`/`OTHER`, `detail`은 200자 이하(선택). 201. 자기 것이면 400, 이미 신고했으면 409 |
| `GET` | `/api/admin/reports` | 🛡️ | 처리 전 신고를 대상별로 묶어 오래된 순으로. 대상 요약, 소유자(정지 여부), 사유별 건수, 신고 목록 |
| `POST` | `/api/admin/reports/{targetType}/{targetId}/hide` | 🛡️ | 대상을 숨기고 걸린 신고를 처리 완료로 닫는다. 방송은 진행 중이면 끝난다. 204 |
| `POST` | `/api/admin/reports/{targetType}/{targetId}/dismiss` | 🛡️ | 문제없음으로 신고만 닫는다. 204 |
| `GET` | `/api/admin/users?q=&suspended=&page=0` | 🛡️ | 회원 목록, 최근 가입한 순 20명씩. `q`는 아이디나 닉네임 일부(대소문자 무시), `suspended`는 `true`/`false`로 정지 여부를 거른다. 항목은 `{id, username, nickname, admin, suspended, suspendedAt, createdAt}` |
| `POST` | `/api/admin/users/{id}/suspend`, `/unsuspend` | 🛡️ | 회원 정지·해제. 정지하면 세션을 끊고 진행 중인 방송을 끝낸다. 운영자는 정지할 수 없다(400). 204 |
| `POST` | `/api/media-server/auth` | | MediaMTX 인증 훅 (내부용) |
| `GET`·`POST` | `/api/internal/simulcast/target`, `/status` | | MediaMTX 동시 송출 중계 스크립트 전용. `X-Relay-Secret` 헤더가 맞지 않으면 404 |

오류는 ProblemDetail 형식이며 `detail`에 사용자에게 보여줄 문구가 담긴다. 로그인이 필요하면 401, 권한이 없거나 CSRF 토큰이 없으면 403이다.

## 테스트

```bash
cd server && ./gradlew test
```

`AccessControlTest`, `DonationTest`, `SimulcastTest`는 Testcontainers로 PostgreSQL을 띄워 HTTP 요청으로 확인하므로 Docker가 실행 중이어야 한다. `AccessControlTest`는 공개 범위와 소유자·작성자 권한을, `DonationTest`는 후원의 결제 흐름을 본다. `DonationTest`는 토스페이먼츠 대신 가짜 결제 서비스로 승인·거절·결과 모름을 정한다. `SimulcastTest`는 유튜브 키 암호화 저장과 중계 내부 API를 본다(실제 중계는 위의 로컬 RTMP 수신기로 확인한다).

## 배포할 때 주의

웹 서버(nginx 등)는 `/api/internal/*`을 밖으로 넘기지 않는다. MediaMTX만 서버에 직접 부르는 경로다(유튜브 동시 송출, ADR-0007).

웹 서버(nginx 등)는 Vite 개발 서버처럼 `/hls/*`를 MediaMTX 8888로, `/whip/*`를 8889로 넘겨야 한다. 이때 앞의 접두사는 떼고 넘긴다. MediaMTX는 첫 HLS 요청을 `/live/...`로 리다이렉트하므로, 응답의 `Location` 헤더에도 `/hls`를 다시 붙여야 한다 (nginx `proxy_redirect`).

## 공개 주소

| 주소 | 내용 |
| --- | --- |
| https://live.duse.dev | 방송 서비스 (웹 화면과 API) |
| https://live.duse.dev/actuator/health | 상태 확인. 서버와 DB가 정상이면 `{"status":"UP"}` |
| https://live.duse.dev/api/broadcasts | 방송 목록 API (데스크톱 앱이 부르는 것과 같다) |
| https://live.duse.dev/download/ | 데스크톱 앱 다운로드 페이지 ([소스](deploy/download/index.html)) |
| https://duse.dev | 같은 서버에 올린 서비스들의 켜짐/꺼짐 카드 |

## 도메인과 HTTPS

| 항목 | 값 |
| --- | --- |
| 도메인 | `duse.dev` — **등록함** (2026-11-19, Cloudflare Registrar, 상태 Active, 만료 2027-11-19) |
| 최초 등록 비용 | **US$8.20** (1년, 2026-11-19 결제) |
| 갱신 비용 | **US$12.20 / 1년** (Cloudflare 도메인 관리 화면의 갱신 가격. 최초 결제 금액보다 높다) |
| 서비스 주소 | `live.duse.dev` (서브도메인) |
| DNS | Cloudflare DNS. `.dev`를 Cloudflare Registrar에서 사면 네임서버를 Cloudflare 밖으로 옮길 수 없다 |
| 인증서 | Let's Encrypt, Caddy가 자동 발급·갱신 (90일 인증서, 만료 30일 전쯤 갱신) |

### DNS 구성

Cloudflare 대시보드 → `duse.dev` → **DNS → Records**에 두 줄을 넣는다.

| Type | Name | Content | Proxy status |
| --- | --- | --- | --- |
| A | `@` | `161.33.218.209` (OCI VM 공인 IP) | DNS only (회색 구름) |
| A | `*` | `161.33.218.209` | DNS only |

- `*`(와일드카드)가 있어서 `live.duse.dev`처럼 새 서브도메인을 만들 때 DNS를 다시 건드리지 않는다.
- **프록시를 끈다.** Cloudflare 프록시는 HTTP(S)만 넘겨서 WebRTC 미디어(UDP/TCP 8189)가 통하지 않고, 인증서도 서버가 직접 받아야 하기 때문이다.
- 확인: `dig +short live.duse.dev` → `161.33.218.209`

### HTTPS 구성

`.dev`는 브라우저에 HSTS가 미리 들어 있어(HSTS preload) HTTP로는 열리지 않는다. 그래서 처음부터 HTTPS가 필요하다.

1. OCI 보안 목록과 서버 iptables에서 80, 443/TCP를 연다(아래 "OCI VM" 참고). 인증서 발급 검증에 80/443이 쓰인다.
2. 서버에 Caddy를 설치한다([공식 apt 저장소](https://caddyserver.com/docs/install#debian-ubuntu-raspbian)). systemd 서비스로 돈다.
3. [deploy/Caddyfile](deploy/Caddyfile)을 `/etc/caddy/Caddyfile`에 두고 반영한다.
   ```bash
   sudo caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
   sudo systemctl reload caddy
   ```
   Caddyfile에 사이트 이름(`live.duse.dev`)만 적으면 Caddy가 Let's Encrypt에서 인증서를 받고, HTTP→HTTPS 리다이렉트도 켠다. 갱신도 Caddy가 알아서 한다.
4. 확인: `curl -sI https://live.duse.dev`가 200이고, 인증서 발급자가 Let's Encrypt인지 본다.
   ```bash
   echo | openssl s_client -connect live.duse.dev:443 -servername live.duse.dev 2>/dev/null | openssl x509 -noout -issuer -enddate
   ```

## OCI VM

| 항목 | 값 |
| --- | --- |
| 리전 | 도쿄 `ap-tokyo-1` (계정의 홈 리전) |
| Shape | `VM.Standard.A1.Flex` (Ampere ARM64) |
| OCPU / 메모리 | **2 OCPU / 12GB** (만들 때는 1 OCPU·6GB, 재고가 생긴 뒤 늘림. 2026-11-21 확인) |
| OS | Ubuntu 24.04 (aarch64) |
| 부트 볼륨 | 약 45GB |
| 네트워크 | VCN `duse-vcn`의 공개 서브넷, 공인 IP `161.33.218.209` |
| 열린 포트 | 22, 80, 443/TCP, 8189/TCP·UDP (OCI 보안 목록과 서버 iptables 두 곳) |
| 실행 환경 | K3s v1.37.2+k3s1 단일 노드(방송 서비스와 같은 VM의 다른 서비스 short-url), Caddy(HTTPS). Docker는 없다(2026-11-21에 지움) |

### 이 사양을 고른 이유

- Always Free의 Arm 기반 A1은 계정 전체에 **월 1,500 OCPU 시간·9,000 GB 시간**(상시 2 OCPU·12GB에 해당)까지 무료다([OCI Always Free Resources](https://docs.oracle.com/en-us/iaas/Content/FreeTier/resourceref.htm), 2026-11-21 확인). 예전에는 월 3,000 OCPU 시간·18,000 GB 시간(4 OCPU·24GB)이었는데, 2026년 6월쯤 문서가 절반으로 바뀌었다. Oracle은 이 변경을 따로 공지하지 않았다. Always Free는 홈 리전에서만 만들 수 있다. 이 계정에는 다른 A1 인스턴스가 없어 한도를 모두 쓸 수 있었다.
- 과제 기준은 2 OCPU·12GB지만, 도쿄 리전에 A1 재고가 없어 생성이 **"Out of capacity"**로 실패했다(2026-11-19). 1 OCPU·6GB로 줄여도 처음에는 같은 오류가 났고, 다시 시도해서 만들어졌다. 홈 리전은 바꿀 수 없어 다른 리전으로 옮기는 대안은 없었다.
- 다른 무료 대안인 `VM.Standard.E2.1.Micro`(AMD, 1/8 OCPU·1GB)는 Spring 서버, PostgreSQL, MediaMTX, ffmpeg 변환 워커를 함께 돌리기에 메모리가 부족하다. 그래서 작더라도 A1을 골랐다.
- 1 OCPU·6GB에서 방송 서비스 전체가 약 1.3GB를 썼다. 재고가 생긴 뒤 같은 인스턴스를 2 OCPU·12GB로 늘렸다(Edit → Shape, 재시작 필요). 2 OCPU·12GB는 지금 무료 한도와 꼭 같다: 31일인 달에 1,488 OCPU 시간·8,928 GB 시간으로 한도(1,500·9,000) 안이다. 그래서 이 VM을 더 키우거나 같은 계정에 A1을 하나 더 만들면 넘는 만큼 요금이 나온다.
- ARM이라서 Docker 이미지가 `linux/arm64`를 지원해야 한다. 이 프로젝트의 이미지(Temurin JDK, PostgreSQL, MediaMTX)는 모두 지원한다.

### 무료 범위를 넘지 않게 막은 것

- 계정은 Pay As You Go다(2026-11-04 전환). 그래서 **할당량 정책 `free-tier-only`**로 A1(2 OCPU·12GB)과 E2 Micro, 블록 스토리지 200GB 말고는 만들 수 없게 막았다. 처음에는 A1 상한이 예전 무료 한도인 4 OCPU·24GB였고, 무료 한도가 줄어든 것을 알고 2 OCPU·12GB로 낮췄다(2026-11-21).
  - Pay As You Go 계정에도 2 OCPU·12GB가 적용되는지는 공식 문서에 따로 나와 있지 않다. 이 VM은 2 OCPU·12GB라 어느 쪽이든 무료 범위 안이다.
- 예산 $1에 실제 지출 1%·99% 알림을 걸었다.
- 무료 송신 한도(월 10TB)를 넘지 않게 [deploy/egress-cap.sh](deploy/egress-cap.sh)가 서버 송신을 29 Mbit/s로 묶는다.

### 처음부터 다시 만드는 순서

1. OCI 콘솔 → Compute → Instances → Create. 이미지 Ubuntu 24.04, Shape `VM.Standard.A1.Flex`(Ampere), OCPU·메모리 지정, 공개 서브넷, 공인 IP 할당, SSH 공개 키 등록.
2. 네트워킹 → VCN → Security Lists → `Default Security List`의 수신 규칙에 80, 443/TCP와 8189/TCP·UDP를 더한다.
3. 서버에 접속해 iptables에서 같은 포트를 연다(Ubuntu 이미지는 22만 열려 있다). `sudo netfilter-persistent save`로 저장한다.
4. Caddy를 설치한다. 서비스는 모두 K3s에서 돌므로 Docker는 설치하지 않는다.
5. DNS A 레코드를 새 공인 IP로 바꾼다(위 "DNS 구성").
6. `/opt/web-broadcast/.env`를 [deploy/env.example](deploy/env.example) 보고 만들고, 아래 "OCI 운영 (K3s)"의 설치·배포 순서를 따른다.
7. Caddyfile과 다운로드 페이지를 올린다(위 "HTTPS 구성", 아래 "데스크톱 앱").

## OCI 운영 (K3s)

https://live.duse.dev 는 OCI VM **한 대**의 단일 노드 K3s에서 돈다. [로컬 오토스케일](#로컬-오토스케일-쿠버네티스)과 같은 매니페스트([k8s/base](k8s/base))에 OCI용 설정([k8s/overlays/oci](k8s/overlays/oci/kustomization.yaml))을 얹었다. API 서버 Pod 수는 HPA가 CPU 사용률에 따라 2~4개로 조절하고, 공개 도메인과 Windows·macOS 앱은 같은 주소를 계속 쓴다.

```
https://live.duse.dev ─ Caddy(호스트) ─┬─ /                → React 빌드 (/var/www/live.duse.dev)
                                      ├─ /api, /media     → 127.0.0.1:8180 → Service server(LoadBalancer, K3s ServiceLB)
                                      │                                      → 준비된 server Pod 2~4개 (HPA)
                                      ├─ /hls             → 127.0.0.1:8888 → Service mediamtx (접두사 떼고, Location에 다시 붙임)
                                      ├─ /whip            → 127.0.0.1:8889 → Service mediamtx (접두사 떼고)
                                      └─ /api/internal    → 404
UDP/TCP 8189 (WebRTC 미디어)          → MediaMTX Pod의 hostPort

VM (2 OCPU·12GB, arm64)
├─ Caddy, duse-status 타이머 (호스트 프로그램)
└─ K3s ─ webbroadcast 네임스페이스: server ×2~4 (HPA) · worker ×1 · postgres ×1 · mediamtx ×1
         short-url 네임스페이스: short-url ×1 (다른 서비스, hostNetwork 127.0.0.1:8190)
         kube-system: coredns · metrics-server · local-path-provisioner · svclb
```

### 구현 범위

| 요구 | 구현 |
| --- | --- |
| 단일 노드 K3s | [deploy/k3s/install.sh](deploy/k3s/install.sh). K3s stable(v1.37.2+k3s1)을 설치하고 Traefik은 끈다(HTTPS는 기존 Caddy가 맡아서 80/443을 두고 다투지 않게) |
| VM 아키텍처(arm64)에 맞는 이미지 | [deploy/k3s/deploy.sh](deploy/k3s/deploy.sh)가 `docker build --platform linux/arm64`로 빌드한다. Apple Silicon 맥은 그대로 arm64이고, x86 PC는 Docker buildx(QEMU)로 빌드된다. 의존 이미지 `eclipse-temurin:25`, `postgres:18`, `bluenviron/mediamtx:1.21.3-ffmpeg`는 모두 `linux/arm64`를 제공한다 |
| 이미지 전달 | 레지스트리 없이 `docker save \| ssh … sudo k3s ctr images import -`로 K3s의 containerd에 넣는다. K3s는 Docker와 이미지 저장소가 따로라서 import가 필요하다. Deployment는 `imagePullPolicy: Never` |
| Deployment·Service | [server](k8s/base/server.yaml): CPU 요청량 `250m`, `readinessProbe`(`/actuator/health/readiness`). Service `server`(LoadBalancer 8180)가 준비된 Pod에만 요청을 나눈다. 워커·DB·MediaMTX도 각각 Deployment |
| 공개 도메인 HTTPS | 기존 [Caddyfile](deploy/Caddyfile)의 `live.duse.dev` 블록을 바꾸지 않았다. Caddy가 넘기던 `127.0.0.1:8180/8888/8889`를 이제 K3s ServiceLB가 받는다. ServiceLB는 VM의 모든 주소에 열리지만 OCI 보안 목록과 iptables가 22·80·443·8189만 허용해서 밖에서는 닿지 않는다 |
| 지표 수집 | K3s에 들어 있는 Metrics Server를 쓴다 (로컬과 달리 따로 설치하지 않는다) |
| HPA | [autoscaling/v2 HPA](k8s/base/autoscaling.yaml): 대상 `Deployment/server`, **최소 2 · 최대 4**, CPU 평균 사용률 **60%**. OCI는 behavior를 주지 않아 축소 안정화가 기본값 **5분**이다 |
| 파드 교체 시 상태 유지 | 아래 "파드가 바뀌어도 남는 것" |
| 부하 | [load-test/load.js](load-test/load.js) (k6, Docker로 실행). `BASE_URL=https://live.duse.dev VUS=20 HOLD=3m load-test/run.sh` |

OCI 오버레이가 base와 다른 점:

- 변환 워커는 HPA 없이 **1개로 고정**한다. 2개가 되면 CPU 요청량 500m이 더 필요해서 server를 4개까지 띄울 수 없다(아래 산정).
- postgres(100m)와 mediamtx(100m)에도 CPU 요청량을 줘서 스케줄러가 남은 CPU를 바르게 계산하게 했다.
- MediaMTX는 WebRTC 8189 TCP·UDP를 Pod의 `hostPort`로 VM에 직접 열고, 공인 IP를 ICE 후보로 알린다(`MTX_WEBRTCADDITIONALHOSTS`). hostPort는 노드 하나에 한 Pod만 쓸 수 있어서 업데이트 방식은 `Recreate`다.
- DB 계정과 선택 비밀 값은 저장소에 없다. VM의 `/opt/web-broadcast/.env`에서 [deploy/k3s/secrets.sh](deploy/k3s/secrets.sh)가 Secret으로 만든다.

### 최대 Pod 수 산정

스케줄러는 Pod의 **요청량 합**이 노드의 할당 가능 자원(Allocatable)을 넘으면 Pod를 띄우지 않는다(Pending). 그래서 HPA 최대값은 실제 사용량이 아니라 요청량 합으로 정했다. `kubectl describe node`의 Allocatable은 **CPU 2 (2000m), 메모리 11.6GiB**였다.

| 구성요소 | CPU 요청 | 메모리 (요청 / 한도) |
| --- | --- | --- |
| K3s 시스템 (coredns 100m, metrics-server 100m, local-path, svclb) | 200m | 약 1GB (K3s 프로세스 포함) |
| short-url ×1 (같은 VM의 다른 서비스) | 50m | 64Mi / 256Mi |
| postgres ×1 | 100m | 256Mi / 없음 |
| mediamtx ×1 | 100m | 128Mi / 없음 |
| worker ×1 (고정) | 500m | 1Gi / 2Gi |
| **server 남은 몫** | **2000 − 950 = 1050m** | |
| server 250m × **4** | 1000m | 512Mi × 4 / 768Mi × 4 = 3Gi |

- CPU: server 5개(1250m)는 1050m을 넘으므로 **최대 4개**. 4개일 때 요청량 합은 1950m(97.5%)이다. 부하 측정(아래 결과)은 short-url이 아직 K3s 밖에 있을 때 했고, 그때 server 4개의 Allocated resources는 `cpu 1900m (95%)`, `memory 3596Mi (30%)`였다. short-url이 K3s로 들어온 뒤 server 2개일 때는 `cpu 1450m (72%)`이다.
- 남는 CPU 요청량이 50m뿐이다. 같은 VM에 서비스를 더 올리거나 요청량을 늘리면 server의 4번째 Pod가 Pending이 되므로, 그때는 maxReplicas를 함께 줄인다.
- 메모리: 모든 Pod가 한도까지 써도 server 3Gi + worker 2Gi + DB·MediaMTX·K3s 약 1.5GB ≈ 6.5GB로 12GB 안이다. short-url, Caddy, OS를 더해도 여유가 있다. 그래서 CPU가 상한을 정한다.
- 최소 2개: Pod 하나가 교체되거나 죽는 동안에도 다른 Pod가 요청을 받게 한다.

### 단일 VM 장애의 영향

- HPA는 **같은 VM 안에서만** Pod를 늘린다. Pod 하나가 죽으면 Service가 남은 Pod로 보내고 Deployment가 새로 띄우지만, VM 자체가 멈추면 server·worker·DB·MediaMTX·Caddy(그리고 같은 VM의 short-url)가 **모두 함께 멈춘다**. 노드가 1대라서 다른 곳으로 옮겨 갈 수 없다.
- VM이 재부팅되면 K3s(systemd)가 시작되고 Deployment들이 다시 뜬다. 데이터는 VM 디스크(local-path PVC)에 있어서 남는다. 다시 뜨는 몇 분 동안은 서비스가 끊기고, 진행 중이던 라이브 방송과 WebRTC 연결은 끊긴다.
- 부트 볼륨이 손상되거나 VM을 지우면 DB와 영상 파일이 함께 사라진다. 지금은 자동 백업이 없다.
- 스케일 아웃의 한계도 VM 하나다. CPU 2개를 다 쓰면 HPA가 최대 4개까지 늘려도 처리량은 더 늘지 않는다. 더 늘리려면 VM Shape를 키우거나 노드를 추가해야 하고(둘 다 무료 한도 2 OCPU·12GB를 넘어 요금이 나온다), 노드가 늘면 `ReadWriteOnce` 볼륨 대신 공유 스토리지가 필요하다.

### 파드가 바뀌어도 남는 것

| 상태 | 어디에 두는가 |
| --- | --- |
| 로그인 세션 | Spring Session JDBC → PostgreSQL `spring_session` 테이블. 어느 server Pod로 가도 같은 세션이다 |
| 방송 상태(Broadcast), 동영상 정보(Video), 댓글, 후원 | PostgreSQL |
| 업로드 원본, 변환된 HLS, 썸네일 | `media-data` PVC (`/data`). 모든 server·worker Pod가 함께 마운트한다 |
| PostgreSQL 데이터 파일 | `postgres-data` PVC |
| 두 PVC의 실제 위치 | K3s 기본 StorageClass `local-path` → VM 디스크 `/var/lib/rancher/k3s/storage/` |
| 서명 키·비밀 값 | 세션은 서명 키 없이 DB에 있다. 동시 송출 암호화 키·중계 비밀 값, 토스 키, DB 비밀번호는 Secret에 있고 모든 Pod가 같은 값을 읽는다 |

### 설치와 배포 순서

필요한 것: 맥(또는 PC)에 Docker와 kubectl, VM에 ssh 키. 아래 명령의 `ubuntu@161.33.218.209`, `~/.ssh/oci_duse`는 이 VM의 값이다(`OCI_HOST`, `OCI_KEY` 환경변수로 바꿀 수 있다).

1. **VM에 K3s 설치** ([install.sh](deploy/k3s/install.sh)). OCI Ubuntu 이미지의 iptables는 INPUT·FORWARD 끝에 REJECT가 있어서 Pod 네트워크(10.42.0.0/16)를 막는다. 스크립트가 그 앞에 허용 규칙을 넣고 `/etc/iptables/rules.v4`에도 저장한다. 그다음 K3s를 설치하고 `ubuntu` 사용자가 `kubectl`을 쓰게 한다.
   ```bash
   scp -i ~/.ssh/oci_duse deploy/k3s/install.sh ubuntu@161.33.218.209:/tmp/
   ssh -i ~/.ssh/oci_duse ubuntu@161.33.218.209 'bash /tmp/install.sh'
   ```
2. **비밀 값**. VM의 `/opt/web-broadcast/.env`([deploy/env.example](deploy/env.example))로 Secret을 만든다.
   ```bash
   ssh -i ~/.ssh/oci_duse ubuntu@161.33.218.209 'bash -s' < deploy/k3s/secrets.sh
   ```
3. **배포** ([deploy.sh](deploy/k3s/deploy.sh)). 맥에서 arm64 이미지를 빌드해 VM의 K3s에 넣고, MediaMTX 설정 ConfigMap과 `k8s/overlays/oci`를 적용한 뒤 준비될 때까지 기다린다. 코드를 바꿀 때마다 이것만 다시 실행한다.
   ```bash
   deploy/k3s/deploy.sh
   ```
4. **(처음 한 번, 끝남) compose 데이터 옮기기** ([migrate-from-compose.sh](deploy/k3s/migrate-from-compose.sh)). compose 볼륨의 DB를 `pg_dump`해서 K3s DB에 복원하고, 미디어 파일을 `media-data` PVC 폴더로 복사한다. 2026-11-21에 실행했고 그 뒤 서버에서 Docker를 지워서 **다시 실행할 수 없다**(기록용). 옛 compose 볼륨과 그 사본은 옮긴 것을 확인한 뒤 지웠다.
   ```bash
   ssh -i ~/.ssh/oci_duse ubuntu@161.33.218.209 'bash -s' < deploy/k3s/migrate-from-compose.sh
   ```
5. **웹 빌드 올리기**, Caddyfile과 다운로드 페이지 (바꿨을 때만)
   ```bash
   (cd web && npm run build)
   rsync -az --delete -e "ssh -i ~/.ssh/oci_duse" web/dist/ ubuntu@161.33.218.209:/var/www/live.duse.dev/

   rsync -az -e "ssh -i ~/.ssh/oci_duse" deploy/Caddyfile deploy/download/index.html ubuntu@161.33.218.209:/tmp/
   ssh -i ~/.ssh/oci_duse ubuntu@161.33.218.209 'sudo cp /etc/caddy/Caddyfile /etc/caddy/Caddyfile.bak \
     && sudo install -m 644 /tmp/Caddyfile /etc/caddy/Caddyfile \
     && sudo install -D -m 644 /tmp/index.html /var/www/live-download/index.html \
     && sudo caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile && sudo systemctl reload caddy'
   ```

2026-11-21에 이 순서로 compose 운영에서 옮겼다. 옮긴 뒤 같은 주소에서 회원 1명, 방송 6개, 동영상 1개(재생 목록 `master.m3u8`과 썸네일 200)가 그대로 보였다.

| 파일 | 서버 위치 | 내용 |
| --- | --- | --- |
| `deploy/env.example` | `/opt/web-broadcast/.env` | 공인 IP와 비밀 값(DB 비밀번호, 동시 송출 비밀 값, 토스 테스트 키). 서버에서 만들고 git에 올리지 않는다 |
| `deploy/Caddyfile` | `/etc/caddy/Caddyfile` | `duse.dev` 메인 페이지, `live.duse.dev` 경로 분배, `s.duse.dev`(short-url) |
| `deploy/duse-dev/` | `/var/www/duse.dev/`, `/etc/duse/services.json`, `/usr/local/bin/duse-status-json`, systemd | `duse.dev` 첫 화면. 타이머가 1분마다 서비스 상태와 헬스 체크를 `status.json`에 쓴다. 서비스마다 K3s 네임스페이스의 Deployment가 원하는 수만큼 준비됐는지 본다(방송 서비스 `webbroadcast`, short-url `short-url`) |
| `deploy/egress-cap.sh`, `.service` | `/usr/local/sbin/`, `/etc/systemd/system/` | 나가는 트래픽을 29 Mbit/s로 묶어 OCI 무료 송신 한도(월 10 TB)를 넘지 않게 한다. 동시 시청은 10명 남짓이 한계다 |

방화벽은 두 겹이다. OCI 보안 목록(`Default Security List for duse-vcn`)과 서버 iptables(`/etc/iptables/rules.v4`) 모두에서 22, 80, 443/TCP와 8189/TCP·UDP만 연다. iptables에는 K3s Pod 대역 허용 규칙이 더해져 있다. 비용은 OCI 할당량 정책 `free-tier-only`(A1 2 OCPU·12GB, E2 Micro, 디스크 200GB만 허용)와 $1 예산의 1%·99% 지출 알림으로 막는다. K3s의 `LoadBalancer`는 VM 안의 ServiceLB가 처리하므로 OCI 유료 Load Balancer는 생기지 않는다.

누구나 가입해 동영상을 올릴 수 있으므로 업로드로 디스크(부트 볼륨 45GB)가 차지 않게 막는다. 파일 하나는 500MB, User 한 명이 올린 원본의 합계는 2GB까지이고, 받은 뒤 디스크 여유가 5GB보다 적어지는 업로드는 507로 거절한다([VideoUploadProperties](server/src/main/java/com/webbroadcast/video/VideoUploadProperties.java), 환경 변수 `VIDEO_UPLOAD_*`로 바꿀 수 있다). Caddy는 `/api/*` 요청 본문을 520MB에서 끊어 큰 요청이 Spring까지 가지 않게 한다.

### 확인 절차

```bash
ssh -i ~/.ssh/oci_duse ubuntu@161.33.218.209
kubectl get nodes                                   # 작업 전후 모두 노드 1대
kubectl -n webbroadcast top pods                    # Metrics Server가 CPU·메모리를 모으는지
kubectl -n webbroadcast get hpa -w                  # 터미널 1: CPU 사용률과 REPLICAS
kubectl -n webbroadcast get pods -l app=server -w   # 터미널 2: server Pod 생성·종료
```

맥에서 부하를 건다. 가상 사용자 20명이 로그인(bcrypt라 CPU를 많이 씀)과 방송·동영상 목록 조회를 반복하고, 동시에 처음 만든 회원의 세션 하나로 로그인 상태·방송·댓글을 조회해서 Pod가 바뀌는 동안 상태가 유지되는지 본다.

```bash
BASE_URL=https://live.duse.dev VUS=20 HOLD=3m load-test/run.sh
```

부하가 끝나면 5분(축소 안정화 기본값) 뒤부터 Pod가 줄어든다. 측정이 끝나면 테스트 회원을 지운다.

```bash
ssh -i ~/.ssh/oci_duse ubuntu@161.33.218.209 \
  'kubectl -n webbroadcast exec -i deploy/postgres -- psql -U webbroadcast -d webbroadcast' < load-test/cleanup.sql
```

### 확인한 결과 (2026-11-21, OCI A1 2 OCPU·12GB · K3s v1.37.2+k3s1 · 노드 1대)

**부하 조건**: 맥에서 `BASE_URL=https://live.duse.dev VUS=20 HOLD=3m load-test/run.sh`. 30초 동안 가상 사용자를 20명까지 올리고, 3분 동안 유지한 뒤 20초 동안 내렸다(총 3분 50초). 요청은 공개 HTTPS 주소 → Caddy → K3s Service로 들어갔다. 원본 기록은 [load-test/results/](load-test/results)에 있다.

- [oci-snapshots.txt](load-test/results/oci-snapshots.txt): 부하 전·중·직후·7분 뒤의 `kubectl get nodes`, `get hpa`, `top pods`, `get pods`
- [oci-server-scale.txt](load-test/results/oci-server-scale.txt): `k8s/watch-scale.sh`의 5초 간격 기록
- [oci-hpa-watch.txt](load-test/results/oci-hpa-watch.txt), [oci-pods-watch.txt](load-test/results/oci-pods-watch.txt): `kubectl get hpa -w`, `kubectl get pods -l app=server -w`
- [oci-k6.txt](load-test/results/oci-k6.txt): k6 요약

**노드**: 작업 전(설치 직후)과 측정 전·중·후 모두 `kubectl get nodes`가 `duse-server` **1대**(Ready)였다.

**지표 수집**: `kubectl top pods`가 부하 전부터 Pod별 CPU·메모리를 보여 줬다(K3s 내장 Metrics Server).

**전·중·후** (`kubectl get hpa -w`, 값이 바뀐 줄만):

```
시각(UTC)  CPU/목표     server Pod
04:00:43   4%/60%      2개      ← 부하 전, 최소 2개
04:01:31   98%/60%     2개      ← 부하 시작, 목표 초과 → 2개 추가 (Pending → ContainerCreating)
04:01:46   231%/60%    4개      ← REPLICAS 4 (최대)
04:02:37~40            4개 준비 ← 새 Pod 2개가 readinessProbe를 통과 (시작에 약 66초)
04:03:32   159%/60%    4개      ← 부하 중: Pod별 318m~471m
04:05:16   46%/60%     4개      ← 부하 끝
04:05:31   1%/60%      4개      ← 축소 안정화 시간(기본 5분) 동안 유지
04:10:16   2%/60%      2개      ← 부하가 끝난 지 약 5분 뒤 2개 종료, 최소 2개로 복귀
04:11:56   1%/60%      2개      ← 부하 끝나고 7분 뒤
```

부하 중 상태 확인 결과 (k6). 요청이 어느 Pod로 가든, Pod가 2개에서 4개로 늘어나는 중에도 같은 세션으로 로그인 상태·방송·댓글이 그대로 조회됐다:

```
✓ 로그인 유지   ✓ 방송 유지   ✓ 댓글 유지
✓ { kind:state }...: 100.00% 5796 out of 5796
✓ http_req_failed..: 0.00%   0 out of 13528
  http_req_duration: med=101ms  p(90)=572ms  p(95)=1.09s
  data_received....: 8.9 MB (나가는 트래픽. 무료 송신 한도 10TB에 비해 무시할 양)
```

**같은 주소의 데이터**: 측정 전후 모두 `https://live.duse.dev/api/broadcasts`와 `/api/videos`가 옮겨 온 방송 6개와 동영상 1개를 돌려줬고, 동영상의 `master.m3u8`과 썸네일이 200이었다. 측정 뒤 [load-test/cleanup.sql](load-test/cleanup.sql)로 테스트 회원 1명과 그 방송·댓글, 로그인 세션 1,933개를 지웠다.

관찰한 것:

- 새 server Pod가 준비되기까지 약 66초 걸렸다. CPU 2개를 부하가 이미 다 쓰는 중에 JVM이 시작돼서다. 그동안은 기존 2개가 요청을 받았고 실패한 요청은 없었다.
- 사용률이 4개로 늘어난 뒤에도 150~220%였다. 노드의 CPU가 2개뿐이라 Pod를 늘려도 전체 처리량에는 한계가 있다(위 "단일 VM 장애의 영향"). 그래도 최대값 4에서 멈춰서 다른 구성요소(DB, MediaMTX, 워커)가 쓸 CPU 요청량은 지켜졌다.
- 줄어들 때 종료된 Pod 2개의 상태가 `Error`로 표시됐다. Java가 종료 신호(SIGTERM)를 받고 끝나면 종료 코드가 143이라서 쿠버네티스가 `Error`로 보여 준다. 부하가 끝난 뒤라 이때 처리 중인 요청은 없었다.

## 데스크톱 앱 (Windows·macOS)

[app/](app/)의 Rust([Dioxus](https://dioxuslabs.com) 0.7) 클라이언트를 데스크톱 패키지로 만든다. 화면은 OS의 WebView(Windows: WebView2, macOS: WKWebView)로 그리고, API 요청은 Rust(reqwest)가 한다. 앱을 열면 첫 화면이 라이브 목록(`GET /api/broadcasts`)이고, 방송을 누르면 HLS(`/hls`, hls.js)로 재생한다. 동영상 목록과 재생(`/media`)도 된다.

| OS | 아키텍처 | 패키지 | 파일 |
| --- | --- | --- | --- |
| Windows 10·11 | x64 | 설치 파일 (.msi) | `WebBroadcast-windows-x64.msi` |
| macOS 11 이상 | arm64 (Apple Silicon) | 디스크 이미지 (.dmg, 끌어 놓아 설치) | `WebBroadcast-macos-arm64.dmg` |

### 빌드와 패키징

- [app/scripts/bundle-desktop.sh](app/scripts/bundle-desktop.sh): 지금 OS의 패키지를 만든다. 서버 주소(`https://live.duse.dev`)를 빌드할 때 앱에 넣는다(`API_ORIGIN`, `HLS_ORIGIN`, `WHIP_ORIGIN`, `WEB_ORIGIN`, [app/src/config.rs](app/src/config.rs)). 다른 서버를 쓰려면 `SERVER=https://… app/scripts/bundle-desktop.sh`.
  - macOS: `dx bundle`로 `.app`을 만들고, 번들 전체를 임시(ad-hoc) 서명한 뒤 `hdiutil`로 DMG를 만든다.
  - Windows: `dx bundle --package-types msi` (WiX). Git Bash에서 실행한다.
- [.github/workflows/desktop.yml](.github/workflows/desktop.yml): GitHub Actions의 `windows-latest`(x64)와 `macos-latest`(arm64) 러너에서 위 스크립트를 돌린다. `desktop-v*` 태그를 올리면 GitHub Release를 만들고 두 파일을 붙인다.
  ```bash
  git tag desktop-v0.1.0 && git push origin desktop-v0.1.0
  ```

필요한 것(로컬 빌드): Rust stable, `dx` 0.7.10(`cargo binstall dioxus-cli@0.7.10`), Windows는 Git Bash.

### 다운로드 페이지

https://live.duse.dev/download/ ([deploy/download/index.html](deploy/download/index.html))에 OS별 링크, 지원 아키텍처, 설치·실행 방법이 있다. 파일 링크(`/download/WebBroadcast-…`)는 Caddy가 GitHub Releases의 최신 릴리스 파일로 넘긴다(`releases/latest/download/…`). 그래서 새 태그를 올리면 페이지를 고치지 않아도 새 버전이 받아진다. 큰 파일이 OCI 송신 한도를 쓰지 않는 것도 이유다.

Windows 앱은 동영상(`/media`)을 hls.js로 받아서 앱 origin에서 교차 출처 요청이 된다. 그래서 Caddy가 `/media/*`에 `Access-Control-Allow-Origin: *`를 붙인다(동영상은 원래 누구나 볼 수 있다). macOS 앱은 동영상을 WKWebView 자체 재생으로 틀기 때문에 이 헤더가 필요 없고, 라이브(`/hls`)는 MediaMTX가 CORS를 허용한다.

### 설치·실행 확인 절차

1. 외부 네트워크(휴대폰 테더링 등)에서 https://live.duse.dev/download/ 를 연다. 주소창의 자물쇠(인증서 Let's Encrypt)를 확인한다.
2. 웹(https://live.duse.dev/broadcast)에서 방송을 하나 시작해 둔다. 동영상도 하나 이상 올라가 있어야 한다.
3. **Windows**: 페이지의 Windows 링크로 `.msi`를 받는다 → 실행 → SmartScreen이 뜨면 "추가 정보 → 실행" → 설치 → 시작 메뉴에서 `WebBroadcast`로 검색해 실행.
4. **macOS**: 페이지의 macOS 링크로 `.dmg`를 받는다 → 열어서 앱을 응용 프로그램 폴더로 끌어 놓기 → 실행 → 막히면 시스템 설정 → 개인정보 보호 및 보안 → "그래도 열기".
5. 각 OS에서 확인한다: (a) 첫 화면에 방송 목록이 보인다, (b) 방송 중인 방송을 눌러 영상과 소리가 나온다, (c) 동영상 목록에서 영상을 눌러 재생된다.

### 테스트 결과

빌드: GitHub Actions [desktop 실행](https://github.com/wiwi-git/web-broadcast-proj/actions/runs/44633769932)에서 두 패키지 모두 성공 (2026-11-20, 릴리스 [desktop-v0.1.0](https://github.com/wiwi-git/web-broadcast-proj/releases/tag/desktop-v0.1.0)).

<!-- TODO: Windows 실기기와 macOS 재생은 직접 확인한 뒤 채운다 -->

| 날짜 | OS (버전) | 아키텍처 | 네트워크 | 다운로드·설치 | 방송 목록 | 라이브 재생 | 동영상 재생 | 비고 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-11-23 | Windows (버전 미기록) | x64 | 외부에서 공개 주소로 접속 | 성공: GitHub Actions 실행 45114790629의 `.msi` 결과물 | 성공: 진행 중인 데모 방송 1개와 종료된 방송 목록, 메뉴 막대 없음 | 성공: 데모 방송(가짜 카메라) 영상 재생. 녹화의 소리가 매우 작아(최대 -45dB) 소리는 미확인 | 성공: "데모 동영상 1" 재생 | 설명영상용 앱 창 녹화(53초)로 확인. 방송하기 화면의 카메라·화면 공유는 미확인 |
| 2026-11-20 | macOS 15.7.9 | arm64 | 개발 PC 인터넷 (외부에서 공개 주소로 접속) | 성공: `live.duse.dev/download/` 링크로 받은 DMG, 서명 검증 통과 | 성공: 라이브 목록(방송 0개)과 동영상 목록이 운영 서버에서 표시됨 | 미확인 (방송 없음) | 상세 화면과 재생기 표시까지 확인, 재생은 미확인 | |
| 2026-11-23 | macOS 15.7.9 | arm64 | 개발 PC 인터넷 (외부에서 공개 주소로 접속) | 이번에는 확인하지 않음 (설치된 앱 실행) | 성공: 진행 중인 데모 방송 1개와 동영상 목록 표시 | 성공: 데모 방송(가짜 카메라)의 영상 재생. 소리는 녹화에 담기지 않아 미확인 | 성공: "데모 동영상 1" 재생 | 설명영상용 화면 녹화(40초)로 확인 |
