# Web Broadcast v2 사실 검증

대상은 `presentation/web-broadcast-presentation-v2.mp4` (6분 25.83초)입니다. 14개 장면의 주요 프레임과 자막 타임라인을 대본·캡처 코드·서버·프런트엔드·설정·테스트 소스에 대조했습니다. MP4 음성과 생성된 나레이션 WAV는 14개 장면의 표본에서 같은 오디오임을 확인했습니다. 앱과 테스트를 새로 실행하거나 실기기 송출 및 결제 승인을 재현하는 단계는 이번 검증 범위에서 제외했습니다.

핵심 기능은 코드로 뒷받침됩니다. 송출 코덱의 설명에는 명확한 기술 오류가 있고, 쿠키·댓글·결제·재시작에는 구현이 보장하는 범위를 넓게 말한 부분이 있습니다.

## 공개 전 수정

### 01:50–01:58 · “HLS는 VP8이나 VP9을 담지 못한다”

**기술 오류.** 이 앱이 H.264만 협상하는 것은 맞습니다. `preferH264()`가 영상 코덱을 H.264로 필터링합니다. 다만 프로젝트에서 사용하는 MediaMTX는 HLS 출력 지원 코덱에 VP9을 명시합니다. 자막과 화면 배지의 `HLS는 VP8/VP9 불가`를 함께 수정하세요. 같은 주장이 소스 주석에도 있습니다. [송출 구현](../web/src/live/whip.ts#L46) · [MediaMTX HLS 공식 문서](https://mediamtx.org/docs/read/hls)

**권장 문구** “이 프로젝트는 WHIP 영상 송출 코덱을 H.264로 고정했습니다.”

### 01:58–02:10 · “미디어 서버는 브라우저 쿠키를 받지 못한다”

**인증 방식과 헤더 전달 여부가 섞였습니다.** 브라우저는 같은 출처의 상대 경로 `/whip/…`로 요청하고, Vite가 이를 MediaMTX로 프록시합니다. 이 프록시 설정에는 요청의 쿠키를 제거하는 처리가 없습니다. 구현이 확실히 보여주는 점은 Spring 인증 훅이 로그인 세션과 별개로 방송 키를 검증한다는 것입니다. 같은 화면의 `비트레이트 2.5Mbps 제한`도 브라우저가 인코딩 파라미터 설정을 받아들일 때 적용됩니다. [WHIP 프록시](../web/vite.config.ts#L19) · [인증 훅](../server/src/main/java/com/webbroadcast/web/broadcast/MediaServerAuthController.java#L32) · [비트레이트 설정](../web/src/live/whip.ts#L70)

**권장 문구** “방송 생성에는 로그인 세션을 사용하고, MediaMTX 송출 허가에는 방송 키를 사용합니다.” 비트레이트 배지를 유지하면 `지원 브라우저에서 최대 2.5Mbps 설정`으로 표기합니다.

### 02:11–02:37 및 06:02 · 방송 상태 동기화와 송출 복구

**상태 갱신은 구현되어 있습니다.** Spring은 작업 종료 후 2초를 두고 다시 조회합니다. 조회가 실패하면 해당 회차는 건너뜁니다. 조회가 성공했을 때 송출이 사라진 기존 `LIVE` 방송은 `ENDED`가 됩니다. “재시작해도 상태가 맞춰진다”는 DB 상태에 관한 설명으로 쓰면 정확합니다. [동기화 작업](../server/src/main/java/com/webbroadcast/broadcast/BroadcastSync.java#L35) · [방송 상태 전이](../server/src/main/java/com/webbroadcast/broadcast/Broadcast.java#L78)

**권장 문구** “MediaMTX 조회에 성공하면 실제 송출 상태를 DB에 반영합니다. 기존 방송의 송출이 끊긴 상태라면 종료로 바뀝니다.”

### 04:12 및 04:21–04:28 · 댓글 권한과 커서

**직접 수정·삭제 권한은 정확합니다.** 작성자 확인은 서버에서 합니다. 다만 콘텐츠를 삭제하면 DB 외래 키의 연쇄 삭제로 댓글도 함께 삭제됩니다. 커서는 이전 페이지의 마지막 댓글을 DB에서 다시 찾습니다. 그 댓글이 삭제되면 ‘잘못된 댓글 위치’ 오류가 나고 다음 페이지를 이어 읽을 수 없습니다. 새 댓글 추가로 인해 기존 목록의 오프셋이 밀리는 문제를 줄인다는 설명은 맞습니다. [댓글 서비스](../server/src/main/java/com/webbroadcast/comment/CommentService.java#L44) · [댓글 쿼리](../server/src/main/java/com/webbroadcast/comment/CommentRepository.java#L16) · [댓글 외래 키](../server/src/main/resources/db/migration/V3__add_users_ownership_comments.sql#L24)

**권장 문구** “댓글을 직접 수정하거나 삭제하는 요청은 작성자만 할 수 있습니다. 더 보기는 마지막으로 본 댓글보다 오래된 댓글을 불러오므로, 새 댓글이 추가돼도 기존 페이지 경계가 밀리지 않습니다.”

### 05:06–05:27 및 06:05 · 결제 재시도와 결과 복구

**같은 승인 요청에 같은 키를 쓰는 것은 사실입니다.** 서버는 승인 호출 전에 `paymentKey`를 저장하고, 토스에 `confirm-{orderId}`를 보냅니다. 타임아웃 시 앱은 `PENDING`을 유지하고 HTTP 503을 반환합니다. 같은 결제 정보로 다시 요청할 수 있습니다. 토스의 멱등성은 같은 API 키·주소·메서드·멱등키 조합에 적용되며 멱등키 유효 기간은 15일입니다. 영상은 이 재시도 흐름의 중복 승인 방지로 범위를 잡는 편이 정확합니다. [승인 처리](../server/src/main/java/com/webbroadcast/donation/DonationService.java#L87) · [토스 승인 요청](../server/src/main/java/com/webbroadcast/donation/TossPaymentsGateway.java#L53) · [토스 멱등키 공식 문서](https://docs.tosspayments.com/reference/using-api/authorization)

**별도 코드상 예외.** 토스가 실제로 승인했지만 앱이 응답을 받지 못한 뒤, 후원자가 같은 `paymentKey`에 다른 금액을 보내면 현재 코드는 `AMOUNT_MISMATCH`로 앱 상태를 `FAILED`로 바꿀 수 있습니다. 정리 작업은 `PENDING`만 조회하므로 그 건은 자동 조회 대상에서 빠집니다. 이는 코드에서 추적한 조건부 결과입니다. [금액 검사](../server/src/main/java/com/webbroadcast/donation/DonationService.java#L95) · [정리 작업 대상](../server/src/main/java/com/webbroadcast/donation/DonationService.java#L148)

**권장 문구** “승인 결과를 받지 못하면 결제 대기로 남겨 두고, 같은 승인 요청에는 동일한 토스 멱등키를 사용합니다. 오래된 대기 건 중 승인 요청 기록이 있는 건은 주문번호로 조회해 결과를 반영합니다.”

## 코드에서 확인한 주요 설명

| 구간 | 확인한 동작 |
|---|---|
| 00:19–01:17 | React·Vite, Spring Boot 4·Java 25, PostgreSQL 18 구성. JDBC 세션, 로그인·로그아웃, 웹 API의 CSRF 헤더. 세션 유지에는 만료 전이라는 조건이 있습니다. [빌드](../server/build.gradle.kts) · [인프라](../compose.yaml) · [세션 설정](../server/src/main/resources/application.properties) |
| 01:17–01:42 | 방송 생성 시 `PENDING`과 방송 키 반환, 진행 중 방송 충돌 시 409, 32바이트 난수 및 해시 저장. 동시 생성 충돌 후 활성 방송이 사라지면 `activeBroadcastId`가 null일 수 있습니다. [방송 서비스](../server/src/main/java/com/webbroadcast/broadcast/BroadcastService.java#L38) · [키 생성](../server/src/main/java/com/webbroadcast/broadcast/StreamKey.java) |
| 02:38–04:05 | 단일 스레드 변환, 원본 해상도에 맞춘 HLS, 별도 썸네일 추출, 변환 완료 후 공개, 재시작 시 재등록. 비로그인 라이브 시청과 종료 목록도 구현되어 있습니다. 화질 메뉴는 hls.js가 두 개 이상 화질을 읽었을 때 표시됩니다. [변환 구현](../server/src/main/java/com/webbroadcast/media/FfmpegVideoTranscoder.java) · [처리 큐](../server/src/main/java/com/webbroadcast/video/VideoProcessingQueue.java) · [재생기](../web/src/components/HlsPlayer.tsx) |
| 04:29–04:47 및 05:27–05:47 | 방송 중 후원 생성, 자기 방송 후원 제한, 금액 검증, 테스트 키 제한, 공개 완료 후원 조회와 3초 폴링. 팝업은 화면을 연 뒤 새로 조회된 완료 후원에 뜹니다. [후원 서비스](../server/src/main/java/com/webbroadcast/donation/DonationService.java#L68) · [테스트 키 설정](../server/src/main/java/com/webbroadcast/donation/TossPaymentsProperties.java) · [알림 훅](../web/src/live/useBroadcastDonations.ts) |
| 06:08–06:21 | 권한과 결제 테스트는 Testcontainers PostgreSQL과 MockMvc를 사용합니다. 결제 테스트는 모의 게이트웨이로 승인·거절·타임아웃 후 재시도를 다룹니다. [권한 테스트](../server/src/test/java/com/webbroadcast/web/AccessControlTest.java) · [결제 재시도 테스트](../server/src/test/java/com/webbroadcast/web/DonationTest.java#L225) |

## 데모에서 확인할 수 있는 범위

- **00:00 및 01:42, 03:37 라이브** — 캡처 스크립트는 데스크톱의 모바일 뷰포트와 가상 카메라를 사용해 WHIP 경로를 밟고, 비로그인 브라우저에서 시청 화면을 캡처합니다. 최종 MP4의 움직이는 플레이어 영상은 스크린샷 위에 원본 클립을 합성합니다. 실물 휴대폰 촬영이나 지연 측정처럼 보이지 않도록 `가상 카메라로 송출한 데모 · 재생 장면 편집`을 넣으면 정확합니다. [라이브 캡처](tools/capture/live.mjs#L12) · [영상 합성](tools/build_composition.py#L58)
- **03:56–04:05 화질** — 캡처는 앱의 화질 값을 720p로 실제 변경합니다. 최종 컷에서 열린 메뉴와 재생 영상은 합성입니다. 앱의 화질 선택 기능과 영상 연출을 구분할 수 있습니다. [화질 캡처](tools/capture/videos.mjs#L80) · [화질 장면 합성](tools/build_composition.py#L399)
- **04:45 및 05:28 후원** — 테스트 결제창과 `완료 처리한 샘플 후원` 라벨을 렌더에서 확인했습니다. DB에서 `DONE`으로 바꾼 5,000원은 알림 UI 데모입니다. 실제 테스트 결제의 승인 성공 여부는 이 컷에서 확인할 수 없습니다. [후원 캡처](tools/capture/live.mjs#L150) · [데모 데이터 설명](README.md)

## Claude Code에 붙여넣을 요청

> `presentation/FACT-CHECK-V2.md`의 ‘공개 전 수정’을 v2 영상 대본과 화면 카드에 반영해줘. 특히 HLS의 VP9 불가 문장과 쿠키 수신 불가 문장을 고치고, 댓글 커서·방송 상태 동기화·결제 멱등성의 범위를 문서의 권장 문구에 맞춰줘. 가상 카메라 데모 표기를 추가하고 후원 샘플 라벨은 유지해줘. 재렌더 후 각 항목의 새 타임스탬프를 알려줘.
