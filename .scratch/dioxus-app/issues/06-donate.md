# 후원하기와 결제 결과

Status: resolved

웹 `DonatePage.tsx`, `DonationResultPage.tsx`를 옮긴다. 토스페이먼츠 JS SDK를 WebView에서 부르고, success/fail 주소를 앱 라우트로 받는다. 앱에서 결제 뒤 돌아오는 방법을 정해야 한다.

## Comments

웹 빌드에서 확인했다: 금액 검사, 토스페이먼츠 테스트 결제창 열기·닫기(USER_CANCEL → 후원 취소), 실패 주소(abandon), 성공 주소(approve 요청, 가짜 키는 결제 서비스가 거절), 값이 빠진 주소. 실제 테스트 결제로 DONE까지 가는 흐름은 하지 않았다.

데스크톱·모바일 앱은 아직 정하지 않았다: 결제창이 WebView 전체를 결제 서비스로 넘긴 뒤 successUrl(앱 origin + /donations/success)로 돌아오는데, 앱 origin(dioxus:// 또는 http://dioxus.index.html)을 결제 서비스가 받아 주는지, 돌아왔을 때 라우터가 그 주소를 읽는지 확인해야 한다. 안 되면 딥링크나 서버의 중계 페이지가 필요하다.
