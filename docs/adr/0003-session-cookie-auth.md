# 로그인은 DB에 저장하는 서버 세션과 쿠키로 유지하고, 송출만 Stream Key로 인증한다

로그인 상태는 Spring Security 세션으로 유지한다. 세션은 Spring Session JDBC로 PostgreSQL에 저장하고, 브라우저에는 HttpOnly 쿠키로 세션 ID만 준다. 대안은 JWT였다. JWT를 쓰면 서버가 상태를 갖지 않지만, 로그아웃을 즉시 반영하려면 결국 차단 목록이 필요하다. 또 토큰을 JavaScript가 읽을 수 있는 곳에 두면 XSS에 노출된다. 웹과 API가 같은 origin에서 동작하므로 쿠키 방식이 가장 단순하다. 세션을 메모리가 아닌 DB에 두면 서버를 재시작해도 로그인이 풀리지 않는다.

MediaMTX의 인증 훅에는 브라우저 쿠키가 전달되지 않는다. 그래서 영상 송출만은 지금처럼 Stream Key로 허가한다(ADR-0002). Stream Key는 Owner에게만 발급되고, 종료·수정·삭제는 세션의 User가 Owner인지로 판단한다.

## Consequences

- 쿠키를 쓰므로 CSRF 방어가 필요하다. 서버는 `XSRF-TOKEN` 쿠키로 토큰을 주고, 웹은 상태를 바꾸는 요청마다 `X-XSRF-TOKEN` 헤더로 돌려보낸다.
- 세션은 마지막 요청 뒤 30분 동안 요청이 없으면 만료된다.
- 다른 origin에서 API를 부르는 클라이언트(모바일 앱 등)가 생기면 토큰 방식을 따로 고민해야 한다.
