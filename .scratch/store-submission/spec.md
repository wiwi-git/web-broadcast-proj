# 스토어 심사 준비 (Microsoft Store 먼저, Google Play 다음)

Status: needs-triage

## 왜

과제: 데스크톱 앱을 서명해 "안전한 앱"으로 설치되게 하고, 안드로이드 앱도 마켓에 올린다.
Windows는 Microsoft Store에 MSIX로 내면 Microsoft가 서명해 줘서 비용이 들지 않는다.
포트폴리오용이라 비공개 대상(Private audience)으로 제출하고, 통과한 뒤 필요하면 판매 중지(Stop acquisition)로 바꾼다.
공개 범위를 좁혀도 심사 기준은 같다. 심사자가 실제로 앱을 실행해 본다.

## 지금 상태 (2026-11-27 확인)

- 서버 API: 인증(가입·로그인·me), 방송, 댓글, 동영상, 후원, 동시 송출만 있다.
- 신고, 차단, 관리자 역할, 계정 정지, 회원 탈퇴: **없음** (`UserService`에 "탈퇴는 없으므로"라는 주석이 있다)
- 개인정보처리방침, 이용약관 페이지: **없음** (웹 라우트는 `web/src/App.tsx`)
- 앱 오류 표시: `ApiError`를 `e.to_string()` 그대로 보여 준다(`app/src/pages/live.rs`). 서버가 죽거나 혼잡할 때 안내 문구와 다시 시도 버튼이 없다.
- 동시 방송·시청자 상한: 설정에서 찾지 못했다. 몰리면 어떻게 실패하는지 모른다.
- 후원 결제: 토스 `test_` 키. 심사자가 후원하면 테스트 결제로 끝난다.
- Windows 패키지: `.msi`(WiX). 스토어에 .msi를 내려면 유료 인증서 서명이 필요하다.

## 필요한 것

### A. 앱 기능 (심사 탈락 위험이 큰 순서)

누구나 방송, 채팅, 동영상을 올릴 수 있어서 스토어는 사용자 생성 콘텐츠(UGC) 앱으로 본다.

1. **콘텐츠 신고**: 방송, 동영상, 댓글(채팅)을 신고하는 버튼과 신고 사유. 서버에 신고를 저장한다. → 완료: `issues/01-content-report.md`
2. **사용자 차단**: 차단한 사용자의 댓글과 방송을 내 화면에서 숨긴다.
3. **운영자 조치**: 관리자 역할. 신고 목록 보기, 방송 강제 종료·숨김, 동영상·댓글 삭제, 계정 정지.
   - 화면 없이 API나 DB 작업으로 시작해도 된다. "신고하면 처리할 수단이 있다"는 것이 핵심이다.
   - → 완료: 웹 관리자 화면, 숨김, 기각, 계정 정지. `issues/02-moderation.md`
4. **회원 탈퇴**: 앱 안에서 계정과 데이터를 지운다. Google Play는 필수이고, Microsoft Store도 계정을 만드는 앱이면 권장한다.
   - Google Play는 **앱 안과 웹 페이지 양쪽**에서 탈퇴할 수 있어야 한다. 계정 정지나 비활성화는 삭제로 인정하지 않는다. 남겨 두는 데이터는 개인정보처리방침에 밝혀야 한다.
5. **서버 오류·혼잡 안내**: 네트워크 오류, 5xx, 혼잡일 때 사람이 읽을 문구와 다시 시도 버튼을 보여 준다. 앱이 멈추거나 빈 화면이 되면 안 된다.
   - (선택) 서버에 동시 방송 상한을 두고, 넘으면 "지금 방송이 많아요" 응답을 준다.
6. **후원 결제 처리 결정**: 다음 중 하나를 고른다.
   - (a) 스토어 빌드에서 후원을 숨긴다.
   - (b) "테스트 결제, 실제로 청구되지 않음"이라고 안내한다.
   - (c) 그대로 둔다.

### B. 문서·페이지

7. **개인정보처리방침** 페이지(duse.dev 웹 URL). 카메라, 마이크, 계정, 후원 내역을 다룬다. 앱 계정 화면에 링크한다. → 완료: `https://live.duse.dev/privacy`, `issues/06-terms-and-privacy.md`
8. **이용약관과 커뮤니티 가이드라인**. 금지 콘텐츠와 신고 후 처리 방식을 적는다. 가입 화면과 앱에 링크한다. → 완료: `https://live.duse.dev/terms`(제9조 커뮤니티 가이드라인, 제10조 신고와 운영 조치), 가입 동의 체크박스와 동의 기록

### C. 패키징 (Windows)

