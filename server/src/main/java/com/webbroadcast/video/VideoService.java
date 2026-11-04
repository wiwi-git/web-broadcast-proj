package com.webbroadcast.video;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.webbroadcast.media.MediaInfo;
import com.webbroadcast.media.MediaInspector;
import com.webbroadcast.storage.VideoStorage;
import com.webbroadcast.user.NotOwnerException;
import com.webbroadcast.user.User;
import com.webbroadcast.user.UserService;

@Service
public class VideoService {

	public static final List<String> ALLOWED_EXTENSIONS = List.of("mp4", "mov", "mkv", "webm", "avi");
	public static final int PAGE_SIZE = 20;

	private final VideoRepository repository;
	private final VideoStorage storage;
	private final MediaInspector inspector;
	private final VideoProcessingQueue queue;
	private final UserService userService;

	VideoService(VideoRepository repository, VideoStorage storage, MediaInspector inspector,
			VideoProcessingQueue queue, UserService userService) {
		this.repository = repository;
		this.storage = storage;
		this.inspector = inspector;
		this.queue = queue;
		this.userService = userService;
	}

	/**
	 * Original을 저장하고 실제 영상인지 확인한 뒤 처리 중 상태의 Video를 만든다.
	 * 변환은 백그라운드에서 이어진다.
	 */
	public Video upload(UUID ownerId, MultipartFile file, String rawTitle, String rawDescription) {
		String title = validTitle(rawTitle);
		String description = validDescription(rawDescription);
		if (file == null || file.isEmpty()) {
			throw new InvalidUploadException("영상 파일을 선택해 주세요.");
		}
		String extension = extensionOf(file.getOriginalFilename());
		if (!ALLOWED_EXTENSIONS.contains(extension)) {
			throw new InvalidUploadException("지원하지 않는 파일 형식입니다. (" + String.join(", ", ALLOWED_EXTENSIONS) + ")");
		}

		UUID id = UUID.randomUUID();
		Path original = storage.originalPath(id, extension);
		MediaInfo info;
		try {
			file.transferTo(original);
			info = inspector.inspect(original).orElse(null);
		}
		catch (IOException e) {
			storage.deleteOriginal(id, extension);
			throw new UncheckedIOException(e);
		}
		catch (InterruptedException e) {
			storage.deleteOriginal(id, extension);
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
		if (info == null) {
			storage.deleteOriginal(id, extension);
			throw new InvalidUploadException("영상 파일을 읽을 수 없습니다. 손상되었거나 영상이 아닌 파일입니다.");
		}

		User owner = userService.get(ownerId);
		Video video = repository.save(
				Video.uploaded(id, owner, title, description, extension, file.getSize(), info.durationSeconds()));
		queue.enqueue(id);
		return video;
	}

	public Video get(UUID id) {
		return repository.findById(id).orElseThrow(() -> new VideoNotFoundException(id));
	}

	/** Owner만 제목과 설명을 바꿀 수 있다. */
	public Video edit(UUID id, UUID userId, String rawTitle, String rawDescription) {
		Video video = ownedBy(id, userId);
		video.edit(validTitle(rawTitle), validDescription(rawDescription));
		return repository.save(video);
	}

	/**
	 * Owner만 지울 수 있다. 파일과 Comment도 함께 지워진다.
	 * 변환 중이었다면 변환이 끝날 때 결과물이 버려진다 (VideoProcessor).
	 */
	public void delete(UUID id, UUID userId) {
		Video video = ownedBy(id, userId);
		repository.delete(video);
		storage.deleteAll(id, video.getOriginalExtension());
	}

	private Video ownedBy(UUID id, UUID userId) {
		Video video = get(id);
		if (!video.isOwnedBy(userId)) {
			throw new NotOwnerException("본인이 올린 동영상만 수정하거나 삭제할 수 있습니다.");
		}
		return video;
	}

	/** 공개된 Video만, 최신순으로. */
	public Page<Video> listPublished(int page) {
		return repository.findByStatusOrderByCreatedAtDesc(VideoStatus.PUBLISHED,
				PageRequest.of(Math.max(page, 0), PAGE_SIZE));
	}

	private static String validTitle(String rawTitle) {
		String title = rawTitle == null ? "" : rawTitle.strip();
		if (title.isEmpty()) {
			throw new InvalidUploadException("제목을 입력해 주세요.");
		}
		if (title.length() > Video.TITLE_MAX_LENGTH) {
			throw new InvalidUploadException("제목은 " + Video.TITLE_MAX_LENGTH + "자 이하여야 합니다.");
		}
		return title;
	}

	private static String validDescription(String rawDescription) {
		String description = rawDescription == null || rawDescription.isBlank() ? null : rawDescription.strip();
		if (description != null && description.length() > Video.DESCRIPTION_MAX_LENGTH) {
			throw new InvalidUploadException("설명은 " + Video.DESCRIPTION_MAX_LENGTH + "자 이하여야 합니다.");
		}
		return description;
	}

	private static String extensionOf(String filename) {
		if (filename == null) {
			return "";
		}
		int dot = filename.lastIndexOf('.');
		return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
	}
}
