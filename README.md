# Web Broadcast

치지직 같은 웹 방송 플랫폼. 휴대폰 카메라로 라이브 방송을 하고 시청하는 기능과, 녹화된 동영상을 올리고 시청하는 기능이 있다.
용어는 [GLOSSARY.md](GLOSSARY.md), 주요 결정은 [docs/adr/](docs/adr/)에 있다.

```
server/     Spring Boot 4 (Java 25) API 서버 + ffmpeg HLS 변환
web/        React + Vite + TypeScript
mediamtx/   라이브 미디어 서버(MediaMTX) 설정
```

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

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| `POST` | `/api/videos` | multipart: `file`, `title`, `description`. 201과 처리 중 상태의 Video를 돌려준다 |
| `GET` | `/api/videos?page=0` | 공개된 Video 목록, 최신순 20개씩 |
| `GET` | `/api/videos/{id}` | Video 하나. 공개 상태면 `playbackUrl`(HLS)과 `thumbnailUrl` 포함 |
| `GET` | `/media/videos/{id}/...` | HLS 플레이리스트·세그먼트·썸네일 |
| `POST` | `/api/broadcasts` | JSON `{title}`. 준비 상태의 Broadcast와 `streamKey`, `whipUrl`을 돌려준다 (`streamKey`는 이때 한 번만 받을 수 있다) |
| `GET` | `/api/broadcasts` | `live`(방송 중)와 `recentlyEnded`(최근 종료 20개) |
| `GET` | `/api/broadcasts/{id}` | Broadcast 하나. 방송 중이면 `playbackUrl`(LL-HLS) 포함 |
| `POST` | `/api/broadcasts/{id}/end` | `X-Stream-Key` 헤더 필요. 204 |
| `POST` | `/api/media-server/auth` | MediaMTX 인증 훅 (내부용) |

오류는 ProblemDetail 형식이며 `detail`에 사용자에게 보여줄 문구가 담긴다.

## 배포할 때 주의

웹 서버(nginx 등)는 Vite 개발 서버처럼 `/hls/*`를 MediaMTX 8888로, `/whip/*`를 8889로 넘겨야 한다. 이때 앞의 접두사는 떼고 넘긴다. MediaMTX는 첫 HLS 요청을 `/live/...`로 리다이렉트하므로, 응답의 `Location` 헤더에도 `/hls`를 다시 붙여야 한다 (nginx `proxy_redirect`).
