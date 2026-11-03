package com.webbroadcast.broadcast;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * MediaMTX 제어 API 클라이언트.
 */
@Component
class MediaMtxServer implements MediaServer {

	private static final Logger log = LoggerFactory.getLogger(MediaMtxServer.class);

	/** 송출 주체 종류별 강제 종료 API */
	private static final Map<String, String> KICK_PATHS = Map.of(
			"webRTCSession", "/v3/webrtcsessions/kick/{id}",
			"rtmpConn", "/v3/rtmpconns/kick/{id}",
			"rtspSession", "/v3/rtspsessions/kick/{id}",
			"srtConn", "/v3/srtconns/kick/{id}");

	record PathList(List<PathItem> items) {
	}

	record PathItem(String name, boolean ready, Publisher source) {
	}

	private final RestClient client;

	MediaMtxServer(@Value("${mediamtx.api-url}") String apiUrl) {
		this.client = RestClient.builder().baseUrl(apiUrl).build();
	}

	@Override
	public Map<String, Publisher> activePaths() {
		PathList list = client.get().uri("/v3/paths/list?itemsPerPage=1000").retrieve().body(PathList.class);
		Map<String, Publisher> active = new HashMap<>();
		if (list != null && list.items() != null) {
			for (PathItem item : list.items()) {
				if (item.ready()) {
					active.put(item.name(), item.source());
				}
			}
		}
		return active;
	}

	@Override
	public void disconnect(Publisher publisher) {
		String kickPath = publisher == null ? null : KICK_PATHS.get(publisher.type());
		if (kickPath == null) {
			log.warn("끊을 수 없는 송출 주체: {}", publisher);
			return;
		}
		client.post().uri(kickPath, publisher.id()).retrieve().toBodilessEntity();
	}
}
