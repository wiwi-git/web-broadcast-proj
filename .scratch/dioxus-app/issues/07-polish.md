# 화질 선택, 머리글 아이콘, 로그인 유지

Status: resolved

hls.js로 재생할 때 화질 선택(웹 `HlsPlayer.tsx`). 머리글 아이콘(웹 `Icons.tsx`). 데스크톱·모바일에서 쿠키를 파일에 저장해 앱을 다시 켜도 로그인 유지.

## Comments

화질 선택(웹 빌드, hls.js), 머리글 아이콘(웹·iOS), 로그인 유지(iOS 시뮬레이터에서 앱 종료 후 다시 켜기)를 확인했다.

앱에서 hls.js를 쓰면 영상 조각 요청이 교차 출처가 되는데 서버 /media는 CORS 헤더를 주지 않는다. 그래서 앱은 자체 HLS 재생이 되면(WKWebView) 그쪽을 먼저 쓰고, 화질 선택은 웹 빌드와 hls.js를 쓰는 WebView에서만 나온다. Windows(WebView2)·Android는 hls.js를 쓰게 되므로 서버에 CORS를 열지, 앱이 미디어를 직접 받아 넘길지 정해야 한다.

맥 앱(2026-11-17): 자체 재생은 LL-HLS 라이브를 첫 장면에서 멈춘 채 따라가지 못했다. 앱에서도 라이브(/hls, MediaMTX는 CORS 허용)는 hls.js로 바꿨고, 맥에서 실시간 재생을 확인했다. 동영상(/media)은 자체 재생 그대로다. 로그인 유지, 업로드 파일 선택 창, 넓은 창 레이아웃도 맥에서 확인했다. 창 제목이 "Dioxus App"으로 나온다.
