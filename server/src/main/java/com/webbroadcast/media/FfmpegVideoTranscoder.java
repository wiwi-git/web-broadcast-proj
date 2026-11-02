package com.webbroadcast.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class FfmpegVideoTranscoder implements VideoTranscoder {

	private static final int SEGMENT_SECONDS = 4;
	private static final int THUMBNAIL_HEIGHT = 360;

	private final String ffmpegPath;
	private final MediaInspector inspector;

	public FfmpegVideoTranscoder(MediaProperties properties, MediaInspector inspector) {
		this.ffmpegPath = properties.ffmpegPath();
		this.inspector = inspector;
	}

	@Override
	public void transcode(Path original, Path outputDir) throws TranscodeException {
		try {
			MediaInfo info = inspector.inspect(original)
				.orElseThrow(() -> new TranscodeException("영상 스트림을 읽을 수 없습니다: " + original));
			List<Rendition> renditions = Rendition.ladderFor(info);
			for (Rendition rendition : renditions) {
				Files.createDirectories(outputDir.resolve(rendition.name()));
			}
			runFfmpeg(hlsCommand(original, outputDir, info, renditions));
			runFfmpeg(thumbnailCommand(original, outputDir, info));
		}
		catch (IOException e) {
			throw new TranscodeException("ffmpeg 실행 실패", e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new TranscodeException("변환이 중단되었습니다", e);
		}
	}

	private void runFfmpeg(List<String> command) throws IOException, InterruptedException, TranscodeException {
		ProcessRunner.Result result = ProcessRunner.run(command);
		if (!result.succeeded()) {
			throw new TranscodeException("ffmpeg 종료 코드 " + result.exitCode() + "\n" + result.output());
		}
	}

	private List<String> hlsCommand(Path original, Path outputDir, MediaInfo info, List<Rendition> renditions) {
		List<String> command = new ArrayList<>(List.of(ffmpegPath, "-hide_banner", "-y", "-i", original.toString()));

		// 영상 하나를 Rendition 수만큼 나눈 뒤 각각 짧은 변 기준으로 축소한다 (세로 영상 대응)
		StringBuilder filter = new StringBuilder("[0:v:0]split=" + renditions.size());
		for (int i = 0; i < renditions.size(); i++) {
			filter.append("[s").append(i).append(']');
		}
		for (int i = 0; i < renditions.size(); i++) {
			int h = renditions.get(i).height();
			filter.append(";[s").append(i).append("]scale=")
				.append("w='if(gte(iw,ih),-2,").append(h).append(")':")
				.append("h='if(gte(iw,ih),").append(h).append(",-2)'")
				.append("[v").append(i).append(']');
		}
		command.addAll(List.of("-filter_complex", filter.toString()));

		StringBuilder streamMap = new StringBuilder();
		for (int i = 0; i < renditions.size(); i++) {
			Rendition rendition = renditions.get(i);
			command.addAll(List.of("-map", "[v" + i + "]"));
			if (info.hasAudio()) {
				command.addAll(List.of("-map", "0:a:0"));
			}
			int kbps = rendition.videoBitrateKbps();
			command.addAll(List.of(
					"-b:v:" + i, kbps + "k",
					"-maxrate:v:" + i, (kbps * 107 / 100) + "k",
					"-bufsize:v:" + i, (kbps * 3 / 2) + "k"));
			if (!streamMap.isEmpty()) {
				streamMap.append(' ');
			}
			streamMap.append("v:").append(i);
			if (info.hasAudio()) {
				streamMap.append(",a:").append(i);
			}
			streamMap.append(",name:").append(rendition.name());
		}

		command.addAll(List.of(
				"-c:v", "libx264", "-preset", "veryfast", "-profile:v", "main", "-pix_fmt", "yuv420p",
				// 모든 Rendition의 키프레임을 같은 시점에 맞춰야 화질 전환이 끊기지 않는다
				"-force_key_frames", "expr:gte(t,n_forced*" + SEGMENT_SECONDS + ")",
				"-sc_threshold", "0"));
		if (info.hasAudio()) {
			command.addAll(List.of("-c:a", "aac", "-b:a", "128k", "-ac", "2"));
		}
		command.addAll(List.of(
				"-f", "hls",
				"-hls_time", String.valueOf(SEGMENT_SECONDS),
				"-hls_playlist_type", "vod",
				"-hls_flags", "independent_segments",
				"-hls_segment_filename", outputDir.resolve("%v/segment_%04d.ts").toString(),
				"-master_pl_name", MASTER_PLAYLIST,
				"-var_stream_map", streamMap.toString(),
				outputDir.resolve("%v/index.m3u8").toString()));
		return command;
	}

	private List<String> thumbnailCommand(Path original, Path outputDir, MediaInfo info) {
		double seekSeconds = info.durationSeconds() == null ? 0 : info.durationSeconds() * 0.1;
		return List.of(ffmpegPath, "-hide_banner", "-y",
				"-ss", String.format(Locale.ROOT, "%.3f", seekSeconds),
				"-i", original.toString(),
				"-frames:v", "1",
				"-vf", "scale=-2:" + THUMBNAIL_HEIGHT,
				"-q:v", "3",
				outputDir.resolve(THUMBNAIL).toString());
	}
}
