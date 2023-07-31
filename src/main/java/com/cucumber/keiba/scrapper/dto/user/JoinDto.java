package com.cucumber.keiba.scrapper.dto.user;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JoinDto {
	private String userId;
	private String userNickname;
	private String password;
}
