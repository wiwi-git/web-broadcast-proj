package com.webbroadcast.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * from: 보내는 사람. webOrigin: 메일 속 링크가 가리킬 웹 주소(예: https://live.duse.dev).
 * SMTP 접속 정보는 spring.mail.*에 둔다.
 */
@ConfigurationProperties("mail")
public record MailProperties(String from, String webOrigin) {

	public MailProperties {
		webOrigin = webOrigin == null ? "" : webOrigin.replaceAll("/+$", "");
	}

	/** 웹 화면 주소. path는 /로 시작한다 */
	public String link(String path) {
		return webOrigin + path;
	}
}
