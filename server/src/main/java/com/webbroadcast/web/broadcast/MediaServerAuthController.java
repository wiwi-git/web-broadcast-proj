package com.webbroadcast.web.broadcast;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.webbroadcast.broadcast.BroadcastService;

/**
 * MediaMTX의 HTTP 인증 훅. 송출·시청 요청마다 MediaMTX가 호출하며, 2xx면 허가한다 (ADR-0002).
 */
@RestController
public class MediaServerAuthController {

	public static final String PATH = "/api/media-server/auth";

	/** MediaMTX가 보내는 요청 중 여기서 쓰는 필드 */
	record AuthRequest(String action, String path, String query, String token) {
	}

	private final BroadcastService broadcastService;

	MediaServerAuthController(BroadcastService broadcastService) {
		this.broadcastService = broadcastService;
	}

	@PostMapping(PATH)
	ResponseEntity<Void> authorize(@RequestBody AuthRequest request) {
		boolean allowed = switch (request.action() == null ? "" : request.action()) {
			case "publish" -> broadcastService.canPublish(request.path(), streamKeyOf(request));
			case "read" -> broadcastService.canWatch(request.path());
			default -> false;
		};
		return ResponseEntity.status(allowed ? HttpStatus.OK : HttpStatus.UNAUTHORIZED).build();
	}

	/** WHIP 주소의 ?key= 또는 Authorization: Bearer 로 전달된 Stream Key */
	static String streamKeyOf(AuthRequest request) {
		if (request.query() != null) {
			for (String pair : request.query().split("&")) {
				if (pair.startsWith("key=")) {
					return URLDecoder.decode(pair.substring(4), StandardCharsets.UTF_8);
				}
			}
		}
		return request.token();
	}
}
