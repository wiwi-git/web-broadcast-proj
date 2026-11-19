package com.webbroadcast.web.simulcast;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.webbroadcast.simulcast.SimulcastService;
import com.webbroadcast.user.CurrentUser;

/**
 * 로그인한 User의 YouTube Key와 동시 송출 기본값 (ADR-0007). 조회도 로그인이 필요하다 (SecurityConfig).
 * 등록한 키는 끝 네 자리만 돌려준다.
 */
@RestController
public class SimulcastController {

	public static final String PATH = "/api/simulcast";

	record KeyRequest(String key) {
	}

	private final SimulcastService simulcastService;

	SimulcastController(SimulcastService simulcastService) {
		this.simulcastService = simulcastService;
	}

	@GetMapping(PATH)
	SimulcastService.Setting get(@AuthenticationPrincipal CurrentUser user) {
		return simulcastService.setting(user.id());
	}

	@PutMapping(PATH + "/youtube-key")
	SimulcastService.Setting saveKey(@AuthenticationPrincipal CurrentUser user, @RequestBody KeyRequest request) {
		return simulcastService.saveYouTubeKey(user.id(), request.key());
	}

	@DeleteMapping(PATH + "/youtube-key")
	ResponseEntity<Void> deleteKey(@AuthenticationPrincipal CurrentUser user) {
		simulcastService.deleteYouTubeKey(user.id());
		return ResponseEntity.noContent().build();
	}
}
