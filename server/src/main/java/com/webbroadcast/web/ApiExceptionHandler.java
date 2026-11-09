package com.webbroadcast.web;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.webbroadcast.broadcast.BroadcastInProgressException;
import com.webbroadcast.broadcast.BroadcastNotFoundException;
import com.webbroadcast.broadcast.InvalidBroadcastException;
import com.webbroadcast.comment.CommentNotFoundException;
import com.webbroadcast.comment.InvalidCommentException;
import com.webbroadcast.donation.DonationNotFoundException;
import com.webbroadcast.donation.InvalidDonationException;
import com.webbroadcast.donation.PaymentUnavailableException;
import com.webbroadcast.user.DuplicateUserException;
import com.webbroadcast.user.InvalidSignUpException;
import com.webbroadcast.user.NotOwnerException;
import com.webbroadcast.video.InvalidUploadException;
import com.webbroadcast.video.VideoNotFoundException;
import com.webbroadcast.web.donation.PaymentNotConfiguredException;

/**
 * 오류는 RFC 9457 ProblemDetail 형식으로, 사용자에게 보여줄 문구를 detail에 담는다.
 * 로그인이 필요한 요청을 로그인 없이 보낸 경우(401)는 SecurityConfig가 같은 형식으로 응답한다.
 */
@RestControllerAdvice
class ApiExceptionHandler {

	@ExceptionHandler({ InvalidUploadException.class, InvalidBroadcastException.class, InvalidCommentException.class,
			InvalidSignUpException.class, InvalidDonationException.class })
	ProblemDetail badRequest(RuntimeException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ProblemDetail unreadableBody(HttpMessageNotReadableException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
	}

	@ExceptionHandler(AuthenticationException.class)
	ProblemDetail loginFailed(AuthenticationException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
	}

	@ExceptionHandler(NotOwnerException.class)
	ProblemDetail forbidden(NotOwnerException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
	}

	@ExceptionHandler(DuplicateUserException.class)
	ProblemDetail duplicateUser(DuplicateUserException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
	}

	/** 진행 중인 Broadcast의 ID를 함께 보내 웹이 그 방송을 종료할 수 있게 한다 */
	@ExceptionHandler(BroadcastInProgressException.class)
	ProblemDetail broadcastInProgress(BroadcastInProgressException e) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
		problem.setProperty("activeBroadcastId", e.getActiveBroadcastId());
		return problem;
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ProblemDetail concurrentChange(OptimisticLockingFailureException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "방송 상태가 막 바뀌었습니다. 다시 시도해 주세요.");
	}

	/** 승인 결과를 아직 모른다. Donation은 결제 대기로 남고, 같은 승인 요청을 다시 보내면 된다 */
	@ExceptionHandler(PaymentUnavailableException.class)
	ProblemDetail paymentUnavailable(PaymentUnavailableException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"결제 결과를 아직 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.");
	}

	@ExceptionHandler(PaymentNotConfiguredException.class)
	ProblemDetail paymentNotConfigured(PaymentNotConfiguredException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"후원 결제가 아직 설정되지 않았습니다. 서버의 토스페이먼츠 키를 확인해 주세요.");
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ProblemDetail tooLarge(MaxUploadSizeExceededException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "파일은 2GB 이하여야 합니다.");
	}

	@ExceptionHandler(VideoNotFoundException.class)
	ProblemDetail videoNotFound(VideoNotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "동영상을 찾을 수 없습니다.");
	}

	@ExceptionHandler(BroadcastNotFoundException.class)
	ProblemDetail broadcastNotFound(BroadcastNotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "방송을 찾을 수 없습니다.");
	}

	@ExceptionHandler(CommentNotFoundException.class)
	ProblemDetail commentNotFound(CommentNotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다.");
	}

	@ExceptionHandler(DonationNotFoundException.class)
	ProblemDetail donationNotFound(DonationNotFoundException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "후원을 찾을 수 없습니다.");
	}

	/** 경로의 ID가 UUID 형식이 아니면 그런 대상은 없는 것으로 본다 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ProblemDetail malformedId(MethodArgumentTypeMismatchException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "찾을 수 없습니다.");
	}
}
