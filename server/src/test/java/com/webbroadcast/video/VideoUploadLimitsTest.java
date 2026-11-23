package com.webbroadcast.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import com.webbroadcast.media.MediaInfo;
import com.webbroadcast.media.MediaInspector;
import com.webbroadcast.storage.VideoStorage;
import com.webbroadcast.user.User;
import com.webbroadcast.user.UserService;

/**
 * 공개 서버의 디스크를 지키는 업로드 제한: 파일 하나 상한, User별 총용량, 디스크 여유.
 * 제한에 걸리면 Original을 디스크에 쓰기 전에 거절한다.
 */
class VideoUploadLimitsTest {

	static final DataSize MAX_FILE = DataSize.ofKilobytes(1);
	static final DataSize QUOTA = DataSize.ofKilobytes(2);
	static final DataSize MIN_FREE = DataSize.ofKilobytes(10);

	@TempDir
	Path dir;

	VideoRepository repository = mock(VideoRepository.class);
	VideoStorage storage = mock(VideoStorage.class);
	MediaInspector inspector = mock(MediaInspector.class);
	UserService userService = mock(UserService.class);
	VideoService service;
	UUID ownerId = UUID.randomUUID();

	@BeforeEach
	void setUp() throws Exception {
		service = new VideoService(repository, storage, inspector, userService,
				new VideoUploadProperties(MAX_FILE, QUOTA, MIN_FREE));
		when(storage.originalPath(any(), any())).thenAnswer(i -> dir.resolve(i.getArgument(0) + ".mp4"));
		when(storage.usableSpace()).thenReturn(1_000_000L);
		when(inspector.inspect(any())).thenReturn(Optional.of(new MediaInfo(320, 240, 1.0, false)));
		when(userService.get(ownerId)).thenReturn(mock(User.class));
		when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
		when(repository.totalSizeOfOwner(ownerId)).thenReturn(0L);
	}

	static MockMultipartFile file(int bytes) {
		return new MockMultipartFile("file", "a.mp4", "video/mp4", new byte[bytes]);
	}

	@Test
	void acceptsAFileWithinAllLimits() {
		Video video = service.upload(ownerId, file(1_024), "영상", null);

		assertThat(video.getStatus()).isEqualTo(VideoStatus.PROCESSING);
	}

	@Test
	void rejectsAFileLargerThanTheFileLimit() {
		assertThatThrownBy(() -> service.upload(ownerId, file(1_025), "영상", null))
			.isInstanceOf(InvalidUploadException.class)
			.hasMessageContaining("1KB");
		verify(storage, never()).originalPath(any(), any());
	}

	@Test
	void rejectsAnUploadThatWouldExceedTheOwnersQuota() {
		when(repository.totalSizeOfOwner(ownerId)).thenReturn(1_600L);

		assertThatThrownBy(() -> service.upload(ownerId, file(449), "영상", null))
			.isInstanceOf(UploadQuotaExceededException.class)
			.hasMessageContaining("2KB");
		verify(storage, never()).originalPath(any(), any());
	}

	@Test
	void acceptsAnUploadThatFillsTheQuotaExactly() {
		when(repository.totalSizeOfOwner(ownerId)).thenReturn(1_600L);

		assertThat(service.upload(ownerId, file(448), "영상", null)).isNotNull();
	}

	@Test
	void rejectsAnUploadThatWouldLeaveTooLittleFreeDisk() {
		when(storage.usableSpace()).thenReturn(10_500L);

		assertThatThrownBy(() -> service.upload(ownerId, file(300), "영상", null))
			.isInstanceOf(StorageFullException.class);
		verify(storage, never()).originalPath(any(), any());
	}
}
