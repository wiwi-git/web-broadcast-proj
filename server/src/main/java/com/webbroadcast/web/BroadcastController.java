package com.webbroadcast.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.webbroadcast.broadcast.BroadcastService;
import com.webbroadcast.broadcast.MediaPath;
import com.webbroadcast.user.CurrentUser;

/** 목록·상세는 누구나, 시작은 로그인한 User, 그 밖의 제어는 Owner만 (SecurityConfig, BroadcastService). */
@RestController
@RequestMapping("/api/broadcasts")
class BroadcastController {

	/** 미디어 서버의 WHIP 송출 주소. 웹 서버(개발 중에는 Vite)가 이 경로를 미디어 서버로 넘긴다. */
	private static final String WHIP_PATH = "/whip/";

	record TitleRequest(String title) {
	}

	/** 송출할 때 whipUrl에 streamKey를 붙인다 */
	record StreamCredentials(String streamKey, String whipUrl) {
	}

	/** Owner에게만 돌려주는 응답 */
	record StartResponse(BroadcastResponse broadcast, String streamKey, String whipUrl) {
	}

	record ListResponse(List<BroadcastResponse> live, List<BroadcastResponse> recentlyEnded) {
	}

	private final BroadcastService broadcastService;

	BroadcastController(BroadcastService broadcastService) {
		this.broadcastService = broadcastService;
	}

	@PostMapping
	ResponseEntity<StartResponse> start(@AuthenticationPrincipal CurrentUser user, @RequestBody TitleRequest request) {
		BroadcastService.Started started = broadcastService.start(user.id(), request.title());
		UUID id = started.broadcast().getId();
		return ResponseEntity.created(URI.create("/api/broadcasts/" + id))
			.body(new StartResponse(BroadcastResponse.from(started.broadcast()), started.streamKey(), whipUrl(id)));
	}

	@GetMapping
	ListResponse list() {
		return new ListResponse(
				broadcastService.listLive().stream().map(BroadcastResponse::from).toList(),
				broadcastService.listRecentlyEnded().stream().map(BroadcastResponse::from).toList());
	}

	@GetMapping("/{id}")
	BroadcastResponse get(@PathVariable UUID id) {
		return BroadcastResponse.from(broadcastService.get(id));
	}

	@PatchMapping("/{id}")
	BroadcastResponse rename(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id,
			@RequestBody TitleRequest request) {
		return BroadcastResponse.from(broadcastService.rename(id, user.id(), request.title()));
	}

	@PostMapping("/{id}/end")
	ResponseEntity<Void> end(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		broadcastService.end(id, user.id());
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		broadcastService.delete(id, user.id());
		return ResponseEntity.noContent().build();
	}

	/** Stream Key는 해시만 저장하므로, 다시 필요하면 새로 발급한다. 이전 값은 무효가 된다. */
	@PostMapping("/{id}/stream-key")
	StreamCredentials reissueStreamKey(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
		return new StreamCredentials(broadcastService.reissueStreamKey(id, user.id()), whipUrl(id));
	}

	private static String whipUrl(UUID id) {
		return WHIP_PATH + MediaPath.of(id) + "/whip";
	}
}
