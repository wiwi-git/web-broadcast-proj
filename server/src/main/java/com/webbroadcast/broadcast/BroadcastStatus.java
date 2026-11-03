package com.webbroadcast.broadcast;

public enum BroadcastStatus {
	/** 시작을 눌렀지만 영상이 아직 도착하지 않음. Viewer에게 보이지 않는다 */
	PENDING,
	/** 영상이 들어오고 있어 시청 가능 */
	LIVE,
	/** Broadcaster가 끝냈거나 송출이 끊김. 다시 시작되지 않는다 */
	ENDED
}
