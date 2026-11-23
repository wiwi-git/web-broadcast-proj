# 도메인·OCI 배포 영상 사실 확인

검토일: 2026-11-23.

## 기준

| 항목 | 값 |
| --- | --- |
| 대본 | [script.md](script.md) |
| 자막 원본 | `video/timeline.json` |
| 장면 | `video/compositions/*.html` |
| ⑥ 시점 배포 설정 | 커밋 `7b9a4aa`(`compose.prod.yaml`), `3c0982b`(`deploy/Caddyfile`) |

화면과 터미널 출력은 2026-11-23의 운영 서버(K3s로 옮긴 뒤)에서 찍었다.

## 판정

**사실과 다른 주장은 없다.** 다만 지금 서버에서 찍은 증거로 ⑥ 시점을 설명하는 곳이 있어서 아래에 적는다.

| 위치 | 주장 | 근거 | 비고 |
| --- | --- | --- | --- |
| 1번 | Cloudflare Registrar는 원가 판매, DNS 무료 | Cloudflare Registrar 정책 | 가격은 화면에 넣지 않았다 |
| 1번 | `.dev`는 HTTPS만 접속된다 | `.dev`는 HSTS preload 목록에 최상위 도메인 단위로 들어 있다 | |
| 1번 | `*`는 도메인 자체(`@`)를 포함하지 않는다 | DNS 와일드카드 규칙 | DNS 캡처에 `@`·`*` 외에 `s.duse.dev`·`tools.duse.dev`가 함께 보인다. ⑥ 뒤에 추가한 레코드다 |
| 1번 | 프록시를 끈 이유: WebRTC 미디어, 무료 요금제 약관 | WebRTC UDP 8189는 Cloudflare HTTP 프록시를 지나지 않는다. 약관은 CDN으로 영상을 대량 전달하는 것을 제한한다 | |
| 1번 | 어떤 서브도메인을 조회해도 같은 주소 | `dig +short` 3건, `tools/terminal-captures.json` | IP는 `<서버 IP>`로 바꿔 보여 준다 |
| 2번 | 도쿄 리전의 가용성 도메인은 하나 | 콘솔 인스턴스 상세 `AD-1` | |
| 2번 | 1 OCPU·6GB로 만들었다 | 생성 당시 "Out of capacity" 오류를 겪고 1/6으로 만든 기록(세션 기록) | 셰이프 캡처는 지금 값(2 OCPU·12GB)을 보여 준다. 화면 자막으로 이 차이를 밝혔다(스펙 D6) |
| 2번 | 보안 목록에 22·80·443·8189(TCP/UDP) | `oci-security-list.png` | |
| 3번 | 송신 상한 29 Mbit/s면 한 달 약 9.7TB | 29e6 / 8 × 31일 × 86,400초 = 9.71TB | |
| 4번 | Let's Encrypt 인증서, HTTP→HTTPS 308, www→본래 주소 301 | `openssl`·`curl` 출력(`terminal-captures.json`) | 지금 서버에서 찍었다. Caddy 설정의 해당 부분은 ⑥ 때와 같다(`3c0982b`의 `www.duse.dev` 블록) |
| 4번 | 경로별 프록시, `/api/internal/*` 404, Location에 `/hls` 다시 붙이기 | `3c0982b:deploy/Caddyfile` | 영상의 Caddyfile은 주석과 `encode`를 뺀 같은 내용이다 |
| 4번 | 저장소 배포 안내는 원래 nginx 기준 | `7b9a4aa^:README.md` 287·289행 "웹 서버(nginx 등)", "nginx `proxy_redirect`" | |
| 4번 | DB는 밖으로 열지 않고, 내부 포트는 127.0.0.1에만 | `7b9a4aa:compose.prod.yaml` | `nc` 결과(5432·8180·8888·8889·9997 닫힘)는 지금 서버에서 찍었다 |
| 4번 | Docker가 연 포트는 iptables INPUT 규칙을 우회한다 | Docker는 DNAT 후 FORWARD 체인으로 보낸다 | |
| 5번 | 새 서비스는 서브도메인과 Caddy 블록 하나 | 지금 `deploy/Caddyfile`의 `s.duse.dev` 블록 | Short URL은 ⑥ 뒤의 다른 프로젝트이고, 영상에서도 다른 프로젝트라고 말한다 |
| 5번 | 켜고 끄는 명령 | 사용자 Mac의 `live-on`·`live-off`·`live-status` | 명령의 내부 구현은 ⑥ 뒤에 K3s용으로 바뀌었다. 영상은 이름만 보여 준다 |
| 6번 | Dioxus 하나로 Windows·macOS, WebView2·WKWebView | README "데스크톱 앱" | |
| 6번 | `desktop-v*` 태그로 `.msi`·`.dmg`를 빌드해 Release에 올린다 | `.github/workflows/desktop.yml`, 실행 44718286700(`desktop-v0.1.3`) | |
| 6번 | 파일은 GitHub Releases에서 받는다 | 다운로드 페이지 링크 | |
| 6번 | macOS 앱으로 라이브·동영상 재생 | `macos-run.mp4`(2026-11-23 녹화). README 기기 확인 표 | 녹화에 소리가 없어 라이브 소리는 확인하지 않았다 |
| 6·7번 | Windows에서도 같은 앱으로 라이브·동영상 재생 | `windows-run.mp4`(2026-11-23 녹화, Actions 실행 45114790629의 `.msi`) | 메뉴 막대가 없어진 것도 녹화에 보인다. 녹화의 소리가 매우 작아 라이브 소리와 방송하기 화면의 카메라·화면 공유는 확인하지 않았다 |

