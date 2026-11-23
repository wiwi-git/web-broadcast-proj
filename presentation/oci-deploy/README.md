# 도메인·OCI 배포와 데스크톱 앱 설명 영상 (치지직 만들기 ⑥)

`oci-deploy-presentation.mp4`는 약 6분 28초짜리 설명 영상이다. 다루는 내용은 아래와 같다.

- `duse.dev` 도메인 구매와 DNS 설정
- OCI 상시 무료 서버 준비
- Caddy와 docker compose로 한 배포
- Windows·macOS 데스크톱 앱 빌드

대본은 [script.md](script.md), 스펙은 [.scratch/oci-video/spec.md](../../.scratch/oci-video/spec.md)에 있다.

**기준 시점**은 ⑥ 당시의 docker compose 구성이다(커밋 `7b9a4aa`, `3c0982b`). 그 뒤에 옮긴 K3s와 오토스케일은 다음 영상에서 다룬다.

**개인정보**: 계정 이름·이메일·OCID·서버 IP·GitHub 사용자 이름은 화면과 대본에 넣지 않았다. 콘솔 캡처에서는 이 값들을 잘라 내거나 덮었다.

만드는 방식은 [오토스케일 영상](../autoscale/README.md)과 같다. Edge TTS로 나레이션을 만들고, HyperFrames HTML 장면으로 MP4를 렌더한다. 다른 점은 실제 사이트와 콘솔 캡처, macOS 앱 녹화를 쓴다는 것이다.

## 다시 만들기

`presentation/`에서 실행한다. 캡처·녹화·글꼴은 git에 없으므로 먼저 아래 "준비물"을 채운다.

1. 나레이션과 자막 타이밍을 만든다 (Edge TTS `ko-KR-SunHiNeural`)
   ```bash
   uv run --with edge-tts python oci-deploy/tools/build_narration.py
   ```
2. 장면을 만들고 렌더한다
   ```bash
   python3 oci-deploy/tools/build_composition.py
   cd oci-deploy/video && npx hyperframes check && npx hyperframes render -q standard -w 4 -o ../oci-deploy-presentation.mp4
   ```

한글 글꼴은 macOS의 Apple SD Gothic Neo를 쓴다. 그래서 macOS에서 렌더해야 같은 모양이 나온다.

## 준비물 (git 밖)

| 파일 | 만드는 법 |
| --- | --- |
| `video/assets/fonts/*.ttf` | 소개 영상 폴더의 `video/assets/fonts/`에서 복사 |
| `video/assets/screens/duse-home.png` 등 사이트 화면 7장 | `PUPPETEER_CORE=<puppeteer-core 경로> node oci-deploy/tools/capture-site.mjs`로 찍는다. 라이브 장면(`live-home-on`, `live-watch`)은 데모 방송이 켜져 있을 때 인자 `live`를 붙여서 찍는다 |
| `video/assets/screens/actions-run.png` | `node oci-deploy/tools/capture-actions.mjs <run id>`로 원본을 찍는다. 사용자 이름은 잘라 내고 덮는다 |
| `video/assets/screens/cloudflare-dns.png`, `oci-*.png` | 로그인한 콘솔 창을 macOS 창 캡처로 찍는다. 서버 IP·OCID·계정 이름·이메일을 잘라 내거나 덮는다 |
| `video/assets/media/macos-run.mp4` | QuickTime으로 녹화한 macOS 앱(앱 창만)을 30fps H.264로 변환 |
| `video/assets/media/windows-run.mp4` (선택) | [Windows 녹화 안내](../../.scratch/oci-video/windows-recording-guide.md). 이 파일을 넣고 1·2단계를 다시 실행하면 장면 6·7이 "확인 예정"에서 실제 실행 화면으로 바뀐다 |
| `demo-media/` | 데모 방송·동영상용 시험 화면(ffmpeg `testsrc2`). 데모 방송은 `tools/demo-broadcast.sh`로 띄운다. 가짜 카메라는 Chrome을 띄운 뒤 첫 방송에서 소리만 나가므로, 방송을 켰다 끈 뒤 다시 켠 두 번째 방송을 찍는다 |

## 장면

| # | 장면 | 화면 |
| --- | --- | --- |
| 0 | 도입 | duse.dev 첫 화면 → 라이브 시청 |
| 1 | 도메인과 DNS | Cloudflare Registrar, `.dev`의 HTTPS 강제, A 레코드 `@`·`*`, 프록시 끔, `dig` |
| 2 | OCI 서버의 기본 개념 | 리전 > 가용성 도메인 > 컴파트먼트 > VCN > 서브넷 > 인스턴스, 콘솔 캡처, 방화벽 두 겹(보안 목록·iptables) |
| 3 | 비용 안전장치 | 할당량 정책, 예산 알림, 송신 상한 |
| 4 | Caddy와 배포 | 자동 HTTPS, 경로별 프록시(Caddyfile), docker compose 운영 설정, 라이브·동영상 재생 |
| 5 | 도메인 하나, 여러 서비스 | duse.dev 서비스 카드, 서비스별 Caddy 블록 |
| 6 | Windows와 macOS 앱 | Dioxus, GitHub Actions 빌드, 다운로드 페이지, macOS 앱 녹화, Windows 자리 |
| 7 | 정리 | 전체 흐름, 확인한 것과 확인 예정 |

사실 확인 결과는 [FACT-CHECK.md](FACT-CHECK.md)에 있다.
