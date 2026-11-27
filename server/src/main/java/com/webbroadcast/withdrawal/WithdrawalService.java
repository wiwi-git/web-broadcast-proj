package com.webbroadcast.withdrawal;

import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.webbroadcast.broadcast.BroadcastService;
import com.webbroadcast.comment.CommentService;
import com.webbroadcast.donation.DonationService;
import com.webbroadcast.report.ReportService;
import com.webbroadcast.user.User;
import com.webbroadcast.user.UserService;
import com.webbroadcast.video.VideoService;

/**
 * 회원 탈퇴. 비밀번호를 다시 확인한 뒤 그 User의 모든 것을 한 트랜잭션에서 지운다.
 * 계정, Video(파일은 커밋 뒤), Broadcast, 쓴 Comment, 보내거나 받은 Donation, 한 Report, 유튜브 키, 모든 기기의 로그인.
 * 그 User의 Content에 다른 사람이 단 Comment도 Content와 함께 지워진다. 개인정보처리방침 3항과 맞춘다.
 */
@Service
public class WithdrawalService {

	private final UserService userService;
	private final CommentService commentService;
	private final DonationService donationService;
	private final ReportService reportService;
	private final BroadcastService broadcastService;
	private final VideoService videoService;
	private final ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessions;

	WithdrawalService(UserService userService, CommentService commentService, DonationService donationService,
			ReportService reportService, BroadcastService broadcastService, VideoService videoService,
			ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sessions) {
		this.userService = userService;
		this.commentService = commentService;
		this.donationService = donationService;
		this.reportService = reportService;
		this.broadcastService = broadcastService;
		this.videoService = videoService;
		this.sessions = sessions;
	}

	@Transactional
	public void withdraw(UUID userId, String password) {
		if (!userService.passwordMatches(userId, password)) {
			throw new InvalidWithdrawalException("비밀번호가 올바르지 않습니다.");
		}
		User user = userService.get(userId);
		if (user.isAdmin()) {
			throw new InvalidWithdrawalException("운영자 계정은 탈퇴할 수 없습니다. 먼저 운영자 역할을 해제해 주세요.");
		}
		String email = user.getEmail();

		// 이 User를 가리키는 기록부터 지운다 (외래 키 순서)
		commentService.deleteAllWrittenBy(userId);
		donationService.deleteAllOf(userId);
		reportService.forget(userId);
		broadcastService.deleteAllOwnedBy(userId);
		videoService.deleteAllOwnedBy(userId);
		userService.delete(userId);

		// 다른 기기에서 로그인한 세션도 끊는다. 지금 요청의 세션은 컨트롤러가 무효로 만든다
		FindByIndexNameSessionRepository<? extends Session> repository = sessions.getIfAvailable();
		if (repository != null) {
			repository.findByPrincipalName(email).keySet().forEach(repository::deleteById);
		}
	}
}
