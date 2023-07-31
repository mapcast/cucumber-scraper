package com.cucumber.keiba.scrapper;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication
@EnableScheduling
public class KeibaScrapperApplication {

	public static void main(String[] args) {
		SpringApplication.run(KeibaScrapperApplication.class, args);
	}

}