9. **MSIX 패키지**: `AppxManifest.xml`과 `makeappx`로 만든다. 들어갈 것은 다음과 같다.
   - `webcam`, `microphone` 권한
   - `runFullTrust`
   - `webbroadcast://` 프로토콜
   - Partner Center의 Publisher ID와 패키지 이름
   - 아이콘 크기별 이미지
   - GitHub Actions(`desktop.yml`)에 MSIX 빌드를 추가한다.

### D. 제출 준비 (사용자가 직접)

10. Partner Center 개인 개발자 계정과 본인 인증, 앱 이름 예약
11. 스토어 등록 정보: 설명, 스크린샷, 연령 등급 설문(IARC)에 "사용자 간 소통"과 "실시간 방송"을 체크
12. 심사 참고 사항: 테스트 계정, 사용 순서. 심사 기간에는 서버를 켜 두고 데모 방송을 틀어 둔다.

## Google Play와 겹치는 것

A-1~5, B-7~8은 Google Play에서도 그대로 필요하다(Play는 UGC 정책과 계정 삭제 요구가 더 엄격하다).
Play 전용으로 개발자 등록 $25, 신규 개인 계정의 비공개 테스트(테스터 12명, 14일), 데이터 보안 양식이 있다.

## 규정 문서 (2026-11-27 검색)

- Microsoft Store Policies 7.20 (2026-09-15): https://learn.microsoft.com/en-us/windows/apps/publish/store-policies
  - 7.20에서 11.11 연령 등급과 11.12 사용자 생성 콘텐츠가 바뀌었다. 변경 이력: https://learn.microsoft.com/en-us/windows/apps/publish/store-policies-change-history
- MSIX는 Microsoft가 서명하고, MSI/EXE는 직접 서명해야 한다: https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/code-signing-options
- 개인 개발자 무료 등록 (2025-09): https://blogs.windows.com/windowsdeveloper/2025/09/10/free-developer-registration-for-individual-developers-on-microsoft-store/
- Google Play 사용자 제작 콘텐츠: https://support.google.com/googleplay/android-developer/answer/9876937
- Google Play 개발자 프로그램 정책 전체: https://support.google.com/googleplay/android-developer/answer/17517561

## MS Store Policies 7.20에서 확인한 것 (2026-11-27, 발효 2026-10-22)

- 11.12 UGC: 이용약관과 커뮤니티 가이드라인을 **앱 안과 웹 양쪽**에 게시해야 한다. 앱 안에 신고 수단이 있어야 하고(사전 자동 탐지로 대신할 수도 있음), 신고된 콘텐츠를 검토해 삭제할 수 있어야 한다. Microsoft가 요청하면 내려야 한다. **차단 기능은 MS 조문에 없다**(Play에는 있다).
- 11.1: 등록 정보와 콘텐츠는 PEGI 12 / ESRB E10+ 이하여야 한다. 11.11.3: UGC처럼 등급이 더 높을 수 있는 콘텐츠는 로그인 등으로 사용자가 직접 선택해서 보게 한다.
- 10.3.1: 로그인이 필요하면 Notes for certification에 데모 계정을 적는다. 10.3.2: 심사 중에는 서버가 동작해야 한다.
- 10.2.4: 핵심 기능이 서버에 의존하면 **스토어 설명 맨 앞**에 밝힌다.
- 10.4.2: 멈추지 않고, 예외가 나도 계속 응답해야 한다(A-5).
- 10.5.1: 개인정보처리방침 URL은 필수이고, Win32 앱은 항상 필요하다. 10.5.2: 제3자(토스)로 개인정보를 보내면 명시적 동의와 철회 수단이 필요하다.
- 10.8.2: 대가 없는 후원은 제3자 결제(토스)로 받아도 된다. 후원 대가로 디지털 혜택을 주면 Store IAP를 써야 한다. 외부 브라우저 결제는 허용된다. Partner Center에 제3자 결제를 쓴다고 밝힌다. 10.8.3: 개인 계정은 핵심 기능에 금융 정보를 요구할 수 없다(후원은 선택 기능).
- **계정 삭제는 MS 조문에 없다**(Xbox 조항뿐). Play에서만 필수다.
- 10.2.9: MSI/EXE는 Trusted Root CA 서명, 무음 설치, HTTPS 버전별 URL이 필요하다. 이 조건 때문에 무료로 가려면 MSIX를 쓴다.

## 정할 것

- A-3 운영자 조치를 어디까지 만들지 (API만 / 웹 관리자 화면까지)
- A-6 후원을 어떻게 처리할지
- 작업 순서: 신고·차단 → 탈퇴 → 문서 → 오류 안내 → MSIX 순서를 제안한다
