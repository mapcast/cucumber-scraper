package com.cucumber.keiba.scrapper.dto.user;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginDto {
	private String userId;
	private String password;
}
