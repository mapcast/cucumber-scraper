package com.cucumber.keiba.scrapper.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.cucumber.keiba.scrapper.repository")
@EnableMongoRepositories(basePackages = "com.cucumber.keiba.scrapper.mongo.repository")
public class PersistenceConfig {
	
}
