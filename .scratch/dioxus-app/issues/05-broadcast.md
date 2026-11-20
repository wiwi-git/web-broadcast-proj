# 방송하기

Status: resolved

웹 `BroadcastPage.tsx`를 옮긴다. 카메라·마이크 미리보기, 방송 시작(WHIP 송출), 제목 변경, 종료. iOS Info.plist·Android 매니페스트 권한이 필요하다. 시뮬레이터에는 카메라가 없으므로 실기기 확인 항목을 남긴다.

## Comments

웹 빌드에서 가짜 카메라(캔버스 + 무음 오디오)로 송출 → LIVE → 다른 탭 시청 재생 → 종료, 이전 방송 막힘·종료, 화면 이탈 시 정리를 확인했다. Dioxus.toml에 카메라·마이크 권한을 선언했다. 앱 WebView(WKWebView, WebView2, Android WebView)에서 getUserMedia가 열리는지는 실기기에서 확인해야 한다 (시뮬레이터에는 카메라가 없다).

맥 앱(2026-11-17): 카메라가 열리지 않는다. WebView의 장치 목록에 마이크만 있고 카메라(videoinput)가 없으며 getUserMedia({video:true})도 OverconstrainedError다. macOS TCC 로그에 이 앱의 카메라 요청이 없다. WebView가 카메라 권한을 묻지 않는 것으로 보여, 네이티브에서 먼저 권한을 요청하는 등의 처리가 필요하다.
