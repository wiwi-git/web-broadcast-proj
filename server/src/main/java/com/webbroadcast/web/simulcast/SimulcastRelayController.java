package com.webbroadcast.web.simulcast;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.webbroadcast.broadcast.BroadcastService;
import com.webbroadcast.broadcast.SimulcastStatus;
import com.webbroadcast.simulcast.SimulcastService;

/**
 * MediaMTX의 중계 스크립트(mediamtx/simulcast.sh)만 부르는 내부 API (ADR-0007).
 * 쿠키 로그인 대신 서버끼리 나눈 비밀 값(헤더)으로 허가한다. 비밀 값이 틀리면 있는지조차 알려 주지 않는다.
 * 웹 서버는 이 경로를 밖으로 넘기지 않는다.
 */
@RestController
public class SimulcastRelayController {

	public static final String PATH = "/api/internal/simulcast";
	static final String SECRET_HEADER = "X-Relay-Secret";

	private final BroadcastService broadcastService;
	private final SimulcastService simulcastService;

	SimulcastRelayController(BroadcastService broadcastService, SimulcastService simulcastService) {
		this.broadcastService = broadcastService;
		this.simulcastService = simulcastService;
	}

	/** 그 경로의 방송을 보낼 유튜브 주소(평문). 중계하지 않을 방송이면 204 */
	@GetMapping(value = PATH + "/target", produces = MediaType.TEXT_PLAIN_VALUE)
	ResponseEntity<String> target(@RequestHeader(name = SECRET_HEADER, required = false) String secret,
			@RequestParam String path) {
		if (!simulcastService.acceptsRelaySecret(secret)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		return broadcastService.simulcastTarget(path)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.noContent().build());
	}

	@PostMapping(PATH + "/status")
	ResponseEntity<Void> status(@RequestHeader(name = SECRET_HEADER, required = false) String secret,
			@RequestParam String path, @RequestParam SimulcastStatus status) {
		if (!simulcastService.acceptsRelaySecret(secret)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		broadcastService.reportSimulcast(path, status);
		return ResponseEntity.noContent().build();
	}
}
