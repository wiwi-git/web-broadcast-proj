# Web Broadcast

치지직 같은 웹 방송 플랫폼. 현재는 녹화된 영상을 올리고 시청하는 기능만 다룬다.

## Language

### 동영상

**Video (동영상)**:
누군가 녹화해 둔 영상 파일을 직접 올려 만든 시청 단위. 제목과 설명을 가진다.
_Avoid_: VOD, 클립, 영상 파일

**Replay (다시보기)**:
라이브 방송이 끝난 뒤 자동으로 남는 녹화본. Video와 별개 개념이며 아직 존재하지 않는다.
_Avoid_: VOD, Video

**Original (원본)**:
업로드된 그대로의 영상 파일. 변환 후에도 보관된다.
_Avoid_: 소스 파일, raw

**Rendition (화질)**:
Original을 특정 해상도(1080p/720p/480p)로 변환한 결과물. Original 해상도를 넘는 Rendition은 만들지 않는다.
_Avoid_: 품질, 버전, 트랙

### 동영상 상태

**처리 중 (Processing)**:
업로드는 끝났지만 Rendition이 아직 준비되지 않아 시청할 수 없는 상태.

**공개 (Published)**:
모든 Rendition이 준비되어 시청할 수 있는 상태. 목록에는 이 상태의 Video만 보인다.
_Avoid_: Ready, 완료

**실패 (Failed)**:
변환에 실패해 시청할 수 없는 상태.
