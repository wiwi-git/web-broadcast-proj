# app — Dioxus 클라이언트

Web Broadcast의 화면을 Rust([Dioxus](https://dioxuslabs.com) 0.7)로 다시 만든 클라이언트입니다.
같은 코드로 데스크톱(Windows, macOS), 모바일(Android, iOS), 웹을 빌드합니다.
기존 React 웹(`../web`)과 같은 서버 API를 씁니다.

## 실행

먼저 서버(`../server`)와 MediaMTX를 띄웁니다. 그다음 이 폴더에서 아래 명령을 실행합니다.

```bash
dx serve --platform desktop          # macOS 창으로 실행 (Windows 앱은 Windows에서 빌드)
dx serve --platform web --port 8090  # 브라우저 (8180은 Spring 서버가 쓴다)
dx serve --platform android          # 안드로이드 에뮬레이터 (Android SDK/NDK 필요)
dx serve --platform ios              # iOS 시뮬레이터 (Mac + Xcode 필요)
dx bundle --platform <...> --release # 배포용 패키지 (데스크톱·모바일)
dx bundle --platform web --release --profile web-release # 웹 배포용 패키지 (크기를 줄인 설정, Cargo.toml 참고)
```

## 구조

```
app/
├── Cargo.toml        플랫폼별 feature(web/desktop/mobile)와 의존성
├── Dioxus.toml       앱 이름·번들 ID, 웹 개발 서버의 API 프록시
├── assets/
│   ├── main.css          ../web/src/index.css를 복사한 스타일
│   ├── hls.light.min.js  HLS 재생 (hls.js)
│   └── broadcaster.js    방송하기의 카메라와 WHIP 송출 (WebView 안에서 실행)
└── src/
    ├── main.rs       진입점. 로그인 상태 context와 라우터를 띄운다
    ├── route.rs      화면 주소(Route enum). ../web/src/App.tsx와 같은 경로
    ├── config.rs     서버 주소. 서버가 준 상대 경로를 플랫폼에 맞는 절대 주소로 바꾼다
    ├── auth.rs       로그인 상태(Auth)와 RequireLogin
    ├── api/
    │   ├── client.rs 공통 HTTP 처리: 쿠키 세션, CSRF 헤더, ProblemDetail 오류
    │   └── mod.rs    API 함수와 응답 타입 (../web/src/api.ts와 대응)
    ├── components/   여러 화면이 쓰는 컴포넌트 (머리글 Shell 등)
    └── pages/        화면 하나당 컴포넌트 하나. 이름은 Route 항목과 같다
```

## 서버와 통신하는 방식

- **로그인**: 서버의 세션 쿠키 방식(ADR-0003)을 그대로 씁니다. 웹은 브라우저가 쿠키를 관리합니다.
  데스크톱·모바일은 `reqwest`의 쿠키 저장소가 세션 쿠키와 `XSRF-TOKEN`을 들고 있다가,
  상태를 바꾸는 요청에 `X-XSRF-TOKEN` 헤더를 붙입니다. 서버는 바꾸지 않아도 됩니다.
  쿠키는 앱 데이터 폴더에 저장해 앱을 다시 켜도 로그인이 유지됩니다 (서버 세션이 살아 있는 동안).
- **주소**: 앱에는 웹 같은 origin이 없습니다. 그래서 `config::resolve`가 `/api`·`/media`는 Spring(8180)으로,
  `/hls`는 MediaMTX HLS(8888)로, `/whip`은 MediaMTX WHIP(8889)으로 보냅니다.
  안드로이드 에뮬레이터에서는 개발 PC가 `10.0.2.2`로 보입니다. 실제 서버 주소는 빌드할 때
  `API_ORIGIN`, `HLS_ORIGIN`, `WHIP_ORIGIN` 환경 변수로 넣습니다.

## 서버 연동 테스트

서버를 띄운 뒤 아래 명령을 실행하면 데스크톱·모바일 경로를 확인합니다.
테스트는 매번 새 회원으로 가입 → 로그아웃 → 로그인을 거치고, 동영상 업로드도 확인합니다.
업로드 테스트에 쓸 짧은 영상 파일 경로를 `UPLOAD_TEST_FILE`로 넘깁니다 (없으면 업로드 테스트는 건너뜁니다).

```bash
UPLOAD_TEST_FILE=/path/to/short.mp4 cargo test --no-default-features --features desktop -- --ignored
```

## 진행 상황

웹(`../web`)의 화면을 모두 옮겼습니다. 작업 목록과 확인 기록은 `../.scratch/dioxus-app/`에 있습니다.

| 화면 | 확인 |
|---|---|
| 머리글(아이콘), 로그인, 회원가입, 로그아웃 | 웹 빌드, iOS 시뮬레이터 |
| 라이브 목록, 라이브 시청 (재생, 댓글, 후원 알림, 제목 변경·종료) | 웹 빌드 |
| 동영상 목록, 상세 (재생, 화질 선택, 수정·삭제, 댓글) | 웹 빌드, iOS 시뮬레이터 |
| 업로드 (진행률) | 웹 빌드, 데스크톱 경로는 서버 연동 테스트 |
| 방송하기 (카메라 미리보기, WHIP 송출, 종료) | 웹 빌드 (가짜 카메라) |
| 후원하기, 결제 결과, 후원 내역 | 웹 빌드 (토스페이먼츠 테스트 결제창 열기·닫기까지) |

## 플랫폼별로 따로 확인해야 할 것

- **HLS 재생**: `components/hls_player.rs`는 빌드마다 순서가 다릅니다.
  웹 빌드는 웹과 같이 hls.js(`assets/hls.light.min.js`, 1.7.4)를 먼저 씁니다 (화질 선택 가능).
  앱의 라이브(`/hls`)는 hls.js를 씁니다. macOS WKWebView의 자체 재생은 저지연 라이브를 첫 장면에서 멈춘 채
  따라가지 못했고, MediaMTX는 CORS를 허용합니다. 앱의 동영상(`/media`)은 `<video>`가 HLS를 바로 재생할 수 있으면
  (macOS·iOS) 그쪽을 씁니다. 서버의 `/media`가 CORS 헤더를 주지 않아 hls.js로는 앱 origin에서 받을 수 없기 때문이고,
  그래서 맥·iOS 앱의 동영상에는 화질 선택이 없습니다. Windows(WebView2)와 Android는 동영상도 hls.js를 쓰게 되므로
  서버 `/media`에 CORS를 열지 정해야 하고, 아직 확인하지 않았습니다.
- **방송하기(카메라·마이크, WebRTC/WHIP)**: 카메라와 송출은 `assets/broadcaster.js`가 WebView 안에서 합니다.
  권한 선언은 `Dioxus.toml`의 `[permissions]`에 있습니다 (iOS·macOS Info.plist, Android 매니페스트로 바뀝니다).
  **맥 앱에서는 카메라가 열리지 않습니다.** WebView에 카메라 장치가 보이지 않고(마이크는 보임), macOS에 권한 요청도
  가지 않습니다. 네이티브에서 카메라 권한을 먼저 요청하는 등의 처리가 필요합니다. iOS·Android는 실기기에서 확인해야 합니다.
- **업로드**: 데스크톱·모바일은 파일 선택 창이 준 경로의 파일을 reqwest로 조금씩 읽어 보냅니다.
  앱 창에서 파일 선택 창이 뜨는지(특히 iOS)는 아직 확인하지 않았습니다.
- **후원 결제(토스페이먼츠)**: 웹 빌드는 앱 안에서 결제창을 엽니다. 데스크톱·모바일 앱은 결제창을 앱 안에 띄울 수 없어서
  (Dioxus 앱은 http(s) 이동을 모두 기본 브라우저로 넘깁니다) 기본 브라우저에서 결제합니다(ADR-0005).
  앱이 후원을 만들고 결제 토큰을 받아 웹(`WEB_ORIGIN`, 기본 `localhost:5173`)의 `/checkout?token=…`을 엽니다.
  결제 화면은 로그인 없이 토큰으로 그 후원 하나만 결제하므로 브라우저 로그인과 상관없이 앱 계정으로 후원됩니다.
  결제가 끝나면 웹이 `webbroadcast://donations` 딥링크로 앱을 다시 열고 앱은 후원 내역으로 갑니다(`src/app_link.rs`).
  iOS·Android의 딥링크 수신은 확인하지 않았습니다.
- **로그인 유지**: 데스크톱·모바일은 API 서버로 보낼 쿠키를 앱 데이터 폴더의 `cookies` 파일에 적어 두고 다시 켤 때 읽습니다.
  서버 세션은 30분 동안 요청이 없으면 끝나므로(`spring.session.timeout`) 그 뒤에는 다시 로그인해야 합니다.
- **웹 빌드의 미디어 주소**: dx의 proxy는 경로 앞부분을 떼어 주지 못하므로 웹 빌드에서도 `/hls`, `/whip`은
  MediaMTX(8888, 8889)로 바로 보냅니다.
