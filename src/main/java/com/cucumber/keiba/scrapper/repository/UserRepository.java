package com.cucumber.keiba.scrapper.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cucumber.keiba.scrapper.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
	User findByUserId(String userId);
	
	Optional<User> findByUserIdAndPasswordAndIsDeletedFalse(String userId, String userPassword); 
	
	Optional<User> findByUserIdAndIsDeletedFalse(String userId);
}
