package com.webbroadcast.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 실제 ffmpeg/ffprobe로 짧은 테스트 영상을 만들어 변환해 본다. ffmpeg가 없으면 건너뛴다.
 */
class FfmpegVideoTranscoderTest {

	private static final MediaProperties PROPERTIES = new MediaProperties(Path.of("unused"), "ffmpeg", "ffprobe");

	private final MediaInspector inspector = new MediaInspector(PROPERTIES);
	private final FfmpegVideoTranscoder transcoder = new FfmpegVideoTranscoder(PROPERTIES, inspector);

	@BeforeAll
	static void requireFfmpeg() {
		boolean available;
		try {
			available = ProcessRunner.run(List.of("ffmpeg", "-version")).succeeded();
		}
		catch (Exception e) {
			available = false;
		}
		assumeTrue(available, "ffmpeg가 설치되어 있지 않음");
	}

	@Test
	void producesHlsRenditionsUpToSourceResolutionAndThumbnail(@TempDir Path dir) throws Exception {
		Path original = generate(dir.resolve("source.mov"), 1280, 720, true);
		Path out = Files.createDirectories(dir.resolve("out"));

		MediaInfo info = inspector.inspect(original).orElseThrow();
		assertThat(info.width()).isEqualTo(1280);
		assertThat(info.height()).isEqualTo(720);
		assertThat(info.hasAudio()).isTrue();
		assertThat(info.durationSeconds()).isBetween(4.5, 5.5);

		transcoder.transcode(original, out);

		String master = Files.readString(out.resolve(VideoTranscoder.MASTER_PLAYLIST));
		assertThat(master).contains("720p/index.m3u8", "480p/index.m3u8").doesNotContain("1080p");
		assertThat(master).contains("RESOLUTION=1280x720", "RESOLUTION=854x480");
		assertThat(Files.readString(out.resolve("720p/index.m3u8"))).contains("#EXT-X-ENDLIST", "segment_0000.ts");
		assertThat(out.resolve(VideoTranscoder.THUMBNAIL)).isNotEmptyFile();
	}

	@Test
	void handlesVideoWithoutAudio(@TempDir Path dir) throws Exception {
		Path original = generate(dir.resolve("silent.mp4"), 640, 480, false);
		Path out = Files.createDirectories(dir.resolve("out"));

		transcoder.transcode(original, out);

		assertThat(Files.readString(out.resolve(VideoTranscoder.MASTER_PLAYLIST))).contains("480p/index.m3u8");
	}

	@Test
	void rejectsFileThatIsNotAVideo(@TempDir Path dir) throws Exception {
		Path fake = Files.writeString(dir.resolve("fake.mp4"), "this is not a video");

		assertThat(inspector.inspect(fake)).isEmpty();
		assertThatThrownBy(() -> transcoder.transcode(fake, dir)).isInstanceOf(TranscodeException.class);
	}

	private static Path generate(Path target, int width, int height, boolean withAudio) throws Exception {
		List<String> command = new ArrayList<>(List.of("ffmpeg", "-hide_banner", "-y",
				"-f", "lavfi", "-i", "testsrc=duration=5:size=" + width + "x" + height + ":rate=30"));
		if (withAudio) {
			command.addAll(List.of("-f", "lavfi", "-i", "sine=frequency=440:duration=5"));
		}
		command.addAll(List.of("-pix_fmt", "yuv420p", target.toString()));
		assertThat(ProcessRunner.run(command).succeeded()).isTrue();
		return target;
	}
}
