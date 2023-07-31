package com.cucumber.keiba.scrapper.service.user;

import org.springframework.stereotype.Service;

import com.cucumber.keiba.scrapper.model.User;
import com.cucumber.keiba.scrapper.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service("userDetailService")
@RequiredArgsConstructor
public class UserDetailService {

	private final UserRepository userRepository;
	
	public User getUserByUserId(String userId) {
		return userRepository.findByUserId(userId);
	}
}
