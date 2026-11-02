package com.webbroadcast.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.webbroadcast.video.InvalidUploadException;
import com.webbroadcast.video.VideoNotFoundException;

/**
 * 오류는 RFC 9457 ProblemDetail 형식으로, 사용자에게 보여줄 문구를 detail에 담는다.
 */
@RestControllerAdvice
class ApiExceptionHandler {

	@ExceptionHandler(InvalidUploadException.class)
	ProblemDetail invalidUpload(InvalidUploadException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ProblemDetail tooLarge(MaxUploadSizeExceededException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "파일은 2GB 이하여야 합니다.");
	}

	@ExceptionHandler({ VideoNotFoundException.class, MethodArgumentTypeMismatchException.class })
	ProblemDetail notFound(RuntimeException e) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "동영상을 찾을 수 없습니다.");
	}
}
