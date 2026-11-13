# Web Broadcast

치지직 같은 웹 방송 플랫폼. 휴대폰 카메라로 라이브 방송을 하고 시청하는 기능과, 녹화된 동영상을 올리고 시청하는 기능이 있다. 회원은 방송과 동영상에 댓글을 남기고, 방송 중인 방송자에게 후원할 수 있다(토스페이먼츠 테스트 결제).
용어는 [GLOSSARY.md](GLOSSARY.md), 주요 결정은 [docs/adr/](docs/adr/)에 있다.

```
server/     Spring Boot 4 (Java 25) API 서버, 그리고 같은 코드로 띄우는 ffmpeg HLS 변환 워커
web/        React + Vite + TypeScript
mediamtx/   라이브 미디어 서버(MediaMTX) 설정
k8s/        로컬 쿠버네티스 배포와 오토스케일(HPA) 설정
load-test/  k6 부하 테스트
```

## 공개 범위와 권한

시청은 치지직처럼 누구나 할 수 있다. 무언가를 만들거나 바꾸려면 로그인해야 하고, 만든 사람만 고치거나 지울 수 있다. 모든 권한은 서버가 검사하며, 웹 화면의 버튼 숨김은 편의일 뿐이다.

| 누가 | 할 수 있는 일 |
| --- | --- |
| 누구나 (로그인 없이) | 라이브 목록·동영상 목록 보기, 라이브와 동영상 시청, 댓글 목록 보기, 방송에 완료된 후원 보기 (후원자 닉네임·금액·메시지) |
| 로그인한 회원 | 위의 모든 것, 동영상 업로드, 방송 시작, 댓글 쓰기, 방송 중인 남의 방송에 후원, 자기 후원 내역(보낸 후원·받은 후원) 보기 |
| 콘텐츠 소유자 (올린 사람·방송한 사람) | 동영상 제목·설명 수정과 삭제, 방송 제목 수정·종료·삭제, 방송 키 재발급 |
| 댓글 작성자 | 자기 댓글 수정·삭제. 콘텐츠 소유자라도 남의 댓글은 지울 수 없다 |
| 후원자 | 자기 후원의 결제 승인·취소 처리. 완료된 후원은 되돌릴 수 없다 |

- 한 회원이 동시에 진행할 수 있는 방송은 하나다.
- 처리 중이거나 처리에 실패한 동영상은 목록에 나오지 않지만, 주소를 알면 상세 정보는 볼 수 있다. 준비 상태의 방송도 목록에는 나오지 않는다.
- 회원의 아이디는 다른 사람에게 보이지 않고 닉네임만 보인다.
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

## 개발 환경 실행

필요한 것: JDK 25, Node 24, Docker, ffmpeg (`brew install ffmpeg`)

```bash
docker compose up -d          # PostgreSQL + MediaMTX
cd server && ./gradlew bootRun   # http://localhost:8080
cd web && npm install && npm run dev   # http://localhost:5173
```

웹 개발 서버는 `/api`, `/media` 요청을 Spring(8080)으로, `/hls`, `/whip` 요청을 MediaMTX(8888, 8889)로 넘긴다. 업로드된 파일은 `server/data/`에 쌓인다.

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

서버를 로컬 쿠버네티스에서 여러 Pod로 띄우고, CPU 사용률에 따라 Pod 수가 자동으로 늘고 줄게 한다.

### 구현 범위

