# 로컬 오토스케일 설명 영상

`autoscale-presentation.mp4` — Web Broadcast의 로컬 오토스케일 단계에서 기존 구조와 무엇이 달라졌는지, 어떤 장점이 생겼는지, 부하 테스트로 무엇을 확인했는지 설명하는 약 8분 10초 영상. 대본은 [script.md](script.md).

만드는 방식은 [프로젝트 소개 영상](../README.md)과 같다(Edge TTS 나레이션 → HyperFrames HTML 장면 → MP4). 앱 화면 캡처 대신 구조도와 차트로 설명한다. 차트 수치는 [load-test/results/](../../load-test/results/)의 실제 기록이다.

## 다시 만들기

`presentation/`에서 실행한다.

1. 나레이션·자막 타이밍 (Edge TTS `ko-KR-SunHiNeural`)
   ```bash
   uv run --with edge-tts python autoscale/tools/build_narration.py
   ```
   `autoscale/video/assets/audio/narration.wav`와 `autoscale/video/timeline.json`이 생긴다.
2. 장면 생성과 렌더
   ```bash
   python3 autoscale/tools/build_composition.py
   cd autoscale/video && npx hyperframes check && npx hyperframes render -q standard -w 4 -o ../autoscale-presentation.mp4
   ```

글꼴 파일(`video/assets/fonts/`)은 저장소에 없다. 소개 영상 폴더의 `video/assets/fonts/`에서 복사한다. 한글은 macOS의 Apple SD Gothic Neo를 쓰므로 macOS에서 렌더해야 같은 모양이 나온다.

## 장면

| # | 장면 | 내용 |
| --- | --- | --- |
| 0 | 타이틀 | 오토스케일이 무엇인지 |
| 1 | 두 가지 일 | 플랫폼이 늘리는 일과, 늘어나도 괜찮은 앱 |
| 2–3 | 기존 구조 | 서버 한 대가 API와 변환을 함께 하던 구조와, 그대로 늘리면 생기는 네 가지 문제 |
| 4–7 | 바뀐 구조 | 변환 워커 분리, DB 대기열과 점유, 상태를 서버 밖으로, 쿠버네티스와 HPA |
| 8–11 | 결과 | k6 시나리오, API 서버 2→4→2, 워커 1→2→1, 확인하면서 고친 것 |
| 12–13 | 정리 | 좋아진 다섯 가지, 한계와 다음 단계 |
