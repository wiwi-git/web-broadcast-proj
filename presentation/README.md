# 프로젝트 소개 영상

`web-broadcast-presentation-v3.mp4` — 방송자·시청자 두 시점으로 화면 → 기능 → API·기술을 보여 주는 약 6분 36초 영상(리뷰·사실검증 반영본). 이전 버전은 `web-broadcast-presentation.mp4`, `-v2.mp4`. 대본은 [script.md](script.md), 리뷰는 [REVIEW.md](REVIEW.md), 사실검증은 [FACT-CHECK-V2.md](FACT-CHECK-V2.md).

## 다시 만들기

1. 나레이션·자막 타이밍 (Edge TTS `ko-KR-SunHiNeural`)
   ```bash
   uv run --with edge-tts python tools/build_narration.py
   ```
   `video/assets/audio/narration.wav`와 `video/timeline.json`이 생긴다.
2. 앱 화면 캡처 — 앱(Vite 5173, Spring 8080, MediaMTX)이 떠 있어야 한다. 헤드리스 Chrome에 쇼츠 영상을 가짜 카메라로 넣어 실제 WHIP 송출로 방송한다.
   ```bash
   export PUPPETEER_CORE=<puppeteer-core 경로>   # 예: ~/.npm/_npx/<hash>/node_modules/puppeteer-core
   export FAKE_MEDIA=<cam.mjpeg, mic.wav가 있는 폴더>
   export UPLOAD_VIDEO=<업로드 데모에 쓸 동영상 파일>
   node tools/capture/step1.mjs    # 로그인 화면, 데모 계정, 동영상 업로드 (9333 포트 Chrome 필요)
   node tools/capture/live.mjs     # 방송·시청·댓글·후원·알림
   node tools/capture/videos.mjs   # 동영상 목록·상세·댓글
   ```
3. 컴포지션 생성과 렌더
   ```bash
   python3 tools/build_composition.py
   cd video && npx hyperframes check && npx hyperframes render -q standard -w 4 -o ../web-broadcast-presentation-v3.mp4
   ```

## 데모 데이터

캡처하면서 로컬 개발 DB에 데모 데이터가 생겼다. 계정 테스트 값은 `tools/demo-accounts.local.json`(git 제외)에 있다.

- 회원 `demobroadcaster`(데모방송자), `demoviewer`(데모시청자)
- 동영상 1개, 종료된 방송 1개, 댓글, 후원
- 5,000원 후원(캡처할 때마다 1건)은 실제 카드 인증 없이 알림 장면을 찍으려고 **DB에서 직접 완료로 바꾼 샘플**이다(`payment_key = 'demo-…'`). 토스페이먼츠에는 이 결제가 없고, 영상 12장면에도 `완료 처리한 샘플 후원`으로 표시한다.

지우려면:

```bash
docker exec web-broadcast-proj-postgres-1 psql -U webbroadcast -d webbroadcast -c "
  delete from donation where donor_id in (select id from app_user where username in ('demobroadcaster','demoviewer'))
                          or recipient_id in (select id from app_user where username in ('demobroadcaster','demoviewer'));
  delete from comment where author_id in (select id from app_user where username in ('demobroadcaster','demoviewer'));
  delete from broadcast where owner_id in (select id from app_user where username in ('demobroadcaster','demoviewer'));
  delete from video where owner_id in (select id from app_user where username in ('demobroadcaster','demoviewer'));
  delete from app_user where username in ('demobroadcaster','demoviewer');"
```

동영상 파일은 `server/data/originals/`, `server/data/public/`에 남으므로 함께 지운다. 컬럼 이름이 다르면 `\d comment` 등으로 확인한다.
