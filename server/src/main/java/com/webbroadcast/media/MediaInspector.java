package com.webbroadcast.media;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * ffprobe로 파일이 실제 영상인지 확인하고 해상도·길이·오디오 유무를 읽는다.
 */
@Component
public class MediaInspector {

	private final String ffprobePath;

	public MediaInspector(MediaProperties properties) {
		this.ffprobePath = properties.ffprobePath();
	}

	/** 영상 스트림을 읽을 수 없는 파일이면 empty. */
	public Optional<MediaInfo> inspect(Path file) throws IOException, InterruptedException {
		ProcessRunner.Result video = ProcessRunner.run(List.of(
				ffprobePath, "-v", "error",
				"-select_streams", "v:0",
				"-show_entries", "stream=width,height:format=duration",
				"-of", "default=noprint_wrappers=1",
				file.toString()));
		if (!video.succeeded()) {
			return Optional.empty();
		}
		Map<String, String> values = parseKeyValues(video.output());
		Integer width = parsePositiveInt(values.get("width"));
		Integer height = parsePositiveInt(values.get("height"));
		if (width == null || height == null) {
			return Optional.empty();
		}

		ProcessRunner.Result audio = ProcessRunner.run(List.of(
				ffprobePath, "-v", "error",
				"-select_streams", "a",
				"-show_entries", "stream=index",
				"-of", "csv=p=0",
				file.toString()));
		boolean hasAudio = audio.succeeded() && !audio.output().isBlank();

		return Optional.of(new MediaInfo(width, height, parseDuration(values.get("duration")), hasAudio));
	}

	private static Map<String, String> parseKeyValues(String output) {
		Map<String, String> values = new HashMap<>();
		for (String line : output.split("\n")) {
			int separator = line.indexOf('=');
			if (separator > 0) {
				values.putIfAbsent(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
			}
		}
		return values;
	}

	private static Integer parsePositiveInt(String value) {
		try {
			int parsed = Integer.parseInt(value);
			return parsed > 0 ? parsed : null;
		}
		catch (NumberFormatException | NullPointerException e) {
			return null;
		}
	}

	private static Double parseDuration(String value) {
		try {
			double parsed = Double.parseDouble(value);
			return parsed > 0 ? parsed : null;
		}
		catch (NumberFormatException | NullPointerException e) {
			return null;
		}
	}
}
