package com.webbroadcast.simulcast;

/** 서버에 동시 송출 설정(암호화 키, 중계 비밀 값)이 없다 */
public class SimulcastNotConfiguredException extends RuntimeException {

	SimulcastNotConfiguredException() {
		super("유튜브 동시 송출이 아직 설정되지 않았습니다.");
	}
}
