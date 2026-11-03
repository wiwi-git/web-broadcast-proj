# 라이브 영상은 MediaMTX가 받고 Spring은 방송 상태만 관리한다

휴대폰 브라우저는 WebRTC(WHIP)로 MediaMTX에 송출하고, Viewer는 MediaMTX가 만든 LL-HLS를 hls.js로 본다(지연 약 2~4초). 대안은 브라우저 MediaRecorder 조각을 WebSocket으로 Spring에 보내 ffmpeg로 HLS를 만드는 방식이었다. 서버는 하나로 끝나지만 지연이 6~10초이고, 모바일 네트워크에서 끊기기 쉬우며, 브라우저마다 녹화 형식이 달랐다. MediaMTX는 나중에 OBS의 RTMP 송출도 설정만으로 받을 수 있다.

Spring은 미디어를 직접 다루지 않는다. MediaMTX의 HTTP 인증 훅으로 송출을 허가하고(Stream Key 확인), MediaMTX API를 주기적으로 조회해 Broadcast 상태(준비/방송 중/종료)를 맞춘다. 훅 대신 주기적 조회를 고른 이유는, 한쪽이 재시작되어 이벤트를 놓쳐도 다음 조회에서 상태가 저절로 맞춰지기 때문이다.

## Consequences

- 송출은 H.264로 해야 한다. HLS는 VP8/VP9를 담지 못하므로 브라우저 송출 코덱을 H.264로 고정한다.
- 상태 반영에 조회 주기(2초)만큼 지연이 생긴다.
- 휴대폰 브라우저는 HTTPS에서만 카메라를 열 수 있고, WebRTC 연결을 위해 MediaMTX가 휴대폰에서 닿는 주소(LAN IP)를 알아야 한다.
