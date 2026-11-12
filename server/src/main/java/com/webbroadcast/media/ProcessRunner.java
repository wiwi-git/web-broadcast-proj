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
 * 실행 중인 스레드가 인터럽트되면 프로그램도 강제로 끝낸다 (워커 종료, 작업 점유를 잃은 경우).
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
		// 출력 읽기는 인터럽트에 반응하지 않으므로 별도 스레드에서 하고, 이 스레드는 종료를 기다린다
		Thread reader = Thread.ofVirtual().start(() -> {
			try (BufferedReader lines = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = lines.readLine()) != null) {
					synchronized (tail) {
						if (tail.size() == KEPT_LINES) {
							tail.removeFirst();
						}
						tail.addLast(line);
					}
				}
			}
			catch (IOException e) {
				// 프로그램을 강제로 끝내면 스트림이 닫히며 여기로 온다. 모은 출력만 쓰면 된다
			}
		});
		try {
			int exitCode = process.waitFor();
			reader.join();
			synchronized (tail) {
				return new Result(exitCode, String.join("\n", tail));
			}
		}
		catch (InterruptedException e) {
			process.destroyForcibly();
			throw e;
		}
	}
}
