package com.cucumber.keiba.scrapper.util;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.cucumber.keiba.scrapper.model.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@Component
public class TokenUtil {
	private final String secretKey = "hschoToken";
	private final Long expireTime = 1000 * 60L * 60L;
	
	public String generateToken(User user) {
		Date now = new Date();
		return Jwts.builder()
				.setSubject(user.getUserId())
				.setHeader(createHeader())
				.setClaims(createClaims(user))
				.setExpiration(new Date(now.getTime() + expireTime))
				.signWith(SignatureAlgorithm.HS256, secretKey)
				.compact();
	}
	private Map<String, Object> createHeader() {
	    Map<String, Object> header = new HashMap<>();
	    header.put("type", "JWT");
	    header.put("alg", "HS256"); 
	    header.put("regDate", System.currentTimeMillis());
	    return header;
	}

	private Map<String, Object> createClaims(User user) {
		ObjectMapper mapper = new ObjectMapper();
	    Map<String, Object> claims = new HashMap<>();
	    try {
			String userString = mapper.writeValueAsString(user);
		    claims.put("userInfo", userString);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
	    return claims;
	}
	
	public String getUserInfoFromToken(String token) {
		return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody().get("userInfo").toString();
	}
}
