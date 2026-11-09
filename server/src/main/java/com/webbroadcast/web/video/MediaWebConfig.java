package com.webbroadcast.web.video;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.webbroadcast.storage.VideoStorage;

/**
 * 공개 영역의 HLS 플레이리스트·세그먼트·썸네일을 정적 파일로 제공한다.
 * Original은 공개 영역 밖에 있으므로 노출되지 않는다.
 */
@Configuration
class MediaWebConfig implements WebMvcConfigurer {

	static final String MEDIA_PATH = "/media/videos/";

	private final VideoStorage storage;

	MediaWebConfig(VideoStorage storage) {
		this.storage = storage;
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler(MEDIA_PATH + "**")
			.addResourceLocations(storage.publicRoot().toUri().toString());
	}
}
