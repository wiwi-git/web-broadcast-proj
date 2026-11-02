package com.webbroadcast.media;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * ffmpeg/ffprobe 같은 외부 프로그램을 실행하고 출력을 모은다.
 * 출력이 아주 길 수 있으므로 마지막 일부만 보관한다.
 */
final class ProcessRunner {

	private static final int KEPT_LINES = 40;

	private ProcessRunner() {
	}

	record Result(int exitCode, String output) {

		boolean succeeded() {
			return exitCode == 0;
		}
	}

	static Result run(List<String> command) throws IOException, InterruptedException {
		Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
		Deque<String> tail = new ArrayDeque<>();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (tail.size() == KEPT_LINES) {
					tail.removeFirst();
				}
				tail.addLast(line);
			}
		}
		catch (IOException e) {
			process.destroyForcibly();
			throw e;
		}
		return new Result(process.waitFor(), String.join("\n", tail));
	}
}
