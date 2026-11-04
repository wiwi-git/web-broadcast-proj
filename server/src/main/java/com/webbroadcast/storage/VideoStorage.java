package com.webbroadcast.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Original과 변환 결과물이 놓이는 곳.
 * 변환 결과는 작업 공간에서 만들어진 뒤 한 번에 공개 영역으로 옮겨지므로,
 * 공개 영역에는 완성된 결과물만 존재한다.
 */
public interface VideoStorage {

	Path originalPath(UUID videoId, String extension);

	/** 비어 있는 작업 공간을 준비한다. 이전 시도의 잔여물이 있으면 지운다. */
	Path prepareWorkDir(UUID videoId) throws IOException;

	/** 작업 공간의 결과물을 공개 영역으로 옮긴다. */
	void publishWorkDir(UUID videoId) throws IOException;

	void discardWorkDir(UUID videoId);

	void deleteOriginal(UUID videoId, String extension);

	/** Original, 작업 공간, 공개된 결과물을 모두 지운다. */
	void deleteAll(UUID videoId, String extension);

	/** 공개 영역의 루트. 이 아래 {videoId}/ 디렉터리가 HTTP로 그대로 제공된다. */
	Path publicRoot();
}
