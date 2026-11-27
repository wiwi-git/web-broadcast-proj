package com.webbroadcast.web;

import java.util.UUID;

import com.webbroadcast.user.User;

/** 다른 사람에게 보이는 User 정보. 이메일은 내보내지 않는다. */
public record UserSummary(UUID id, String nickname) {

	public static UserSummary from(User user) {
		return new UserSummary(user.getId(), user.getNickname());
	}
}
