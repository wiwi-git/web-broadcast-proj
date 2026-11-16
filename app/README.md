# app — Dioxus 클라이언트

Web Broadcast의 화면을 Rust([Dioxus](https://dioxuslabs.com) 0.7)로 다시 만든 클라이언트입니다.
같은 코드로 데스크톱(Windows, macOS), 모바일(Android, iOS), 웹을 빌드합니다.
기존 React 웹(`../web`)과 같은 서버 API를 씁니다.

## 실행

먼저 서버(`../server`)와 MediaMTX를 띄웁니다. 그다음 이 폴더에서 아래 명령을 실행합니다.

```bash
dx serve --platform desktop          # macOS 창으로 실행 (Windows 앱은 Windows에서 빌드)
dx serve --platform web --port 8090  # 브라우저 (8080은 Spring 서버가 쓴다)
dx serve --platform android          # 안드로이드 에뮬레이터 (Android SDK/NDK 필요)
dx serve --platform ios              # iOS 시뮬레이터 (Mac + Xcode 필요)
dx bundle --platform <...> --release # 배포용 패키지
```

## 구조

```
app/
├── Cargo.toml        플랫폼별 feature(web/desktop/mobile)와 의존성
├── Dioxus.toml       앱 이름·번들 ID, 웹 개발 서버의 API 프록시
├── assets/main.css   ../web/src/index.css를 복사한 스타일
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
  쿠키 저장소는 메모리에만 있어서 앱을 다시 켜면 로그인이 풀립니다.
- **주소**: 앱에는 웹 같은 origin이 없습니다. 그래서 `config::resolve`가 `/api`·`/media`는 Spring(8080)으로,
  `/hls`는 MediaMTX HLS(8888)로, `/whip`은 MediaMTX WHIP(8889)으로 보냅니다.
  안드로이드 에뮬레이터에서는 개발 PC가 `10.0.2.2`로 보입니다. 실제 서버 주소는 빌드할 때
  `API_ORIGIN`, `HLS_ORIGIN`, `WHIP_ORIGIN` 환경 변수로 넣습니다.

## 서버 연동 테스트

서버를 띄운 뒤 아래 명령을 실행하면 데스크톱·모바일 경로를 확인합니다.
테스트는 매번 새 회원으로 가입 → 로그아웃 → 로그인 → 목록 조회를 거칩니다.

```bash
cargo test --no-default-features --features desktop -- --ignored
```

## 진행 상황

| 화면 | 상태 |
|---|---|
| 머리글, 라우팅, 로그인 확인, 로그인/로그아웃 | 완료 |
| 라이브 목록, 동영상 목록 | 목록만 표시 |
| 라이브 시청, 동영상 상세, 업로드, 방송하기, 회원가입, 후원, 후원 내역 | 자리만 있음 |

## 플랫폼별로 따로 확인해야 할 것

- **HLS 재생**: macOS·iOS WebView(WKWebView)는 `<video>`로 HLS를 바로 재생합니다.
  Windows(WebView2)와 Android WebView는 버전에 따라 바로 재생되지 않을 수 있습니다.
  그런 경우를 대비해 웹처럼 hls.js로 재생하는 방법을 준비해 둬야 합니다.
- **방송하기(카메라·마이크, WebRTC/WHIP)**: 앱마다 권한 선언이 필요합니다.
  iOS는 Info.plist의 카메라·마이크 사용 설명, Android는 매니페스트의 CAMERA·RECORD_AUDIO 권한입니다.
  WebView에서 `getUserMedia`를 허용하는지도 플랫폼마다 확인해야 합니다.
- **후원 결제(토스페이먼츠)**: JS SDK뿐이라 WebView 안에서 JS로 호출해야 합니다.
  결제 뒤 돌아오는 success/fail 주소를 앱에서 어떻게 받을지(딥링크 등) 정해야 합니다.