## 개인정보 점검

| 항목 | 확인 방법 | 결과 |
| --- | --- | --- |
| 사이트 화면 | 로그인하지 않은 시청자로 찍고, 화면 글자를 함께 출력해 확인 | 보이는 것은 데모 계정 닉네임과 공개 콘텐츠뿐이다 |
| Cloudflare 캡처 | 직접 확인 | 이메일·계정 ID가 든 사이드바와 주소창은 잘라 냈다. IP 세 개는 덮었다 |
| OCI 캡처 | 직접 확인 | OCID, 컴파트먼트(테넌시 이름), 공인 IP를 덮었다. 보안 목록 화면에는 가릴 값이 없다 |
| Actions 캡처 | 직접 확인 | 저장소 경로가 든 머리말은 잘라 냈다. "pushed" 앞의 사용자 이름과 아바타는 덮었다 |
| macOS·Windows 녹화 | 직접 확인 | 앱 창만 담겼다. 메뉴 막대·알림·사용자 이름은 없다 |
| 원본 캡처 | | 가리기 전 원본은 모두 지웠다 |

남은 노출이 하나 있다. 다운로드 페이지와 동영상 목록의 썸네일에는 개인정보가 없다. 다만 사이트 자체(다운로드 링크)에서는 GitHub 저장소 주소를 알 수 있다.

## 공식 문서 재확인 (2026-11-23)

| 주장 | 결과 | 출처 |
| --- | --- | --- |
| Cloudflare Registrar는 원가 판매·갱신 | 맞다. 레지스트리 가격 그대로 갱신 | [Cloudflare Registrar 문서](https://developers.cloudflare.com/registrar) |
| `.dev`는 HTTPS만 | 맞다. TLD 전체가 HSTS preload 목록에 있다 | [Google Security Blog](https://security.googleblog.com/2017/09/broadening-hsts-to-secure-more-of-web.html) |
| 프록시는 UDP(WebRTC)를 통과시키지 못한다 | 맞다. 프록시는 HTTP/HTTPS만, UDP는 Spectrum이 따로 필요 | [Cloudflare Proxy status](https://developers.cloudflare.com/dns/manage-dns-records/reference/proxied-dns-records) |
| 약관이 영상 대량 전달을 제한 | 맞다. 다만 무료만이 아니라 **엔터프라이즈가 아닌 모든 요금제**가 대상이다(Stream·R2 같은 유료 서비스를 쓰라는 조항) | [Service-Specific Terms](https://www.cloudflare.com/service-specific-terms-application-services) |
| 도쿄 리전은 가용성 도메인 1개 | 맞다 | [OCI Regions and Availability Domains](https://docs.oracle.com/iaas/Content/General/Concepts/regions.htm) |
| 상시 무료 송신 10TB/월 | 맞다 | [Always Free Resources](https://docs.oracle.com/en-us/iaas/Content/FreeTier/resourceref.htm) |
| A1 상시 무료 합계 | 2026년에 4 OCPU·24GB → 2 OCPU·12GB(월 1,500 OCPU시간·9,000GB시간)로 줄었다. 영상은 합계를 말하지 않아 영향이 없다. 스펙의 옛 값은 고쳤다 | [Always Free Resources](https://docs.oracle.com/en-us/iaas/Content/FreeTier/resourceref.htm), [Oracle 커뮤니티 공지](https://community.oracle.com/customerconnect/discussion/970310/oci-always-free-updated-ampere-a1-compute-allocation) |
| OCI Ubuntu 이미지는 22번만 열려 있고 iptables를 쓴다 | 맞다(공식 문서에는 Ubuntu 기본 규칙이 없고, Oracle 개발자 블로그·커뮤니티 기준) | [Oracle 개발자 블로그](https://blogs.oracle.com/developers/post/enabling-network-traffic-to-ubuntu-images-in-oracle-cloud-infrastructure) |
| Caddy가 Let's Encrypt 인증서를 받아 자동 갱신 | 대체로 맞다. Caddy의 기본 발급처는 Let's Encrypt와 ZeroSSL(예비)이다. 이 서버의 실제 인증서는 Let's Encrypt(YE2)다 | [Caddy Automatic HTTPS](https://caddyserver.com/docs/automatic-https) |
| Docker가 연 포트는 iptables INPUT을 우회 | 맞다. nat 테이블에서 DNAT 후 FORWARD로 간다 | [Docker Packet filtering and firewalls](https://docs.docker.com/engine/network/packet-filtering-firewalls/) |
| Let's Encrypt `YE2` | 2025년 발표한 Generation Y 중간 인증서다 | [Let's Encrypt Gen Y](https://letsencrypt.org/2025/11/24/gen-y-hierarchy) |
| Dioxus 데스크톱은 OS 웹뷰(wry)를 쓴다 | 맞다. Windows는 WebView2, macOS는 WKWebView | [dioxus-desktop 0.7.10](https://docs.rs/crate/dioxus-desktop/0.7.10/source/README.md) |
