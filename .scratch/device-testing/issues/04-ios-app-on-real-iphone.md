# iOS 앱(Dioxus) 실기기 테스트

Status: ready-for-human

01~03은 휴대폰 **브라우저**로 웹을 쓰는 경우다. 이 이슈는 `app/`을 아이폰에 설치한 **앱**을 확인한다.

## 준비

1. Xcode에서 Bundle Identifier `com.webbroadcast.app`으로 빈 앱을 아이폰에 한 번 실행해 프로비저닝 프로파일을 만든다 (2026-11-18에 만듦, 2027-11-18 만료).
2. Docker를 켜고 MediaMTX가 휴대폰에 Mac 주소를 알리게 한다: `LAN_IP=<Mac LAN IP> docker compose up -d mediamtx`
3. 서버(`server`)와 같은 Wi-Fi에서 열리는 웹(`web-lan`, 결제 화면용)을 띄운다.
4. Mac 주소를 넣어 빌드·서명하고 설치한다 (`app/`에서):

```bash
H=http://<Mac LAN IP>; API_ORIGIN=$H:8180 HLS_ORIGIN=$H:8888 WHIP_ORIGIN=$H:8889 WEB_ORIGIN=$H:5173 \
  dx bundle --platform ios --device "iPhone" --codesign true --apple-team-id "<서명 인증서 이름>"
xcrun devicectl device install app --device <기기 ID> target/dx/web-broadcast-app/debug/ios/WebBroadcastApp.app
```

## 확인할 것

- [x] 앱이 뜨고 라이브 목록이 나온다 (2026-11-18, iPhone SE)
- [ ] 회원가입·로그인이 되고, 앱을 껐다 켜도 로그인이 유지된다
- [ ] 라이브 시청이 멈추지 않고 이어진다 (앱의 라이브는 hls.js)
- [ ] 동영상(VOD)이 재생된다 (앱의 동영상은 네이티브 재생)
- [ ] 업로드에서 파일 선택 창(사진 보관함)이 뜨고 업로드가 끝난다
- [ ] 방송하기에서 카메라·마이크 권한 요청이 뜨고 미리보기가 나온다 (맥 앱에서는 카메라가 잡히지 않았다)
- [ ] 방송 시작 후 PC 시청 화면에 영상이 나온다
- [ ] 후원하기를 누르면 Safari에서 결제 화면이 열리고, 결제를 마치면 앱의 후원 내역으로 돌아온다 (딥링크 `webbroadcast://donations`)
- [ ] 결제 화면에서 취소하고 "앱으로 돌아가기"를 누르면 앱으로 돌아온다

## Comments
