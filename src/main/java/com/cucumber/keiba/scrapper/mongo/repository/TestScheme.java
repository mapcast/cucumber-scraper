package com.cucumber.keiba.scrapper.mongo.repository;

import javax.persistence.Id;

import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.Setter;

@Document("soccer")
@Getter
@Setter
public class TestScheme {

	@Id
	private String _id;
	
	private String title;
	
	private String message;
	
}