package com.webbroadcast.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.webbroadcast.media.MediaProperties;

/**
 * 로컬 디스크 저장소. 디렉터리 구조:
 * <pre>
 * {root}/originals/{videoId}.{ext}
 * {root}/work/{videoId}/...     변환 중
 * {root}/public/{videoId}/...   공개된 HLS와 썸네일
 * </pre>
 */
@Component
public class LocalVideoStorage implements VideoStorage {

	private static final Logger log = LoggerFactory.getLogger(LocalVideoStorage.class);

	private final Path originals;
	private final Path work;
	private final Path published;

	public LocalVideoStorage(MediaProperties properties) {
		Path root = properties.storageRoot().toAbsolutePath().normalize();
		this.originals = root.resolve("originals");
		this.work = root.resolve("work");
		this.published = root.resolve("public");
		try {
			Files.createDirectories(originals);
			Files.createDirectories(work);
			Files.createDirectories(published);
		}
		catch (IOException e) {
			throw new UncheckedIOException("저장소 디렉터리를 만들 수 없습니다: " + root, e);
		}
	}

	@Override
	public Path originalPath(UUID videoId, String extension) {
		return originals.resolve(videoId + "." + extension);
	}

	@Override
	public Path prepareWorkDir(UUID videoId) throws IOException {
		Path dir = work.resolve(videoId.toString());
		deleteRecursively(dir);
		return Files.createDirectories(dir);
	}

	@Override
	public void publishWorkDir(UUID videoId) throws IOException {
		Path target = published.resolve(videoId.toString());
		deleteRecursively(target);
		Files.move(work.resolve(videoId.toString()), target, StandardCopyOption.ATOMIC_MOVE);
	}

	@Override
	public void discardWorkDir(UUID videoId) {
		try {
			deleteRecursively(work.resolve(videoId.toString()));
		}
		catch (IOException e) {
			log.warn("작업 공간 정리 실패: {}", videoId, e);
		}
	}

	@Override
	public void deleteOriginal(UUID videoId, String extension) {
		try {
			Files.deleteIfExists(originalPath(videoId, extension));
		}
		catch (IOException e) {
			log.warn("Original 삭제 실패: {}", videoId, e);
		}
	}

	@Override
	public void deleteAll(UUID videoId, String extension) {
		deleteOriginal(videoId, extension);
		discardWorkDir(videoId);
		try {
			deleteRecursively(published.resolve(videoId.toString()));
		}
		catch (IOException e) {
			log.warn("공개된 결과물 삭제 실패: {}", videoId, e);
		}
	}

	@Override
	public Path publicRoot() {
		return published;
	}

	private static void deleteRecursively(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			return;
		}
		Files.walkFileTree(dir, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
				Files.delete(d);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
