package com.webbroadcast.comment;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.webbroadcast.broadcast.BroadcastService;
import com.webbroadcast.user.NotOwnerException;
import com.webbroadcast.user.UserService;
import com.webbroadcast.video.VideoService;

/**
 * 누구나 Comment를 읽을 수 있고, 이메일 인증을 마친 User만 쓸 수 있다. 수정·삭제는 Author만 한다.
 */
@Service
public class CommentService {

	/** Video의 Comment는 최신순으로 이만큼씩 더 불러온다. */
	public static final int VIDEO_PAGE_SIZE = 20;
	/** Broadcast는 채팅처럼 최근 Comment만 보여 준다. */
	public static final int BROADCAST_RECENT_LIMIT = 100;

	/** nextCursor가 null이면 더 오래된 Comment가 없다. */
	public record VideoComments(List<Comment> items, UUID nextCursor, long totalCount) {
	}

	private final CommentRepository repository;
	private final VideoService videoService;
	private final BroadcastService broadcastService;
	private final UserService userService;
	private final Clock clock;

	CommentService(CommentRepository repository, VideoService videoService, BroadcastService broadcastService,
			UserService userService, Clock clock) {
		this.repository = repository;
		this.videoService = videoService;
		this.broadcastService = broadcastService;
		this.userService = userService;
		this.clock = clock;
	}

	/** 최신순. before에는 이전 응답의 nextCursor를 넘긴다. */
	public VideoComments listOnVideo(UUID videoId, UUID before) {
		videoService.get(videoId);
		Limit limit = Limit.of(VIDEO_PAGE_SIZE + 1);
		Comment cursor = before == null ? null : repository.findById(before)
			.filter(c -> videoId.equals(c.getVideoId()))
			.orElseThrow(() -> new InvalidCommentException("잘못된 댓글 위치입니다."));
		List<Comment> found = cursor == null
				? repository.findLatestOnVideo(videoId, limit)
				: repository.findOnVideoOlderThan(videoId, cursor.getCreatedAt(), cursor.getId(), limit);
		boolean hasMore = found.size() > VIDEO_PAGE_SIZE;
		List<Comment> items = hasMore ? found.subList(0, VIDEO_PAGE_SIZE) : found;
		return new VideoComments(items, hasMore ? items.getLast().getId() : null, repository.countByVideoIdAndHiddenAtIsNull(videoId));
	}

	/**
	 * Video에서 운영자가 숨긴 Comment의 ID들. 동영상 화면은 댓글을 한 번만 불러오므로,
	 * 이미 열어 둔 화면이 주기적으로 이 목록을 받아 숨긴 Comment를 뺀다. 본문은 주지 않는다.
	 */
	public List<UUID> hiddenIdsOnVideo(UUID videoId) {
		videoService.get(videoId);
		return repository.findHiddenIdsOnVideo(videoId);
	}

	/** 최근 Comment들을 오래된 순으로. 화면 아래에 새 Comment가 쌓인다. */
	public List<Comment> listOnBroadcast(UUID broadcastId) {
		broadcastService.get(broadcastId);
		return repository.findLatestOnBroadcast(broadcastId, Limit.of(BROADCAST_RECENT_LIMIT)).reversed();
	}

	public Comment writeOnVideo(UUID videoId, UUID authorId, String rawBody) {
		String body = validBody(rawBody);
		videoService.get(videoId);
		return repository.save(Comment.onVideo(videoId, userService.requireVerified(authorId), body, clock.instant()));
	}

	public Comment writeOnBroadcast(UUID broadcastId, UUID authorId, String rawBody) {
		String body = validBody(rawBody);
		broadcastService.get(broadcastId);
		return repository.save(Comment.onBroadcast(broadcastId, userService.requireVerified(authorId), body, clock.instant()));
	}

	/** 운영자가 내린 Comment는 Author에게도 없는 것으로 보인다 */
	public Comment get(UUID id) {
		return repository.findById(id).filter(c -> !c.isHidden()).orElseThrow(CommentNotFoundException::new);
	}

	/** 회원 탈퇴. 그 User가 쓴 Comment를 모두 지운다 */
	public void deleteAllWrittenBy(UUID authorId) {
		repository.deleteByAuthorId(authorId);
	}

	/** 운영자 화면용. 운영자가 내린 것들 */
	public Page<Comment> listHidden(Pageable pageable) {
		return repository.findByHiddenAtIsNotNull(pageable);
	}

	/** 운영자 화면용. 숨긴 Comment도 찾는다 */
	public Optional<Comment> findIncludingHidden(UUID id) {
		return repository.findById(id);
	}

	public void hide(UUID id) {
		Comment comment = repository.findById(id).orElseThrow(CommentNotFoundException::new);
		comment.hide(clock.instant());
		repository.save(comment);
	}

	public Comment edit(UUID id, UUID userId, String rawBody) {
		String body = validBody(rawBody);
		Comment comment = writtenBy(id, userId);
		comment.edit(body, clock.instant());
		return repository.save(comment);
	}

	public void delete(UUID id, UUID userId) {
		repository.delete(writtenBy(id, userId));
	}

	private Comment writtenBy(UUID id, UUID userId) {
		Comment comment = get(id);
		if (!comment.isWrittenBy(userId)) {
			throw new NotOwnerException("본인이 쓴 댓글만 수정하거나 삭제할 수 있습니다.");
		}
		return comment;
	}

	private static String validBody(String rawBody) {
		String body = rawBody == null ? "" : rawBody.strip();
		if (body.isEmpty()) {
			throw new InvalidCommentException("댓글 내용을 입력해 주세요.");
		}
		if (body.length() > Comment.BODY_MAX_LENGTH) {
			throw new InvalidCommentException("댓글은 " + Comment.BODY_MAX_LENGTH + "자 이하여야 합니다.");
		}
		return body;
	}
}
