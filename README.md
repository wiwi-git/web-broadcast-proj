# Web Broadcast

치지직 같은 웹 방송 플랫폼. 지금은 녹화된 동영상을 올리고(제목·설명 포함) 시청하는 기능만 있다.
용어는 [GLOSSARY.md](GLOSSARY.md), 주요 결정은 [docs/adr/](docs/adr/)에 있다.

```
server/   Spring Boot 4 (Java 25) API 서버 + ffmpeg HLS 변환
web/      React + Vite + TypeScript
```

## 개발 환경 실행

필요한 것: JDK 25, Node 24, Docker, ffmpeg (`brew install ffmpeg`)

```bash
docker compose up -d          # PostgreSQL
cd server && ./gradlew bootRun   # http://localhost:8080
cd web && npm install && npm run dev   # http://localhost:5173
```

웹 개발 서버는 `/api`, `/media` 요청을 8080으로 넘긴다. 업로드된 파일은 `server/data/`에 쌓인다.

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

오류는 ProblemDetail 형식이며 `detail`에 사용자에게 보여줄 문구가 담긴다.
