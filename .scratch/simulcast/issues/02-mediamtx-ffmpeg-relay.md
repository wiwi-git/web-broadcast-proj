# MediaMTX: ffmpeg 중계 스크립트

Status: resolved
Blocked by: 01

## 할 것
- MediaMTX 이미지를 `-ffmpeg` 태그로 바꾸고, RTSP를 127.0.0.1에서만 연다.
- `runOnAvailable`로 중계 스크립트를 띄운다. 스크립트는 내부 API에서 주소를 받아 ffmpeg로 보내고(영상 copy, 소리 AAC), 상태를 알린다. 끊기면 10초 간격 3번 다시 시도한다.
- compose와 k8s 설정을 맞춘다. 로컬 확인용 RTMP 수신기를 compose 프로필로 둔다.

## 확인할 것
- [x] 동시 송출을 켠 방송이 로컬 RTMP 수신기에 H.264 + AAC로 들어온다
- [x] 켜지 않은 방송은 중계되지 않는다
- [x] 방송을 끝내면 ffmpeg도 끝난다
- [x] 수신기가 없으면 세 번 다시 시도한 뒤 실패로 남는다

## Comments

2026-11-19, 로컬 RTMP 수신기(compose rtmp-sink)와 컨테이너 안 ffmpeg WHIP 송출로 확인.
