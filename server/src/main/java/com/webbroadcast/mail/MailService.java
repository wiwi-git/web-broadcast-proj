package com.webbroadcast.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 짧은 안내 메일(이메일 인증, 비밀번호 재설정)을 보낸다.
 * SMTP(spring.mail.host)가 설정되지 않았으면 보내지 않고 내용을 로그에 남긴다(개발용).
 * 보내다 실패해도 예외를 던지지 않는다. 회원은 화면에서 다시 보내기를 누를 수 있다.
 */
@Service
public class MailService {

	private static final Logger log = LoggerFactory.getLogger(MailService.class);

	private final ObjectProvider<JavaMailSender> sender;
	private final MailProperties properties;

	MailService(ObjectProvider<JavaMailSender> sender, MailProperties properties) {
		this.sender = sender;
		this.properties = properties;
	}

	/** @return 보냈으면 true. SMTP가 없거나 실패했으면 false */
	public boolean send(String to, String subject, String body) {
		JavaMailSender smtp = sender.getIfAvailable();
		if (smtp == null) {
			log.info("메일 발송이 설정되지 않아 로그로 대신합니다. to={} subject={}\n{}", to, subject, body);
			return false;
		}
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(properties.from());
		message.setTo(to);
		message.setSubject(subject);
		message.setText(body);
		try {
			smtp.send(message);
			return true;
		}
		catch (MailException e) {
			log.warn("메일을 보내지 못했습니다. to={} subject={}", to, subject, e);
			return false;
		}
	}
}
