# Web Broadcast

치지직 같은 웹 방송 플랫폼. 휴대폰으로 라이브 방송을 하고 시청하는 기능과, 녹화된 영상을 올리고 시청하는 기능을 다룬다.

## Language

### 동영상

**Video (동영상)**:
누군가 녹화해 둔 영상 파일을 직접 올려 만든 시청 단위. 제목과 설명을 가진다.
_Avoid_: VOD, 클립, 영상 파일

**Replay (다시보기)**:
Broadcast가 끝난 뒤 자동으로 남는 녹화본. Video와 별개 개념이며 아직 존재하지 않는다.
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

### 라이브

**Broadcast (방송)**:
Broadcaster가 시작해서 종료할 때까지의 라이브 방송 한 회차. 제목을 가진다. 한 번 종료된 Broadcast는 다시 시작되지 않는다.
_Avoid_: 스트림, 라이브, 생방송

**Broadcaster (방송자)**:
휴대폰 카메라와 마이크로 Broadcast를 송출하는 사람.
_Avoid_: 스트리머, 호스트, 송출자

**Viewer (시청자)**:
Broadcast나 Video를 보는 사람.
_Avoid_: 유저, 관객

**Stream Key (방송 키)**:
Broadcast를 시작할 때 Broadcaster에게만 주어지는 비밀 값. 이 값을 가진 쪽만 송출하고 종료할 수 있다.
_Avoid_: 토큰, 비밀번호

### 방송 상태

**준비 (Pending)**:
Broadcaster가 시작을 눌렀지만 영상이 아직 도착하지 않은 상태. Viewer에게는 보이지 않는다. 일정 시간 안에 영상이 오지 않으면 종료된다.

**방송 중 (Live)**:
영상이 들어오고 있어 시청할 수 있는 상태.
_Avoid_: On-air, 진행 중

**종료 (Ended)**:
Broadcaster가 끝냈거나 송출이 끊겨 더 이상 시청할 수 없는 상태. 끊긴 송출은 이어지지 않는다.
_Avoid_: Offline, 완료
