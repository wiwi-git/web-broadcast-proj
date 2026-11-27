# 09. 이메일 인증 (메일 발송 포함)

Status: resolved (2026-11-27, 브랜치 `email-account`, 아직 커밋 전)

이메일 계정 묶음(08~13)의 두 번째 편이다. 나중에 영상으로 만들 때 이 문서를 대본 재료로 쓴다.

## 한 줄 요약

서버가 **SMTP로 메일을 보낼 수 있게** 하고, 가입하면 **인증 링크가 든 메일**을 보낸다. 링크를 누르면 이메일 인증이 끝난다.

## 정한 것 (사용자와 결정, 2026-11-27)

| 항목 | 결정 | 이유 |
|---|---|---|
| 메일 발송 | **표준 SMTP**로 만들고 업체는 나중에 정한다 | Gmail, OCI Email Delivery, Resend 등 무엇이든 주소·계정만 넣으면 된다 |
| 개발 환경 | **Mailpit**(가짜 메일 서버)으로 받아서 확인한다 | 진짜 메일을 보내지 않고 받은 메일을 웹 화면(http://localhost:8025)에서 본다 |
| 인증 방식 | **메일 속 링크 클릭** | 비밀번호 재설정(12)도 링크라서 방식이 같다. 앱 회원은 링크가 브라우저에서 열린다 |
| 링크 유효 | 24시간, 한 번만 | 오래된 메일이 새어도 쓸 수 없게 한다 |
| 다시 받기 | 1분에 한 번, 새 링크를 보내면 이전 링크는 무효 | 메일 폭탄을 막는다 |

## 메일 발송 기반

- `spring-boot-starter-mail`
- `application.properties`의 `spring.mail.*`는 `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_STARTTLS` 환경변수로 받는다. 타임아웃은 5초다.
- `mail.from`(보내는 사람), `mail.web-origin`(`WEB_ORIGIN`, 메일 속 링크의 웹 주소)
- `mail/MailService`
  - SMTP가 설정되지 않았으면 메일 대신 **서버 로그에 내용(링크)을 남긴다**(개발용).
  - 보내다 실패해도 예외를 던지지 않는다. 회원은 화면에서 다시 받기를 누를 수 있다.
- `compose.yaml`: `mailpit` 서비스(1025 SMTP, 8025 화면). `server/config/local.properties.example`에 Mailpit 설정 예시를 넣었다.
- 운영
  - `deploy/env.example`에 `MAIL_*`를 추가했다.
  - `deploy/k3s/secrets.sh`가 `mail` Secret을 만든다(`MAIL_HOST`가 있을 때만).
  - `k8s/base/server.yaml`은 이 Secret을 선택적으로 읽는다.
  - `k8s/base/config.yaml`에 `WEB_ORIGIN: https://live.duse.dev`를 넣었다.

## 일회용 링크 (인증과 재설정이 함께 쓴다)

- `V12__email_verification_and_tokens.sql`
  - `app_user.email_verified_at`
  - `email_token` 테이블: 회원, 용도(`VERIFY_EMAIL`/`RESET_PASSWORD`), **값의 SHA-256 해시**, 만든 시각, 만료 시각, 사용 시각. 회원이 탈퇴하면 함께 지워진다.
- `user/EmailToken`: 32바이트 무작위 값(base64url)을 만든다. 메일에는 원래 값을, DB에는 해시만 넣는다.
- `user/AccountMailService`
  - `sendVerification`: 가입 직후 보낸다.
  - `resendVerification`: 이미 인증했으면 400, 1분 안이면 429. 이전 링크를 무효화하고 새로 보낸다.
  - `verifyEmail(token)`: 해시로 찾고, 용도·만료·사용 여부를 확인한 뒤 사용 처리와 인증 시각을 기록한다.
  - 메일은 **DB 커밋이 끝난 뒤** 보낸다(`TransactionSynchronization.afterCommit`). 저장이 실패했는데 메일만 나가는 일을 막는다.

### 메일 내용

> 제목: [Web Broadcast] 이메일 주소를 인증해 주세요
>
> ○○님, Web Broadcast에 가입해 주셔서 감사합니다. 아래 링크를 누르면 이메일 인증이 끝나고, 동영상을 올리고 댓글을 쓸 수 있습니다. 링크는 24시간 동안 한 번 쓸 수 있습니다. (링크) 가입한 적이 없다면 이 메일은 무시해 주세요.

### API

| 요청 | 결과 |
|---|---|
| `POST /api/auth/verify-email {token}` (로그인 불필요) | 204. 잘못됐거나 만료·사용된 값이면 400 |
| `POST /api/auth/verify-email/resend` (로그인) | 204. 이미 인증했으면 400, 1분 안이면 429 |
| `GET /api/auth/me` | `emailVerified` 추가. 로그인 세션은 로그인할 때의 모습이라, 인증 여부는 **DB에서 다시 읽는다** |

## 웹·앱

- 웹
  - `components/VerifyBanner`: 인증 전 회원에게 머리글 아래 노란 띠로 "○○로 보낸 메일의 링크를 눌러 …"를 보여 준다. [메일 다시 받기] [인증했어요] 버튼이 있다.
  - `pages/VerifyEmailPage` (`/verify-email?token=`): 열리면 바로 인증을 요청한다. 개발 모드에서 두 번 요청되지 않게 막았다. "앱에서 가입했다면 앱으로 돌아가 '인증했어요'를 누르세요" 안내를 둔다.
  - 내 계정: 이메일 옆에 "인증됨"/"인증 전" 배지, 인증 전이면 다시 받기와 "인증했어요"
  - `AuthProvider.refresh()`: 로그인 상태를 다시 읽는다.
- 앱
  - 같은 띠(`VerifyBanner`)와 내 계정 화면
  - `Auth::refresh()`
  - 앱은 인증 링크를 열 화면이 없다. 링크는 메일 앱에서 브라우저로 열리고, 앱으로 돌아와 "인증했어요"를 누르면 상태를 다시 읽는다.

## 테스트: `AccountTest` (MailService를 가짜로 바꿔 보낸 내용에서 링크를 꺼낸다)

- 가입하면 메일 1통, `emailVerified=false`다. 링크 값으로 인증하면 204이고 `true`가 된다. 같은 값을 다시 쓰면 400, 다시 받기는 "이미 인증한 이메일입니다." 400이다.
- 가입 직후 다시 받기는 429다. 만든 시각을 2분 전으로 돌리면 204가 되고 새 링크가 온다. 이전 링크는 400, 새 링크는 204다.
- 만료된 값, 지어낸 값, 빈 요청은 모두 400이다.

## 확인한 것 (2026-11-27, 로컬, 실제 SMTP → Mailpit)

1. 웹에서 `mailtest1@example.com`으로 가입했다. 머리글 아래 인증 안내 띠가 나왔다.
2. Mailpit에 제목 "[Web Broadcast] 이메일 주소를 인증해 주세요", 보낸 사람 `no-reply@duse.dev`인 메일이 도착했다. 본문에 `http://localhost:5173/verify-email?token=…` 링크가 있었다.
3. 링크를 열자 "이메일 인증을 마쳤습니다"가 나오고 띠가 사라졌다.
4. 앱(Dioxus 웹 빌드)에서 인증하지 않은 두 번째 회원으로 가입하자 같은 띠가 나왔다.

## 남은 일

- **운영용 발송 업체를 정해야 한다.** 정하면 `.env`의 `MAIL_*`를 채우고, 개인정보처리방침 위탁 표의 "메일 발송 서비스(업체를 정하면 적습니다)" 줄을 실제 업체로 바꾼다. 도메인 메일(`no-reply@duse.dev`)로 보내려면 SPF·DKIM DNS 설정도 필요하다.
- 메일은 텍스트만 보낸다(HTML 디자인 없음).

## 영상 시나리오 메모

1. "서버가 메일을 보낸다"는 것: SMTP 한 줄 설명, 업체는 갈아 끼울 수 있게
2. Mailpit 화면: 가입 → 메일 도착 장면(가짜 메일 서버의 쓸모)
3. 링크 속 값은 해시로만 저장한다: DB를 봐도 링크를 만들 수 없다
4. 시연: 띠 → 메일 → 링크 → 띠가 사라짐, 앱에서는 "인증했어요"
