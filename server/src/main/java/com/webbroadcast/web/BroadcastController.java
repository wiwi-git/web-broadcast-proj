package com.webbroadcast.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.webbroadcast.broadcast.BroadcastService;
import com.webbroadcast.broadcast.MediaPath;

@RestController
@RequestMapping("/api/broadcasts")
class BroadcastController {

	static final String STREAM_KEY_HEADER = "X-Stream-Key";

	/** 미디어 서버의 WHIP 송출 주소. 웹 서버(개발 중에는 Vite)가 이 경로를 미디어 서버로 넘긴다. */
	private static final String WHIP_PATH = "/whip/";

	record StartRequest(String title) {
	}

	/** Broadcaster에게만 한 번 돌려주는 응답. 송출할 때 whipUrl에 streamKey를 붙인다. */
	record StartResponse(BroadcastResponse broadcast, String streamKey, String whipUrl) {
	}

	record ListResponse(List<BroadcastResponse> live, List<BroadcastResponse> recentlyEnded) {
	}

	private final BroadcastService broadcastService;

	BroadcastController(BroadcastService broadcastService) {
		this.broadcastService = broadcastService;
	}

	@PostMapping
	ResponseEntity<StartResponse> start(@RequestBody StartRequest request) {
		BroadcastService.Started started = broadcastService.start(request.title());
		UUID id = started.broadcast().getId();
		return ResponseEntity.created(URI.create("/api/broadcasts/" + id))
			.body(new StartResponse(
					BroadcastResponse.from(started.broadcast()),
					started.streamKey(),
					WHIP_PATH + MediaPath.of(id) + "/whip"));
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

	@PostMapping("/{id}/end")
	ResponseEntity<Void> end(@PathVariable UUID id,
			@RequestHeader(value = STREAM_KEY_HEADER, required = false) String streamKey) {
		broadcastService.end(id, streamKey);
		return ResponseEntity.noContent().build();
	}
}
