# iOS의 Viewer는 라이브를 WebRTC(WHEP)로 본다

Viewer는 MediaMTX가 만든 LL-HLS로 본다(ADR-0002). 그런데 송출이 WebRTC(WHIP)라 소리가 Opus로 들어오고, MediaMTX는 그 소리를 그대로 HLS에 담는다. iOS는 Opus가 든 HLS를 재생하지 못한다. 아이폰 앱(iOS 26.6)에서 hls.js는 Media Source가 디코딩에 실패해 다시 붙기를 반복했고(`isTypeSupported('audio/mp4; codecs="opus"')`가 false), iOS 자체 재생은 "Media failed to decode"(코드 3)로 멈췄다. 아이폰 Safari로 웹을 볼 때도 같다.

그래서 이 기기가 Opus를 HLS로 재생하지 못하면 MediaMTX에서 WebRTC(WHEP)로 직접 받는다. 서버는 방송 중인 Broadcast에 LL-HLS 주소(`playbackUrl`)와 함께 WHEP 주소(`webrtcUrl`, `/whip/live/{id}/whep`)를 준다. 재생기는 기기 능력을 직접 물어(Media Source의 Opus 지원) 고르므로 기기 이름으로 나누지 않는다. MediaMTX의 시청 허가(`read`)는 프로토콜과 상관없이 같은 규칙이라 Spring은 바뀌지 않는다.

대안은 MediaMTX 옆에서 ffmpeg가 소리만 AAC로 바꿔 HLS를 다시 만드는 것이었다. 모든 기기가 HLS 하나로 보지만 ffmpeg 프로세스와 경로·인증 설정이 늘고 지연이 조금 는다.

## Consequences

- iOS Viewer는 지연이 1초 안쪽으로 줄지만, Viewer마다 MediaMTX와 WebRTC 연결이 생겨 HLS보다 무겁다. Viewer가 많아지면 다시 정해야 한다.
- iOS Viewer도 송출처럼 MediaMTX의 WebRTC 포트(8189)에 닿아야 한다. MediaMTX가 휴대폰에서 닿는 주소(LAN IP)를 알아야 하는 것도 같다.
- WebRTC로 볼 때는 화질 선택이 없다.
