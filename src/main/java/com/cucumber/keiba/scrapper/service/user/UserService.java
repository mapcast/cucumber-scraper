package com.cucumber.keiba.scrapper.service.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.cucumber.keiba.scrapper.dto.user.JoinDto;
import com.cucumber.keiba.scrapper.dto.user.LoginDto;
import com.cucumber.keiba.scrapper.model.User;
import com.cucumber.keiba.scrapper.repository.UserRepository;
import com.cucumber.keiba.scrapper.util.EncryptionUtil;
import com.cucumber.keiba.scrapper.util.TokenUtil;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
	
	private final UserRepository userRepository;
	
	private final EncryptionUtil encryptionUtil;
	
	private final TokenUtil tokenUtil;
	
	public String login(LoginDto dto) throws Exception {
		String encrypted = encryptionUtil.encrypt(dto.getPassword());
		Optional<User> wrapped = userRepository.findByUserIdAndPasswordAndIsDeletedFalse(dto.getUserId(), encrypted);
		if(wrapped.isPresent()) {
			return tokenUtil.generateToken(wrapped.get());
		} else {
			return "LoginFailed";
		}
	}
	
	public boolean join(JoinDto dto) throws Exception {
		Optional<User> wrapped = userRepository.findByUserIdAndIsDeletedFalse(dto.getUserId());
		if(!wrapped.isPresent()) {
			User user = new User();
			user.setUuid(UUID.randomUUID().toString());
			user.setUserId(dto.getUserId());
			user.setUserNickname(dto.getUserNickname());
			user.setPassword(encryptionUtil.encrypt(dto.getPassword()));
			user.setRole("ROLE_USER");
			userRepository.save(user);
			return true;
		} else {
			return false;
		}
	}
	
	public User getUserByUserId(String userId) {
		return userRepository.findByUserIdAndIsDeletedFalse(userId).orElseGet(null);
	}
}
