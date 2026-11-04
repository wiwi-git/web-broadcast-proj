# 라이브 방송 실기기 테스트

Status: ready-for-human

라이브 방송 단계(커밋 `3b3cf54`)는 데스크톱 브라우저에서 캔버스 영상으로만 확인했다. 휴대폰에서만 확인할 수 있는 항목을 모아 둔다. 실기기 테스트는 여러 단계를 모아 한 번에 한다.

## 준비

1. `.env`의 `LAN_IP`가 지금 PC의 IP와 같은지 확인한다 (`ipconfig getifaddr en0`). 다르면 고치고 `docker compose up -d`.
2. `docker compose up -d`, `cd server && ./gradlew bootRun`, `cd web && npm run dev:https`
3. 휴대폰을 PC와 같은 Wi-Fi에 연결하고 `https://<LAN_IP>:5173`에 접속한다. 인증서 경고는 넘긴다.

## 확인할 것

### 송출 (Broadcaster)

- [ ] Android Chrome: `/broadcast`에서 카메라·마이크 권한 요청이 뜨고 미리보기가 나온다
- [ ] iPhone Safari: 위와 같음
- [ ] 방송 시작 후 몇 초 안에 `LIVE` 표시로 바뀐다 (연결이 안 되면 맥 방화벽의 5173/TCP, 8189/UDP·TCP와 공유기의 기기 간 통신 차단을 의심)
- [ ] 전면/후면 카메라 전환이 된다
- [ ] 휴대폰을 세로로 들면 시청 화면에도 세로 영상으로 나온다
- [ ] 방송 중에는 화면이 저절로 꺼지지 않는다 (Wake Lock)
- [ ] 방송 종료 버튼을 누르면 확인 창이 뜨고, 종료 후 시청 화면이 "방송이 종료되었습니다"로 바뀐다
- [ ] 방송 중 탭을 닫거나 Wi-Fi를 끄면 몇 초 안에 목록에서 종료로 바뀐다

### 시청 (Viewer)

- [ ] 다른 휴대폰이나 PC에서 라이브 목록에 방송이 보이고 재생된다
- [ ] iPhone Safari에서 재생된다. MediaMTX가 "part duration changed … error in iOS clients" 경고를 남겼으므로 특히 확인한다
- [ ] 소리가 나온다
- [ ] 지연이 대략 2~4초 안이다 (방송 화면 앞에 시계를 비춰 비교)

## Comments
