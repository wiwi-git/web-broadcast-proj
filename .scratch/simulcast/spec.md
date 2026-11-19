# 유튜브 동시 송출 (Simulcast)

라이브 방송을 켤 때 Broadcaster가 고르면 자기 유튜브 채널에도 같은 방송이 라이브로 나간다. 영상 업로드가 아니라 라이브다. 설계는 ADR-0007, 용어는 GLOSSARY의 Simulcast·YouTube Key.

## 정한 것 (2026-11-19 grilling)

- 대상은 유튜브 하나. Broadcaster 각자의 채널로 보낸다. 영상만 보내고 유튜브 채팅·후원은 잇지 않는다.
- 중계는 MediaMTX `runOnAvailable` + ffmpeg. 영상은 그대로, 소리만 Opus → AAC.
- YouTube Key는 유튜브 스튜디오에서 복사해 붙여넣는다(OAuth는 나중). User마다 하나, DB에 암호화, 화면에는 끝 4자리만.
- 키 등록과 "이번 방송도 유튜브에 송출" 선택은 방송하기 화면(시작 전)에 둔다. 선택의 기본값은 지난번 값. 방송 중에는 바꿀 수 없다.
- 유튜브 상태(연결 중/송출 중/실패)는 Broadcaster에게만 보인다. Broadcast에는 동시 송출을 켰는지와 마지막 상태만 남긴다.
- 유튜브가 실패해도 Broadcast는 계속된다. 10초 간격 3번 다시 시도 후 실패.
- 웹·iOS 앱·Mac 앱 모두. 송출 코드는 그대로.
- 자동 확인은 로컬 RTMP 수신기로, 진짜 유튜브는 마지막에 사람이 확인한다.
