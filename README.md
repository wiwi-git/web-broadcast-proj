# Web Broadcast

치지직 같은 웹 방송 플랫폼. 휴대폰 카메라로 라이브 방송을 하고 시청하는 기능과, 녹화된 동영상을 올리고 시청하는 기능이 있다. 회원은 방송과 동영상에 댓글을 남길 수 있다.
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
| 누구나 (로그인 없이) | 라이브 목록·동영상 목록 보기, 라이브와 동영상 시청, 댓글 목록 보기 |
| 로그인한 회원 | 위의 모든 것, 동영상 업로드, 방송 시작, 댓글 쓰기 |
| 콘텐츠 소유자 (올린 사람·방송한 사람) | 동영상 제목·설명 수정과 삭제, 방송 제목 수정·종료·삭제, 방송 키 재발급 |
| 댓글 작성자 | 자기 댓글 수정·삭제. 콘텐츠 소유자라도 남의 댓글은 지울 수 없다 |

- 한 회원이 동시에 진행할 수 있는 방송은 하나다.
- 처리 중이거나 처리에 실패한 동영상은 목록에 나오지 않지만, 주소를 알면 상세 정보는 볼 수 있다. 준비 상태의 방송도 목록에는 나오지 않는다.
- 회원의 아이디는 다른 사람에게 보이지 않고 닉네임만 보인다.
- 로그인은 서버 세션 쿠키로 유지되며, 30분 동안 요청이 없으면 만료된다. 상태를 바꾸는 요청에는 CSRF 토큰이 필요하다 ([ADR-0003](docs/adr/0003-session-cookie-auth.md)).
- 영상 송출(WHIP)만은 로그인 세션이 아닌 방송 키로 허가한다. 미디어 서버가 브라우저 쿠키를 받지 못하기 때문이다. 방송 키는 소유자에게만 발급된다.

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
| `POST` | `/api/media-server/auth` | | MediaMTX 인증 훅 (내부용) |

오류는 ProblemDetail 형식이며 `detail`에 사용자에게 보여줄 문구가 담긴다. 로그인이 필요하면 401, 권한이 없거나 CSRF 토큰이 없으면 403이다.

## 테스트

```bash
cd server && ./gradlew test
```

`AccessControlTest`는 Testcontainers로 PostgreSQL을 띄워 공개 범위와 소유자·작성자 권한을 HTTP 요청으로 확인하므로 Docker가 실행 중이어야 한다.

## 배포할 때 주의

웹 서버(nginx 등)는 Vite 개발 서버처럼 `/hls/*`를 MediaMTX 8888로, `/whip/*`를 8889로 넘겨야 한다. 이때 앞의 접두사는 떼고 넘긴다. MediaMTX는 첫 HLS 요청을 `/live/...`로 리다이렉트하므로, 응답의 `Location` 헤더에도 `/hls`를 다시 붙여야 한다 (nginx `proxy_redirect`).
