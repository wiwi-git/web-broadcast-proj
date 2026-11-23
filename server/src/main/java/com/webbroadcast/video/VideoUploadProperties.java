package com.webbroadcast.video;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * 업로드 제한. 공개 서버 디스크(OCI 부트 볼륨 45GB)가 차서 DB·라이브·변환이 멈추지 않게 한다.
 * 파일 하나 상한은 multipart 설정(spring.servlet.multipart.*)과 Caddy의 요청 크기 상한보다 작거나 같아야 한다.
 *
 * @param maxFileSize 파일 하나의 최대 크기
 * @param quotaPerUser User 한 명이 올린 Original 크기의 합계 상한. 동영상을 지우면 그만큼 다시 올릴 수 있다
 * @param minFreeDisk 업로드한 뒤에도 저장소 디스크에 남아 있어야 하는 여유 공간
 */
@ConfigurationProperties("video.upload")
public record VideoUploadProperties(
		@DefaultValue("500MB") DataSize maxFileSize,
		@DefaultValue("2GB") DataSize quotaPerUser,
		@DefaultValue("5GB") DataSize minFreeDisk) {
}
