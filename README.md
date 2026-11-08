# Web Broadcast

치지직 같은 웹 방송 플랫폼. 휴대폰 카메라로 라이브 방송을 하고 시청하는 기능과, 녹화된 동영상을 올리고 시청하는 기능이 있다. 회원은 방송과 동영상에 댓글을 남기고, 방송 중인 방송자에게 후원할 수 있다(토스페이먼츠 테스트 결제).
용어는 [GLOSSARY.md](GLOSSARY.md), 주요 결정은 [docs/adr/](docs/adr/)에 있다.

```
server/     Spring Boot 4 (Java 25) API 서버 + ffmpeg HLS 변환
web/        React + Vite + TypeScript
mediamtx/   라이브 미디어 서버(MediaMTX) 설정
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
docker compose --profile app up -d --build   # PostgreSQL + 서버(ffmpeg 포함)
```

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