| 요구 | 구현 |
| --- | --- |
| 컨테이너 이미지 | [server/Dockerfile](server/Dockerfile). API 서버와 변환 워커가 같은 이미지를 쓴다 |
| 여러 인스턴스 실행 | Docker Desktop Kubernetes의 Deployment: [server](k8s/app/server.yaml), [worker](k8s/app/worker.yaml) |
| 지표 기반 자동 조절 | [HPA](k8s/app/autoscaling.yaml). API 서버는 CPU 60% 기준 **2~4개**, 변환 워커는 CPU 70% 기준 **1~2개**. 최대값은 Docker Desktop 자원(CPU 10개, 메모리 8GB) 안에 들어오게 정했다. CPU 지표는 [metrics-server](k8s/metrics-server/kustomization.yaml)가 모은다 |
| 요청 분산 | `server` Service(LoadBalancer)가 준비된 서버 Pod들에 요청을 나눈다. Docker Desktop은 이것을 `localhost:8080`에 연다. Pod는 `/actuator/health/readiness`가 통과해야 요청을 받는다 |
| 상태를 인스턴스 밖에 | 로그인 세션(Spring Session JDBC), Broadcast(라이브 상태)·Video·Comment·Donation, 변환 대기열은 모두 [PostgreSQL](k8s/app/postgres.yaml)에 있다. 업로드한 영상과 HLS 파일은 모든 Pod가 함께 마운트하는 [media-data 볼륨](k8s/app/media-storage.yaml)에 있다. 서버 Pod는 메모리에 상태를 두지 않는다 |
| 부하 발생 | [load-test/load.js](load-test/load.js) (k6, Docker로 실행) |

함께 고려한 것:

- **주기 작업**: Broadcast 동기화와 오래된 Donation 정리는 서버 Pod마다 돈다. 둘 다 낙관적 잠금(`@Version`)으로 저장하므로, 여러 Pod가 같은 행을 동시에 바꿔도 하나만 반영되고 나머지는 다음 주기에 다시 맞춘다.
- **영상 변환**: 무거운 변환은 API 서버 밖의 워커 Pod가 맡는다([ADR-0004](docs/adr/0004-transcoding-worker-with-db-queue.md)). 그래서 업로드가 몰려도 API 서버가 느려지지 않고, 워커만 따로 늘어난다.
- **MediaMTX**: WebRTC 연결을 직접 들고 있어서 한 대로 둔다. 송출 허가는 `server` Service로 물어보므로 서버 Pod 수와 상관없이 동작한다.
- **줄어들 때**: Pod는 종료 신호를 받으면 처리 중인 요청을 마치고 내려간다(Spring Boot graceful shutdown). 워커는 변환 중이던 Video의 점유를 풀어서 다른 워커가 바로 이어받게 한다.
- **한계**: Docker Desktop은 노드가 하나라서 `ReadWriteOnce` 볼륨도 모든 Pod가 함께 쓸 수 있다. 노드가 여러 대면 `ReadWriteMany` 볼륨이나 S3 같은 오브젝트 스토리지가 필요하다.

### 실행 방법

필요한 것: Docker Desktop(설정 → Kubernetes → Enable Kubernetes), kubectl. k6는 Docker 이미지로 실행하므로 설치하지 않아도 된다.

```bash
docker compose down                 # compose로 띄운 것이 있으면 내린다 (8080 포트를 같이 쓴다)
kubectl config use-context docker-desktop
k8s/up.sh                           # 이미지 빌드 + metrics-server 설치 + 배포 (처음엔 몇 분 걸린다)
cd web && npm run dev               # 웹은 그대로 http://localhost:5173 (API는 localhost:8080 Service로 간다)
```

지울 때는 `k8s/down.sh`를 실행한다. DB와 영상 파일도 함께 지워진다.

### 환경변수

서버와 워커는 [k8s/app/config.yaml](k8s/app/config.yaml)의 ConfigMap과 Secret에서 값을 받는다. 아래 DB 계정은 로컬 개발용 예시 값이다.

| 이름 | 예시 | 설명 |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://postgres:5432/webbroadcast` | 공용 DB |
| `DB_USERNAME` / `DB_PASSWORD` | `webbroadcast` / `webbroadcast` | Secret `db-credentials` |
| `MEDIAMTX_API_URL` | `http://mediamtx-api:9997` | 방송 상태 동기화 |
| `MEDIA_STORAGE_ROOT` | `/data` | 공유 볼륨 마운트 위치 |
| `VIDEO_WORKER_ENABLED` | `false` (서버), 워커는 `SPRING_PROFILES_ACTIVE=worker` | 변환을 누가 맡는지 |
| `TOSS_CLIENT_KEY` / `TOSS_SECRET_KEY` | `test_ck_…` / `test_sk_…` | 선택. Secret `toss-keys`로 넣는다 |
| `LAN_IP` (`.env`) | `192.168.0.10` | 선택. 휴대폰 송출용. `k8s/up.sh`가 MediaMTX에 넘긴다 |

