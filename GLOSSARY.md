# Web Broadcast

치지직 같은 웹 방송 플랫폼. 휴대폰으로 라이브 방송을 하고 시청하는 기능과, 녹화된 영상을 올리고 시청하는 기능을 다룬다.

## Language

### 회원과 콘텐츠

**User (회원)**:
아이디와 비밀번호로 가입해 로그인할 수 있는 사람. 다른 회원에게는 닉네임으로 보인다. 로그인하지 않아도 시청은 할 수 있다.
_Avoid_: 유저, 계정, 멤버

**Content (콘텐츠)**:
Video와 Broadcast를 함께 이르는 말. 모든 Content에는 Owner가 있다.
_Avoid_: 방송(라이브만 뜻한다), 게시물

**Owner (소유자)**:
Content를 만든 User. Content를 수정·삭제할 수 있는 유일한 사람이다. Broadcast의 Owner가 곧 그 Broadcast의 Broadcaster다.
_Avoid_: 작성자(Comment에 쓴다), 업로더

**Comment (댓글)**:
User가 Content 하나에 남기는 짧은 글. 답글은 없다. Broadcast에 남긴 Comment도 실시간 채팅이 아니라 Comment다.
_Avoid_: 채팅, 메시지

**Author (작성자)**:
Comment를 쓴 User. Comment를 수정·삭제할 수 있는 유일한 사람이다. Content의 Owner라도 남의 Comment는 지울 수 없다.
_Avoid_: 소유자(Content에 쓴다)

### 후원

**Donation (후원)**:
Donor가 방송 중인 Broadcast를 보다가 그 Broadcaster에게 돈을 보내는 일 한 건. 금액과 짧은 메시지를 가진다. 완료된 Donation만 방송 화면에 알림으로 뜨고, 완료된 Donation은 되돌리지 않는다. 받은 Broadcast가 삭제되어도 Donation은 남는다.
_Avoid_: 도네, 도네이션, 기부, 치즈

**Donor (후원자)**:
Donation을 보낸 User. 항상 닉네임으로 드러나며 익명 후원은 없다. 자기 Broadcast에는 후원할 수 없다.
_Avoid_: 후원인, 결제자

**Recipient (받는 사람)**:
Donation을 받는 User. 후원한 Broadcast의 Broadcaster다.
_Avoid_: 수혜자, 스트리머

**Payment (결제)**:
Donation 한 건의 돈을 실제로 옮기는 결제 서비스 쪽 거래. Donation은 결제 요청 전에 먼저 만들어지고, 결제가 승인되어야 완료된다.
_Avoid_: 주문, 거래

### 후원 상태

**결제 대기 (Pending)**:
Donation을 만들었지만 결제가 아직 승인되지 않은 상태. 아무에게도 알림이 뜨지 않는다. 오래 이 상태로 남으면 만료된다.

**완료 (Done)**:
결제가 승인되어 Recipient에게 후원된 상태. 이 상태의 Donation만 공개된다.
_Avoid_: 성공, 승인됨

**실패 (Failed)**:
결제가 거절되었거나 금액이 맞지 않아 후원되지 않은 상태.

**취소 (Canceled)**:
Donor가 결제를 끝내지 않고 스스로 그만둔 상태. 완료된 Donation을 되돌리는 환불이 아니다.
_Avoid_: 환불

**만료 (Expired)**:
결제 대기인 채로 오래 지나 더 이상 결제할 수 없게 된 상태.

### 동영상

**Video (동영상)**:
Owner가 녹화해 둔 영상 파일을 직접 올려 만든 시청 단위. 제목과 설명을 가진다.
_Avoid_: VOD, 클립, 영상 파일, 녹화방송

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
휴대폰 카메라와 마이크로 Broadcast를 송출하는 User. 그 Broadcast의 Owner다. 한 번에 하나의 Broadcast만 진행할 수 있다.
_Avoid_: 스트리머, 호스트, 송출자

**Viewer (시청자)**:
Broadcast나 Video를 보는 사람. 로그인하지 않은 사람도 Viewer다.
_Avoid_: 유저, 관객

**Stream Key (방송 키)**:
Broadcast의 Owner에게만 주어지는 송출 연결용 비밀 값. 이 값을 가진 쪽만 영상을 보낼 수 있다. 종료·수정·삭제는 Stream Key가 아니라 Owner인지로 판단한다.
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
