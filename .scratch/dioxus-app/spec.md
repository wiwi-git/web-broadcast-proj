# Dioxus 앱: 웹과 같은 기능 갖추기

`app/`(Dioxus)에 웹(`web/`)에는 있지만 앱에는 없는 기능을 옮긴다. 화면 구성과 문구, CSS class는 웹과 같게 둔다.
서버는 바꾸지 않는다.

## 이미 있는 것

라이브 목록, 동영상 목록·상세(재생, 수정·삭제, 댓글), 로그인·로그아웃.

## 만들 것 (순서대로)

| # | 일 | 웹 원본 |
|---|---|---|
| 01 | 회원가입 | `pages/SignUpPage.tsx` |
| 02 | 라이브 시청: 재생, 실시간 댓글, 후원 알림, 방송자 제목 변경·종료 | `pages/LiveWatchPage.tsx`, `components/LiveComments.tsx`, `components/DonationAlert.tsx` |
| 03 | 후원 내역: 보낸 후원·받은 후원 | `pages/DonationsPage.tsx` |
| 04 | 동영상 업로드와 진행률 | `pages/UploadPage.tsx` |
| 05 | 방송하기: 카메라·마이크, WHIP 송출 | `pages/BroadcastPage.tsx` |
| 06 | 후원하기(토스페이먼츠)와 결제 결과 화면 | `pages/DonatePage.tsx`, `pages/DonationResultPage.tsx` |
| 07 | 화질 선택, 머리글 아이콘, 앱을 다시 켜도 로그인 유지 | `components/HlsPlayer.tsx`, `components/Icons.tsx` |

순서는 쉬운 것과 앞 일이 쓰는 것을 먼저 둔다.
- 01은 기존 로그인 화면을 거의 그대로 쓴다.
- 02의 댓글·후원 조회는 03, 06에서 다시 쓴다.
- 04~06은 플랫폼 기능(파일, 카메라, 외부 결제)이 필요해서 뒤에 둔다.

## 확인 방법

웹 빌드(`dx serve --platform web`)로 먼저 확인하고, 화면이 달라질 수 있는 것은 iOS 시뮬레이터로도 본다.
카메라·결제처럼 시뮬레이터로 확인할 수 없는 것은 해당 이슈에 남긴다.