토스페이먼츠 테스트 키는 저장소에 넣지 않고 클러스터에 직접 만든다.

```bash
kubectl -n webbroadcast create secret generic toss-keys \
  --from-literal=TOSS_CLIENT_KEY=test_ck_... --from-literal=TOSS_SECRET_KEY=test_sk_...
kubectl -n webbroadcast rollout restart deployment/server
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

스케일이 바뀌는 동안 상태 확인 결과 (k6):

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
- 워커 메모리 한도가 1GiB일 때 JVM이 75%를 잡아서, 1080p 변환 중 ffmpeg가 메모리 부족으로 죽었다(종료 코드 137). 그래서 워커는 JVM 힙을 20%로 줄이고 한도를 2GiB로 올렸다.

## API

🔓 누구나, 🔑 로그인 필요, 👤 소유자 또는 작성자만. 🔑·👤 요청은 `X-XSRF-TOKEN` 헤더에 `XSRF-TOKEN` 쿠키 값을 담아야 한다.

| 메서드 | 경로 | | 설명 |
| --- | --- | --- | --- |
| `GET` | `/api/auth/me` | 🔓 | `{user}`. 로그인하지 않았으면 `user`가 null. CSRF 토큰 쿠키도 이때 받는다 |
| `POST` | `/api/auth/signup` | 🔓 | JSON `{username, password, nickname}`. 가입하면 바로 로그인된다 |
| `POST` | `/api/auth/login` | 🔓 | JSON `{username, password}` |
| `POST` | `/api/auth/logout` | 🔑 | 204 |
| `POST` | `/api/videos` | 🔑 | multipart: `file`, `title`, `description`. 201과 처리 중 상태의 Video |
| `GET` | `/api/videos?page=0` | 🔓 | 공개된 Video 목록, 최신순 20개씩 |
| `GET` | `/api/videos/{id}` | 🔓 | Video 하나. 공개 상태면 `playbackUrl`(HLS)과 `thumbnailUrl` 포함 |
| `PATCH` | `/api/videos/{id}` | 👤 | JSON `{title, description}` |
| `DELETE` | `/api/videos/{id}` | 👤 | 파일과 댓글도 함께 지운다. 204 |
| `GET` | `/media/videos/{id}/...` | 🔓 | HLS 플레이리스트·세그먼트·썸네일 |
| `POST` | `/api/broadcasts` | 🔑 | JSON `{title}`. 준비 상태의 Broadcast와 `streamKey`, `whipUrl`. 진행 중인 방송이 있으면 409와 `activeBroadcastId` |
| `GET` | `/api/broadcasts` | 🔓 | `live`(방송 중)와 `recentlyEnded`(최근 종료 20개) |
| `GET` | `/api/broadcasts/{id}` | 🔓 | Broadcast 하나. 방송 중이면 `playbackUrl`(LL-HLS) 포함 |
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
| `POST` | `/api/media-server/auth` | | MediaMTX 인증 훅 (내부용) |

오류는 ProblemDetail 형식이며 `detail`에 사용자에게 보여줄 문구가 담긴다. 로그인이 필요하면 401, 권한이 없거나 CSRF 토큰이 없으면 403이다.

## 테스트

```bash
cd server && ./gradlew test
```

`AccessControlTest`와 `DonationTest`는 Testcontainers로 PostgreSQL을 띄워 HTTP 요청으로 확인하므로 Docker가 실행 중이어야 한다. `AccessControlTest`는 공개 범위와 소유자·작성자 권한을, `DonationTest`는 후원의 결제 흐름을 본다. `DonationTest`는 토스페이먼츠 대신 가짜 결제 서비스로 승인·거절·결과 모름을 정한다.

## 배포할 때 주의

웹 서버(nginx 등)는 Vite 개발 서버처럼 `/hls/*`를 MediaMTX 8888로, `/whip/*`를 8889로 넘겨야 한다. 이때 앞의 접두사는 떼고 넘긴다. MediaMTX는 첫 HLS 요청을 `/live/...`로 리다이렉트하므로, 응답의 `Location` 헤더에도 `/hls`를 다시 붙여야 한다 (nginx `proxy_redirect`).
