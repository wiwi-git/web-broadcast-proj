# 업로드된 Video는 API 서버 안에서 HLS로 변환한다

> 변환을 어디서 돌리는지는 [ADR-0004](0004-transcoding-worker-with-db-queue.md)로 바뀌었다. HLS Rendition을 만든다는 결정은 그대로다.

Original을 그대로 Range 요청으로 재생하는 대신, 업로드 후 ffmpeg로 1080p/720p/480p HLS Rendition을 만들어 제공한다. 실제 방송 플랫폼처럼 화질 선택과 적응형 재생이 가능하고, 브라우저가 재생할 수 없는 MOV/MKV/AVI도 받을 수 있기 때문이다. 변환은 별도 워커나 메시지 큐 없이 Spring 서버 안의 백그라운드 스레드에서 한 번에 하나씩 돌린다. 지금 규모에서는 큐가 과하기 때문이며, 변환 로직은 인터페이스 뒤에 두어 나중에 워커로 떼어낼 수 있게 한다.

## Consequences

- 서버 실행 환경(로컬, Docker 이미지)에 ffmpeg/ffprobe가 반드시 있어야 한다.
- 서버가 재시작되면 처리 중인 Video를 다시 변환 대기열에 넣는다.
- 변환이 CPU를 많이 쓰므로 업로드가 몰리면 API 응답이 느려질 수 있다. 그때가 워커로 분리할 시점이다.
