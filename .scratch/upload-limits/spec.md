# 업로드 용량 제한 (공개 운영 대비)

Status: resolved (2026-11-23)

## 왜

설명영상 공개를 앞두고 보안 점검(2026-11-23)을 했다. 누구나 가입해서 동영상을 올릴 수 있는데, 파일 하나 크기 말고는 막는 것이 없다.
지금 서버 디스크로는 2GB 파일 약 18개면 가득 찬다. 가득 차면 DB, 라이브 HLS, 변환 작업이 모두 멈춘다.

## 지금 상태 (2026-11-23 확인)

- 파일 하나 상한: 2GB
  - 서버: `server/src/main/resources/application.properties`의 `spring.servlet.multipart.max-file-size=2GB`, `max-request-size=2100MB`
  - 웹: `web/src/pages/UploadPage.tsx`의 `MAX_FILE_BYTES`
  - 초과하면 413을 돌려준다: `ApiExceptionHandler`
- 사용자별 총용량, 개수, 업로드 빈도 제한: **없음**
- 가입 제한(빈도·인증): **없음**
- Caddy `request_body` 상한: **없음**. Spring 앞에서 막지 않는다
- 운영 서버 디스크: 부트 볼륨 45GB 중 7.5GB 사용, 37GB 남음. K3s 저장소는 252MB

## 할 일 후보 (사용자와 정할 것)

1. 파일 하나 상한을 운영에 맞게 낮춘다. 예: 500MB. 웹 안내 문구와 테스트도 함께 바꾼다.
2. 사용자별 총용량 또는 영상 개수 상한을 둔다. 넘으면 업로드를 거절한다.
3. 디스크 여유가 기준 아래면 업로드를 받지 않는다. 라이브와 DB를 지키기 위해서다.
4. Caddy에 `request_body max_size`를 둬서 큰 요청을 Spring까지 보내지 않는다.
5. (선택) 가입·업로드 빈도 제한

## 참고

- 같은 점검에서 확인한 것
  - SSH 비밀번호 로그인은 꺼져 있다(`passwordauthentication no`). 하루 600건 넘는 봇 로그인 시도는 모두 키 인증에서 막힌다.
  - 토스 키 두 개는 모두 `test_` 키다.
- 데모 계정과 콘텐츠는 나중에 모두 지우고 예시 영상만 남길 예정이라 이 작업 범위가 아니다.

## 결정과 구현 (2026-11-23)

- 파일 하나 500MB, User별 원본 합계 2GB, 업로드 뒤 디스크 여유 5GB 미만이면 거절(507). 값은 `VideoUploadProperties`(`video.upload.*`).
- 제한은 Original을 디스크에 쓰기 전에 `VideoService.checkLimits`에서 확인한다. 동시에 올린 업로드끼리는 총용량을 조금 넘을 수 있다.
- multipart 상한 500MB/510MB, Caddy `/api/*` 요청 본문 520MB.
- 웹과 데스크톱 앱의 사전 검사·안내 문구를 500MB로 맞췄다. 데스크톱 앱은 다음 릴리스부터 반영된다.
- 가입·업로드 빈도 제한은 하지 않았다(필요하면 새 이슈).
