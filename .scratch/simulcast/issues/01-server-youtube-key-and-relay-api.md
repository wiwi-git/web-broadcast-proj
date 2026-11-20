# 서버: YouTube Key 저장과 중계 내부 API

Status: resolved

## 할 것
- User마다 YouTube Key(암호화)와 "지난번 선택"을 저장한다. 키 등록·바꾸기·지우기 API, 끝 4자리만 돌려준다.
- 방송 시작 요청에 동시 송출 여부를 받는다. 키 없이 켜면 거부한다.
- MediaMTX 전용 내부 API: 방송 경로로 유튜브 주소를 돌려주고, 연결 상태를 받는다. 비밀 헤더가 없거나 틀리면 거부한다.
- Broadcast 응답에 동시 송출 상태를 Owner에게만 담는다.

## 확인할 것
- [x] 키를 저장하면 DB에 평문이 없다
- [x] 비밀 헤더 없이는 내부 API가 거부된다
- [x] 동시 송출을 켠 방송만 유튜브 주소를 받는다
- [x] 다른 사람에게는 동시 송출 상태가 보이지 않는다

## Comments

2026-11-19, server/src/test/.../SimulcastTest.
